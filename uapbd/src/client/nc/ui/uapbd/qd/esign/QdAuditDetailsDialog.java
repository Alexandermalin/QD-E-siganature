package nc.ui.uapbd.qd.esign;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.WindowConstants;
import javax.swing.table.AbstractTableModel;
import nc.bs.framework.common.NCLocator;
import nc.itf.uapbd.qd.esign.ILogQueryService;
import nc.ui.pub.beans.UIButton;
import nc.ui.pub.beans.UIDialog;
import nc.ui.pub.beans.UILabel;
import nc.ui.pub.beans.UIPanel;
import nc.ui.pub.beans.UIScrollPane;
import nc.ui.pub.beans.UITextArea;
import nc.vo.uapbd.qd.esign.log.AuditDetails;

/** 审计详情窗口，按服务端认可的审计标识和月份加载分页差异及快照；截断预览不可视作完整字段值。 */
public final class QdAuditDetailsDialog extends UIDialog {
    /** serialVersionUID：Java 序列化版本号，保持跨 RPC 或 Swing 组件序列化的兼容标识。 */
    private static final long serialVersionUID = 1L;
    /**
     * auditId：当前审计主记录标识。
     * archiveMonth：审计或日志关联的北京时间自然月。
     */
    private final String auditId, archiveMonth;
    /** event：审计主记录摘要文本区域。 */
    private final UITextArea event = new UITextArea();
    /**
     * changes：字段差异集合或对应表模型。
     * snapshots：快照集合或对应表模型。
     */
    private final DetailModel changes = new DetailModel(), snapshots = new DetailModel();
    /**
     * changeTable：字段差异列表。
     * snapshotTable：快照列表。
     */
    private final JTable changeTable = new JTable(changes), snapshotTable = new JTable(snapshots);
    /** tabs：差异与快照分页的标签容器。 */
    private final JTabbedPane tabs = new JTabbedPane();
    /** status：明细加载及大字段截断说明的提示控件。 */
    private final UILabel status = new UILabel("大字段为受控预览；截断标记不代表完整值。");
    /**
     * previous：上一页按钮。
     * next：下一页按钮。
     */
    private final UIButton previous = new UIButton("上一页"), next = new UIButton("下一页");
    /**
     * view：查看当前所选内容按钮。
     * closeButton：关闭窗口按钮。
     */
    private final UIButton view = new UIButton("查看所选内容"), closeButton = new UIButton("关闭");
    /** busy：是否有后台任务正在执行，用于防止并发提交及提前关闭。 */
    private boolean busy;
    /** offset：从零开始的分页偏移。 */
    private int offset;
    /** pageSize：每页记录数，服务端还会校验范围。 */
    private final int pageSize = 100;
    /**
     * changeTotal：字段差异记录总数。
     * snapshotTotal：快照记录总数。
     */
    private long changeTotal, snapshotTotal;

