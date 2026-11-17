package nc.bs.uapbd.qd.esign.core;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import nc.md.model.IAttribute;
import nc.md.model.IBean;
import nc.md.model.IColumn;
import nc.md.model.ITable;
import nc.md.model.type.IType;
import nc.md.model.type.impl.PrimitiveType;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;

/** The batch request must use trusted field configuration and currently verified physical NC metadata. */
public final class NativeBatchMetadataFieldsTests {
    private NativeBatchMetadataFieldsTests() { }
    public static void run() throws Exception {
        materialMnemonicUsesNativeValueHandling();
        materialClassificationRequiresExactPhysicalMapping();
        Fixture f=new Fixture();
        NativeBatchMetadataFields.Verified metadata=f.verify();
        Map<String,Object> values=new HashMap<String,Object>();values.put("name","name1");
        NativeBatchChangeSet original=NativeBatchChangeSet.create(new String[]{"name"},values,metadata);
        f.column.put("getSQLDataType","NVARCHAR2");
        NativeBatchChangeSet changed=NativeBatchChangeSet.create(new String[]{"name"},values,f.verify());
        require(!original.getFingerprint().equals(changed.getFingerprint()),"Changed physical metadata did not invalidate authorization");
        values.put("name",Integer.valueOf(1));
        try { NativeBatchChangeSet.create(new String[]{"name"},values,metadata);throw new AssertionError("Invalid metadata value type accepted"); }
        catch(BusinessException expected) { }
        values.put("name",null);f.attr.put("isNullable",Boolean.FALSE);metadata=f.verify();
        try { NativeBatchChangeSet.create(new String[]{"name"},values,metadata);throw new AssertionError("Null for a non-null field accepted"); }
        catch(BusinessException expected) { }
        f.attr.put("isNullable",Boolean.TRUE);
        f.attr.put("hasAccessPower",Boolean.TRUE);reject(f);f.attr.put("hasAccessPower",Boolean.FALSE);
        f.attr.put("isReadOnly",Boolean.TRUE);reject(f);f.attr.put("isReadOnly",Boolean.FALSE);
        f.column.put("getName","password");reject(f);f.column.put("getName","name");
        f.table.put("getName","bd_supplier");reject(f);f.table.put("getName","bd_customer");
        f.allowed.clear();reject(f);f.allowed.add("name");
        try { NativeBatchMetadataFields.verify(f.bean,"bd_customer",new String[]{"pk_org"},new HashSet<String>(Arrays.asList("pk_org")));
            throw new AssertionError("Ownership field accepted before owner-transfer scope is supported"); }
        catch(BusinessException expected) { }
        try { NativeBatchMetadataFields.verify(f.bean,"bd_customer",new String[]{"name","name"},f.allowed);
            throw new AssertionError("Duplicate metadata field accepted"); }
        catch(BusinessException expected) { }
        Fixture material=new Fixture();material.table.put("getName","bd_material");
        for(String controlled:Arrays.asList("pk_source","pk_material_pf","latest","version")) {
            material.addRelated(controlled);
            try { NativeBatchMetadataFields.verify(material.bean,"bd_material",new String[]{controlled},
                    new HashSet<String>(Arrays.asList(controlled)));
                throw new AssertionError("Material version identity accepted as an ordinary batch field"); }
            catch(BusinessException expected) { }
        }
        Fixture linked=new Fixture();linked.addRelated("shortname");linked.allowed.add("shortname");
        NativeBatchMetadataFields.Verified both=linked.verify();
        Map<String,Object> linkedValues=new HashMap<String,Object>();linkedValues.put("name","n");linkedValues.put("shortname","s");
        NativeBatchChangeSet linkedSet=NativeBatchChangeSet.create(new String[]{"name"},linkedValues,both);
        require(linkedSet.getSelectedFields().length==1 && "name".equals(linkedSet.getSelectedFields()[0])
            && linkedSet.copyValues().size()==2,"Native selected and related fields were not kept distinct");
        linkedValues.put("shortname","changed");
        require(!linkedSet.getFingerprint().equals(NativeBatchChangeSet.create(new String[]{"name"},linkedValues,both).getFingerprint()),
            "Changing a native related field did not invalidate authorization");
        linkedValues.put("unexpected","x");
        try { NativeBatchChangeSet.create(new String[]{"name"},linkedValues,both);throw new AssertionError("Unconfigured related value accepted"); }
        catch(BusinessException expected) { }
        Fixture duplicate=new Fixture();duplicate.addRelated("shortname","name");duplicate.allowed.add("shortname");
        try { duplicate.verify();throw new AssertionError("Two attributes mapped to one physical column"); }
        catch(BusinessException expected) { }
        physicalChanges();
        System.out.println("PASS NativeBatchMetadataFieldsTests (trusted field configuration and physical metadata; no NC or Oracle)");
    }
    private static void materialMnemonicUsesNativeValueHandling() throws BusinessException {
        String field="materialmnecode";
        Fixture fixture=new Fixture();
        fixture.table.put("getName","bd_material");
        fixture.column.put("getName",field);
        fixture.attr.put("getName",field);
        PrimitiveType type=new PrimitiveType(1);
        type.setName("String");
        fixture.attr.put("getDataType",type);
        fixture.attributes.put(field,fixture.attributes.remove("name"));
        String[] selected={field};
        NativeBatchMetadataFields.Verified metadata=NativeBatchMetadataFields.verify(
            fixture.bean,"bd_material",selected,Collections.singleton(field));
        for(String value:new String[]{"1","ABC","001"}) {
            NativeBatchChangeSet change=NativeBatchChangeSet.create(
                selected,Collections.<String,Object>singletonMap(field,value),metadata);
            require(value.equals(change.copyValues().get(field)),"Material mnemonic changed before native processing");
        }
    }
    private static void materialClassificationRequiresExactPhysicalMapping() throws BusinessException {
        String field="pk_marbasclass";
        Fixture fixture=new Fixture();
        fixture.table.put("getName","bd_material");
        fixture.column.put("getName",field);
        fixture.attr.put("getName",field);
        fixture.attr.put("hasAccessPower",Boolean.TRUE);
        fixture.attributes.put(field,fixture.attributes.remove("name"));
        String[] selected={field};
        Set<String> configured=Collections.singleton(field);
        NativeBatchMetadataFields.verify(fixture.bean,"bd_material",selected,configured);

        fixture.column.put("getName","different_column");
        rejectClassification(fixture,"bd_material",selected,configured);
        fixture.column.put("getName",field);
        fixture.column.put("isPkey",Boolean.TRUE);
        rejectClassification(fixture,"bd_material",selected,configured);
        fixture.column.put("isPkey",Boolean.FALSE);
        fixture.attr.put("mappingExtendTable",Boolean.TRUE);
        rejectClassification(fixture,"bd_material",selected,configured);
        fixture.attr.put("mappingExtendTable",Boolean.FALSE);
        fixture.table.put("getName","bd_customer");
        rejectClassification(fixture,"bd_customer",selected,configured);

        fixture.table.put("getName","bd_material");
        fixture.attr.put("hasAccessPower",Boolean.FALSE);
        fixture.column.put("getName","different_column");
        rejectClassification(fixture,"bd_material",selected,configured);
        fixture.column.put("getName",field);
        String alias="classification_alias";
        fixture.attr.put("getName",alias);
        fixture.attributes.put(alias,fixture.attributes.remove(field));
        rejectClassification(fixture,"bd_material",new String[]{alias},Collections.singleton(alias));
    }
    private static void rejectClassification(Fixture fixture,String table,String[] selected,Set<String> configured)
            throws BusinessException {
        try {
            NativeBatchMetadataFields.verify(fixture.bean,table,selected,configured);
            throw new AssertionError("Unsupported material classification mapping was accepted");
        } catch(BusinessException expected) {
            require(expected.getMessage().contains("批改字段涉及"),"Unexpected classification mapping failure");
        }
    }
    private static void physicalChanges() throws Exception {
        Fixture f=new Fixture();f.column.put("getName","physical_name");f.addRelated("shortname");f.allowed.add("shortname");
        NativeBatchMetadataFields.Verified metadata=f.verify();
        Map<String,Object> changes=new HashMap<String,Object>();changes.put("name","new");changes.put("shortname","new2");
        NativeBatchChangeSet values=NativeBatchChangeSet.create(new String[]{"name"},changes,metadata);
        TestVO prior=new TestVO();
        SnapshotRow before=physical("old","old2"),after=physical("new","new2");
        values.requirePhysicalChanges(prior,"bd_customer","A",Collections.singletonList(before),Collections.singletonList(after));
        rejectPhysical(values,prior,before,physical("old","new2"));
        rejectPhysical(values,prior,before,physical("new","old2"));
        Map<String,String> incomplete=new HashMap<String,String>();incomplete.put("physical_name","new");
        rejectPhysical(values,prior,before,new SnapshotRow("bd_customer","A",null,incomplete,
            Collections.<String,String>emptyMap(),Collections.<String,String>emptyMap(),true));
    }
    private static SnapshotRow physical(String name,String shortname) {
        Map<String,String> columns=new HashMap<String,String>();columns.put("physical_name",name);columns.put("shortname",shortname);
        return new SnapshotRow("bd_customer","A",null,columns,Collections.<String,String>emptyMap(),Collections.<String,String>emptyMap(),true);
    }
    private static void rejectPhysical(NativeBatchChangeSet values,SuperVO prior,SnapshotRow before,SnapshotRow after) throws Exception {
        try { values.requirePhysicalChanges(prior,"bd_customer","A",Collections.singletonList(before),Collections.singletonList(after));
            throw new AssertionError("Unchanged or missing signed physical column accepted"); }
        catch(BusinessException expected) { }
    }
    private static final class TestVO extends SuperVO {
        private static final long serialVersionUID=1L;
        @Override public String getPrimaryKey() { return "A"; }
        @Override public Object getAttributeValue(String name) {
            if("name".equals(name)) return "old";
            if("shortname".equals(name)) return "old2";
            throw new AssertionError("Unexpected test attribute");
        }
    }
    static final class Fixture {
        final Map<String,Object> table=new HashMap<String,Object>(),column=new HashMap<String,Object>(),attr=new HashMap<String,Object>();
        final Set<String> allowed=new HashSet<String>(Arrays.asList("name"));final IBean bean;final ITable nativeTable;
        final Map<String,IAttribute> attributes=new HashMap<String,IAttribute>();
        Fixture() {
            table.put("getName","bd_customer");nativeTable=proxy(ITable.class,table);
            column.put("getName","name");column.put("getSQLDataType","VARCHAR2");column.put("getOwnerTableView",nativeTable);
            column.put("isActive",Boolean.TRUE);column.put("isNullable",Boolean.TRUE);IColumn c=proxy(IColumn.class,column);
            Map<String,Object> type=new HashMap<String,Object>();type.put("getFullName","java.lang.String");type.put("__valueType",String.class);
            IType dt=proxy(IType.class,type);
            attr.put("getName","name");attr.put("getColumn",c);attr.put("getDataType",dt);
            attr.put("isActive",Boolean.TRUE);attr.put("isNullable",Boolean.TRUE);IAttribute a=proxy(IAttribute.class,attr);
            attributes.put("name",a);Map<String,Object> b=new HashMap<String,Object>();b.put("getTable",nativeTable);b.put("__attributes",attributes);bean=proxy(IBean.class,b);
        }
        void addRelated(String name) { addRelated(name,name); }
        void addRelated(String name,String columnName) {
            Map<String,Object> col=new HashMap<String,Object>();col.put("getName",columnName);col.put("getSQLDataType","VARCHAR2");
            col.put("getOwnerTableView",nativeTable);col.put("isActive",Boolean.TRUE);col.put("isNullable",Boolean.TRUE);
            Map<String,Object> type=new HashMap<String,Object>();type.put("getFullName","java.lang.String");type.put("__valueType",String.class);
            Map<String,Object> related=new HashMap<String,Object>();related.put("getName",name);related.put("getColumn",proxy(IColumn.class,col));
            related.put("getDataType",proxy(IType.class,type));related.put("isActive",Boolean.TRUE);related.put("isNullable",Boolean.TRUE);
            attributes.put(name,proxy(IAttribute.class,related));
        }
        NativeBatchMetadataFields.Verified verify() throws BusinessException {
            return NativeBatchMetadataFields.verify(bean,"bd_customer",new String[]{"name"},allowed);
        }
    }
    @SuppressWarnings("unchecked") private static <T> T proxy(Class<T> type,final Map<String,Object> values) {
        return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},new InvocationHandler() {
            public Object invoke(Object proxy,Method method,Object[] args) {
                if("isInstance".equals(method.getName())) return Boolean.valueOf(((Class<?>)values.get("__valueType")).isInstance(args[0]));
                if("getAttributeByName".equals(method.getName())) return ((Map<?,?>)values.get("__attributes")).get(args[0]);
                Object value=values.get(method.getName());
                if(value==null && method.getReturnType()==Boolean.TYPE) return Boolean.FALSE;
                if(value==null && method.getReturnType()==Integer.TYPE) return Integer.valueOf(0);
                return value;
            }
        });
    }
    private static void reject(Fixture f) throws Exception {
        try { f.verify();throw new AssertionError("Unsafe native field metadata accepted"); }
        catch(BusinessException expected) { }
    }
    private static void require(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
}
