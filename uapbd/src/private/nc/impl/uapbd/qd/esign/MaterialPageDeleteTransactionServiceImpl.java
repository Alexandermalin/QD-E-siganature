package nc.impl.uapbd.qd.esign;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.transaction.TransactionSynchronizationRegistry;
import nc.bs.uapbd.qd.esign.audit.AuditCaptureScope;
import nc.bs.uapbd.qd.esign.audit.AuditRecorder;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.bs.uapbd.qd.esign.core.NativeTransactionBoundaryProbe;
import nc.bs.uapbd.qd.esign.core.ObjectRelations;
import nc.bs.uapbd.qd.esign.core.RequestRules;
import nc.bs.uapbd.qd.esign.core.TransactionRollback;
import nc.bs.uapbd.qd.esign.persistence.QdEsignStore;
import nc.bs.uapbd.qd.esign.security.EsignPermission;
import nc.bs.uapbd.qd.esign.security.ServerIdentity;
import nc.itf.uapbd.qd.esign.IMaterialPageDeleteService;
import nc.itf.uapbd.qd.esign.IMaterialPageDeleteTransactionService;
import nc.itf.uapbd.qd.esign.spi.BusinessOperationAdapter;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.MaterialOrgInfoCommand;
import nc.vo.uapbd.qd.esign.MaterialCommand;
import nc.vo.uapbd.qd.esign.MaterialPageDeleteResult;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.PartnerOrgInfoCommand;
import nc.vo.uapbd.qd.esign.PartnerOrgPage;
import nc.vo.uapbd.qd.esign.WarehouseDeleteCommand;
import nc.vo.uapbd.qd.esign.PolicyDecision;

/** Durable claim, independent page-row commits, and explicit unknown-outcome reconciliation. */
public final class MaterialPageDeleteTransactionServiceImpl implements IMaterialPageDeleteTransactionService {
    public Claim claim_RequiresNew(String id) throws BusinessException {
        QdEsignStore store = new QdEsignStore(); ServerIdentity identity = ServerIdentity.current();
        Map<String,Object> request = SignatureServiceImpl.owned(store,id,identity,false);
        requireRoute(request,identity);
        if (!"READY".equals(request.get("business_state"))) {
            EsignValues.require(!"PENDING".equals(request.get("business_state")), "请先完成签名或填写事由。");
            return null;
        }
        request = SignatureServiceImpl.owned(store,id,identity,true);
        if (!"READY".equals(request.get("business_state"))) return null;
        List<Map<String,Object>> objects = store.loadObjects(id);
        requireObjectCount(request,objects);
        orderWarehouseObjects(request,objects);
        validate(store,identity,request,objects,false);
        EsignValues.require(RequestRules.mayExecute(EsignValues.text(request,"signature_state"),"READY")
            && System.currentTimeMillis() <= EsignValues.parseUtc(EsignValues.text(request,"expires_at")),
            "物料页签删除授权已失效，请重新发起。");
        List<UnitKey> units = new ArrayList<UnitKey>();
        for (Map<String,Object> object : objects) {
            EsignValues.require("PENDING".equals(object.get("unit_state")), "物料页签删除请求已经使用。");
        }
        List<Map<String,Object>> roots=partnerDelete(request)?MaterialPageDeleteServiceImpl.partnerRoots(request,objects):objects;
        for(Map<String,Object> object:roots)
            units.add(new UnitKey(EsignValues.text(object,"object_id"),EsignValues.text(object,"client_object_id")));
        String token=EsignValues.id();
        store.updateRequest(id,EsignValues.row("business_state","RUNNING","execution_token",token));
        return new Claim(token,units);
    }

