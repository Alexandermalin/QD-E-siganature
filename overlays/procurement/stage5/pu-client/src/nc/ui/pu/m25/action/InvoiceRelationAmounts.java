package nc.ui.pu.m25.action;

import nc.pu.m25.relevance.vo.RelevanceBVO;
import nc.pu.m25.relevance.vo.RelevanceHVO;
import nc.vo.pub.lang.UFDouble;

/** Compares the NC amounts with the original selection used by the confirmation dialog. */
final class InvoiceRelationAmounts {
    final UFDouble grossDifference;
    final UFDouble taxDifference;
    final UFDouble netDifference;
    final boolean differs;

    private InvoiceRelationAmounts(UFDouble grossDifference, UFDouble taxDifference, UFDouble netDifference,
            boolean differs) {
        this.grossDifference = grossDifference;
        this.taxDifference = taxDifference;
        this.netDifference = netDifference;
        this.differs = differs;
    }

    static InvoiceRelationAmounts compare(UFDouble gross, UFDouble tax, UFDouble net, RelevanceHVO[] selected) {
        UFDouble selectedGross = UFDouble.ZERO_DBL;
        UFDouble selectedTax = UFDouble.ZERO_DBL;
        UFDouble selectedNet = UFDouble.ZERO_DBL;
        for (RelevanceHVO invoice : selected) {
            selectedGross = selectedGross.add(new UFDouble(invoice.getJshj()));
            for (RelevanceBVO detail : invoice.getDetails()) {
                selectedTax = selectedTax.add(new UFDouble(detail.getSe()));
                selectedNet = selectedNet.add(new UFDouble(detail.getBhsje()));
            }
        }
        return new InvoiceRelationAmounts(gross.sub(selectedGross), tax.sub(selectedTax), net.sub(selectedNet),
                gross.compareTo(selectedGross) != 0 || tax.compareTo(selectedTax) != 0
                        || net.compareTo(selectedNet) != 0);
    }
}
