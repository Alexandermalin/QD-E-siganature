package nc.bs.uapbd.qd.esign.core;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import nc.bs.dao.BaseDAO;
import nc.bs.uapbd.qd.esign.audit.SnapshotSupport;
import nc.jdbc.framework.SQLParameter;
import nc.jdbc.framework.processor.BaseProcessor;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.PartnerBatchDraft;
import nc.vo.uapbd.qd.esign.PartnerOrgBatchPlan;

/** Server-selected page identities for the native root-by-organization quick-batch query. */
public final class NativeOrgBatchSelection {
    private static final int IN_LIMIT=400;
    private static final Pattern PK=Pattern.compile("[A-Za-z0-9_-]{1,20}");
    private NativeOrgBatchSelection() { }

    public interface Reader {
        List<Row> read(NativeOrgBatchPage page,String[] roots,String[] targets,boolean lock) throws BusinessException;
    }
    /** Reads exact page identities for a planned slice, without expanding it to the root/target cross product. */
    public interface PageReader extends Reader {
        List<Row> readPages(NativeOrgBatchPage page,String[] pageIds,boolean lock) throws BusinessException;
    }
    interface Query { List<Row> read(String sql,String[] parameters) throws BusinessException; }
    public static final class Row {
        private final String id,root,target,group;
        public Row(String id,String root,String target,String group) { this.id=id;this.root=root;this.target=target;this.group=group; }
        public String getId() { return id; }
        public String getRoot() { return root; }
        public String getTarget() { return target; }
        public String getGroup() { return group; }
    }
    public static final class Impact {
        private final String group,domain,fingerprint;private final NativeOrgBatchPage page;
        private final List<String> roots,targets;private final List<Row> rows;
        private Impact(String group,String domain,NativeOrgBatchPage page,List<String> roots,List<String> targets,List<Row> rows,String fingerprint) {
            this.group=group;this.domain=domain;this.page=page;this.fingerprint=fingerprint;
            this.roots=Collections.unmodifiableList(new ArrayList<String>(roots));
            this.targets=Collections.unmodifiableList(new ArrayList<String>(targets));
            this.rows=Collections.unmodifiableList(new ArrayList<Row>(rows));
        }
        public String getGroup() { return group; }
        public String getDomain() { return domain; }
        public NativeOrgBatchPage getPage() { return page; }
        public List<String> getRoots() { return roots; }
        public List<String> getTargets() { return targets; }
        public List<Row> getRows() { return rows; }
        public String getFingerprint() { return fingerprint; }
        boolean matches(String group,String domain,List<BusinessObjectRef> selected) throws BusinessException {
            return this.group.equals(group) && this.domain.equals(domain) && roots.equals(rootIds(domain,selected));
        }
    }

    /** Includes deleted rows because NC's original page selection does not filter the deletion marker. */
    public static Impact capture(String group,String domain,NativeOrgBatchPage page,List<BusinessObjectRef> selected,
            String[] targetIds,Reader reader,boolean lock) throws BusinessException {
        return capture(group,domain,page,selected,targetIds,reader,lock,null);
    }

