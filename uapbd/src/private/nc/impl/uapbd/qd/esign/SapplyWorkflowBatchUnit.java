package nc.impl.uapbd.qd.esign;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.transaction.TransactionSynchronizationRegistry;
import nc.bs.framework.common.NCLocator;
import nc.bs.uapbd.qd.esign.audit.AuditCaptureScope;
import nc.bs.uapbd.qd.esign.audit.AuditRecorder;
import nc.bs.uapbd.qd.esign.audit.InventoryPhysicalSnapshots;
import nc.bs.uapbd.qd.esign.audit.SapplyBpAudit;
import nc.bs.uapbd.qd.esign.audit.SapplyBatchApprovalProof;
import nc.bs.uapbd.qd.esign.audit.SapplyWorkflowBatchAspect;
import nc.bs.uapbd.qd.esign.audit.SapplyWorkflowBatchAspect.NativeCall;
import nc.bs.uapbd.qd.esign.audit.SapplyWorkflowBatchAspect.Unit;
import nc.bs.uapbd.qd.esign.audit.SapplyWorkflowBatchAspect.UnitCallback;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.bs.uapbd.qd.esign.core.MappingRules;
import nc.bs.uapbd.qd.esign.core.NativeTransactionBoundaryProbe;
import nc.bs.uapbd.qd.esign.persistence.QdEsignStore;
import nc.bs.uapbd.qd.esign.security.EsignPermission;
import nc.bs.uapbd.qd.esign.security.ServerIdentity;
import nc.itf.ic.qd.esign.IInventoryPfStateAdapter;
import nc.pubitf.rbac.IDataPermissionPubService;
import nc.vo.ic.general.define.ICBillHeadVO;
import nc.vo.ic.qd.esign.InventoryPfStateCommand;
import nc.vo.ic.qd.esign.InventoryPfStateResult;
import nc.vo.pub.BusinessException;
import nc.vo.pubapp.pflow.PfUserObject;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.InventoryBillScope;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.PolicyDecision;
import nc.vo.uapbd.qd.esign.SapplyBatchScope;

/** Authorizes one native PF unit and commits its audit and receipt with the same bill. */
public final class SapplyWorkflowBatchUnit implements UnitCallback {
    static final String NODE = SapplyBatchScope.NODE;
    static final String ADAPTER = "nc.itf.ic.qd.esign.IInventoryPfStateAdapter";
    static final String POLICY_MODE = SapplyBatchScope.POLICY_MODE;

    @Override
    public Object execute(Unit unit, final NativeCall nativeCall) throws Throwable {
        QdEsignStore store = new QdEsignStore();
        ServerIdentity identity = ServerIdentity.current();
        String id = unit.getRequestId();
        // Take locks in request/object order, also used by the batch finalizer.
        Map<String,Object> request = SignatureServiceImpl.owned(store, id, identity, true);
        String button = EsignValues.text(request, "button_code");
        String operation = EsignValues.text(request, "operation_type");
        String org = EsignValues.text(request, "pk_org");
        requireRequest(request, unit.getExecutionToken(), unit.getAction(), System.currentTimeMillis());

        ICBillHeadVO head = unit.getBill().getHead();
        String pk = head == null ? null : key(head.getCgeneralhid());
        EsignValues.require(pk != null && identity.groupId.equals(key(head.getPk_group()))
                && org.equals(key(head.getPk_org())), "出库申请批量单据集团或组织与授权不一致。");
        Map<String,Object> object = store.lockSapplyBatchBill(id, pk);
        BusinessObjectRef ref = requirePendingBill(object, identity.groupId, org, pk);
        List<BusinessObjectRef> refs = Collections.singletonList(ref);

        EsignPermission.requireBusiness(identity, NODE, button, org);
        if ("Delete".equals(button) || "UnCommit".equals(button)) {
            IDataPermissionPubService permissions = NCLocator.getInstance().lookup(IDataPermissionPubService.class);
            EsignValues.require(permissions.isUserhasPermission("4455", pk, button.toLowerCase(Locale.ROOT),
                    identity.groupId, identity.userId), "当前用户没有该出库申请单据的操作权限。");
        }
        PolicyDecision policy = PolicyServiceImpl.resolveFor(identity, org, NODE, button);
        requirePolicy(policy, request);
        requireMapping(store, button, operation, refs);

        Object user = unit.getUserObject();
        EsignValues.require(user == null || user instanceof PfUserObject, "出库申请批量流程参数类型不正确。");
        OperationContext context = new OperationContext(id, EsignValues.text(request, "batch_id"), identity.groupId,
                org, NODE, EsignValues.text(request, "node_name"), button, EsignValues.text(request, "button_name"),
                operation, EsignValues.text(request, "reason"), refs);
        InventoryPfStateCommand command = new InventoryPfStateCommand(unit.getBillType(), unit.getBill(),
                (PfUserObject) user, unit.getWorkflowNote(), unit.getParameters());
        boolean approval="Approve".equals(button) || "UnApprove".equals(button);
        SapplyBatchApprovalProof approvalProof=unit.getApprovalProof();
        if(approval){
            EsignValues.require(approvalProof!=null,"出库申请批量审批缺少签名内容核对。");
            SapplyWorkflowBatchAspect.requireApprovalIntent(context,command);
        } else EsignValues.require(approvalProof==null,"非审批批量携带了审批内容。");
        IInventoryPfStateAdapter adapter = NCLocator.getInstance().lookup(IInventoryPfStateAdapter.class);
        TransactionSynchronizationRegistry transaction = unit.getTransaction();
        try (AuditCaptureScope scope = new AuditCaptureScope(context, policy.isAuditEnabled());
                SapplyBpAudit.Capture capture = SapplyBpAudit.open(context, scope, transaction)) {
            InventoryPfStateResult result = adapter.executeSapplyUnit(context, command, unit.getAction(),
                    new IInventoryPfStateAdapter.NativeCall() {
                        public Object proceed() throws Throwable {
                            return nativeCall.proceed();
                        }
                    });
            List<BusinessObjectRef> saved = adapter.describeResult(context, result);
            capture.verify(saved);
            BusinessObjectRef completed = saved.get(0);
            store.backfillObject(id, ref.getClientObjectId(), completed.getObjectPk(), completed.getBillCode());
            if (scope.isEnabled()) {
                new InventoryPhysicalSnapshots().after(scope, context, saved);
                String clientId = ref.getClientObjectId();
                EsignValues.require(scope.hasBefore(clientId) && (result.isDeleted() || scope.hasAfter(clientId)),
                        "出库申请批量单据的前后快照不完整。");
                new AuditRecorder().record(request, object, scope.getBefore(clientId), scope.getAfter(clientId),
                        scope.getBeforeReferences(clientId));
            }
            store.finishAssignmentUnit(id, EsignValues.text(object, "object_id"), "SUCCESS", null);
            NativeTransactionBoundaryProbe.requireSingleDataSource(transaction, unit.getTransactionKey());
            return result.getNativeResult();
        }
    }

