package nc.vo.pu.qd.esign;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import nc.itf.pu.m20.IPraybillMaintain;
import nc.itf.pu.m21.IOrderClose;
import nc.itf.pu.m21.IOrderMaintain;
import nc.itf.pu.m25.IInvoiceMaintain;
import nc.itf.pu.m422x.IStoreReqAppClose;
import nc.vo.pu.m20.entity.PraybillHeaderVO;
import nc.vo.pu.m20.entity.PraybillItemVO;
import nc.vo.pu.m20.entity.PraybillVO;
import nc.vo.pu.m21.entity.OrderHeaderVO;
import nc.vo.pu.m21.entity.OrderItemVO;
import nc.vo.pu.m21.entity.OrderPaymentVO;
import nc.vo.pu.m21.entity.OrderVO;
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

public enum ProcurementStatusRoute {
    STORE_BILL_CLOSE(Bill.STORE, Action.BILL_CLOSE, IStoreReqAppClose.class, "billClose"),
    STORE_BILL_OPEN(Bill.STORE, Action.BILL_OPEN, IStoreReqAppClose.class, "billOpen"),
    STORE_LINE_CLOSE(Bill.STORE, Action.LINE_CLOSE, IStoreReqAppClose.class, "rowClose"),
    STORE_LINE_OPEN(Bill.STORE, Action.LINE_OPEN, IStoreReqAppClose.class, "rowOpen"),
    PRAY_BILL_CLOSE(Bill.PRAY, Action.BILL_CLOSE, IPraybillMaintain.class, "closeBill"),
    PRAY_BILL_OPEN(Bill.PRAY, Action.BILL_OPEN, IPraybillMaintain.class, "openBill"),
    PRAY_LINE_CLOSE(Bill.PRAY, Action.LINE_CLOSE, IPraybillMaintain.class, "closeBillRow"),
    PRAY_LINE_OPEN(Bill.PRAY, Action.LINE_OPEN, IPraybillMaintain.class, "openBillRow"),
    ORDER_BILL_CLOSE(Bill.ORDER, Action.BILL_CLOSE, IOrderClose.class, "close"),
    ORDER_BILL_OPEN(Bill.ORDER, Action.BILL_OPEN, IOrderClose.class, "open"),
    ORDER_LINE_CLOSE(Bill.ORDER, Action.LINE_CLOSE, IOrderClose.class, "close"),
    ORDER_LINE_OPEN(Bill.ORDER, Action.LINE_OPEN, IOrderClose.class, "open"),
    ORDER_FREEZE(Bill.ORDER, Action.FREEZE, IOrderMaintain.class, "freezeOrder"),
    ORDER_UNFREEZE(Bill.ORDER, Action.UNFREEZE, IOrderMaintain.class, "unfreezeOrder"),
    INVOICE_FREEZE(Bill.INVOICE, Action.FREEZE, IInvoiceMaintain.class, "freezeByLightVOs"),
    INVOICE_UNFREEZE(Bill.INVOICE, Action.UNFREEZE, IInvoiceMaintain.class, "unFreezeByLightVOs");

    // Native EnumCloseFlag transport values for FINAL_CLOSE and ROW_CLOSE.
    private static final Integer FINAL_CLOSE = Integer.valueOf(0);
    private static final Integer ROW_CLOSE = Integer.valueOf(1);

    private final Bill bill;
    private final Action action;
    private final Class<?> service;
    private final String method;

    ProcurementStatusRoute(Bill bill, Action action, Class<?> service, String method) {
        this.bill = bill;
        this.action = action;
        this.service = service;
        this.method = method;
    }

    public static ProcurementStatusRoute forAction(String node, String button) throws BusinessException {
        for (ProcurementStatusRoute route : values()) {
            if (route.bill.node.equals(node) && route.action.button.equals(button)) {
                return route;
            }
        }
        throw new BusinessException("采购节点与状态操作不匹配。");
    }

    public Class<?> service() { return service; }
    public String method() { return method; }
    public String nodeName() { return bill.nodeName; }
    public String buttonName() { return action.buttonName; }
    public String operationType() { return action.name(); }
    public String objectType() { return bill.objectType; }
    public String headerKey() { return bill.headerKey; }
    public String rowKey() { return bill.rowKey; }
    public Class<? extends SuperVO> headerType() { return bill.headerType; }
    public Class<? extends SuperVO> rowType() { return bill.rowType; }
    public boolean isRow() { return action == Action.LINE_CLOSE || action == Action.LINE_OPEN; }

    public boolean isChildType(Class<?> type, String key) {
        if (type == bill.rowType) {
            return bill.rowKey.equals(key);
        }
        if (bill == Bill.ORDER) {
            return type == OrderPaymentVO.class && "pk_payment".equals(key);
        }
        return bill == Bill.INVOICE && (type == InvoiceItemVO.class && "pk_invoice_b".equals(key)
                || type == InvoiceSettleItemVO.class && "pk_invoice_settle".equals(key));
    }

    public Map<String, AbstractBill> bills(ProcurementStatusCommand command) throws BusinessException {
        Object[] args = command.getArguments();
        int count = service == IOrderClose.class ? 3 : this == ORDER_FREEZE ? 2 : 1;
        if (!service.getName().equals(command.getServiceName()) || !method.equals(command.getMethodName())
                || args == null || args.length != count) {
            throw new BusinessException("采购状态业务入口与授权操作不匹配。");
        }
        if (service == IOrderClose.class
                && (!(isRow() ? ROW_CLOSE : FINAL_CLOSE).equals(args[1])
                        || !Boolean.valueOf(!isRow()).equals(args[2]))) {
            throw new BusinessException("采购订单关闭或打开范围与授权操作不匹配。");
        }
        if (this == ORDER_FREEZE && args[1] != null && !(args[1] instanceof String)) {
            throw new BusinessException("采购订单冻结原因类型不正确。");
        }
        Map<String, AbstractBill> inputs = typedBills(args[0]);
        if ((isRow() || service == IOrderMaintain.class) && inputs.size() != 1) {
            throw new BusinessException("采购状态操作须对应一张选中的单据。");
        }
        return inputs;
    }

