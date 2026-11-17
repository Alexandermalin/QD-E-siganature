package nc.bs.uapbd.qd.esign.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import nc.vo.bd.cust.CustomerVO;
import nc.vo.bd.errorlog.ErrLogResult;
import nc.vo.bd.errorlog.ErrLogReturnValue;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;

/** Missing NC result objects are distinguished from known no-ops and cannot hide physical changes. */
public final class NativeBatchUpdateOutcomeTests {
    private NativeBatchUpdateOutcomeTests() { }
    public static void run() throws Exception {
        List<BusinessObjectRef> refs=Arrays.asList(ref("a","A"),ref("b","B"),ref("c","C"));
        Map<String,List<SnapshotRow>> before=rows("1","1","1"),after=rows("2","1","1");Set<String> unchanged=new HashSet<String>(Arrays.asList("B"));
        Map<String,String> excluded=new HashMap<String,String>();excluded.put("C","VALIDATION");
        List<NativeBatchUpdateOutcome.Unit> result=NativeBatchUpdateOutcome.reconcile(refs,unchanged,excluded,CustomerVO.class,
            nativeResult(3,1,customer("A")),before,after);
        require("SUCCESS".equals(result.get(0).getState()) && "UNCHANGED".equals(result.get(1).getState())
            && "FAILED".equals(result.get(2).getState()),"Partial native batch was not reconciled per archive");
        Set<String> allUnchanged=new HashSet<String>(Arrays.asList("A","B","C"));
        List<NativeBatchUpdateOutcome.Unit> noops=NativeBatchUpdateOutcome.reconcile(refs,allUnchanged,Collections.<String,String>emptyMap(),CustomerVO.class,
            nativeResult(3,0),before,before);
        for(NativeBatchUpdateOutcome.Unit unit:noops) require("UNCHANGED".equals(unit.getState()),"Unchanged batch was not recognized");
        reject(refs,unchanged,excluded,nativeResult(2,1,customer("A")),before,after);
        reject(refs,unchanged,Collections.<String,String>emptyMap(),nativeResult(3,1,customer("A")),before,after);
        reject(refs,unchanged,excluded,nativeResult(3,1,customer("A"),customer("A")),before,after);
        reject(refs,unchanged,excluded,nativeResult(3,1,customer("A"),customer("X")),before,after);
        reject(refs,unchanged,excluded,nativeResult(3,1,customer("A"),customer("B")),before,after);
        reject(refs,unchanged,excluded,nativeResult(3,1,customer("A"),customer("C")),before,after);
        reject(refs,unchanged,excluded,nativeResult(3,1,customer("A")),before,rows("2","1","2"));
        reject(refs,Collections.<String>emptySet(),Collections.<String,String>emptyMap(),nativeResult(3,1,customer("A"),customer("B"),customer("C")),before,after);
        Map<String,List<SnapshotRow>> missing=rows("1","1","1");missing.remove("c");
        reject(refs,unchanged,excluded,nativeResult(3,1,customer("A")),missing,after);
        Map<String,String> unknown=new HashMap<String,String>();unknown.put("C","guess");
        reject(refs,unchanged,unknown,nativeResult(3,1,customer("A")),before,after);
        System.out.println("PASS NativeBatchUpdateOutcomeTests (native partial, unchanged and physical side effects; no NC or Oracle)");
    }
    private static BusinessObjectRef ref(String id,String pk) { return new BusinessObjectRef(id,"BD_CUSTOMER",pk,pk,null); }
    private static CustomerVO customer(String pk) { CustomerVO result=new CustomerVO();result.setPk_customer(pk);return result; }
    private static ErrLogReturnValue nativeResult(int total,int errors,SuperVO... result) {
        return new ErrLogReturnValue(new ErrLogResult(null,errors,null),result,total);
    }
    private static Map<String,List<SnapshotRow>> rows(String a,String b,String c) {
        Map<String,List<SnapshotRow>> result=new HashMap<String,List<SnapshotRow>>();
        result.put("a",Collections.singletonList(row("A",a)));result.put("b",Collections.singletonList(row("B",b)));
        result.put("c",Collections.singletonList(row("C",c)));return result;
    }
    private static SnapshotRow row(String pk,String value) {
        Map<String,String> fields=new HashMap<String,String>();fields.put("value",value);
        return new SnapshotRow("bd_customer",pk,null,fields,Collections.<String,String>emptyMap(),Collections.<String,String>emptyMap(),true);
    }
    private static void reject(List<BusinessObjectRef> refs,Set<String> unchanged,Map<String,String> excluded,ErrLogReturnValue result,
            Map<String,List<SnapshotRow>> before,Map<String,List<SnapshotRow>> after) throws Exception {
        try { NativeBatchUpdateOutcome.reconcile(refs,unchanged,excluded,CustomerVO.class,result,before,after);throw new AssertionError("Ambiguous batch outcome accepted"); }
        catch(BusinessException expected) { }
    }
    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
}
