package nc.impl.uapbd.qd.esign;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import nc.bs.uapbd.qd.esign.persistence.MonthlyArchiveStore;
import nc.bs.uapbd.qd.esign.persistence.QdEsignStore;
import nc.bs.uapbd.qd.esign.persistence.QdUtcClock;
import nc.bs.uapbd.qd.esign.persistence.NativeSecurityLogImporter;
import nc.bs.uapbd.qd.esign.security.ServerIdentity;
import nc.itf.uapbd.qd.esign.IArchiveMaintenanceService;
import nc.jdbc.framework.SQLParameter;
import nc.vo.pub.BusinessException;

/** 本地维护事务实现，将每批归档、过期请求处理及原生日志导入纳入调用方的 NC CMT，不自行提交或吞掉失败。 */
public final class ArchiveMaintenanceServiceImpl implements IArchiveMaintenanceService {
    /** 对指定集团执行 1 至 900 条的一批全主键重扫，返回扫描数量、周期完成标记及游标，补获增量水位无法覆盖的迟提交。 */
    public Map<String,Object> reconcileNativeSecurity(String groupId,String sourceTimeZone,int batchSize) throws BusinessException {
        ServerIdentity identity=ServerIdentity.current();
        if(!identity.groupId.equals(groupId)) { throw new BusinessException("Native reconciliation group mismatch"); }
        return new NativeSecurityLogImporter().reconcileBatch(groupId,sourceTimeZone,batchSize);
    }
    /** 按显式来源时区及源时间截止值增量导入指定集团安全事实，批量为 1 至 900；返回本批选中数量，事件与游标同事务提交。 */
    public int syncNativeSecurity(String groupId,String sourceTimeZone,String sourceCutoff,int batchSize) throws BusinessException {
        ServerIdentity identity=ServerIdentity.current();
        if(!identity.groupId.equals(groupId)) { throw new BusinessException("Native security sync group mismatch"); }
        return new NativeSecurityLogImporter().importBatch(groupId,sourceTimeZone,sourceCutoff,batchSize);
    }
    /**
     * 归档指定北京时间月份的有限批记录，batchSize 为 1 至 900；返回每表移动量、remaining 可归档量及 pending_open 未结束数，历史表须预先建立。
     */
    public Map<String,Long> archiveBatch(String month,int batchSize) throws BusinessException {
        ServerIdentity.current();
        return new MonthlyArchiveStore().archiveMonth(month,new Date(),batchSize);
    }
    /** 分批结束指定集团未执行的过期请求，保留既有认证成功事实，不处理无法推断结果的 RUNNING 请求。 */
    public int expireBatch(String groupId,int batchSize) throws BusinessException {
        ServerIdentity identity=ServerIdentity.current();
        if(!identity.groupId.equals(groupId)||batchSize<1||batchSize>900) {
            throw new BusinessException("Invalid expiration task scope or batch size");
        }
        String now=QdUtcClock.now(); QdEsignStore store=new QdEsignStore(); SQLParameter p=new SQLParameter();
        p.addParam(groupId);p.addParam(now);p.addParam(batchSize);
        List<Map<String,Object>> rows=store.list("SELECT request_id FROM (SELECT request_id FROM qd_es_request"
            + " WHERE pk_group=? AND expires_at<? AND business_state IN ('PENDING','READY') ORDER BY request_id) WHERE ROWNUM<=?",p);
        int changed=0;
        for(Map<String,Object> row:rows) {
            String id=String.valueOf(row.get("request_id")); Map<String,Object> request=store.loadRequest(id,true);
            if(request==null||!groupId.equals(request.get("pk_group"))) { throw new BusinessException("Expiration request scope changed"); }
            String business=String.valueOf(request.get("business_state"));
            if((!"PENDING".equals(business)&&!"READY".equals(business))
                    ||String.valueOf(request.get("expires_at")).compareTo(now)>=0) { continue; }
            Map<String,Object> updates=new LinkedHashMap<String,Object>();
            updates.put("business_state","EXPIRED");updates.put("finished_at",now);updates.put("error_code","REQUEST_EXPIRED");
            if("PENDING".equals(request.get("signature_state"))) { updates.put("signature_state","EXPIRED"); }
            // 请求过期也不能抹去先前 SUCCESS 的认证事实，只结束尚未执行的业务授权。
            store.updateRequest(id,updates);changed++;
        }
        return changed;
    }
}
