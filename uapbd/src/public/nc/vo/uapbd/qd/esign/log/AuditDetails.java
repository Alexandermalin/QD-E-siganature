package nc.vo.uapbd.qd.esign.log;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 审计主记录及分页差异、快照的只读结果；大字段仅提供有上限的预览，原长度和截断标记随结果显式返回。 */
public final class AuditDetails implements Serializable {
    /** serialVersionUID：Java 序列化版本号，保持跨 RPC 或 Swing 组件序列化的兼容标识。 */
    private static final long serialVersionUID=1L;
    /** audit：已经过组织授权的审计主记录。 */
    private final Map<String,Object> audit;
    /**
     * changes：字段差异集合或对应表模型。
     * snapshots：快照集合或对应表模型。
     */
    private final List<Map<String,Object>> changes,snapshots;
    /**
     * changeTotal：字段差异记录总数。
     * snapshotTotal：快照记录总数。
     */
    private final long changeTotal,snapshotTotal;
    /** 复制审计主记录、差异及快照容器并只读暴露，同时保存两类明细的总数。 */
    public AuditDetails(Map<String,Object> audit,List<Map<String,Object>> changes,List<Map<String,Object>> snapshots,long changeTotal,long snapshotTotal) {
        this.audit=Collections.unmodifiableMap(new LinkedHashMap<String,Object>(audit));
        this.changes=copy(changes);this.snapshots=copy(snapshots);this.changeTotal=changeTotal;this.snapshotTotal=snapshotTotal;
    }
    /** 复制每行映射和外层列表并包装为只读集合，隔离调用方的容器修改。 */
    private static List<Map<String,Object>> copy(List<Map<String,Object>> rows) {
        List<Map<String,Object>> copy=new ArrayList<Map<String,Object>>();
        for(Map<String,Object> row:rows){copy.add(Collections.unmodifiableMap(new LinkedHashMap<String,Object>(row)));}
        return Collections.unmodifiableList(copy);
    }
    /** 返回已经过组织授权的审计主记录。 */
    public Map<String,Object> getAudit(){return audit;}
    /** 返回字段差异集合或对应表模型。 */
    public List<Map<String,Object>> getChanges(){return changes;}
    /** 返回快照集合或对应表模型。 */
    public List<Map<String,Object>> getSnapshots(){return snapshots;}
    /** 返回字段差异记录总数。 */
    public long getChangeTotal(){return changeTotal;}
    /** 返回快照记录总数。 */
    public long getSnapshotTotal(){return snapshotTotal;}
}
