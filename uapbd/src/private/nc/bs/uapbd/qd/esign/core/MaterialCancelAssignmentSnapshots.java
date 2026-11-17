package nc.bs.uapbd.qd.esign.core;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;

/** Physical evidence for one material and one cancellation target. */
public final class MaterialCancelAssignmentSnapshots {
    private MaterialCancelAssignmentSnapshots() { }

    public static String reconcile(String group,String material,String target,Set<String> expectedTables,
            List<SnapshotRow> before,List<SnapshotRow> after) throws BusinessException {
        Map<String,String> keys=keys();
        EsignValues.require(group!=null && material!=null && target!=null && expectedTables!=null
            && expectedTables.contains("bd_marorg") && keys.keySet().containsAll(expectedTables),
            "物料取消分配缺少集团、目标组织或已核实的物理表。");
        Map<String,SnapshotRow> old=rows(before,keys,expectedTables,group,material,target);
        Map<String,SnapshotRow> current=rows(after,keys,expectedTables,group,material,target);
        SnapshotRow oldRoot=old.get("bd_material"),newRoot=current.get("bd_material");
        EsignValues.require(oldRoot!=null && newRoot!=null && oldRoot.getRowId().equals(newRoot.getRowId())
            && oldRoot.getValues().equals(newRoot.getValues()) && oldRoot.getTypes().equals(newRoot.getTypes()),
            "物料主档在取消分配期间发生额外变化，业务必须回滚。");
        for(String table:expectedTables) EsignValues.require(!current.containsKey(table),
            "原生取消分配后仍存在目标组织关系或页签，业务必须回滚。");
        return old.size()==1?"ALREADY_UNASSIGNED":"SUCCESS";
    }

    private static Map<String,SnapshotRow> rows(List<SnapshotRow> input,Map<String,String> keys,Set<String> expected,
            String group,String material,String target) throws BusinessException {
        EsignValues.require(input!=null,"物料取消分配物理快照缺失。");
        Map<String,SnapshotRow> result=new HashMap<String,SnapshotRow>();
        for(SnapshotRow row:input) {
            EsignValues.require(row!=null && row.hasPhysicalColumns() && ("bd_material".equals(row.getEntity())
                || expected.contains(row.getEntity())) && result.put(row.getEntity(),row)==null,
                "物料取消分配快照包含未知表或重复行。");
            String table=row.getEntity(),key="bd_material".equals(table)?"pk_material":keys.get(table);
            Map<String,String> fields=row.getValues();
            EsignValues.require(key!=null && row.getRowId()!=null && row.getRowId().equals(fields.get(key))
                && material.equals(fields.get("pk_material")) && group.equals(fields.get("pk_group")),
                "物料取消分配快照所属档案或集团不一致。");
            if(!"bd_material".equals(table)) EsignValues.require(target.equals(fields.get("pk_org")),
                "物料取消分配快照目标组织不一致。");
        }
        return result;
    }

    public static Map<String,String> keys() {
        Map<String,String> result=new HashMap<String,String>();
        result.put("bd_marorg","pk_marorg");result.put("bd_materialfi","pk_materialfi");
        result.put("bd_materialpfc","pk_materialpfc");result.put("bd_materialpu","pk_materialpu");
        result.put("bd_materialstock","pk_materialstock");result.put("bd_materialsale","pk_materialsale");
        result.put("bd_materialplan","pk_materialplan");result.put("bd_materialprod","pk_materialprod");
        return result;
    }
}
