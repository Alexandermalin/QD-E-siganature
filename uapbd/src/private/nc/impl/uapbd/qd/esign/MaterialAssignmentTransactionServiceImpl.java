package nc.impl.uapbd.qd.esign;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.transaction.TransactionSynchronizationRegistry;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.bs.uapbd.qd.esign.core.NativeTransactionBoundaryProbe;
import nc.bs.uapbd.qd.esign.core.RequestRules;
import nc.bs.uapbd.qd.esign.core.TransactionRollback;
import nc.bs.uapbd.qd.esign.persistence.QdEsignStore;
import nc.bs.uapbd.qd.esign.security.EsignPermission;
import nc.bs.uapbd.qd.esign.security.ServerIdentity;
import nc.itf.uapbd.qd.esign.IMaterialAssignmentService;
import nc.itf.uapbd.qd.esign.IMaterialAssignmentTransactionService;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.MaterialAssignmentResult;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.PolicyDecision;

public final class MaterialAssignmentTransactionServiceImpl implements IMaterialAssignmentTransactionService {
    public Claim claim_RequiresNew(String id) throws BusinessException {
        QdEsignStore store = new QdEsignStore();
        ServerIdentity identity = ServerIdentity.current();
        Map<String,Object> request = SignatureServiceImpl.owned(store, id, identity, false);
        requireRoute(request, identity);
        if (!"READY".equals(request.get("business_state"))) {
            EsignValues.require(!"PENDING".equals(request.get("business_state")), "请先完成签名或填写事由。");
            return null;
        }
        request = SignatureServiceImpl.owned(store, id, identity, true);
        if (!"READY".equals(request.get("business_state"))) { return null; }
        List<Map<String,Object>> objects = store.loadObjects(id);
        requireObjectCount(request, objects);
        validate(store, identity, request, objects, false);
        EsignValues.require(RequestRules.mayExecute(EsignValues.text(request, "signature_state"), "READY"),
            "物料分配授权尚未准备就绪。");
        EsignValues.require(System.currentTimeMillis() <= EsignValues.parseUtc(EsignValues.text(request, "expires_at")),
            "物料分配请求已过期，请重新发起。");
        String token = EsignValues.id();
        List<String> ids = new ArrayList<String>();
        for (Map<String,Object> object : objects) {
            EsignValues.require("PENDING".equals(object.get("unit_state")), "物料分配请求已经使用。");
            ids.add(EsignValues.text(object, "object_id"));
        }
        store.updateRequest(id, EsignValues.row("business_state", "RUNNING", "execution_token", token));
        return new Claim(token, ids);
    }

    public void apply_RequiresNew(String id, String token, String objectId) throws BusinessException {
        TransactionSynchronizationRegistry transaction = NativeTransactionBoundaryProbe.requireActiveTransaction();
        Object transactionKey = transaction.getTransactionKey();
        QdEsignStore store = new QdEsignStore();
        ServerIdentity identity = ServerIdentity.current();
        Map<String,Object> request = running(store, identity, id, token);
        Map<String,Object> object = store.loadAssignmentUnit(id, objectId);
        EsignValues.require(object != null, "找不到本次物料分配目标。");
        if (!"PENDING".equals(object.get("unit_state"))) { return; }
        try {
            validate(store, identity, request, Collections.singletonList(object), true);
            EsignValues.require(store.isActiveBusinessUnit(identity.groupId, EsignValues.text(object, "target_org")),
                "目标组织须为当前集团内启用的业务单元。");
            boolean wrote = new MaterialAssignmentNative().apply(request, object);
            EsignValues.require(transactionKey.equals(transaction.getTransactionKey()),
                "原生物料分配执行期间业务事务发生变化。");
            store.finishAssignmentUnit(id, objectId, wrote ? "SUCCESS" : "ALREADY_ASSIGNED", null);
            NativeTransactionBoundaryProbe.requireSingleDataSource(transaction,transactionKey);
        } catch (BusinessException failure) {
            throw TransactionRollback.abort(transaction, transactionKey, failure);
        } catch (RuntimeException failure) {
            throw TransactionRollback.abort(transaction, transactionKey, failure);
        }
    }

