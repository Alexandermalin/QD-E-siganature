package nc.bs.uapbd.qd.esign.task;

import nc.bs.framework.common.NCLocator;
import nc.bs.pub.pa.PreAlertObject;
import nc.bs.pub.pa.PreAlertReturnType;
import nc.bs.pub.taskcenter.BgWorkingContext;
import nc.bs.pub.taskcenter.IBackgroundWorkPlugin;
import nc.itf.uapbd.qd.esign.IMaterialImageCleanupService;
import nc.vo.pub.BusinessException;

/**
 * 物料图片提交后清理的后台任务。
 * 只处理已登记的待清理目录，按固定批次循环；失败任务由服务自身退避或转待核对，
 * 返回本轮处理数量；文件级删除尚未启用，目录任务会转为待核对。
 */
public final class MaterialImageCleanupTask implements IBackgroundWorkPlugin {
    public PreAlertObject executeTask(BgWorkingContext context) throws BusinessException {
        TaskEnvironment.require(context);
        IMaterialImageCleanupService service=NCLocator.getInstance().lookup(IMaterialImageCleanupService.class);
        int processed,total=0,batches=0;
        do {
            if(++batches>1000) { throw new BusinessException("Image cleanup safety ceiling reached; inspect task state"); }
            processed=service.cleanupBatch(context.getGroupId(),900);
            total+=processed;
        } while(processed>0);
        String summary="QD material image cleanup tasks processed="+total+"; check review status";
        context.setLogStr(summary);
        PreAlertObject response=new PreAlertObject();
        response.setReturnType(PreAlertReturnType.RETURNMESSAGE);
        response.setReturnObj(summary);
        return response;
    }
}
