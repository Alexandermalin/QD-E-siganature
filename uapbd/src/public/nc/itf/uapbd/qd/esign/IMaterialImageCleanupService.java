package nc.itf.uapbd.qd.esign;

import nc.vo.pub.BusinessException;

/** 物料图片提交后清理的本地维护服务契约；只处理已登记的待清理目录，不扩大删除范围。 */
public interface IMaterialImageCleanupService {
    /**
     * 在固定批次上限内处理一个集团的待清理任务。
     * 返回本批已认领处理的任务数；当前未验证文件级条件删除，任务只会转为待核对，不会删除图片。
     */
    int cleanupBatch(String groupId,int batchSize) throws BusinessException;
}
