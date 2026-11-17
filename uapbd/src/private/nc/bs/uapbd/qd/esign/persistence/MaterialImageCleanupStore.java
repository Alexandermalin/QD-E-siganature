package nc.bs.uapbd.qd.esign.persistence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import nc.bs.dao.BaseDAO;
import nc.bs.dao.DAOException;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.bs.uapbd.qd.esign.core.MaterialImageFolder;
import nc.jdbc.framework.SQLParameter;
import nc.jdbc.framework.processor.MapListProcessor;
import nc.vo.pub.BusinessException;

/**
 * 物料图片提交后清理任务表 qd_es_img_cleanup 的数据访问。
 * 登记随业务 CMT 提交，审计失败回滚时任务一并消失，图片从未被删除；
 * 当前后台仅核对并保留待处理证据，本类不拥有独立提交权。
 */
public class MaterialImageCleanupStore {
    private static final String[] FILE_FIELDS={"filepath","isfolder","isdoc","pk_doc","modifytime","lasttime","filelength"};

    /** NC 原生数据访问入口加入调用方事务；接口用于离线核验 SQL 参数。 */
    interface Database {
        List<Map<String,Object>> query(String sql,SQLParameter parameters) throws DAOException;
        int update(String sql,SQLParameter parameters) throws DAOException;
    }
    private final Database db;

    public MaterialImageCleanupStore() { this(new BaseDAO()); }
    public MaterialImageCleanupStore(final BaseDAO dao) {
        if (dao == null) { throw new IllegalArgumentException("dao is required"); }
        dao.setAddTimeStamp(false);
        this.db=new Database() {
            @SuppressWarnings("unchecked") public List<Map<String,Object>> query(String sql,SQLParameter parameters) throws DAOException {
                return (List<Map<String,Object>>)dao.executeQuery(sql,parameters,new MapListProcessor());
            }
            public int update(String sql,SQLParameter parameters) throws DAOException { return dao.executeUpdate(sql,parameters); }
        };
    }
    MaterialImageCleanupStore(Database db) { if(db==null) throw new IllegalArgumentException("database is required");this.db=db; }

    /** 在业务事务内登记待清理目录；同一请求和物料的重复登记保持幂等。 */
    public void enqueue(String requestId,String groupId,String objectType,String objectPk,MaterialImageFolder folder)
            throws BusinessException {
        EsignValues.require(requestId != null && requestId.trim().length() == 32 && groupId != null
            && objectType != null && objectPk != null && folder != null, "图片清理任务登记信息不完整。");
        Map<String,Object> existing = one("SELECT cleanup_id,request_id FROM qd_es_img_cleanup WHERE request_id=? AND object_pk=?",
            requestId,objectPk);
        if (existing != null) {
            EsignValues.require(requestId.equals(existing.get("request_id")), "物料图片清理任务已存在。");
            return;
        }
        SQLParameter parameters = new SQLParameter();
        String cleanupId=EsignValues.id();
        parameters.addParam(cleanupId);
        parameters.addParam(requestId);
        parameters.addParam(groupId);
        parameters.addParam(objectType);
        parameters.addParam(objectPk);
        parameters.addParam(folder.getFullPath());
        parameters.addParam(folder.getNodePk());
        parameters.addParam(Long.valueOf(folder.getModifiedAt()));
        parameters.addParam("PENDING");
        parameters.addParam(EsignValues.utc());
        int count;
        try {
            count = db.update("INSERT INTO qd_es_img_cleanup (cleanup_id,request_id,pk_group,object_type,"
                + "object_pk,folder_path,folder_pk,folder_version,status,created_at) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?)", parameters);
        } catch (DAOException failure) {
            throw new BusinessException("物料图片清理任务登记失败：" + failure.getMessage(), failure);
        }
        EsignValues.require(count == 1, "物料图片清理任务登记失败。");
        captureItems(cleanupId,folder);
    }

    /** A failed file-system probe must not erase evidence or turn an already valid business delete into a fake success. */
    public void enqueueUnknown(String requestId,String groupId,String objectPk) throws BusinessException {
        EsignValues.require(requestId!=null && requestId.trim().length()==32 && groupId!=null && objectPk!=null,
            "图片待核对任务登记信息不完整。");
        Map<String,Object> existing=one("SELECT cleanup_id,request_id FROM qd_es_img_cleanup WHERE request_id=? AND object_pk=?",
            requestId,objectPk);
        if(existing!=null)return;
        String time=EsignValues.utc();
        int count=update("INSERT INTO qd_es_img_cleanup (cleanup_id,request_id,pk_group,object_type,object_pk,"
            + "folder_path,status,created_at,finished_at,last_error) VALUES (?,?,?,?,?,?,?,?,?,?)",EsignValues.id(),
            requestId,groupId,"BD_MATERIAL",objectPk,MaterialImageFolder.folderPath(objectPk),"NEEDS_REVIEW",time,time,
            "无法核实图片目录或文件存储，未删除图片，请按物料主键核对。");
        EsignValues.require(count==1,"图片待核对任务登记失败。");
    }

