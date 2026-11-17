package nc.bs.uapbd.qd.esign.audit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.md.common.AssociationKind;
import nc.md.model.IAssociation;
import nc.md.model.IAttribute;
import nc.md.model.IBean;
import nc.md.model.IColumn;
import nc.md.model.IForeignKey;
import nc.md.model.ITable;
import nc.md.util.MDUtil;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;

/** Resolves cascading physical rows solely from server-side NC metadata. */
public final class NativeDeleteMetadataGraph {
    private NativeDeleteMetadataGraph() { }

    public interface Reader {
        List<SnapshotRow> read(ITable table,String match,String value,boolean lock) throws BusinessException;
    }

    public static final class Capture {
        private final List<SnapshotRow> rows;private final List<String> evidence,metadataEvidence;
        private final Node graph;private final Map<String,ITable> tables;
        Capture(Node graph,Map<String,ITable> tables,Map<String,SnapshotRow> rows,Set<String> metadata) {
            this.graph=graph;this.tables=Collections.unmodifiableMap(new TreeMap<String,ITable>(tables));
            this.rows=Collections.unmodifiableList(new ArrayList<SnapshotRow>(rows.values()));
            List<String> facts=new ArrayList<String>();
            for(String item:metadata) facts.add("metadata:"+SnapshotSupport.sha256(item));
            Collections.sort(facts);this.metadataEvidence=Collections.unmodifiableList(new ArrayList<String>(facts));
            for(SnapshotRow row:rows.values()) facts.add("row:"+SnapshotSupport.rowFingerprint(row));
            Collections.sort(facts);this.evidence=Collections.unmodifiableList(facts);
        }
        public List<SnapshotRow> getRows() { return rows; }
        public List<String> evidence() { return evidence; }
        public List<String> metadataEvidence() { return metadataEvidence; }
        public Set<String> tableNames() { return Collections.unmodifiableSet(tables.keySet()); }
    }

    /** Build the complete metadata graph even for tables that currently contain no rows. */
    public static Capture capture(IBean root,List<SnapshotRow> roots,Reader reader,boolean lock) throws BusinessException {
        return capture(root,roots,reader,lock,false,Collections.<String>emptySet(),new TreeSet<String>());
    }

    /** Include native update cascades and every explicitly loaded child, even if it is not a delete cascade. */
    public static Capture captureForUpdate(IBean root,List<SnapshotRow> roots,Reader reader,boolean lock,String[] nativeChildren)
            throws BusinessException {
        EsignValues.require(root!=null && nativeChildren!=null,"组织页签更新缺少原生子属性范围。");
        Set<String> metadata=new TreeSet<String>(),forced=requiredChildren(root,nativeChildren,metadata);
        return capture(root,roots,reader,lock,true,forced,metadata);
    }
    private static Capture capture(IBean root,List<SnapshotRow> roots,Reader reader,boolean lock,boolean update,
            Set<String> forced,Set<String> metadata) throws BusinessException {
        EsignValues.require(root!=null && roots!=null && reader!=null,"删除元数据或主档快照缺失。");
        Node graph=build(root,new HashSet<String>(),metadata,update,forced);
        Map<String,ITable> tables=new TreeMap<String,ITable>();collectTables(graph,tables);
        Map<String,SnapshotRow> rows=new TreeMap<String,SnapshotRow>();
        Set<String> rootIds=new HashSet<String>();
        for(SnapshotRow row:roots) {
            verifyRow(graph.table,row);
            EsignValues.require(rootIds.add(row.getRowId()),"删除元数据主行重复。");
            visit(graph,row,reader,lock,rows);
        }
        return new Capture(graph,tables,rows,metadata);
    }

    private static Set<String> requiredChildren(IBean root,String[] names,Set<String> metadata) throws BusinessException {
        Set<String> requested=new HashSet<String>(),forced=new HashSet<String>();
        List<IAssociation> associations=root.getAssociations();
        for(String name:names) {
            EsignValues.require(name!=null && name.matches("[A-Za-z][A-Za-z0-9_]{0,63}") && requested.add(name),
                "原生组织页签子属性无效或重复。");
            IAttribute attribute=root.getAttributeByName(name);
            IAssociation selected=attribute==null?null:attribute.getAssociation();
            EsignValues.require(attribute!=null && name.equals(attribute.getName()) && present(attribute.getID())
                && selected!=null && present(selected.getID()) && associations!=null,
                "原生组织页签子属性缺少关联元数据："+name);
            int matches=0;
            for(IAssociation candidate:associations) if(candidate!=null && selected.getID().equals(candidate.getID())) {
                IAttribute start=candidate.getStartAttribute();
                EsignValues.require(start!=null && attribute.getID().equals(start.getID())
                    && candidate.getEndBean()!=null && selected.getEndBean()!=null
                    && present(selected.getEndBean().getID())
                    && selected.getEndBean().getID().equals(candidate.getEndBean().getID()),
                    "原生组织页签子属性关联不一致："+name);
                matches++;
            }
            EsignValues.require(matches==1 && forced.add(selected.getID()),"原生组织页签子属性关联缺失或重复："+name);
            metadata.add(pack("update-child",name,attribute.getID(),selected.getID()));
        }
        return forced;
    }

