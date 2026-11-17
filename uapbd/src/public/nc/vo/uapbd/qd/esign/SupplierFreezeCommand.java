package nc.vo.uapbd.qd.esign;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import nc.vo.bd.supplier.SupplierVO;

/** The native supplier operation and its explicitly selected target organizations. */
public final class SupplierFreezeCommand implements Serializable {
    private static final long serialVersionUID=1L;
    private final SupplierVO supplier;
    private final List<String> targetOrganizations;
    public SupplierFreezeCommand(SupplierVO supplier,List<String> targetOrganizations) {
        if(supplier==null || targetOrganizations==null || targetOrganizations.isEmpty())
            throw new IllegalArgumentException("Supplier and target organizations are required");
        this.supplier=supplier;
        this.targetOrganizations=Collections.unmodifiableList(new ArrayList<String>(targetOrganizations));
    }
    public SupplierVO getSupplier() { return supplier; }
    public List<String> getTargetOrganizations() { return targetOrganizations; }
}
