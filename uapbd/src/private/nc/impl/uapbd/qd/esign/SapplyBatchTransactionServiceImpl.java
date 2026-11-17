package nc.impl.uapbd.qd.esign;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.transaction.TransactionSynchronizationRegistry;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.bs.uapbd.qd.esign.core.NativeTransactionBoundaryProbe;
import nc.bs.uapbd.qd.esign.core.RequestRules;
import nc.bs.uapbd.qd.esign.core.TransactionRollback;
import nc.bs.uapbd.qd.esign.persistence.QdEsignStore;
import nc.bs.uapbd.qd.esign.security.EsignPermission;
import nc.bs.uapbd.qd.esign.security.ServerIdentity;
import nc.itf.uapbd.qd.esign.ISapplyBatchTransactionService;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.SapplyBatchOutcome;

/** Claims a batch once and reconciles its independently committed native units. */
public final class SapplyBatchTransactionServiceImpl implements ISapplyBatchTransactionService {
    @Override
    public String claim_RequiresNew(String id, String action, String[] billPks, String[] signedApprovalIds) throws BusinessException {
        TransactionSynchronizationRegistry tx = NativeTransactionBoundaryProbe.requireActiveTransaction();
        Object key = tx.getTransactionKey();
        try {
            QdEsignStore store = new QdEsignStore();
            ServerIdentity identity = ServerIdentity.current();
            Map<String,Object> request = SignatureServiceImpl.owned(store, id, identity, true);
            String button = EsignValues.text(request, "button_code");
            String operation = EsignValues.text(request, "operation_type");
            String org = EsignValues.text(request, "pk_org");
            requireReady(request, action, System.currentTimeMillis());
            EsignPermission.requireBusiness(identity, SapplyWorkflowBatchUnit.NODE, button, org);
            SapplyWorkflowBatchUnit.requirePolicy(
                    PolicyServiceImpl.resolveFor(identity, org, SapplyWorkflowBatchUnit.NODE, button), request);
            List<Map<String,Object>> rows = store.loadObjects(id);
            requireReceiptCount(request, rows);
            List<BusinessObjectRef> authorized = requireExactSelection(rows, billPks, identity.groupId, org);
            requireApprovalIntents(action,billPks,signedApprovalIds,authorized);
            SapplyWorkflowBatchUnit.requireMapping(store, button, operation, authorized);
            String token = EsignValues.id();
            store.updateRequest(id, EsignValues.row("business_state", "RUNNING", "execution_token", token));
            NativeTransactionBoundaryProbe.requireSingleDataSource(tx, key);
            return token;
        } catch (BusinessException failure) {
            throw TransactionRollback.abort(tx, key, failure);
        } catch (RuntimeException failure) {
            throw TransactionRollback.abort(tx, key, failure);
        }
    }

    @Override
    public void finish_RequiresNew(String id, String token, String[] nativeRejectedPks,
            String[] precheckSkippedPks) throws BusinessException {
        TransactionSynchronizationRegistry tx = NativeTransactionBoundaryProbe.requireActiveTransaction();
        Object key = tx.getTransactionKey();
        try {
            QdEsignStore store = new QdEsignStore();
            ServerIdentity identity = ServerIdentity.current();
            Map<String,Object> request = SignatureServiceImpl.owned(store, id, identity, true);
            SapplyWorkflowBatchUnit.requireRunning(request, token);
            String org = EsignValues.text(request, "pk_org");
            EsignPermission.requireBusiness(identity, SapplyWorkflowBatchUnit.NODE,
                    EsignValues.text(request, "button_code"), org);
            List<Map<String,Object>> rows = store.loadObjects(id);
            requireReceiptCount(request, rows);
            RejectionSets decisions=requireRejections(rows,nativeRejectedPks,precheckSkippedPks,identity.groupId,org);
            for (Map<String,Object> row : rows) {
                String pk = EsignValues.text(row, "object_pk");
                if ("PENDING".equals(EsignValues.text(row,"unit_state"))) {
                    boolean nativeRejected = decisions.rejected.contains(pk), precheckSkipped = decisions.skipped.contains(pk);
                    String state = nativeRejected || precheckSkipped ? "FAILED" : "UNKNOWN";
                    String code = precheckSkipped ? "CLIENT_PRECHECK_SKIPPED"
                            : nativeRejected ? "PF_UNIT_REJECTED" : "PF_UNIT_OUTCOME_UNKNOWN";
                    store.finishAssignmentUnit(id, EsignValues.text(row, "object_id"), state, code);
                }
            }
            SapplyBatchOutcome outcome = summarize(id, store.loadObjects(id), identity.groupId, org);
            boolean unknown = "UNKNOWN".equals(outcome.getState());
            store.updateRequest(id, EsignValues.row("business_state", outcome.getState(), "execution_token", null,
                    "finished_at", unknown ? null : EsignValues.utc(),
                    "error_code", unknown ? "BUSINESS_OUTCOME_UNKNOWN" : null));
            NativeTransactionBoundaryProbe.requireSingleDataSource(tx, key);
        } catch (BusinessException failure) {
            throw TransactionRollback.abort(tx, key, failure);
        } catch (RuntimeException failure) {
            throw TransactionRollback.abort(tx, key, failure);
        }
    }