    /** Re-read prior identities and follow prior parent keys even if a parent was deleted. */
    public static List<SnapshotRow> recapture(Capture before,Reader reader,boolean lock) throws BusinessException {
        EsignValues.require(before!=null && reader!=null,"删除后缺少元数据快照范围。");
        Map<String,SnapshotRow> current=new TreeMap<String,SnapshotRow>();
        for(SnapshotRow prior:before.rows) {
            ITable table=before.tables.get(lower(prior.getEntity()));
            EsignValues.require(table!=null,"删除后缺少已授权物理表的元数据。");
            List<SnapshotRow> rows=reader.read(table,lower(table.getPrimaryKeyName()),prior.getRowId(),lock);
            EsignValues.require(rows!=null && rows.size()<=1,"删除后物理主键读取不完整。");
            for(SnapshotRow row:rows) {
                verifyRow(table,row);EsignValues.require(prior.getRowId().equals(row.getRowId()),"删除后读取了其他物理行。");
                add(current,row);
            }
        }
        visitHistorical(before.graph,before.rows,reader,lock,current);
        verifyUnmoved(before.graph,before.rows,current);
        return Collections.unmodifiableList(new ArrayList<SnapshotRow>(current.values()));
    }

    private static Node build(IBean bean,Set<String> path,Set<String> metadata,boolean update,Set<String> forced) throws BusinessException {
        EsignValues.require(bean!=null && bean.getTable()!=null && present(bean.getID()),"删除元数据缺少业务实体或物理表。");
        ITable table=bean.getTable();validateTable(table);
        String marker=bean.getID()+"\u0000"+lower(table.getName());
        EsignValues.require(path.add(marker),"删除元数据包含循环级联。");
        Node node=new Node(table);metadata.add(pack("bean",bean.getID(),lower(table.getName()),lower(table.getPrimaryKeyName())));
        List<ITable> extras=bean.getExtendTableList();
        if(extras!=null) for(ITable extra:extras) {
            EsignValues.require(extra!=null,"删除元数据包含无效的扩展表。");validateTable(extra);
            Edge edge=edge(table,extra,true);node.children.add(edge);
            metadata.add(pack("extend",bean.getID(),lower(extra.getName()),lower(extra.getPrimaryKeyName()),edge.parentField,edge.childField));
        }
        List<ITable> mapped=bean.getORMAPTableList();
        if(mapped!=null) for(ITable mappedTable:mapped) {
            validateTable(mappedTable);
            boolean known=sameMapping(table,mappedTable);
            if(extras!=null) for(ITable extra:extras) if(sameMapping(extra,mappedTable)) known=true;
            EsignValues.require(known,"删除元数据包含未经核实的映射表："+mappedTable.getName());
        }
        List<IAssociation> associations=bean.getAssociations();
        if(associations!=null) for(IAssociation association:associations) {
            EsignValues.require(association!=null && association.getType()!=null,"删除元数据关联不完整。");
            if(update) EsignValues.require(present(association.getID()),"组织页签更新关联缺少稳定标识。");
            boolean writes=association.isCascadeDelete() || association.getType()==AssociationKind.Composite
                || update && (association.isCascadeUpdate() || forced.contains(association.getID()));
            if(association.getType()==AssociationKind.Relation) {
                EsignValues.require(association.getStartAttribute()!=null && association.getStartAttribute().getDataType()!=null,
                    "删除元数据关联字段不完整。");
                writes=writes || !MDUtil.isRefType(association.getStartAttribute().getDataType())
                    && !association.getStartAttribute().isCalculation();
            }
            if(!writes) continue;
            IBean child=association.getEndBean();
            EsignValues.require(child!=null && child.getTable()!=null && present(child.getID()),"删除级联对象缺少物理元数据。");
            Edge edge=edge(table,child.getTable(),false);
            edge.child=build(child,path,metadata,update,Collections.<String>emptySet());node.children.add(edge);
            if(update) metadata.add(pack("update-association",bean.getID(),association.getID(),association.getType().name(),
                Boolean.toString(association.isCascadeDelete()),Boolean.toString(association.isCascadeUpdate()),
                child.getID(),lower(child.getTable().getName()),edge.parentField,edge.childField));
            else metadata.add(pack("association",bean.getID(),association.getID(),association.getType().name(),
                Boolean.toString(association.isCascadeDelete()),child.getID(),lower(child.getTable().getName()),edge.parentField,edge.childField));
        }
        path.remove(marker);return node;
    }