    /** A non-null page list fixes a planned slice, including an explicitly empty no-op slice. */
    public static Impact capture(String group,String domain,NativeOrgBatchPage page,List<BusinessObjectRef> selected,
            String[] targetIds,Reader reader,boolean lock,PartnerOrgBatchPlan.Page[] selectedPages) throws BusinessException {
        EsignValues.require(validPk(group) && page!=null && page.domain.equals(domain) && reader!=null,
            "组织页签批改缺少有效集团或原生页签。");
        List<String> roots=rootIds(domain,selected);List<String> targets=targetIds(targetIds);
        Set<String> rootSet=new HashSet<String>(roots),targetSet=new HashSet<String>(targets),pageIds=new HashSet<String>();
        java.util.Map<String,PartnerOrgBatchPlan.Page> planned=null;List<Row> queried;
        if(selectedPages==null) queried=reader.read(page,roots.toArray(new String[roots.size()]),targets.toArray(new String[targets.size()]),lock);
        else {
            EsignValues.require(reader instanceof PageReader && selectedPages.length<=PartnerBatchDraft.MAX_SINGLE_TRANSACTION_PAGE_ROWS,
                "组织页签计划缺少有效的行读取器或超过单事务上限。");
            planned=new java.util.TreeMap<String,PartnerOrgBatchPlan.Page>();
            for(PartnerOrgBatchPlan.Page item:selectedPages.clone()) EsignValues.require(item!=null && validPk(item.getId())
                && rootSet.contains(item.getRoot()) && targetSet.contains(item.getTarget()) && planned.put(item.getId(),item)==null,
                "组织页签计划包含无效、重复或不属于所选档案和目标的行。");
            queried=planned.isEmpty()?Collections.<Row>emptyList():((PageReader)reader).readPages(page,
                planned.keySet().toArray(new String[planned.size()]),lock);
        }
        EsignValues.require(queried!=null,"无法核定组织页签批改的实际行。");
        List<Row> rows=new ArrayList<Row>();
        for(Row row:queried) {
            PartnerOrgBatchPlan.Page expected=row==null || planned==null?null:planned.remove(row.id);
            EsignValues.require(row!=null && validPk(row.id) && pageIds.add(row.id) && rootSet.contains(row.root)
                && targetSet.contains(row.target) && group.equals(row.group) && (planned==null
                    || expected!=null && expected.getRoot().equals(row.root) && expected.getTarget().equals(row.target)),
                "组织页签批改包含重复、跨集团或未选择的数据行。");
            rows.add(new Row(row.id,row.root,row.target,row.group));
        }
        EsignValues.require(planned==null || planned.isEmpty(),"组织页签计划中的行已不存在或不属于授权范围。");
        Collections.sort(rows,new Comparator<Row>() { public int compare(Row a,Row b) {
            int value=a.root.compareTo(b.root);if(value!=0) return value;
            value=a.target.compareTo(b.target);return value!=0?value:a.id.compareTo(b.id);
        }});
        StringBuilder evidence=new StringBuilder(pack("org-batch-selection-v1",group,domain,page.tabCode,page.metadataId,
            page.table,page.key,page.rootKey,page.service,Integer.toString(roots.size())));
        for(String root:roots) evidence.append(pack(root));
        evidence.append(pack(Integer.toString(targets.size())));for(String target:targets) evidence.append(pack(target));
        evidence.append(pack(Integer.toString(rows.size())));for(Row row:rows) evidence.append(pack(row.root,row.target,row.id));
        return new Impact(group,domain,page,roots,targets,rows,SnapshotSupport.sha256(evidence.toString()));
    }

    private static List<String> rootIds(String domain,List<BusinessObjectRef> selected) throws BusinessException {
        EsignValues.require(selected!=null && !selected.isEmpty() && selected.size()<=PartnerBatchDraft.MAX_SINGLE_TRANSACTION_OBJECTS,
            "组织页签批改缺少主档选择或超过单事务上限。");
        Set<String> ids=new TreeSet<String>(),clients=new HashSet<String>();
        for(BusinessObjectRef ref:selected) EsignValues.require(ref!=null && domain!=null && domain.equals(ref.getObjectType())
            && validPk(ref.getObjectPk()) && ids.add(ref.getObjectPk()) && ref.getClientObjectId()!=null
            && clients.add(ref.getClientObjectId()) && ref.getParentClientObjectId()==null && ref.getDesiredOwnerOrg()==null,
            "组织页签批改的主档选择不完整或重复。");
        return new ArrayList<String>(ids);
    }
    static List<String> targetIds(String[] input) throws BusinessException {
        EsignValues.require(input!=null && input.length>0,"组织页签批改缺少目标组织。");
        Set<String> targets=new TreeSet<String>();
        for(String target:input.clone()) EsignValues.require(validPk(target) && targets.add(target),"组织页签批改目标组织无效或重复。");
        return new ArrayList<String>(targets);
    }