    /** Record exact NC file nodes with the business transaction; never delete by this directory alone. */
    private void captureItems(String cleanupId,MaterialImageFolder folder) throws BusinessException {
        List<Map<String,Object>> items=readFileNodes(folder.getFullPath(),true);
        EsignValues.require(items!=null && !items.isEmpty(),"图片目录清单为空，无法确认文件身份。");
        Set<String> keys=new HashSet<String>();boolean foundRoot=false;
        for(Map<String,Object> item:items) {
            String pk=EsignValues.text(item,"pk"),path=EsignValues.text(item,"filepath");
            EsignValues.require(pk!=null && keys.add(pk) && path!=null
                && (path.equals(folder.getFullPath()) || path.startsWith(folder.getFullPath()+"/")),
                "图片目录包含无效、重复或越界文件身份。");
            if(path.equals(folder.getFullPath())) {
                EsignValues.require(!foundRoot && pk.equals(folder.getNodePk())
                    && number(item,"modifytime")==folder.getModifiedAt(),"物料图片目录身份或版本已变化。");
                foundRoot=true;
            }
            int saved=update("INSERT INTO qd_es_img_cleanup_item (cleanup_id,file_pk,file_path,is_folder,is_doc,storage_pk,"
                + "modify_time,last_time,file_length) VALUES (?,?,?,?,?,?,?,?,?)",cleanupId,pk,path,
                EsignValues.text(item,"isfolder"),EsignValues.text(item,"isdoc"),EsignValues.text(item,"pk_doc"),
                EsignValues.text(item,"modifytime"),EsignValues.text(item,"lasttime"),EsignValues.text(item,"filelength"));
            EsignValues.require(saved==1,"物料图片文件清单登记失败。");
        }
        EsignValues.require(foundRoot,"物料图片目录主节点不存在。");
    }

    public enum ManifestStatus { MATCHED, CHANGED, MISSING }

    /** Compare all registered file nodes with the current metadata for review. */
    public ManifestStatus compareManifest(String cleanupId,MaterialImageFolder folder) throws BusinessException {
        SQLParameter parameters=new SQLParameter();parameters.addParam(cleanupId);
        List<Map<String,Object>> recorded;
        try {
            recorded=db.query("SELECT file_pk AS pk,file_path AS filepath,is_folder AS isfolder,is_doc AS isdoc,"
                + "storage_pk AS pk_doc,modify_time AS modifytime,last_time AS lasttime,file_length AS filelength"
                + " FROM qd_es_img_cleanup_item WHERE cleanup_id=?",parameters);
        } catch(DAOException failure) { throw new BusinessException("登记的物料图片清单读取失败。",failure); }
        if(recorded.isEmpty()) return ManifestStatus.MISSING;
        Map<String,Map<String,Object>> expected=new HashMap<String,Map<String,Object>>();
        for(Map<String,Object> row:recorded) {
            String pk=EsignValues.text(row,"pk");
            if(pk==null || expected.put(pk,row)!=null) return ManifestStatus.CHANGED;
        }
        Map<String,Object> root=expected.get(folder.getNodePk());
        if(root==null || !folder.getFullPath().equals(EsignValues.text(root,"filepath"))) return ManifestStatus.CHANGED;
        for(Map<String,Object> current:readFileNodes(folder.getFullPath(),false)) {
            Map<String,Object> saved=expected.remove(EsignValues.text(current,"pk"));
            if(saved==null) return ManifestStatus.CHANGED;
            for(String field:FILE_FIELDS) if(!Objects.equals(EsignValues.text(saved,field),EsignValues.text(current,field)))
                return ManifestStatus.CHANGED;
        }
        return expected.isEmpty()?ManifestStatus.MATCHED:ManifestStatus.CHANGED;
    }

    private List<Map<String,Object>> readFileNodes(String path,boolean lock) throws BusinessException {
        SQLParameter parameters=new SQLParameter();parameters.addParam(path);
        parameters.addParam(escapeLike(path)+"/%");
        try {
            return db.query("SELECT pk,filepath,isfolder,isdoc,pk_doc,modifytime,lasttime,filelength FROM sm_pub_filesystem"
                + " WHERE filepath=? OR filepath LIKE ? ESCAPE '\\' ORDER BY filepath"+(lock?" FOR UPDATE NOWAIT":""),parameters);
        } catch(DAOException failure) { throw new BusinessException("物料图片文件清单读取失败。",failure); }
    }

    static String escapeLike(String value) {
        return value.replace("\\","\\\\").replace("_","\\_").replace("%","\\%");
    }
    private static long number(Map<String,Object> row,String key) throws BusinessException {
        String text=EsignValues.text(row,key);if(text==null || text.trim().isEmpty())return 0L;
        try { return Long.parseLong(text.trim()); }
        catch(NumberFormatException failure) { throw new BusinessException("物料图片目录版本格式无效。",failure); }
    }

