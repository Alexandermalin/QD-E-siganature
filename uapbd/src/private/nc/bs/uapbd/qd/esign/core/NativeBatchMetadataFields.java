package nc.bs.uapbd.qd.esign.core;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import nc.bs.uapbd.qd.esign.audit.SnapshotSupport;
import nc.md.model.IAttribute;
import nc.md.model.IBean;
import nc.md.model.IColumn;
import nc.md.model.ITableView;
import nc.md.model.type.IType;
import nc.vo.pub.BusinessException;

/** Candidate server-side validation of configured NC batch fields and their physical metadata. */
public final class NativeBatchMetadataFields {
    private NativeBatchMetadataFields() { }
    private static final class Field {
        final IType type;final boolean nullable;final String column;
        final boolean nativeValueValidation;
        Field(IType type,boolean nullable,String column,boolean nativeValueValidation) {
            this.type=type;this.nullable=nullable;this.column=column;
            this.nativeValueValidation=nativeValueValidation;
        }
    }
    public static final class Verified {
        private final Map<String,Field> fields;private final Set<String> selected;private final String fingerprint;
        private Verified(Map<String,Field> fields,Set<String> selected,String fingerprint) {
            this.fields=Collections.unmodifiableMap(fields);this.selected=Collections.unmodifiableSet(selected);this.fingerprint=fingerprint;
        }
        Set<String> names() { return fields.keySet(); }
        String fingerprint() { return fingerprint; }
        Map<String,String> columns() {
            Map<String,String> result=new HashMap<String,String>();
            for(Map.Entry<String,Field> entry:fields.entrySet()) result.put(entry.getKey(),entry.getValue().column);
            return Collections.unmodifiableMap(result);
        }
        void validate(String[] selected,Map<String,Object> values) throws BusinessException {
            EsignValues.require(selected!=null && values!=null && fields.keySet().equals(values.keySet())
                && selected.length==this.selected.size(),"批改字段与已核实的元数据不一致。");
            Set<String> names=new HashSet<String>();
            for(String name:selected) EsignValues.require(this.selected.contains(name) && names.add(name),"批改字段与已核实的元数据不一致。");
            for(Map.Entry<String,Object> value:values.entrySet()) {
                Field field=fields.get(value.getKey());Object raw=value.getValue();
                if(field.nativeValueValidation) continue;
                EsignValues.require(raw==null?field.nullable:field.type.isInstance(raw),"批改值不符合原生字段类型或非空限制。");
            }
        }
    }

