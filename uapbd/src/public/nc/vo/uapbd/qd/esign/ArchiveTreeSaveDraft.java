package nc.vo.uapbd.qd.esign;

import java.io.Serializable;

/** Fields that can change the set of rows touched by a tree save. */
public final class ArchiveTreeSaveDraft implements Serializable {
    private static final long serialVersionUID=1L;
    private final String code,parent,warehouse;
    public ArchiveTreeSaveDraft(String code,String parent,String warehouse) {
        this.code=code;this.parent=parent;this.warehouse=warehouse;
    }
    public String getCode() { return code; }
    public String getParent() { return parent; }
    public String getWarehouse() { return warehouse; }
}
