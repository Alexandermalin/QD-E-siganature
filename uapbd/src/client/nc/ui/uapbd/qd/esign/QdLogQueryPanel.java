package nc.ui.uapbd.qd.esign;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Dimension;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JFileChooser;
import javax.swing.JTable;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import nc.bs.framework.common.NCLocator;
import nc.bs.framework.common.InvocationInfoProxy;
import nc.itf.uapbd.qd.esign.ILogExportService;
import nc.itf.uapbd.qd.esign.ILogQueryService;
import nc.ui.pub.beans.MessageDialog;
import nc.ui.pub.beans.UIButton;
import nc.ui.pub.beans.UIComboBox;
import nc.ui.pub.beans.UIDialog;
import nc.ui.pub.beans.UILabel;
import nc.ui.pub.beans.UIPanel;
import nc.ui.pub.beans.UIRefPane;
import nc.ui.pub.beans.UIScrollPane;
import nc.ui.pub.beans.UITextArea;
import nc.ui.pub.beans.UITextField;
import nc.ui.org.ref.BusinessUnitDefaultRefModel;
import nc.ui.bd.ref.busi.UserDefaultRefModel;
import nc.ui.querytemplate.querytype.UFDateTimeTextField;
import nc.vo.uapbd.qd.esign.log.LogKind;
import nc.vo.uapbd.qd.esign.log.LogPage;
import nc.vo.uapbd.qd.esign.log.LogQuery;

/** 日志查询面板提供只读分页表、关联记录与受限 CSV 导出。服务端负责最终权限校验。 */
final class QdLogQueryPanel extends UIPanel {
    /** serialVersionUID：Java 序列化版本号，保持跨 RPC 或 Swing 组件序列化的兼容标识。 */
    private static final long serialVersionUID = 1L;
    /** kind：当前操作的日志类别。 */
    private final LogKind kind;
    /**
     * from：北京时间查询起点输入控件。
     * to：北京时间查询终点输入控件。
     */
    private final UITextField from = new UITextField(20), to = new UITextField(20);
    private final UFDateTimeTextField auditFrom, auditTo;
    private final UIRefPane orgRef, operatorRef;
    private final UIComboBox signatureResult;
    private final QdAuditDetailsPanel embeddedDetails;
    /**
     * user：用户身份。
     * node：真实节点编码筛选输入控件。
     */
    private final UITextField user = new UITextField(20), node = new UITextField(20);
    /**
     * bill：单据编码筛选控件。
     * request：请求标识筛选控件。
     */
    private final UITextField bill = new UITextField(20), request = new UITextField(20);
    /**
     * batch：批次标识。
     * result：结果码筛选输入控件。
     */
    private final UITextField batch = new UITextField(20), result = new UITextField(20);
    /**
     * reason：事由包含匹配的筛选输入控件。
     * userId：用户主键筛选输入控件。
     */
    private final UITextField reason = new UITextField(20), userId = new UITextField(20);
    /** controls：用于统一调整按钮可用性的控件集合。 */
    private final JButtonGroup controls = new JButtonGroup();
    /**
     * search：执行筛选查询按钮。
     * previous：上一页按钮。
     * next：下一页按钮。
     */
    private final UIButton search = controls.add("查询"), previous = controls.add("上一页"), next = controls.add("下一页");
    /**
     * summary：查看所选日志摘要按钮。
     * relatedSign：查看关联签名日志按钮。
     * relatedAudit：查看关联业务审计按钮。
     */
    private final UIButton summary = controls.add("选中记录摘要"), relatedSign = controls.add("关联签名"), relatedAudit = controls.add("关联审计");
    /** auditDetails：查看审计差异和快照按钮。 */
    private final UIButton auditDetails = controls.add("审计明细");
    /**
     * exportPage：导出已加载页按钮。
     * exportAll：导出全部筛选结果按钮。
     * closeButton：关闭窗口按钮。
     */
    private final UIButton exportPage = controls.add("导出已加载页"), exportAll = controls.add("导出筛选结果");
    private final UIButton closeButton;
    /** status：列表查询、分页及导出的状态提示控件。 */
    private final UILabel status = new UILabel("查询条件为北京时间，列表时间为 UTC。默认查询最近 24 小时。");
    /** tableModel：只读日志列表模型。 */
    private final LogTableModel tableModel;
    /** table：日志展示表格。 */
    private final JTable table = new JTable() {
        private static final long serialVersionUID = 1L;
        @Override public String getToolTipText(MouseEvent event) {
            int viewRow = rowAtPoint(event.getPoint()), viewColumn = columnAtPoint(event.getPoint());
            if (kind == LogKind.SIGNATURE && viewRow >= 0 && viewColumn >= 0
                    && "pk_org".equals(tableModel.key(convertColumnIndexToModel(viewColumn)))) {
                Object id = tableModel.row(convertRowIndexToModel(viewRow)).get("pk_org");
                return id == null ? "业务单元主键不可用" : "业务单元主键：" + id;
            }
            return super.getToolTipText(event);
        }
    };
    /** applied：当前已加载页实际使用的筛选条件。 */
    private LogQuery applied;
    /** total：符合当前筛选条件的总记录数。 */
    private long total;
    /** busy：是否有后台任务正在执行，用于防止并发提交及提前关闭。 */
    private boolean busy;

