package nc.bs.uapbd.qd.esign.task;

import java.util.Date;
import java.util.List;
import java.util.Map;
import nc.bs.framework.common.NCLocator;
import nc.bs.pub.pa.PreAlertObject;
import nc.bs.pub.pa.PreAlertReturnType;
import nc.bs.pub.taskcenter.BgWorkingContext;
import nc.bs.pub.taskcenter.IBackgroundWorkPlugin;
import nc.bs.uapbd.qd.esign.persistence.QdEsignStore;
import nc.bs.uapbd.qd.esign.persistence.QdUtcClock;
import nc.itf.uapbd.qd.esign.IArchiveMaintenanceService;
import nc.jdbc.framework.SQLParameter;
import nc.vo.pub.BusinessException;

/** 月度日志归档任务，建议每月 1 日北京时间 01:00 运行并配置每日补跑；重新访问旧月份以处理较晚结束的跨月请求。 */
public final class MonthlyArchiveTask implements IBackgroundWorkPlugin {
    /** 检查后台任务环境，在固定批次上限内调用本地维护服务，失败向 NC 任务引擎传播，交由现场调度策略重试或核对。 */
    public PreAlertObject executeTask(BgWorkingContext context) throws BusinessException {
        TaskEnvironment.require(context); QdEsignStore store=new QdEsignStore(); SQLParameter p=new SQLParameter();
        p.addParam(QdUtcClock.firstOnlineMonth(new Date()));
        // 后发生的回执可能跨月：先处理较新月份，并在后续补跑时重访所有旧月份。
        List<Map<String,Object>> months=store.list("SELECT archive_month FROM qd_es_archive WHERE archive_month<? ORDER BY archive_month DESC",p);
        IArchiveMaintenanceService maintenance=NCLocator.getInstance().lookup(IArchiveMaintenanceService.class);
        long moved=0,pending=0,blockedReceipts=0;
        for(Map<String,Object> month:months) {
            String value=String.valueOf(month.get("archive_month")); Map<String,Long> result;
            int batches=0;
            do {
                if(++batches>10000) { throw new BusinessException("Archive safety ceiling reached for " + value + "; inspect state before retry"); }
                result=maintenance.archiveBatch(value,900);
                for(Map.Entry<String,Long> count:result.entrySet()) { if(count.getKey().startsWith("qd_es_")) { moved+=count.getValue().longValue(); } }
            } while(result.get("remaining").longValue()>0);
            pending+=result.get("pending_open").longValue();
            Long blocked=result.get("blocked_receipts");if(blocked!=null)blockedReceipts+=blocked.longValue();
        }
        String summary="QD monthly archive moved="+moved+", pending open requests="+pending+", rows awaiting receipt archive="+blockedReceipts;
        context.setLogStr(summary); PreAlertObject response=new PreAlertObject();
        response.setReturnType(PreAlertReturnType.RETURNMESSAGE);response.setReturnObj(summary);return response;
    }
}
