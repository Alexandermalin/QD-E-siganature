package nc.impl.so.qd.esign;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import nc.bs.dao.BaseDAO;
import nc.bs.framework.common.NCLocator;
import nc.itf.so.qd.esign.ISaleOrderPfSaveAdapter;
import nc.itf.uap.pf.IplatFormEntry;
import nc.vo.pub.AggregatedValueObject;
import nc.vo.pub.BusinessException;
import nc.vo.pub.ISuperVO;
import nc.vo.pub.IVOMeta;
import nc.vo.pubapp.pflow.PFReturnObject;
import nc.vo.pubapp.pflow.PfUserObject;
import nc.vo.so.m30.entity.SaleOrderHVO;
import nc.vo.so.m30.entity.SaleOrderUserObject;
import nc.vo.so.m30.entity.SaleOrderVO;
import nc.vo.so.m30.sobalance.entity.SoBalanceVO;
import nc.vo.so.qd.esign.SaleOrderPfSaveCommand;
import nc.vo.so.qd.esign.SaleOrderPfSaveResult;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.SaleOrderPilotScope;

/** Preserves the platform PF WRITE entry; never substitutes the lower-level sales script service. */
public class SaleOrderPfSaveAdapter implements ISaleOrderPfSaveAdapter {
    public void validate(OperationContext context, Serializable payload) throws BusinessException {
        require(context != null && "40060301".equals(context.getNodeCode())
                && "Save".equals(context.getButtonCode()) && "INSERT".equals(context.getOperationType()),
                "Only the sales-order new-save pilot is supported");
        require(SaleOrderPilotScope.supports(context.getGroupId(), context.getOrgId()) && context.getObjects().size() == 1,
                "One authorized sales organization and one draft are required");
        BusinessObjectRef ref = context.getObjects().get(0);
        require("SO_SALE_ORDER".equals(ref.getObjectType()) && ref.getObjectPk() == null
                && ref.getParentClientObjectId() == null, "The signed object must be one new sales order");
        require(payload instanceof SaleOrderPfSaveCommand, "Sales-order PF command required");
        SaleOrderPfSaveCommand cmd = (SaleOrderPfSaveCommand) payload;
        SaleOrderVO bill = cmd.getBill();
        require(bill != null && bill.getParentVO() != null && bill.getPrimaryKey() != null
                && bill.getPrimaryKey().trim().length() > 0, "Native preallocated sales-order key required");
        SaleOrderHVO header = bill.getParentVO();
        require(context.getGroupId().equals(header.getPk_group()) && context.getOrgId().equals(header.getPk_org()),
                "Sales-order organization differs from the authorization");
        require(header.getStatus() == 2, "Only native new-order data is supported");
        String type = header.getVtrantypecode();
        require(cmd.getBillType() != null && cmd.getBillType().matches("30(?:-[A-Za-z0-9_-]+)?")
                && (cmd.getBillType().equals(type)
                || (type == null && "30".equals(cmd.getBillType()))), "Sales-order transaction type mismatch");
        Map<String, Object> parameters = cmd.getParameters();
        require(parameters.isEmpty() || (parameters.size() == 1
                && "notechecked".equals(parameters.get("notechecked"))), "Unsupported PF parameters");
        require(cmd.getWorkflowNote() != null || "notechecked".equals(parameters.get("notechecked")),
                "Native workflow preparation is missing");
        PfUserObject pf = cmd.getUserObject();
        require(pf != null && pf.getUserObject() instanceof SaleOrderUserObject
                && (pf.getBusinessCheckMap() == null || pf.getBusinessCheckMap().isEmpty()),
                "Native sales-order user object required");
        SaleOrderUserObject sales = (SaleOrderUserObject) pf.getUserObject();
        require(sales.isIsclientsave(), "销售订单保存缺少原生客户端标记，请联系管理员。");
        require(sales.getOffsetTempVO() == null, "销售订单试点暂不支持应收抵消，请取消抵消后重试。");
        require(isEmptyBalance(sales.getSoBalanceVO()), "销售订单试点暂不支持收款结算，请移除收款后重试。");
        require(sales.getArsubViews() == null || sales.getArsubViews().length == 0,
                "销售订单试点暂不支持应收抵消明细。");
        require(sales.getAggffilevomap() == null || sales.getAggffilevomap().isEmpty(),
                "销售订单试点暂不支持物料特征附加信息。");
        require(sales.getThisGatheringMny() == null
                || sales.getThisGatheringMny().compareTo(nc.vo.pub.lang.UFDouble.ZERO_DBL) == 0,
                "销售订单试点暂不支持本次收款，请移除收款后重试。");
        require(loadHeader(bill.getPrimaryKey()) == null, "Preallocated sales-order key already exists");
    }

