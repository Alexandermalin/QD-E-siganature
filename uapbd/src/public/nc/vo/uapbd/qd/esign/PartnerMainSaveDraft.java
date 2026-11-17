package nc.vo.uapbd.qd.esign;

import java.io.Serializable;

/** Draft attributes needed to discover native customer/supplier main-save side effects. */
public final class PartnerMainSaveDraft implements Serializable {
    private static final long serialVersionUID=1L;
    private final String parent;private final boolean counterpart;
    public PartnerMainSaveDraft(String parent,boolean counterpart) { this.parent=normalizeParent(parent);this.counterpart=counterpart; }
    public static String normalizeParent(String parent) {
        return parent==null || parent.trim().isEmpty() || "~".equals(parent)?null:parent;
    }
    public String getParent() { return parent; }
    public boolean hasCounterpart() { return counterpart; }
}
