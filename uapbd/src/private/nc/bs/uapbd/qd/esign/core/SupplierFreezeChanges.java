package nc.bs.uapbd.qd.esign.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;

/** Verifies actual physical rows and flags before recognizing a target as changed. */
public final class SupplierFreezeChanges {
    private SupplierFreezeChanges() { }

    public static boolean changed(List<SnapshotRow> before,List<SnapshotRow> after,
            String supplier,String group,String target,boolean freeze) throws BusinessException {
        Map<String,SnapshotRow> prior=index(before,supplier,group,target);
        Map<String,SnapshotRow> current=index(after,supplier,group,target);
        EsignValues.require(prior.keySet().equals(current.keySet()),
            "供应商冻结涉及的数据行发生变化，业务必须回滚。");
        boolean changed=false;
        for(Map.Entry<String,SnapshotRow> entry:current.entrySet()) {
            SnapshotRow row=entry.getValue();Map<String,String> values=row.getValues();
            if("bd_supfinance".equals(row.getEntity())) {
                requireFlag(values,"payfreezeflag",freeze);
                requireFlag(values,"makeoutfreezeflag",freeze);
            } else requireFlag(values,"orderfreezeflag",freeze);
            if(!values.equals(prior.get(entry.getKey()).getValues())) changed=true;
        }
        return changed;
    }
    private static Map<String,SnapshotRow> index(List<SnapshotRow> rows,String supplier,String group,String target)
            throws BusinessException {
        EsignValues.require(rows!=null && supplier!=null && group!=null && target!=null,
            "供应商冻结缺少数据范围。");
        Map<String,SnapshotRow> result=new LinkedHashMap<String,SnapshotRow>();
        for(SnapshotRow row:rows) {
            EsignValues.require(row!=null && row.hasPhysicalColumns()
                && ("bd_supfinance".equals(row.getEntity()) || "bd_supstock".equals(row.getEntity()))
                && supplier.equals(row.getParentRowId()) && supplier.equals(row.getValues().get("pk_supplier"))
                && group.equals(row.getValues().get("pk_group")) && target.equals(row.getValues().get("pk_org"))
                && row.getRowId()!=null && row.getRowId().equals(row.getValues().get(
                    "bd_supfinance".equals(row.getEntity())?"pk_supfinance":"pk_supstock"))
                && result.put(row.getEntity()+"\u0000"+row.getRowId(),row)==null,
                "供应商冻结包含重复、跨集团或跨目标组织的数据行。");
        }
        return result;
    }
    private static void requireFlag(Map<String,String> values,String field,boolean expected) throws BusinessException {
        EsignValues.require(values.containsKey(field),"供应商冻结状态字段缺失，业务必须回滚。");
        String value=values.get(field);
        boolean yes="Y".equalsIgnoreCase(value) || "1".equals(value) || "TRUE".equalsIgnoreCase(value);
        boolean no=value==null || "N".equalsIgnoreCase(value) || "0".equals(value) || "FALSE".equalsIgnoreCase(value);
        EsignValues.require((yes || no) && expected==yes,"无法核实供应商冻结结果，业务必须回滚。");
    }
}