    static void requireRequest(Map<String,Object> request, String token, String action, long now)
            throws BusinessException {
        String button = EsignValues.text(request, "button_code");
        String operation = EsignValues.text(request, "operation_type");
        String signature = EsignValues.text(request, "signature_state");
        requireRunning(request, token);
        String expires = EsignValues.text(request, "expires_at");
        EsignValues.require(key(EsignValues.text(request, "pk_org")) != null
                && ("SUCCESS".equals(signature) || "NOT_REQUIRED".equals(signature))
                && matchesNativeAction(button,operation,action,EsignValues.text(request,"user_id"))
                && expires != null && now <= EsignValues.parseUtc(expires),
                "出库申请批量授权无效、已消费或与原生动作不一致。");
    }

    static void requireRunning(Map<String,Object> request, String token) throws BusinessException {
        EsignValues.require(!QdEsignStore.isArchivedResult(request) && NODE.equals(request.get("node_code"))
                && POLICY_MODE.equals(request.get("policy_mode")) && "RUNNING".equals(request.get("business_state"))
                && request.get("finished_at") == null && token != null && token.equals(request.get("execution_token")),
                "出库申请批量请求已结束或执行令牌无效。");
    }

    static void requireMapping(QdEsignStore store, String button, String operation, List<BusinessObjectRef> refs)
            throws BusinessException {
        Map<String,Object> mapping = store.loadNodeByAction(NODE, button);
        EsignValues.require(mapping != null && ADAPTER.equals(EsignValues.text(mapping, "adapter_class")),
                "出库申请批量操作缺少对应的 PF 适配器。");
        MappingRules.requireDomain(mapping, operation, refs);
    }

    static String nativeAction(String button, String operation) {
        if ("Delete".equals(button) && "DELETE".equals(operation)) return "DELETE";
        if (!"UPDATE".equals(operation)) return null;
        if ("Commit".equals(button)) return "SAVE";
        if ("UnCommit".equals(button)) return "UNSAVE";
        if ("Approve".equals(button)) return "APPROVE";
        if ("UnApprove".equals(button)) return "UNAPPROVE";
        return null;
    }

    static boolean matchesNativeAction(String button,String operation,String action,String userId) {
        String expected=nativeAction(button,operation);
        if(expected==null)return false;
        if("Approve".equals(button) || "UnApprove".equals(button))
            return nc.vo.ic.qd.esign.SapplyNativeAction.matches(button,action,userId);
        return expected.equals(action);
    }

    static void requirePolicy(PolicyDecision policy, Map<String,Object> request) throws BusinessException {
        EsignValues.require(policy != null && policy.isControlled() && policy.isValidated()
                && policy.getVersion() == EsignValues.number(request, "policy_version")
                && policy.getControlVersion() == EsignValues.number(request, "control_version")
                && (!policy.isSignatureRequired() || "SUCCESS".equals(request.get("signature_state")))
                && (!policy.isReasonRequired() || key(EsignValues.text(request, "reason")) != null),
                "出库申请批量签名或审计策略已变化，请重新发起操作。");
    }

    static BusinessObjectRef requirePendingBill(Map<String,Object> object, String group, String org, String pk)
            throws BusinessException {
        BusinessObjectRef bill = requireBill(object, group, org, pk);
        EsignValues.require("PENDING".equals(object.get("unit_state")), "出库申请批量单据已经执行。");
        return bill;
    }

    static BusinessObjectRef requireBill(Map<String,Object> object, String group, String org, String pk)
            throws BusinessException {
        EsignValues.require(object != null && pk != null && pk.equals(key(pk)) && pk.equals(EsignValues.text(object, "object_pk"))
                && InventoryBillScope.SAPPLY.equals(object.get("object_type"))
                && "ROOT".equals(object.get("relation_type")) && object.get("parent_object_id") == null
                && group.equals(object.get("pk_group")) && org.equals(object.get("pk_org"))
                && key(EsignValues.text(object, "object_id")) != null
                && key(EsignValues.text(object, "client_object_id")) != null,
                "出库申请批量单据超出授权范围。");
        return new BusinessObjectRef(EsignValues.text(object, "client_object_id"), InventoryBillScope.SAPPLY,
                pk, EsignValues.text(object, "bill_code"), null);
    }

    static String key(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() || "~".equals(trimmed) ? null : trimmed;
    }
}