    /** Parameterized chunks use the exact native predicate; locks are held by the caller's NC transaction. */
    public static Reader database() {
        final BaseDAO dao=new BaseDAO();
        return batched(new Query() { public List<Row> read(String sql,String[] values) throws BusinessException {
            SQLParameter args=new SQLParameter();for(String value:values) args.addParam(value);
            @SuppressWarnings("unchecked") List<Row> rows=(List<Row>)dao.executeQuery(sql,args,new BaseProcessor() {
                private static final long serialVersionUID=1L;
                public Object processResultSet(ResultSet rs) throws SQLException {
                    List<Row> values=new ArrayList<Row>();
                    while(rs.next()) values.add(new Row(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4)));
                    return values;
                }
            });
            return rows;
        }});
    }
    static Reader batched(final Query query) {
        if(query==null) throw new IllegalArgumentException("Native page query is required");
        return new PageReader() { public List<Row> read(NativeOrgBatchPage page,String[] roots,String[] targets,boolean lock)
                throws BusinessException {
            List<Row> result=new ArrayList<Row>();
            for(int rs=0;rs<roots.length;rs+=IN_LIMIT) {
                int re=Math.min(rs+IN_LIMIT,roots.length);
                for(int ts=0;ts<targets.length;ts+=IN_LIMIT) {
                    int te=Math.min(ts+IN_LIMIT,targets.length);String[] args=new String[re-rs+te-ts];
                    System.arraycopy(roots,rs,args,0,re-rs);System.arraycopy(targets,ts,args,re-rs,te-ts);
                    List<Row> rows=query.read(sql(page,re-rs,te-ts,lock),args);
                    EsignValues.require(rows!=null,"组织页签批改查询没有返回结果集。");result.addAll(rows);
                }
            }
            return result;
        }
        public List<Row> readPages(NativeOrgBatchPage page,String[] ids,boolean lock) throws BusinessException {
            List<Row> result=new ArrayList<Row>();
            for(int start=0;start<ids.length;start+=IN_LIMIT) {
                int end=Math.min(start+IN_LIMIT,ids.length);String[] args=new String[end-start];
                System.arraycopy(ids,start,args,0,args.length);List<Row> rows=query.read(pageSql(page,args.length,lock),args);
                EsignValues.require(rows!=null,"组织页签计划行查询没有返回结果集。");result.addAll(rows);
            }
            return result;
        }};
    }
    static String sql(NativeOrgBatchPage page,int roots,int targets,boolean lock) {
        if(page==null || roots<1 || roots>IN_LIMIT || targets<1 || targets>IN_LIMIT)
            throw new IllegalArgumentException("Invalid native page selection");
        StringBuilder sql=new StringBuilder("SELECT ").append(page.key).append(',').append(page.rootKey)
            .append(",pk_org,pk_group FROM ").append(page.table).append(" WHERE ").append(page.rootKey).append(" IN (");
        placeholders(sql,roots);sql.append(") AND pk_org IN (");placeholders(sql,targets);
        sql.append(") ORDER BY ").append(page.rootKey).append(",pk_org,").append(page.key);
        if(lock) sql.append(" FOR UPDATE NOWAIT");return sql.toString();
    }
    static String pageSql(NativeOrgBatchPage page,int count,boolean lock) {
        if(page==null || count<1 || count>IN_LIMIT) throw new IllegalArgumentException("Invalid planned page selection");
        StringBuilder sql=new StringBuilder("SELECT ").append(page.key).append(',').append(page.rootKey)
            .append(",pk_org,pk_group FROM ").append(page.table).append(" WHERE ").append(page.key).append(" IN (");
        placeholders(sql,count);sql.append(") ORDER BY ").append(page.key);
        if(lock) sql.append(" FOR UPDATE NOWAIT");return sql.toString();
    }
    private static void placeholders(StringBuilder sql,int count) {
        for(int i=0;i<count;i++) { if(i>0) sql.append(',');sql.append('?'); }
    }
    private static boolean validPk(String value) { return value!=null && PK.matcher(value).matches(); }
    private static String pack(String... values) {
        StringBuilder result=new StringBuilder();for(String value:values) result.append(value==null?-1:value.length()).append(':').append(value==null?"":value);
        return result.toString();
    }
}
