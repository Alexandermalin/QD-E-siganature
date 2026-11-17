package nc.bs.uapbd.qd.esign.task;

import nc.bs.framework.common.InvocationInfoProxy;
import nc.bs.pub.taskcenter.BgWorkingContext;
import nc.vo.pub.BusinessException;

/** 后台任务执行环境校验工具，要求存在可用的集团和数据源上下文，避免缺少身份范围时执行维护。 */
final class TaskEnvironment {
    /** 工具类不提供实例，使用静态方法完成本类职责。 */
    private TaskEnvironment() { }
    /** 要求后台工作上下文与当前原生调用身份具备必要集团和数据源，缺失时拒绝执行维护。 */
    static void require(BgWorkingContext context) throws BusinessException {
        InvocationInfoProxy invocation=InvocationInfoProxy.getInstance();
        if(context==null||context.getDatasource()==null||context.getGroupId()==null
                ||!context.getDatasource().equals(invocation.getUserDataSource())
                ||!context.getGroupId().equals(invocation.getGroupId())
                ||invocation.getUserId()==null) {
            throw new BusinessException("NC task requires an authenticated matching datasource/group context");
        }
    }
}