    /** 保存审计标识及月份，构建主记录、差异和快照只读页面以及分页控件。 */
    public QdAuditDetailsDialog(Container parent, String auditId, String archiveMonth) {
        super(parent, "业务审计明细", true);
        this.auditId = auditId; this.archiveMonth = archiveMonth;
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        event.setEditable(false); event.setLineWrap(true); event.setWrapStyleWord(true);
        tabs.add("审计事件", new UIScrollPane(event));
        configure(changeTable); configure(snapshotTable);
        tabs.add("字段差异", new UIScrollPane(changeTable));
        tabs.add("数据快照", new UIScrollPane(snapshotTable));
        UIPanel content = new UIPanel(new BorderLayout(8, 10));
        content.setBorder(BorderFactory.createEmptyBorder(12, 14, 10, 14));
        content.add(new UILabel("明细按请求关联跨月读取；权限以所选审计事件为准。"), BorderLayout.NORTH);
        content.add(tabs, BorderLayout.CENTER);
        UIPanel footer = new UIPanel(new BorderLayout(6, 6)); footer.add(status, BorderLayout.NORTH);
        UIPanel buttons = new UIPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(previous); buttons.add(next); buttons.add(view); buttons.add(closeButton);
        footer.add(buttons, BorderLayout.SOUTH); content.add(footer, BorderLayout.SOUTH);
        setContentPane(content); setSize(1080, 650); updateEnabled();
        previous.addActionListener(new ActionListener() {
        /** 响应当前控件绑定的界面事件，将处理交给相应查询、保存、导出或关闭路径。 */
        public void actionPerformed(ActionEvent e) { load(Math.max(0, offset - pageSize)); } });
        next.addActionListener(new ActionListener() {
        /** 响应当前控件绑定的界面事件，将处理交给相应查询、保存、导出或关闭路径。 */
        public void actionPerformed(ActionEvent e) { if (offset <= Integer.MAX_VALUE - pageSize) { load(offset + pageSize); } } });
        view.addActionListener(new ActionListener() {
        /** 响应当前控件绑定的界面事件，将处理交给相应查询、保存、导出或关闭路径。 */
        public void actionPerformed(ActionEvent e) { showContent(); } });
        closeButton.addActionListener(new ActionListener() {
        /** 响应当前控件绑定的界面事件，将处理交给相应查询、保存、导出或关闭路径。 */
        public void actionPerformed(ActionEvent e) { closeCancel(); } });
    }

