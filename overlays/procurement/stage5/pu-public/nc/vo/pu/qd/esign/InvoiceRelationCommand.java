package nc.vo.pu.qd.esign;

import java.io.Serializable;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import nc.pu.m25.relevance.vo.RelevanceBVO;
import nc.pu.m25.relevance.vo.RelevanceHVO;
import nc.vo.pu.m25.entity.InvoiceHeaderVO;
import nc.vo.pu.m25.entity.InvoiceItemVO;
import nc.vo.pu.m25.entity.InvoiceVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.lang.UFDouble;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;

public final class InvoiceRelationCommand implements Serializable {
    private static final long serialVersionUID = 1L;
    public static final String NODE = "40041000";
    public static final String RELATE = "RelevanceInvoice";
    public static final String UNRELATE = "UnRelevanceInvoice";
    public static final String OBJECT_TYPE = "PU_PURCHASE_INVOICE";
    private static final int LINK_FIELD_BYTES = 101;

    private final String button;
    private final String invoicePk;
    private final String ts;
    private final String previousNumbers;
    private final String previousIds;
    private final String previousState;
    private final Amounts original;
    private final List<Target> targets;

    private InvoiceRelationCommand(String button, InvoiceHeaderVO header, Amounts original, List<Target> targets) {
        this.button = button;
        invoicePk = header.getPk_invoice();
        ts = header.getTs() == null ? null : header.getTs().toString();
        previousNumbers = text(header.getVdef1());
        previousIds = text(header.getVdef3());
        previousState = text(header.getVdef2());
        this.original = original;
        this.targets = new ArrayList<Target>(targets);
    }

    public static InvoiceRelationCommand from(String button, InvoiceVO invoice, RelevanceHVO[] selected)
            throws BusinessException {
        if (invoice == null || invoice.getParentVO() == null) throw new BusinessException("请选择一张采购发票。");
        List<Target> targets = new ArrayList<Target>();
        if (selected != null) for (RelevanceHVO item : selected) targets.add(Target.from(item));
        InvoiceRelationCommand command = new InvoiceRelationCommand(button, invoice.getParentVO(),
                amounts(invoice.getParentVO(), invoice.getChildrenVO()), targets);
        command.validateShape();
        return command;
    }

    public void validateShape() throws BusinessException {
        operation(button);
        if (!safeKey(invoicePk, 20) || ts == null || ts.isEmpty() || ts.length() > 30 || original == null
                || !original.isComplete() || previousNumbers == null || previousIds == null || previousState == null
                || targets == null || targets.size() > LINK_FIELD_BYTES
                || (isRelate() ? targets.isEmpty() : !targets.isEmpty())) {
            throw new BusinessException("采购发票关联对象或版本不完整。");
        }
        if (isRelate()) {
            Set<String> ids = new HashSet<String>();
            Set<String> numbers = new HashSet<String>();
            for (Target target : targets) {
                if (target == null || !safeKey(target.id, LINK_FIELD_BYTES)
                        || !safeKey(target.number, LINK_FIELD_BYTES) || !ids.add(target.id)
                        || !numbers.add(target.number) || target.amounts == null || !target.amounts.isComplete()
                        || !validUrl(target.pdfUrl)) {
                    throw new BusinessException("数电发票标识、金额或附件地址无效。");
                }
            }
            if (joined(false).length() > LINK_FIELD_BYTES || joined(true).length() > LINK_FIELD_BYTES) {
                throw new BusinessException("所选数电发票编号超出采购发票关联字段长度。");
            }
        }
    }

    public OperationContext draft(String loginGroup, InvoiceHeaderVO header) throws BusinessException {
        String group = header.getPk_group();
        String org = header.getPk_org();
        if (group == null || !group.equals(loginGroup) || org == null || org.trim().isEmpty()
                || !invoicePk.equals(header.getPk_invoice())) {
            throw new BusinessException("采购发票所属集团或业务单元无法核对。");
        }
        BusinessObjectRef ref = new BusinessObjectRef(bindingId(), OBJECT_TYPE, invoicePk, header.getVbillcode(), null);
        return new OperationContext(null, null, group, org, NODE, "采购发票维护", button,
                isRelate() ? "关联发票" : "取消关联发票", operation(button), null, Collections.singletonList(ref));
    }

