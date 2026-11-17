package nc.impl.uapbd.qd.esign;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import nc.bs.framework.common.NCLocator;
import nc.bs.uapbd.qd.esign.audit.AuditCaptureScope;
import nc.bs.uapbd.qd.esign.audit.PhysicalSnapshotReader;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.bs.uapbd.qd.esign.security.EsignPermission;
import nc.bs.uapbd.qd.esign.security.ServerIdentity;
import nc.itf.bd.cust.creditctl.ICustCreditctlService;
import nc.itf.bd.cust.finance.ICustFinanceService;
import nc.itf.bd.cust.sale.ICustSaleService;
import nc.itf.bd.supplier.finance.ISupplierFinanceService;
import nc.itf.bd.supplier.stock.ISupplierStockService;
import nc.itf.uapbd.qd.esign.IPartnerOrgInfoOperationAdapter;
import nc.vo.bd.cust.creditctl.CustCreditCtlVO;
import nc.vo.bd.cust.finance.CustFinanceVO;
import nc.vo.bd.cust.saleinfo.CustsaleVO;
import nc.vo.bd.supplier.finance.SupFinanceVO;
import nc.vo.bd.supplier.stock.SupStockVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.PartnerOrgInfoCommand;
import nc.vo.uapbd.qd.esign.PartnerOrgPage;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;

