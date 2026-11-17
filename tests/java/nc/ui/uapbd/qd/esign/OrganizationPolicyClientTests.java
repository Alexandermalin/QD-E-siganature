package nc.ui.uapbd.qd.esign;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.JTable;
import javax.swing.table.TableRowSorter;
import nc.vo.uapbd.qd.esign.ButtonPolicyChange;
import nc.vo.uapbd.qd.esign.ButtonPolicyRow;

/** 无头检查明细修改、权限、版本和显示编码。 */
public final class OrganizationPolicyClientTests {
    public static void main(String[] args) { run(); }

    public static void run() {
        QdPolicyTableModel model = new QdPolicyTableModel();
        List<ButtonPolicyRow> rows = new ArrayList<ButtonPolicyRow>();
        rows.add(policy(false, false, false, 0L));
        for (int i = 1; i < 243; i++) {
            rows.add(new ButtonPolicyRow("row-" + i, null, "待映射", null, "按钮", "UNMAPPED",
                false, false, false, false, 0L));
        }
        model.load(rows);
        check(model.getRowCount() == 243, "Full catalog in table");
        check(!model.isCellEditable(0, QdPolicyTableModel.SIGN), "Query-only permission denies editing");
        model.setEditingAllowed(true);
        check(model.isCellEditable(0, QdPolicyTableModel.SIGN) && !model.isCellEditable(1, QdPolicyTableModel.SIGN),
            "Only verified rows are editable");
        model.setValueAt(Boolean.TRUE, 1, QdPolicyTableModel.AUDIT);
        check(!model.isDirty(), "Unmapped row remains read-only even for programmatic edits");
        model.setValueAt(Boolean.TRUE, 0, QdPolicyTableModel.REASON);
        model.setValueAt(Boolean.TRUE, 0, QdPolicyTableModel.AUDIT);
        ButtonPolicyChange edit = model.changes().get(0);
        check(model.changes().size() == 1 && edit.isReasonRequired() && edit.isAuditEnabled() && !edit.isSignatureEnabled(),
            "Independent options combine into one versioned row");
        model.acceptSaved(Collections.singletonList(policy(false, true, true, 1L)));
        check(!model.isDirty(), "Successful save establishes new baseline");
        model.setValueAt(Boolean.TRUE, 0, QdPolicyTableModel.SIGN);
        check(model.changes().get(0).getExpectedVersion() == 1L, "Subsequent edit uses returned row version");
        model.setValueAt(Boolean.FALSE, 0, QdPolicyTableModel.SIGN);
        check(!model.isDirty(), "Reverting to baseline removes pending edit");
        model.setValueAt(Boolean.TRUE, 0, QdPolicyTableModel.SIGN);
        model.load(rows);
        check(!model.isDirty() && Boolean.FALSE.equals(model.getValueAt(0, QdPolicyTableModel.SIGN)),
            "Reload clears the preceding organization's edits");

        testDisplayCodes(model);
        testNodeFilterPreservesChanges(model);
        System.out.println("PASS OrganizationPolicyClientTests (headless table and site display codes)");
    }

    private static void testNodeFilterPreservesChanges(QdPolicyTableModel model) {
        List<ButtonPolicyRow> rows=new ArrayList<ButtonPolicyRow>();
        rows.add(policy(false,false,false,0L));
        rows.add(new ButtonPolicyRow("customer", "10140CUO", "客户", "Save", "保存", "PILOT", true, false, false, false, 0L));
        model.load(rows); model.setEditingAllowed(true);
        model.setValueAt(Boolean.TRUE,0,QdPolicyTableModel.SIGN);
        model.setValueAt(Boolean.TRUE,1,QdPolicyTableModel.AUDIT);
        JTable table=new JTable(model); TableRowSorter<QdPolicyTableModel> sorter=new TableRowSorter<QdPolicyTableModel>(model); table.setRowSorter(sorter);
        sorter.setRowFilter(QdPolicyPanel.nodeNameFilter("物料"));
        check(table.getRowCount()==1 && table.convertRowIndexToModel(0)==0, "node name filters visible rows");
        check(model.dirtyCount()==2 && model.isRowDirty(1), "hidden changes remain pending");
        sorter.setRowFilter(QdPolicyPanel.nodeNameFilter("10140cuo"));
        check(table.getRowCount()==1 && table.convertRowIndexToModel(0)==1, "node code matches case insensitively");
        sorter.setRowFilter(QdPolicyPanel.nodeNameFilter("  "));
        check(table.getRowCount()==2 && model.changes().size()==2, "clearing filter restores all rows and saves all edits");
    }

