package nc.ui.pu.m25.action;

import java.util.ArrayList;
import java.util.Arrays;

import nc.pu.m25.relevance.vo.RelevanceBVO;
import nc.pu.m25.relevance.vo.RelevanceHVO;
import nc.vo.pub.lang.UFDouble;

public final class InvoiceRelationAmountsTest {
    public static void main(String[] args) {
        RelevanceHVO[] selected = {
            invoice("61.00", detail("5.00", "35.00"), detail("2.00", "18.00")),
            invoice("39.00", detail("4.00", "36.00"))
        };

        InvoiceRelationAmounts equal = compare("100", "11", "89", selected);
        require(!equal.differs, "Selected invoices sum to the NC gross, tax and net");
        requireAmount("0", equal.grossDifference);
        requireAmount("0", equal.taxDifference);
        requireAmount("0", equal.netDifference);

        InvoiceRelationAmounts netOnly = compare("100", "11", "89.01", selected);
        require(netOnly.differs, "A net-only difference must select the original NO dialog");
        requireAmount("0.01", netOnly.netDifference);

        InvoiceRelationAmounts adjusted = compare("101.10", "10.50", "90.60", selected);
        require(adjusted.differs, "Difference amounts must select the original NO dialog");
        requireAmount("1.10", adjusted.grossDifference);
        requireAmount("-0.50", adjusted.taxDifference);
        requireAmount("1.60", adjusted.netDifference);
    }

    private static InvoiceRelationAmounts compare(String gross, String tax, String net, RelevanceHVO[] selected) {
        return InvoiceRelationAmounts.compare(new UFDouble(gross), new UFDouble(tax), new UFDouble(net), selected);
    }

    private static RelevanceHVO invoice(String gross, RelevanceBVO... details) {
        RelevanceHVO invoice = new RelevanceHVO();
        invoice.setJshj(gross);
        invoice.setDetails(new ArrayList<RelevanceBVO>(Arrays.asList(details)));
        return invoice;
    }

    private static RelevanceBVO detail(String tax, String net) {
        RelevanceBVO detail = new RelevanceBVO();
        detail.setSe(tax);
        detail.setBhsje(net);
        return detail;
    }

    private static void requireAmount(String expected, UFDouble actual) {
        require(actual.compareTo(new UFDouble(expected)) == 0, "Expected " + expected + " but got " + actual);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
