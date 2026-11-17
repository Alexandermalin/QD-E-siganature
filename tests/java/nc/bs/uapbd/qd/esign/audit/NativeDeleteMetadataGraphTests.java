package nc.bs.uapbd.qd.esign.audit;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import nc.md.common.AssociationKind;
import nc.md.model.IAssociation;
import nc.md.model.IAttribute;
import nc.md.model.IBean;
import nc.md.model.IColumn;
import nc.md.model.IForeignKey;
import nc.md.model.ITable;
import nc.md.model.type.IType;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;

/** Cascades and physical extensions are read by their server-provided metadata links. */
public final class NativeDeleteMetadataGraphTests {
    private NativeDeleteMetadataGraphTests() { }

    public static void run() throws Exception {
        Data data=new Data();
        NativeDeleteMetadataGraph.Capture before=NativeDeleteMetadataGraph.capture(data.root.bean(),
            Collections.singletonList(data.rootRow()),data,true);
        require(before.getRows().size()==6 && data.locked==4,"Nested metadata rows or locked reads missing");
        require(find(before.getRows(),"x_extra","C")!=null && find(before.getRows(),"x_page_child","N2")!=null,
            "Physical extension or nested child omitted");
        require(before.tableNames().containsAll(Arrays.asList("x_root","x_extra","x_page","x_page_child")),
            "The metadata table set omitted a cascade table");
        List<String> facts=before.evidence();
        data.rows.add(row("x_page_child","N3","pk_child","N3","page_ref","P2"));
        require(!facts.equals(NativeDeleteMetadataGraph.capture(data.root.bean(),Collections.singletonList(data.rootRow()),data,false).evidence()),
            "New cascade row did not change authorization evidence");
        data=new Data();before=NativeDeleteMetadataGraph.capture(data.root.bean(),Collections.singletonList(data.rootRow()),data,false);
        data.remove("x_extra","C");data.remove("x_page","P1");
        data.rows.add(row("x_page_child","N3","pk_child","N3","page_ref","P1"));
        List<SnapshotRow> after=NativeDeleteMetadataGraph.recapture(before,data,true);
        require(find(after,"x_root","C")==null && find(after,"x_page","P1")==null
            && find(after,"x_page_child","N1")!=null && find(after,"x_page_child","N3")!=null
            && find(after,"x_page","P2")!=null,"Recapture missed orphaned or newly linked rows after parent deletion");
        data=new Data();before=NativeDeleteMetadataGraph.capture(data.root.bean(),Collections.singletonList(data.rootRow()),data,false);
        data.remove("x_page","P1");data.rows.add(row("x_page","P1","pk_page","P1","parent_ref","OTHER"));
        try { NativeDeleteMetadataGraph.recapture(before,data,false);throw new AssertionError("A transferred cascade row was accepted"); }
        catch(BusinessException expected) { }
        data=new Data();facts=NativeDeleteMetadataGraph.capture(data.root.bean(),Collections.singletonList(data.rootRow()),data,false).evidence();
        data.page.associations.clear();
        require(!facts.equals(NativeDeleteMetadataGraph.capture(data.root.bean(),Collections.singletonList(data.rootRow()),data,false).evidence()),
            "Metadata graph change did not change authorization evidence");
        data=new Data();data.page.table.foreign.clear();reject(data,"Unverified parent relation accepted");
        data=new Data();data.page.table.fk("parent_ref2",data.root.table,"pk_customer");reject(data,"Ambiguous parent relation accepted");
        data=new Data();data.root.table.fk("page_ref",data.page.table,"pk_page");reject(data,"Potentially shared reverse reference accepted");
        data=new Data();data.page.associations.add(association("cycle",data.root));reject(data,"Cyclic cascade accepted");
        data=new Data();data.root.mapped.add(new Table("x_unexpected","pk_x","pk_x").proxy());
        reject(data,"Unaccounted ORM table accepted");
        data=new Data();data.wrong=true;reject(data,"A row belonging to another parent was accepted");
        data=new Data();data.rows.add(row("x_page","P1","pk_page","P1","parent_ref","C"));
        reject(data,"Duplicate cascade row accepted");
        data=new Data();data.remove("x_extra","C");data.rows.add(row("x_extra","C","pk_customer","C","flag","extra"));
        facts=NativeDeleteMetadataGraph.capture(data.root.bean(),
            Collections.singletonList(row("x_root","C","pk_customer","C","flag","main")),data,false).evidence();
        data.remove("x_extra","C");data.rows.add(row("x_extra","C","pk_customer","C","flag","main"));
        require(!facts.equals(NativeDeleteMetadataGraph.capture(data.root.bean(),
            Collections.singletonList(row("x_root","C","pk_customer","C","flag","extra")),data,false).evidence()),
            "Swapping row values across physical tables preserved delete authorization evidence");
        SnapshotRow unlinked=row("x_extra","C","pk_customer","C");
        SnapshotRow linked=new SnapshotRow(unlinked.getEntity(),unlinked.getRowId(),"PARENT",unlinked.getValues(),
            unlinked.getTypes(),unlinked.getLabels(),true);
        require(!SnapshotSupport.rowFingerprint(unlinked).equals(SnapshotSupport.rowFingerprint(linked)),
            "Snapshot parent identity was omitted from authorization evidence");
        updateScope();
        System.out.println("PASS NativeDeleteMetadataGraphTests (metadata cascade, extension and drift; no NC or Oracle)");
    }

