package nc.impl.uapbd.qd.esign;

import java.util.List;
import java.util.Map;
import nc.bs.framework.common.NCLocator;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.bs.uapbd.qd.esign.core.MaterialImageFolder;
import nc.bs.uapbd.qd.esign.persistence.MaterialImageCleanupStore;
import nc.bs.uapbd.qd.esign.persistence.MaterialImageCleanupStore.ManifestStatus;
import nc.itf.bd.imageSystem.IImageSystemService;
import nc.itf.uapbd.qd.esign.IMaterialImageCleanupService;
import nc.vo.pub.BusinessException;
import nc.vo.pub.filesystem.NCFileVO;

/**
 * 物料图片提交后清理服务。
 * 目录级身份不足以安全删除子文件，且 NC 的目录删除会吞掉存储异常；
 * 在文件级身份、条件删除和存储核验完成之前，将任务转为待核对，不触碰文件。
 */
public final class MaterialImageCleanupServiceImpl implements IMaterialImageCleanupService {
    /** 单个目录的最多重试次数，超过后转 NEEDS_REVIEW 由人工核对。 */
    static final int MAX_ATTEMPTS = 10;

    public int cleanupBatch(String groupId,int batchSize) throws BusinessException {
        EsignValues.require(groupId!=null && batchSize>0 && batchSize<=900,"图片清理批次参数不正确。");
        MaterialImageCleanupStore store=new MaterialImageCleanupStore();
        store.holdExpired(groupId);
        IImageSystemService images=NCLocator.getInstance().lookup(IImageSystemService.class);
        List<Map<String,Object>> tasks=store.loadPendingBatch(groupId,batchSize);
        int processed=0;
        for(Map<String,Object> task:tasks) {
            String token=EsignValues.id(),id=EsignValues.text(task,"cleanup_id");
            if(store.claim(groupId,id,token,EsignValues.utc(System.currentTimeMillis()+15L*60000L))) {
                process(store,images,task,token);processed++;
            }
        }
        return processed;
    }

    /** 处理单个任务；失去租约时不覆盖其他执行者的结果。数据库错误仍向调用方报告。 */
    private static int process(MaterialImageCleanupStore store,IImageSystemService images,
            Map<String,Object> task,String token) throws BusinessException {
        String id=EsignValues.text(task,"cleanup_id"),pk=EsignValues.text(task,"object_pk");
        String path=EsignValues.text(task,"folder_path");
        int attempts=(int)EsignValues.number(task,"attempt_count");
        try {
            if(!MaterialImageFolder.folderPath(pk).equals(path)) {
                store.finish(id,token,"NEEDS_REVIEW","登记目录与物料主键对应的原生目录不一致。");
                return 0;
            }
            if(store.materialStillExists(pk)) {
                store.finish(id,token,"NEEDS_REVIEW","物料主档行已恢复或被重建，禁止删除其图片目录。");
                return 0;
            }
            MaterialImageFolder recorded=MaterialImageFolder.recorded(path,
                EsignValues.text(task,"folder_pk"),EsignValues.number(task,"folder_version"));
            Decision decision=decide(store.materialStillExists(pk),
                images.queryFileVOByPkFilePath(path),recorded);
            if(decision==Decision.FOLDER_MISSING) {
                store.finish(id,token,"NEEDS_REVIEW","目录记录不存在，无法证明物理文件已删除，请核对文件存储。"); return 0;
            }
            if(decision==Decision.MATERIAL_RESTORED) {
                store.finish(id,token,"NEEDS_REVIEW","物料主档行已恢复或被重建，禁止删除其图片目录。"); return 0;
            }
            if(decision==Decision.IDENTITY_CHANGED) {
                store.finish(id,token,"NEEDS_REVIEW","目录身份或版本与登记时不一致，请核对。"); return 0;
            }
            ManifestStatus status=store.compareManifest(id,recorded);
            String message;
            switch(status) {
                case MISSING: message="任务缺少登记时的图片清单，请核对。";break;
                case CHANGED: message="当前图片清单与登记证据不一致，请核对新增、移除或更新的文件。";break;
                default: message="当前文件元数据与登记清单一致，物理存储仍需核对。";
            }
            store.finish(id,token,"NEEDS_REVIEW",message);
            return 0;
        } catch(BusinessException failure) {
            return retryOrHold(store,id,token,attempts,failure.getMessage());
        } catch(RuntimeException failure) {
            return retryOrHold(store,id,token,attempts,String.valueOf(failure.getMessage()));
        }
    }

    /** 目录身份匹配仍不足以证明文件和存储版本可安全删除。 */
    static Decision decide(boolean materialExists,NCFileVO current,MaterialImageFolder recorded) {
        if(materialExists) { return Decision.MATERIAL_RESTORED; }
        if(current==null) { return Decision.FOLDER_MISSING; }
        return recorded.isSameNode(current) ? Decision.REVIEW_UNVERIFIED : Decision.IDENTITY_CHANGED;
    }

    /** 所有分支都保留图片，直到文件级条件删除完成验证。 */
    enum Decision { MATERIAL_RESTORED, FOLDER_MISSING, IDENTITY_CHANGED, REVIEW_UNVERIFIED }

    /** 登记一次退避重试；达到上限转待核对，保留现场证据供人工处理。 */
    private static int retryOrHold(MaterialImageCleanupStore store,String id,String token,int attempts,String message)
            throws BusinessException {
        attempts=attempts+1;
        if(attempts>=MAX_ATTEMPTS) { store.finish(id,token,"NEEDS_REVIEW","重试达到上限："+message); return 0; }
        store.retry(id,token,attempts,message,nextRetryUtc(attempts));
        return 0;
    }

    /** 线性退避：按尝试次数成倍增加分钟数，最长一天，避免高频冲击文件存储。 */
    static String nextRetryUtc(int attempts) {
        long minutes=Math.min(60L*attempts,1440L);
        return EsignValues.utc(System.currentTimeMillis()+minutes*60000L);
    }
}
