package nc.ui.uapbd.qd.esign;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.swing.JComboBox;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.table.AbstractTableModel;
import nc.bs.framework.common.NCLocator;
import nc.itf.uapbd.qd.esign.ILogQueryService;
import nc.ui.pub.beans.UIButton;
import nc.ui.pub.beans.UIDialog;
import nc.ui.pub.beans.UILabel;
import nc.ui.pub.beans.UIPanel;
import nc.ui.pub.beans.UIScrollPane;
import nc.ui.pub.beans.UITextArea;
import nc.vo.uapbd.qd.esign.audit.SnapshotContent;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;
import nc.vo.uapbd.qd.esign.log.AuditDetails;

/** 跟随上方审计记录的只读事件、字段差异及快照面板。 */
final class QdAuditDetailsPanel extends UIPanel {
    private static final long serialVersionUID = 1L;
    private static final int PAGE_SIZE = 100;
    private final JTabbedPane tabs = new JTabbedPane();
    private final EventModel events = new EventModel();
    private final ChangeModel changes = new ChangeModel();
    private final SnapshotModel fields = new SnapshotModel();
    private final JTable eventTable = new JTable(events), changeTable = new JTable(changes), fieldTable = new JTable(fields);
    private final JComboBox<String> snapshotSelector = new JComboBox<String>();
    private final UILabel status = new UILabel("请选择一条审计记录。");
    private final UIButton previous = new UIButton("明细上一页"), next = new UIButton("明细下一页"), view = new UIButton("查看所选内容"), retry = new UIButton("重新加载明细");
    private List<Map<String, Object>> snapshots = Collections.emptyList();
    private String auditId, archiveMonth;
    private int changeOffset, snapshotOffset;
    private long changeTotal, snapshotTotal, generation, snapshotGeneration;
    private boolean busy, changingSelector;
    interface DetailLoader { AuditDetails load(String auditId, String month, int offset, int pageSize) throws Exception; }
    private final DetailLoader loader;

