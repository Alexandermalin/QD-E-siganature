package nc.impl.uapbd.qd.esign;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import nc.bs.bd.service.ValueObjWithErrLog;
import nc.bs.dao.BaseDAO;
import nc.bs.framework.common.NCLocator;
import nc.bs.uapbd.qd.esign.audit.AuditCaptureScope;
import nc.bs.uapbd.qd.esign.audit.PhysicalSnapshotReader;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.bs.uapbd.qd.esign.core.NativeBatchStateOutcome;
import nc.bs.uapbd.qd.esign.core.PartnerStateSnapshots;
import nc.itf.bd.cust.baseinfo.ICustBaseInfoService;
import nc.itf.bd.supplier.baseinfo.ISupplierBaseInfoService;
import nc.itf.uapbd.qd.esign.IPartnerStateAdapter;
import nc.vo.bd.cust.CustomerVO;
import nc.vo.bd.cust.CustSupplierVO;
import nc.vo.bd.supplier.SupplierVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.uapbd.qd.esign.ArchiveBatchResult;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.PartnerStateCommand;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;
import nc.vo.util.BDPKLockUtil;

/** Candidate for NC's single native customer/supplier enable/disable batch. */
public final class PartnerStateAdapter implements IPartnerStateAdapter {
    public void validate(OperationContext context,Serializable command) throws BusinessException { rows(context,command); }

    static void prelock(OperationContext context,Serializable command) throws BusinessException {
        SuperVO[] values=rows(context,command);
        // NC locks all selected root VOs before locking their shared partner VOs.
        BDPKLockUtil.lockSuperVO(values);
        BaseDAO dao=new BaseDAO();List<SuperVO> shared=new ArrayList<SuperVO>();
        for (BusinessObjectRef ref:context.getObjects()) {
            CustSupplierVO row=(CustSupplierVO)dao.retrieveByPK(CustSupplierVO.class,ref.getObjectPk());
            if (row!=null) {
                EsignValues.require(context.getGroupId().equals(row.getPk_group()) && ref.getObjectPk().equals(row.getPk_cust_sup()),
                    "客户供应商关联行的集团或主键不一致。");
                shared.add(row);
            }
        }
        if (!shared.isEmpty()) BDPKLockUtil.lockSuperVO(shared.toArray(new SuperVO[shared.size()]));
    }

    public Serializable execute(OperationContext context,Serializable command) throws BusinessException {
        SuperVO[] values=rows(context,command);
        boolean customer=customer(context),enable="ENABLE".equals(context.getOperationType());
        String table=customer?"bd_customer":"bd_supplier",stateColumn=customer?"custenablestate":"supenablestate";
        AuditCaptureScope scope=AuditCaptureScope.current();
        EsignValues.require(scope!=null,"档案启停缺少受控事务上下文。");
        PhysicalSnapshotReader reader=new PhysicalSnapshotReader();
        Map<String,List<SnapshotRow>> prior=new LinkedHashMap<String,List<SnapshotRow>>();Set<String> already=new HashSet<String>();
        for (BusinessObjectRef ref:context.getObjects()) {
            List<SnapshotRow> before=snapshot(reader,context,ref,true);prior.put(ref.getClientObjectId(),before);
            if (PartnerStateSnapshots.alreadyTarget(before,table,ref.getObjectPk(),enable)) already.add(ref.getObjectPk());
            if (scope.isEnabledFor(ref.getClientObjectId())) scope.before(ref.getClientObjectId(),before);
        }
        ValueObjWithErrLog nativeResult;
        if (customer) {
            CustomerVO[] typed=new CustomerVO[values.length];for(int i=0;i<values.length;i++) typed[i]=(CustomerVO)values[i];
            ICustBaseInfoService service=NCLocator.getInstance().lookup(ICustBaseInfoService.class);
            nativeResult=enable?service.enableCustBaseInfo(typed):service.disableCustBaseInfo(typed);
        } else {
            SupplierVO[] typed=new SupplierVO[values.length];for(int i=0;i<values.length;i++) typed[i]=(SupplierVO)values[i];
            ISupplierBaseInfoService service=NCLocator.getInstance().lookup(ISupplierBaseInfoService.class);
            nativeResult=enable?service.enableSupBaseInfo(typed):service.disableSupBaseInfo(typed);
        }
        List<NativeBatchStateOutcome.Unit> classified=NativeBatchStateOutcome.reconcile(context.getObjects(),already,nativeResult);
        List<ArchiveBatchResult.Unit> units=new ArrayList<ArchiveBatchResult.Unit>();
        for (NativeBatchStateOutcome.Unit unit:classified) {
            BusinessObjectRef ref=find(context,unit.getClientId());List<SnapshotRow> after=snapshot(reader,context,ref,false);
            List<SnapshotRow> before=prior.get(ref.getClientObjectId());
            if ("SUCCESS".equals(unit.getState())) {
                PartnerStateSnapshots.verifySuccess(before,after,table,ref.getObjectPk(),stateColumn,enable);
                if (scope.isEnabledFor(ref.getClientObjectId())) scope.after(ref.getClientObjectId(),after);
            } else EsignValues.require(PartnerStateSnapshots.sameRows(before,after),
                "原生未成功档案或其异步日志发生变化，业务必须回滚。");
            units.add(new ArchiveBatchResult.Unit(unit.getClientId(),unit.getPk(),unit.getState()));
        }
        return new ArchiveBatchResult(nativeResult,units);
    }

