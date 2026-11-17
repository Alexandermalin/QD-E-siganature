package nc.vo.uapbd.qd.esign;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Receipt for independently committed material and target pairs. */
public final class MaterialCancelAssignmentResult implements Serializable {
    private static final long serialVersionUID=1L;
    private final String requestId,state;private final List<Unit> units;
    public MaterialCancelAssignmentResult(String requestId,String state,List<Unit> units) {
        this.requestId=requestId;this.state=state;this.units=Collections.unmodifiableList(new ArrayList<Unit>(units));
    }
    public String getRequestId() { return requestId; }
    public String getState() { return state; }
    public List<Unit> getUnits() { return units; }
    public static boolean isRoute(String node,String button,String operation) {
        return ("10140MAG".equals(node) || "10140MAO".equals(node))
            && "CancelAssign".equals(button) && "CANCEL_ASSIGN".equals(operation);
    }
    public static final class Unit implements Serializable {
        private static final long serialVersionUID=1L;
        private final String material,code,target,state,errorCode;
        public Unit(String material,String code,String target,String state,String errorCode) {
            this.material=material;this.code=code;this.target=target;this.state=state;this.errorCode=errorCode;
        }
        public String getMaterial() { return material; }
        public String getCode() { return code; }
        public String getTarget() { return target; }
        public String getState() { return state; }
        public String getErrorCode() { return errorCode; }
    }
}

