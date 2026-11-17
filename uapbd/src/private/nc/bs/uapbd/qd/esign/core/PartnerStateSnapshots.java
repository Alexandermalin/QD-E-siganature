package nc.bs.uapbd.qd.esign.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;

/** Verifies state changes using physical rows, including the shared partner and async log. */
public final class PartnerStateSnapshots {
    private PartnerStateSnapshots() { }

    public static boolean sameRows(List<SnapshotRow> before,List<SnapshotRow> after) throws BusinessException {
        Map<String,SnapshotRow> left=index(before),right=index(after);
        if (!left.keySet().equals(right.keySet())) return false;
        for (String key:left.keySet()) {
            SnapshotRow a=left.get(key),b=right.get(key);
            if (!equal(a.getParentRowId(),b.getParentRowId()) || !a.getValues().equals(b.getValues())) return false;
        }
        return true;
    }

    public static boolean alreadyTarget(List<SnapshotRow> rows,String table,String pk,boolean enable) throws BusinessException {
        SnapshotRow root=required(index(rows),table,pk);
        String state=root.getValues().get("enablestate");
        EsignValues.require("1".equals(state) || "2".equals(state) || "3".equals(state),"档案启停状态无法识别。");
        return (enable?"2":"3").equals(state);
    }

    public static void verifySuccess(List<SnapshotRow> before,List<SnapshotRow> after,String table,String pk,
            String sharedState,boolean enable) throws BusinessException {
        Map<String,SnapshotRow> left=index(before),right=index(after);
        SnapshotRow root=required(right,table,pk);
        required(left,table,pk);
        EsignValues.require((enable?"2":"3").equals(root.getValues().get("enablestate")),"原生启停后的档案状态不正确。");
        String sharedKey="bd_cust_supplier\u0000"+pk;
        EsignValues.require(left.containsKey(sharedKey)==right.containsKey(sharedKey),"客户供应商关联行范围发生变化。");
        if (right.containsKey(sharedKey)) EsignValues.require((enable?"2":"3").equals(right.get(sharedKey).getValues().get(sharedState)),
            "客户供应商关联状态未正确同步。");
    }

    private static Map<String,SnapshotRow> index(List<SnapshotRow> rows) throws BusinessException {
        EsignValues.require(rows!=null && !rows.isEmpty(),"档案快照为空。");
        Map<String,SnapshotRow> result=new LinkedHashMap<String,SnapshotRow>();
        for (SnapshotRow row:rows) EsignValues.require(row!=null && result.put(row.getEntity()+"\u0000"+row.getRowId(),row)==null,
            "档案快照包含重复的物理行。");
        return result;
    }

    private static SnapshotRow required(Map<String,SnapshotRow> rows,String table,String pk) throws BusinessException {
        SnapshotRow row=rows.get(table+"\u0000"+pk);
        EsignValues.require(row!=null,"档案主行缺失。");
        return row;
    }

    private static boolean equal(Object a,Object b) { return a==null?b==null:a.equals(b); }
}
