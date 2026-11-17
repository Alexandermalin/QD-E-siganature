package nc.impl.uapbd.qd.esign;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import nc.bs.framework.common.NCLocator;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.bs.uapbd.qd.esign.core.NativeTransactionBoundaryProbe;
import nc.bs.uapbd.qd.esign.core.TransactionBoundaryProbe;
import nc.bs.uapbd.qd.esign.core.ObjectRelations;
import nc.bs.uapbd.qd.esign.persistence.QdEsignStore;
import nc.bs.uapbd.qd.esign.security.ServerIdentity;
import nc.itf.uapbd.qd.esign.IMaterialPageDeleteService;
import nc.itf.uapbd.qd.esign.IMaterialPageDeleteTransactionService;
import nc.itf.uapbd.qd.esign.IMaterialPageDeleteTransactionService.Claim;
import nc.itf.uapbd.qd.esign.IMaterialPageDeleteTransactionService.UnitKey;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.MaterialPageDeleteCommand;
import nc.vo.uapbd.qd.esign.MaterialPageDeleteResult;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.WarehouseDeleteCommand;

/** Runs each native page delete in its own CMT and retains successful, failed and uncertain units. */
public final class MaterialPageDeleteServiceImpl implements IMaterialPageDeleteService {
    private static final Logger LOG=Logger.getLogger(MaterialPageDeleteServiceImpl.class.getName());

    public MaterialPageDeleteResult execute(String id,MaterialPageDeleteCommand command) throws BusinessException {
        NativeTransactionBoundaryProbe boundary=new NativeTransactionBoundaryProbe();boundary.assertNoTransaction();
        QdEsignStore store=new QdEsignStore();ServerIdentity identity=ServerIdentity.current();
        Map<String,Object> request=SignatureServiceImpl.owned(store,id,identity,false);
        MaterialPageDeleteTransactionServiceImpl.requireRoute(request,identity);
        if (!"READY".equals(request.get("business_state"))) {
            EsignValues.require(!"PENDING".equals(request.get("business_state")),"请先完成签名或填写事由。");
            return getResult(id);
        }
        List<Map<String,Object>> objects=store.loadObjects(id);
        MaterialPageDeleteTransactionServiceImpl.requireObjectCount(request,objects);
        MaterialPageDeleteTransactionServiceImpl.orderWarehouseObjects(request,objects);
        boolean warehouse=MaterialPageDeleteResult.isWarehouse(EsignValues.text(request,"node_code"),
            EsignValues.text(request,"button_code"),EsignValues.text(request,"operation_type"));
        boolean partner=MaterialPageDeleteResult.isPartnerMain(EsignValues.text(request,"node_code"),
            EsignValues.text(request,"button_code"),EsignValues.text(request,"operation_type"));
        List<Map<String,Object>> selected=partner?partnerRoots(request,objects):objects;
        requireCommand(selected,command,warehouse);
        if(warehouse) requireWarehouseCommands(request,objects,command);
        if(MaterialPageDeleteResult.isMaterialMain(EsignValues.text(request,"node_code"),
                EsignValues.text(request,"button_code"),EsignValues.text(request,"operation_type")))
            requireMaterialCommands(objects,command);
        if(partner) requirePartnerCommands(request,selected,command);
        IMaterialPageDeleteTransactionService service=NCLocator.getInstance().lookup(IMaterialPageDeleteTransactionService.class);
        Claim claim=service.claim_RequiresNew(id);
        boundary.assertNoTransaction();
        if (claim==null) return getResult(id);
        dispatchUnits(service,id,claim,command,warehouse,boundary);
        return getResult(id);
    }

