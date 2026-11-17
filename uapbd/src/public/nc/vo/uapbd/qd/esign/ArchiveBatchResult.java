package nc.vo.uapbd.qd.esign;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import nc.bs.bd.service.ValueObjWithErrLog;

/** Per-archive result of one native batch transaction; it is not proof of commit until the gateway returns. */
public final class ArchiveBatchResult implements Serializable {
    private static final long serialVersionUID=1L;
    private final ValueObjWithErrLog nativeResult;
    private final List<Unit> units;
    public ArchiveBatchResult(ValueObjWithErrLog nativeResult,List<Unit> units) {
        if(units==null) throw new IllegalArgumentException("Batch units are required");
        this.nativeResult=nativeResult;this.units=Collections.unmodifiableList(new ArrayList<Unit>(units));
    }
    public ValueObjWithErrLog getNativeResult() { return nativeResult; }
    public List<Unit> getUnits() { return units; }
    public static boolean isCustomerFreeze(String node,String button,String operation) {
        return ("10140CUG".equals(node) || "10140CUO".equals(node))
            && (("Freeze".equals(button) && "FREEZE".equals(operation))
                || ("Unfreeze".equals(button) && "UNFREEZE".equals(operation)));
    }
    public static boolean isSupplierFreeze(String node,String button,String operation) {
        return ("10140SUG".equals(node) || "10140SUO".equals(node))
            && (("Freeze".equals(button) && "FREEZE".equals(operation))
                || ("Unfreeze".equals(button) && "UNFREEZE".equals(operation)));
    }
    public static boolean isPartnerState(String node,String button,String operation) {
        return ("10140CUG".equals(node) || "10140CUO".equals(node) || "10140SUG".equals(node) || "10140SUO".equals(node))
            && (("Enable".equals(button) && "ENABLE".equals(operation))
                || ("Disable".equals(button) && "DISABLE".equals(operation)));
    }
    public static boolean isMaterialState(String node,String button,String operation) {
        return ("10140MAG".equals(node) || "10140MAO".equals(node))
            && (("Enable".equals(button) && "ENABLE".equals(operation))
                || ("Disable".equals(button) && "DISABLE".equals(operation)));
    }
    public static boolean isWarehouseState(String node,String button,String operation) {
        return "10140WARH".equals(node) && (("Enable".equals(button) && "ENABLE".equals(operation))
            || ("Disable".equals(button) && "DISABLE".equals(operation)));
    }
    public static boolean isArchiveBatch(String node,String button,String operation) {
        return isCustomerFreeze(node,button,operation) || isSupplierFreeze(node,button,operation)
            || isPartnerState(node,button,operation) || isMaterialState(node,button,operation) || isWarehouseState(node,button,operation)
            || isPartnerBatchUpdate(node,button,operation);
    }
    public static boolean isPartnerBatchUpdate(String node,String button,String operation) {
        return ("10140CUG".equals(node) || "10140CUO".equals(node) || "10140SUG".equals(node) || "10140SUO".equals(node)
            || "10140MAG".equals(node) || "10140MAO".equals(node))
            && "BatchUpdate".equals(button) && "UPDATE".equals(operation);
    }
    public static final class Unit implements Serializable {
        private static final long serialVersionUID=1L;
        private final String clientId,objectPk,state;
        public Unit(String clientId,String objectPk,String state) { this.clientId=clientId;this.objectPk=objectPk;this.state=state; }
        public String getClientId() { return clientId; }
        public String getObjectPk() { return objectPk; }
        public String getState() { return state; }
    }
}
