package nc.bs.uapbd.qd.esign.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import nc.vo.pub.BusinessException;

/** Reconstructs independent selected-root delete units and their policy/audit-only related archives. */
public final class PartnerMainDeleteUnits {
    private PartnerMainDeleteUnits() { }

    public static final class Unit {
        private final Map<String,Object> root;private final List<Map<String,Object>> objects;
        private Unit(Map<String,Object> root,List<Map<String,Object>> objects) {
            this.root=root;this.objects=Collections.unmodifiableList(new ArrayList<Map<String,Object>>(objects));
        }
        public Map<String,Object> getRoot() { return root; }
        public List<Map<String,Object>> getObjects() { return objects; }
    }

    public static List<Unit> group(List<Map<String,Object>> objects,String selectedDomain) throws BusinessException {
        EsignValues.require(objects!=null && !objects.isEmpty()
            && ("BD_CUSTOMER".equals(selectedDomain) || "BD_SUPPLIER".equals(selectedDomain)),"主档删除单元缺少授权对象。");
        Map<String,Map<String,Object>> byId=new LinkedHashMap<String,Map<String,Object>>();Set<String> clients=new HashSet<String>();
        for(Map<String,Object> object:objects) {
            EsignValues.require(object!=null,"主档删除对象缺失。");
            String id=EsignValues.text(object,"object_id"),client=EsignValues.text(object,"client_object_id"),
                type=EsignValues.text(object,"object_type"),pk=EsignValues.text(object,"object_pk");
            EsignValues.require(present(id) && present(client) && present(pk)
                && ("BD_CUSTOMER".equals(type) || "BD_SUPPLIER".equals(type))
                && EsignValues.text(object,"desired_owner_org")==null && byId.put(id,object)==null && clients.add(client),
                "主档删除授权对象无效或重复。");
        }
        Map<String,List<Map<String,Object>>> members=new LinkedHashMap<String,List<Map<String,Object>>>();
        Set<String> selected=new HashSet<String>();
        for(Map.Entry<String,Map<String,Object>> entry:byId.entrySet()) {
            Map<String,Object> object=entry.getValue();
            if(EsignValues.text(object,"parent_object_id")==null) {
                EsignValues.require(selectedDomain.equals(EsignValues.text(object,"object_type"))
                    && selected.add(EsignValues.text(object,"object_pk")),"主档删除选中档案类型错误或重复。");
                List<Map<String,Object>> unit=new ArrayList<Map<String,Object>>();unit.add(object);members.put(entry.getKey(),unit);
            }
        }
        EsignValues.require(!members.isEmpty(),"主档删除缺少选中档案。");
        for(Map<String,Object> object:objects) {
            String parent=EsignValues.text(object,"parent_object_id");if(parent==null) continue;
            List<Map<String,Object>> unit=members.get(parent);
            EsignValues.require(unit!=null,"主档删除关联档案缺少选中主档或包含多层关系。");
            String identity=EsignValues.text(object,"object_type")+"\u0000"+EsignValues.text(object,"object_pk");
            for(Map<String,Object> member:unit) EsignValues.require(!identity.equals(EsignValues.text(member,"object_type")
                +"\u0000"+EsignValues.text(member,"object_pk")),"同一主档删除单元包含重复档案。");
            unit.add(object);
        }
        List<Unit> result=new ArrayList<Unit>();
        for(List<Map<String,Object>> rows:members.values()) result.add(new Unit(rows.get(0),rows));
        return Collections.unmodifiableList(result);
    }
    private static boolean present(String value) { return value!=null && !value.trim().isEmpty(); }
}
