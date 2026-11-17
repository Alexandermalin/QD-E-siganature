package nc.ui.pu.qd.esign;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collections;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;

public final class ProcurementAuthorizationScopeTest {
    public static void main(String[] args) throws Exception {
        OperationContext draft = context(null, "invoice:digest", "40041000", "RelevanceInvoice");
        OperationContext authorized = context("request", "invoice:digest", "40041000", "RelevanceInvoice");
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(buffer)) {
            out.writeObject(authorized);
        }
        OperationContext remote;
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(buffer.toByteArray()))) {
            remote = (OperationContext) in.readObject();
        }
        if (draft.getObjects().equals(remote.getObjects()) || !ProcurementAuthorizationScope.matches(draft, remote)) {
            throw new AssertionError("authorization must match after crossing the service boundary");
        }
        if (ProcurementAuthorizationScope.matches(draft, context("request", "invoice:changed", "40041000", "RelevanceInvoice"))
                || ProcurementAuthorizationScope.matches(draft, context("request", "invoice:digest", "40041000", "UnRelevanceInvoice"))) {
            throw new AssertionError("changed selection or route must be refused");
        }
    }

    private static OperationContext context(String request, String object, String node, String button) {
        BusinessObjectRef ref = new BusinessObjectRef(object, "PU_PURCHASE_INVOICE", "invoice", "BILL", null);
        return new OperationContext(request, null, "G", "O", node, "Invoice", button, button, "RELATE_INVOICE",
                null, Collections.singletonList(ref));
    }
}
