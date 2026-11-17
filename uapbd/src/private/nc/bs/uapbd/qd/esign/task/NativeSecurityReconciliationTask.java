package nc.bs.uapbd.qd.esign.task;

import java.util.Map;
import nc.bs.framework.common.NCLocator;
import nc.bs.pub.pa.PreAlertObject;
import nc.bs.pub.pa.PreAlertReturnType;
import nc.bs.pub.taskcenter.BgWorkingContext;
import nc.bs.pub.taskcenter.IBackgroundWorkPlugin;
import nc.bs.uapbd.qd.esign.persistence.NativeSecurityLogImporter;
import nc.itf.uapbd.qd.esign.IArchiveMaintenanceService;
import nc.vo.pub.BusinessException;

/** 原生安全日志的全主键循环对账任务，应在频繁增量同步之外按日运行；完整周期与原生日志保留期需在现场验证。 */
public final class NativeSecurityReconciliationTask implements IBackgroundWorkPlugin {
    /** sourceTimeZone：原生日志时间采用的时区，默认 Asia/Shanghai，需现场核实。 */
    private String sourceTimeZone="Asia/Shanghai";
    /** 设置原生日志的明确来源时区，部署时需与实际日志时间约定一致。 */
    public void setSourceTimeZone(String zone) { NativeSecurityLogImporter.zone(zone);sourceTimeZone=zone; }
    /** 检查后台任务环境，在固定批次上限内调用本地维护服务，失败向 NC 任务引擎传播，交由现场调度策略重试或核对。 */
    public PreAlertObject executeTask(BgWorkingContext context) throws BusinessException {
        TaskEnvironment.require(context);IArchiveMaintenanceService service=NCLocator.getInstance().lookup(IArchiveMaintenanceService.class);
        int batches=0;long total=0;Map<String,Object> result;
        do {
            if(++batches>10000) { throw new BusinessException("Native reconciliation cycle exceeds safety ceiling; inspect cursor and retention before retry"); }
            result=service.reconcileNativeSecurity(context.getGroupId(),sourceTimeZone,900);
            total+=((Number)result.get("rows_examined")).longValue();
        } while(!Boolean.TRUE.equals(result.get("cycle_complete")));
        String message="QD native security reconciliation completed; source records examined="+total+", completed UTC="+result.get("cycle_completed_at");
        context.setLogStr(message);PreAlertObject response=new PreAlertObject();response.setReturnType(PreAlertReturnType.RETURNMESSAGE);response.setReturnObj(message);return response;
    }
}