    /** Production callers must pass the server-read native selection and related-field configuration. */
    public static Verified verify(IBean bean,String expectedTable,String[] selected,NativeBatchConfiguration.Verified configuration)
            throws BusinessException {
        EsignValues.require(configuration!=null && expectedTable!=null && expectedTable.equals(configuration.table()) && configuration.matches(selected),
            "批改配置与原生元数据主表不一致。");
        return verifyInternal(bean,expectedTable,selected,configuration.fields(),configuration.fingerprint());
    }
    /** Package-local test seam; callers outside this package cannot supply a client field allowlist. */
    static Verified verify(IBean bean,String expectedTable,String[] selected,Set<String> configured) throws BusinessException {
        return verifyInternal(bean,expectedTable,selected,configured,null);
    }
    private static Verified verifyInternal(IBean bean,String expectedTable,String[] selected,Set<String> configured,String configFingerprint)
            throws BusinessException {
        EsignValues.require(bean!=null && bean.getTable()!=null && configured!=null && selected!=null && selected.length>0
            && knownTable(expectedTable)
            && expectedTable.equalsIgnoreCase(bean.getTable().getName()),"批改原生元数据或主表不一致。");
        Set<String> nativeSelected=new HashSet<String>();
        for(String name:selected) EsignValues.require(validName(name) && configured.contains(name) && nativeSelected.add(name),
            "批改所选字段不在经服务端核实的原生配置中。");
        Map<String,Field> fields=new HashMap<String,Field>();Map<String,String> evidence=new TreeMap<String,String>();
        Set<String> physicalColumns=new HashSet<String>();
        for(String name:configured) {
            EsignValues.require(validName(name) && !blocked(name)
                && !("bd_material".equals(expectedTable) && materialVersionField(name)),
                "批改字段不在经服务端核实的原生配置中。");
            IAttribute attr=bean.getAttributeByName(name);IColumn column=attr==null?null:attr.getColumn();
            IType type=attr==null?null:attr.getDataType();ITableView table=column==null?null:column.getOwnerTableView();
            boolean materialTable="bd_material".equals(expectedTable);
            boolean materialColumn=materialTable && column!=null && name.equalsIgnoreCase(column.getName());
            boolean materialClassification=materialColumn && "pk_marbasclass".equals(name);
            boolean classificationAlias=materialTable && !materialClassification && column!=null
                && ("pk_marbasclass".equalsIgnoreCase(name) || "pk_marbasclass".equalsIgnoreCase(column.getName()));
            EsignValues.require(attr!=null && name.equals(attr.getName()) && attr.isActive() && !attr.isReadOnly()
                && !attr.isCalculation() && !attr.isPseudo() && !attr.isNotExistInModel() && !attr.isInnerControlAttr()
                && !classificationAlias && (!attr.hasAccessPower() || materialClassification)
                && !attr.mappingExtendTable() && column!=null && column.isActive() && !column.isPkey()
                && validName(column.getName()) && !blocked(column.getName())
                && !("bd_material".equals(expectedTable) && materialVersionField(column.getName()))
                && table!=null && expectedTable.equalsIgnoreCase(table.getName())
                && type!=null && present(type.getFullName()) && present(column.getSQLDataType()),
                "批改字段涉及未核实的权限、主键、扩展表或物理列。");
            boolean nullable=attr.isNullable() && column.isNullable();
            String physical=column.getName().toLowerCase(Locale.ENGLISH);
            EsignValues.require(physicalColumns.add(physical),"批改字段不能重复映射同一物理列。");
            // NC validates these values in its material batch service. The adapter checks the signed classification reference.
            boolean nativeValueValidation=materialClassification || (materialColumn && "materialmnecode".equals(name));
            fields.put(name,new Field(type,nullable,physical,nativeValueValidation));
            evidence.put(name,pack(physical,column.getSQLDataType(),type.getFullName(),nullable?"1":"0"));
        }
        StringBuilder digest=new StringBuilder(pack("batch-metadata-v1",expectedTable,configFingerprint));
        for(Map.Entry<String,String> item:evidence.entrySet()) digest.append(pack(item.getKey(),item.getValue()));
        return new Verified(fields,nativeSelected,SnapshotSupport.sha256(digest.toString()));
    }
    private static boolean blocked(String name) {
        String field=name.toLowerCase(Locale.ENGLISH);
        return SnapshotSupport.sensitive(field) || "pk_group".equals(field) || "pk_org".equals(field)
            || "pk_customer".equals(field) || "pk_supplier".equals(field) || "pk_material".equals(field)
            || "pk_cust_sup".equals(field) || "pk_customer_main".equals(field) || "pk_supplier_main".equals(field)
            || "code".equals(field) || "dr".equals(field) || "ts".equals(field) || "creator".equals(field)
            || "creationtime".equals(field) || "modifier".equals(field) || "modifiedtime".equals(field)
            || "enablestate".equals(field) || "custenablestate".equals(field) || "supenablestate".equals(field);
    }
    private static boolean materialVersionField(String name) {
        String field=name.toLowerCase(Locale.ENGLISH);
        return "pk_source".equals(field) || "pk_material_pf".equals(field) || "latest".equals(field) || "version".equals(field);
    }
    private static boolean knownTable(String table) {
        if("bd_customer".equals(table) || "bd_supplier".equals(table) || "bd_material".equals(table)) return true;
        for(NativeOrgBatchPage page:NativeOrgBatchPage.values()) if(page.table.equals(table)) return true;
        return false;
    }
    private static boolean validName(String value) { return value!=null && value.matches("[A-Za-z][A-Za-z0-9_]{0,63}"); }
    private static boolean present(String value) { return value!=null && !value.trim().isEmpty(); }
    private static String pack(String... values) {
        StringBuilder result=new StringBuilder();for(String value:values) result.append(value==null?-1:value.length()).append(':').append(value==null?"":value);
        return result.toString();
    }
}