    /** 读取一个集团到达重试时间的待清理任务，固定上限防止单批占用过多连接。 */
    public List<Map<String,Object>> loadPendingBatch(String groupId,int limit) throws BusinessException {
        SQLParameter parameters = new SQLParameter();
        parameters.addParam(groupId);
        parameters.addParam(EsignValues.utc());
        try {
            List<Map<String,Object>> rows = db.query(
                "SELECT cleanup_id,request_id,object_pk,folder_path,folder_pk,folder_version,attempt_count FROM qd_es_img_cleanup"
                    + " WHERE pk_group=? AND status='PENDING' AND (next_retry_at IS NULL OR next_retry_at<=?)"
                    + " ORDER BY created_at", parameters);
            List<Map<String,Object>> result = new ArrayList<Map<String,Object>>();
            for (Map<String,Object> row : rows) {
                if (result.size() >= limit) { break; }
                Map<String,Object> copy = new LinkedHashMap<String,Object>(row);
                result.add(copy);
            }
            return result;
        } catch (DAOException failure) {
            throw new BusinessException("物料图片清理任务读取失败：" + failure.getMessage(), failure);
        }
    }

    /** 物料主键是否又出现在 bd_material 中；清理前必须确认业务行仍不存在。 */
    public boolean materialStillExists(String objectPk) throws BusinessException {
        try {
            SQLParameter parameters = new SQLParameter();
            parameters.addParam(objectPk);
            List<Map<String,Object>> rows = db.query("SELECT pk_material FROM bd_material WHERE pk_material=?", parameters);
            return !rows.isEmpty();
        } catch (DAOException failure) {
            throw new BusinessException("物料清理前核对失败：" + failure.getMessage(), failure);
        }
    }

    /** Atomic compare-and-set claim; a competing worker must skip the same row. */
    public boolean claim(String groupId,String cleanupId,String token,String leaseUntil) throws BusinessException {
        EsignValues.require(groupId!=null && cleanupId!=null && token!=null && leaseUntil!=null,"清理任务认领信息不完整。");
        return update("UPDATE qd_es_img_cleanup SET status='RUNNING',lease_token=?,lease_until=?"
            + " WHERE pk_group=? AND cleanup_id=? AND status='PENDING' AND (next_retry_at IS NULL OR next_retry_at<=?)",
            token,leaseUntil,groupId,cleanupId,EsignValues.utc())==1;
    }

    /** An expired worker may still be deleting files. Never silently retry an unknown outcome. */
    public void holdExpired(String groupId) throws BusinessException {
        update("UPDATE qd_es_img_cleanup SET status='NEEDS_REVIEW',finished_at=?,last_error=?,lease_token=NULL,lease_until=NULL"
            + " WHERE pk_group=? AND status='RUNNING' AND lease_until<?",
            EsignValues.utc(),"清理任务租约过期，执行结果未知，请核对后处理。",groupId,EsignValues.utc());
    }

    /** 任务终态更新；租约已失效时返回 false，不能覆盖其他执行者的结果。 */
    public boolean finish(String cleanupId,String token,String status,String message) throws BusinessException {
        EsignValues.require("CLEANED".equals(status) || "NEEDS_REVIEW".equals(status), "图片清理终态不正确。");
        int count = update("UPDATE qd_es_img_cleanup SET status=?,finished_at=?,last_error=?,lease_token=NULL,lease_until=NULL"
            + " WHERE cleanup_id=? AND status='RUNNING' AND lease_token=?",
            status, EsignValues.utc(), message, cleanupId,token);
        return count == 1;
    }

    /** 记录一次失败尝试；租约已失效时返回 false，不能重新排队其他执行者的任务。 */
    public boolean retry(String cleanupId,String token,int attempts,String message,String nextRetryUtc) throws BusinessException {
        int count = update("UPDATE qd_es_img_cleanup SET status='PENDING',attempt_count=?,next_retry_at=?,last_error=?,"
            + "lease_token=NULL,lease_until=NULL WHERE cleanup_id=? AND status='RUNNING' AND lease_token=?",
            Integer.valueOf(attempts), nextRetryUtc, message, cleanupId,token);
        return count == 1;
    }

    private int update(String sql,Object... values) throws BusinessException {
        SQLParameter parameters = new SQLParameter();
        for (Object value : values) { if(value==null) parameters.addNullParam(java.sql.Types.VARCHAR); else parameters.addParam(value); }
        try { return db.update(sql, parameters); }
        catch (DAOException failure) { throw new BusinessException("图片清理任务更新失败：" + failure.getMessage(), failure); }
    }

    private Map<String,Object> one(String sql,Object... values) throws BusinessException {
        SQLParameter parameters = new SQLParameter();
        for (Object value : values) { if(value==null) parameters.addNullParam(java.sql.Types.VARCHAR); else parameters.addParam(value); }
        try {
            List<Map<String,Object>> rows = db.query(sql, parameters);
            return rows.isEmpty() ? null : rows.get(0);
        } catch (DAOException failure) { throw new BusinessException("图片清理任务查询失败：" + failure.getMessage(), failure); }
    }
}