    public void apply_RequiresNew(String id, String token, String objectId, SuperVO row) throws BusinessException {
        TransactionSynchronizationRegistry transaction=NativeTransactionBoundaryProbe.requireActiveTransaction();
        Object transactionKey=transaction.getTransactionKey();
        QdEsignStore store=new QdEsignStore(); ServerIdentity identity=ServerIdentity.current();
        Map<String,Object> request=running(store,identity,id,token);
        Map<String,Object> object=store.loadAssignmentUnit(id,objectId);
        EsignValues.require(object!=null,"找不到本次物料页签删除行。");
        if (!"PENDING".equals(object.get("unit_state"))) return;
        try {
            boolean partnerMain=partnerDelete(request);
            List<Map<String,Object>> unit=partnerMain?partnerUnit(store,request,id,objectId):Collections.singletonList(object);
            List<BusinessObjectRef> refs=ObjectRelations.restore(unit);
            OperationContext context=new OperationContext(id,EsignValues.text(request,"batch_id"),identity.groupId,
                EsignValues.text(request,"pk_org"),EsignValues.text(request,"node_code"),EsignValues.text(request,"node_name"),
                EsignValues.text(request,"button_code"),EsignValues.text(request,"button_name"),"DELETE",
                EsignValues.text(request,"reason"),refs,OperationContext.OWNER_POLICY_PROTOCOL);
            boolean warehouse=MaterialPageDeleteResult.isWarehouse(context.getNodeCode(),context.getButtonCode(),"DELETE");
            boolean material=MaterialPageDeleteResult.isMaterialMain(context.getNodeCode(),context.getButtonCode(),"DELETE");
            if(warehouse) {
                EsignValues.require(row instanceof nc.vo.bd.stordoc.StordocVO,"仓库删除数据类型不正确。");
                EsignPermission.requireArchiveData(identity,"stordoc","delete",refs);
                WarehouseDeleteAdapter.prelock(context,new WarehouseDeleteCommand((nc.vo.bd.stordoc.StordocVO)row));
            }
            if(material) {
                EsignValues.require(row!=null && row.getClass()==nc.vo.bd.material.MaterialVO.class,"物料删除数据类型不正确。");
                EsignPermission.requireArchiveData(identity,"material","delete",refs);
                MaterialMainDeleteAdapter.prelock(context,new MaterialCommand((nc.vo.bd.material.MaterialVO)row));
            }
            if(partnerMain) {
                EsignPermission.requireArchiveData(identity,nc.bs.uapbd.qd.esign.core.PartnerMainDeleteRoute.permissionResource(context.getNodeCode()),
                    "delete",Collections.singletonList(refs.get(0)));
                PartnerMainDeleteAdapter.prelock(context,row);
            }
            validate(store,identity,request,unit,true);
            String clientId=refs.get(0).getClientObjectId();
            java.util.Set<String> audited=new java.util.HashSet<String>();
            for(Map<String,Object> member:unit) if(EsignValues.yes(member,"owner_audit_enabled"))
                audited.add(EsignValues.text(member,"client_object_id"));
            AuditCaptureScope scope=new AuditCaptureScope(context,audited);
            try {
                boolean partner=PartnerOrgPage.forAction(context.getNodeCode(),context.getButtonCode(),"DELETE")!=null;
                BusinessOperationAdapter adapter=partnerMain?new PartnerMainDeleteAdapter():material?new MaterialMainDeleteAdapter():warehouse?new WarehouseDeleteAdapter()
                    :partner?new PartnerOrgInfoOperationAdapter():new MaterialOrgInfoOperationAdapter();
                Serializable command=partnerMain?row:material?new MaterialCommand((nc.vo.bd.material.MaterialVO)row):warehouse?new WarehouseDeleteCommand((nc.vo.bd.stordoc.StordocVO)row)
                    :partner?new PartnerOrgInfoCommand(row):new MaterialOrgInfoCommand(row);
                adapter.validate(context,command);
                Serializable result=adapter.execute(context,command);
                List<BusinessObjectRef> saved=adapter.describeResult(context,result);
                EsignValues.require(saved.size()==refs.size(),"档案删除结果数量不一致。");
                java.util.Map<String,BusinessObjectRef> expected=new java.util.HashMap<String,BusinessObjectRef>();
                for(BusinessObjectRef ref:refs) expected.put(ref.getClientObjectId(),ref);
                for(BusinessObjectRef ref:saved) {
                    BusinessObjectRef prior=ref==null?null:expected.remove(ref.getClientObjectId());
                    EsignValues.require(prior!=null && prior.getObjectType().equals(ref.getObjectType())
                        && prior.getObjectPk().equals(ref.getObjectPk()),"档案删除结果与授权对象不一致。");
                }
                EsignValues.require(expected.isEmpty(),"档案删除结果缺少授权对象。");
                for(Map<String,Object> member:unit) {
                    String memberId=EsignValues.text(member,"client_object_id");
                    if(scope.isEnabledFor(memberId)) {
                        EsignValues.require(scope.hasBefore(memberId), "删除快照缺失，业务必须回滚。");
                        new AuditRecorder().record(request,member,scope.getBefore(memberId),scope.getAfter(memberId));
                    }
                }
                EsignValues.require(transactionKey.equals(transaction.getTransactionKey()) && !transaction.getRollbackOnly(),
                    "物料页签删除事务已变化或已标记回滚。");
                for(Map<String,Object> member:unit)
                    store.finishAssignmentUnit(id,EsignValues.text(member,"object_id"),"SUCCESS",null);
                NativeTransactionBoundaryProbe.requireSingleDataSource(transaction,transactionKey);
            } finally { scope.close(); }
        } catch (BusinessException failure) { throw TransactionRollback.abort(transaction,transactionKey,failure); }
        catch (RuntimeException failure) { throw TransactionRollback.abort(transaction,transactionKey,failure); }
    }

