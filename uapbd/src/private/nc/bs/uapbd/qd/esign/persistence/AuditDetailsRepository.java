package nc.bs.uapbd.qd.esign.persistence;

import java.io.IOException;
import java.io.Reader;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import nc.bs.dao.BaseDAO;
import nc.bs.dao.DAOException;
import nc.jdbc.framework.SQLParameter;
import nc.jdbc.framework.processor.BaseProcessor;
import nc.vo.uapbd.qd.esign.audit.SnapshotContent;
import nc.vo.uapbd.qd.esign.audit.SnapshotPayload;
import nc.vo.uapbd.qd.esign.log.AuditDetails;

/** 授权范围内的审计详情仓储，先检查主记录权限，再按服务端请求定位目录查找跨月明细；大字段读取带长度与内存上限。 */
public final class AuditDetailsRepository {
    /** dao：NC 原生数据访问入口，使用调用方事务。 */
    private final BaseDAO dao=new BaseDAO();
    /** store：与当前 DAO 协作的数据存取工具。 */
    private final QdEsignStore store=new QdEsignStore(dao);
    /** 先校验审计主记录的访问范围，再按实际请求关联加载分页差异与快照；未知、越权及不合法分页请求明确失败。 */
    public AuditDetails details(String auditId,String month,int offset,int pageSize,String group,List<String> orgs) throws DAOException {
        if(offset<0||pageSize<1||pageSize>1000||offset>Integer.MAX_VALUE-pageSize) {
            throw new IllegalArgumentException("Invalid audit detail page");
        }
        Map<String,Object> audit=requireAudit(auditId,month,group,orgs);
        audit=new QdLogRepository(dao).withOrganizationNames(Collections.singletonList(audit),group).get(0);
        String requestId=String.valueOf(audit.get("request_id"));
        List<String> months=relatedMonths(requestId,month);
        List<String> changes=tables("qd_es_change",months),snapshots=tables("qd_es_snapshot",months);
        return new AuditDetails(audit,page("qd_es_change",changes,auditId,requestId,group,orgs,offset,pageSize),
            page("qd_es_snapshot",snapshots,auditId,requestId,group,orgs,offset,pageSize),
            count("qd_es_change",changes,auditId,requestId,group,orgs),count("qd_es_snapshot",snapshots,auditId,requestId,group,orgs));
    }

    /** 按已授权的快照读取所有字段预览，或读取一个字段的指定长度片段。 */
    public SnapshotContent snapshotContent(String auditId,String month,String snapshotId,final String field,
            final long offset,String group,List<String> orgs) throws DAOException {
        if(snapshotId==null||!snapshotId.matches("[a-fA-F0-9]{32}")||offset<0
                ||(field==null && offset!=0)||(field!=null && (field.length()==0 || field.length()>200))) {
            throw new IllegalArgumentException("Invalid audit snapshot request");
        }
        String requestId=String.valueOf(requireAudit(auditId,month,group,orgs).get("request_id"));
        for(String table:tables("qd_es_snapshot",relatedMonths(requestId,month))) {
            SQLParameter p=params(auditId,requestId,group,snapshotId);
            String sql="SELECT payload FROM "+table+" WHERE audit_id=? AND request_id=? AND pk_group=? AND snapshot_id=? AND "
                +orgCondition(orgs,p);
            SnapshotContent content=(SnapshotContent)dao.executeQuery(sql,p,new BaseProcessor() {
                private static final long serialVersionUID=1L;
                public Object processResultSet(ResultSet rs) throws SQLException {
                    if(!rs.next()){return null;}
                    try(Reader input=rs.getCharacterStream(1)) {
                        if(input==null){throw new SQLException("Audit snapshot has no payload");}
                        return SnapshotPayload.read(input,field,offset,
                            field==null?SnapshotContent.FIELD_PREVIEW:SnapshotContent.VALUE_PAGE_SIZE);
                    } catch(IOException e) {
                        throw new SQLException("Cannot read complete audit snapshot fields",e);
                    }
                }
            });
            if(content!=null){return content;}
        }
        throw new DAOException("Audit snapshot not found in the authorized scope");
    }

    private Map<String,Object> requireAudit(String auditId,String month,String group,List<String> orgs) throws DAOException {
        if(auditId==null||!auditId.matches("[a-fA-F0-9]{32}")||group==null||orgs==null||orgs.isEmpty()) {
            throw new IllegalArgumentException("Invalid audit identity or authorized scope");
        }
        QdTableMetadata.historyTable("qd_es_audit",month);
        for(String table:auditTables(month)) {
            SQLParameter p=new SQLParameter();p.addParam(auditId);p.addParam(month);p.addParam(group);
            String where="audit_id=? AND archive_month=? AND pk_group=? AND "+orgCondition(orgs,p);
            Map<String,Object> row=store.one("SELECT "+QdTableMetadata.columnList("qd_es_audit")+" FROM "+table+" WHERE "+where,p);
            if(row!=null) { return row; }
        }
        throw new DAOException("Audit record not found in the authorized scope");
    }

