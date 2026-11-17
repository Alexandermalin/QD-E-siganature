package nc.itf.uapbd.qd.esign;

import java.util.List;
import nc.vo.pub.BusinessException;

/** Local CMT only. Claim and finish have separate transactions from all units. */
public interface IMaterialAssignmentTransactionService {
    Claim claim_RequiresNew(String requestId) throws BusinessException;
    void apply_RequiresNew(String requestId, String token, String objectId) throws BusinessException;
    void verifyUnit_RequiresNew(String requestId,String token,String objectId) throws BusinessException;
    void recordUncommitted_RequiresNew(String requestId, String token, String objectId, boolean rejected) throws BusinessException;
    void finish_RequiresNew(String requestId, String token) throws BusinessException;

    final class Claim {
        private final String token;
        private final List<String> objectIds;

        public Claim(String token, List<String> objectIds) {
            this.token = token;
            this.objectIds = objectIds;
        }

        public String getToken() { return token; }
        public List<String> getObjectIds() { return objectIds; }
    }
}
