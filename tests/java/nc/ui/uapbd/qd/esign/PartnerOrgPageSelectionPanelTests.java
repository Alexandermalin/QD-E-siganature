package nc.ui.uapbd.qd.esign;

import java.util.Arrays;
import java.util.Collections;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.PartnerOrgBatchPlan;

/** The signer can inspect every fixed page and its archive/target without opening an NC session. */
public final class PartnerOrgPageSelectionPanelTests {
    private PartnerOrgPageSelectionPanelTests() { }
    public static void run() {
        BusinessObjectRef a=new BusinessObjectRef("a","BD_CUSTOMER","A","A-code",null);
        BusinessObjectRef b=new BusinessObjectRef("b","BD_CUSTOMER","B","B-code",null);
        PartnerOrgBatchPlan.Page[] pages={new PartnerOrgBatchPlan.Page("P1","A","T1"),
            new PartnerOrgBatchPlan.Page("P2","B","T2")};
        QdPartnerOrgPageSelectionPanel panel=new QdPartnerOrgPageSelectionPanel(Arrays.asList(a,b),pages);
        pages[0]=new PartnerOrgBatchPlan.Page("CHANGED","A","T1");
        require(panel.table().getRowCount()==2 && "A-code".equals(panel.table().getValueAt(0,0))
            && "A".equals(panel.table().getValueAt(0,1)) && "T1".equals(panel.table().getValueAt(0,2))
            && "P1".equals(panel.table().getValueAt(0,3)) && "B-code".equals(panel.table().getValueAt(1,0)),
            "The review table lost page identities or their archive/target mapping");
        require(new QdPartnerOrgPageSelectionPanel(Collections.singletonList(a),new PartnerOrgBatchPlan.Page[0])
            .table().getRowCount()==0,"An empty planned slice could not be reviewed");
        try { new QdPartnerOrgPageSelectionPanel(Collections.singletonList(a),
                new PartnerOrgBatchPlan.Page[]{new PartnerOrgBatchPlan.Page("P","B","T")});
            throw new AssertionError("A page outside the authorized roots was displayed"); } catch(IllegalArgumentException expected) { }
        try { new QdPartnerOrgPageSelectionPanel(Collections.singletonList(a),
                new PartnerOrgBatchPlan.Page[]{new PartnerOrgBatchPlan.Page("P","A","T"),new PartnerOrgBatchPlan.Page("P","A","T")});
            throw new AssertionError("Duplicate page identities were displayed"); } catch(IllegalArgumentException expected) { }
        System.out.println("PASS PartnerOrgPageSelectionPanelTests (complete page review and immutable selection; no NC session)");
    }
    private static void require(boolean value,String message) { if(!value)throw new AssertionError(message); }
}
