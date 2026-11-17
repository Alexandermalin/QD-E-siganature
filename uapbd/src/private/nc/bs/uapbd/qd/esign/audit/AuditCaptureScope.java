package nc.bs.uapbd.qd.esign.audit;
import java.util.*;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;

/** 单线程同步服务调用内的审计采集作用域，按栈恢复嵌套上下文；不能作为跨 RPC 或跨线程的上下文传递机制。 */
public final class AuditCaptureScope implements AutoCloseable {
    /** CURRENT：当前线程的审计作用域，禁止用作 RPC 传输或跨线程共享。 */
    private static final ThreadLocal<AuditCaptureScope> CURRENT=new ThreadLocal<AuditCaptureScope>();
    /** previous：进入本作用域前的外层审计作用域，关闭时按栈恢复。 */
    private final AuditCaptureScope previous;
    /**
     * before：以客户端对象标识索引的执行前快照集合。
     * after：以客户端对象标识索引的持久化后快照集合。
     */
    private final Map<String,List<SnapshotRow>> before=new LinkedHashMap<String,List<SnapshotRow>>(),after=new LinkedHashMap<String,List<SnapshotRow>>();
    /** Shared reference facts are evidence, not rows owned or deleted by the business object. */
    private final Map<String,List<SnapshotRow>> beforeReferences=new LinkedHashMap<String,List<SnapshotRow>>();
    /** closed：作用域是否已按顺序关闭，避免重复或乱序恢复。 */
    private boolean closed;
    /** context：当前操作的业务上下文。 */
    private final OperationContext context;
    /** enabled：当前请求是否启用审计采集。 */
    private final boolean enabled;
    private final Set<String> enabledObjectIds;
    private final Set<String> selectedObjectIds;
    /** 保存前一层线程作用域并安装当前作用域；无参形式不限制业务上下文，带参形式用于校验授权对象及审计开关。 */
    public AuditCaptureScope(){this(null,true);}
    /** 保存前一层线程作用域并安装当前作用域；无参形式不限制业务上下文，带参形式用于校验授权对象及审计开关。 */
    public AuditCaptureScope(OperationContext context,boolean enabled){
        this.context=context;this.enabled=enabled;this.enabledObjectIds=null;this.selectedObjectIds=objectIds(context);
        previous=CURRENT.get();CURRENT.set(this);
    }
    /** 指定逐对象审计范围；未列入的档案绝不能被同批其他档案带入审计。 */
    public AuditCaptureScope(OperationContext context,Set<String> enabledObjectIds){
        if(context==null||enabledObjectIds==null)throw new IllegalArgumentException("Per-object audit scope is required");
        selectedObjectIds=objectIds(context);
        if(!selectedObjectIds.containsAll(enabledObjectIds))throw new IllegalArgumentException("Audit object is outside the request");
        this.context=context;this.enabledObjectIds=Collections.unmodifiableSet(new HashSet<String>(enabledObjectIds));
        this.enabled=!enabledObjectIds.isEmpty();previous=CURRENT.get();CURRENT.set(this);
    }
    private static Set<String> objectIds(OperationContext context) {
        Set<String> ids=new HashSet<String>();
        if(context!=null) for(BusinessObjectRef object:context.getObjects()) ids.add(object.getClientObjectId());
        return ids;
    }
    /** 获取当前线程作用域中的上下文，不创建跨线程或跨 RPC 的共享状态。 */
    public static AuditCaptureScope current(){return CURRENT.get();}
    /** 返回当前操作的业务上下文。 */
    public OperationContext getContext(){return context;}
    /** 返回当前请求是否启用审计采集。 */
    public boolean isEnabled(){return enabled;}
    public boolean isEnabledFor(String clientId){return enabled&&(enabledObjectIds==null||enabledObjectIds.contains(clientId));}
    /** 按对象类型及持久化主键寻找本请求的客户端对象标识；新增单对象可缺主键，歧义或超出签名范围时拒绝采集。 */
    public String objectId(String type,String pk){
        return objectId(type,pk,null);
    }
    /** 分配时以物料主键与目标组织共同定位同批次中的一个对象。 */
    public String objectId(String type,String pk,String targetOrg){
        if(context==null)throw new IllegalStateException("Business context is unavailable");String found=null;
        for(BusinessObjectRef object:context.getObjects())if(type.equals(object.getObjectType())&&
            (pk!=null&&pk.equals(object.getObjectPk())||object.getObjectPk()==null&&context.getObjects().size()==1)&&
            (targetOrg==null||targetOrg.equals(object.getTargetOrg()))){
            if(found!=null)throw new IllegalStateException("Ambiguous audit object");found=object.getClientObjectId();
        }
        if(found==null)throw new IllegalStateException("Audit object is outside the signed request");return found;
    }
    /** 保存指定授权对象的执行前权威快照，重复采集或无效快照会被拒绝。 */
    public void before(String clientId,List<SnapshotRow> rows){capture(before,clientId,rows);}
    /** Save referenced facts separately so dropping a reference cannot masquerade as a row deletion. */
    public void beforeReferences(String clientId,List<SnapshotRow> rows){capture(beforeReferences,clientId,rows);}
    /** 保存指定授权对象的已持久化执行后快照，供网关完整性检查与差异计算使用。 */
    public void after(String clientId,List<SnapshotRow> rows){capture(after,clientId,rows);}
    /** 检查作用域、对象授权和非空快照，复制列表后只读保存，防止后续修改或重复覆盖证据。 */
    private void capture(Map<String,List<SnapshotRow>> target,String id,List<SnapshotRow> rows){
        // 每个对象同一阶段只采集一次，关闭后禁止继续写入，避免后来事件覆盖权威快照。
        if(closed||id==null||target.containsKey(id))throw new IllegalStateException("Duplicate/closed audit capture");
        if(!isEnabledFor(id))throw new IllegalStateException("Audit is not enabled for this object");
        if(rows==null||rows.isEmpty()||rows.contains(null))throw new IllegalArgumentException("An authoritative non-empty snapshot is required");
        // 快照对象必须属于本次上下文授权范围，不能夹带其他对象证据。
        if(context!=null && !selectedObjectIds.contains(id)) throw new IllegalArgumentException("Snapshot object is outside this request");
        target.put(id,Collections.unmodifiableList(new ArrayList<SnapshotRow>(rows)));
    }
    /** 判断指定对象是否已经采集执行前快照。 */
    public boolean hasBefore(String id){return before.containsKey(id);}
    /** 判断指定对象是否已经采集执行后快照。 */
    public boolean hasAfter(String id){return after.containsKey(id);}
    /** 返回指定对象的执行前快照，未采集时返回空列表；调用方仍须单独检查是否满足必需快照条件。 */
    public List<SnapshotRow> getBefore(String id){return before.containsKey(id)?before.get(id):Collections.<SnapshotRow>emptyList();}
    public List<SnapshotRow> getBeforeReferences(String id){return beforeReferences.containsKey(id)?beforeReferences.get(id):Collections.<SnapshotRow>emptyList();}
    /** 返回指定对象的执行后快照，未采集时返回空列表；空结果不证明采集成功。 */
    public List<SnapshotRow> getAfter(String id){return after.containsKey(id)?after.get(id):Collections.<SnapshotRow>emptyList();}
    /** 按后进先出顺序关闭审计作用域并恢复前一层线程上下文；已关闭时直接返回，乱序关闭明确失败。 */
    public void close(){if(closed)return;if(CURRENT.get()!=this)throw new IllegalStateException("Audit scopes closed out of order");if(previous==null)CURRENT.remove();else CURRENT.set(previous);closed=true;}
}