    public String bindingId() {
        try {
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            digest(hash, button, invoicePk, ts, previousNumbers, previousIds, previousState);
            original.digest(hash);
            for (Target target : targets) {
                digest(hash, target.id, target.number, text(target.pdfUrl));
                target.amounts.digest(hash);
            }
            StringBuilder id = new StringBuilder(invoicePk).append(':');
            for (byte value : hash.digest()) {
                id.append(Character.forDigit((value & 0xf0) >>> 4, 16));
                id.append(Character.forDigit(value & 0x0f, 16));
            }
            return id.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static void digest(MessageDigest hash, String... parts) {
        for (String part : parts) {
            byte[] bytes = part.getBytes(StandardCharsets.UTF_8);
            hash.update((byte) (bytes.length >>> 24));
            hash.update((byte) (bytes.length >>> 16));
            hash.update((byte) (bytes.length >>> 8));
            hash.update((byte) bytes.length);
            hash.update(bytes);
        }
    }

    public static String operation(String button) throws BusinessException {
        if (RELATE.equals(button)) return "RELATE_INVOICE";
        if (UNRELATE.equals(button)) return "UNRELATE_INVOICE";
        throw new BusinessException("采购发票关联按钮不受支持。");
    }

    public boolean isRelate() { return RELATE.equals(button); }
    public String getButton() { return button; }
    public String getInvoicePk() { return invoicePk; }
    public String getTs() { return ts; }
    public String getPreviousNumbers() { return previousNumbers; }
    public String getPreviousIds() { return previousIds; }
    public String getPreviousState() { return previousState; }
    public Amounts getOriginal() { return original; }
    public List<Target> getTargets() { return Collections.unmodifiableList(targets); }

    public Amounts selectedAmounts() {
        Amounts total = new Amounts(UFDouble.ZERO_DBL, UFDouble.ZERO_DBL, UFDouble.ZERO_DBL);
        for (Target target : targets) total = total.add(target.amounts);
        return total;
    }

    public String joined(boolean ids) {
        StringBuilder result = new StringBuilder();
        for (Target target : targets) {
            if (result.length() > 0) result.append(';');
            result.append(ids ? target.id : target.number);
        }
        return result.toString();
    }

    public static Amounts amounts(InvoiceHeaderVO head, InvoiceItemVO[] body) throws BusinessException {
        if (head.getNtotalorigmny() == null || body == null || body.length == 0) {
            throw new BusinessException("采购发票缺少价税合计或明细。");
        }
        UFDouble tax = UFDouble.ZERO_DBL;
        UFDouble net = UFDouble.ZERO_DBL;
        for (InvoiceItemVO row : body) {
            if (row == null || row.getNorigmny() == null) throw new BusinessException("采购发票明细缺少无税金额。");
            if (row.getNtax() != null) tax = tax.add(row.getNtax());
            net = net.add(row.getNorigmny());
        }
        return new Amounts(head.getNtotalorigmny(), tax, net);
    }

    public static String text(String value) { return value == null ? "" : value; }

    public static boolean safeKey(String value, int limit) {
        return value != null && value.length() <= limit && value.matches("[A-Za-z0-9_-][A-Za-z0-9_.-]*");
    }

    private static boolean validUrl(String value) {
        if (value == null || value.isEmpty()) return true;
        if (value.length() > 2000) return false;
        try {
            URI uri = URI.create(value);
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getRawUserInfo() == null;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public static final class Target implements Serializable {
        private static final long serialVersionUID = 1L;
        public final String id;
        public final String number;
        public final String pdfUrl;
        public final Amounts amounts;

        private Target(String id, String number, String pdfUrl, Amounts amounts) {
            this.id = id;
            this.number = number;
            this.pdfUrl = pdfUrl;
            this.amounts = amounts;
        }

        private static Target from(RelevanceHVO item) throws BusinessException {
            if (item == null || item.getDetails() == null) throw new BusinessException("所选数电发票缺少明细。");
            UFDouble tax = UFDouble.ZERO_DBL;
            UFDouble net = UFDouble.ZERO_DBL;
            try {
                for (RelevanceBVO row : item.getDetails()) {
                    tax = tax.add(new UFDouble(row.getSe()));
                    net = net.add(new UFDouble(row.getBhsje()));
                }
                return new Target(item.getId(), item.getFphm(), item.getPdfurl(),
                        new Amounts(new UFDouble(item.getJshj()), tax, net));
            } catch (RuntimeException ex) {
                throw new BusinessException("所选数电发票金额无效。");
            }
        }
    }

    public static final class Amounts implements Serializable {
        private static final long serialVersionUID = 1L;
        public final UFDouble gross;
        public final UFDouble tax;
        public final UFDouble net;

        Amounts(UFDouble gross, UFDouble tax, UFDouble net) {
            this.gross = gross;
            this.tax = tax;
            this.net = net;
        }

        Amounts add(Amounts other) {
            return new Amounts(gross.add(other.gross), tax.add(other.tax), net.add(other.net));
        }

        public boolean same(Amounts other) {
            return gross.compareTo(other.gross) == 0 && tax.compareTo(other.tax) == 0 && net.compareTo(other.net) == 0;
        }

        private boolean isComplete() { return gross != null && tax != null && net != null; }

        void digest(MessageDigest hash) {
            InvoiceRelationCommand.digest(hash, gross.toString(), tax.toString(), net.toString());
        }
    }
}
