package nc.bs.uapbd.qd.esign.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.ArchiveBatchResult;
import nc.vo.uapbd.qd.esign.ArchiveBatchUpdateResult;
import nc.vo.bd.errorlog.ErrLogResult;
import nc.vo.bd.errorlog.ErrLogReturnValue;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;

/** Batch outcome proof is independent of any assumed native transaction. */
public final class ArchiveBatchReceiptTests {
    private ArchiveBatchReceiptTests() { }
    public static void run() throws Exception {
        List<BusinessObjectRef> refs=Arrays.asList(ref("a","A"),ref("b","B"),ref("c","C"));
        ArchiveBatchResult result=result(unit("a","A","SUCCESS"),unit("b","B","FAILED"),unit("c","C","UNCHANGED"));
        ArchiveBatchReceipt receipt=ArchiveBatchReceipt.verify(refs,result);
        require("PARTIAL".equals(receipt.getState()) && "UNCHANGED".equals(receipt.get("c").getState()),"Mixed native result is not partial");
        require("SUCCESS".equals(ArchiveBatchReceipt.verify(refs,result(unit("a","A","UNCHANGED"),unit("b","B","UNCHANGED"),unit("c","C","UNCHANGED"))).getState()),
            "A wholly unchanged batch must not imply a failed native call");
        require("FAILED".equals(ArchiveBatchReceipt.verify(refs,result(unit("a","A","UNCHANGED"),unit("b","B","FAILED"),unit("c","C","UNCHANGED"))).getState()),
            "No successfully written row must not be reported as partial success");
        ErrLogReturnValue nativeUpdate=new ErrLogReturnValue(new ErrLogResult(null,1,null),null,3);
        ArchiveBatchUpdateResult updated=new ArchiveBatchUpdateResult(nativeUpdate,result.getUnits());
        require("PARTIAL".equals(ArchiveBatchReceipt.verifyUpdate(refs,updated).getState()),"Quick-batch receipt lost per-object outcomes");
        try { ArchiveBatchReceipt.verifyUpdate(refs,new ArchiveBatchUpdateResult(nativeUpdate,Collections.singletonList(unit("a","A","SUCCESS"))));
            throw new AssertionError("Incomplete quick-batch receipt accepted"); }
        catch(BusinessException expected) { }
        reject(refs,result(unit("a","A","SUCCESS")));
        reject(refs,result(unit("a","A","SUCCESS"),unit("a","A","FAILED"),unit("c","C","UNCHANGED")));
        reject(refs,result(unit("a","FORGED","SUCCESS"),unit("b","B","FAILED"),unit("c","C","UNCHANGED")));
        reject(refs,result(unit("a","A","SUCCESS"),unit("b","B","UNKNOWN"),unit("c","C","UNCHANGED")));
        organizationReceipt();
        System.out.println("PASS ArchiveBatchReceiptTests (exact per-row outcomes; no NC transaction)");
    }
    private static void organizationReceipt() throws Exception {
        NativeOrgBatchSnapshotTests.ReceiptFixture fixture=NativeOrgBatchSnapshotTests.receiptFixture();
        List<BusinessObjectRef> refs=ObjectRelations.restore(fixture.objects);
        String id="0123456789abcdef0123456789abcdef";
        ArchiveBatchReceipt receipt=ArchiveBatchReceipt.verifyOrganization(id,"G","ORG",NativeOrgBatchPage.CUSTOMER_FINANCE,
            refs,fixture.evidence);
        require("PARTIAL".equals(receipt.getState()) && "PARTIAL".equals(receipt.get(refs.get(0).getClientObjectId()).getState()),
            "A partially written archive did not contribute both success and failure");
        try { ArchiveBatchReceipt.verifyOrganization("ffffffffffffffffffffffffffffffff","G","ORG",NativeOrgBatchPage.CUSTOMER_FINANCE,
                refs,fixture.evidence);throw new AssertionError("Another request reused an organization receipt"); }
        catch(BusinessException expected) { }
        try { ArchiveBatchReceipt.verifyOrganization(id,"G","ORG",NativeOrgBatchPage.CUSTOMER_SALE,refs,fixture.evidence);
            throw new AssertionError("Another page reused an organization receipt"); } catch(BusinessException expected) { }
        try { ArchiveBatchReceipt.verifyOrganization(id,"G","ORG",NativeOrgBatchPage.CUSTOMER_FINANCE,
                refs.subList(1,refs.size()),fixture.evidence);throw new AssertionError("An incomplete archive set reused the receipt"); }
        catch(BusinessException expected) { }
        reject(refs,result(unit(refs.get(0).getClientObjectId(),refs.get(0).getObjectPk(),"PARTIAL"),
            unit(refs.get(1).getClientObjectId(),refs.get(1).getObjectPk(),"UNCHANGED"),
            unit(refs.get(2).getClientObjectId(),refs.get(2).getObjectPk(),"UNCHANGED")));
    }
    private static BusinessObjectRef ref(String id,String pk) { return new BusinessObjectRef(id,"BD_CUSTOMER",pk,null,null); }
    private static ArchiveBatchResult.Unit unit(String id,String pk,String state) { return new ArchiveBatchResult.Unit(id,pk,state); }
    private static ArchiveBatchResult result(ArchiveBatchResult.Unit... units) { return new ArchiveBatchResult(null,Arrays.asList(units)); }
    private static void reject(List<BusinessObjectRef> refs,ArchiveBatchResult result) throws Exception {
        try { ArchiveBatchReceipt.verify(refs,result);throw new AssertionError("Invalid batch receipt accepted"); }
        catch(BusinessException expected) { }
    }
    private static void require(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
}
