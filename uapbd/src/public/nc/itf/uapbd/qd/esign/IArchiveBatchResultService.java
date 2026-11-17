package nc.itf.uapbd.qd.esign;

import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.ArchiveBatchStatus;

public interface IArchiveBatchResultService {
    ArchiveBatchStatus getResult(String requestId) throws BusinessException;
}
