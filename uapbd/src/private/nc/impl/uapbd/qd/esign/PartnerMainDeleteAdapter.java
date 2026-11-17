package nc.impl.uapbd.qd.esign;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import nc.bs.dao.BaseDAO;
import nc.bs.framework.common.NCLocator;
import nc.bs.uapbd.qd.esign.audit.AuditCaptureScope;
import nc.bs.uapbd.qd.esign.audit.PhysicalSnapshotReader;
import nc.bs.uapbd.qd.esign.audit.SnapshotSupport;
import nc.bs.uapbd.qd.esign.audit.WmsArchiveSnapshots;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.bs.uapbd.qd.esign.core.PartnerMainDeleteRoute;
import nc.bs.uapbd.qd.esign.core.PartnerMainDeleteScope;
import nc.itf.bd.cust.baseinfo.ICustBaseInfoExtendService;
import nc.itf.bd.supplier.baseinfo.ISupBaseInfoExtendService;
import nc.itf.uapbd.qd.esign.spi.BusinessOperationAdapter;
import nc.md.persist.framework.MDPersistenceService;
import nc.vo.bd.cust.CustomerVO;
import nc.vo.bd.supplier.SupplierVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;
import nc.vo.util.BDPKLockUtil;

/** Candidate for one signed partner-main delete unit; not yet registered as an RPC entry. */
public final class PartnerMainDeleteAdapter implements BusinessOperationAdapter {
    public void validate(OperationContext context,Serializable command) throws BusinessException { draft(context,command); }

    /** Acquires the native master lock first, then freezes assignment registration and bank relationship writes. */
    static void prelock(OperationContext context,Serializable command) throws BusinessException {
        SuperVO requested=draft(context,command);BusinessObjectRef root=root(context);
        SuperVO current=load(requested,context,root);
        BDPKLockUtil.lockSuperVO(new SuperVO[]{current});
        new BaseDAO().executeUpdate("LOCK TABLE bd_assign_tab IN SHARE MODE NOWAIT");
        PartnerMainDeleteBankLock.acquire();
        new BaseDAO().executeUpdate("LOCK TABLE "+("BD_CUSTOMER".equals(root.getObjectType())?"bd_custstructure":"bd_supstructure")
            +" IN SHARE ROW EXCLUSIVE MODE NOWAIT");
        PartnerMainDeleteScope.verify(context.getGroupId(),context.getOrgId(),root.getObjectType(),context.getObjects(),
            PartnerMainDeleteData.reader(context.getNodeCode()),true);
        load(requested,context,root);
    }