    /** Read durable unit and audit facts in a transaction distinct from the native deletion. */
    public void verifyUnit_RequiresNew(String id,String token,String objectId) throws BusinessException {
        TransactionSynchronizationRegistry transaction=NativeTransactionBoundaryProbe.requireActiveTransaction();
        Object key=transaction.getTransactionKey();QdEsignStore store=new QdEsignStore();
        Map<String,Object> request=running(store,ServerIdentity.current(),id,token),root=store.loadAssignmentUnit(id,objectId);
        EsignValues.require(root!=null,"找不到待核对的档案删除单元。");
        List<Map<String,Object>> members=Collections.singletonList(root);
        if(partnerDelete(request)) {
            List<Map<String,Object>> all=store.loadObjects(id);requireObjectCount(request,all);members=null;
            String domain=nc.bs.uapbd.qd.esign.core.PartnerMainDeleteRoute.domain(EsignValues.text(request,"node_code"));
            for(nc.bs.uapbd.qd.esign.core.PartnerMainDeleteUnits.Unit unit:
                    nc.bs.uapbd.qd.esign.core.PartnerMainDeleteUnits.group(all,domain))
                if(objectId.equals(EsignValues.text(unit.getRoot(),"object_id")))members=unit.getObjects();
        }
        EsignValues.require(members!=null && !members.isEmpty(),"档案删除的持久化单元不完整。");
        for(Map<String,Object> member:members) {
            requireCommittedMember(request,member);
            if(EsignValues.yes(member,"owner_audit_enabled")) {
                nc.jdbc.framework.SQLParameter parameters=new nc.jdbc.framework.SQLParameter();
                parameters.addParam(id);parameters.addParam(EsignValues.text(member,"object_id"));
                parameters.addParam(EsignValues.text(request,"pk_group"));parameters.addParam(EsignValues.text(request,"pk_org"));
                Map<String,Object> count=store.one("SELECT COUNT(*) audit_count FROM qd_es_audit WHERE request_id=? AND object_id=?"
                    +" AND pk_group=? AND pk_org=? AND result_code='SUCCESS'",parameters);
                EsignValues.require(count!=null && EsignValues.number(count,"audit_count")==1,"档案删除审计结果尚未确认。");
            }
        }
        EsignValues.require(key.equals(transaction.getTransactionKey()) && !transaction.getRollbackOnly(),"档案删除结果核对事务不可用。");
    }