    public List<BusinessObjectRef> describeResult(OperationContext context,Serializable result) throws BusinessException {
        EsignValues.require(result instanceof ArchiveBatchResult,"档案启停结果不完整。");return context.getObjects();
    }

    static List<SnapshotRow> snapshot(PhysicalSnapshotReader reader,OperationContext context,BusinessObjectRef ref,boolean lock)
            throws BusinessException {
        boolean customer=customer(context);String table=customer?"bd_customer":"bd_supplier",pk=customer?"pk_customer":"pk_supplier";
        List<SnapshotRow> root=read(reader,table,pk,null,pk,ref.getObjectPk(),"pk_group",context.getGroupId(),true,lock);
        EsignValues.require(root.size()==1 && ref.getPolicyOwnerOrg().equals(root.get(0).getValues().get("pk_org"))
            && ref.getBillCode()!=null && ref.getBillCode().equals(root.get(0).getValues().get("code")),"档案所属组织或编码已变化。");
        List<SnapshotRow> result=new ArrayList<SnapshotRow>(root);
        List<SnapshotRow> shared=read(reader,"bd_cust_supplier","pk_cust_sup",null,"pk_cust_sup",ref.getObjectPk(),
            "pk_group",context.getGroupId(),true,lock);
        EsignValues.require(shared.size()<=1,"客户供应商关联行不唯一。");
        for(SnapshotRow row:shared) result.add(new SnapshotRow(row.getEntity(),row.getRowId(),ref.getObjectPk(),
            row.getValues(),row.getTypes(),row.getLabels(),true));
        // The native enable service queries by pk_basdoc without a group or dr condition.
        result.addAll(read(reader,"bd_ansylog","pk_anydelte","pk_basdoc","pk_basdoc",ref.getObjectPk(),null,null,false,lock));
        return result;
    }

    private static List<SnapshotRow> read(PhysicalSnapshotReader reader,String table,String key,String parent,String match,String value,
            String scope,String scopeValue,boolean active,boolean lock) throws BusinessException {
        return lock?reader.readLocked(table,key,parent,match,value,scope,scopeValue,active)
            :reader.read(table,key,parent,match,value,scope,scopeValue,active);
    }

    private static BusinessObjectRef find(OperationContext context,String id) throws BusinessException {
        for(BusinessObjectRef ref:context.getObjects()) if(ref.getClientObjectId().equals(id)) return ref;
        throw new BusinessException("档案启停结果包含未授权对象。");
    }

    private static boolean customer(OperationContext context) { return "10140CUG".equals(context.getNodeCode()) || "10140CUO".equals(context.getNodeCode()); }

    static SuperVO[] rows(OperationContext context,Serializable command) throws BusinessException {
        EsignValues.require(context!=null && ArchiveBatchResult.isPartnerState(context.getNodeCode(),context.getButtonCode(),context.getOperationType())
            && OperationContext.OWNER_POLICY_PROTOCOL.equals(context.getPolicyProtocol()) && context.getGroupId()!=null
            && context.getOrgId()!=null && !context.getObjects().isEmpty() && command instanceof PartnerStateCommand,"档案启停载荷或节点不正确。");
        Map<String,SuperVO> rows=((PartnerStateCommand)command).getRows();boolean customer=customer(context);
        EsignValues.require(rows.size()==context.getObjects().size(),"档案启停数据与授权范围不一致。");
        List<SuperVO> values=new ArrayList<SuperVO>();Set<String> pks=new HashSet<String>();
        for(BusinessObjectRef ref:context.getObjects()) {
            SuperVO raw=rows.get(ref.getClientObjectId());String pk=null,group=null,org=null;
            if(customer && raw!=null && raw.getClass()==CustomerVO.class) {
                CustomerVO value=(CustomerVO)raw;pk=value.getPk_customer();group=value.getPk_group();org=value.getPk_org();
            } else if(!customer && raw!=null && raw.getClass()==SupplierVO.class) {
                SupplierVO value=(SupplierVO)raw;pk=value.getPk_supplier();group=value.getPk_group();org=value.getPk_org();
            }
            EsignValues.require((customer?"BD_CUSTOMER":"BD_SUPPLIER").equals(ref.getObjectType())
                && ref.getObjectPk()!=null && ref.getObjectPk().equals(pk) && pks.add(pk) && context.getGroupId().equals(group)
                && ref.getPolicyOwnerOrg()!=null && ref.getPolicyOwnerOrg().equals(org) && ref.getDesiredOwnerOrg()==null
                && ref.getParentClientObjectId()==null && context.getOrgId().equals(ref.getTargetOrg()),"档案启停对象、所属组织或操作组织不匹配。");
            values.add(raw);
        }
        return values.toArray(new SuperVO[values.size()]);
    }
}