    private static void updateScope() throws Exception {
        final Data data=new Data();data.root.associations.clear();
        final boolean[] cascade={false};final IAssociation[] link={null};
        final IType type=proxy(IType.class,new InvocationHandler() {
            public Object invoke(Object ignored,Method method,Object[] args) { return empty(method); }
        });
        final IAttribute attribute=proxy(IAttribute.class,new InvocationHandler() {
            public Object invoke(Object ignored,Method method,Object[] args) {
                String name=method.getName();if("getName".equals(name)) return "pages";
                if("getID".equals(name)) return "ATTR";if("getDataType".equals(name)) return type;
                if("getAssociation".equals(name)) return link[0];return empty(method);
            }
        });
        link[0]=proxy(IAssociation.class,new InvocationHandler() {
            public Object invoke(Object ignored,Method method,Object[] args) {
                String name=method.getName();if("getID".equals(name)) return "UPDATE_REL";
                if("getEndBean".equals(name)) return data.page.bean();if("getStartAttribute".equals(name)) return attribute;
                if("getType".equals(name)) return AssociationKind.Relation;
                if("isCascadeUpdate".equals(name)) return Boolean.valueOf(cascade[0]);return empty(method);
            }
        });
        data.root.attributes.put("pages",attribute);data.root.associations.add(link[0]);
        NativeDeleteMetadataGraph.Capture forced=NativeDeleteMetadataGraph.captureForUpdate(data.root.bean(),
            Collections.singletonList(data.rootRow()),data,true,new String[]{"pages"});
        require(find(forced.getRows(),"x_page","P1")!=null && find(forced.getRows(),"x_page_child","N2")!=null,
            "Native-declared child not marked cascade-update was omitted from update evidence");
        cascade[0]=true;
        NativeDeleteMetadataGraph.Capture cascaded=NativeDeleteMetadataGraph.captureForUpdate(data.root.bean(),
            Collections.singletonList(data.rootRow()),data,false,new String[0]);
        require(find(cascaded.getRows(),"x_page","P1")!=null && find(cascaded.getRows(),"x_page_child","N2")!=null,
            "Update-only cascade was omitted from update evidence");
        require(!forced.metadataEvidence().equals(cascaded.metadataEvidence()),"Native child or update cascade change was not bound");
        try { NativeDeleteMetadataGraph.captureForUpdate(data.root.bean(),Collections.singletonList(data.rootRow()),data,false,new String[]{"missing"});
            throw new AssertionError("A native child without metadata was accepted"); } catch(BusinessException expected) { }
        try { NativeDeleteMetadataGraph.captureForUpdate(data.root.bean(),Collections.singletonList(data.rootRow()),data,false,new String[]{"pages","pages"});
            throw new AssertionError("Duplicate native child metadata was accepted"); } catch(BusinessException expected) { }
        data.root.associations.clear();
        try { NativeDeleteMetadataGraph.captureForUpdate(data.root.bean(),Collections.singletonList(data.rootRow()),data,false,new String[]{"pages"});
            throw new AssertionError("A child detached from its owning bean was accepted"); } catch(BusinessException expected) { }
    }

