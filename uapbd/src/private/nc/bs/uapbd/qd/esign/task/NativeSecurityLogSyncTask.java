package nc.bs.uapbd.qd.esign.task;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import nc.bs.framework.common.NCLocator;
import nc.bs.pub.pa.PreAlertObject;
import nc.bs.pub.pa.PreAlertReturnType;
import nc.bs.pub.taskcenter.BgWorkingContext;
import nc.bs.pub.taskcenter.IBackgroundWorkPlugin;
import nc.bs.uapbd.qd.esign.persistence.NativeSecurityLogImporter;
import nc.itf.uapbd.qd.esign.IArchiveMaintenanceService;
import nc.vo.pub.BusinessException;

/** 原生登录、注销日志增量同步任务，来源时间不带时区，必须配置并核对实际 NC 服务器日志时区。 */
public final class NativeSecurityLogSyncTask implements IBackgroundWorkPlugin {
    /** sourceTimeZone：原生日志时间采用的时区，默认 Asia/Shanghai，需现场核实。 */
    private String sourceTimeZone="Asia/Shanghai";
    /** 设置原生日志的明确来源时区，部署时需与实际日志时间约定一致。 */
    public void setSourceTimeZone(String value) { NativeSecurityLogImporter.zone(value);sourceTimeZone=value; }
    /** 检查后台任务环境，在固定批次上限内调用本地维护服务，失败向 NC 任务引擎传播，交由现场调度策略重试或核对。 */
    public PreAlertObject executeTask(BgWorkingContext context) throws BusinessException {
        TaskEnvironment.require(context);SimpleDateFormat format=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.ENGLISH);
        format.setTimeZone(NativeSecurityLogImporter.zone(sourceTimeZone));String cutoff=format.format(new Date());
        IArchiveMaintenanceService service=NCLocator.getInstance().lookup(IArchiveMaintenanceService.class);
        int count,total=0,batches=0;
        do {
            if(++batches>10000) { throw new BusinessException("Native log sync safety ceiling reached; inspect cursor before retry"); }
            count=service.syncNativeSecurity(context.getGroupId(),sourceTimeZone,cutoff,900);total+=count;
        } while(count==900);
        String message="QD native security source records examined="+total;
        context.setLogStr(message);PreAlertObject response=new PreAlertObject();
        response.setReturnType(PreAlertReturnType.RETURNMESSAGE);response.setReturnObj(message);return response;
    }
}
