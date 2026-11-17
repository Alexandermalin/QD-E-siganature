package nc.impl.uapbd.qd.esign;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.transaction.TransactionSynchronizationRegistry;
import nc.bs.uapbd.qd.esign.core.SupplierAssignmentRequestScope;
import nc.bs.uapbd.qd.esign.core.SupplierAssignmentRoute;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.bs.uapbd.qd.esign.core.NativeTransactionBoundaryProbe;
import nc.bs.uapbd.qd.esign.core.ObjectRelations;
import nc.bs.uapbd.qd.esign.core.RequestRules;
import nc.bs.uapbd.qd.esign.core.TransactionRollback;
import nc.bs.uapbd.qd.esign.persistence.QdEsignStore;
import nc.bs.uapbd.qd.esign.security.EsignPermission;
import nc.bs.uapbd.qd.esign.security.ServerIdentity;
import nc.itf.uapbd.qd.esign.ISupplierAssignmentTransactionService;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.SupplierAssignmentResult;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.PolicyDecision;

/** Candidate transaction boundary; no node is enabled until real NC/Oracle validation. */
public final class SupplierAssignmentTransactionServiceImpl implements ISupplierAssignmentTransactionService {
    public Claim claim_RequiresNew(String id) throws BusinessException {
        QdEsignStore store=new QdEsignStore();ServerIdentity identity=ServerIdentity.current();
        Map<String,Object> request=SignatureServiceImpl.owned(store,id,identity,false);requireRoute(request,identity);
        if(!"READY".equals(request.get("business_state"))) {
            EsignValues.require(!"PENDING".equals(request.get("business_state")),"请先完成供应商分配签名或事由确认。");return null;
        }
        request=SignatureServiceImpl.owned(store,id,identity,true);
        if(!"READY".equals(request.get("business_state"))) return null;
        List<Map<String,Object>> objects=store.loadObjects(id);requireCount(request,objects);
        Map<String,Object> mapping=mapping(store,request);
        PolicyDecision policy=PolicyServiceImpl.validateOwnerRequest(store,identity,request,mapping,objects,false);
        requireReady(request,policy);
        SupplierAssignmentRequestScope.Expanded scope=PolicyServiceImpl.verifySupplierAssignmentScope(store,identity,request,
            mapping,ObjectRelations.restore(objects));
        Map<String,String> objectIds=new HashMap<String,String>();
        for(Map<String,Object> object:objects) {
            EsignValues.require("PENDING".equals(object.get("unit_state")) && objectIds.put(
                EsignValues.text(object,"client_object_id"),EsignValues.text(object,"object_id"))==null,"供应商分配授权对象已使用或重复。");
        }
        List<String> ordered=new ArrayList<String>();
        for(BusinessObjectRef ref:scope.getUnits()) {
            String objectId=objectIds.remove(ref.getClientObjectId());EsignValues.require(objectId!=null,"供应商分配授权对象缺失。");
            ordered.add(objectId);
        }
        EsignValues.require(objectIds.isEmpty(),"供应商分配授权对象多于已确认范围。");
        String token=EsignValues.id();store.updateRequest(id,EsignValues.row("business_state","RUNNING","execution_token",token));
        return new Claim(token,ordered);
    }