    static void requireCommittedMember(Map<String,Object> request,Map<String,Object> member) throws BusinessException {
        String id=request==null?null:EsignValues.text(request,"request_id"),group=request==null?null:EsignValues.text(request,"pk_group"),
            org=request==null?null:EsignValues.text(request,"pk_org");
        EsignValues.require(member!=null && id!=null && group!=null && org!=null
            && "SUCCESS".equals(EsignValues.text(member,"unit_state")) && member.get("unit_finished_at")!=null
            && member.get("unit_error_code")==null && id.equals(EsignValues.text(member,"request_id"))
            && group.equals(EsignValues.text(member,"pk_group")) && org.equals(EsignValues.text(member,"pk_org")),
            "档案删除提交结果尚未确认。");
    }

    public void recordUncommitted_RequiresNew(String id,String token,String objectId,boolean rolledBack)
            throws BusinessException {
        QdEsignStore store=new QdEsignStore();Map<String,Object> request=running(store,ServerIdentity.current(),id,token);
        Map<String,Object> object=store.loadAssignmentUnit(id,objectId);
        EsignValues.require(object!=null,"找不到本次物料页签删除行。");
        if ("PENDING".equals(object.get("unit_state"))) {
            List<Map<String,Object>> members=partnerDelete(request)?partnerUnit(store,request,id,objectId):Collections.singletonList(object);
            for(Map<String,Object> member:members) {
                Map<String,Object> locked=store.loadAssignmentUnit(id,EsignValues.text(member,"object_id"));
                EsignValues.require(locked!=null && "PENDING".equals(locked.get("unit_state")),"档案删除单元状态不一致，请人工核对。");
                store.finishAssignmentUnit(id,EsignValues.text(member,"object_id"),rolledBack?"FAILED":"UNKNOWN",
                    rolledBack?"PAGE_DELETE_ROLLED_BACK":"PAGE_DELETE_OUTCOME_UNKNOWN");
            }
        }
    }

    public void skip_RequiresNew(String id,String token,String objectId) throws BusinessException {
        QdEsignStore store=new QdEsignStore();Map<String,Object> request=running(store,ServerIdentity.current(),id,token);
        EsignValues.require(MaterialPageDeleteResult.isWarehouse(EsignValues.text(request,"node_code"),
            EsignValues.text(request,"button_code"),EsignValues.text(request,"operation_type")),"只有仓库删除可以停止后续单元。");
        Map<String,Object> object=store.loadAssignmentUnit(id,objectId);
        EsignValues.require(object!=null && "PENDING".equals(object.get("unit_state")),"无法确认未执行的仓库删除单元。");
        store.finishAssignmentUnit(id,objectId,"SKIPPED","PREVIOUS_UNIT_STOPPED");
    }

    public void finish_RequiresNew(String id,String token) throws BusinessException {
        QdEsignStore store=new QdEsignStore();Map<String,Object> request=running(store,ServerIdentity.current(),id,token);
        List<Map<String,Object>> objects=store.loadObjects(id);
        requireObjectCount(request,objects);
        String state=summarize(objects);
        store.updateRequest(id,EsignValues.row("business_state",state,"execution_token",null,
            "finished_at","UNKNOWN".equals(state)?null:EsignValues.utc(),
            "error_code","UNKNOWN".equals(state)?"PAGE_DELETE_OUTCOME_UNKNOWN":null));
    }

    static void requireObjectCount(Map<String,Object> request,List<Map<String,Object>> objects) throws BusinessException {
        EsignValues.require(!objects.isEmpty() && objects.size()==EsignValues.number(request,"owner_object_count"),
            "物料页签删除对象清单不完整，请按请求号核对。");
    }

