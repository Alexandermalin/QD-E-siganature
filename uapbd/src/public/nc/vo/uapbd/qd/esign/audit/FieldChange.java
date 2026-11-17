package nc.vo.uapbd.qd.esign.audit;
import java.io.Serializable;
/** 一个字段或父子关系的审计差异，关联对应快照行，并保存变更类型、前值和后值；空值与空字符串保持区别。 */
public final class FieldChange implements Serializable {
    /** serialVersionUID：Java 序列化版本号，保持跨 RPC 或 Swing 组件序列化的兼容标识。 */
    private static final long serialVersionUID=1L;
    /** row：差异所属的实体快照行。 */
    private final SnapshotRow row;
    /**
     * fieldCode：发生变更的字段编码。
     * changeType：INSERT、UPDATE 或 DELETE 变更类型。
     * beforeValue：变更前值。
     * afterValue：变更后值。
     */
    private final String fieldCode,changeType,beforeValue,afterValue;
    /** 保存差异对应的快照行、字段编码、变更类型和前后值。 */
    public FieldChange(SnapshotRow row,String field,String type,String before,String after){this.row=row;fieldCode=field;changeType=type;beforeValue=before;afterValue=after;}
    /** 返回差异所属的实体快照行。 */
    public SnapshotRow getRow(){return row;}
    /** 返回发生变更的字段编码。 */
    public String getFieldCode(){return fieldCode;}
    /** 返回INSERT、UPDATE 或 DELETE 变更类型。 */
    public String getChangeType(){return changeType;}
    /** 返回变更前值。 */
    public String getBeforeValue(){return beforeValue;}
    /** 返回变更后值。 */
    public String getAfterValue(){return afterValue;}
}
