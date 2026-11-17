package nc.bs.uapbd.qd.esign.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import nc.vo.bd.cust.CustomerVO;
import nc.vo.pub.BusinessException;

/** Proven removals are recorded by identity; native error-message text is never parsed as an object result. */
public final class NativeBatchFailureTrackerTests {
    private NativeBatchFailureTrackerTests() { }
    public static void run() throws Exception {
        NativeBatchFailureTracker tracker=new NativeBatchFailureTracker();
        List<CustomerVO> initial=new ArrayList<CustomerVO>(Arrays.asList(row("A"),row("B"),row("C")));
        Set<String> input=NativeBatchFailureTracker.snapshot(initial);
        initial.remove(1);tracker.filtered("PERMISSION",input,initial);
        Set<String> validated=NativeBatchFailureTracker.snapshot(initial);
        tracker.filtered("VALIDATION",validated,Collections.singletonList(row("A")));
        Map<String,String> excluded=tracker.excluded();
        require(excluded.size()==2 && "PERMISSION".equals(excluded.get("B")) && "VALIDATION".equals(excluded.get("C")),
            "Native filtered objects lost their original identity or stage");
        try { excluded.put("X","VALIDATION");throw new AssertionError("Tracker returned a mutable result"); }
        catch(UnsupportedOperationException expected) { }
        reject(tracker,"BEFORE_EVENT",NativeBatchFailureTracker.snapshot(Arrays.asList(row("A"),row("B"))),Collections.singletonList(row("A")));
        reject(tracker,"VALIDATION",NativeBatchFailureTracker.snapshot(Collections.singletonList(row("A"))),Arrays.asList(row("A"),row("X")));
        reject(tracker,"GUESS",NativeBatchFailureTracker.snapshot(Collections.singletonList(row("A"))),Collections.singletonList(row("A")));
        try { NativeBatchFailureTracker.snapshot(Arrays.asList(row("A"),row("A")));throw new AssertionError("Duplicate input accepted"); }
        catch(BusinessException expected) { }
        require(tracker.excluded().equals(excluded),"A rejected stage changed the tracked result");
        System.out.println("PASS NativeBatchFailureTrackerTests (explicit native validation removals; no NC or Oracle)");
    }
    private static CustomerVO row(String pk) { CustomerVO result=new CustomerVO();result.setPk_customer(pk);return result; }
    private static void reject(NativeBatchFailureTracker tracker,String stage,Set<String> before,List<CustomerVO> after) throws Exception {
        try { tracker.filtered(stage,before,after);throw new AssertionError("Invalid native validation stage accepted"); }
        catch(BusinessException expected) { }
    }
    private static void require(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
}
