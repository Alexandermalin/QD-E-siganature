package nc.vo.purp.qd.esign;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;

/** Authorizes removing rows from the editor before the operator saves the batch. */
public final class SupplierPriceEditDeleteCommand implements Serializable {
    private static final long serialVersionUID = 1L;
    public static final String STAGE_DELETE = "STAGE_DELETE";

    private final ArrayList<BusinessObjectRef> objects;

    public SupplierPriceEditDeleteCommand(List<BusinessObjectRef> objects) {
        this.objects = new ArrayList<BusinessObjectRef>(objects);
    }

    public List<BusinessObjectRef> getObjects() {
        return Collections.unmodifiableList(objects);
    }
}
