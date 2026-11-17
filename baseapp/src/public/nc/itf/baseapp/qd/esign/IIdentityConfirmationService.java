package nc.itf.baseapp.qd.esign;

import nc.vo.baseapp.qd.esign.auth.IdentityConfirmationRequest;
import nc.vo.baseapp.qd.esign.auth.IdentityConfirmationResult;
import nc.vo.pub.BusinessException;

/** 对当前已认证 NC 调用者进行密码再确认的桥接契约，不创建新会话。普通密码错误及账号锁定以结果返回，由签名服务持久化后再决定业务流程。 */
public interface IIdentityConfirmationService {

    /** 对当前 NC 会话身份进行一次密码再确认，返回明确的认证结果；认证失败并不等同于业务异常，敏感数组在调用结束后清理。 */
    IdentityConfirmationResult confirm(IdentityConfirmationRequest request)
            throws BusinessException;
}