    public Serializable execute(OperationContext context, Serializable payload) throws BusinessException {
        validate(context, payload);
        SaleOrderPfSaveCommand cmd = (SaleOrderPfSaveCommand) payload;
        Object raw = platformEntry().processAction("WRITE", cmd.getBillType(), cmd.getWorkflowNote(),
                cmd.getBill(), cmd.getUserObject(), cmd.getParameters());
        require(raw instanceof Serializable, "Native PF did not return a serializable save result");
        SaleOrderVO saved = savedOrder(raw);
        String key = cmd.getBill().getPrimaryKey();
        require(saved != null && key.equals(saved.getPrimaryKey()), "Native PF result does not identify the new order");
        SaleOrderHVO persisted = loadHeader(key);
        require(persisted != null && context.getGroupId().equals(persisted.getPk_group())
                && context.getOrgId().equals(persisted.getPk_org()), "Native PF did not persist the authorized order");
        require(saved.getParentVO() != null && persisted.getVbillcode() != null
                && persisted.getVbillcode().equals(saved.getParentVO().getVbillcode()),
                "Native PF result does not match the persisted order number");
        return new SaleOrderPfSaveResult((Serializable) raw, key, persisted.getVbillcode());
    }

    public List<BusinessObjectRef> describeResult(OperationContext context, Serializable result) throws BusinessException {
        require(result instanceof SaleOrderPfSaveResult && context.getObjects().size() == 1, "Invalid PF save result");
        SaleOrderPfSaveResult saved = (SaleOrderPfSaveResult) result;
        require(saved.getPrimaryKey() != null && saved.getBillCode() != null, "Saved identity is missing");
        BusinessObjectRef ref = context.getObjects().get(0);
        List<BusinessObjectRef> out = new ArrayList<BusinessObjectRef>();
        out.add(new BusinessObjectRef(ref.getClientObjectId(), ref.getObjectType(), saved.getPrimaryKey(),
                saved.getBillCode(), ref.getParentClientObjectId()));
        return out;
    }

    protected IplatFormEntry platformEntry() { return NCLocator.getInstance().lookup(IplatFormEntry.class); }
    protected SaleOrderHVO loadHeader(String key) throws BusinessException {
        return (SaleOrderHVO) new BaseDAO().retrieveByPK(SaleOrderHVO.class, key,
                new String[] { "csaleorderid", "pk_group", "pk_org", "vbillcode" });
    }

    private static SaleOrderVO savedOrder(Object result) throws BusinessException {
        Object value = result;
        if (value instanceof PFReturnObject[]) {
            PFReturnObject[] wrapped = (PFReturnObject[]) value;
            require(wrapped.length == 1 && wrapped[0] != null, "Unexpected PF return wrapper");
            value = wrapped[0].getBills();
        } else if (value instanceof PFReturnObject) {
            value = ((PFReturnObject) value).getBills();
        }
        if (value instanceof AggregatedValueObject[]) {
            AggregatedValueObject[] rows = (AggregatedValueObject[]) value;
            require(rows.length == 1, "PF result must contain exactly one order");
            value = rows[0];
        }
        require(value instanceof SaleOrderVO, "Unexpected sales-order PF result type");
        return (SaleOrderVO) value;
    }

    /** NC initializes an empty balance for every new card; populated balances remain out of scope. */
    private static boolean isEmptyBalance(SoBalanceVO balance) {
        if (balance == null) { return true; }
        if (balance.getParentVO() != null
                || (balance.getListenerflag() != null && balance.getListenerflag().booleanValue())) { return false; }
        IVOMeta[] childTypes = balance.getMetaData().getChildren();
        if (childTypes == null) { return false; }
        for (IVOMeta childType : childTypes) {
            if (childType == null) { return false; }
            ISuperVO[] rows = balance.getChildren(childType);
            if (rows != null && rows.length > 0) { return false; }
        }
        return true;
    }

    private static void require(boolean condition, String message) throws BusinessException {
        if (!condition) { throw new BusinessException(message); }
    }
}
