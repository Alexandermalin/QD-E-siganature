package nc.bs.uapbd.qd.esign.task;

import nc.bs.framework.common.NCLocator;
import nc.bs.pub.pa.PreAlertObject;
import nc.bs.pub.pa.PreAlertReturnType;
import nc.bs.pub.taskcenter.BgWorkingContext;
import nc.bs.pub.taskcenter.IBackgroundWorkPlugin;
import nc.itf.uapbd.qd.esign.IArchiveMaintenanceService;
import nc.vo.pub.BusinessException;

/** 仅结束未执行且已过期授权的维护任务，不推测 RUNNING 或外部操作的实际结果，也不覆盖已成功认证的事实。 */
public final class ExpiredRequestTask implements IBackgroundWorkPlugin {
    /** 检查后台任务环境，在固定批次上限内调用本地维护服务，失败向 NC 任务引擎传播，交由现场调度策略重试或核对。 */
    public PreAlertObject executeTask(BgWorkingContext context) throws BusinessException {
        TaskEnvironment.require(context);IArchiveMaintenanceService service=NCLocator.getInstance().lookup(IArchiveMaintenanceService.class);
        int changed,total=0,batches=0;
        do {
            if(++batches>10000) { throw new BusinessException("Expiration safety ceiling reached; inspect task state"); }
            changed=service.expireBatch(context.getGroupId(),900);total+=changed;
        } while(changed==900);
        String summary="QD unused requests expired="+total;context.setLogStr(summary);
        PreAlertObject response=new PreAlertObject();response.setReturnType(PreAlertReturnType.RETURNMESSAGE);response.setReturnObj(summary);return response;
    }
}
