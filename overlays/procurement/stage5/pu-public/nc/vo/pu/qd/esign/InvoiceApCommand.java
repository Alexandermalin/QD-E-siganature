package nc.vo.pu.qd.esign;

import java.io.Serializable;
import java.util.Collections;
import nc.vo.pu.m25.entity.InvoiceHeaderVO;
import nc.vo.pu.m25.entity.InvoiceVO;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;

public final class InvoiceApCommand implements Serializable {
    private static final long serialVersionUID = 1L;
    public static final String NODE = "40041000";
    public static final String OBJECT = "PU_PURCHASE_INVOICE";
    public static final String SEND_BUTTON = "SendToAP";
    public static final String CANCEL_BUTTON = "CancelToAP";
    public static final String SEND = "SENDAP";
    public static final String CANCEL = "CANCELSENDAP";

    private final String action;
    private final String billType;
    private final InvoiceVO bill;
    private final boolean reload;

    public InvoiceApCommand(String action, String billType, InvoiceVO bill, boolean reload) {
        this.action = action;
        this.billType = billType;
        this.bill = bill;
        this.reload = reload;
    }

    public String getAction() { return action; }
    public String getBillType() { return billType; }
    public InvoiceVO getBill() { return bill; }
    public boolean isReload() { return reload; }

    public static String actionFor(String button) throws BusinessException {
        if (SEND_BUTTON.equals(button)) return SEND;
        if (CANCEL_BUTTON.equals(button)) return CANCEL;
        throw new BusinessException("采购发票应付按钮不匹配。");
    }

    public static InvoiceHeaderVO header(InvoiceVO invoice) throws BusinessException {
        InvoiceHeaderVO head = invoice == null || invoice.getClass() != InvoiceVO.class ? null : invoice.getParentVO();
        if (head == null || head.getClass() != InvoiceHeaderVO.class || !validKey(head.getPk_invoice())) {
            throw new BusinessException("采购发票缺少有效的表头主键。");
        }
        return head;
    }

    public static OperationContext draft(String button, String loginGroup, InvoiceVO invoice) throws BusinessException {
        String action = actionFor(button);
        InvoiceHeaderVO head = header(invoice);
        if (!validKey(loginGroup) || !loginGroup.equals(head.getPk_group()) || !validKey(head.getPk_org())) {
            throw new BusinessException("采购发票所属集团或业务单元不匹配。");
        }
        String pk = head.getPk_invoice();
        BusinessObjectRef object = new BusinessObjectRef(pk, OBJECT, pk, head.getVbillcode(), null);
        return new OperationContext(null, null, loginGroup, head.getPk_org(), NODE, "采购发票",
                button, SEND.equals(action) ? "传应付" : "取消传应付", action, null, Collections.singletonList(object));
    }

    public static boolean validKey(String value) {
        return value != null && !value.isEmpty() && value.equals(value.trim()) && !"~".equals(value);
    }
}
