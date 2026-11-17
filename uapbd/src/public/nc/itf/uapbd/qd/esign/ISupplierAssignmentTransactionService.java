package nc.itf.uapbd.qd.esign;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import nc.vo.pub.BusinessException;

/** Local CMT contract: claim and each archive/target operation use distinct transactions. */
public interface ISupplierAssignmentTransactionService {
    Claim claim_RequiresNew(String requestId) throws BusinessException;
    void apply_RequiresNew(String requestId,String token,String objectId) throws BusinessException;
    void verifyUnit_RequiresNew(String requestId,String token,String objectId) throws BusinessException;
    void recordUncommitted_RequiresNew(String requestId,String token,String objectId,boolean rolledBack) throws BusinessException;
    void finish_RequiresNew(String requestId,String token) throws BusinessException;

    final class Claim {
        private final String token;private final List<String> objectIds;
        public Claim(String token,List<String> objectIds) {
            this.token=token;this.objectIds=Collections.unmodifiableList(new ArrayList<String>(objectIds));
        }
        public String getToken() { return token; }
        public List<String> getObjectIds() { return objectIds; }
    }
}
