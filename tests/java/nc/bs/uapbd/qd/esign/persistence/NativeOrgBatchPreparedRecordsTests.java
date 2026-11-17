package nc.bs.uapbd.qd.esign.persistence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import nc.bs.uapbd.qd.esign.core.NativeOrgBatchPage;
import nc.bs.uapbd.qd.esign.core.NativeOrgBatchPreparedScope;
import nc.bs.uapbd.qd.esign.core.NativeOrgBatchSnapshotTests;
import nc.bs.uapbd.qd.esign.core.ObjectRelations;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.PartnerOrgBatchPlan;

/** Prepared page rows remain recoverable and distinguish an empty selection from missing evidence. */
public final class NativeOrgBatchPreparedRecordsTests {
    private static final String ID="0123456789abcdef0123456789abcdef";
    private NativeOrgBatchPreparedRecordsTests() { }
    public static void run() throws Exception {
        NativeOrgBatchSnapshotTests.ReceiptFixture fixture=NativeOrgBatchSnapshotTests.receiptFixture();
        Map<String,Object> request=preparing(fixture.request);
        List<Map<String,Object>> rows=NativeOrgBatchPreparedRecords.encode(request,fixture.objects,NativeOrgBatchPage.CUSTOMER_FINANCE,
            fixture.preparedEvidence);
        require(rows.size()==4 && "W".equals(rows.get(0).get("row_kind")) && "202611".equals(rows.get(0).get("archive_month")),
            "Prepared page header or records are missing");
        NativeOrgBatchPreparedScope.Evidence decoded=decode(fixture,request,rows);
        require(decoded.getPages().size()==3 && "P1".equals(decoded.getPages().get(0).getId())
            && "A".equals(decoded.getPages().get(0).getRoot()) && "T".equals(decoded.getPages().get(0).getTarget()),
            "Prepared page identity, archive or organization was lost");
        NativeOrgBatchPreparedScope.requireOutcome(ID,"G","ORG",ObjectRelations.restore(fixture.objects),decoded,fixture.evidence);
        try { decoded.getPages().clear();throw new AssertionError("Prepared evidence is mutable"); } catch(UnsupportedOperationException expected) { }
        List<Map<String,Object>> overlap=new ArrayList<Map<String,Object>>(rows);Collections.reverse(overlap);
        overlap.add(new HashMap<String,Object>(rows.get(0)));require(decode(fixture,request,overlap).getPages().size()==3,
            "Identical history overlap was not deduplicated");
        List<Map<String,Object>> missing=new ArrayList<Map<String,Object>>(rows);missing.remove(1);reject(fixture,request,missing);
        missing=new ArrayList<Map<String,Object>>(rows);missing.remove(0);reject(fixture,request,missing);
        List<Map<String,Object>> changed=new ArrayList<Map<String,Object>>(rows);Map<String,Object> bad=new HashMap<String,Object>(rows.get(1));
        bad.put("target_org","NO_ROWS");changed.set(1,bad);reject(fixture,request,changed);
        changed=new ArrayList<Map<String,Object>>(rows);bad=new HashMap<String,Object>(rows.get(1));
        bad.put("object_id",fixture.objects.get(1).get("object_id"));changed.set(1,bad);reject(fixture,request,changed);
        changed=new ArrayList<Map<String,Object>>(rows);bad=new HashMap<String,Object>(rows.get(0));
        bad.put("page_count",Integer.valueOf(2));changed.set(0,bad);reject(fixture,request,changed);
        changed=new ArrayList<Map<String,Object>>(rows);bad=new HashMap<String,Object>(rows.get(0));
        bad.put("event_time","2026-11-30 15:59:58");changed.set(0,bad);reject(fixture,request,changed);
        changed=new ArrayList<Map<String,Object>>(rows);bad=new HashMap<String,Object>(rows.get(1));
        bad.put("record_id","ffffffffffffffffffffffffffffffff");changed.add(bad);reject(fixture,request,changed);
        changed=new ArrayList<Map<String,Object>>(rows);bad=new HashMap<String,Object>(rows.get(1));
        bad.put("item_key","FOREIGN");changed.add(bad);reject(fixture,request,changed);
        changed=new ArrayList<Map<String,Object>>(rows);bad=new HashMap<String,Object>(rows.get(1));
        bad.put("row_kind","P");changed.set(1,bad);reject(fixture,request,changed);

        request.put("signature_state","CANCELLED");request.put("business_state","CANCELLED");request.put("finished_at","2026-12-01 00:00:00");
        require(decode(fixture,request,rows).getPages().size()==3,"Cancellation lost the signed selection");
        NativeOrgBatchSnapshotTests.ReceiptFixture empty=NativeOrgBatchSnapshotTests.emptyReceiptFixture();
        Map<String,Object> pending=preparing(empty.request);
        List<Map<String,Object>> emptyRows=NativeOrgBatchPreparedRecords.encode(pending,empty.objects,NativeOrgBatchPage.CUSTOMER_FINANCE,
            empty.preparedEvidence);
        require(emptyRows.size()==1 && "W".equals(emptyRows.get(0).get("row_kind")) && decode(empty,pending,emptyRows).getPages().isEmpty(),
            "A deliberate empty selection was not preserved");
        reject(empty,pending,Collections.<Map<String,Object>>emptyList());
        try { NativeOrgBatchPreparedScope.requireOutcome(ID,"G","ORG",ObjectRelations.restore(empty.objects),
                empty.preparedEvidence,fixture.evidence);throw new AssertionError("Unselected native page outcomes were accepted"); }
        catch(BusinessException expected) { }
        System.out.println("PASS NativeOrgBatchPreparedRecordsTests (prepared pages, empty marker and tamper rejection; no Oracle)");
    }
    private static Map<String,Object> preparing(Map<String,Object> original) {
        Map<String,Object> request=new HashMap<String,Object>(original);request.put("signature_state","PENDING");
        request.put("business_state","PENDING");request.put("execution_token",null);return request;
    }
    private static NativeOrgBatchPreparedScope.Evidence decode(NativeOrgBatchSnapshotTests.ReceiptFixture fixture,Map<String,Object> request,
            List<Map<String,Object>> rows) throws BusinessException {
        return NativeOrgBatchPreparedRecords.decode(request,fixture.objects,NativeOrgBatchPage.CUSTOMER_FINANCE,fixture.targetEvidence,rows);
    }
    private static void reject(NativeOrgBatchSnapshotTests.ReceiptFixture fixture,Map<String,Object> request,List<Map<String,Object>> rows)
            throws Exception {
        try { decode(fixture,request,rows);throw new AssertionError("Invalid prepared page evidence accepted"); }
        catch(BusinessException expected) { }
    }
    private static void require(boolean value,String message) { if(!value)throw new AssertionError(message); }
}