    public void verifyUnit_RequiresNew(String id,String token,String objectId) throws BusinessException {
        TransactionSynchronizationRegistry tx=NativeTransactionBoundaryProbe.requireActiveTransaction();Object key=tx.getTransactionKey();
        QdEsignStore store=new QdEsignStore();ServerIdentity identity=ServerIdentity.current();
        Map<String,Object> request=running(store,identity,id,token);requireRoute(request,identity);
        AssignmentUnitVerification.require(store,request,store.loadAssignmentUnit(id,objectId),"SUCCESS","ALREADY_ASSIGNED");
        EsignValues.require(key.equals(tx.getTransactionKey()) && !tx.getRollbackOnly(),"物料分配结果核对事务不可用。");
    }
    public void recordUncommitted_RequiresNew(String id, String token, String objectId, boolean rejected)
            throws BusinessException {
        QdEsignStore store = new QdEsignStore();
        running(store, ServerIdentity.current(), id, token);
        Map<String,Object> object = store.loadAssignmentUnit(id, objectId);
        EsignValues.require(object != null, "找不到本次物料分配目标。");
        // Commit may have succeeded before the dispatch error. The durable unit always takes precedence.
        if ("PENDING".equals(object.get("unit_state"))) {
            store.finishAssignmentUnit(id, objectId, rejected ? "FAILED" : "UNKNOWN",
                rejected ? "ASSIGNMENT_ROLLED_BACK" : "ASSIGNMENT_OUTCOME_UNKNOWN");
        }
    }

    public void finish_RequiresNew(String id, String token) throws BusinessException {
        QdEsignStore store = new QdEsignStore();
        Map<String,Object> request = running(store, ServerIdentity.current(), id, token);
        List<Map<String,Object>> objects = store.loadObjects(id);
        requireObjectCount(request, objects);
        String state = summarize(objects);
        store.updateRequest(id, EsignValues.row("business_state", state, "execution_token", null,
            "finished_at", "UNKNOWN".equals(state) ? null : EsignValues.utc(),
            "error_code", "UNKNOWN".equals(state) ? "ASSIGNMENT_OUTCOME_UNKNOWN" : null));
    }

    static String summarize(List<Map<String,Object>> units) {
        boolean successes = false, failures = false;
        for (Map<String,Object> unit : units) {
            Object state = unit.get("unit_state");
            if ("SUCCESS".equals(state) || "ALREADY_ASSIGNED".equals(state)) { successes = true; }
            else if ("FAILED".equals(state)) { failures = true; }
            else { return "UNKNOWN"; }
        }
        return failures ? (successes ? "PARTIAL" : "FAILED") : "SUCCESS";
    }

    static void requireObjectCount(Map<String,Object> request, List<Map<String,Object>> objects) throws BusinessException {
        EsignValues.require(!objects.isEmpty() && objects.size() == EsignValues.number(request, "owner_object_count"),
            "物料分配对象清单不完整，请按请求号核对。");
    }

    static void requireRoute(Map<String,Object> request, ServerIdentity identity) throws BusinessException {
        String node = EsignValues.text(request, "node_code"), button = EsignValues.text(request, "button_code");
        EsignValues.require(MaterialAssignmentResult.isRoute(node, button, EsignValues.text(request, "operation_type"))
            && OperationContext.OWNER_POLICY_PROTOCOL.equals(request.get("policy_mode")), "请求不是物料分配。");
        EsignPermission.requireBusiness(identity, node, button, EsignValues.text(request, "pk_org"));
    }

    private static Map<String,Object> running(QdEsignStore store, ServerIdentity identity, String id, String token)
            throws BusinessException {
        Map<String,Object> request = SignatureServiceImpl.owned(store, id, identity, true);
        EsignValues.require("RUNNING".equals(request.get("business_state")) && token != null
            && token.equals(request.get("execution_token")), "物料分配请求已结束或执行令牌无效。");
        return request;
    }

    private static void validate(QdEsignStore store, ServerIdentity identity, Map<String,Object> request,
            List<Map<String,Object>> objects, boolean lock) throws BusinessException {
        Map<String,Object> mapping = store.loadNodeByAction(EsignValues.text(request, "node_code"), "Assign");
        EsignValues.require(mapping != null && MaterialAssignmentResult.SCOPE.equals(mapping.get("group_scope_id"))
            && IMaterialAssignmentService.class.getName().equals(mapping.get("adapter_class"))
            && EsignValues.yes(mapping, "validated"), "物料分配入口尚未启用。");
        PolicyDecision policy = PolicyServiceImpl.validateOwnerRequest(store, identity, request, mapping, objects, lock);
        EsignValues.require(!policy.isSignatureRequired() || "SUCCESS".equals(request.get("signature_state")),
            "物料分配签名状态无效。");
        EsignValues.require(!policy.isReasonRequired() || EsignValues.text(request, "reason") != null, "请填写物料分配事由。");
    }

}
