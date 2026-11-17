package nc.impl.uapbd.qd.esign;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import nc.bs.bd.service.ValueObjWithErrLog;
import nc.bs.dao.BaseDAO;
import nc.bs.framework.common.NCLocator;
import nc.bs.uapbd.qd.esign.audit.AuditCaptureScope;
import nc.bs.uapbd.qd.esign.audit.PhysicalSnapshotReader;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.bs.uapbd.qd.esign.core.NativeBatchStateOutcome;
import nc.itf.bd.cust.baseinfo.ICustBaseInfoService;
import nc.itf.uapbd.qd.esign.ICustomerFreezeAdapter;
import nc.vo.bd.cust.CustomerVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.uapbd.qd.esign.ArchiveBatchResult;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.CustomerFreezeCommand;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;
import nc.vo.util.BDPKLockUtil;

/** Candidate customer freeze/unfreeze, preserving the native one-call batch transaction. */
public final class CustomerFreezeAdapter implements ICustomerFreezeAdapter {
    public void validate(OperationContext context,Serializable command) throws BusinessException { rows(context,command); }
    static void prelock(OperationContext context,Serializable command) throws BusinessException {
        CustomerVO[] values=rows(context,command);
        BDPKLockUtil.lockSuperVO(values);
    }
    public Serializable execute(OperationContext context,Serializable command) throws BusinessException {
        CustomerVO[] values=rows(context,command);boolean freeze="FREEZE".equals(context.getOperationType());
        AuditCaptureScope scope=AuditCaptureScope.current();
        EsignValues.require(scope!=null,"客户冻结缺少受控事务上下文。");
        PhysicalSnapshotReader reader=new PhysicalSnapshotReader();Map<String,List<SnapshotRow>> prior=new LinkedHashMap<String,List<SnapshotRow>>();
        for(BusinessObjectRef ref:context.getObjects()) {
            List<SnapshotRow> before=customer(reader,context,ref,true);prior.put(ref.getClientObjectId(),before);
            if(scope.isEnabledFor(ref.getClientObjectId())) scope.before(ref.getClientObjectId(),before);
        }
        ICustBaseInfoService service=NCLocator.getInstance().lookup(ICustBaseInfoService.class);
        ValueObjWithErrLog nativeResult=freeze?service.freezeCustBaseInfo(values):service.unFreezeCustBaseInfo(values);
        List<NativeBatchStateOutcome.Unit> nativeUnits=NativeBatchStateOutcome.reconcile(context.getObjects(),
            Collections.<String>emptySet(),nativeResult);
        List<ArchiveBatchResult.Unit> units=new ArrayList<ArchiveBatchResult.Unit>();
        BaseDAO dao=new BaseDAO();
        for(NativeBatchStateOutcome.Unit unit:nativeUnits) {
            BusinessObjectRef ref=find(context,unit.getClientId());List<SnapshotRow> after=customer(reader,context,ref,false);
            if("SUCCESS".equals(unit.getState())) {
                CustomerVO saved=(CustomerVO)dao.retrieveByPK(CustomerVO.class,ref.getObjectPk(),new String[]{"pk_customer","pk_group","pk_org","frozenflag"});
                EsignValues.require(saved!=null && saved.getFrozenflag()!=null && saved.getFrozenflag().booleanValue()==freeze,
                    "无法核实客户冻结结果，业务必须回滚。");
                if(scope.isEnabledFor(ref.getClientObjectId())) scope.after(ref.getClientObjectId(),after);
            } else EsignValues.require(prior.get(ref.getClientObjectId()).get(0).getValues().equals(after.get(0).getValues()),
                "原生失败客户的数据已变化，业务必须回滚。");
            units.add(new ArchiveBatchResult.Unit(unit.getClientId(),unit.getPk(),unit.getState()));
        }
        return new ArchiveBatchResult(nativeResult,units);
    }
    public List<BusinessObjectRef> describeResult(OperationContext context,Serializable result) throws BusinessException {
        EsignValues.require(result instanceof ArchiveBatchResult,"客户冻结结果不完整。");return context.getObjects();
    }
    private static List<SnapshotRow> customer(PhysicalSnapshotReader reader,OperationContext context,BusinessObjectRef ref,boolean lock)
            throws BusinessException {
        List<SnapshotRow> rows=lock?reader.readLocked("bd_customer","pk_customer",null,"pk_customer",ref.getObjectPk(),"pk_group",context.getGroupId(),true)
            :reader.read("bd_customer","pk_customer",null,"pk_customer",ref.getObjectPk(),"pk_group",context.getGroupId(),true);
        EsignValues.require(rows.size()==1 && ref.getPolicyOwnerOrg().equals(rows.get(0).getValues().get("pk_org"))
            && ref.getBillCode()!=null && ref.getBillCode().equals(rows.get(0).getValues().get("code")),
            "客户不存在、编码或所属组织已变化。");
        return rows;
    }
    private static BusinessObjectRef find(OperationContext context,String id) throws BusinessException {
        for(BusinessObjectRef ref:context.getObjects()) if(ref.getClientObjectId().equals(id)) return ref;
        throw new BusinessException("客户冻结结果包含未授权对象。");
    }
    private static CustomerVO[] rows(OperationContext context,Serializable command) throws BusinessException {
        EsignValues.require(context!=null && ("10140CUG".equals(context.getNodeCode()) || "10140CUO".equals(context.getNodeCode()))
            && (("Freeze".equals(context.getButtonCode()) && "FREEZE".equals(context.getOperationType()))
                || ("Unfreeze".equals(context.getButtonCode()) && "UNFREEZE".equals(context.getOperationType())))
            && OperationContext.OWNER_POLICY_PROTOCOL.equals(context.getPolicyProtocol()) && context.getGroupId()!=null
            && context.getOrgId()!=null && !context.getObjects().isEmpty() && command instanceof CustomerFreezeCommand,
            "客户冻结载荷或节点不正确。");
        Map<String,CustomerVO> rows=((CustomerFreezeCommand)command).getRows();
        EsignValues.require(rows!=null && rows.size()==context.getObjects().size(),"客户冻结数据与授权范围不一致。");
        List<CustomerVO> values=new ArrayList<CustomerVO>();
        for(BusinessObjectRef ref:context.getObjects()) {
            CustomerVO value=rows.get(ref.getClientObjectId());
            EsignValues.require("BD_CUSTOMER".equals(ref.getObjectType()) && ref.getObjectPk()!=null
                && ref.getPolicyOwnerOrg()!=null && ref.getDesiredOwnerOrg()==null && ref.getParentClientObjectId()==null
                && value!=null && value.getClass()==CustomerVO.class && ref.getObjectPk().equals(value.getPk_customer())
                && context.getGroupId().equals(value.getPk_group()) && ref.getPolicyOwnerOrg().equals(value.getPk_org()),
                "客户冻结包含其他档案或所属组织不匹配的数据。");
            values.add(value);
        }
        return values.toArray(new CustomerVO[values.size()]);
    }
}