    /** Validate the complete claimed set before invoking any independently committed unit. */
    static void dispatchUnits(IMaterialPageDeleteTransactionService service,String id,Claim claim,MaterialPageDeleteCommand command)
            throws BusinessException {
        dispatchUnits(service,id,claim,command,false,new NativeTransactionBoundaryProbe());
    }
    static void dispatchUnits(IMaterialPageDeleteTransactionService service,String id,Claim claim,MaterialPageDeleteCommand command,
            boolean stopOnFailure) throws BusinessException {
        dispatchUnits(service,id,claim,command,stopOnFailure,new NativeTransactionBoundaryProbe());
    }
    static void dispatchUnits(IMaterialPageDeleteTransactionService service,String id,Claim claim,MaterialPageDeleteCommand command,
            boolean stopOnFailure,TransactionBoundaryProbe boundary) throws BusinessException {
        EsignValues.require(service!=null && id!=null && claim!=null && claim.getToken()!=null
            && !claim.getToken().trim().isEmpty() && claim.getUnits()!=null && command!=null && command.getRows()!=null && boundary!=null,
            "档案页签删除认领结果不完整，请按请求号核对。");
        Set<String> objects=new HashSet<String>(),clients=new HashSet<String>();
        for(UnitKey unit:claim.getUnits()) {
            EsignValues.require(unit!=null && unit.getObjectId()!=null && unit.getClientObjectId()!=null
                && objects.add(unit.getObjectId()) && clients.add(unit.getClientObjectId())
                && command.getRows().get(unit.getClientObjectId())!=null,"档案页签删除认领对象不一致，请按请求号核对。");
        }
        EsignValues.require(!clients.isEmpty() && clients.equals(command.getRows().keySet()),
            "档案页签删除认领范围不完整，请按请求号核对。");
        List<UnitKey> ordered=new ArrayList<UnitKey>(claim.getUnits());
        if(stopOnFailure) {
            java.util.Iterator<String> order=command.getRows().keySet().iterator();
            for(UnitKey unit:ordered) EsignValues.require(order.hasNext() && unit.getClientObjectId().equals(order.next()),
                "仓库删除顺序与授权时的选择不一致，请重新发起。");
            EsignValues.require(!order.hasNext(),"仓库删除顺序不完整。");
        }
        boolean stopped=false;boundary.assertNoTransaction();
        for (UnitKey unit:ordered) {
            if(stopped) { service.skip_RequiresNew(id,claim.getToken(),unit.getObjectId());boundary.assertNoTransaction();continue; }
            try {
                service.apply_RequiresNew(id,claim.getToken(),unit.getObjectId(),command.getRows().get(unit.getClientObjectId()));
                boundary.assertNoTransaction();
                service.verifyUnit_RequiresNew(id,claim.getToken(),unit.getObjectId());
                boundary.assertNoTransaction();
            }
            catch (BusinessException | RuntimeException failure) {
                LOG.log(Level.WARNING,"Material page delete unit requires reconciliation. Request "+id
                    +", object "+unit.getObjectId(),failure);
                try {
                    boundary.assertNoTransaction();service.recordUncommitted_RequiresNew(id,claim.getToken(),unit.getObjectId(),false);
                    boundary.assertNoTransaction();
                } catch(BusinessException | RuntimeException recording) {
                    if(recording!=failure)failure.addSuppressed(recording);
                    if(failure instanceof BusinessException)throw (BusinessException)failure;
                    throw (RuntimeException)failure;
                }
                if(stopOnFailure) stopped=true;
            }
        }
        service.finish_RequiresNew(id,claim.getToken());boundary.assertNoTransaction();
    }

    static void requireCommand(List<Map<String,Object>> objects, MaterialPageDeleteCommand command) throws BusinessException {
        requireCommand(objects,command,false);
    }
    static void requireCommand(List<Map<String,Object>> objects, MaterialPageDeleteCommand command,boolean ordered) throws BusinessException {
        Set<String> expected=new HashSet<String>();
        for (Map<String,Object> object:objects) expected.add(EsignValues.text(object,"client_object_id"));
        EsignValues.require(command!=null && command.getRows()!=null && expected.size()==objects.size()
            && expected.equals(command.getRows().keySet()) && !command.getRows().containsValue(null),
            "档案页签删除数据与授权范围不一致。");
        if(ordered) {
            java.util.Iterator<String> keys=command.getRows().keySet().iterator();
            for(Map<String,Object> object:objects) EsignValues.require(keys.hasNext()
                && EsignValues.text(object,"client_object_id").equals(keys.next()),"仓库删除顺序与授权时的选择不一致，请重新发起。");
            EsignValues.require(!keys.hasNext(),"仓库删除顺序不完整。");
        }
    }

    /** Reject a malformed or foreign row before claiming or committing any warehouse in the batch. */
    static void requireWarehouseCommands(Map<String,Object> request,List<Map<String,Object>> objects,MaterialPageDeleteCommand command)
            throws BusinessException {
        for(Map<String,Object> object:objects) {
            java.util.List<nc.vo.uapbd.qd.esign.BusinessObjectRef> refs=ObjectRelations.restore(java.util.Collections.singletonList(object));
            String clientId=EsignValues.text(object,"client_object_id");Object raw=command.getRows().get(clientId);
            EsignValues.require(raw instanceof nc.vo.bd.stordoc.StordocVO,"仓库删除数据类型不正确。");
            OperationContext context=new OperationContext(EsignValues.text(request,"request_id"),EsignValues.text(request,"batch_id"),
                EsignValues.text(request,"pk_group"),EsignValues.text(request,"pk_org"),EsignValues.text(request,"node_code"),
                EsignValues.text(request,"node_name"),EsignValues.text(request,"button_code"),EsignValues.text(request,"button_name"),
                EsignValues.text(request,"operation_type"),EsignValues.text(request,"reason"),refs,OperationContext.OWNER_POLICY_PROTOCOL);
            new WarehouseDeleteAdapter().validate(context,new WarehouseDeleteCommand((nc.vo.bd.stordoc.StordocVO)raw));
        }
    }

