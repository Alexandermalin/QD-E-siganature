package nc.impl.uapbd.qd.esign;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.transaction.TransactionSynchronizationRegistry;
import nc.bs.bd.assignservice.AssignToOrgServiceImpl;
import nc.bs.bd.assignservice.IAssignToOrgService;
import nc.bs.framework.common.InvocationInfoProxy;
import nc.bs.framework.common.NCLocator;
import nc.bs.uapbd.qd.esign.audit.AuditRecorder;
import nc.bs.uapbd.qd.esign.audit.PhysicalSnapshotReader;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.bs.uapbd.qd.esign.core.NativeTransactionBoundaryProbe;
import nc.bs.uapbd.qd.esign.core.MaterialCancelAssignmentSnapshots;
import nc.bs.bd.material.marorg.IMarOrgService;
import nc.vo.bd.errorlog.ErrLogResult;
import nc.vo.bd.errorlog.ErrLogReturnValue;
import nc.vo.bd.errorlog.ErrorLogUtil;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;
import nc.vo.util.BDPKLockUtil;

/** Candidate single-material/target unit. The caller must verify complete scope, policies and permissions. */
final class MaterialCancelAssignmentNative {
    private static final String LOG_KEY="assignerrorlogutilkey";

    /** The caller must run in its actual CMT and mark it rollback-only on any thrown failure. */
    String apply(Map<String,Object> request,Map<String,Object> object,String expectedRoute) throws BusinessException {
        String group=EsignValues.text(request,"pk_group"),material=EsignValues.text(object,"object_pk");
        String target=EsignValues.text(object,"target_org"),operation=EsignValues.text(request,"operation_type");
        EsignValues.require(group!=null && material!=null && target!=null && "CANCEL_ASSIGN".equals(operation)
            && "BD_MATERIAL".equals(EsignValues.text(object,"object_type")) && expectedRoute!=null,
            "物料分配事务单元缺少档案、目标组织或已授权的页签配置。");
        TransactionSynchronizationRegistry transaction=NativeTransactionBoundaryProbe.requireActiveTransaction();
        Object transactionKey=transaction.getTransactionKey();
        BDPKLockUtil.lockString(new String[]{material});
        MaterialCancelAssignmentRoutePlan nativeService=new MaterialCancelAssignmentRoutePlan();
        MaterialCancelAssignmentRoutePlan.Plan plan=nativeService.inspect(target);
        EsignValues.require(expectedRoute.equals(plan.fingerprint()),"物料分配页签配置已变化，请重新发起操作。");
        PhysicalSnapshotReader reader=new PhysicalSnapshotReader();
        List<SnapshotRow> before=read(reader,group,material,target,plan.tables(),true);
        SnapshotRow main=find(before,"bd_material");
        EsignValues.require(main!=null && EsignValues.text(object,"policy_owner_org")!=null
            && EsignValues.text(object,"policy_owner_org").equals(main.getValues().get("pk_org"))
            && EsignValues.text(object,"bill_code")!=null && EsignValues.text(object,"bill_code").equals(main.getValues().get("code")),
            "物料档案编码或所属组织已变化。");
        Map<IAssignToOrgService,List<String>> pages=plan.routes();
        for(Iterator<Map.Entry<IAssignToOrgService,List<String>>> it=pages.entrySet().iterator();it.hasNext();) {
            Map.Entry<IAssignToOrgService,List<String>> entry=it.next();
            String table=((AssignToOrgServiceImpl)entry.getKey()).getAssignTableName().toLowerCase(java.util.Locale.ENGLISH);
            boolean exists=find(before,table)!=null;
            if(!exists) it.remove();
        }
        boolean relationExists=find(before,"bd_marorg")!=null;
        if(relationExists) {
            IMarOrgService relation=NCLocator.getInstance().lookup(IMarOrgService.class);
            EsignValues.require(relation!=null,"无法获取原生物料分配关系服务。");
            InvocationInfoProxy invocation=InvocationInfoProxy.getInstance();String previous=invocation.getProperty(LOG_KEY);
            invocation.setProperty(LOG_KEY,MaterialCancelAssignmentRoutePlan.generateNewPk());
            try {
                ErrLogReturnValue result=relation.cancelAssignMaterialByPks(new String[]{material},new String[]{target},null);
                requireNoErrors(result);
            } finally { invocation.setProperty(LOG_KEY,previous); }
        }
        if(!pages.isEmpty()) {
            ErrorLogUtil errors=new ErrorLogUtil(nativeService.baseBeanId(),EsignValues.text(request,"user_id"),
                "物料取消分配",false);
            nativeService.applyPages(material,pages,errors);
            requireNoErrors(errors.getErrLogReturnValue(null,pages.size()));
        }
        EsignValues.require(transactionKey.equals(transaction.getTransactionKey()) && !transaction.getRollbackOnly(),
            "原生物料分配事务已变化或已标记回滚。");
        List<SnapshotRow> after=read(reader,group,material,target,plan.tables(),false);
        String result=MaterialCancelAssignmentSnapshots.reconcile(group,material,target,plan.tables(),before,after);
        EsignValues.require(expectedRoute.equals(new MaterialCancelAssignmentRoutePlan().inspect(target).fingerprint()),
            "物料分配页签配置在提交期间已变化，业务必须回滚。");
        if("SUCCESS".equals(result) && EsignValues.yes(object,"owner_audit_enabled"))
            new AuditRecorder().record(request,object,before,after);
        EsignValues.require(transactionKey.equals(transaction.getTransactionKey()) && !transaction.getRollbackOnly(),
            "物料分配审计事务已变化或已标记回滚。");
        return result;
    }

