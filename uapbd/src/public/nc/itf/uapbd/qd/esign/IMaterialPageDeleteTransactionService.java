package nc.itf.uapbd.qd.esign;

import java.util.List;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;

/** Local CMT services; each page row and its successful audit share one commit. */
public interface IMaterialPageDeleteTransactionService {
    Claim claim_RequiresNew(String requestId) throws BusinessException;
    void apply_RequiresNew(String requestId, String token, String objectId, SuperVO row) throws BusinessException;
    /** Read the unit and required audit after its business transaction has returned. */
    void verifyUnit_RequiresNew(String requestId,String token,String objectId) throws BusinessException;
    void recordUncommitted_RequiresNew(String requestId, String token, String objectId, boolean rolledBack) throws BusinessException;
    void skip_RequiresNew(String requestId, String token, String objectId) throws BusinessException;
    void finish_RequiresNew(String requestId, String token) throws BusinessException;

    final class Claim {
        private final String token;
        private final List<UnitKey> units;
        public Claim(String token, List<UnitKey> units) { this.token=token; this.units=units; }
        public String getToken() { return token; }
        public List<UnitKey> getUnits() { return units; }
    }
    final class UnitKey {
        private final String objectId, clientObjectId;
        public UnitKey(String objectId, String clientObjectId) { this.objectId=objectId; this.clientObjectId=clientObjectId; }
        public String getObjectId() { return objectId; }
        public String getClientObjectId() { return clientObjectId; }
    }
}
