package nc.impl.uapbd.qd.esign;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import nc.bs.dao.BaseDAO;
import nc.bs.framework.common.NCLocator;
import nc.bs.uapbd.qd.esign.audit.AuditCaptureScope;
import nc.bs.uapbd.qd.esign.audit.PhysicalSnapshotReader;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.bs.uapbd.qd.esign.core.SupplierFreezeChanges;
import nc.bs.uapbd.qd.esign.security.EsignPermission;
import nc.bs.uapbd.qd.esign.security.ServerIdentity;
import nc.itf.bd.supplier.baseinfo.ISupplierBaseInfoService;
import nc.itf.uapbd.qd.esign.ISupplierFreezeAdapter;
import nc.vo.bd.supplier.SupplierVO;
import nc.vo.bd.supplier.finance.SupFinanceVO;
import nc.vo.bd.supplier.stock.SupStockVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.uapbd.qd.esign.ArchiveBatchResult;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.SupplierFreezeCommand;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;
import nc.vo.util.BDPKLockUtil;

/** Candidate adapter for the native one-supplier, multi-organization CMT operation. */
public final class SupplierFreezeAdapter implements ISupplierFreezeAdapter {
    private static final ThreadLocal<LockedRows> LOCKED=new ThreadLocal<LockedRows>();
    public void validate(OperationContext context,Serializable command) throws BusinessException { requireCommand(context,command); }

    /** NC locks all finance rows followed by stock rows before writing either kind. */
    static void prelock(OperationContext context,Serializable command) throws BusinessException {
        SupplierFreezeCommand payload=requireCommand(context,command);
        EsignValues.require(LOCKED.get()==null && context.getRequestId()!=null,"供应商冻结锁上下文无效。");
        NativeRows before=nativeRows(context,payload);
        BDPKLockUtil.lockSuperVO(before.finance.toArray(new SupFinanceVO[before.finance.size()]));
        BDPKLockUtil.lockSuperVO(before.stock.toArray(new SupStockVO[before.stock.size()]));
        NativeRows after=nativeRows(context,payload);
        EsignValues.require(before.identities.equals(after.identities),"供应商冻结数据范围已变化，请重新发起操作。");
        LOCKED.set(new LockedRows(context.getRequestId(),after.identities));
    }
    static void clearPrelock() { LOCKED.remove(); }

