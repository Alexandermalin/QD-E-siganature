package nc.bs.uapbd.qd.esign.core;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;

/** Candidate per-target physical result check. It does not replace NC's validators, events or transaction. */
public final class PartnerAssignmentSnapshots {
    private PartnerAssignmentSnapshots() { }

    /**
     * expectedTables is the server-verified set of flat native routes, including the multi-org relation.
     * Unexpected tables, duplicate rows, changes to pre-existing rows on assignment and ambiguous outcomes fail closed.
     */
    public static String reconcile(String domain,String operation,String group,String archive,String target,
            Set<String> expectedTables,List<SnapshotRow> before,List<SnapshotRow> after) throws BusinessException {
        boolean customer="BD_CUSTOMER".equals(domain);
        EsignValues.require((customer || "BD_SUPPLIER".equals(domain)) && ("ASSIGN".equals(operation)
            || "CANCEL_ASSIGN".equals(operation)) && group!=null && archive!=null && target!=null,
            "往来档案分配结果缺少集团、档案或目标组织。");
        Map<String,String> allowed=allowed(customer);String relation=customer?"bd_custorg":"bd_suporg";
        EsignValues.require(expectedTables!=null && expectedTables.contains(relation) && allowed.keySet().containsAll(expectedTables),
            "往来档案分配页签包含未核实的物理表。");
        Map<String,SnapshotRow> old=rows(before,allowed,expectedTables,customer,group,archive,target);
        Map<String,SnapshotRow> now=rows(after,allowed,expectedTables,customer,group,archive,target);
        String root=customer?"bd_customer":"bd_supplier";
        EsignValues.require(old.containsKey(root) && now.containsKey(root) && same(old.get(root),now.get(root)),
            "往来档案主档在分配期间发生额外变化，业务必须回滚。");
        if("ASSIGN".equals(operation)) {
            for(String table:expectedTables) {
                EsignValues.require(now.containsKey(table),"原生分配未写入预期的组织关系或页签，业务必须回滚。");
                if(old.containsKey(table)) EsignValues.require(same(old.get(table),now.get(table)),
                    "原生分配修改了已存在的组织关系或页签，业务必须回滚。");
            }
            return old.size()==now.size()?"ALREADY_ASSIGNED":"SUCCESS";
        }
        for(String table:expectedTables) EsignValues.require(!now.containsKey(table),
            "原生取消分配仍存在组织关系或页签，业务必须回滚。");
        return old.size()==1?"ALREADY_UNASSIGNED":"SUCCESS";
    }

    private static Map<String,SnapshotRow> rows(List<SnapshotRow> values,Map<String,String> allowed,Set<String> expected,
            boolean customer,String group,String archive,String target) throws BusinessException {
        EsignValues.require(values!=null,"往来档案分配快照缺失。");Map<String,SnapshotRow> result=new HashMap<String,SnapshotRow>();
        String root=customer?"bd_customer":"bd_supplier",rootKey=customer?"pk_customer":"pk_supplier";
        for(SnapshotRow row:values) {
            EsignValues.require(row!=null && row.hasPhysicalColumns() && (root.equals(row.getEntity()) || expected.contains(row.getEntity()))
                && result.put(row.getEntity(),row)==null,"往来档案分配快照包含未知表或重复行。");
            Map<String,String> fields=row.getValues();String table=row.getEntity(),key=root.equals(table)?rootKey:allowed.get(table);
            EsignValues.require(key!=null && row.getRowId()!=null && row.getRowId().equals(fields.get(key))
                && archive.equals(fields.get(rootKey)) && group.equals(fields.get("pk_group")),"往来档案分配快照所属对象不一致。");
            if(!root.equals(table)) EsignValues.require(target.equals(fields.get("pk_org")),"往来档案分配快照目标组织不一致。");
        }
        return result;
    }
    private static Map<String,String> allowed(boolean customer) {
        Map<String,String> result=new HashMap<String,String>();
        if(customer) {
            result.put("bd_custorg","pk_custorg");result.put("bd_custfinance","pk_custfinance");
            result.put("bd_custsale","pk_custsale");result.put("bd_custcreditctl","pk_custcreditctl");
        } else {
            result.put("bd_suporg","pk_suporg");result.put("bd_supfinance","pk_supfinance");result.put("bd_supstock","pk_supstock");
        }
        return result;
    }
    private static boolean same(SnapshotRow left,SnapshotRow right) {
        return left.getEntity().equals(right.getEntity()) && left.getRowId().equals(right.getRowId())
            && same(left.getParentRowId(),right.getParentRowId()) && left.getValues().equals(right.getValues())
            && left.getTypes().equals(right.getTypes());
    }
    private static boolean same(Object left,Object right) { return left==null?right==null:left.equals(right); }
}
