package nc.bs.uapbd.qd.esign.core;
import nc.vo.pub.BusinessException;
/** 无事务调度入口的环境探针接口，便于生产环境检查原生事务注册表、离线测试注入替身。 */
public interface TransactionBoundaryProbe {
    /** 要求当前线程不存在外层活动事务，防止一次性认领与后续业务意外合并为同一事务。 */
    void assertNoTransaction() throws BusinessException; }
