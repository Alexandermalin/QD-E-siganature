package nc.ui.pu.qd.esign;

import java.util.List;
import java.util.Objects;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;

/** Compares the client selection with the same scope returned across the NC service boundary. */
public final class ProcurementAuthorizationScope {
    private ProcurementAuthorizationScope() { }

    public static boolean matches(OperationContext draft, OperationContext actual) {
        if (actual == null || !Objects.equals(draft.getNodeCode(), actual.getNodeCode())
                || !Objects.equals(draft.getButtonCode(), actual.getButtonCode())
                || !Objects.equals(draft.getOperationType(), actual.getOperationType())
                || !Objects.equals(draft.getGroupId(), actual.getGroupId())
                || !Objects.equals(draft.getOrgId(), actual.getOrgId())) return false;
        List<BusinessObjectRef> expected = draft.getObjects();
        List<BusinessObjectRef> returned = actual.getObjects();
        if (expected.size() != returned.size()) return false;
        for (int i = 0; i < expected.size(); i++) {
            BusinessObjectRef a = expected.get(i);
            BusinessObjectRef b = returned.get(i);
            if (b == null || !Objects.equals(a.getClientObjectId(), b.getClientObjectId())
                    || !Objects.equals(a.getObjectType(), b.getObjectType())
                    || !Objects.equals(a.getObjectPk(), b.getObjectPk())
                    || !Objects.equals(a.getBillCode(), b.getBillCode())
                    || !Objects.equals(a.getParentClientObjectId(), b.getParentClientObjectId())) return false;
        }
        return true;
    }
}
