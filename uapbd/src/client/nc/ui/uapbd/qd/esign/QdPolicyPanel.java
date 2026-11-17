package nc.ui.uapbd.qd.esign;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.RowFilter;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.RowSorterEvent;
import javax.swing.event.RowSorterListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;
import nc.bs.framework.common.InvocationInfoProxy;
import nc.bs.framework.common.NCLocator;
import nc.itf.uapbd.qd.esign.IPolicyService;
import nc.ui.org.ref.BusinessUnitDefaultRefModel;
import nc.ui.pub.beans.MessageDialog;
import nc.ui.pub.beans.UIButton;
import nc.ui.pub.beans.UICheckBox;
import nc.ui.pub.beans.UIDialog;
import nc.ui.pub.beans.UILabel;
import nc.ui.pub.beans.UIPanel;
import nc.ui.pub.beans.UIRefPane;
import nc.ui.pub.beans.UIScrollPane;
import nc.ui.pub.beans.UITextField;
import nc.ui.pub.beans.ValueChangedEvent;
import nc.ui.pub.beans.ValueChangedListener;
import nc.vo.uapbd.qd.esign.ButtonPolicyChange;
import nc.vo.uapbd.qd.esign.ButtonPolicyRow;
import nc.vo.uapbd.qd.esign.OrganizationPolicyView;

/** 查询当前集团或所选业务单元的策略，分别保存按钮和总控。 */
final class QdPolicyPanel extends UIPanel {
    private static final long serialVersionUID = 1L;

    private final UIRefPane orgRef = new UIRefPane();
    private final UITextField nodeFilter = new UITextField(20);
    private final UIButton query = new UIButton("查询");
    private final UICheckBox master = new UICheckBox("组织总控");
    private final UIButton saveMaster = new UIButton("保存组织总控");
    private final UIButton save = new UIButton("保存策略");
    private final UIButton closeButton;
    private final boolean groupPolicy;
    private final UILabel status = new UILabel();
    private final UILabel accessStatus = new UILabel();
    private final QdPolicyTableModel tableModel = new QdPolicyTableModel();
    private final TableRowSorter<QdPolicyTableModel> nodeSorter = new TableRowSorter<QdPolicyTableModel>(tableModel);
    private final JTable table = new JTable(tableModel) {
        private static final long serialVersionUID = 1L;
        @Override public String getToolTipText(MouseEvent event) {
            int row = rowAtPoint(event.getPoint());
            int column = convertColumnIndexToModel(columnAtPoint(event.getPoint()));
            if (row >= 0 && (column == 1 || column == 3 || column == 4)) {
                return tableModel.codeDetails(convertRowIndexToModel(row));
            }
            return super.getToolTipText(event);
        }
    };

    private String selectedOrg;
    private OrganizationPolicyView loaded;
    private boolean savedMaster;
    private long controlVersion;
    private boolean restoringSelection;
    private boolean busy;
    private boolean needsReload;

    QdPolicyPanel(final String currentOrg, UIButton closeButton) {
        this(currentOrg, closeButton, false);
    }