    private List<String> relatedMonths(String requestId,String month) throws DAOException {
        // 主记录授权通过后才使用实际请求号查月份定位，不信任客户端自报关联。
        List<String> months=new ArrayList<String>();
        for(Map<String,Object> row:store.list("SELECT archive_month FROM qd_es_locator WHERE request_id=? ORDER BY archive_month",params(requestId))) {
            months.add(String.valueOf(row.get("archive_month")));
        }
        if(!months.contains(month)){months.add(month);}
        return months;
    }
    /** 取得审计主记录可能所在的在线及目标月份历史表集合。 */
    private List<String> auditTables(String month) throws DAOException {
        List<String> months=new ArrayList<String>();months.add(month);return tables("qd_es_audit",months);
    }
    /** 按服务端定位月份和归档目录选择固定表，目录存在但历史表缺失时明确失败。 */
    private List<String> tables(String base,List<String> months) throws DAOException {
        Set<String> result=new LinkedHashSet<String>();result.add(base);
        for(String month:months) {
            Map<String,Object> directory=store.one("SELECT archive_month FROM qd_es_archive WHERE archive_month=?",params(month));
            if(directory==null){continue;}
            String history=QdTableMetadata.historyTable(base,month);
            if(!dao.isTableExisted(history)){throw new DAOException("Missing approved history table: "+history);}
            result.add(history);
        }
        return new ArrayList<String>(result);
    }
    /** 根据上一页或下一页方向计算偏移，沿用已加载页的筛选条件。 */
    private List<Map<String,Object>> page(String base,List<String> tables,String audit,String request,String group,List<String> orgs,int offset,int size) throws DAOException {
        String key=QdTableMetadata.primaryKey(base);SQLParameter p=new SQLParameter();
        String idUnion=idUnion(base,tables,audit,request,group,orgs,p);
        String idSql="SELECT "+key+" FROM (SELECT qd_ordered.*,ROWNUM qd_page FROM (SELECT DISTINCT "+key+" FROM ("
            +idUnion+") ORDER BY "+key+") qd_ordered WHERE ROWNUM<=?) WHERE qd_page>?";
        p.addParam(offset+size);p.addParam(offset);
        List<Map<String,Object>> selected=store.list(idSql,p);if(selected.isEmpty()){return new ArrayList<Map<String,Object>>();}
        List<String> ids=new ArrayList<String>();for(Map<String,Object> row:selected){ids.add(String.valueOf(row.get(key)));}
        Map<String,Map<String,Object>> found=new LinkedHashMap<String,Map<String,Object>>();
        // 同页复用预览读取器，跨在线和历史表累计检查字符总量。
        int clobsPerRow="qd_es_change".equals(base)?2:1;
        PreviewProcessor processor=new PreviewProcessor(Math.min(100000,16*1024*1024/(size*clobsPerRow)));
        for(String table:tables) {
            SQLParameter query=params(audit,request,group);String scope=orgCondition(orgs,query);
            StringBuilder holders=new StringBuilder();for(String id:ids){if(holders.length()>0){holders.append(',');}holders.append('?');query.addParam(id);}
            String sql="SELECT "+detailProjection(base)+" FROM "+table+" WHERE audit_id=? AND request_id=? AND pk_group=? AND "
                +scope+" AND "+key+" IN ("+holders+")";
            @SuppressWarnings("unchecked") List<Map<String,Object>> rows=(List<Map<String,Object>>)dao.executeQuery(sql,query,processor);
            for(Map<String,Object> row:rows){String id=String.valueOf(row.get(key));if(!found.containsKey(id)){found.put(id,row);}}
        }
        if(found.size()!=ids.size()){throw new DAOException("Audit detail changed during retrieval; refresh the record");}
        List<Map<String,Object>> result=new ArrayList<Map<String,Object>>(found.values());
        final String pk=key;Collections.sort(result,new Comparator<Map<String,Object>>(){
            /** 按固定主键文本排序，恢复与已选分页主键一致的展示顺序。 */
            public int compare(Map<String,Object> a,Map<String,Object> b){return String.valueOf(a.get(pk)).compareTo(String.valueOf(b.get(pk)));}
        });return result;
    }
    /** 计算符合权限和筛选条件的去重记录总数，不将在线与历史重叠副本重复计数。 */
    private long count(String base,List<String> tables,String audit,String request,String group,List<String> orgs) throws DAOException {
        SQLParameter p=new SQLParameter();String union=idUnion(base,tables,audit,request,group,orgs,p);
        Map<String,Object> result=store.one("SELECT COUNT(DISTINCT "+QdTableMetadata.primaryKey(base)+") row_count FROM ("+union+")",p);
        return ((Number)result.get("row_count")).longValue();
    }
    /** 只联合普通主键并逐分支添加审计、请求、集团和组织条件，避免 CLOB 集合操作。 */
    private String idUnion(String base,List<String> tables,String audit,String request,String group,List<String> orgs,SQLParameter p) {
        StringBuilder sql=new StringBuilder();String key=QdTableMetadata.primaryKey(base);
        for(String table:tables){
            if(sql.length()>0){sql.append(" UNION ALL ");}
            sql.append("SELECT ").append(key).append(" FROM ").append(table).append(" WHERE audit_id=? AND request_id=? AND pk_group=? AND ");
            p.addParam(audit);p.addParam(request);p.addParam(group);sql.append(orgCondition(orgs,p));
        }return sql.toString();
    }
    /** 对服务端组织范围去重并按每组 900 项分段构造参数化条件，拒绝空组织标识。 */
    private static String orgCondition(List<String> orgs,SQLParameter p) {
        StringBuilder sql=new StringBuilder("(");int n=0;
        for(String org:new LinkedHashSet<String>(orgs)){
            if(org==null||org.length()==0){throw new IllegalArgumentException("Invalid server organization scope");}
            if(n==0){sql.append("pk_org IN (");}else if(n%900==0){sql.append(") OR pk_org IN (");}else{sql.append(',');}
            sql.append('?');p.addParam(org);n++;
        }return sql.append("))").toString();
    }
    /** 按原顺序构造 SQL 参数集合，供固定 SQL 查询使用。 */
    private static SQLParameter params(Object... values){SQLParameter p=new SQLParameter();for(Object value:values){p.addParam(value);}return p;}
    /** 额外获取 CLOB 的完整字符数，供受限字符流预览标记截断。 */
    static String detailProjection(String base) {
        String columns = QdTableMetadata.columnList(base);
        if ("qd_es_change".equals(base)) {
            return columns + ",DBMS_LOB.GETLENGTH(before_value) before_value_qd_length"
                + ",DBMS_LOB.GETLENGTH(after_value) after_value_qd_length";
        }
        if ("qd_es_snapshot".equals(base)) {
            return columns + ",DBMS_LOB.GETLENGTH(payload) payload_qd_length";
        }
        throw new IllegalArgumentException("Unsupported audit detail table");
    }
    /** 受限的审计结果集读取器，逐列处理普通值与 CLOB；单字段预览最多十万字符，累计超过 16Mi 字符时明确失败。 */
    private static final class PreviewProcessor extends BaseProcessor {
        /** serialVersionUID：Java 序列化版本号，保持跨 RPC 或 Swing 组件序列化的兼容标识。 */
        private static final long serialVersionUID=1L;
        private final int perFieldLimit;
        /** totalChars：本次明细读取已经累计的预览字符数。 */
        private int totalChars;
        PreviewProcessor(){this(100000);}
        PreviewProcessor(int perFieldLimit){this.perFieldLimit=perFieldLimit;}
        /** 读取结果集并生成带大字段长度和截断标记的预览，关闭字符流并执行累计内存上限检查。 */
        public Object processResultSet(ResultSet rs) throws SQLException {
            List<Map<String,Object>> rows=new ArrayList<Map<String,Object>>();ResultSetMetaData metadata=rs.getMetaData();
            while(rs.next()) {
                Map<String,Object> row=new LinkedHashMap<String,Object>();
                for(int i=1;i<=metadata.getColumnCount();i++){
                    String field=metadata.getColumnLabel(i).toLowerCase(Locale.ENGLISH);
                    if(field.endsWith("_qd_length") && QdTableMetadata.isClob(field.substring(0,field.length()-10))){continue;}
                    int type=metadata.getColumnType(i);
                    if(type==Types.CLOB||type==Types.NCLOB){
                        long length=rs.getLong(field+"_qd_length");
                        if(rs.wasNull()){row.put(field,null);row.put(field+"_chars",Long.valueOf(0));row.put(field+"_truncated",Boolean.FALSE);continue;}
                        int cap=(int)Math.min(length,perFieldLimit);StringBuilder value=new StringBuilder(cap);
                        Reader reader=rs.getCharacterStream(i);
                        if(reader==null){throw new SQLException("Audit CLOB length is present but its character stream is unavailable");}
                        try{char[] buffer=new char[4096];while(value.length()<cap){int n=reader.read(buffer,0,Math.min(buffer.length,cap-value.length()));if(n<0){break;}value.append(buffer,0,n);}}
                        catch(IOException failure){SQLException e=new SQLException("Cannot read audit CLOB preview");e.initCause(failure);throw e;}
                        finally{try{reader.close();}catch(IOException failure){SQLException e=new SQLException("Cannot close audit CLOB reader");e.initCause(failure);throw e;}}
                        totalChars+=value.length();if(totalChars>16*1024*1024){throw new SQLException("Audit preview exceeds 16 Mi characters; reduce pageSize");}
                        row.put(field,value.toString());row.put(field+"_chars",Long.valueOf(length));row.put(field+"_truncated",Boolean.valueOf(length>value.length()));
                    }else{row.put(field,rs.getObject(i));}
                }rows.add(row);
            }return rows;
        }
    }
}
