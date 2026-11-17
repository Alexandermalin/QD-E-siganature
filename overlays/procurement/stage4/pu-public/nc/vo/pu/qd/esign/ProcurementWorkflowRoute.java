package nc.vo.pu.qd.esign;

import nc.vo.pu.m20.entity.PraybillHeaderVO;
import nc.vo.pu.m20.entity.PraybillItemVO;
import nc.vo.pu.m20.entity.PraybillVO;
import nc.vo.pu.m21.entity.OrderHeaderVO;
import nc.vo.pu.m21.entity.OrderItemVO;
import nc.vo.pu.m21.entity.OrderPaymentVO;
import nc.vo.pu.m21.entity.OrderVO;
import nc.vo.pu.m21.entity.PayPlanVO;
import nc.vo.pu.m25.entity.InvoiceHeaderVO;
import nc.vo.pu.m25.entity.InvoiceItemVO;
import nc.vo.pu.m25.entity.InvoiceSettleItemVO;
import nc.vo.pu.m25.entity.InvoiceVO;
import nc.vo.pu.m422x.entity.StoreReqAppHeaderVO;
import nc.vo.pu.m422x.entity.StoreReqAppItemVO;
import nc.vo.pu.m422x.entity.StoreReqAppVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.ISuperVO;
import nc.vo.pub.SuperVO;
import nc.vo.pubapp.pattern.model.entity.bill.AbstractBill;

public enum ProcurementWorkflowRoute {
    STORE("40040000", "物资需求申请单", "422X", "PU_STORE_REQ", "pk_storereq",
            StoreReqAppVO.class, StoreReqAppHeaderVO.class, StoreReqAppItemVO.class),
    PRAY("40040200", "请购单", "20", "PU_PRAYBILL", "pk_praybill",
            PraybillVO.class, PraybillHeaderVO.class, PraybillItemVO.class),
    ORDER("40040400", "采购订单", "21", "PU_PURCHASE_ORDER", "pk_order",
            OrderVO.class, OrderHeaderVO.class, OrderItemVO.class, OrderPaymentVO.class, PayPlanVO.class),
    INVOICE("40041000", "采购发票", "25", "PU_PURCHASE_INVOICE", "pk_invoice",
            InvoiceVO.class, InvoiceHeaderVO.class, InvoiceItemVO.class, InvoiceSettleItemVO.class);

    public enum Action {
        COMMIT("Commit", "提交", "SAVE"),
        UNCOMMIT("UnCommit", "收回", "UNSAVEBILL"),
        APPROVE("Approve", "审批", "APPROVE"),
        UNAPPROVE("UnApprove", "取消审批", "UNAPPROVE");

        private final String button;
        private final String label;
        private final String pfAction;

        Action(String button, String label, String pfAction) {
            this.button = button;
            this.label = label;
            this.pfAction = pfAction;
        }

        public static Action forButton(String button) throws BusinessException {
            for (Action action : values()) {
                if (action.button.equals(button)) return action;
            }
            throw new BusinessException("采购审批按钮与授权操作不匹配。");
        }

        public String label() { return label; }
        public String pfAction() { return pfAction; }
    }

    private final String node;
    private final String nodeName;
    private final String billType;
    private final String objectType;
    private final String headerKey;
    private final Class<? extends AbstractBill> billClass;
    private final Class<? extends SuperVO> headerClass;
    private final Class<?>[] childClasses;

    ProcurementWorkflowRoute(String node, String nodeName, String billType, String objectType, String headerKey,
            Class<? extends AbstractBill> billClass, Class<? extends SuperVO> headerClass, Class<?>... childClasses) {
        this.node = node;
        this.nodeName = nodeName;
        this.billType = billType;
        this.objectType = objectType;
        this.headerKey = headerKey;
        this.billClass = billClass;
        this.headerClass = headerClass;
        this.childClasses = childClasses;
    }

    public static ProcurementWorkflowRoute forNode(String node) throws BusinessException {
        for (ProcurementWorkflowRoute route : values()) {
            if (route.node.equals(node)) return route;
        }
        throw new BusinessException("采购审批节点与授权范围不匹配。");
    }

    public String nodeName() { return nodeName; }
    public String billType() { return billType; }
    public String objectType() { return objectType; }
    public String headerKey() { return headerKey; }
    public Class<? extends SuperVO> headerClass() { return headerClass; }

    public boolean isChildClass(Class<?> type) {
        for (Class<?> childClass : childClasses) {
            if (childClass == type) return true;
        }
        return false;
    }

    public SuperVO header(Object value) throws BusinessException {
        if (value == null || value.getClass() != billClass) {
            throw new BusinessException("采购审批单据类型与授权范围不匹配。");
        }
        ISuperVO header = ((AbstractBill) value).getParent();
        if (header == null || header.getClass() != headerClass || !validKey(header.getPrimaryKey())) {
            throw new BusinessException("采购审批单据缺少对应表头主键。");
        }
        return headerClass.cast(header);
    }

    public static boolean validKey(String key) {
        return key != null && !key.isEmpty() && key.equals(key.trim());
    }

    public static String field(SuperVO header, String field) {
        Object value = header.getAttributeValue(field);
        return value instanceof String ? (String) value : null;
    }
}
