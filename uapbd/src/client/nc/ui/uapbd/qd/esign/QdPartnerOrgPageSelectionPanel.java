package nc.ui.uapbd.qd.esign;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.PartnerOrgBatchPlan;

/** Read-only, lazy table of every physical page row included in one authorization. */
final class QdPartnerOrgPageSelectionPanel extends JPanel {
    private static final long serialVersionUID=1L;
    private final JTable table;
    QdPartnerOrgPageSelectionPanel(List<BusinessObjectRef> roots,PartnerOrgBatchPlan.Page[] pages) {
        if(roots==null || roots.isEmpty() || pages==null)throw new IllegalArgumentException("Page selection is incomplete");
        Map<String,String> codes=new HashMap<String,String>();
        for(BusinessObjectRef root:roots) if(root==null || root.getObjectPk()==null || codes.containsKey(root.getObjectPk()))
            throw new IllegalArgumentException("Page selection roots are invalid or duplicated");
        else codes.put(root.getObjectPk(),root.getBillCode());
        PartnerOrgBatchPlan.Page[] copy=pages.clone();Set<String> ids=new HashSet<String>();
        for(PartnerOrgBatchPlan.Page page:copy) if(page==null || !codes.containsKey(page.getRoot()) || page.getId()==null
                || page.getTarget()==null || !ids.add(page.getId()))
            throw new IllegalArgumentException("Page selection contains a foreign or duplicate row");
        setLayout(new BorderLayout(0,8));
        table=new JTable(new Rows(copy,codes));table.setCellSelectionEnabled(true);table.setAutoCreateRowSorter(false);
        table.setFillsViewportHeight(true);JScrollPane scroll=new JScrollPane(table);scroll.setPreferredSize(new Dimension(780,340));
        add(scroll,BorderLayout.CENTER);
        add(new JLabel("显示本批全部页签行；可选中单元格后使用 Ctrl+C 复制。计划后新增的行不包含在本批。"),BorderLayout.SOUTH);
    }
    JTable table() { return table; }
    private static final class Rows extends AbstractTableModel {
        private static final long serialVersionUID=1L;
        private static final String[] COLUMNS={"档案编码","档案主键","目标组织","页签主键"};
        private final PartnerOrgBatchPlan.Page[] pages;private final Map<String,String> codes;
        Rows(PartnerOrgBatchPlan.Page[] pages,Map<String,String> codes) { this.pages=pages;this.codes=codes; }
        public int getRowCount() { return pages.length; }
        public int getColumnCount() { return COLUMNS.length; }
        public String getColumnName(int column) { return COLUMNS[column]; }
        public Object getValueAt(int row,int column) {
            PartnerOrgBatchPlan.Page page=pages[row];
            switch(column) {
                case 0:return codes.get(page.getRoot());
                case 1:return page.getRoot();
                case 2:return page.getTarget();
                case 3:return page.getId();
                default:throw new IndexOutOfBoundsException();
            }
        }
    }
}