    private static List<SnapshotRow> read(PhysicalSnapshotReader reader,String group,String material,String target,
            Set<String> tables,boolean lock) throws BusinessException {
        List<SnapshotRow> result=new ArrayList<SnapshotRow>();
        List<SnapshotRow> roots=lock?reader.readLocked("bd_material","pk_material",null,"pk_material",material,false)
            :reader.read("bd_material","pk_material",null,"pk_material",material,false);
        EsignValues.require(roots.size()==1,"物料主档不存在或不唯一。");requireActive(roots);result.addAll(roots);
        for(String table:tables) {
            String key=key(table);
            List<SnapshotRow> rows=lock?reader.readLocked(table,key,"pk_material","pk_material",material,"pk_org",target,false)
                :reader.read(table,key,"pk_material","pk_material",material,"pk_org",target,false);
            EsignValues.require(rows.size()<=1,"物料分配存在多条组织关系或历史行，无法安全核对。");
            requireActive(rows);result.addAll(rows);
        }
        for(SnapshotRow row:result) EsignValues.require(group.equals(row.getValues().get("pk_group")),"物料分配数据跨集团。");
        return result;
    }
    private static String key(String table) throws BusinessException {
        String key=MaterialCancelAssignmentSnapshots.keys().get(table);
        EsignValues.require(key!=null,"物料分配物理表不在已核实范围内。");return key;
    }
    private static void requireActive(List<SnapshotRow> rows) throws BusinessException {
        for(SnapshotRow row:rows) {
            String dr=row.getValues().get("dr");EsignValues.require(dr==null || "0".equals(dr),"物料分配包含待核实的删除历史行。");
        }
    }
    private static SnapshotRow find(List<SnapshotRow> rows,String table) {
        for(SnapshotRow row:rows) if(table.equals(row.getEntity())) return row;return null;
    }
    private static void requireNoErrors(ErrLogReturnValue result) throws BusinessException {
        ErrLogResult errors=result==null?null:result.getErrLogResult();
        EsignValues.require(result!=null && (errors==null || errors.getErrorMessagegNum()==0
            && (errors.getErrorMsgs()==null || errors.getErrorMsgs().length==0)),"原生物料分配校验未通过，业务必须回滚。");
    }
}

