package nc.itf.baseapp.qd.esign;

import nc.vo.baseapp.qd.esign.auth.IdentityConfirmationRequest;
import nc.vo.baseapp.qd.esign.auth.IdentityConfirmationResult;
import nc.vo.pub.BusinessException;

/** 服务端内部签名认证入口，锁定判断由持有签名请求行锁的调用方提供。 */
public interface ISignatureIdentityConfirmationService {
    IdentityConfirmationResult confirm(IdentityConfirmationRequest request, boolean lockOnFailure)
            throws BusinessException;
}
