package nc.bs.uapbd.qd.esign.persistence;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import nc.bs.dao.BaseDAO;
import nc.bs.framework.common.NCLocator;
import nc.bs.pub.operatelog.itf.IOperatelogQueryService;
import nc.jdbc.framework.SQLParameter;
import nc.vo.pub.BusinessException;
import nc.vo.pub.operatelog.OperateLogVO;

/** 仅导入原生登录、注销的真实事实，不读取或序列化认证详情、密码及令牌；增量游标与事件在同一事务更新，并提供全主键重扫补获迟提交记录。 */
public final class NativeSecurityLogImporter {
    /** SOURCE：原生日志增量同步使用的独立来源标识。 */
    private static final String SOURCE="NC_OPERATELOG";
    /** RECONCILE_SOURCE：全主键对账使用的独立游标来源标识。 */
    private static final String RECONCILE_SOURCE="NC_OPERATELOG_RECONCILE";
    /** dao：NC 原生数据访问入口，使用调用方事务。 */
    private final BaseDAO dao=new BaseDAO();
    /** store：与当前 DAO 协作的数据存取工具。 */
    private final QdEsignStore store=new QdEsignStore(dao);

    /** 按来源时间与主键复合水位选取增量记录，在同一 CMT 内导入事实并推进游标。 */
    public int importBatch(String group,String sourceTimeZone,String sourceCutoff,int batchSize) throws BusinessException {
        TimeZone zone=zone(sourceTimeZone);parseLocal(sourceCutoff,zone);
        if(group==null||batchSize<1||batchSize>900) { throw new BusinessException("Invalid native sync scope/batch"); }
        SQLParameter scope=new SQLParameter();scope.addParam(group);scope.addParam(SOURCE);
        Map<String,Object> cursor=store.one("SELECT last_source_ts,last_source_pk FROM qd_es_sync_cursor WHERE pk_group=? AND source_name=? FOR UPDATE",scope);
        if(cursor==null) {
            Map<String,Object> row=new LinkedHashMap<String,Object>();row.put("pk_group",group);row.put("source_name",SOURCE);
            row.put("last_source_ts","1970-01-01 00:00:00");row.put("last_source_pk","!");row.put("modified_at",QdUtcClock.now());
            store.insert("qd_es_sync_cursor",row);cursor=row;
        }
        // 缺少 ts 的日志无法可靠推进增量水位，明确失败并保留游标，不静默漏过。
        Map<String,Object> unversioned=store.one("SELECT COUNT(*) row_count FROM sm_log_operatelog WHERE pk_group=? AND type=0 AND ts IS NULL",params(group));
        if(((Number)unversioned.get("row_count")).longValue()>0) {
            throw new BusinessException("Native login logs without ts need reconciliation before reliable incremental sync");
        }
        String lastTs=String.valueOf(cursor.get("last_source_ts")),lastPk=String.valueOf(cursor.get("last_source_pk"));
        SQLParameter p=params(group,lastTs,lastTs,lastPk,sourceCutoff,Integer.valueOf(batchSize));
        List<Map<String,Object>> selected=store.list("SELECT pk_operatelog,ts FROM (SELECT pk_operatelog,ts FROM sm_log_operatelog"
            + " WHERE pk_group=? AND type=0 AND (ts>? OR (ts=? AND pk_operatelog>?)) AND ts<=? ORDER BY ts,pk_operatelog) WHERE ROWNUM<=?",p);
        if(selected.isEmpty()) { return 0; }
        // 先读取并导入这一批真实事实，全部成功之后才能推进复合游标。
        String[] keys=keys(selected);importFacts(group,zone,keys);
        Map<String,Object> last=selected.get(selected.size()-1);
        int changed=dao.executeUpdate("UPDATE qd_es_sync_cursor SET last_source_ts=?,last_source_pk=?,modified_at=? WHERE pk_group=? AND source_name=?",
            params(last.get("ts"),last.get("pk_operatelog"),QdUtcClock.now(),group,SOURCE));
        QdEsignStore.requireOne(changed,"advance native security cursor"); return selected.size();
    }

