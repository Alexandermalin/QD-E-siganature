package nc.impl.pu.qd.esign;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import nc.itf.uapbd.qd.esign.IInvoiceRelationJournal;
import nc.vo.pub.BusinessException;
import nc.vo.pub.lang.UFDouble;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;

public final class InvoiceRelationOperationAdapterTest {
    public static void main(String[] args) throws Exception {
        trace("A;B", "EXTERNAL_ACCEPTED", false, false,
                "clear", "start", "external", "EXTERNAL_ACCEPTED", "local", "finish");
        trace("A", "EXTERNAL_REJECTED", false, true, "clear", "start", "external", "EXTERNAL_REJECTED");
        trace("A", null, false, true, "clear", "start", "external", "EXTERNAL_UNKNOWN");
        trace("A", "EXTERNAL_ACCEPTED", true, true, "clear", "start", "external", "EXTERNAL_ACCEPTED", "local");
        trace("", null, false, false, "clear", "local");
        if (InvoiceRelationNative.headerParams(null, "unlinked", null, UFDouble.ZERO_DBL, "I").getCountParams() != 5) {
            throw new AssertionError("cancel relation must bind both cleared NC link fields");
        }
        check("EXTERNAL_ACCEPTED", "{\"result\":[{\"fphm\":\"A\",\"code\":\"E000\"},{\"fphm\":\"B\",\"code\":\"E000\"}]}");
        check("EXTERNAL_REJECTED", "{\"result\":[{\"code\":\"E001\"},{\"code\":\"E001\"}]}");
        check("EXTERNAL_UNKNOWN", "{\"result\":[{\"code\":\"E001\"},{\"code\":\"E000\"}]}");
        check("EXTERNAL_UNKNOWN", "{\"result\":[{\"fphm\":\"A\",\"code\":\"E000\"},{\"fphm\":\"C\",\"code\":\"E000\"}]}");
        check("EXTERNAL_UNKNOWN", "{\"result\":[]}");
        check("EXTERNAL_UNKNOWN", "network error");
    }

    private static void check(String phase, String response) {
        if (!phase.equals(InvoiceRelationExternal.classify(response, new String[] {"A", "B"}))) {
            throw new AssertionError("incorrect external receipt classification for " + response);
        }
    }

    private static void trace(String numbers, final String phase, final boolean localFailure, boolean expectedFailure,
            String... expected) throws Exception {
        final List<String> events = new ArrayList<String>();
        IInvoiceRelationJournal journal = new IInvoiceRelationJournal() {
            @Override public void assertClear(String invoicePk) { events.add("clear"); }
            @Override public void start_RequiresNew(OperationContext context, String invoicePk, String numbers) {
                events.add("start");
            }
            @Override public void checkpoint_RequiresNew(String id, String value) { events.add(value); }
            @Override public void finish(String id) { events.add("finish"); }
        };
        BusinessObjectRef ref = new BusinessObjectRef("I:hash", "PU_PURCHASE_INVOICE", "I", "code", null);
        OperationContext scope = new OperationContext("test-request", "B", "G", "O", "40041000", "Invoice",
                "RelevanceInvoice", "Relate", "RELATE_INVOICE", null, Collections.singletonList(ref));
        try {
            String result = InvoiceRelationOperationAdapter.executeTracked(scope, "I", numbers, journal,
                    new InvoiceRelationOperationAdapter.NativeCall() {
                        @Override public String external() throws Exception {
                            events.add("external");
                            if (phase == null) throw new IOException("no response");
                            return phase;
                        }
                        @Override public void local() throws BusinessException {
                            events.add("local");
                            if (localFailure) throw new BusinessException("failure");
                        }
                    });
            if (expectedFailure || !"I".equals(result)) throw new AssertionError("wrong result");
        } catch (BusinessException ex) {
            if (!expectedFailure || !ex.getMessage().contains("test-request")) throw ex;
        }
        if (!events.equals(Arrays.asList(expected))) throw new AssertionError("wrong execution sequence: " + events);
    }
}