    private static void reject(Data data,String message) throws Exception {
        try { NativeDeleteMetadataGraph.capture(data.root.bean(),Collections.singletonList(data.rootRow()),data,false);
            throw new AssertionError(message); }
        catch(BusinessException expected) { }
    }
    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
    private static SnapshotRow find(List<SnapshotRow> rows,String table,String id) {
        for(SnapshotRow row:rows) if(table.equals(row.getEntity()) && id.equals(row.getRowId())) return row;return null;
    }
    private static SnapshotRow row(String table,String id,String... fields) {
        Map<String,String> values=new HashMap<String,String>();
        for(int index=0;index<fields.length;index+=2) values.put(fields[index],fields[index+1]);
        return new SnapshotRow(table,id,null,values,Collections.<String,String>emptyMap(),Collections.<String,String>emptyMap(),true);
    }
    private static final class Data implements NativeDeleteMetadataGraph.Reader {
        final Bean root=new Bean("ROOT",new Table("x_root","pk_customer","pk_customer","page_ref"));
        final Bean page=new Bean("PAGE",new Table("x_page","pk_page","pk_page","parent_ref","parent_ref2"));
        final Bean child=new Bean("CHILD",new Table("x_page_child","pk_child","pk_child","page_ref"));
        final Table extra=new Table("x_extra","pk_customer","pk_customer");
        final List<SnapshotRow> rows=new ArrayList<SnapshotRow>();int locked;boolean wrong;
        Data() {
            root.extra.add(extra.proxy());root.associations.add(association("pages",page));page.associations.add(association("children",child));
            page.table.fk("parent_ref",root.table,"pk_customer");child.table.fk("page_ref",page.table,"pk_page");
            rows.addAll(Arrays.asList(row("x_extra","C","pk_customer","C"),
                row("x_page","P1","pk_page","P1","parent_ref","C"),row("x_page","P2","pk_page","P2","parent_ref","C"),
                row("x_page_child","N1","pk_child","N1","page_ref","P1"),row("x_page_child","N2","pk_child","N2","page_ref","P2")));
        }
        void remove(String table,String id) {
            for(java.util.Iterator<SnapshotRow> it=rows.iterator();it.hasNext();) {
                SnapshotRow row=it.next();if(table.equals(row.getEntity()) && id.equals(row.getRowId())) it.remove();
            }
        }
        SnapshotRow rootRow() { return row("x_root","C","pk_customer","C"); }
        public List<SnapshotRow> read(ITable table,String match,String value,boolean lock) {
            if(lock) locked++;
            List<SnapshotRow> result=new ArrayList<SnapshotRow>();
            for(SnapshotRow row:rows) if(table.getName().equals(row.getEntity()) && value.equals(row.getValues().get(match))) result.add(row);
            if(wrong && "x_page".equals(table.getName())) result.add(row("x_page","BAD","pk_page","BAD","parent_ref","OTHER"));
            return result;
        }
    }
    private static final class Bean {
        final String id;final Table table;final List<ITable> extra=new ArrayList<ITable>(),mapped=new ArrayList<ITable>();
        final List<IAssociation> associations=new ArrayList<IAssociation>();
        final Map<String,IAttribute> attributes=new HashMap<String,IAttribute>();
        Bean(String id,Table table) { this.id=id;this.table=table; }
        IBean bean() { return proxy(IBean.class,new InvocationHandler() {
            public Object invoke(Object proxy,Method method,Object[] args) {
                String name=method.getName();
                if("getID".equals(name)) return id;if("getTable".equals(name)) return table.proxy();
                if("getExtendTableList".equals(name)) return extra;if("getAssociations".equals(name)) return associations;
                if("getAttributeByName".equals(name)) return attributes.get(args[0]);
                if("getORMAPTableList".equals(name)) return mapped;return empty(method);
            }
        }); }
    }
    private static final class Table {
        final String name,pk;final Map<String,IColumn> columns=new HashMap<String,IColumn>();
        final List<IForeignKey> foreign=new ArrayList<IForeignKey>();
        Table(String name,String pk,String... fields) { this.name=name;this.pk=pk;
            for(String field:fields) columns.put(field.toLowerCase(),column(field)); }
        ITable proxy() { return NativeDeleteMetadataGraphTests.proxy(ITable.class,new InvocationHandler() {
            public Object invoke(Object proxy,Method method,Object[] args) {
                String operation=method.getName();if("getName".equals(operation)) return name;
                if("getPrimaryKeyName".equals(operation)) return pk;if("getForeignKeies".equals(operation)) return foreign;
                if("getColumnByName".equals(operation)) return columns.get(((String)args[0]).toLowerCase());return empty(method);
            }
        }); }
        void fk(final String start,final Table end,final String finish) {
            foreign.add(NativeDeleteMetadataGraphTests.proxy(IForeignKey.class,new InvocationHandler() {
                public Object invoke(Object proxy,Method method,Object[] args) {
                    String operation=method.getName();if("getStartTable".equals(operation)) return Table.this.proxy();
                    if("getEndTable".equals(operation)) return end.proxy();if("getStartColumn".equals(operation)) return columns.get(start);
                    if("getEndColumn".equals(operation)) return end.columns.get(finish);return empty(method);
                }
            }));
        }
    }
    private static IAssociation association(final String id,final Bean child) {
        return proxy(IAssociation.class,new InvocationHandler() {
            public Object invoke(Object proxy,Method method,Object[] args) {
                String name=method.getName();if("getID".equals(name)) return id;if("getEndBean".equals(name)) return child.bean();
                if("getType".equals(name)) return AssociationKind.Composite;return empty(method);
            }
        });
    }
    private static IColumn column(final String name) { return proxy(IColumn.class,new InvocationHandler() {
        public Object invoke(Object proxy,Method method,Object[] args) { return "getName".equals(method.getName())?name:empty(method); }
    }); }
    private static Object empty(Method method) {
        if(method.getReturnType()==boolean.class) return Boolean.FALSE;if(method.getReturnType()==int.class) return Integer.valueOf(0);
        return null;
    }
    private static <T> T proxy(Class<T> type,InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},handler));
    }
}