    /** 按完整主键顺序重扫一批原生日志，使用独立游标和同一幂等目录；周期到尾后回到起点。 */
    public Map<String,Object> reconcileBatch(String group,String sourceTimeZone,int batchSize) throws BusinessException {
        TimeZone zone=zone(sourceTimeZone);NativeReconciliationRules.completesCycle(0,batchSize);
        if(group==null) { throw new BusinessException("Native reconciliation requires a group"); }
        Map<String,Object> cursor=store.one("SELECT last_source_pk,cycle_started_at,cycle_completed_at FROM qd_es_sync_cursor WHERE pk_group=? AND source_name=? FOR UPDATE",
            params(group,RECONCILE_SOURCE));
        String now=QdUtcClock.now();
        if(cursor==null) {
            cursor=new LinkedHashMap<String,Object>();cursor.put("pk_group",group);cursor.put("source_name",RECONCILE_SOURCE);
            cursor.put("last_source_ts","1970-01-01 00:00:00");cursor.put("last_source_pk",NativeReconciliationRules.START_KEY);
            cursor.put("cycle_started_at",now);cursor.put("modified_at",now);store.insert("qd_es_sync_cursor",cursor);
        }
        String started=cursor.get("cycle_started_at")==null?now:String.valueOf(cursor.get("cycle_started_at"));
        List<Map<String,Object>> selected=store.list("SELECT pk_operatelog FROM (SELECT pk_operatelog FROM sm_log_operatelog"
            + " WHERE pk_group=? AND type=0 AND pk_operatelog>? ORDER BY pk_operatelog) WHERE ROWNUM<=?",
            params(group,cursor.get("last_source_pk"),Integer.valueOf(batchSize)));
        if(!selected.isEmpty()) { importFacts(group,zone,keys(selected)); }
        // 满批不能证明到尾；到尾后重置起点，下一轮才可能补获本轮越过主键的迟提交。
        boolean complete=NativeReconciliationRules.completesCycle(selected.size(),batchSize);
        String last=selected.isEmpty()?null:String.valueOf(selected.get(selected.size()-1).get("pk_operatelog"));
        String next=NativeReconciliationRules.nextPrimaryKey(last,selected.size(),batchSize);
        String finished=QdUtcClock.now();Object completed=complete?finished:cursor.get("cycle_completed_at");
        int changed=dao.executeUpdate("UPDATE qd_es_sync_cursor SET last_source_pk=?,cycle_started_at=?,cycle_completed_at=?,modified_at=? WHERE pk_group=? AND source_name=?",
            params(next,complete?null:started,completed,finished,group,RECONCILE_SOURCE));
        QdEsignStore.requireOne(changed,"advance native reconciliation cycle");
        Map<String,Object> result=new LinkedHashMap<String,Object>();result.put("rows_examined",Integer.valueOf(selected.size()));
        result.put("cycle_complete",Boolean.valueOf(complete));result.put("cycle_completed_at",completed);result.put("last_source_pk",next);
        return result;
    }
    /** 按查询返回顺序提取原生主键，用于加载真实 OperateLogVO。 */
    private static String[] keys(List<Map<String,Object>> rows) {
        String[] keys=new String[rows.size()];for(int i=0;i<keys.length;i++){keys[i]=String.valueOf(rows.get(i).get("pk_operatelog"));}return keys;
    }
    /** 通过原生查询接口读取 VO，仅导入明确记录的登录结果与真实注销时间，不推测不存在的安全事件。 */
    private void importFacts(String group,TimeZone zone,String[] keys) throws BusinessException {
        OperateLogVO[] nativeRows=NCLocator.getInstance().lookup(IOperatelogQueryService.class).queryByPKs(keys);
        Map<String,OperateLogVO> byId=new HashMap<String,OperateLogVO>();
        if(nativeRows!=null) { for(OperateLogVO vo:nativeRows) { byId.put(vo.getPk_operatelog(),vo); } }
        for(String key:keys) {
            OperateLogVO vo=byId.get(key);
            // 选中的源记录丢失或改变集团时停止整批，不能跳过后继续推进水位。
            if(vo==null||!group.equals(vo.getPk_group())||vo.getType()==null||vo.getType().intValue()!=0) {
                throw new BusinessException("Native source log disappeared or changed scope; cursor not advanced");
            }
            if(vo.getLogdate()==null||vo.getLogtime()==null||vo.getEntersystemresult()==null) {
                throw new BusinessException("Incomplete native login fact: " + key);
            }
            String localLogin=vo.getLogdate().toString()+" "+vo.getLogtime().toString();
            int result=vo.getEntersystemresult().intValue();
            // 仅 0/1 已核实为原生成功/失败枚举，其他结果码保留原值，不擅自归类。
            String outcome=result==0?"SUCCESS":result==1?"FAILURE":"NATIVE_"+result;
            insert(vo,"LOGIN",outcome,localLogin,zone);
            if(vo.getLogoffdate()!=null&&vo.getLogofftime()!=null) {
                insert(vo,"LOGOUT","NATIVE_RECORDED",vo.getLogoffdate().toString()+" "+vo.getLogofftime().toString(),zone);
            } else if(vo.getLogoffdate()!=null||vo.getLogofftime()!=null) {
                throw new BusinessException("Incomplete native logout timestamp: " + key);
            }
        }
    }
    /** 以原生事件主键和版本构造幂等安全事实；只读取确定存在的身份与时间字段，不补造用户编码或终端名称。 */
    private void insert(OperateLogVO vo,String event,String outcome,String local,TimeZone zone) throws BusinessException {
        Date date=parseLocal(local,zone);String version=event+":"+local+":"+outcome;
        String sourceKey=vo.getPk_operatelog();String id;
        // 事件 ID 由固定来源、源主键及事实版本确定，增量与全扫复用幂等目录。
        try { id=UUID.nameUUIDFromBytes((SOURCE+"|"+sourceKey+"|"+version).getBytes("UTF-8")).toString().replace("-",""); }
        catch(java.io.UnsupportedEncodingException impossible) { throw new IllegalStateException(impossible); }
        Map<String,Object> row=new LinkedHashMap<String,Object>();
        row.put("event_id",id);row.put("source_system",SOURCE);row.put("source_event_id",sourceKey);row.put("source_version",version);
        row.put("user_id",vo.getPk_user());row.put("user_name",vo.getUser_name());row.put("pk_group",vo.getPk_group());
        row.put("event_type",event);row.put("result_code",outcome);row.put("client_ip",vo.getIp());
        row.put("event_time",QdUtcClock.utc(date));row.put("archive_month",QdUtcClock.beijingMonth(date));
        // 原生 VO 未提供用户编码及终端主机名，保持未知，不自行补造。
        store.insert("qd_es_security",row);
    }
    /** 校验显式来源时区，防止 TimeZone 对未知 ID 静默回退 GMT。 */
    public static TimeZone zone(String id) {
        if(id!=null) { for(String candidate:TimeZone.getAvailableIDs()) { if(candidate.equals(id)) { return TimeZone.getTimeZone(id); } } }
        throw new IllegalArgumentException("Explicit valid native log source time zone is required");
    }
    /** 按已经验证的来源时区解析原生日志时间，再交由 UTC 工具保存。 */
    private static Date parseLocal(String value,TimeZone zone) {
        if(value==null||!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}")) { throw new IllegalArgumentException("Invalid native log timestamp"); }
        SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.ENGLISH);f.setTimeZone(zone);f.setLenient(false);
        try{return f.parse(value);}catch(ParseException failure){throw new IllegalArgumentException("Invalid native log timestamp",failure);}
    }
    /** 按原顺序构造 SQL 参数集合，供固定 SQL 查询使用。 */
    private static SQLParameter params(Object... values) { return QdEsignStore.parameters(values); }
}