    private static Edge edge(ITable parent,ITable child,boolean extension) throws BusinessException {
        validateTable(parent);validateTable(child);
        Set<String> mappings=new TreeSet<String>(),reverse=new TreeSet<String>();
        collectForeignKeys(child,parent,true,mappings);
        collectForeignKeys(parent,child,false,reverse);
        // A parent-held reference can point to a shared object. Metadata alone does not prove exclusivity.
        EsignValues.require(reverse.isEmpty(),"删除元数据包含可能被其他档案共享的反向关联："+parent.getName()+" / "+child.getName());
        if(mappings.isEmpty() && extension && parent.getPrimaryKeyName().equalsIgnoreCase(child.getPrimaryKeyName())
                && column(child,parent.getPrimaryKeyName())!=null)
            mappings.add(pack(lower(parent.getPrimaryKeyName()),lower(parent.getPrimaryKeyName())));
        EsignValues.require(mappings.size()==1,"删除元数据的父子表关联缺失或不唯一："+parent.getName()+" / "+child.getName());
        String selected=mappings.iterator().next();String[] fields=unpackPair(selected);
        EsignValues.require(column(parent,fields[0])!=null && column(child,fields[1])!=null,
            "删除元数据父子关联字段不存在。");
        return new Edge(child,fields[0],fields[1]);
    }

    private static void collectForeignKeys(ITable source,ITable target,boolean childToParent,Set<String> mappings)
            throws BusinessException {
        List<IForeignKey> keys=source.getForeignKeies();if(keys==null) return;
        for(IForeignKey key:keys) {
            EsignValues.require(key!=null && key.getStartTable()!=null && key.getEndTable()!=null,
                "删除元数据外键缺少关联表。");
            if(!sameTable(source,key.getStartTable()) || !sameTable(target,key.getEndTable())) continue;
            EsignValues.require(key.getStartColumn()!=null && key.getEndColumn()!=null
                && present(key.getStartColumn().getName()) && present(key.getEndColumn().getName()),"删除元数据外键字段缺失。");
            String from=lower(key.getStartColumn().getName()),to=lower(key.getEndColumn().getName());
            mappings.add(childToParent?pack(to,from):pack(from,to));
        }
    }

    private static void visit(Node node,SnapshotRow parent,Reader reader,boolean lock,Map<String,SnapshotRow> rows)
            throws BusinessException {
        add(rows,parent);
        visitChildren(node,parent,reader,lock,rows);
    }

    private static void visitChildren(Node node,SnapshotRow parent,Reader reader,boolean lock,Map<String,SnapshotRow> rows)
            throws BusinessException {
        for(Edge edge:node.children) {
            EsignValues.require(parent.getValues().containsKey(edge.parentField),"删除快照缺少元数据关联字段。");
            String value=parent.getValues().get(edge.parentField);
            if(value==null || value.isEmpty()) continue;
            List<SnapshotRow> children=reader.read(edge.table,edge.childField,value,lock);
            EsignValues.require(children!=null,"删除级联物理数据读取不完整。");
            Set<String> ids=new HashSet<String>();
            for(SnapshotRow child:children) {
                verifyRow(edge.table,child);
                EsignValues.require(value.equals(child.getValues().get(edge.childField)) && ids.add(child.getRowId()),
                    "删除级联物理行不属于预期父对象或重复。");
                if(edge.child==null) add(rows,child);else visit(edge.child,child,reader,lock,rows);
            }
        }
    }

    private static void visitHistorical(Node node,List<SnapshotRow> prior,Reader reader,boolean lock,
            Map<String,SnapshotRow> rows) throws BusinessException {
        for(SnapshotRow row:prior) if(lower(node.table.getName()).equals(row.getEntity()))
            visitChildren(node,row,reader,lock,rows);
        for(Edge edge:node.children) if(edge.child!=null) visitHistorical(edge.child,prior,reader,lock,rows);
    }