    QdPolicyPanel(final String currentOrg, UIButton closeButton, boolean groupPolicy) {
        super(new BorderLayout(8, 10));
        this.closeButton = closeButton;
        this.groupPolicy = groupPolicy;
        String group = sessionGroup();
        if (groupPolicy) {
            selectedOrg = group;
            master.setText("集团总控");
            saveMaster.setText("保存集团总控");
        } else {
            BusinessUnitDefaultRefModel reference = new BusinessUnitDefaultRefModel();
            orgRef.setRefModel(reference);
            if (group != null) { reference.setPk_group(group); }
            orgRef.setMultiSelectedEnabled(false);
            orgRef.setMinimumSize(new Dimension(70, 26));
            orgRef.setPreferredSize(new Dimension(200, 26));
            orgRef.setMaximumSize(new Dimension(240, 26));
        }

        setBorder(BorderFactory.createEmptyBorder(12, 14, 10, 14));
        nodeFilter.setColumns(14);
        Dimension filterSize = nodeFilter.getPreferredSize();
        filterSize.height = Math.max(26, filterSize.height);
        nodeFilter.setMinimumSize(new Dimension(45, filterSize.height));
        nodeFilter.setPreferredSize(filterSize);
        nodeFilter.setMaximumSize(filterSize);
        sizeHeaderButton(query);
        sizeHeaderButton(saveMaster);
        sizeHeaderButton(save);
        UIPanel header = new UIPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.X_AXIS));
        if (!groupPolicy) {
            addHeaderControl(header, new UILabel("业务单元"));
            addHeaderControl(header, orgRef);
        }
        addHeaderControl(header, new UILabel("功能节点"));
        addHeaderControl(header, nodeFilter);
        addHeaderControl(header, query);
        addHeaderControl(header, master);
        addHeaderControl(header, saveMaster);
        addHeaderControl(header, save);
        header.add(Box.createHorizontalGlue());
        add(header, BorderLayout.NORTH);

        configureTable();
        add(new UIScrollPane(table), BorderLayout.CENTER);
        UIPanel footer = new UIPanel(new BorderLayout(8, 8));
        footer.add(status, BorderLayout.NORTH);
        footer.add(accessStatus, BorderLayout.CENTER);
        if (closeButton != null) {
            UIPanel buttons = new UIPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
            buttons.add(closeButton);
            footer.add(buttons, BorderLayout.SOUTH);
        }
        add(footer, BorderLayout.SOUTH);

        if (!groupPolicy) {
            orgRef.addValueChangedListener(new ValueChangedListener() {
                @Override public void valueChanged(ValueChangedEvent event) { selectionChanged(); }
            });
        }
        query.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent event) { queryPolicies(); }
        });
        save.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent event) { savePolicies(); }
        });
        saveMaster.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent event) { saveOrganizationMaster(); }
        });
        master.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent event) { updateEnabled(); }
        });
        tableModel.addTableModelListener(new TableModelListener() {
            @Override public void tableChanged(TableModelEvent event) { updateEnabled(); }
        });
        nodeSorter.addRowSorterListener(new RowSorterListener() {
            @Override public void sorterChanged(RowSorterEvent event) { updateEnabled(); }
        });
        nodeFilter.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { applyNodeFilter(); }
            @Override public void removeUpdate(DocumentEvent event) { applyNodeFilter(); }
            @Override public void changedUpdate(DocumentEvent event) { applyNodeFilter(); }
        });
        if (!groupPolicy && currentOrg != null) {
            addHierarchyListener(new HierarchyListener() {
                @Override public void hierarchyChanged(HierarchyEvent event) {
                    if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing()) {
                        removeHierarchyListener(this);
                        setReference(currentOrg);
                        selectionChanged();
                    }
                }
            });
        }
        status.setText(groupPolicy ? "请查询当前集团的基础档案策略。" : "请选择业务单元后查询策略。");
        updateEnabled();
    }

    private static void addHeaderControl(UIPanel header, Component control) {
        if (header.getComponentCount() > 0) { header.add(Box.createHorizontalStrut(4)); }
        if (control instanceof JComponent) { ((JComponent) control).setAlignmentY(Component.CENTER_ALIGNMENT); }
        header.add(control);
    }

    private static void sizeHeaderButton(UIButton button) {
        Dimension minimum = button.getMinimumSize();
        Dimension preferred = button.getPreferredSize();
        Dimension size = new Dimension(Math.max(minimum.width, preferred.width), Math.max(minimum.height, preferred.height));
        button.setMinimumSize(size);
        button.setPreferredSize(size);
        button.setMaximumSize(size);
    }

    private void configureTable() {
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setRowSorter(nodeSorter);
        table.setFillsViewportHeight(true);
        table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        int[] widths = {166, 185, 166, 145, 125, 108, 108, 108};
        for (int column = 0; column < widths.length; column++) {
            table.getColumnModel().getColumn(column).setPreferredWidth(widths[column]);
        }
        final TableCellRenderer checkbox = table.getDefaultRenderer(Boolean.class);
        table.setDefaultRenderer(Boolean.class, new TableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable source, Object value, boolean selected,
                    boolean focused, int row, int column) {
                Component component = checkbox.getTableCellRendererComponent(source, value, selected, focused, row, column);
                component.setEnabled(source.isCellEditable(row, column));
                return component;
            }
        });
    }

    private IPolicyService service() { return NCLocator.getInstance().lookup(IPolicyService.class); }

    /** 仅筛选已加载行；底层模型及按 scopeId 保存的未提交修改不改变。 */
    private void applyNodeFilter() {
        if (!stopEditing()) { status.setText("请先完成当前单元格编辑。"); return; }
        nodeSorter.setRowFilter(nodeNameFilter(nodeFilter.getText()));
        if (loaded != null && !busy) { status.setText("已查询 " + tableModel.getRowCount() + " 项操作，当前显示 " + table.getRowCount() + " 项。"); }
        updateEnabled();
    }

    static RowFilter<QdPolicyTableModel,Integer> nodeNameFilter(String text) {
        final String term = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        if (term.length() == 0) { return null; }
        return new RowFilter<QdPolicyTableModel,Integer>() {
            @Override public boolean include(Entry<? extends QdPolicyTableModel,? extends Integer> entry) {
                return entry.getStringValue(0).toLowerCase(Locale.ROOT).contains(term)
                    || entry.getStringValue(1).toLowerCase(Locale.ROOT).contains(term);
            }
        };
    }

    private String selectedPk() {
        if (groupPolicy) { return sessionGroup(); }
        String pk = orgRef.getRefPK();
        return pk == null || pk.length() == 0 ? null : pk;
    }

    private static String sessionGroup() {
        String group = InvocationInfoProxy.getInstance().getGroupId();
        return group == null || group.trim().isEmpty() ? null : group;
    }

    private void setReference(String org) {
        restoringSelection = true;
        try { orgRef.setPK(org); } finally { restoringSelection = false; }
    }

    private void selectionChanged() {
        if (restoringSelection || busy) { return; }
        String target = selectedPk();
        if (Objects.equals(selectedOrg, target)) { return; }
        if (!confirmDiscard("切换业务单元")) {
            setReference(selectedOrg);
            return;
        }
        selectedOrg = target;
        clearPolicy();
        status.setText("请选择业务单元后查询策略。");
    }

    private void queryPolicies() {
        if (busy || !hasSelectedOrganization() || !confirmDiscard("重新查询")) { return; }
        final String org = selectedOrg;
        clearPolicy();
        setBusy(true, groupPolicy ? "正在查询基础档案集团策略…" : "正在查询组织策略…");
        new NcClientWorker<OrganizationPolicyView>(this) {
            @Override protected OrganizationPolicyView callService() throws Exception {
                return groupPolicy ? service().queryGroupPolicy() : service().queryOrganizationPolicy(org);
            }
            @Override protected void completed(OrganizationPolicyView view) {
                if (view == null || !org.equals(view.getOrgId())) {
                    throw new IllegalStateException("查询返回的策略范围与当前范围不匹配。");
                }
                loaded = view;
                savedMaster = view.isMasterEnabled();
                controlVersion = view.getControlVersion();
                master.setSelected(savedMaster);
                tableModel.load(view.getRows());
                status.setText("已查询 " + tableModel.getRowCount() + " 项操作，当前显示 " + table.getRowCount() + " 项。");
            }
            @Override protected void failed(Throwable error) {
                status.setText("策略查询失败，请核对当前策略范围及查询权限。");
                super.failed(error);
            }
            @Override protected void settled() { setBusy(false, null); }
        }.execute();
    }

    private void savePolicies() {
        if (!canWrite() || !loaded.canSavePolicies() || !stopEditing() || !tableModel.isDirty()) { return; }
        final String org = loaded.getOrgId();
        final List<ButtonPolicyChange> changes = tableModel.changes();
        setBusy(true, "正在保存按钮策略…");
        new NcClientWorker<List<ButtonPolicyRow>>(this) {
            @Override protected List<ButtonPolicyRow> callService() throws Exception {
                return groupPolicy ? service().saveGroupPolicies(changes) : service().saveOrganizationPolicies(org, changes);
            }
            @Override protected void completed(List<ButtonPolicyRow> saved) {
                tableModel.acceptSaved(saved);
                status.setText("已保存 " + saved.size() + " 条按钮策略。");
            }
            @Override protected void failed(Throwable error) {
                needsReload = true;
                status.setText("策略保存结果待核对，请重新查询。");
                super.failed(error);
            }
            @Override protected void settled() { setBusy(false, null); }
        }.execute();
    }

    private void saveOrganizationMaster() {
        if (!canWrite() || !loaded.canSaveMaster() || !stopEditing() || !masterChanged()) { return; }
        final String org = loaded.getOrgId();
        final boolean enabled = master.isSelected();
        final long expected = controlVersion;
        setBusy(true, "正在保存总控…");
        new NcClientWorker<Long>(this) {
            @Override protected Long callService() throws Exception {
                return Long.valueOf(groupPolicy ? service().setGroupMaster(enabled, expected)
                        : service().setOrganizationMaster(org, enabled, expected));
            }
            @Override protected void completed(Long version) {
                if (version == null || version.longValue() <= expected) {
                    throw new IllegalStateException("保存返回的总控版本无效。");
                }
                savedMaster = enabled;
                controlVersion = version.longValue();
                status.setText((groupPolicy ? "集团总控" : "当前业务单元组织总控") + (enabled ? "已开启。" : "已关闭。"));
            }
            @Override protected void failed(Throwable error) {
                needsReload = true;
                status.setText("总控保存结果待核对，请重新查询。");
                super.failed(error);
            }
            @Override protected void settled() { setBusy(false, null); }
        }.execute();
    }

    private void clearPolicy() {
        loaded = null;
        needsReload = false;
        savedMaster = false;
        controlVersion = 0L;
        master.setSelected(false);
        tableModel.load(Collections.<ButtonPolicyRow>emptyList());
    }

    private boolean hasSelectedOrganization() {
        return selectedOrg != null && selectedOrg.equals(selectedPk());
    }

    private boolean canWrite() {
        return !busy && !needsReload && loaded != null && hasSelectedOrganization()
            && loaded.getOrgId().equals(selectedOrg);
    }

    private boolean stopEditing() { return !table.isEditing() || table.getCellEditor().stopCellEditing(); }
    private boolean masterChanged() { return master.isSelected() != savedMaster; }

    private boolean confirmDiscard(String operation) {
        return stopEditing() && ((!tableModel.isDirty() && !masterChanged())
            || MessageDialog.showYesNoDlg(this, "未保存的修改", "是否放弃未保存的修改并" + operation + "？") == UIDialog.ID_YES);
    }

    private void setBusy(boolean active, String message) {
        busy = active;
        if (message != null) { status.setText(message); }
        updateEnabled();
    }

    private void updateEnabled() {
        boolean writable = canWrite();
        boolean policyWritable = writable && loaded.canSavePolicies();
        boolean masterWritable = writable && loaded.canSaveMaster();
        orgRef.setEnabled(!busy);
        nodeFilter.setEnabled(!busy);
        query.setEnabled(!busy && hasSelectedOrganization());
        tableModel.setEditingAllowed(policyWritable);
        table.repaint();
        master.setEnabled(masterWritable);
        save.setEnabled(policyWritable && tableModel.isDirty());
        saveMaster.setEnabled(masterWritable && masterChanged());
        accessStatus.setText(accessMessage());
        if (closeButton != null) { closeButton.setEnabled(!busy); }
    }

    private String accessMessage() {
        if (loaded == null || busy) { return ""; }
        if (needsReload) { return "请重新查询当前策略后再修改。"; }
        StringBuilder message = new StringBuilder();
        if (!loaded.canSavePolicies()) { message.append("缺少策略保存权限（SAVE）。"); }
        if (!loaded.canSaveMaster()) { message.append("缺少组织总控权限（MASTER）。"); }
        if (!tableModel.hasConfigurableRows()) { message.append("当前没有完成接入验证的可配置按钮。"); }
        if (tableModel.isDirty()) {
            int visible = 0;
            for (int view = 0; view < table.getRowCount(); view++) {
                if (tableModel.isRowDirty(table.convertRowIndexToModel(view))) { visible++; }
            }
            message.append("待保存 ").append(tableModel.dirtyCount()).append(" 条，其中筛选隐藏 ")
                .append(tableModel.dirtyCount() - visible).append(" 条；保存策略将提交全部修改。");
        }
        return message.toString();
    }

    boolean canClose(String operation) {
        return !busy && confirmDiscard(operation);
    }
}