    private static void testDisplayCodes(QdPolicyTableModel model) {
        List<ButtonPolicyRow> rows = new ArrayList<ButtonPolicyRow>();
        rows.add(policy(false, false, false, 0L));
        rows.add(unmapped("QD-ES-B9C29A5B958696F4"));
        rows.add(unmapped("QD-ES-4CE7157ECDB4A443"));
        rows.add(unmapped("unknown"));
        rows.add(new ButtonPolicyRow("QD-ES-B48245BDDB687314", null, "物料", null, "财务信息保存",
            "UNMAPPED", true, false, false, false, 0L));
        rows.add(unmapped("QD-ES-36702F5EC874AF77"));
        rows.add(unmapped("QD-ES-643C1BCD6537DD6B"));
        rows.add(unmapped("QD-ES-765869462A8F9898"));
        rows.add(unmapped("QD-ES-5F83691030EF585B"));
        model.load(rows);
        check("10140MAO".equals(model.getValueAt(0, 1)) && "Save".equals(model.getValueAt(0, 3)),
            "The verified pilot mapping takes precedence over both registered candidates");
        check("10140CCLG / 10140CCLO".equals(model.getValueAt(1, 1))
            && "Save / Save".equals(model.getValueAt(1, 3))
            && "10140CCLG / Save，10140CCLO / Save".equals(model.codeDetails(1))
            && "已登记，待接入".equals(model.getValueAt(1, 4))
            && !model.isCellEditable(1, QdPolicyTableModel.SIGN), "Registered codes are paired and read-only");
        check("待确认 / 待确认".equals(model.getValueAt(2, 3))
            && model.codeDetails(2).contains("子页入口 BatchUpdate")
            && "已定位子页入口".equals(model.getValueAt(2, 4)), "An entry button stays separate from a terminal action");
        check("待确认".equals(model.getValueAt(3, 1)) && "待确认".equals(model.getValueAt(3, 3)),
            "Unknown codes have an explicit pending label");
        check("部分入口试点可配置".equals(model.getValueAt(4, 4)),
            "a verified alias does not label every physical entry as integrated");
        check("10140CUG / 10140CUO".equals(model.getValueAt(5, 1))
            && "FiSave / FiSave".equals(model.getValueAt(5, 3))
            && "已登记，待接入".equals(model.getValueAt(5, 4))
            && !model.isCellEditable(5, QdPolicyTableModel.SIGN),
            "Customer page registration is displayed but remains read-only until integrated");
        check("FiDel / FiDel".equals(model.getValueAt(6, 3))
            && "已登记，待接入".equals(model.getValueAt(6, 4))
            && !model.isCellEditable(6, QdPolicyTableModel.AUDIT),
            "Supplier finance delete uses the supplier page code without enabling audit");
        check("PrSave / PrSave".equals(model.getValueAt(7, 3))
            && "已登记，待接入".equals(model.getValueAt(7, 4))
            && !model.isCellEditable(7, QdPolicyTableModel.SIGN),
            "Confirmed production page code is displayed without enabling the mapping");
        check("待确认".equals(model.getValueAt(8, 3))
            && "按钮待确认".equals(model.getValueAt(8, 4))
            && !model.isCellEditable(8, QdPolicyTableModel.SIGN),
            "Unresolved purchase-order close remains pending and read-only");
    }

    private static ButtonPolicyRow unmapped(String scope) {
        return new ButtonPolicyRow(scope, null, "待映射", null, "按钮", "UNMAPPED", false, false, false, false, 0L);
    }

    private static ButtonPolicyRow policy(boolean sign, boolean reason, boolean audit, long version) {
        return new ButtonPolicyRow("QD-ES-A7FBCA61C6991D48", "10140MAO", "物料", "Save", "保存", "PILOT",
            true, sign, reason, audit, version);
    }

    private static void check(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
    }
}
