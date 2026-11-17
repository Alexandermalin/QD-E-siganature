package nc.ui.uapbd.qd.esign;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.table.AbstractTableModel;
import nc.vo.uapbd.qd.esign.ArchiveBatchStatus;

/** Read-only details from one checked persisted receipt; the table keeps every object without paging it away. */
final class QdPartnerBatchRecoveryPanel extends JPanel {
    private static final long serialVersionUID=1L;
    private final JTable table;
    private JTable pageTable,targetTable,preparedTable;
    QdPartnerBatchRecoveryPanel(QdPartnerBatchRecovery.Summary summary) {
        if(summary==null) throw new IllegalArgumentException("A verified recovery result is required");
        setLayout(new BorderLayout(0,8));
        JTextArea heading=new JTextArea("以下是查询时的结果，执行中或未知状态可能随后变化。\n"+summary.describe());
        heading.setEditable(false);heading.setOpaque(false);heading.setLineWrap(true);heading.setWrapStyleWord(true);heading.setRows(4);
        add(heading,BorderLayout.NORTH);
        table=new JTable(new Rows(summary.units));table.setCellSelectionEnabled(true);table.setAutoCreateRowSorter(false);
        table.setFillsViewportHeight(true);
        JScrollPane scroll=new JScrollPane(table);scroll.setPreferredSize(new Dimension(850,360));
        if(summary.organization==null)add(scroll,BorderLayout.CENTER);
        else {
            JTabbedPane tabs=new JTabbedPane();tabs.addTab("档案结果",scroll);
            pageTable=new JTable(new Pages(summary.units,summary.organization.getPages()));pageTable.setCellSelectionEnabled(true);
            targetTable=new JTable(new Targets(summary.organization.getTargets()));targetTable.setCellSelectionEnabled(true);
            if(summary.organization.hasPreparedSelection()) {
                preparedTable=new JTable(new PreparedPages(summary.units,summary.organization.getPreparedPages()));
                preparedTable.setCellSelectionEnabled(true);tabs.addTab("签名前固定页签",new JScrollPane(preparedTable));
            }
            tabs.addTab("组织页签明细",new JScrollPane(pageTable));tabs.addTab("全部目标组织",new JScrollPane(targetTable));
            add(tabs,BorderLayout.CENTER);
        }
        add(new JLabel("显示全部档案；可选择表格单元格后使用 Ctrl+C 复制，未结束时可刷新状态。"),BorderLayout.SOUTH);
    }
    JTable table() { return table; }
    JTable pageTable() { return pageTable; }
    JTable targetTable() { return targetTable; }
    JTable preparedTable() { return preparedTable; }
    private static final class Rows extends AbstractTableModel {
        private static final long serialVersionUID=1L;
        private final List<ArchiveBatchStatus.Unit> units;
        private static final String[] COLUMNS={"档案编码","档案主键","结果","所属组织","操作组织","说明"};
        Rows(List<ArchiveBatchStatus.Unit> units) { this.units=units; }
        public int getRowCount() { return units.size(); }
        public int getColumnCount() { return COLUMNS.length; }
        public String getColumnName(int column) { return COLUMNS[column]; }
        public Object getValueAt(int row,int column) {
            ArchiveBatchStatus.Unit unit=units.get(row);
            switch(column) {
                case 0:return unit.getCode();
                case 1:return unit.getObjectPk();
                case 2:return label(unit.getState());
                case 3:return unit.getOwnerOrg();
                case 4:return unit.getTargetOrg();
                case 5:return explanation(unit.getErrorCode());
                default:throw new IndexOutOfBoundsException();
            }
        }
        private static String label(String state) {
            return "SUCCESS".equals(state)?"成功":"FAILED".equals(state)?"失败":"PARTIAL".equals(state)?"部分成功":"UNCHANGED".equals(state)?"未变化"
                :"PENDING".equals(state)?"待定":"UNKNOWN".equals(state)?"未知":state;
        }
        private static String explanation(String code) {
            if("NATIVE_REJECTED".equals(code)) return "未通过原生校验";
            if("PARTIAL_NATIVE_REJECTED".equals(code)) return "部分组织页签未通过原生检查";
            if("BATCH_ROLLED_BACK".equals(code)) return "业务执行失败，已标记回滚";
            if("BATCH_OUTCOME_UNKNOWN".equals(code)) return "实际结果需要核对";
            return code;
        }
    }
    private static final class Pages extends AbstractTableModel {
        private static final long serialVersionUID=1L;
        private final List<ArchiveBatchStatus.Page> pages;private final java.util.Map<String,String> codes=new java.util.HashMap<String,String>();
        private static final String[] COLUMNS={"档案编码","页签主键","目标组织","结果","说明"};
        Pages(List<ArchiveBatchStatus.Unit> units,List<ArchiveBatchStatus.Page> pages) {
            this.pages=pages;for(ArchiveBatchStatus.Unit unit:units)codes.put(unit.getClientId(),unit.getCode());
        }
        public int getRowCount() { return pages.size(); }
        public int getColumnCount() { return COLUMNS.length; }
        public String getColumnName(int column) { return COLUMNS[column]; }
        public Object getValueAt(int row,int column) {
            ArchiveBatchStatus.Page page=pages.get(row);
            switch(column) {
                case 0:return codes.get(page.getClientId());
                case 1:return page.getPagePk();
                case 2:return page.getTargetOrg();
                case 3:return Rows.label(page.getState());
                case 4:return "PERMISSION".equals(page.getFailureStage())?"原生权限检查未通过"
                    :"VALIDATION".equals(page.getFailureStage())?"原生校验未通过"
                    :"BEFORE_EVENT".equals(page.getFailureStage())?"原生更新前检查未通过":null;
                default:throw new IndexOutOfBoundsException();
            }
        }
    }
    private static final class PreparedPages extends AbstractTableModel {
        private static final long serialVersionUID=1L;
        private static final String[] COLUMNS={"档案编码","档案主键","目标组织","页签主键"};
        private final List<nc.vo.uapbd.qd.esign.PartnerOrgBatchPlan.Page> pages;
        private final java.util.Map<String,String> codes=new java.util.HashMap<String,String>();
        PreparedPages(List<ArchiveBatchStatus.Unit> units,List<nc.vo.uapbd.qd.esign.PartnerOrgBatchPlan.Page> pages) {
            this.pages=pages;for(ArchiveBatchStatus.Unit unit:units)codes.put(unit.getObjectPk(),unit.getCode());
        }
        public int getRowCount() { return pages.size(); }
        public int getColumnCount() { return COLUMNS.length; }
        public String getColumnName(int column) { return COLUMNS[column]; }
        public Object getValueAt(int row,int column) {
            nc.vo.uapbd.qd.esign.PartnerOrgBatchPlan.Page page=pages.get(row);
            switch(column) {
                case 0:return codes.get(page.getRoot());case 1:return page.getRoot();
                case 2:return page.getTarget();case 3:return page.getId();default:throw new IndexOutOfBoundsException();
            }
        }
    }
    private static final class Targets extends AbstractTableModel {
        private static final long serialVersionUID=1L;private final List<String> targets;
        Targets(List<String> targets) { this.targets=targets; }
        public int getRowCount() { return targets.size(); }
        public int getColumnCount() { return 1; }
        public String getColumnName(int column) { return "目标组织"; }
        public Object getValueAt(int row,int column) { return targets.get(row); }
    }
}
