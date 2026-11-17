package nc.bs.uapbd.qd.esign.core;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import nc.bs.uapbd.qd.esign.audit.SnapshotSupport;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.pub.lang.UFBoolean;
import nc.vo.pub.lang.UFDate;
import nc.vo.pub.lang.UFDateTime;
import nc.vo.pub.lang.UFDouble;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;
import nc.vo.uap.BDVOUtil;

/** A candidate batch change is bound to its exact field names, value types and values before signing. */
public final class NativeBatchChangeSet {
    private final Map<String,Object> values;private final Map<String,String> columns;
    private final String[] selectedFields;private final String fingerprint;
    private NativeBatchChangeSet(Map<String,Object> values,Map<String,String> columns,String[] selectedFields,String fingerprint) {
        this.values=Collections.unmodifiableMap(values);this.columns=Collections.unmodifiableMap(columns);
        this.selectedFields=selectedFields.clone();this.fingerprint=fingerprint;
    }
    /** Only verified server-side NC metadata may be used by production batch preparation and execution. */
    public static NativeBatchChangeSet create(String[] selected,Map<String,Object> values,NativeBatchMetadataFields.Verified metadata)
            throws BusinessException {
        EsignValues.require(metadata!=null,"基础档案批改缺少已核实的原生元数据。");
        metadata.validate(selected,values);return createInternal(selected,values,metadata.names(),metadata.columns(),metadata.fingerprint(),true);
    }
    /** Package-local test seam; callers outside this package cannot pass a client-controlled field list. */
    static NativeBatchChangeSet create(String[] selected,Map<String,Object> values,Set<String> allowedFields)
            throws BusinessException {
        Map<String,String> columns=new LinkedHashMap<String,String>();
        if(values!=null) for(String name:values.keySet()) if(name!=null) columns.put(name,name.toLowerCase(java.util.Locale.ENGLISH));
        return createInternal(selected,values,allowedFields,columns,null,false);
    }
    private static NativeBatchChangeSet createInternal(String[] selected,Map<String,Object> values,Set<String> allowedFields,
            Map<String,String> columns,String metadataFingerprint,boolean relatedFieldsAllowed)
            throws BusinessException {
        EsignValues.require(selected!=null && selected.length>0 && values!=null && allowedFields!=null,
            "基础档案批改缺少字段或原生元数据。");
        Set<String> names=new HashSet<String>();
        for(String name:selected) EsignValues.require(name!=null && name.matches("[A-Za-z][A-Za-z0-9_]{0,63}")
            && !SnapshotSupport.sensitive(name) && allowedFields.contains(name) && names.add(name),"基础档案批改字段无效或未获授权。");
        EsignValues.require(values.keySet().containsAll(names) && (relatedFieldsAllowed || names.equals(values.keySet()))
            && allowedFields.containsAll(values.keySet()) && columns!=null && values.keySet().equals(columns.keySet()),
            "基础档案批改字段与提交值不一致。");
        Map<String,Object> copy=new LinkedHashMap<String,Object>();StringBuilder canonical=new StringBuilder(pack(metadataFingerprint));
        // NC validators receive the original selected array; related fields are only present in the value map.
        canonical.append(pack(Integer.toString(selected.length)));for(String name:selected) canonical.append(pack(name));
        Map<String,Object> ordered=new TreeMap<String,Object>(values);
        for(Map.Entry<String,Object> item:ordered.entrySet()) {
            Object value=item.getValue();EsignValues.require(supported(value),"基础档案批改值类型尚未核实。");value=copyValue(value);
            String normalized;
            try { normalized=exactValue(value); }
            catch(IllegalArgumentException failure) { throw new BusinessException("基础档案批改值无法规范化。",failure); }
            canonical.append(pack(item.getKey())).append(pack(value==null?null:value.getClass().getName())).append(pack(normalized));
            copy.put(item.getKey(),value);
        }
        return new NativeBatchChangeSet(copy,new LinkedHashMap<String,String>(columns),selected,SnapshotSupport.sha256(canonical.toString()));
    }
    public String getFingerprint() { return fingerprint; }
    public String[] getSelectedFields() { return selectedFields.clone(); }
    /** Native services receive a fresh copy so neither UI nor service mutation changes the signed values. */
    public Map<String,Object> copyValues() {
        Map<String,Object> result=new LinkedHashMap<String,Object>();
        for(Map.Entry<String,Object> item:values.entrySet()) result.put(item.getKey(),copyValue(item.getValue()));return result;
    }
    /** Use exactly NC's BDVOUtil comparison; these VOs must be read and locked by the server. */
    public Set<String> unchanged(List<? extends SuperVO> rows) throws BusinessException {
        EsignValues.require(rows!=null && !rows.isEmpty(),"基础档案批改缺少服务端当前数据。");
        Set<String> result=new HashSet<String>(),seen=new HashSet<String>();
        for(SuperVO row:rows) {
            String pk=row==null?null:row.getPrimaryKey();
            EsignValues.require(pk!=null && seen.add(pk),"基础档案批改服务端数据缺少唯一主键。");
            boolean changed=false;
            for(Map.Entry<String,Object> item:values.entrySet())
                if(BDVOUtil.isValueChanged(item.getValue(),row.getAttributeValue(item.getKey()))) { changed=true;break; }
            if(!changed) result.add(pk);
        }
        return Collections.unmodifiableSet(result);
    }
    /** A native success must change every signed physical field that differed in the locked VO. */
    public void requirePhysicalChanges(SuperVO prior,String table,String pk,List<SnapshotRow> before,List<SnapshotRow> after)
            throws BusinessException {
        EsignValues.require(prior!=null && pk!=null && pk.equals(prior.getPrimaryKey()) && table!=null,
            "批改物理字段核对缺少执行前对象。");
        SnapshotRow old=physicalRow(before,table,pk),current=physicalRow(after,table,pk);boolean changed=false;
        for(Map.Entry<String,Object> entry:values.entrySet()) {
            String column=columns.get(entry.getKey());
            EsignValues.require(column!=null && old.getValues().containsKey(column) && current.getValues().containsKey(column),
                "批改物理快照缺少已授权字段。");
            if(BDVOUtil.isValueChanged(entry.getValue(),prior.getAttributeValue(entry.getKey()))) {
                changed=true;String left=old.getValues().get(column),right=current.getValues().get(column);
                EsignValues.require(left==null?right!=null:!left.equals(right),"原生批改成功但已授权字段没有物理变化。");
            }
        }
        EsignValues.require(changed,"原生批改成功结果没有待修改的授权字段。");
    }
    private static SnapshotRow physicalRow(List<SnapshotRow> rows,String table,String pk) throws BusinessException {
        SnapshotRow found=null;
        EsignValues.require(rows!=null,"批改物理快照缺失。");
        for(SnapshotRow row:rows) if(row!=null && table.equals(row.getEntity()) && pk.equals(row.getRowId())) {
            EsignValues.require(found==null,"批改物理快照主行重复。");found=row;
        }
        EsignValues.require(found!=null,"批改物理快照主行缺失。");return found;
    }
    private static boolean supported(Object value) {
        if(value==null) return true;Class<?> type=value.getClass();
        return type==String.class || type==Boolean.class || type==Byte.class || type==Short.class || type==Integer.class
            || type==Long.class || type==Float.class || type==Double.class || type==BigDecimal.class || type==BigInteger.class
            || type==Date.class || type==java.sql.Date.class || type==java.sql.Timestamp.class || type==java.sql.Time.class
            || type==UFBoolean.class || type==UFDate.class || type==UFDateTime.class || type==UFDouble.class;
    }
    private static Object copyValue(Object value) {
        if(value instanceof Date) return ((Date)value).clone();
        if(value instanceof UFDate) return ((UFDate)value).clone();
        if(value instanceof UFDateTime) return ((UFDateTime)value).clone();
        if(value instanceof UFDouble) return ((UFDouble)value).clone();
        return value;
    }
    /** Authorization must retain milliseconds, nanoseconds and numeric representation, not audit display rounding. */
    private static String exactValue(Object value) {
        if(value==null) return null;
        if(value instanceof java.sql.Timestamp) {
            java.sql.Timestamp timestamp=(java.sql.Timestamp)value;return timestamp.getTime()+":"+timestamp.getNanos();
        }
        if(value instanceof Date) return Long.toString(((Date)value).getTime());
        if(value instanceof UFDateTime) return Long.toString(((UFDateTime)value).getMillis());
        if(value instanceof UFDate) return Long.toString(((UFDate)value).getMillis());
        if(value instanceof Float) {
            float number=((Float)value).floatValue();
            if(Float.isNaN(number) || Float.isInfinite(number)) throw new IllegalArgumentException("Non-finite value");
            return Integer.toHexString(Float.floatToIntBits(number));
        }
        if(value instanceof Double) {
            double number=((Double)value).doubleValue();
            if(Double.isNaN(number) || Double.isInfinite(number)) throw new IllegalArgumentException("Non-finite value");
            return Long.toHexString(Double.doubleToLongBits(number));
        }
        if(value instanceof UFDouble) {
            UFDouble number=(UFDouble)value;return number.toBigDecimal().toString()+":"+number.getPower();
        }
        return value.toString();
    }
    private static String pack(String value) { return (value==null?"-1":Integer.toString(value.length()))+":"+(value==null?"":value); }
}
