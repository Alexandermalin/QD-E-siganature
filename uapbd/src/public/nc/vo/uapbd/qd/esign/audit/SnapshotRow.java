package nc.vo.uapbd.qd.esign.audit;
import java.io.Serializable;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
/** 一行审计快照，包含实体、稳定行标识、父子关系和允许采集的字段值、类型及显示名称；映射复制后以只读形式暴露。 */
public final class SnapshotRow implements Serializable {
    /** Marks a shared inventory batch record captured as context rather than a bill-owned row. */
    public static final String INVENTORY_BATCH_REFERENCE="reference:scm_batchcode";
    /** serialVersionUID：Java 序列化版本号，保持跨 RPC 或 Swing 组件序列化的兼容标识。 */
    private static final long serialVersionUID=1L;
    /**
     * entity：快照所属实体标识。
     * rowId：实体内用于前后对齐的稳定行标识。
     * parentRowId：快照行对应的父行标识。
     */
    private final String entity,rowId,parentRowId;
    /**
     * values：字段规范化后的文本值，保留 null 语义。
     * types：字段原始数据类型名称。
     * labels：字段业务显示名称。
     */
    private final Map<String,String> values,types,labels;
    private final boolean physicalColumns;
    /** 检查快照标识及三组字段映射完整，复制到排序只读映射，使字段输出及差异比较顺序稳定。 */
    public SnapshotRow(String entity,String rowId,String parentRowId,Map<String,String> values,Map<String,String> types,Map<String,String> labels){
        this(entity,rowId,parentRowId,values,types,labels,false);
    }
    public SnapshotRow(String entity,String rowId,String parentRowId,Map<String,String> values,Map<String,String> types,Map<String,String> labels,boolean physicalColumns){
        if(entity==null||rowId==null||values==null||types==null||labels==null)throw new IllegalArgumentException("Incomplete snapshot row");
        this.entity=entity;this.rowId=rowId;this.parentRowId=parentRowId;
        this.values=Collections.unmodifiableMap(new TreeMap<String,String>(values));
        this.types=Collections.unmodifiableMap(new TreeMap<String,String>(types));
        this.labels=Collections.unmodifiableMap(new TreeMap<String,String>(labels));
        this.physicalColumns=physicalColumns;
    }
    /** 返回快照所属实体标识。 */
    public String getEntity(){return entity;}
    /** 返回实体内用于前后对齐的稳定行标识。 */
    public String getRowId(){return rowId;}
    /** 返回快照行对应的父行标识。 */
    public String getParentRowId(){return parentRowId;}
    /** 返回字段规范化后的文本值，保留 null 语义。 */
    public Map<String,String> getValues(){return values;}
    /** 返回字段原始数据类型名称。 */
    public Map<String,String> getTypes(){return types;}
    /** 返回字段业务显示名称。 */
    public Map<String,String> getLabels(){return labels;}
    /** 物理列快照与旧版 VO 字段快照采用可区分的载荷格式。 */
    public boolean hasPhysicalColumns(){return physicalColumns;}
}