    QdAuditDetailsPanel() {
        this(new DetailLoader() {
            public AuditDetails load(String auditId, String month, int offset, int pageSize) throws Exception {
                return NCLocator.getInstance().lookup(ILogQueryService.class).details(auditId, month, offset, pageSize);
            }
        });
    }
    QdAuditDetailsPanel(DetailLoader loader) {
        super(new BorderLayout(6, 4));
        if (loader == null) { throw new IllegalArgumentException("Audit detail loader is required"); }
        this.loader = loader;
        setMinimumSize(new Dimension(200, 150));
        configure(eventTable); configure(changeTable); configure(fieldTable);
        tabs.addTab("审计事件", new UIScrollPane(eventTable));
        tabs.addTab("字段差异", new UIScrollPane(changeTable));
        UIPanel snapshot = new UIPanel(new BorderLayout(4, 4));
        UIPanel selector = new UIPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        selector.add(new UILabel("快照对象及阶段")); selector.add(snapshotSelector);
        snapshot.add(selector, BorderLayout.NORTH); snapshot.add(new UIScrollPane(fieldTable), BorderLayout.CENTER);
        tabs.addTab("数据快照", snapshot); add(tabs, BorderLayout.CENTER);
        UIPanel footer = new UIPanel(new BorderLayout(4, 2)); footer.add(status, BorderLayout.CENTER);
        UIPanel buttons = new UIPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        for (UIButton button : new UIButton[] {previous, next, view, retry}) { QdLogQueryPanel.fitButton(button); }
        buttons.add(previous); buttons.add(next); buttons.add(view); buttons.add(retry); footer.add(buttons, BorderLayout.EAST);
        add(footer, BorderLayout.SOUTH);
        previous.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { page(-1); } });
        next.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { page(1); } });
        view.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { showContent(); } });
        retry.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { if (!busy && auditId != null) { load(0, -1); } } });
        snapshotSelector.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { if (!changingSelector) { showSnapshot(); } } });
        tabs.addChangeListener(new ChangeListener() { public void stateChanged(ChangeEvent e) {
            if (tabs.getSelectedIndex() == 2) { showSnapshot(); } else { updateEnabled(); }
        } });
        updateEnabled();
    }

    private static void configure(JTable table) {
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
    }

    /** 更换选择时立即清空旧明细；每次请求均有令牌，旧结果不能覆盖新选择。 */
    void select(String id, String month) {
        generation++; auditId = id; archiveMonth = month; busy = false;
        changeOffset = snapshotOffset = 0; changeTotal = snapshotTotal = 0;
        events.show(null); changes.show(Collections.<Map<String, Object>>emptyList()); setSnapshots(Collections.<Map<String, Object>>emptyList());
        if (id == null || month == null || id.length() == 0 || month.length() == 0) {
            status.setText("请选择有审计编号和归档月份的记录。"); updateEnabled(); return;
        }
        load(0, -1);
    }

    private void page(int direction) {
        if (busy || auditId == null) { return; }
        int tab = tabs.getSelectedIndex();
        if (tab != 1 && tab != 2) { return; }
        int offset = tab == 1 ? changeOffset : snapshotOffset;
        long total = tab == 1 ? changeTotal : snapshotTotal;
        long requested = (long) offset + direction * PAGE_SIZE;
        if (requested < 0 || requested >= total || requested > Integer.MAX_VALUE) { return; }
        load((int) requested, tab);
    }

    private void load(final int requestedOffset, final int targetTab) {
        final long ticket = ++generation;
        final String selectedId = auditId, selectedMonth = archiveMonth;
        busy = true; status.setText("正在加载审计明细…"); updateEnabled();
        new NcClientWorker<AuditDetails>(this) {
            protected AuditDetails callService() throws Exception {
                return loader.load(selectedId, selectedMonth, requestedOffset, PAGE_SIZE);
            }
            protected void completed(AuditDetails result) {
                if (ticket != generation) { return; }
                if (targetTab == -1) { events.show(result.getAudit()); }
                if (targetTab == -1 || targetTab == 1) {
                    changeOffset = requestedOffset; changeTotal = result.getChangeTotal(); changes.show(result.getChanges());
                }
                if (targetTab == -1 || targetTab == 2) {
                    snapshotOffset = requestedOffset; snapshotTotal = result.getSnapshotTotal(); setSnapshots(result.getSnapshots());
                }
                sizeColumns(eventTable); sizeColumns(changeTable); sizeColumns(fieldTable);
                if (tabs.getSelectedIndex() == 2) { showSnapshot(); }
                else { status.setText("字段差异 " + changeTotal + " 项；快照 " + snapshotTotal + " 项。大字段为受控预览。"); }
            }
            protected void failed(Throwable error) {
                if (ticket != generation) { return; }
                status.setText("审计明细查询失败，请重新选择记录或重试。"); super.failed(error);
            }
            protected void settled() { if (ticket == generation) { busy = false; updateEnabled(); } }
        }.execute();
    }

    private static void sizeColumns(JTable table) {
        for (int i = 0; i < table.getColumnCount(); i++) { table.getColumnModel().getColumn(i).setPreferredWidth(i == 1 ? 240 : 150); }
    }

    private void setSnapshots(List<Map<String, Object>> rows) {
        snapshots = new ArrayList<Map<String, Object>>(rows);
        changingSelector = true;
        try {
            snapshotSelector.removeAllItems(); int firstAfter = -1;
            for (int i = 0; i < snapshots.size(); i++) {
                Map<String, Object> row = snapshots.get(i);
                String phase = string(row.get("snapshot_phase"));
                if (firstAfter < 0 && "AFTER".equals(phase)) { firstAfter = i; }
                snapshotSelector.addItem(snapshotTitle(phase,string(row.get("entity_name")),string(row.get("row_id"))));
            }
            if (!snapshots.isEmpty()) { snapshotSelector.setSelectedIndex(firstAfter >= 0 ? firstAfter : 0); }
        } finally { changingSelector = false; }
        snapshotGeneration++;
        fields.show(null, null);
    }

    private void showSnapshot() {
        final long ticket = ++snapshotGeneration;
        int selected = snapshotSelector.getSelectedIndex();
        if (selected < 0 || selected >= snapshots.size()) { fields.show(null, null); updateEnabled(); return; }
        final Map<String, Object> row = snapshots.get(selected);
        if (AuditUiData.truncated(row, "payload")) {
            fields.show(null, null);
            status.setText("正在加载完整快照的字段…");
            final String id = auditId, month = archiveMonth;
            new NcClientWorker<SnapshotContent>(this) {
                protected SnapshotContent callService() throws Exception { return readSnapshot(id, month, row, null, 0); }
                protected void completed(SnapshotContent data) {
                    if (ticket == snapshotGeneration && tabs.getSelectedIndex() == 2) { showFields(row, data); }
                }
                protected void failed(Throwable error) {
                    if (ticket == snapshotGeneration && tabs.getSelectedIndex() == 2) {
                        status.setText("快照字段加载失败，请重试。"); super.failed(error);
                    }
                }
            }.execute();
            return;
        }
        try {
            showFields(row, AuditUiData.content(row, SnapshotContent.FIELD_PREVIEW));
        } catch (IllegalArgumentException failure) {
            fields.show(null, null); status.setText(failure.getMessage());
        }
    }

    private void showFields(Map<String, Object> row, SnapshotContent data) {
        fields.show(row, data);
        String coverage = data.hasPhysicalColumns() ? "全字段快照" : "历史快照，按当时采集的字段展示";
        status.setText("快照 " + (snapshotOffset + snapshotSelector.getSelectedIndex() + 1) + " / " + snapshotTotal
                + " · " + coverage + " · " + data.getFields().size() + " 个字段");
        sizeColumns(fieldTable); updateEnabled();
    }

    private static SnapshotContent readSnapshot(String id, String month, Map<String, Object> row, String field, long offset)
            throws Exception {
        return NCLocator.getInstance().lookup(ILogQueryService.class).snapshotContent(id, month,
                string(row.get("snapshot_id")), field, offset);
    }

    private void showContent() {
        if (tabs.getSelectedIndex() == 2) {
            int selection = fieldTable.getSelectedRow();
            if (selection < 0) { status.setText("请选择一个快照字段。"); return; }
            SnapshotContent.Field field = fields.rows.get(fieldTable.convertRowIndexToModel(selection));
            new SnapshotValueView(snapshots.get(snapshotSelector.getSelectedIndex()), field).show();
            return;
        }
        Map<String, Object> row = null;
        if (tabs.getSelectedIndex() == 1 && changeTable.getSelectedRow() >= 0) {
            row = changes.rows.get(changeTable.convertRowIndexToModel(changeTable.getSelectedRow()));
        }
        if (row == null) { status.setText("请选择一条字段差异或快照。"); return; }
        StringBuilder text = new StringBuilder("大字段为受控预览；截断内容不是完整记录。\n\n");
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            String key = entry.getKey();
            if ("batch_id".equals(key) || key.endsWith("_chars") || key.endsWith("_truncated")) { continue; }
            text.append(QdLogQueryPanel.label(key)).append("：").append(AuditUiData.displayValue(entry.getValue()));
            if (AuditUiData.truncated(row, key)) { text.append("【已截断，原长度 ").append(row.get(key + "_chars")).append(" 字符】"); }
            text.append('\n');
            if (text.length() > 220000) { text.setLength(220000); text.append("\n【内容预览达到显示上限】"); break; }
        }
        UIDialog dialog = new UIDialog(this, "明细内容预览", true);
        UITextArea area = QdLogQueryPanel.previewArea(text.toString());
        dialog.setContentPane(new UIScrollPane(area)); dialog.setSize(820, 580);
        try { dialog.showModal(); } finally { dialog.dispose(); }
    }

    private void updateEnabled() {
        int tab = tabs.getSelectedIndex(); int offset = tab == 1 ? changeOffset : snapshotOffset;
        long total = tab == 1 ? changeTotal : snapshotTotal;
        previous.setEnabled(!busy && tab > 0 && offset > 0);
        next.setEnabled(!busy && tab > 0 && (long) offset + PAGE_SIZE < total);
        snapshotSelector.setEnabled(!busy && !snapshots.isEmpty());
        view.setEnabled(!busy && tab > 0 && auditId != null);
        retry.setEnabled(!busy && auditId != null);
    }

    @Override public void removeNotify() { generation++; snapshotGeneration++; busy = false; super.removeNotify(); }
    static String snapshotTitle(String phase,String entity,String rowId) {
        if("BEFORE".equals(phase) && SnapshotRow.INVENTORY_BATCH_REFERENCE.equals(entity))
            return "删除前引用 / 批次档案 / " + rowId;
        String title="BEFORE".equals(phase)?"保存前":"AFTER".equals(phase)?"保存后":"DELETE".equals(phase)?"删除前":phase;
        return title + " / " + entity + " / " + rowId;
    }
    private static String string(Object value) { return value == null ? "" : String.valueOf(value); }

    private static final class EventModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private final String[] keys = {"event_time","user_code","user_name","node_name","button_name","operation_type","bill_code","result_code","reason","node_code","button_code","request_id","audit_id","object_type","object_pk","pk_org","qd_org_name","policy_owner_org","qd_owner_org_name","target_org","qd_target_org_name","pk_group","client_ip","terminal_name","archive_month"};
        private Map<String, Object> row;
        void show(Map<String, Object> value) { row = value; fireTableDataChanged(); }
        public int getRowCount() { return row == null ? 0 : 1; }
        public int getColumnCount() { return keys.length; }
        public String getColumnName(int i) { return QdLogQueryPanel.label(keys[i]); }
        public Object getValueAt(int r, int c) { Object value=row.get(keys[c]); return value==null&&("policy_owner_org".equals(keys[c])||"target_org".equals(keys[c]))?"未记录":string(value); }
    }

    private static final class ChangeModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private final String[] keys = {"entity_name","row_id","field_code","field_name","change_type","before_value","after_value"};
        private final String[] labels = {"实体","数据行","字段编码","字段名称","变更类型","原值","现值"};
        private List<Map<String, Object>> rows = Collections.emptyList();
        void show(List<Map<String, Object>> values) { rows = new ArrayList<Map<String, Object>>(values); fireTableDataChanged(); }
        public int getRowCount() { return rows.size(); }
        public int getColumnCount() { return keys.length; }
        public String getColumnName(int c) { return labels[c]; }
        public Object getValueAt(int r, int c) {
            Map<String, Object> row = rows.get(r); String key = keys[c];
            if ("field_name".equals(key)) { return AuditUiData.fieldName(string(row.get("entity_name")), string(row.get("field_code")), string(row.get(key))); }
            if ("before_value".equals(key) || "after_value".equals(key)) {
                String value = AuditUiData.displayValue(row.get(key));
                if (value.length() > 2000) { value = value.substring(0, 2000) + "…（查看内容预览）"; }
                return AuditUiData.truncated(row, key) ? value + "【已截断】" : value;
            }
            return string(row.get(key));
        }
    }

    private static final class SnapshotModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private final String[] labels = {"表名", "数据行", "字段编码", "字段名称", "字段值"};
        private List<SnapshotContent.Field> rows = Collections.emptyList();
        private String table, rowId;
        void show(Map<String, Object> row, SnapshotContent data) {
            table = row == null ? "" : string(row.get("entity_name"));
            rowId = row == null ? "" : string(row.get("row_id"));
            rows = data == null ? Collections.<SnapshotContent.Field>emptyList() : data.getFields();
            fireTableDataChanged();
        }
        public int getRowCount() { return rows.size(); }
        public int getColumnCount() { return labels.length; }
        public String getColumnName(int c) { return labels[c]; }
        public Object getValueAt(int r, int c) {
            SnapshotContent.Field field = rows.get(r);
            switch (c) {
                case 0: return table;
                case 1: return rowId;
                case 2: return field.getCode();
                case 3: return AuditUiData.fieldName(table, field.getCode(), field.getLabel());
                default:
                    String text = AuditUiData.displayValue(field.getValue());
                    return field.hasMore() ? text + "…（查看完整字段值）" : text;
            }
        }
    }

    private final class SnapshotValueView {
        private final String id = auditId, month = archiveMonth;
        private final Map<String, Object> row;
        private final UIDialog dialog;
        private final UITextArea area = QdLogQueryPanel.previewArea("");
        private final UILabel range = new UILabel();
        private final UIButton back = new UIButton("上一段"), forward = new UIButton("下一段");
        private SnapshotContent.Field field;
        private boolean closed;

        SnapshotValueView(Map<String, Object> row, SnapshotContent.Field field) {
            this.row = row; this.field = field;
            String type = field.getType() == null ? "" : " [" + field.getType() + "]";
            dialog = new UIDialog(QdAuditDetailsPanel.this,
                    "快照字段值 / " + string(row.get("entity_name")) + "." + field.getCode() + type, true);
            UIPanel body = new UIPanel(new BorderLayout(4, 4));
            body.add(new UIScrollPane(area), BorderLayout.CENTER);
            UIPanel controls = new UIPanel(new FlowLayout(FlowLayout.RIGHT));
            controls.add(range); controls.add(back); controls.add(forward); body.add(controls, BorderLayout.SOUTH);
            back.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) {
                load(Math.max(0, SnapshotValueView.this.field.getOffset() - SnapshotContent.VALUE_PAGE_SIZE));
            } });
            forward.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) {
                SnapshotContent.Field current = SnapshotValueView.this.field;
                load(current.getOffset() + current.getValue().length());
            } });
            dialog.setContentPane(body); dialog.setSize(820, 580);
        }

        void show() {
            if (field.hasMore()) { load(0); } else { display(field); }
            try { dialog.showModal(); } finally { closed = true; dialog.dispose(); }
        }

        private void load(final long offset) {
            final String code = field.getCode();
            range.setText("正在读取字段值…"); back.setEnabled(false); forward.setEnabled(false);
            new NcClientWorker<SnapshotContent>(dialog) {
                protected SnapshotContent callService() throws Exception { return readSnapshot(id, month, row, code, offset); }
                protected void completed(SnapshotContent result) { if (!closed) { display(result.getFields().get(0)); } }
                protected void failed(Throwable error) {
                    if (!closed) { range.setText("字段值读取失败"); super.failed(error); }
                }
            }.execute();
        }

        private void display(SnapshotContent.Field value) {
            field = value;
            area.setText(AuditUiData.displayValue(value.getValue())); area.setCaretPosition(0);
            long start = value.getLength() == 0 ? 0 : value.getOffset() + 1;
            long end = value.getOffset() + (value.getValue() == null ? 0 : value.getValue().length());
            range.setText(start + "–" + end + " / " + value.getLength() + " 字符");
            back.setEnabled(value.getOffset() > 0); forward.setEnabled(value.hasMore());
        }
    }
}