    /** Validate the whole payload before claiming any independently committed material unit. */
    static void requireMaterialCommands(List<Map<String,Object>> objects,MaterialPageDeleteCommand command) throws BusinessException {
        for(Map<String,Object> object:objects) {
            Object value=command.getRows().get(EsignValues.text(object,"client_object_id"));
            EsignValues.require(value!=null && value.getClass()==nc.vo.bd.material.MaterialVO.class
                && EsignValues.text(object,"object_pk")!=null
                && EsignValues.text(object,"object_pk").equals(((nc.vo.bd.material.MaterialVO)value).getPrimaryKey()),
                "物料删除数据与授权档案不一致。");
        }
    }

    static List<Map<String,Object>> partnerRoots(Map<String,Object> request,List<Map<String,Object>> objects) throws BusinessException {
        String domain=nc.bs.uapbd.qd.esign.core.PartnerMainDeleteRoute.domain(EsignValues.text(request,"node_code"));
        List<Map<String,Object>> result=new ArrayList<Map<String,Object>>();
        for(nc.bs.uapbd.qd.esign.core.PartnerMainDeleteUnits.Unit unit:
                nc.bs.uapbd.qd.esign.core.PartnerMainDeleteUnits.group(objects,domain)) result.add(unit.getRoot());
        return result;
    }
    static void requirePartnerCommands(Map<String,Object> request,List<Map<String,Object>> roots,MaterialPageDeleteCommand command)
            throws BusinessException {
        String domain=nc.bs.uapbd.qd.esign.core.PartnerMainDeleteRoute.domain(EsignValues.text(request,"node_code"));
        Class<?> expected="BD_CUSTOMER".equals(domain)?nc.vo.bd.cust.CustomerVO.class:nc.vo.bd.supplier.SupplierVO.class;
        for(Map<String,Object> root:roots) {
            Object value=command.getRows().get(EsignValues.text(root,"client_object_id"));
            EsignValues.require(value!=null && value.getClass()==expected && value instanceof nc.vo.pub.SuperVO,
                "客户或供应商删除数据类型不正确。");
            nc.vo.pub.SuperVO row=(nc.vo.pub.SuperVO)value;
            String group=EsignValues.text(request,"pk_group"),owner=EsignValues.text(root,"policy_owner_org");
            EsignValues.require(EsignValues.text(root,"object_pk").equals(row.getPrimaryKey())
                && group!=null && group.equals(row.getAttributeValue("pk_group"))
                && owner!=null && owner.equals(row.getAttributeValue("pk_org")),
                "客户或供应商删除数据与授权档案不一致。");
        }
    }

    public MaterialPageDeleteResult getResult(String id) throws BusinessException {
        QdEsignStore store=new QdEsignStore();ServerIdentity identity=ServerIdentity.current();
        Map<String,Object> request=SignatureServiceImpl.owned(store,id,identity,false);
        MaterialPageDeleteTransactionServiceImpl.requireRoute(request,identity);
        List<Map<String,Object>> objects=store.loadPageDeleteResultObjects(request);
        MaterialPageDeleteTransactionServiceImpl.requireObjectCount(request,objects);
        if(MaterialPageDeleteResult.isPartnerMain(EsignValues.text(request,"node_code"),EsignValues.text(request,"button_code"),
                EsignValues.text(request,"operation_type"))) objects=partnerRoots(request,objects);
        List<MaterialPageDeleteResult.Unit> units=new ArrayList<MaterialPageDeleteResult.Unit>();
        for (Map<String,Object> object:objects) units.add(new MaterialPageDeleteResult.Unit(
            EsignValues.text(object,"client_object_id"),EsignValues.text(object,"object_pk"),
            EsignValues.text(object,"bill_code"),EsignValues.text(object,"target_org"),
            EsignValues.text(object,"unit_state"),EsignValues.text(object,"unit_error_code")));
        return new MaterialPageDeleteResult(id,EsignValues.text(request,"business_state"),units);
    }
}