/** One native partner page save or per-row delete, after owner, target and permission checks. */
public final class PartnerOrgInfoOperationAdapter implements IPartnerOrgInfoOperationAdapter {
    public void validate(OperationContext context,Serializable command) throws BusinessException { draft(context,page(context),command); }
    public Serializable execute(OperationContext context,Serializable command) throws BusinessException {
        PartnerOrgPage page=page(context);SuperVO value=draft(context,page,command);
        BusinessObjectRef ref=context.getObjects().get(0);
        String root=attribute(value,page.rootKey),target=attribute(value,"pk_org");
        ServerIdentity identity=ServerIdentity.current();
        EsignValues.require(context.getGroupId().equals(identity.groupId),"往来档案集团已变化。");
        EsignPermission.requirePageOrganization(identity,context.getNodeCode(),context.getButtonCode(),page.permission,target);
        if(page==PartnerOrgPage.CUSTOMER_SALE) EsignPermission.requireArchiveData(identity,"custsaleclass",
            "DELETE".equals(context.getOperationType())?"delete":"edit",context.getObjects());
        PhysicalSnapshotReader reader=new PhysicalSnapshotReader();
        List<SnapshotRow> roots=reader.readLocked(page.rootTable,page.rootKey,null,page.rootKey,root,"pk_group",context.getGroupId(),true);
        EsignValues.require(roots.size()==1 && ref.getPolicyOwnerOrg().equals(roots.get(0).getValues().get("pk_org")),
            "往来档案不存在或所属组织已变化。");
        String code=roots.get(0).getValues().get("code");
        EsignValues.require(present(code) && (!present(ref.getBillCode()) || ref.getBillCode().equals(code)),"往来档案编码已变化。");
        List<SnapshotRow> before=reader.readLocked(page.table,page.key,page.rootKey,page.key,ref.getObjectPk(),"pk_group",context.getGroupId(),true);
        requireOwned(before,context.getGroupId(),target,root,page);
        AuditCaptureScope scope=AuditCaptureScope.current();
        EsignValues.require(scope!=null,"往来档案页签缺少受控事务上下文。");
        if(scope.isEnabledFor(ref.getClientObjectId())) scope.before(ref.getClientObjectId(),before);
        if("DELETE".equals(context.getOperationType())) {
            delete(page,value);
            EsignValues.require(reader.read(page.table,page.key,page.rootKey,page.key,ref.getObjectPk(),"pk_group",context.getGroupId(),true).isEmpty(),
                "无法核实往来档案页签删除结果，业务必须回滚。");
            return value;
        }
        SuperVO saved=update(page,value);
        EsignValues.require(saved!=null && saved.getClass()==page.type && ref.getObjectPk().equals(saved.getPrimaryKey())
            && context.getGroupId().equals(attribute(saved,"pk_group")) && target.equals(attribute(saved,"pk_org"))
            && root.equals(attribute(saved,page.rootKey)),"原生页签保存返回了其他档案或组织。");
        List<SnapshotRow> after=reader.read(page.table,page.key,page.rootKey,page.key,ref.getObjectPk(),"pk_group",context.getGroupId(),true);
        requireOwned(after,context.getGroupId(),target,root,page);
        if(scope.isEnabledFor(ref.getClientObjectId())) scope.after(ref.getClientObjectId(),after);
        return saved;
    }
    public List<BusinessObjectRef> describeResult(OperationContext context,Serializable result) throws BusinessException {
        PartnerOrgPage page=page(context);BusinessObjectRef ref=context.getObjects().get(0);
        EsignValues.require(result!=null && result.getClass()==page.type && ref.getObjectPk().equals(((SuperVO)result).getPrimaryKey()),
            "往来档案页签结果与授权不一致。");
        String root=attribute((SuperVO)result,page.rootKey);
        List<SnapshotRow> rows=new PhysicalSnapshotReader().read(page.rootTable,page.rootKey,null,page.rootKey,root,"pk_group",context.getGroupId(),true);
        EsignValues.require(rows.size()==1 && ref.getPolicyOwnerOrg().equals(rows.get(0).getValues().get("pk_org"))
            && present(rows.get(0).getValues().get("code")),"往来档案的所属组织已变化。");
        return Collections.singletonList(new BusinessObjectRef(ref.getClientObjectId(),page.domain,ref.getObjectPk(),
            rows.get(0).getValues().get("code"),ref.getParentClientObjectId()));
    }
    private static PartnerOrgPage page(OperationContext context) throws BusinessException {
        EsignValues.require(context!=null && ("UPDATE".equals(context.getOperationType()) || "DELETE".equals(context.getOperationType()))
            && OperationContext.OWNER_POLICY_PROTOCOL.equals(context.getPolicyProtocol()) && present(context.getGroupId())
            && present(context.getOrgId()) && context.getObjects().size()==1,"往来档案页签须使用所属组织策略并选择一行。");
        PartnerOrgPage page=PartnerOrgPage.forAction(context.getNodeCode(),context.getButtonCode(),context.getOperationType());
        EsignValues.require(page!=null,"往来档案页签入口不受支持。");return page;
    }
    private static SuperVO draft(OperationContext context,PartnerOrgPage page,Serializable command) throws BusinessException {
        EsignValues.require(command instanceof PartnerOrgInfoCommand,"缺少往来档案页签数据。");
        SuperVO value=((PartnerOrgInfoCommand)command).getValue();BusinessObjectRef ref=context.getObjects().get(0);
        EsignValues.require(value!=null && value.getClass()==page.type && page.domain.equals(ref.getObjectType())
            && present(ref.getObjectPk()) && ref.getObjectPk().equals(value.getPrimaryKey()) && present(ref.getPolicyOwnerOrg())
            && context.getGroupId().equals(attribute(value,"pk_group")) && present(attribute(value,page.rootKey))
            && present(attribute(value,"pk_org")) && attribute(value,"pk_org").equals(ref.getTargetOrg()),
            "往来档案页签数据与授权对象或目标组织不一致。");return value;
    }
    private static SuperVO update(PartnerOrgPage page,SuperVO value) throws BusinessException {
        NCLocator locator=NCLocator.getInstance();
        switch(page) {
            case CUSTOMER_FINANCE:return locator.lookup(ICustFinanceService.class).updateCustFinance((CustFinanceVO)value);
            case CUSTOMER_SALE:return locator.lookup(ICustSaleService.class).updateCustSaleVO((CustsaleVO)value);
            case CUSTOMER_CREDIT:return locator.lookup(ICustCreditctlService.class).updateCustCreditctl((CustCreditCtlVO)value);
            case SUPPLIER_FINANCE:return locator.lookup(ISupplierFinanceService.class).updateFinanceVO((SupFinanceVO)value);
            case SUPPLIER_STOCK:return locator.lookup(ISupplierStockService.class).updateSupStockVO((SupStockVO)value);
            default:throw new BusinessException("往来档案页签保存入口不受支持。");
        }
    }
    private static void delete(PartnerOrgPage page,SuperVO value) throws BusinessException {
        NCLocator locator=NCLocator.getInstance();
        switch(page) {
            case CUSTOMER_FINANCE:locator.lookup(ICustFinanceService.class).deleteCustFinance((CustFinanceVO)value);return;
            case CUSTOMER_SALE:locator.lookup(ICustSaleService.class).deleteCustSaleVO((CustsaleVO)value);return;
            case CUSTOMER_CREDIT:locator.lookup(ICustCreditctlService.class).deleteCustCreditctl((CustCreditCtlVO)value);return;
            case SUPPLIER_FINANCE:locator.lookup(ISupplierFinanceService.class).deleteFinanceVO((SupFinanceVO)value);return;
            case SUPPLIER_STOCK:locator.lookup(ISupplierStockService.class).deleteSupStockVO((SupStockVO)value);return;
            default:throw new BusinessException("往来档案页签删除入口不受支持。");
        }
    }
    private static void requireOwned(List<SnapshotRow> rows,String group,String target,String root,PartnerOrgPage page) throws BusinessException {
        EsignValues.require(rows.size()==1,"往来档案页签数据不存在。");Map<String,String> data=rows.get(0).getValues();
        EsignValues.require(group.equals(data.get("pk_group")) && target.equals(data.get("pk_org")) && root.equals(data.get(page.rootKey)),
            "往来档案页签属于其他组织或主档。");
    }
    private static String attribute(SuperVO value,String code) { Object actual=value.getAttributeValue(code);return actual instanceof String?(String)actual:null; }
    private static boolean present(String value) { return value!=null && !value.trim().isEmpty(); }
}
