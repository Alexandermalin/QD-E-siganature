package nc.ui.uapbd.qd.esign;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.table.AbstractTableModel;
import nc.vo.uapbd.qd.esign.ButtonPolicyChange;
import nc.vo.uapbd.qd.esign.ButtonPolicyRow;

/** 保存查询基线与当前组织的未提交修改，按清单标识关联明细。 */
final class QdPolicyTableModel extends AbstractTableModel {
    private static final long serialVersionUID = 1L;
    static final int SIGN = 5;
    static final int REASON = 6;
    static final int AUDIT = 7;
    private static final String[] COLUMNS = {"功能节点", "节点编码", "按钮", "按钮编码", "接入状态",
        "启用电子签名", "操作事由必填", "启用业务审计"};

    private final List<ButtonPolicyRow> rows = new ArrayList<ButtonPolicyRow>();
    private final Map<String,ButtonPolicyChange> pending = new LinkedHashMap<String,ButtonPolicyChange>();
    private boolean editingAllowed;

    void load(List<ButtonPolicyRow> result) {
        rows.clear();
        rows.addAll(result);
        pending.clear();
        fireTableDataChanged();
    }

    void setEditingAllowed(boolean allowed) { editingAllowed = allowed; }
    boolean isDirty() { return !pending.isEmpty(); }
    int dirtyCount() { return pending.size(); }
    boolean isRowDirty(int index) { return pending.containsKey(rows.get(index).getScopeId()); }
    List<ButtonPolicyChange> changes() { return new ArrayList<ButtonPolicyChange>(pending.values()); }

    boolean hasConfigurableRows() {
        for (ButtonPolicyRow row : rows) {
            if (row.isConfigurable()) { return true; }
        }
        return false;
    }

    String codeDetails(int index) {
        QdPolicyCodeCatalog.Codes codes = siteCodes(rows.get(index));
        return codes == null ? null : codes.pairs();
    }

    void acceptSaved(List<ButtonPolicyRow> result) {
        if (result == null || result.size() != pending.size()) {
            throw new IllegalStateException("保存返回的按钮策略数量不匹配。");
        }
        Map<String,ButtonPolicyRow> saved = new LinkedHashMap<String,ButtonPolicyRow>();
        for (ButtonPolicyRow row : result) {
            ButtonPolicyChange change = row == null ? null : pending.get(row.getScopeId());
            if (change == null || row.getVersion() <= change.getExpectedVersion()
                    || saved.put(row.getScopeId(), row) != null) {
                throw new IllegalStateException("保存返回的按钮策略与本次提交不匹配。");
            }
        }
        for (int index = 0; index < rows.size(); index++) {
            ButtonPolicyRow row = saved.get(rows.get(index).getScopeId());
            if (row != null) { rows.set(index, row); }
        }
        pending.clear();
        fireTableDataChanged();
    }

    @Override public int getRowCount() { return rows.size(); }
    @Override public int getColumnCount() { return COLUMNS.length; }
    @Override public String getColumnName(int column) { return COLUMNS[column]; }
    @Override public Class<?> getColumnClass(int column) { return column >= SIGN ? Boolean.class : String.class; }

    @Override public Object getValueAt(int index, int column) {
        ButtonPolicyRow row = rows.get(index);
        ButtonPolicyChange change = pending.get(row.getScopeId());
        switch (column) {
            case 0: return row.getNodeName();
            case 1: return displayCode(row, true);
            case 2: return row.getButtonName();
            case 3: return displayCode(row, false);
            case 4: return mappingLabel(row);
            case SIGN: return Boolean.valueOf(change == null ? row.isSignatureEnabled() : change.isSignatureEnabled());
            case REASON: return Boolean.valueOf(change == null ? row.isReasonRequired() : change.isReasonRequired());
            case AUDIT: return Boolean.valueOf(change == null ? row.isAuditEnabled() : change.isAuditEnabled());
            default: throw new IndexOutOfBoundsException(String.valueOf(column));
        }
    }

    @Override public boolean isCellEditable(int index, int column) {
        return editingAllowed && column >= SIGN && rows.get(index).isConfigurable();
    }

    @Override public void setValueAt(Object value, int index, int column) {
        if (!(value instanceof Boolean) || !isCellEditable(index, column)) { return; }
        ButtonPolicyRow row = rows.get(index);
        boolean flag = ((Boolean)value).booleanValue();
        boolean sign = column == SIGN ? flag : Boolean.TRUE.equals(getValueAt(index, SIGN));
        boolean reason = column == REASON ? flag : Boolean.TRUE.equals(getValueAt(index, REASON));
        boolean audit = column == AUDIT ? flag : Boolean.TRUE.equals(getValueAt(index, AUDIT));
        if (sign == row.isSignatureEnabled() && reason == row.isReasonRequired() && audit == row.isAuditEnabled()) {
            pending.remove(row.getScopeId());
        } else {
            pending.put(row.getScopeId(), new ButtonPolicyChange(row.getScopeId(), sign, reason, audit, row.getVersion()));
        }
        fireTableRowsUpdated(index, index);
    }

    private static String mappingLabel(ButtonPolicyRow row) {
        if (row.isConfigurable()) {
            if ("PILOT".equals(row.getMappingState())) { return "试点可配置"; }
            if ("UNMAPPED".equals(row.getMappingState())) { return "部分入口试点可配置"; }
            return "可配置";
        }
        QdPolicyCodeCatalog.Codes codes = siteCodes(row);
        if (codes != null) { return codes.status(); }
        return "UNMAPPED".equals(row.getMappingState()) ? "未映射" : "待验证";
    }

    private static String displayCode(ButtonPolicyRow row, boolean node) {
        String actual = node ? row.getNodeCode() : row.getButtonCode();
        if (hasCode(actual)) { return actual; }
        QdPolicyCodeCatalog.Codes codes = siteCodes(row);
        if (codes == null) { return QdPolicyCodeCatalog.PENDING; }
        return node ? codes.nodes() : codes.buttons();
    }

    private static QdPolicyCodeCatalog.Codes siteCodes(ButtonPolicyRow row) {
        return hasCode(row.getNodeCode()) || hasCode(row.getButtonCode())
                ? null : QdPolicyCodeCatalog.find(row.getScopeId());
    }

    private static boolean hasCode(String code) { return code != null && !code.trim().isEmpty(); }
}