    public void apply_RequiresNew(String id,String token,String objectId) throws BusinessException {
        TransactionSynchronizationRegistry transaction=NativeTransactionBoundaryProbe.requireActiveTransaction();
        Object transactionKey=transaction.getTransactionKey();QdEsignStore store=new QdEsignStore();
        ServerIdentity identity=ServerIdentity.current();Map<String,Object> request=running(store,identity,id,token);
        Map<String,Object> unit=store.loadAssignmentUnit(id,objectId);
        EsignValues.require(unit!=null,"找不到供应商分配目标。");if(!"PENDING".equals(unit.get("unit_state"))) return;
        try {
            List<Map<String,Object>> objects=store.loadObjects(id);requireCount(request,objects);Map<String,Object> mapping=mapping(store,request);
            SupplierAssignmentRequestScope.Expanded scope=PolicyServiceImpl.verifySupplierAssignmentScope(store,identity,request,
                mapping,ObjectRelations.restore(objects));
            PolicyDecision policy=PolicyServiceImpl.validatePartnerAssignmentUnit(store,identity,request,mapping,unit,true);
            requireReady(request,policy);
            String state=new SupplierAssignmentNative().apply(request,unit,scope.getRouteFingerprint(EsignValues.text(unit,"target_org")));
            EsignValues.require(transactionKey.equals(transaction.getTransactionKey()) && !transaction.getRollbackOnly(),
                "供应商分配提交事务已变化或标记回滚。");
            store.finishAssignmentUnit(id,objectId,state,null);
            NativeTransactionBoundaryProbe.requireSingleDataSource(transaction,transactionKey);
        } catch(BusinessException failure) { throw TransactionRollback.abort(transaction,transactionKey,failure); }
        catch(RuntimeException failure) { throw TransactionRollback.abort(transaction,transactionKey,failure); }
    }
    public void verifyUnit_RequiresNew(String id,String token,String objectId) throws BusinessException {
        TransactionSynchronizationRegistry tx=NativeTransactionBoundaryProbe.requireActiveTransaction();Object key=tx.getTransactionKey();
        QdEsignStore store=new QdEsignStore();Map<String,Object> request=running(store,ServerIdentity.current(),id,token);
        AssignmentUnitVerification.require(store,request,store.loadAssignmentUnit(id,objectId),"SUCCESS",
            "ASSIGN".equals(EsignValues.text(request,"operation_type"))?"ALREADY_ASSIGNED":"ALREADY_UNASSIGNED");
        EsignValues.require(key.equals(tx.getTransactionKey()) && !tx.getRollbackOnly(),"供应商分配结果核对事务不可用。");
    }
    public void recordUncommitted_RequiresNew(String id,String token,String objectId,boolean rolledBack) throws BusinessException {
        QdEsignStore store=new QdEsignStore();running(store,ServerIdentity.current(),id,token);
        Map<String,Object> unit=store.loadAssignmentUnit(id,objectId);EsignValues.require(unit!=null,"找不到供应商分配目标。");
        if("PENDING".equals(unit.get("unit_state"))) store.finishAssignmentUnit(id,objectId,
            rolledBack?"FAILED":"UNKNOWN",rolledBack?"ASSIGNMENT_ROLLED_BACK":"ASSIGNMENT_OUTCOME_UNKNOWN");
    }
    public void finish_RequiresNew(String id,String token) throws BusinessException {
        QdEsignStore store=new QdEsignStore();Map<String,Object> request=running(store,ServerIdentity.current(),id,token);
        List<Map<String,Object>> objects=store.loadObjects(id);requireCount(request,objects);String state=summarize(objects);
        store.updateRequest(id,EsignValues.row("business_state",state,"execution_token",null,
            "finished_at","UNKNOWN".equals(state)?null:EsignValues.utc(),
            "error_code","UNKNOWN".equals(state)?"ASSIGNMENT_OUTCOME_UNKNOWN":null));
    }
    static String summarize(List<Map<String,Object>> units) {
        boolean success=false,failed=false;
        for(Map<String,Object> unit:units) {
            Object state=unit.get("unit_state");
            if("SUCCESS".equals(state) || "ALREADY_ASSIGNED".equals(state) || "ALREADY_UNASSIGNED".equals(state)) success=true;
            else if("FAILED".equals(state)) failed=true;else return "UNKNOWN";
        }
        return failed?(success?"PARTIAL":"FAILED"):"SUCCESS";
    }
    static void requireCount(Map<String,Object> request,List<Map<String,Object>> objects) throws BusinessException {
        EsignValues.require(objects!=null && !objects.isEmpty() && objects.size()==EsignValues.number(request,"owner_object_count"),
            "供应商分配授权对象清单不完整，请按请求号核对。");
    }
    static void requireRoute(Map<String,Object> request,ServerIdentity identity) throws BusinessException {
        String node=EsignValues.text(request,"node_code"),button=EsignValues.text(request,"button_code");
        EsignValues.require(SupplierAssignmentResult.isRoute(node,button,EsignValues.text(request,"operation_type"))
            && OperationContext.OWNER_POLICY_PROTOCOL.equals(request.get("policy_mode")),"请求不是供应商分配或取消分配。");
        EsignPermission.requireBusiness(identity,node,button,EsignValues.text(request,"pk_org"));
    }
    private static Map<String,Object> mapping(QdEsignStore store,Map<String,Object> request) throws BusinessException {
        Map<String,Object> mapping=store.loadNodeByAction(EsignValues.text(request,"node_code"),EsignValues.text(request,"button_code"));
        SupplierAssignmentRoute.requireMapping(mapping,EsignValues.text(request,"operation_type"));return mapping;
    }
    private static Map<String,Object> running(QdEsignStore store,ServerIdentity identity,String id,String token) throws BusinessException {
        Map<String,Object> request=SignatureServiceImpl.owned(store,id,identity,true);requireRoute(request,identity);
        EsignValues.require("RUNNING".equals(request.get("business_state")) && token!=null && token.equals(request.get("execution_token")),
            "供应商分配请求已结束或执行令牌无效。");return request;
    }
    private static void requireReady(Map<String,Object> request,PolicyDecision policy) throws BusinessException {
        EsignValues.require(policy!=null && (!policy.isSignatureRequired() || "SUCCESS".equals(request.get("signature_state")))
            && (!policy.isReasonRequired() || EsignValues.text(request,"reason")!=null),"供应商分配签名或事由状态无效。");
        EsignValues.require(RequestRules.mayExecute(EsignValues.text(request,"signature_state"),"READY")
            && System.currentTimeMillis()<=EsignValues.parseUtc(EsignValues.text(request,"expires_at")),"供应商分配授权已失效或过期。");
    }
}
