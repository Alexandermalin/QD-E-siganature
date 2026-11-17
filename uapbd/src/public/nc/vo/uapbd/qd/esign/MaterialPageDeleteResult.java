package nc.vo.uapbd.qd.esign;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Outcomes of the individual native organization-page delete commits. */
public final class MaterialPageDeleteResult implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String requestId, state;
    private final List<Unit> units;
    public MaterialPageDeleteResult(String requestId, String state, List<Unit> units) {
        this.requestId = requestId; this.state = state;
        this.units = Collections.unmodifiableList(new ArrayList<Unit>(units));
    }
    public String getRequestId() { return requestId; }
    public String getState() { return state; }
    public List<Unit> getUnits() { return units; }
    public static boolean isRoute(String node, String button, String operation) {
        return "DELETE".equals(operation) && ((("10140MAG".equals(node) || "10140MAO".equals(node))
            && MaterialOrgPage.forDeleteButton(button) != null) || PartnerOrgPage.forAction(node,button,operation)!=null
            || isWarehouse(node,button,operation) || isMaterialMain(node,button,operation) || isPartnerMain(node,button,operation));
    }
    public static boolean isPartnerMain(String node,String button,String operation) {
        return ("10140CUG".equals(node) || "10140CUO".equals(node) || "10140SUG".equals(node) || "10140SUO".equals(node))
            && "Delete".equals(button) && "DELETE".equals(operation);
    }
    public static boolean isMaterialMain(String node,String button,String operation) {
        return ("10140MAG".equals(node) || "10140MAO".equals(node)) && "Delete".equals(button) && "DELETE".equals(operation);
    }
    public static boolean isWarehouse(String node,String button,String operation) {
        return "10140WARH".equals(node) && "Delete".equals(button) && "DELETE".equals(operation);
    }
    public static final class Unit implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String clientObjectId, objectPk, materialCode, targetOrg, state, errorCode;
        public Unit(String clientObjectId, String objectPk, String materialCode, String targetOrg, String state, String errorCode) {
            this.clientObjectId=clientObjectId; this.objectPk=objectPk; this.materialCode=materialCode;
            this.targetOrg=targetOrg; this.state=state; this.errorCode=errorCode;
        }
        public String getClientObjectId() { return clientObjectId; }
        public String getObjectPk() { return objectPk; }
        public String getMaterialCode() { return materialCode; }
        public String getTargetOrg() { return targetOrg; }
        public String getState() { return state; }
        public String getErrorCode() { return errorCode; }
    }
}