    private static boolean partnerDelete(Map<String,Object> request) {
        return MaterialPageDeleteResult.isPartnerMain(EsignValues.text(request,"node_code"),EsignValues.text(request,"button_code"),
            EsignValues.text(request,"operation_type"));
    }
    private static List<Map<String,Object>> partnerUnit(QdEsignStore store,Map<String,Object> request,String id,String objectId)
            throws BusinessException {
        List<Map<String,Object>> objects=store.loadObjects(id);requireObjectCount(request,objects);
        String domain=nc.bs.uapbd.qd.esign.core.PartnerMainDeleteRoute.domain(EsignValues.text(request,"node_code"));
        for(nc.bs.uapbd.qd.esign.core.PartnerMainDeleteUnits.Unit unit:
                nc.bs.uapbd.qd.esign.core.PartnerMainDeleteUnits.group(objects,domain))
            if(objectId.equals(EsignValues.text(unit.getRoot(),"object_id"))) {
                for(Map<String,Object> member:unit.getObjects()) EsignValues.require("PENDING".equals(member.get("unit_state")),
                    "客户或供应商删除单元状态不一致。");
                return unit.getObjects();
            }
        throw new BusinessException("客户或供应商删除选中单元不存在。");
    }

    /** Java's binary UUID ordering matches the order assigned at preparation even with a linguistic Oracle collation. */
    static void orderWarehouseObjects(Map<String,Object> request,List<Map<String,Object>> objects) throws BusinessException {
        if(!MaterialPageDeleteResult.isWarehouse(EsignValues.text(request,"node_code"),EsignValues.text(request,"button_code"),
                EsignValues.text(request,"operation_type"))) return;
        for(Map<String,Object> object:objects) EsignValues.require(EsignValues.text(object,"object_id")!=null,"仓库删除对象标识缺失。");
        Collections.sort(objects,new java.util.Comparator<Map<String,Object>>() {
            public int compare(Map<String,Object> a,Map<String,Object> b) {
                return EsignValues.text(a,"object_id").compareTo(EsignValues.text(b,"object_id"));
            }
        });
    }

    static String summarize(List<Map<String,Object>> objects) {
        boolean success=false,failed=false;
        for (Map<String,Object> object:objects) {
            Object state=object.get("unit_state");
            if ("SUCCESS".equals(state)) success=true;
            else if ("FAILED".equals(state) || "SKIPPED".equals(state)) failed=true;
            else return "UNKNOWN";
        }
        return failed?(success?"PARTIAL":"FAILED"):"SUCCESS";
    }

    static void requireRoute(Map<String,Object> request,ServerIdentity identity) throws BusinessException {
        String node=EsignValues.text(request,"node_code"),button=EsignValues.text(request,"button_code");
        EsignValues.require(MaterialPageDeleteResult.isRoute(node,button,EsignValues.text(request,"operation_type"))
            && OperationContext.OWNER_POLICY_PROTOCOL.equals(request.get("policy_mode")),"请求不是物料页签删除。");
        EsignPermission.requireBusiness(identity,node,button,EsignValues.text(request,"pk_org"));
    }

    private static Map<String,Object> running(QdEsignStore store,ServerIdentity identity,String id,String token)
            throws BusinessException {
        Map<String,Object> request=SignatureServiceImpl.owned(store,id,identity,true);
        requireRoute(request,identity);
        EsignValues.require("RUNNING".equals(request.get("business_state")) && token!=null
            && token.equals(request.get("execution_token")),"物料页签删除请求已结束或执行令牌无效。");
        return request;
    }

    private static void validate(QdEsignStore store,ServerIdentity identity,Map<String,Object> request,
            List<Map<String,Object>> objects,boolean lock) throws BusinessException {
        String node=EsignValues.text(request,"node_code"),button=EsignValues.text(request,"button_code");
        Map<String,Object> mapping=store.loadNodeByAction(node,button);
        EsignValues.require(mapping!=null && IMaterialPageDeleteService.class.getName().equals(EsignValues.text(mapping,"adapter_class"))
            && EsignValues.yes(mapping,"validated"),"物料页签删除入口尚未启用。");
        PolicyDecision policy=PolicyServiceImpl.validateOwnerRequest(store,identity,request,mapping,objects,lock);
        EsignValues.require(!policy.isSignatureRequired() || "SUCCESS".equals(request.get("signature_state")),
            "物料页签删除签名状态无效。");
        EsignValues.require(!policy.isReasonRequired() || EsignValues.text(request,"reason")!=null,"请填写删除事由。");
    }
}
