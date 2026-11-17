package nc.itf.uapbd.qd.esign;

import java.util.Map;
import nc.vo.pub.BusinessException;

/** 仅本地注册的 CMT 维护接口，用于分批归档、过期请求清理及原生安全日志同步与对账，禁止作为远程用户服务暴露。 */
public interface IArchiveMaintenanceService {
    /**
     * 归档指定北京时间月份的有限批记录，batchSize 为 1 至 900；返回每表移动量、remaining 可归档量及 pending_open 未结束数，历史表须预先建立。
     */
    Map<String,Long> archiveBatch(String month,int batchSize) throws BusinessException;
    /** 分批结束指定集团未执行的过期请求，保留既有认证成功事实，不处理无法推断结果的 RUNNING 请求。 */
    int expireBatch(String groupId,int batchSize) throws BusinessException;
    /** 按显式来源时区及源时间截止值增量导入指定集团安全事实，批量为 1 至 900；返回本批选中数量，事件与游标同事务提交。 */
    int syncNativeSecurity(String groupId,String sourceTimeZone,String sourceCutoff,int batchSize) throws BusinessException;
    /** 对指定集团执行 1 至 900 条的一批全主键重扫，返回扫描数量、周期完成标记及游标，补获增量水位无法覆盖的迟提交。 */
    Map<String,Object> reconcileNativeSecurity(String groupId,String sourceTimeZone,int batchSize) throws BusinessException;
}
