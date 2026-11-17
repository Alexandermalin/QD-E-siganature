package nc.itf.uapbd.qd.esign;
import java.io.Serializable;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.OperationResponse;
/** 受控业务的远程无事务调度入口，先完成独立的一次性认领，再进入业务 CMT，防止失败后复用授权重复执行。 */
public interface IOperationService {
    /** 以已准备的请求标识和类型化命令执行业务；服务端先独立提交执行认领，再进入业务事务，重复成功请求只读取已提交对象。 */
    OperationResponse execute(String requestId,Serializable businessCommand) throws BusinessException;
}