    public Serializable execute(OperationContext context,Serializable command) throws BusinessException {
        SuperVO requested=draft(context,command);BusinessObjectRef root=root(context);
        SuperVO current=load(requested,context,root);
        AuditCaptureScope audit=AuditCaptureScope.current();
        EsignValues.require(audit!=null,"客户或供应商删除缺少受控事务上下文。");
        PartnerMainDeleteScope.verify(context.getGroupId(),context.getOrgId(),root.getObjectType(),context.getObjects(),
            PartnerMainDeleteData.reader(context.getNodeCode()),true);
        PartnerMainDeleteData.State state=PartnerMainDeleteData.capture(context.getNodeCode(),context.getGroupId(),
            root.getObjectType(),root.getObjectPk(),true);
        PhysicalSnapshotReader physical=new PhysicalSnapshotReader();
        List<SnapshotRow> extension=current instanceof SupplierVO
            ?PartnerMainExtension.rows(physical,context.getGroupId(),root.getObjectPk(),true):Collections.<SnapshotRow>emptyList();
        PartnerMainExtension.DeletePlan extensions=PartnerMainExtension.deletePlan(context.getNodeCode(),current,extension);
        PartnerMainDeleteNativeBanks.prepare(current,state.banks,PartnerMainDeleteNativeBanks.database());
        if(audit.isEnabledFor(root.getClientObjectId())) {
            List<SnapshotRow> before=new ArrayList<SnapshotRow>(state.rows);
            before.addAll(structure(physical,root.getObjectType(),root.getObjectPk(),true));
            before.addAll(virtualParent(physical,current,true));
            before.addAll(WmsArchiveSnapshots.read(physical,root.getObjectType(),root.getObjectPk(),true));
            audit.before(root.getClientObjectId(),unique(before));
        }
        for(BusinessObjectRef ref:context.getObjects()) if(ref!=root && audit.isEnabledFor(ref.getClientObjectId()))
            audit.before(ref.getClientObjectId(),related(physical,context,ref,true));

        NCLocator locator=NCLocator.getInstance();
        if(current instanceof CustomerVO) locator.lookup(ICustBaseInfoExtendService.class)
            .deleteCustomerWithExtendVO((CustomerVO)current,extensions.objects,extensions.context);
        else locator.lookup(ISupBaseInfoExtendService.class)
            .deleteSupplierWithExtendVO((SupplierVO)current,extensions.objects,extensions.context);

        List<SnapshotRow> after=PartnerMainDeleteData.after(state,false);
        PartnerMainDeleteData.requireClean(state,after);
        if(audit.isEnabledFor(root.getClientObjectId())) {
            List<SnapshotRow> saved=new ArrayList<SnapshotRow>(after);
            saved.addAll(structure(physical,root.getObjectType(),root.getObjectPk(),false));
            saved.addAll(virtualParent(physical,current,false));
            saved.addAll(WmsArchiveSnapshots.read(physical,root.getObjectType(),root.getObjectPk(),false));
            if(!saved.isEmpty()) audit.after(root.getClientObjectId(),unique(saved));
        }
        for(BusinessObjectRef ref:context.getObjects()) if(ref!=root) {
            List<SnapshotRow> saved=related(physical,context,ref,false);
            if(audit.isEnabledFor(ref.getClientObjectId())) audit.after(ref.getClientObjectId(),saved);
        }
        return current;
    }

    public List<BusinessObjectRef> describeResult(OperationContext context,Serializable result) throws BusinessException {
        BusinessObjectRef root=root(context);
        EsignValues.require(result instanceof SuperVO && root.getObjectPk().equals(((SuperVO)result).getPrimaryKey()),
            "客户或供应商删除结果与授权主档不一致。");
        return context.getObjects();
    }

    private static SuperVO load(SuperVO requested,OperationContext context,BusinessObjectRef root) throws BusinessException {
        String[] attributes=requested instanceof CustomerVO?new String[]{"custcontacts","custvat"}
            :new String[]{"suplinkman","supbankacc","supcountrytaxes","supvat"};
        Object[] loaded=MDPersistenceService.lookupPersistenceQueryService().queryBillOfVOByPKsWithOrder(
            requested.getClass(),new String[]{root.getObjectPk()},attributes);
        SuperVO stored=loaded!=null && loaded.length==1 && loaded[0] instanceof SuperVO?(SuperVO)loaded[0]:null;
        EsignValues.require(stored!=null && stored.getClass()==requested.getClass()
            && context.getGroupId().equals(stored.getAttributeValue("pk_group"))
            && root.getPolicyOwnerOrg().equals(stored.getAttributeValue("pk_org"))
            && root.getBillCode()!=null && root.getBillCode().equals(stored.getAttributeValue("code"))
            && Objects.equals(requested.getAttributeValue("ts"),stored.getAttributeValue("ts")),
            "客户或供应商档案、所属组织或版本已变化，请刷新后重试。");
        return stored;
    }