    public Serializable execute(OperationContext context,Serializable command) throws BusinessException {
        SupplierFreezeCommand payload=requireCommand(context,command);LockedRows lock=LOCKED.get();
        EsignValues.require(lock!=null && context.getRequestId().equals(lock.requestId),"供应商冻结缺少原生业务锁。");
        AuditCaptureScope scope=AuditCaptureScope.current();
        EsignValues.require(scope!=null,"供应商冻结缺少受控事务上下文。");
        String supplier=payload.getSupplier().getPk_supplier();PhysicalSnapshotReader reader=new PhysicalSnapshotReader();
        List<SnapshotRow> root=reader.readLocked("bd_supplier","pk_supplier",null,"pk_supplier",supplier,"pk_group",context.getGroupId(),true);
        BusinessObjectRef first=context.getObjects().get(0);
        EsignValues.require(root.size()==1 && first.getPolicyOwnerOrg().equals(root.get(0).getValues().get("pk_org"))
            && first.getBillCode()!=null && first.getBillCode().equals(root.get(0).getValues().get("code")),
            "供应商不存在，或编码、所属组织已变化。");
        Map<String,List<SnapshotRow>> before=new LinkedHashMap<String,List<SnapshotRow>>();
        Set<String> snapshots=new HashSet<String>();ServerIdentity identity=ServerIdentity.current();
        for(BusinessObjectRef ref:context.getObjects()) {
            List<SnapshotRow> selected=readRows(reader,context,ref,true);
            for(SnapshotRow row:selected) {
                EsignPermission.requirePageOrganization(identity,context.getNodeCode(),context.getButtonCode(),
                    "bd_supfinance".equals(row.getEntity())?"finance_info":"stock_info",ref.getTargetOrg());
                EsignValues.require(snapshots.add(row.getEntity()+"\u0000"+row.getRowId()),"供应商冻结重复读取数据行。");
            }
            before.put(ref.getClientObjectId(),selected);
            if(!selected.isEmpty() && scope.isEnabledFor(ref.getClientObjectId()))
                scope.before(ref.getClientObjectId(),withRoot(root,selected));
        }
        EsignValues.require(lock.identities.equals(snapshots),"供应商冻结数据范围与已取得的业务锁不一致。");
        ISupplierBaseInfoService nativeService=NCLocator.getInstance().lookup(ISupplierBaseInfoService.class);
        String[] targets=payload.getTargetOrganizations().toArray(new String[payload.getTargetOrganizations().size()]);
        boolean freeze="FREEZE".equals(context.getOperationType());
        if(freeze) nativeService.freezeSupEntirely(payload.getSupplier(),targets);
        else nativeService.unFreezeSupEntirely(payload.getSupplier(),targets);
        List<SnapshotRow> rootAfter=reader.read("bd_supplier","pk_supplier",null,"pk_supplier",supplier,"pk_group",context.getGroupId(),true);
        EsignValues.require(rootAfter.size()==1 && root.get(0).getValues().equals(rootAfter.get(0).getValues()),
            "供应商主档发生额外变化，业务必须回滚。");
        List<ArchiveBatchResult.Unit> units=new ArrayList<ArchiveBatchResult.Unit>();
        for(BusinessObjectRef ref:context.getObjects()) {
            List<SnapshotRow> selected=readRows(reader,context,ref,false);
            boolean changed=SupplierFreezeChanges.changed(before.get(ref.getClientObjectId()),selected,
                supplier,context.getGroupId(),ref.getTargetOrg(),freeze);
            if(changed && scope.isEnabledFor(ref.getClientObjectId())) scope.after(ref.getClientObjectId(),withRoot(rootAfter,selected));
            units.add(new ArchiveBatchResult.Unit(ref.getClientObjectId(),supplier,changed?"SUCCESS":"UNCHANGED"));
        }
        return new ArchiveBatchResult(null,units);
    }
    public List<BusinessObjectRef> describeResult(OperationContext context,Serializable result) throws BusinessException {
        EsignValues.require(result instanceof ArchiveBatchResult,"供应商冻结结果不完整。");return context.getObjects();
    }