    /** 窗口创建后发起首次加载，让界面先完成初始化再执行远程查询。 */
    public void startInitialQuery() { load(0); }
    /** 异步加载指定筛选或分页位置，仅在成功返回后替换当前展示数据。 */
    private void load(final int requestedOffset) {
        if (busy) { return; }
        busy = true; status.setText("正在查询审计字段差异与快照…"); updateEnabled();
        new NcClientWorker<AuditDetails>(this) {
            /** 在携带原生身份的后台线程执行本次远程调用，异常交由统一完成回调处理。 */
            protected AuditDetails callService() throws Exception {
                return NCLocator.getInstance().lookup(ILogQueryService.class).details(auditId, archiveMonth, requestedOffset, pageSize);
            }
            /** 在 Swing 界面线程处理成功结果并更新本次请求对应的控件或表格。 */
            protected void completed(AuditDetails details) {
                offset = requestedOffset; changeTotal = details.getChangeTotal(); snapshotTotal = details.getSnapshotTotal();
                event.setText(preview(details.getAudit())); changes.show(details.getChanges()); snapshots.show(details.getSnapshots());
                for (JTable table : new JTable[] {changeTable, snapshotTable}) {
                    for (int column = 0; column < table.getColumnCount(); column++) { table.getColumnModel().getColumn(column).setPreferredWidth(170); }
                }
                status.setText("第 " + (offset / pageSize + 1) + " 页；字段差异 " + changeTotal + " 项，快照 "
                        + snapshotTotal + " 项。大字段为受控预览，截断处明确标记。");
            }
            /** 在界面线程处理后台失败并提示用户，不把失败结果当作成功数据展示。 */
            protected void failed(Throwable error) { status.setText("审计明细查询未完成。"); super.failed(error); }
            /** 无论成功或失败都执行的结束钩子，用于清理凭据、解除忙碌状态和刷新按钮可用性。 */
            protected void settled() { busy = false; updateEnabled(); }
        }.execute();
    }
    /** 配置只读详情表格的选择和展示属性。 */
    private void configure(JTable table) {
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
    }
    /** 显示当前选中差异或快照的受限内容，并明确提示原长度及是否截断。 */
    private void showContent() {
        JTable table = tabs.getSelectedIndex() == 1 ? changeTable : snapshotTable;
        DetailModel model = tabs.getSelectedIndex() == 1 ? changes : snapshots;
        if (tabs.getSelectedIndex() == 0 || table.getSelectedRow() < 0) {
            status.setText("请在字段差异或数据快照页选择一条明细。"); return;
        }
        Map<String, Object> row = model.rows.get(table.convertRowIndexToModel(table.getSelectedRow()));
        UIDialog dialog = new UIDialog((Container) this, "明细内容预览", true);
        UITextArea area = QdLogQueryPanel.previewArea(preview(row));
        dialog.setContentPane(new UIScrollPane(area)); dialog.setSize(820, 580);
        try { dialog.showModal(); } finally { dialog.dispose(); }
    }
    /** 将详情行整理为可展示的预览文本，保留大字段截断说明。 */
    private static String preview(Map<String, Object> row) {
        StringBuilder result = new StringBuilder("大字段为预览；已截断字段请结合原长度判断。\n\n");
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            String key = entry.getKey();
            result.append(key).append("：").append(String.valueOf(entry.getValue()));
            Object truncated = row.get(key + "_truncated");
            if (Boolean.TRUE.equals(truncated) || "true".equalsIgnoreCase(String.valueOf(truncated)) || "Y".equals(truncated)) {
                result.append("\n【已截断；原长度 ").append(row.get(key + "_chars")).append(" 字符】");
            }
            result.append("\n\n");
            if (result.length() > 220000) { result.setLength(220000); result.append("\n【此窗口显示上限已到，当前内容不是完整记录】"); break; }
        }
        return result.toString();
    }
    /** 根据忙碌状态、已加载数据及分页位置计算按钮是否可操作。 */
    private void updateEnabled() {
        previous.setEnabled(!busy && offset > 0);
        next.setEnabled(!busy && (long) offset + pageSize < Math.max(changeTotal, snapshotTotal));
        view.setEnabled(!busy); closeButton.setEnabled(!busy);
    }
    /** 处理取消或关闭请求；后台调用期间按窗口规则阻止提前结束，并清理仍可取消的授权。 */
    @Override public void closeCancel() { if (!busy) { super.closeCancel(); } }
    /** 将窗口关闭事件纳入本窗口的忙碌状态和取消逻辑，其他事件继续交给父类。 */
    @Override protected void processWindowEvent(WindowEvent e) {
        if (e.getID() == WindowEvent.WINDOW_CLOSING) { closeCancel(); return; } super.processWindowEvent(e);
    }
    /** 只读审计详情表模型，分别承载差异或快照页，不允许界面修改服务端证据。 */
    private static final class DetailModel extends AbstractTableModel {
        /** serialVersionUID：Java 序列化版本号，保持跨 RPC 或 Swing 组件序列化的兼容标识。 */
        private static final long serialVersionUID = 1L;
        /** rows：当前结果行集合。 */
        private List<Map<String, Object>> rows = Collections.emptyList();
        /** keys：当前表格显示的字段编码集合。 */
        private List<String> keys = Collections.emptyList();
        /** 替换当前只读表格数据并重建列集合，随后通知 Swing 刷新。 */
        void show(List<Map<String, Object>> values) {
            rows = new ArrayList<Map<String, Object>>(values); LinkedHashSet<String> columns = new LinkedHashSet<String>();
            for (Map<String, Object> row : rows) { columns.addAll(row.keySet()); }
            keys = new ArrayList<String>(columns); fireTableStructureChanged();
        }
        /** 返回当前页可展示的记录行数。 */
        public int getRowCount() { return rows.size(); }
        /** 返回当前数据模型的展示列数。 */
        public int getColumnCount() { return keys.size(); }
        /** 返回指定列的显示名称。 */
        public String getColumnName(int column) {
            String key = keys.get(column);
            return key.endsWith("_truncated") ? key.substring(0, key.length() - 10) + "（是否截断）"
                    : key.endsWith("_chars") ? key.substring(0, key.length() - 6) + "（原长度）" : key;
        }
        /** 从当前只读结果行读取指定列值，不改写原始证据。 */
        public Object getValueAt(int row, int column) {
            Object value = rows.get(row).get(keys.get(column)); String text = value == null ? "" : String.valueOf(value);
            return text.length() > 2000 ? text.substring(0, 2000) + "…（打开内容预览）" : text;
        }
        /** 始终禁止在表格中编辑日志或审计证据。 */
        public boolean isCellEditable(int row, int column) { return false; }
    }
}
