package nc.vo.uapbd.qd.esign;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Durable per-bill receipts for a single signed outbound-request batch. */
public final class SapplyBatchOutcome implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String requestId, state;
    private final List<Unit> units;

    public SapplyBatchOutcome(String requestId, String state, List<Unit> units) {
        this.requestId = requestId;
        this.state = state;
        this.units = Collections.unmodifiableList(new ArrayList<Unit>(units));
    }

    public String getRequestId() { return requestId; }
    public String getState() { return state; }
    public List<Unit> getUnits() { return units; }

    public static final class Unit implements Serializable {
        private static final long serialVersionUID = 1L;
        private final BusinessObjectRef bill;
        private final String state, errorCode;

        public Unit(BusinessObjectRef bill, String state, String errorCode) {
            this.bill = bill;
            this.state = state;
            this.errorCode = errorCode;
        }

        public BusinessObjectRef getBill() { return bill; }
        public String getState() { return state; }
        public String getErrorCode() { return errorCode; }
    }
}