    private static void collectTables(Node node,Map<String,ITable> tables) throws BusinessException {
        table(node.table,tables);
        for(Edge edge:node.children) {
            table(edge.table,tables);
            if(edge.child!=null) collectTables(edge.child,tables);
        }
    }
    private static void verifyUnmoved(Node node,List<SnapshotRow> prior,Map<String,SnapshotRow> current) throws BusinessException {
        for(Edge edge:node.children) {
            verifyField(node.table,edge.parentField,prior,current);
            verifyField(edge.table,edge.childField,prior,current);
            if(edge.child!=null) verifyUnmoved(edge.child,prior,current);
        }
    }
    private static void verifyField(ITable table,String field,List<SnapshotRow> prior,Map<String,SnapshotRow> current)
            throws BusinessException {
        String name=lower(table.getName());
        for(SnapshotRow old:prior) if(name.equals(old.getEntity())) {
            SnapshotRow row=current.get(name+"\u0000"+old.getRowId());if(row==null) continue;
            EsignValues.require(old.getValues().containsKey(field) && row.getValues().containsKey(field)
                && java.util.Objects.equals(old.getValues().get(field),row.getValues().get(field)),
                "删除级联行的父子关联被转移，必须回滚。");
        }
    }
    private static void table(ITable table,Map<String,ITable> tables) throws BusinessException {
        String name=lower(table.getName());ITable prior=tables.put(name,table);
        EsignValues.require(prior==null || sameMapping(prior,table),"同一删除物理表的元数据主键不一致。");
    }

    private static void add(Map<String,SnapshotRow> rows,SnapshotRow row) throws BusinessException {
        String key=row.getEntity()+"\u0000"+row.getRowId();SnapshotRow prior=rows.put(key,row);
        EsignValues.require(prior==null || SnapshotSupport.json(prior).equals(SnapshotSupport.json(row)),
            "同一删除物理行在不同级联路径中不一致。");
    }
    private static void verifyRow(ITable table,SnapshotRow row) throws BusinessException {
        EsignValues.require(row!=null && lower(table.getName()).equals(lower(row.getEntity()))
            && row.getRowId().equals(row.getValues().get(lower(table.getPrimaryKeyName()))),"删除物理行与元数据主键不一致。");
    }
    private static void validateTable(ITable table) throws BusinessException {
        EsignValues.require(table!=null && identifier(table.getName()) && identifier(table.getPrimaryKeyName()),"删除元数据表名或主键无效。");
    }
    private static IColumn column(ITable table,String name) {
        IColumn result=table.getColumnByName(name);
        if(result==null) result=table.getColumnByName(name.toUpperCase(Locale.ENGLISH));
        return result;
    }
    private static boolean sameTable(ITable a,ITable b) {
        return a!=null && b!=null && a.getName()!=null && b.getName()!=null && a.getName().equalsIgnoreCase(b.getName());
    }
    private static boolean sameMapping(ITable a,ITable b) {
        return sameTable(a,b) && a.getPrimaryKeyName()!=null && b.getPrimaryKeyName()!=null
            && a.getPrimaryKeyName().equalsIgnoreCase(b.getPrimaryKeyName());
    }
    private static String lower(String value) { return value==null?null:value.toLowerCase(Locale.ENGLISH); }
    private static boolean identifier(String name) { return name!=null && name.matches("[A-Za-z][A-Za-z0-9_$#]{0,29}"); }
    private static boolean present(String value) { return value!=null && !value.trim().isEmpty(); }
    private static String pack(String... values) {
        StringBuilder result=new StringBuilder();for(String value:values)
            result.append(value==null?-1:value.length()).append(':').append(value==null?"":value);
        return result.toString();
    }
    private static String[] unpackPair(String value) {
        int first=value.indexOf(':');int length=Integer.parseInt(value.substring(0,first));
        int end=first+1+length;String a=value.substring(first+1,end);int second=value.indexOf(':',end);
        return new String[]{a,value.substring(second+1)};
    }
    public static Reader database() {
        return new Reader() {
            private final PhysicalSnapshotReader physical=new PhysicalSnapshotReader();
            public List<SnapshotRow> read(ITable table,String match,String value,boolean lock) throws BusinessException {
                return physical.readMetadata(table,null,match,value,lock);
            }
        };
    }
    private static final class Node {
        final ITable table;final List<Edge> children=new ArrayList<Edge>();Node(ITable table) { this.table=table; }
    }
    private static final class Edge {
        final ITable table;final String parentField,childField;Node child;
        Edge(ITable table,String parentField,String childField) { this.table=table;this.parentField=parentField;this.childField=childField; }
    }
}