    @Override
    public SapplyBatchOutcome verifyFinished_RequiresNew(String id) throws BusinessException {
        TransactionSynchronizationRegistry tx = NativeTransactionBoundaryProbe.requireActiveTransaction();
        Object key = tx.getTransactionKey();
        QdEsignStore store = new QdEsignStore();
        ServerIdentity identity = ServerIdentity.current();
        Map<String,Object> request = SignatureServiceImpl.owned(store, id, identity, false);
        String org = EsignValues.text(request, "pk_org");
        EsignValues.require(SapplyWorkflowBatchUnit.POLICY_MODE.equals(request.get("policy_mode"))
                && SapplyWorkflowBatchUnit.NODE.equals(request.get("node_code")) && request.get("execution_token") == null,
                "出库申请批量请求尚未完成，请按请求号核对。");
        EsignPermission.requireBusiness(identity, SapplyWorkflowBatchUnit.NODE,
                EsignValues.text(request, "button_code"), org);
        List<Map<String,Object>> rows = store.loadSapplyBatchResultObjects(request);
        requireReceiptCount(request, rows);
        for (Map<String,Object> row : rows) {
            EsignValues.require(!"PENDING".equals(row.get("unit_state")), "出库申请批量回执尚未提交。");
        }
        SapplyBatchOutcome outcome = summarize(id, rows, identity.groupId, org);
        EsignValues.require(outcome.getState().equals(request.get("business_state"))
                && ("UNKNOWN".equals(outcome.getState()) == (request.get("finished_at") == null)),
                "出库申请批量回执与已提交状态不一致，请按请求号核对。");
        EsignValues.require(key.equals(tx.getTransactionKey()) && !tx.getRollbackOnly(),
                "出库申请批量回执读取事务已变化。");
        return outcome;
    }

    private static void requireReceiptCount(Map<String,Object> request, List<Map<String,Object>> rows)
            throws BusinessException {
        EsignValues.require(rows != null && rows.size() == EsignValues.number(request, "owner_object_count"),
                "出库申请批量回执单据数量与已认领范围不符。");
    }

    static void requireReady(Map<String,Object> request, String action, long now) throws BusinessException {
        String button=EsignValues.text(request,"button_code"),operation=EsignValues.text(request,"operation_type");
        String expires = EsignValues.text(request, "expires_at");
        EsignValues.require(SapplyWorkflowBatchUnit.POLICY_MODE.equals(request.get("policy_mode"))
                && SapplyWorkflowBatchUnit.NODE.equals(request.get("node_code"))
                && !QdEsignStore.isArchivedResult(request) && request.get("execution_token") == null
                && request.get("finished_at") == null
                && RequestRules.mayExecute(EsignValues.text(request, "signature_state"),
                        EsignValues.text(request, "business_state"))
                && SapplyWorkflowBatchUnit.matchesNativeAction(button,operation,action,EsignValues.text(request,"user_id"))
                && expires != null && now <= EsignValues.parseUtc(expires),
                "出库申请批量请求尚未授权、已失效或与原生动作不一致。");
    }

    static List<BusinessObjectRef> requireExactSelection(List<Map<String,Object>> rows, String[] selected,
            String group, String org) throws BusinessException {
        EsignValues.require(rows != null && selected != null && selected.length >= 2 && rows.size() == selected.length,
                "出库申请批量所选单据与签名数量不一致。");
        Set<String> remaining = new HashSet<String>();
        for (String pk : selected) {
            EsignValues.require(pk != null && pk.equals(SapplyWorkflowBatchUnit.key(pk)) && remaining.add(pk),
                    "出库申请批量所选单据主键无效或重复。");
        }
        List<BusinessObjectRef> refs = new ArrayList<BusinessObjectRef>(rows.size());
        for (Map<String,Object> row : rows) {
            String pk = EsignValues.text(row, "object_pk");
            EsignValues.require(remaining.remove(pk), "出库申请批量所选单据与签名范围不一致。");
            refs.add(SapplyWorkflowBatchUnit.requirePendingBill(row, group, org, pk));
        }
        return refs;
    }

