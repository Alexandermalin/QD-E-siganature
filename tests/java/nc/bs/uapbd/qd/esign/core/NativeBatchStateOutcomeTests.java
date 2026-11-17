package nc.bs.uapbd.qd.esign.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import nc.bs.bd.service.ErrLogElement;
import nc.bs.bd.service.ValueObjWithErrLog;
import nc.vo.bd.cust.finance.CustFinanceVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;

/** Native partial responses, including the product's omitted already-in-state rows. */
public final class NativeBatchStateOutcomeTests {
    private NativeBatchStateOutcomeTests() { }
    public static void run() throws Exception {
        List<BusinessObjectRef> objects=Arrays.asList(ref("a","A"),ref("b","B"),ref("c","C"));
        ErrLogElement failure=new ErrLogElement();failure.setErrPK("B");failure.setErrReason("native validation failed");
        ValueObjWithErrLog nativeResult=result(new SuperVO[]{row("A")},failure);
        List<NativeBatchStateOutcome.Unit> units=NativeBatchStateOutcome.reconcile(objects,Collections.singleton("C"),nativeResult);
        require("SUCCESS".equals(units.get(0).getState()) && "FAILED".equals(units.get(1).getState())
            && "UNCHANGED".equals(units.get(2).getState()),"Mixed native results must be individually classified");
        expectRejected(objects,Collections.<String>emptySet(),nativeResult);
        expectRejected(objects,Collections.singleton("C"),result(new SuperVO[]{row("FOREIGN")},failure));
        ErrLogElement duplicate=new ErrLogElement();duplicate.setErrPK("A");
        expectRejected(objects,Collections.singleton("C"),result(new SuperVO[]{row("A")},duplicate));
        ErrLogElement missingPk=new ErrLogElement();
        expectRejected(objects,Collections.singleton("C"),result(new SuperVO[]{row("A")},missingPk));
        expectRejected(objects,new HashSet<String>(Arrays.asList("A","C")),nativeResult);
        System.out.println("PASS NativeBatchStateOutcomeTests (partial, unchanged and ambiguous native results; no NC transaction)");
    }
    private static ValueObjWithErrLog result(SuperVO[] values,ErrLogElement error) {
        ArrayList<ErrLogElement> errors=new ArrayList<ErrLogElement>();errors.add(error);
        return new ValueObjWithErrLog(values,errors);
    }
    private static BusinessObjectRef ref(String id,String pk) { return new BusinessObjectRef(id,"BD_CUSTOMER",pk,null,null); }
    private static SuperVO row(final String pk) {
        return new CustFinanceVO() { @Override public String getPrimaryKey() { return pk; }
            @Override public boolean equals(Object value) { return this==value; }
            @Override public int hashCode() { return System.identityHashCode(this); } };
    }
    private static void expectRejected(List<BusinessObjectRef> objects,java.util.Set<String> already,ValueObjWithErrLog result) throws Exception {
        try { NativeBatchStateOutcome.reconcile(objects,already,result);throw new AssertionError("Ambiguous native batch result was accepted"); }
        catch(BusinessException expected) { }
    }
    private static void require(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
}
