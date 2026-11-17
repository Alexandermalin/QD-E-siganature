package nc.itf.uapbd.qd.esign.spi;
import java.io.Serializable;
import java.util.List;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;

/** 服务端本地业务适配 SPI，由网关传入已持久化的权威上下文；适配器负责校验类型化载荷、委托原服务并描述返回对象。 */
public interface BusinessOperationAdapter {
    /** 在执行业务前核对命令类型、操作范围、授权对象和组织归属；不替代 NC 原生金额、状态等业务规则。 */
    void validate(OperationContext context,Serializable command) throws BusinessException;
    /** 在网关持有的 CMT 和审计作用域内委托原生业务服务，返回真实业务结果；不得另行提交或吞掉异常。 */
    Serializable execute(OperationContext context,Serializable command) throws BusinessException;
    /** 将真实业务返回值还原为与请求客户端标识对应的对象引用，供网关检查完整性并回填持久化主键及编码。 */
    List<BusinessObjectRef> describeResult(OperationContext context,Serializable result) throws BusinessException;
}
