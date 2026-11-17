package nc.bs.uapbd.qd.esign.core;
import java.util.*;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
/** 将持久化对象关系还原为业务对象引用，使用已保存的父对象 ID 映射，避免按列表顺序推测父子关系。 */
public final class ObjectRelations {
    /** 工具类不提供实例，使用静态方法完成本类职责。 */
    private ObjectRelations(){}
    /** 使用持久化对象 ID 到客户端对象 ID 的映射还原父子关联，不依赖数据库返回顺序。 */
    public static List<BusinessObjectRef> restore(List<Map<String,Object>> rows){
        Map<String,String> keys=new HashMap<String,String>();
        for(Map<String,Object> row:rows){String id=EsignValues.text(row,"object_id");if(id==null||keys.put(id,EsignValues.text(row,"client_object_id"))!=null)throw new IllegalStateException("Invalid persisted object identities");}
        List<BusinessObjectRef> refs=new ArrayList<BusinessObjectRef>();
        for(Map<String,Object> row:rows){String parent=EsignValues.text(row,"parent_object_id");if(parent!=null&&!keys.containsKey(parent))throw new IllegalStateException("Missing persisted parent object");refs.add(new BusinessObjectRef(EsignValues.text(row,"client_object_id"),EsignValues.text(row,"object_type"),EsignValues.text(row,"object_pk"),EsignValues.text(row,"bill_code"),keys.get(parent),EsignValues.text(row,"desired_owner_org"),EsignValues.text(row,"policy_owner_org"),EsignValues.text(row,"target_org")));}return refs;
    }
}