    static SupplierFreezeCommand requireCommand(OperationContext context,Serializable command) throws BusinessException {
        EsignValues.require(context!=null && ArchiveBatchResult.isSupplierFreeze(context.getNodeCode(),context.getButtonCode(),context.getOperationType())
            && OperationContext.OWNER_POLICY_PROTOCOL.equals(context.getPolicyProtocol()) && context.getGroupId()!=null
            && context.getOrgId()!=null && !context.getObjects().isEmpty() && command instanceof SupplierFreezeCommand,
            "供应商冻结载荷或节点不正确。");
        SupplierFreezeCommand value=(SupplierFreezeCommand)command;SupplierVO supplier=value.getSupplier();
        EsignValues.require(supplier!=null && supplier.getClass()==SupplierVO.class && safe(supplier.getPk_supplier())
            && context.getGroupId().equals(supplier.getPk_group()) && supplier.getPk_org()!=null,
            "供应商冻结缺少有效的主档或集团。");
        Set<String> expected=new HashSet<String>(),actual=new HashSet<String>();
        for(BusinessObjectRef ref:context.getObjects()) {
            EsignValues.require("BD_SUPPLIER".equals(ref.getObjectType()) && supplier.getPk_supplier().equals(ref.getObjectPk())
                && supplier.getPk_org().equals(ref.getPolicyOwnerOrg()) && safe(ref.getTargetOrg())
                && ref.getDesiredOwnerOrg()==null && ref.getParentClientObjectId()==null && expected.add(ref.getTargetOrg()),
                "供应商冻结目标、档案或所属组织与授权不一致。");
        }
        for(String target:value.getTargetOrganizations()) EsignValues.require(safe(target) && actual.add(target),"供应商冻结目标组织无效或重复。");
        EsignValues.require(expected.equals(actual),"供应商冻结目标组织与授权范围不一致。");
        if("10140SUO".equals(context.getNodeCode())) EsignValues.require(actual.size()==1 && actual.contains(supplier.getPk_org()),
            "业务单元供应商冻结目标与原生操作不一致。");
        return value;
    }
    private static NativeRows nativeRows(OperationContext context,SupplierFreezeCommand command) throws BusinessException {
        BaseDAO dao=new BaseDAO();NativeRows result=new NativeRows();
        for(String target:command.getTargetOrganizations()) {
            String where="pk_supplier='"+command.getSupplier().getPk_supplier()+"' and pk_org='"+target+"'";
            Collection<?> finance=dao.retrieveByClause(SupFinanceVO.class,where),stock=dao.retrieveByClause(SupStockVO.class,where);
            for(Object value:finance) addNative(result,value,SupFinanceVO.class,"bd_supfinance",context,command,target);
            for(Object value:stock) addNative(result,value,SupStockVO.class,"bd_supstock",context,command,target);
        }
        return result;
    }
    private static void addNative(NativeRows rows,Object value,Class<?> type,String table,OperationContext context,
            SupplierFreezeCommand command,String target) throws BusinessException {
        EsignValues.require(value!=null && value.getClass()==type,"供应商冻结查询返回未知数据类型。");
        SuperVO row=(SuperVO)value;
        EsignValues.require(row.getPrimaryKey()!=null && context.getGroupId().equals(row.getAttributeValue("pk_group"))
            && target.equals(row.getAttributeValue("pk_org")) && command.getSupplier().getPk_supplier().equals(row.getAttributeValue("pk_supplier"))
            && rows.identities.add(table+"\u0000"+row.getPrimaryKey()),"供应商冻结原生数据范围不一致。");
        if(type==SupFinanceVO.class) rows.finance.add((SupFinanceVO)row);else rows.stock.add((SupStockVO)row);
    }
    private static List<SnapshotRow> readRows(PhysicalSnapshotReader reader,OperationContext context,BusinessObjectRef ref,boolean lock)
            throws BusinessException {
        List<SnapshotRow> rows=new ArrayList<SnapshotRow>();
        for(String[] table:new String[][]{{"bd_supfinance","pk_supfinance"},{"bd_supstock","pk_supstock"}}) {
            List<SnapshotRow> found=lock?reader.readLocked(table[0],table[1],"pk_supplier","pk_supplier",ref.getObjectPk(),"pk_org",ref.getTargetOrg(),true)
                :reader.read(table[0],table[1],"pk_supplier","pk_supplier",ref.getObjectPk(),"pk_org",ref.getTargetOrg(),true);
            rows.addAll(found);
        }
        return rows;
    }
    private static List<SnapshotRow> withRoot(List<SnapshotRow> root,List<SnapshotRow> rows) {
        List<SnapshotRow> result=new ArrayList<SnapshotRow>(root);result.addAll(rows);return result;
    }
    private static boolean safe(String value) { return value!=null && value.matches("[A-Za-z0-9_-]{1,20}"); }
    private static final class NativeRows {
        final List<SupFinanceVO> finance=new ArrayList<SupFinanceVO>();final List<SupStockVO> stock=new ArrayList<SupStockVO>();
        final Set<String> identities=new HashSet<String>();
    }
    private static final class LockedRows {
        final String requestId;final Set<String> identities;
        LockedRows(String requestId,Set<String> identities) { this.requestId=requestId;this.identities=new HashSet<String>(identities); }
    }
}
