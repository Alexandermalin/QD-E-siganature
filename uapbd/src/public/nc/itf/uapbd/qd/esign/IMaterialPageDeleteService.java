package nc.itf.uapbd.qd.esign;

import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.MaterialPageDeleteCommand;
import nc.vo.uapbd.qd.esign.MaterialPageDeleteResult;

/** NONE dispatcher for a batch whose native delete calls commit independently. */
public interface IMaterialPageDeleteService {
    MaterialPageDeleteResult execute(String requestId, MaterialPageDeleteCommand command) throws BusinessException;
    MaterialPageDeleteResult getResult(String requestId) throws BusinessException;
}