    public Map<String, AbstractBill> typedBills(Object value) throws BusinessException {
        if (value == null || value.getClass() != bill.arrayType || ((AbstractBill[]) value).length == 0) {
            throw new BusinessException("采购状态操作未提供对应类型的有效单据。");
        }
        Map<String, AbstractBill> indexed = new LinkedHashMap<String, AbstractBill>();
        for (AbstractBill entry : (AbstractBill[]) value) {
            if (indexed.put(header(entry).getPrimaryKey(), entry) != null) {
                throw new BusinessException("采购状态操作包含重复单据。");
            }
        }
        return indexed;
    }

    public SuperVO header(Object value) throws BusinessException {
        if (value == null || value.getClass() != bill.arrayType.getComponentType()) {
            throw new BusinessException("采购状态操作单据类型不正确，或单据超出选中范围。");
        }
        ISuperVO header = ((AbstractBill) value).getParent();
        if (header == null || header.getClass() != bill.headerType || !validKey(header.getPrimaryKey())) {
            throw new BusinessException("采购状态操作缺少对应表头主键。");
        }
        return bill.headerType.cast(header);
    }

    public Map<String, SuperVO> rows(AbstractBill value) throws BusinessException {
        String pk = header(value).getPrimaryKey();
        Map<String, SuperVO> indexed = new TreeMap<String, SuperVO>();
        ISuperVO[] children = value.getChildren(bill.rowType);
        if (children == null || children.length == 0) {
            throw new BusinessException("采购行状态操作缺少选中明细。");
        }
        for (ISuperVO row : children) {
            if (row == null || row.getClass() != bill.rowType || !validKey(row.getPrimaryKey())) {
                throw new BusinessException("采购行状态操作缺少对应明细主键。");
            }
            Object parent = row.getAttributeValue(bill.headerKey);
            if (parent != null && !pk.equals(parent)
                    || indexed.put(row.getPrimaryKey(), bill.rowType.cast(row)) != null) {
                throw new BusinessException("采购状态操作行不属于选中单据或包含重复明细。");
            }
        }
        return indexed;
    }

    public String clientId(String pk, Map<String, SuperVO> selected) {
        if (selected == null) {
            return pk;
        }
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
        appendKey(digest, pk);
        for (String key : selected.keySet()) {
            appendKey(digest, key);
        }
        byte[] bytes = digest.digest();
        char[] hex = "0123456789abcdef".toCharArray();
        StringBuilder id = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            id.append(hex[(value & 0xff) >>> 4]).append(hex[value & 0xf]);
        }
        return id.toString();
    }

    private static void appendKey(MessageDigest digest, String key) {
        byte[] bytes = key.getBytes(StandardCharsets.UTF_8);
        int length = bytes.length;
        digest.update(new byte[] {(byte) (length >>> 24), (byte) (length >>> 16),
                (byte) (length >>> 8), (byte) length});
        digest.update(bytes);
    }

    public static boolean validKey(String value) {
        return value != null && !value.isEmpty() && value.equals(value.trim());
    }

    public static String text(SuperVO vo, String field) {
        Object value = vo.getAttributeValue(field);
        return value instanceof String ? (String) value : null;
    }

    private enum Bill {
        STORE("40040000", "物资需求申请单", "PU_STORE_REQ", "pk_storereq", "pk_storereq_b",
                StoreReqAppVO[].class, StoreReqAppHeaderVO.class, StoreReqAppItemVO.class),
        PRAY("40040200", "请购单维护", "PU_PRAYBILL", "pk_praybill", "pk_praybill_b",
                PraybillVO[].class, PraybillHeaderVO.class, PraybillItemVO.class),
        ORDER("40040400", "采购订单维护", "PU_PURCHASE_ORDER", "pk_order", "pk_order_b",
                OrderVO[].class, OrderHeaderVO.class, OrderItemVO.class),
        INVOICE("40041000", "采购发票维护", "PU_PURCHASE_INVOICE", "pk_invoice", null,
                InvoiceVO[].class, InvoiceHeaderVO.class, null);

        private final String node;
        private final String nodeName;
        private final String objectType;
        private final String headerKey;
        private final String rowKey;
        private final Class<? extends AbstractBill[]> arrayType;
        private final Class<? extends SuperVO> headerType;
        private final Class<? extends SuperVO> rowType;

        Bill(String node, String nodeName, String objectType, String headerKey, String rowKey,
                Class<? extends AbstractBill[]> arrayType, Class<? extends SuperVO> headerType,
                Class<? extends SuperVO> rowType) {
            this.node = node;
            this.nodeName = nodeName;
            this.objectType = objectType;
            this.headerKey = headerKey;
            this.rowKey = rowKey;
            this.arrayType = arrayType;
            this.headerType = headerType;
            this.rowType = rowType;
        }
    }

    private enum Action {
        BILL_CLOSE("BillClose", "整单关闭"), BILL_OPEN("BillOpen", "整单打开"),
        LINE_CLOSE("LineClose", "行关闭"), LINE_OPEN("LineOpen", "行打开"),
        FREEZE("Freeze", "冻结"), UNFREEZE("UnFreeze", "解冻");

        private final String button;
        private final String buttonName;

        Action(String button, String buttonName) {
            this.button = button;
            this.buttonName = buttonName;
        }
    }
}