    static void requireApprovalIntents(String action,String[] selected,String[] ids,List<BusinessObjectRef> refs)
            throws BusinessException {
        boolean approval=nc.vo.ic.qd.esign.SapplyNativeAction.approvalButton(action)!=null;
        if(!approval){EsignValues.require(ids==null,"非审批批量不能携带审批授权。");return;}
        EsignValues.require(selected!=null && ids!=null && refs!=null && selected.length==ids.length
                && refs.size()==selected.length,"出库申请批量审批签名范围不完整。");
        Map<String,String> expected=new java.util.HashMap<String,String>();
        for(BusinessObjectRef ref:refs)EsignValues.require(ref!=null && ref.getObjectPk()!=null
                && expected.put(ref.getObjectPk(),ref.getClientObjectId())==null,"出库申请批量审批单据重复。");
        for(int index=0;index<selected.length;index++)
            EsignValues.require(ids[index]!=null && ids[index].equals(expected.remove(selected[index])),
                    "出库申请批量审批内容与签名授权不一致。");
        EsignValues.require(expected.isEmpty(),"出库申请批量审批签名范围不一致。");
    }

    static RejectionSets requireRejections(List<Map<String,Object>> rows,String[] nativeRejectedPks,
            String[] precheckSkippedPks,String group,String org) throws BusinessException {
        EsignValues.require(rows!=null && !rows.isEmpty() && nativeRejectedPks!=null && precheckSkippedPks!=null,
                "出库申请批量拒绝清单缺失。");
        Set<String> rejected=new HashSet<String>(java.util.Arrays.asList(nativeRejectedPks));
        Set<String> skipped=new HashSet<String>(java.util.Arrays.asList(precheckSkippedPks));
        EsignValues.require(rejected.size()==nativeRejectedPks.length && skipped.size()==precheckSkippedPks.length
                && java.util.Collections.disjoint(rejected,skipped),"出库申请批量拒绝清单重复或冲突。");
        Set<String> authorized=new HashSet<String>();
        for(Map<String,Object> row:rows) {
            EsignValues.require(row!=null,"出库申请批量回执缺失。");
            String pk=EsignValues.text(row,"object_pk");
            requireUniqueBill(row,group,org,pk,authorized);
            String state=EsignValues.text(row,"unit_state");
            if(!"PENDING".equals(state)) {
                EsignValues.require("SUCCESS".equals(state) || "FAILED".equals(state) || "UNKNOWN".equals(state),
                        "出库申请批量回执状态无效。");
                EsignValues.require(row.get("unit_finished_at")!=null,"出库申请批量回执缺少完成时间。");
                EsignValues.require(!skipped.contains(pk) && !rejected.contains(pk),
                        "出库申请已执行单据不能标记为执行前拒绝。");
            }
        }
        EsignValues.require(authorized.containsAll(rejected) && authorized.containsAll(skipped),
                "NC 出库申请批量拒绝结果超出授权范围。");
        return new RejectionSets(rejected,skipped);
    }

    static final class RejectionSets {
        final Set<String> rejected,skipped;
        RejectionSets(Set<String> rejected,Set<String> skipped) {
            this.rejected=java.util.Collections.unmodifiableSet(new HashSet<String>(rejected));
            this.skipped=java.util.Collections.unmodifiableSet(new HashSet<String>(skipped));
        }
    }

    static SapplyBatchOutcome summarize(String id, List<Map<String,Object>> rows, String group, String org)
            throws BusinessException {
        EsignValues.require(rows != null && rows.size() >= 2, "出库申请批量回执范围不完整。");
        List<SapplyBatchOutcome.Unit> units = new ArrayList<SapplyBatchOutcome.Unit>(rows.size());
        Set<String> pks = new HashSet<String>();
        boolean success = false, failed = false, unknown = false;
        for (Map<String,Object> row : rows) {
            String pk = EsignValues.text(row, "object_pk");
            BusinessObjectRef bill = requireUniqueBill(row, group, org, pk, pks);
            String state = EsignValues.text(row, "unit_state");
            String code = EsignValues.text(row, "unit_error_code");
            if ("PENDING".equals(state)) {
                state = "UNKNOWN";
                code = "PF_UNIT_OUTCOME_UNKNOWN";
            } else {
                EsignValues.require(row.get("unit_finished_at") != null, "出库申请批量回执缺少完成时间。");
            }
            if ("SUCCESS".equals(state)) success = true;
            else if ("FAILED".equals(state)) failed = true;
            else if ("UNKNOWN".equals(state)) unknown = true;
            else throw new BusinessException("出库申请批量回执状态无效。");
            units.add(new SapplyBatchOutcome.Unit(bill, state, code));
        }
        String state = "SUCCESS";
        if (unknown) state = "UNKNOWN";
        else if (failed) state = success ? "PARTIAL" : "FAILED";
        return new SapplyBatchOutcome(id, state, units);
    }

    private static BusinessObjectRef requireUniqueBill(Map<String,Object> row, String group, String org,
            String pk, Set<String> pks) throws BusinessException {
        BusinessObjectRef bill = SapplyWorkflowBatchUnit.requireBill(row, group, org, pk);
        EsignValues.require(pks.add(pk), "出库申请批量回执重复。");
        return bill;
    }
}