    /** 初始化日志类别、北京时间默认区间、只读列表及分页导出控件，并绑定对应事件。 */
    QdLogQueryPanel(LogKind kind, UIButton closeButton) {
        super(new BorderLayout(8, 10));
        if (kind == null) { throw new IllegalArgumentException("Log kind is required"); }
        this.kind = kind;
        this.closeButton = closeButton;
        tableModel = new LogTableModel(kind);
        // tableModel 必须先于表格创建；表格实例在构造器中绑定。
        table.setModel(tableModel);
        if (kind == LogKind.AUDIT || kind == LogKind.SIGNATURE) {
            auditFrom = new UFDateTimeTextField(); auditTo = new UFDateTimeTextField();
            orgRef = new UIRefPane(); operatorRef = new UIRefPane();
            BusinessUnitDefaultRefModel orgModel = new BusinessUnitDefaultRefModel();
            UserDefaultRefModel userModel = new UserDefaultRefModel();
            if (kind == LogKind.SIGNATURE) { userModel.setAddEnableStateWherePart(false); }
            String group = InvocationInfoProxy.getInstance().getGroupId();
            if (group != null && group.length() > 0) { orgModel.setPk_group(group); userModel.setPk_group(group); }
            orgRef.setRefModel(orgModel); operatorRef.setRefModel(userModel);
            orgRef.setMultiSelectedEnabled(false); operatorRef.setMultiSelectedEnabled(false);
            if (kind == LogKind.SIGNATURE) { operatorRef.setReturnCode(true); }
            embeddedDetails = kind == LogKind.AUDIT ? new QdAuditDetailsPanel() : null;
        } else { auditFrom = auditTo = null; orgRef = operatorRef = null; embeddedDetails = null; }
        signatureResult = kind == LogKind.SIGNATURE ? new UIComboBox(SignatureResultOption.values()) : null;
        setBorder(BorderFactory.createEmptyBorder(12, 14, 10, 14));
        UIPanel filters;
        if (kind == LogKind.AUDIT) {
            filters = new UIPanel(new GridBagLayout());
            auditFilter(filters, 0, "开始时间（含）", auditFrom); auditFilter(filters, 1, "结束时间（不含）", auditTo);
            auditFilter(filters, 2, "业务单元", orgRef); auditFilter(filters, 3, "操作用户", operatorRef);
            auditFilter(filters, 4, "操作人员编码", user); auditFilter(filters, 5, "功能节点编码", node);
            auditFilter(filters, 6, "单据编码", bill); auditFilter(filters, 7, "请求编号", request);
            auditFilter(filters, 8, "结果编码", result); auditFilter(filters, 9, "操作事由包含", reason);
            UIPanel actions = new UIPanel(new FlowLayout(FlowLayout.LEFT, 6, 0)); actions.add(search); actions.add(new UILabel("每页 100 条；导出读取服务器当前值"));
            GridBagConstraints c = new GridBagConstraints(); c.gridx = 0; c.gridy = 5; c.gridwidth = 4;
            c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.insets = new Insets(3, 0, 0, 0); filters.add(actions, c);
            for (UIButton button : controls.buttons) { fitButton(button); }
        } else if (kind == LogKind.SIGNATURE) {
            filters = new UIPanel(new GridBagLayout());
            auditFilter(filters, 0, "开始时间（含）", auditFrom); auditFilter(filters, 1, "结束时间（不含）", auditTo);
            auditFilter(filters, 2, "业务单元", orgRef); auditFilter(filters, 3, "操作人编码", operatorRef);
            auditFilter(filters, 4, "功能节点编码", node); auditFilter(filters, 5, "单据编码", bill);
            auditFilter(filters, 6, "请求编号", request); auditFilter(filters, 7, "批次编号", batch);
            auditFilter(filters, 8, "结果类型", signatureResult); auditFilter(filters, 9, "操作事由包含", reason);
            UIPanel actions = new UIPanel(new FlowLayout(FlowLayout.LEFT, 6, 0)); actions.add(search); actions.add(new UILabel("每页 100 条；签名成功仅表示身份确认成功"));
            GridBagConstraints c = new GridBagConstraints(); c.gridx = 0; c.gridy = 5; c.gridwidth = 4;
            c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.insets = new Insets(3, 0, 0, 0); filters.add(actions, c);
            for (UIButton button : controls.buttons) { fitButton(button); }
        } else {
            filters = new UIPanel(new GridLayout(0, 4, 10, 6));
            filter(filters, "开始时间（含）", from); filter(filters, "结束时间（不含）", to);
            filter(filters, "操作人员编码", user); filter(filters, "功能节点编码", node);
            filter(filters, "单据编码", bill); filter(filters, "请求编号", request);
            filter(filters, "批次编号", batch); filter(filters, "结果编码", result);
            filter(filters, "操作事由包含", reason); filter(filters, "操作人员主键", userId);
            filters.add(search); filters.add(new UILabel("每页 100 条；导出读取服务器当前值"));
        }
        add(filters, BorderLayout.NORTH);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF); table.setAutoCreateRowSorter(true);
        table.setFillsViewportHeight(true);
        table.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        if (kind == LogKind.AUDIT) {
            final JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new UIScrollPane(table), embeddedDetails);
            split.setResizeWeight(0.45); split.setContinuousLayout(true);
            split.addHierarchyListener(new HierarchyListener() {
                public void hierarchyChanged(HierarchyEvent event) {
                    if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && split.isShowing()) {
                        split.removeHierarchyListener(this);
                        SwingUtilities.invokeLater(new Runnable() { public void run() { split.setDividerLocation(0.45); } });
                    }
                }
            });
            add(split, BorderLayout.CENTER);
            table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
                public void valueChanged(ListSelectionEvent event) {
                    if (event.getValueIsAdjusting()) { return; }
                    int selected = table.getSelectedRow();
                    if (selected < 0) { embeddedDetails.select(null, null); return; }
                    Map<String, Object> row = tableModel.row(table.convertRowIndexToModel(selected));
                    Object id = row.get("audit_id"), month = row.get("archive_month");
                    embeddedDetails.select(id == null ? null : String.valueOf(id), month == null ? null : String.valueOf(month));
                }
            });
        } else { add(new UIScrollPane(table), BorderLayout.CENTER); }
        UIPanel footer = new UIPanel(new BorderLayout(8, 8)); footer.add(status, BorderLayout.NORTH);
        UIPanel buttons = new UIPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        for (UIButton control : controls.buttons) {
            if (control == search || (kind != LogKind.SECURITY && control == auditDetails)
                    || (kind == LogKind.SIGNATURE && control == relatedSign)) { continue; }
            buttons.add(control);
        }
        if (closeButton != null) { buttons.add(closeButton); }
        footer.add(buttons, BorderLayout.SOUTH); add(footer, BorderLayout.SOUTH);
        updateEnabled();
        search.addActionListener(new ActionListener() {
        /** 响应当前控件绑定的界面事件，将处理交给相应查询、保存、导出或关闭路径。 */
        public void actionPerformed(ActionEvent e) { search(); } });
        previous.addActionListener(new ActionListener() {
        /** 响应当前控件绑定的界面事件，将处理交给相应查询、保存、导出或关闭路径。 */
        public void actionPerformed(ActionEvent e) { page(-1); } });
        next.addActionListener(new ActionListener() {
        /** 响应当前控件绑定的界面事件，将处理交给相应查询、保存、导出或关闭路径。 */
        public void actionPerformed(ActionEvent e) { page(1); } });
        summary.addActionListener(new ActionListener() {
        /** 响应当前控件绑定的界面事件，将处理交给相应查询、保存、导出或关闭路径。 */
        public void actionPerformed(ActionEvent e) { showSummary(); } });
        relatedSign.addActionListener(new ActionListener() {
        /** 响应当前控件绑定的界面事件，将处理交给相应查询、保存、导出或关闭路径。 */
        public void actionPerformed(ActionEvent e) { related(LogKind.SIGNATURE); } });
        relatedAudit.addActionListener(new ActionListener() {
        /** 响应当前控件绑定的界面事件，将处理交给相应查询、保存、导出或关闭路径。 */
        public void actionPerformed(ActionEvent e) { related(LogKind.AUDIT); } });
        auditDetails.addActionListener(new ActionListener() {
        /** 响应当前控件绑定的界面事件，将处理交给相应查询、保存、导出或关闭路径。 */
        public void actionPerformed(ActionEvent e) { showAuditDetails(); } });
        exportPage.addActionListener(new ActionListener() {
        /** 响应当前控件绑定的界面事件，将处理交给相应查询、保存、导出或关闭路径。 */
        public void actionPerformed(ActionEvent e) { export(true); } });
        exportAll.addActionListener(new ActionListener() {
        /** 响应当前控件绑定的界面事件，将处理交给相应查询、保存、导出或关闭路径。 */
        public void actionPerformed(ActionEvent e) { export(false); } });
    }

    /** 以当前时间初始化最近 24 小时的筛选范围。 */
    void initializeTimeRange() {
        long now = System.currentTimeMillis();
        setTimeRange(LogUiFormats.beijing(now - 24L * 60 * 60 * 1000), LogUiFormats.beijing(now));
    }

    /** 查询面板初始化后加载当前筛选条件。 */
    void startInitialQuery() { search(); }

    /** 以原查询的时间范围和选中记录的真实请求号加载关联日志。 */
    void startRelatedQuery(LogQuery source, String requestId) {
        if (source != null) {
            setTimeRange(LogUiFormats.beijingFromUtc(source.getFromUtc()), LogUiFormats.beijingFromUtc(source.getToUtc()));
        }
        request.setText(requestId);
        search();
    }
    /** 向查询表单添加一个筛选标签及输入控件。 */
    private void filter(UIPanel panel, String label, Component field) { panel.add(new UILabel(label)); panel.add(field); }
    private void auditFilter(UIPanel panel, int index, String label, Component field) {
        GridBagConstraints c = new GridBagConstraints(); c.gridy = index / 2; c.gridx = (index % 2) * 2;
        c.anchor = GridBagConstraints.WEST; c.insets = new Insets(2, 2, 2, 8); panel.add(new UILabel(label), c);
        c.gridx++; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.insets = new Insets(2, 0, 2, 12); panel.add(field, c);
    }
    static void fitButton(UIButton button) {
        Dimension preferred = button.getPreferredSize();
        // NC 按钮样式为边框和内部布局保留较大间距，不能只按文字像素加普通 Swing 边距。
        int width = Math.max(button.getFontMetrics(button.getFont()).stringWidth(button.getText()) + 64,
                button.getText().length() * 18 + 32);
        button.setPreferredSize(new Dimension(Math.max(preferred.width, width), preferred.height));
    }
    private void setTimeRange(String start, String end) {
        if (kind != LogKind.SECURITY) { AuditDateTimeSupport.set(auditFrom, start); AuditDateTimeSupport.set(auditTo, end); }
        else { from.setText(start); to.setText(end); }
    }
    /** 读取并校验筛选输入，从第一页发起新的异步查询。 */
    private void search() {
        if (busy) { return; }
        try { load(readFilter()); }
        catch (IllegalArgumentException error) { status.setText(error.getMessage()); }
    }
    /** 把北京时间输入显式转换为 UTC 并构造筛选，缺少或非法时间会阻止查询。 */
    private LogQuery readFilter() {
        LogQuery query = new LogQuery();
        query.setFromUtc(LogUiFormats.utcFromBeijing(kind != LogKind.SECURITY ? auditTime(auditFrom) : value(from)));
        query.setToUtc(LogUiFormats.utcFromBeijing(kind != LogKind.SECURITY ? auditTime(auditTo) : value(to)));
        if (query.getFromUtc().compareTo(query.getToUtc()) >= 0) { throw new IllegalArgumentException("结束时间必须晚于开始时间。"); }
        query.setUserCode(kind == LogKind.SIGNATURE ? null : optional(user));
        query.setUserId(kind == LogKind.SECURITY ? optional(userId) : refPk(operatorRef));
        SignatureResultOption option = kind == LogKind.SIGNATURE ? (SignatureResultOption) signatureResult.getSelectedItem() : null;
        query.setResultCode(kind == LogKind.SIGNATURE ? (option == null ? null : option.code()) : optional(result));
        if (kind != LogKind.SECURITY) { query.setOrganizationId(refPk(orgRef)); }
        if (kind != LogKind.SECURITY) {
            query.setNodeCode(optional(node)); query.setBillCode(optional(bill)); query.setRequestId(optional(request));
            if (kind != LogKind.AUDIT) { query.setBatchId(optional(batch)); }
            query.setReasonContains(optional(reason));
        }
        query.setOffset(0); query.setPageSize(100);
        return query;
    }
    /** 异步加载指定筛选或分页位置，仅在成功返回后替换当前展示数据。 */
    private void load(final LogQuery query) {
        if (kind != LogKind.SECURITY) {
            table.clearSelection(); if (embeddedDetails != null) { embeddedDetails.select(null, null); }
            tableModel.show(Collections.<Map<String, Object>>emptyList()); applied = null; total = 0;
        }
        setBusy(true, "正在查询…");
        new NcClientWorker<LogPage>(this) {
            /** 在携带原生身份的后台线程执行本次远程调用，异常交由统一完成回调处理。 */
            protected LogPage callService() throws Exception {
                ILogQueryService service = NCLocator.getInstance().lookup(ILogQueryService.class);
                return kind == LogKind.AUDIT ? service.queryAuditV2(query)
                        : kind == LogKind.SIGNATURE ? service.querySignatureV2(query) : service.query(kind, query);
            }
            /** 在 Swing 界面线程处理成功结果并更新本次请求对应的控件或表格。 */
            protected void completed(LogPage page) {
                applied = query; total = page.getTotal(); tableModel.show(page.getRows());
                for (int column = 0; column < table.getColumnCount(); column++) { table.getColumnModel().getColumn(column).setPreferredWidth(150); }
                status.setText("第 " + (query.getOffset() / query.getPageSize() + 1) + " 页，共 " + total + " 条；列表时间 UTC，查询条件北京时间。");
                if (kind == LogKind.AUDIT && table.getRowCount() > 0) { table.setRowSelectionInterval(0, 0); }
            }
            /** 在界面线程处理后台失败并提示用户，不把失败结果当作成功数据展示。 */
            protected void failed(Throwable error) {
                status.setText(kind != LogKind.SECURITY ? "日志查询失败，当前列表已清空。" : "查询失败；保留上一次已确认结果。");
                if (kind != LogKind.SECURITY && versionMismatch(error)) { MessageDialog.showErrorDlg(QdLogQueryPanel.this, "日志查询", "服务端日志版本不匹配，请更新并重启中间件。"); }
                else { super.failed(error); }
            }
            /** 无论成功或失败都执行的结束钩子，用于清理凭据、解除忙碌状态和刷新按钮可用性。 */
            protected void settled() { setBusy(false, null); }
        }.execute();
    }
    /** 根据上一页或下一页方向计算偏移，沿用已加载页的筛选条件。 */
    private void page(int direction) {
        if (busy || applied == null) { return; }
        LogQuery query = copy(applied);
        long offset = (long) query.getOffset() + direction * query.getPageSize();
        if (offset < 0 || offset >= total || offset > Integer.MAX_VALUE) { return; }
        query.setOffset((int) offset); load(query);
    }

    /** 先由用户选择目标及确认覆盖，再异步取得受限 CSV；已加载页导出交接精确 ID 集合。 */
    private void export(final boolean currentPage) {
        if (busy || applied == null) { return; }
        final String[] loadedIds;
        try {
            if (!sameFilters(applied, readFilter())) { status.setText("查询条件已修改，请先查询后导出。"); return; }
            loadedIds = currentPage ? LogUiSelections.loadedIds(kind, tableModel.rows) : null;
        } catch (IllegalArgumentException error) { status.setText(error.getMessage()); return; }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(currentPage ? "导出已加载记录的服务器当前值 CSV" : "重新查询并导出当前筛选结果 CSV");
        chooser.setSelectedFile(new File("NC-" + kind.name() + "-logs.csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) { return; }
        File selected = chooser.getSelectedFile();
        if (!selected.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".csv")) { selected = new File(selected.getParentFile(), selected.getName() + ".csv"); }
        final File destination = selected;
        final boolean replace = destination.exists();
        if (replace && MessageDialog.showYesNoDlg(this, "覆盖文件", "文件已存在，是否覆盖所选 CSV 文件？") != UIDialog.ID_YES) { return; }
        final long expectedLength = destination.length(), expectedModified = destination.lastModified();
        final LogQuery query = copy(applied);
        setBusy(true, "正在导出…服务器将检查权限与导出大小限制。");
        new NcClientWorker<String>(this) {
            /** 在携带原生身份的后台线程执行本次远程调用，异常交由统一完成回调处理。 */
            protected String callService() throws Exception {
                ILogExportService service = NCLocator.getInstance().lookup(ILogExportService.class);
                byte[] bytes = kind == LogKind.AUDIT
                        ? (currentPage ? service.exportAuditLoadedV2(query, loadedIds) : service.exportAuditV2(query, false))
                        : kind == LogKind.SIGNATURE
                          ? (currentPage ? service.exportSignatureLoadedV2(query, loadedIds) : service.exportSignatureV2(query, false))
                          : (currentPage ? service.exportLoadedCsv(kind, query, loadedIds) : service.exportCsv(kind, query, false));
                try {
                    if (bytes == null) { throw new IllegalStateException("服务器未返回导出内容。"); }
                    CsvFileSaver.save(destination, bytes, replace, expectedLength, expectedModified);
                    return destination.getAbsolutePath();
                } finally { if (bytes != null) { Arrays.fill(bytes, (byte) 0); } }
            }
            /** 在 Swing 界面线程处理成功结果并更新本次请求对应的控件或表格。 */
            protected void completed(String path) { status.setText("CSV 已导出：" + path); }
            /** 在界面线程处理后台失败并提示用户，不把失败结果当作成功数据展示。 */
            protected void failed(Throwable error) {
                status.setText("导出未完成，请核对错误后重试。");
                if (kind != LogKind.SECURITY && versionMismatch(error)) { MessageDialog.showErrorDlg(QdLogQueryPanel.this, "日志导出", "服务端日志版本不匹配，请更新并重启中间件。"); }
                else { super.failed(error); }
            }
            /** 无论成功或失败都执行的结束钩子，用于清理凭据、解除忙碌状态和刷新按钮可用性。 */
            protected void settled() { setBusy(false, null); }
        }.execute();
    }

    /** 取得当前选中日志行，未选择时提示或拒绝后续明细操作。 */
    private Map<String, Object> selected() {
        int selected = table.getSelectedRow();
        if (selected < 0) { status.setText("请先选择一条记录。"); return null; }
        return tableModel.row(table.convertRowIndexToModel(selected));
    }
    /** 显示当前所选记录的只读摘要，不把列表预览称为完整审计值。 */
    private void showSummary() {
        Map<String, Object> row = selected(); if (row == null) { return; }
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (kind == LogKind.AUDIT && "batch_id".equals(entry.getKey())) { continue; }
            if (kind == LogKind.SIGNATURE && ("user_id".equals(entry.getKey()) || "qd_org_name".equals(entry.getKey()))) { continue; }
            String key=entry.getKey();Object display=kind == LogKind.SIGNATURE && "pk_org".equals(key) ? organizationName(row)
                    : kind == LogKind.SIGNATURE && "result_code".equals(key) ? SignatureResultOption.display(entry.getValue()) : entry.getValue();
            if (display == null && ("policy_owner_org".equals(key) || "target_org".equals(key))) { display = "未记录"; }
            text.append(kind == LogKind.SIGNATURE ? signatureLabel(key) : label(key)).append("：").append(String.valueOf(display)).append('\n');
            if (text.length() > 20000) { text.setLength(20000); text.append("\n内容较长，请使用授权导出查看。"); break; }
        }
        UIDialog dialog = new UIDialog(this, "选中记录摘要", true);
        UITextArea area = previewArea(text.toString());
        dialog.setContentPane(new UIScrollPane(area)); dialog.setSize(760, 520);
        try { dialog.showModal(); } finally { dialog.dispose(); }
    }
    /** 以当前审计记录的真实标识和月份打开受权限保护的详情窗口。 */
    private void showAuditDetails() {
        Map<String, Object> row = selected(); if (row == null) { return; }
        Object auditId = row.get("audit_id"), month = row.get("archive_month");
        if (auditId == null || month == null) { status.setText("所选记录缺少审计编号或月份，请重新查询。"); return; }
        QdAuditDetailsDialog dialog = new QdAuditDetailsDialog(this, auditId.toString(), month.toString());
        dialog.startInitialQuery();
        try { dialog.showModal(); } finally { dialog.dispose(); }
    }
    /** 按当前行保存的请求标识打开关联类别日志，不按时间或名称猜配关系。 */
    private void related(LogKind relatedKind) {
        Map<String, Object> row = selected(); if (row == null) { return; }
        Object id = row.get("request_id");
        if (id == null || id.toString().length() == 0) { status.setText("所选记录没有关联请求编号。"); return; }
        QdLogQueryDialog dialog = new QdLogQueryDialog(this, relatedKind);
        dialog.startRelatedQuery(applied, id.toString());
        try { dialog.showModal(); } finally { dialog.dispose(); }
    }

    /** 更新后台任务忙碌标记和提示，再统一调整控件可用性。 */
    private void setBusy(boolean value, String message) { busy = value; if (message != null) { status.setText(message); } updateEnabled(); }
    /** 根据忙碌状态、已加载数据及分页位置计算按钮是否可操作。 */
    private void updateEnabled() {
        for (UIButton button : controls.buttons) { button.setEnabled(!busy); }
        for (UITextField field : Arrays.asList(from, to, user, userId, node, bill, request, batch, result, reason)) { field.setEnabled(!busy); }
        if (kind != LogKind.SECURITY) { auditFrom.setEnabled(!busy); auditTo.setEnabled(!busy); orgRef.setEnabled(!busy); operatorRef.setEnabled(!busy); }
        if (signatureResult != null) { signatureResult.setEnabled(!busy); }
        if (kind == LogKind.SECURITY) {
            for (UITextField field : Arrays.asList(node, bill, request, batch, reason)) { field.setEnabled(false); }
        }
        previous.setEnabled(!busy && applied != null && applied.getOffset() > 0);
        next.setEnabled(!busy && applied != null && (long) applied.getOffset() + applied.getPageSize() < total);
        for (UIButton button : Arrays.asList(summary, exportPage, exportAll)) { button.setEnabled(!busy && applied != null); }
        relatedSign.setEnabled(!busy && applied != null && kind != LogKind.SECURITY);
        relatedAudit.setEnabled(!busy && applied != null && kind != LogKind.SECURITY);
        auditDetails.setEnabled(!busy && applied != null && kind == LogKind.AUDIT);
        if (closeButton != null) { closeButton.setEnabled(!busy); }
    }

    /** 在当前后台查询或导出结束后允许关闭宿主。 */
    boolean canClose() { return !busy; }
    /** 取得输入控件内容并处理首尾空白，供筛选或策略定位使用。 */
    private String value(UITextField field) { return field.getText() == null ? "" : field.getText().trim(); }
    /** 取得可选筛选文本，空白输入按未提供处理。 */
    private String optional(UITextField field) { String value = value(field); return value.length() == 0 ? null : value; }
    private String auditTime(UFDateTimeTextField field) {
        try { return AuditDateTimeSupport.get(field); }
        catch (RuntimeException error) { throw new IllegalArgumentException("请输入有效的开始和结束时间。", error); }
    }
    private String refPk(UIRefPane reference) { String value = reference.getRefPK(); return value == null || value.trim().length() == 0 ? null : value.trim(); }
    static UITextArea previewArea(String text) {
        UITextArea area = new UITextArea(); area.setText(text); area.setEditable(false); area.setLineWrap(true); area.setWrapStyleWord(true); return area;
    }
    private static boolean versionMismatch(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            String text = cause.toString().toLowerCase(java.util.Locale.ENGLISH);
            if (text.contains("nosuchmethod") || text.contains("abstractmethod") || text.contains("method not found")) { return true; }
        }
        return false;
    }
    /** 复制筛选内容与分页参数，使后续输入修改不影响已发出的查询或导出。 */
    static LogQuery copy(LogQuery input) {
        LogQuery copy = new LogQuery(); copy.setFromUtc(input.getFromUtc()); copy.setToUtc(input.getToUtc());
        copy.setUserId(input.getUserId()); copy.setUserCode(input.getUserCode()); copy.setOrganizationId(input.getOrganizationId()); copy.setNodeCode(input.getNodeCode());
        copy.setBillCode(input.getBillCode()); copy.setRequestId(input.getRequestId()); copy.setBatchId(input.getBatchId());
        copy.setResultCode(input.getResultCode()); copy.setReasonContains(input.getReasonContains());
        copy.setOffset(input.getOffset()); copy.setPageSize(input.getPageSize()); return copy;
    }
    /** 比较筛选内容是否一致，用于确认已加载页与当前导出条件相符。 */
    static boolean sameFilters(LogQuery a, LogQuery b) {
        return Arrays.asList(a.getFromUtc(), a.getToUtc(), a.getUserId(), a.getUserCode(), a.getOrganizationId(), a.getNodeCode(), a.getBillCode(),
                a.getRequestId(), a.getBatchId(), a.getResultCode(), a.getReasonContains()).equals(
                Arrays.asList(b.getFromUtc(), b.getToUtc(), b.getUserId(), b.getUserCode(), b.getOrganizationId(), b.getNodeCode(), b.getBillCode(),
                        b.getRequestId(), b.getBatchId(), b.getResultCode(), b.getReasonContains()));
    }
    /** 将固定字段编码映射为界面显示名称，未配置字段仍保留原编码以便识别。 */
    static String label(String key) {
        String[] keys = {"event_id","request_id","batch_id","user_id","user_code","user_name","node_code","node_name",
                "button_code","button_name","operation_type","bill_code","event_time","created_at","result_code","reason",
                "pk_org","pk_group","object_pk","object_type","archive_month","lock_result","before_value","after_value","field_code",
                "audit_id","object_id","entity_name","row_id","parent_row_id","field_name","data_type","change_type","snapshot_phase","payload","payload_hash","snapshot_id","client_ip","terminal_name",
                "policy_owner_org","policy_owner_type","policy_scope_id","target_org","qd_org_name","qd_owner_org_name","qd_target_org_name"};
        String[] labels = {"记录编号","请求编号","批次编号","人员主键","人员编码","人员姓名","功能节点编码","功能名称",
                "按钮编码","按钮名称","操作类型","单据编码","事件时间（UTC）","创建时间（UTC）","结果编码","操作事由",
                "业务单元主键","集团主键","业务对象主键","对象类型","归档月份","锁定结果","原值","现值","字段编码",
                "审计编号","对象编号","实体","数据行","父数据行","字段名称","数据类型","变更类型","快照阶段","快照内容","快照摘要","快照编号","客户端IP","终端名",
                "策略所属组织主键","所属组织类型","策略动作标识","目标组织主键","操作组织","策略所属组织","目标组织"};
        for (int i = 0; i < keys.length; i++) { if (keys[i].equals(key)) { return labels[i]; } }
        return key;
    }
    private static String signatureLabel(String key) {
        if ("pk_org".equals(key)) { return "操作组织"; }
        if ("user_code".equals(key)) { return "操作人编码"; }
        if ("user_name".equals(key)) { return "操作人"; }
        if ("attempt_id".equals(key)) { return "签名处理编号"; }
        if ("result_code".equals(key)) { return "结果类型"; }
        return label(key);
    }
    private static String organizationName(Map<String,Object> row) {
        Object value=row.get("qd_org_name");
        return value == null || String.valueOf(value).trim().length() == 0 ? "未识别组织" : String.valueOf(value);
    }
    /** 查询窗口的按钮集合，集中保存控件以便异步请求期间统一更新可用状态。 */
    private static final class JButtonGroup {
        /** buttons：需要统一管理可用状态的按钮集合。 */
        final List<UIButton> buttons = new ArrayList<UIButton>();
        /** 创建按钮并加入统一管理集合，返回控件供窗口绑定事件。 */
        UIButton add(String title) { UIButton button = new UIButton(title); buttons.add(button); return button; }
    }
    /** 只读日志表模型，维护当前页记录和实际列集合，向 Swing 提供行列及显示值。 */
    private static final class LogTableModel extends AbstractTableModel {
        /** serialVersionUID：Java 序列化版本号，保持跨 RPC 或 Swing 组件序列化的兼容标识。 */
        private static final long serialVersionUID = 1L;
        /** rows：当前结果行集合。 */
        private List<Map<String, Object>> rows = Collections.emptyList();
        /** keys：当前表格显示的字段编码集合。 */
        private List<String> keys = Collections.emptyList();
        private final LogKind kind;
        LogTableModel(LogKind kind) { this.kind = kind; }
        /** 替换当前只读表格数据并重建列集合，随后通知 Swing 刷新。 */
        void show(List<Map<String, Object>> data) {
            rows = new ArrayList<Map<String, Object>>(data);
            LinkedHashSet<String> columns = new LinkedHashSet<String>();
            for (Map<String, Object> row : rows) { columns.addAll(row.keySet()); }
            if (kind == LogKind.AUDIT) { columns.remove("batch_id"); }
            if (kind == LogKind.SIGNATURE) { columns.remove("user_id"); columns.remove("qd_org_name"); columns.remove("pk_org"); }
            keys = new ArrayList<String>(columns);
            if (kind == LogKind.SIGNATURE && !rows.isEmpty()) { keys.add(0, "pk_org"); }
            fireTableStructureChanged();
        }
        /** 按模型行号返回当前日志记录，供摘要、关联查询及导出选择使用。 */
        Map<String, Object> row(int index) { return new LinkedHashMap<String, Object>(rows.get(index)); }
        /** 返回当前页可展示的记录行数。 */
        public int getRowCount() { return rows.size(); }
        /** 返回当前数据模型的展示列数。 */
        public int getColumnCount() { return keys.size(); }
        /** 返回指定列的显示名称。 */
        String key(int index) { return keys.get(index); }
        public String getColumnName(int index) { return kind == LogKind.SIGNATURE ? signatureLabel(keys.get(index)) : label(keys.get(index)); }
        /** 从当前只读结果行读取指定列值，不改写原始证据。 */
        public Object getValueAt(int row, int column) {
            Object value = rows.get(row).get(keys.get(column));
            if (kind == LogKind.SIGNATURE && "pk_org".equals(keys.get(column))) { value = organizationName(rows.get(row)); }
            if (kind == LogKind.SIGNATURE && "result_code".equals(keys.get(column))) { value = SignatureResultOption.display(value); }
            if (value == null && ("policy_owner_org".equals(keys.get(column)) || "target_org".equals(keys.get(column)))) { value = "未记录"; }
            String text = value == null ? "" : String.valueOf(value);
            return text.length() > 2000 ? text.substring(0, 2000) + "…" : text;
        }
        /** 始终禁止在表格中编辑日志或审计证据。 */
        public boolean isCellEditable(int row, int column) { return false; }
    }
}