    private static SuperVO draft(OperationContext context,Serializable command) throws BusinessException {
        EsignValues.require(context!=null && PartnerMainDeleteRoute.matches(context.getNodeCode(),context.getButtonCode(),context.getOperationType())
            && OperationContext.OWNER_POLICY_PROTOCOL.equals(context.getPolicyProtocol()) && context.getGroupId()!=null
            && context.getOrgId()!=null && command instanceof SuperVO,"客户或供应商删除载荷或入口不正确。");
        BusinessObjectRef root=root(context);SuperVO value=(SuperVO)command;
        Class<?> expected="BD_CUSTOMER".equals(root.getObjectType())?CustomerVO.class:SupplierVO.class;
        EsignValues.require(value.getClass()==expected && root.getObjectType().equals(PartnerMainDeleteRoute.domain(context.getNodeCode()))
            && root.getObjectPk()!=null && root.getObjectPk().equals(value.getPrimaryKey())
            && context.getGroupId().equals(value.getAttributeValue("pk_group")) && root.getPolicyOwnerOrg()!=null
            && root.getPolicyOwnerOrg().equals(value.getAttributeValue("pk_org")),"删除载荷与授权档案或所属组织不一致。");
        Set<String> identities=new HashSet<String>();
        for(BusinessObjectRef ref:context.getObjects()) {
            EsignValues.require(ref!=null && ref.getObjectPk()!=null && ref.getPolicyOwnerOrg()!=null
                && ref.getDesiredOwnerOrg()==null && ("BD_CUSTOMER".equals(ref.getObjectType()) || "BD_SUPPLIER".equals(ref.getObjectType()))
                && (ref.getTargetOrg()==null || context.getOrgId().equals(ref.getTargetOrg()))
                && (ref==root || root.getClientObjectId().equals(ref.getParentClientObjectId()))
                && identities.add(ref.getObjectType()+"\u0000"+ref.getObjectPk()),"删除授权包含无效或重复的关联档案。");
            // NC's native master delete removes the shared row. Never orphan an existing paired archive.
            EsignValues.require(ref==root || !root.getObjectPk().equals(ref.getObjectPk()),"关联的客户或供应商仍然存在，请先按原生规则解除关联。");
        }
        return value;
    }
    private static BusinessObjectRef root(OperationContext context) throws BusinessException {
        EsignValues.require(context!=null && context.getObjects()!=null,"客户或供应商删除授权为空。");
        BusinessObjectRef result=null;
        for(BusinessObjectRef ref:context.getObjects()) if(ref!=null && ref.getParentClientObjectId()==null) {
            EsignValues.require(result==null,"客户或供应商删除单元包含多个主档。");result=ref;
        }
        EsignValues.require(result!=null,"客户或供应商删除缺少选中主档。");return result;
    }
    private static List<SnapshotRow> related(PhysicalSnapshotReader reader,OperationContext context,BusinessObjectRef ref,boolean lock)
            throws BusinessException {
        boolean customer="BD_CUSTOMER".equals(ref.getObjectType());String table=customer?"bd_customer":"bd_supplier";
        String key=customer?"pk_customer":"pk_supplier";
        List<SnapshotRow> rows=lock?reader.readLocked(table,key,null,key,ref.getObjectPk(),false)
            :reader.read(table,key,null,key,ref.getObjectPk(),false);
        EsignValues.require(rows.size()==1 && context.getGroupId().equals(rows.get(0).getValues().get("pk_group"))
            && ref.getPolicyOwnerOrg().equals(rows.get(0).getValues().get("pk_org")),"受影响档案不存在或所属组织已变化。");
        rows.addAll(structure(reader,ref.getObjectType(),ref.getObjectPk(),lock));return rows;
    }
    private static List<SnapshotRow> structure(PhysicalSnapshotReader reader,String domain,String pk,boolean lock) throws BusinessException {
        boolean customer="BD_CUSTOMER".equals(domain);String table=customer?"bd_custstructure":"bd_supstructure";
        String key=customer?"pk_customer":"pk_supplier";
        return lock?reader.readLocked(table,key,null,key,pk,false):reader.read(table,key,null,key,pk,false);
    }
    private static List<SnapshotRow> virtualParent(PhysicalSnapshotReader reader,SuperVO value,boolean lock) throws BusinessException {
        if(!(value instanceof CustomerVO) || !"~".equals(value.getAttributeValue("pk_customer_main")))
            return Collections.emptyList();
        return structure(reader,"BD_CUSTOMER","~",lock);
    }
    private static List<SnapshotRow> unique(List<SnapshotRow> rows) throws BusinessException {
        java.util.Map<String,SnapshotRow> result=new java.util.TreeMap<String,SnapshotRow>();
        for(SnapshotRow row:rows) {
            String key=row.getEntity()+"\u0000"+row.getRowId();SnapshotRow old=result.put(key,row);
            EsignValues.require(old==null || SnapshotSupport.json(old).equals(SnapshotSupport.json(row)),"删除审计快照的同一行不一致。");
        }
        return new ArrayList<SnapshotRow>(result.values());
    }
}
