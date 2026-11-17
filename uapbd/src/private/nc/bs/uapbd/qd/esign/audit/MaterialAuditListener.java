package nc.bs.uapbd.qd.esign.audit;
import java.util.*;
import nc.bs.businessevent.IBusinessEvent;
import nc.bs.businessevent.IBusinessListener;
import nc.bs.businessevent.bd.BDCommonEvent;
import nc.vo.bd.material.MaterialVO;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;

/** 物料主档同步审计监听器候选实现，仅在当前线程存在审计作用域时采集；不会自动完成原生事件注册或启用节点。 */
public final class MaterialAuditListener implements IBusinessListener {
    /** FIELDS：明确允许采集的物料字段与显示名称映射。 */
    private static final Map<String,String> FIELDS;
    static{Map<String,String> f=new LinkedHashMap<String,String>();for(String key:new String[]{"pk_material","code","name","pk_org","pk_group","pk_marbasclass","materialspec","materialtype","pk_measdoc","enablestate"})f.put(key,key);FIELDS=Collections.unmodifiableMap(f);}
    /** 仅响应白名单中的物料前后同步事件，在当前审计作用域内采集指定对象；无法取得权威行时失败并要求调用方回滚。 */
    public void doAction(IBusinessEvent event) throws BusinessException {
        AuditCaptureScope scope=AuditCaptureScope.current();if(scope==null||!scope.isEnabled()||scope.getContext()==null)return;
        boolean relevant=false;for(BusinessObjectRef ref:scope.getContext().getObjects())if("BD_MATERIAL".equals(ref.getObjectType()))relevant=true;if(!relevant)return;
        if(!(event instanceof BDCommonEvent))throw new BusinessException("Material audit requires a synchronous BDCommonEvent");
        BDCommonEvent e=(BDCommonEvent)event;String phase=e.getEventType();
        if("1005".equals(phase))throw new BusinessException("Complete material deletion relations have not been registered; deletion audit is blocked");
        if(!"1003".equals(phase)&&!"1002".equals(phase)&&!"1004".equals(phase))return;
        Object[] values="1003".equals(phase)?e.getOldObjs():e.getNewObjs();
        if(values==null||values.length==0)throw new BusinessException("Material event has no authoritative snapshot objects");
        for(Object value:values){
            if(!(value instanceof MaterialVO))throw new BusinessException("Unexpected material event object");MaterialVO material=(MaterialVO)value;
            String client=scope.objectId("BD_MATERIAL",material.getPrimaryKey());
            SnapshotRow row=snapshot(material);
            if("1003".equals(phase))scope.before(client,Arrays.asList(row));else scope.after(client,Arrays.asList(row));
        }
    }

    /** 依据统一白名单采集已经定位的物料主档。 */
    public static SnapshotRow snapshot(MaterialVO material) throws BusinessException {
        Set<String> attributes = new HashSet<String>(Arrays.asList(material.getAttributeNames()));
        if (!attributes.containsAll(FIELDS.keySet())) {
            throw new BusinessException("Material audit field schema differs from this NC version");
        }
        return SnapshotSupport.collect(material, "bd_material", material.getPrimaryKey(), null, FIELDS);
    }
}
