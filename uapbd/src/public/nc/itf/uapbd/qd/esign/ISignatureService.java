package nc.itf.uapbd.qd.esign;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.SignaturePreparation;
import nc.vo.uapbd.qd.esign.SignatureResult;
/** 签名请求的远程服务契约，负责准备、认证、取消与结果查询；签名状态和业务状态分别维护。 */
public interface ISignatureService {
    /** 检查事由、身份、策略与对象域，创建请求及对象关联；不受控时返回 BYPASS，受控请求记录各自的签名和业务初始状态。 */
    SignaturePreparation prepare(OperationContext draft) throws BusinessException;
    /** 对当前调用者确认一次密码，普通错误密码或锁定以结果返回；调用者须清理自己持有的密码数组，签名服务另行记录结果。 */
    SignatureResult confirm(String requestId,char[] password,String reason) throws BusinessException;
    /** 尝试结束仍可取消的请求；服务端依据持久化状态判断，已提交或正在执行的业务不会由此推测为失败。 */
    void cancel(String requestId,String reason) throws BusinessException;
    /** 上报允许的客户端失败码，仅在请求仍为 READY 时登记失败及结束时间。 */
    void reportBusinessFailure(String requestId,String errorCode) throws BusinessException;
    /** 查询当前调用者拥有的请求结果，分别返回签名状态与业务状态。 */
    SignatureResult getResult(String requestId) throws BusinessException;
}
