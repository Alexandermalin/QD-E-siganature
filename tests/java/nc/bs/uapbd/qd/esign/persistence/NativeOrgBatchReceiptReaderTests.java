package nc.bs.uapbd.qd.esign.persistence;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import nc.bs.dao.DAOException;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.bs.uapbd.qd.esign.core.NativeOrgBatchPage;
import nc.bs.uapbd.qd.esign.core.NativeOrgBatchSnapshotTests;
import nc.jdbc.framework.SQLParameter;
import nc.vo.pub.BusinessException;

/** Cross-month read behavior with explicit store substitutes; no NC authorization or Oracle is invoked. */
public final class NativeOrgBatchReceiptReaderTests {
    private static final String EVENT="2026-11-30 16:01:01";
    private NativeOrgBatchReceiptReaderTests() { }
    public static void run() throws Exception {
        Store store=new Store();NativeOrgBatchReceiptReader.Result result=read(store);
        require(result.getPage()==NativeOrgBatchPage.CUSTOMER_FINANCE && result.getEvidence().getPages().size()==3
            && result.getEvidence().getTargets().equals(Arrays.asList("NO_ROWS","T")) && store.historyReads==2,
            "Receipt was not restored across online and monthly history");
        store=new Store();store.open=true;reject(store);
        store=new Store();store.duplicateLocator=true;reject(store);
        store=new Store();store.missingHistory=true;reject(store);
        store=new Store();store.directories.clear();reject(store);
        store=new Store();store.available=false;reject(store);
        store=new Store();store.fixture.request.put("finished_at",null);reject(store);
        store=new Store();store.online.clear();store.historyScope.clear();store.historyOutcome.clear();reject(store);
        store=new Store();store.online.get(0).put("archive_month","202610");reject(store);
        store=new Store();store.historyOutcome.get(0).put("archive_month","202611");reject(store);
        store=new Store();store.historyOutcome.get(0).put("tab_code","sale_info");reject(store);
        store=new Store();store.historyOutcome.get(0).put("request_id","ffffffffffffffffffffffffffffffff");reject(store);
        store=new Store();Map<String,Object> conflicting=new HashMap<String,Object>(store.online.get(2));
        conflicting.put("item_key","OTHER");store.historyOutcome.add(conflicting);reject(store);
        store=new Store();conflicting=new HashMap<String,Object>(store.online.get(0));
        conflicting.put("row_kind","T");store.historyScope.add(conflicting);reject(store);
        store=new Store();store.historyScope.clear();store.online.remove(0);store.online.remove(0);reject(store);
        availableReader();
        preparedReader();
        targetReader();
        System.out.println("PASS NativeOrgBatchReceiptReaderTests (online/history overlap and locator checks; no NC or Oracle)");
    }
    private static void preparedReader() throws Exception {
        Store store=new Store();addPrepared(store);
        NativeOrgBatchReceiptReader.Result result=read(store);
        require(result.getSelection()!=null && result.getSelection().getPages().size()==3 && result.getEvidence().getPages().size()==3,
            "Prepared pages were not recovered and matched to the business result");
        store=new Store();addPrepared(store);
        for(java.util.Iterator<Map<String,Object>> it=store.historyScope.iterator();it.hasNext();)if("V".equals(it.next().get("row_kind"))) {
            it.remove();break;
        }
        reject(store);
        store=new Store();addPrepared(store);store.open=true;store.fixture.request.put("business_state","PENDING");
        store.fixture.request.put("signature_state","PENDING");store.fixture.request.put("execution_token",null);store.fixture.request.put("finished_at",null);
        for(Map<String,Object> object:store.fixture.objects)object.put("unit_state","PENDING");
        removeOutcome(store.online);store.online.addAll(store.historyScope);store.historyScope.clear();store.historyOutcome.clear();
        result=new NativeOrgBatchReceiptReader(store).readAvailable(store.fixture.request,store.fixture.objects);
        require(result.getSelection()!=null && result.getSelection().getPages().size()==3 && result.getEvidence()==null,
            "Pending prepared pages were lost or invented a business result");
    }
    private static void addPrepared(Store store) throws Exception {
        Map<String,Object> preparing=new HashMap<String,Object>(store.fixture.request);preparing.put("business_state","PENDING");
        preparing.put("signature_state","PENDING");preparing.put("execution_token",null);preparing.put("finished_at",null);
        for(Map<String,Object> object:store.fixture.objects)object.put("unit_state","PENDING");
        for(Map<String,Object> row:NativeOrgBatchPreparedRecords.encode(preparing,store.fixture.objects,NativeOrgBatchPage.CUSTOMER_FINANCE,
                store.fixture.preparedEvidence))store.historyScope.add(new HashMap<String,Object>(row));
        if(store.fixture.request.get("finished_at")!=null) {
            store.fixture.objects.get(0).put("unit_state","PARTIAL");store.fixture.objects.get(1).put("unit_state","UNCHANGED");
            store.fixture.objects.get(2).put("unit_state","UNCHANGED");
        }
    }
    private static void availableReader() throws Exception {
        Store store=new Store();store.open=true;store.fixture.request.put("business_state","PENDING");
        store.fixture.request.put("signature_state","PENDING");store.fixture.request.put("execution_token",null);store.fixture.request.put("finished_at",null);
        for(Map<String,Object> object:store.fixture.objects)object.put("unit_state","PENDING");
        removeOutcome(store.online);store.online.add(new HashMap<String,Object>(store.historyScope.get(0)));
        store.historyScope.clear();store.historyOutcome.clear();
        NativeOrgBatchReceiptReader.Result available=new NativeOrgBatchReceiptReader(store).readAvailable(store.fixture.request,store.fixture.objects);
        require(available.getEvidence()==null && available.getTarget().getTargets().equals(Arrays.asList("NO_ROWS","T")),
            "Pending request did not retain target evidence without inventing a business result");
        store=new Store();store.fixture.request.put("business_state","FAILED");
        for(Map<String,Object> object:store.fixture.objects)object.put("unit_state","FAILED");
        removeOutcome(store.online);store.historyOutcome.clear();
        available=new NativeOrgBatchReceiptReader(store).readAvailable(store.fixture.request,store.fixture.objects);
        require(available.getEvidence()==null && available.getTarget().getTargets().size()==2,
            "Rolled-back request fabricated or lost a page outcome");
        reject(store);
    }
    private static void removeOutcome(List<Map<String,Object>> rows) {
        for(java.util.Iterator<Map<String,Object>> it=rows.iterator();it.hasNext();) {
            String kind=String.valueOf(it.next().get("row_kind"));
            if("T".equals(kind) || "H".equals(kind) || "P".equals(kind))it.remove();
        }
    }
    private static void targetReader() throws Exception {
        Store store=new Store();store.open=true;store.fixture.request.put("business_state","PENDING");
        store.fixture.request.put("signature_state","PENDING");store.fixture.request.put("execution_token",null);store.fixture.request.put("finished_at",null);
        for(Map<String,Object> object:store.fixture.objects)object.put("unit_state","PENDING");
        store.online.add(new HashMap<String,Object>(store.historyScope.get(0)));store.historyScope.clear();store.historyOutcome.clear();
        require(new NativeOrgBatchTargetReader(store).read(store.fixture.request,store.fixture.objects).getTargets().equals(Arrays.asList("NO_ROWS","T"))
            && store.historyReads==0,"An active request did not read its prepared target scope online");
        store=new Store();store.fixture.request.put("business_state","CANCELLED");store.fixture.request.put("signature_state","CANCELLED");
        for(Map<String,Object> object:store.fixture.objects)object.put("unit_state","PENDING");
        require(new NativeOrgBatchTargetReader(store).read(store.fixture.request,store.fixture.objects).getTargets().size()==2,
            "Cancelled request lost its prepared targets");
        store=new Store();store.fixture.request.put("business_state","FAILED");
        for(Map<String,Object> object:store.fixture.objects)object.put("unit_state","FAILED");
        store.historyOutcome.clear();
        require(new NativeOrgBatchTargetReader(store).read(store.fixture.request,store.fixture.objects).getTargets().size()==2,
            "Rolled-back request lost its prepared targets");
        store=new Store();store.fixture.request.put("business_state","FAILED");store.fixture.request.put("signature_state","ACCOUNT_LOCKED");
        for(Map<String,Object> object:store.fixture.objects)object.put("unit_state","PENDING");
        require(new NativeOrgBatchTargetReader(store).read(store.fixture.request,store.fixture.objects).getTargets().size()==2,
            "Authentication failure lost its prepared targets");
        store.fixture.request.put("signature_state","FORGED");
        try { new NativeOrgBatchTargetReader(store).read(store.fixture.request,store.fixture.objects);
            throw new AssertionError("Invalid signature state accepted for target recovery"); } catch(BusinessException expected) { }
        store=new Store();store.historyScope.clear();store.online.remove(0);store.online.remove(0);
        try { new NativeOrgBatchTargetReader(store).read(store.fixture.request,store.fixture.objects);
            throw new AssertionError("Missing prepared target scope accepted"); } catch(BusinessException expected) { }
    }
    private static NativeOrgBatchReceiptReader.Result read(Store store) throws BusinessException {
        return new NativeOrgBatchReceiptReader(store).read(store.fixture.request,store.fixture.objects);
    }
    private static void reject(Store store) throws Exception {
        try { read(store);throw new AssertionError("Incomplete or conflicting receipt history accepted"); }
        catch(BusinessException expected) { }
    }
    private static void require(boolean value,String message) { if(!value)throw new AssertionError(message); }
    private static final class Store extends QdEsignStore {
        final NativeOrgBatchSnapshotTests.ReceiptFixture fixture;final List<Map<String,Object>> online=new ArrayList<Map<String,Object>>();
        final List<Map<String,Object>> historyScope=new ArrayList<Map<String,Object>>(),historyOutcome=new ArrayList<Map<String,Object>>();
        final Set<String> directories=new HashSet<String>();
        boolean open,duplicateLocator,missingHistory,available=true;int historyReads;
        Store() throws Exception {
            fixture=NativeOrgBatchSnapshotTests.receiptFixture();
            List<Map<String,Object>> rows=NativeOrgBatchReceiptRecords.encode(fixture.request,fixture.objects,
                NativeOrgBatchPage.CUSTOMER_FINANCE,fixture.evidence,EVENT);
            Map<String,Object> preparing=new HashMap<String,Object>(fixture.request);preparing.put("business_state","PENDING");
            preparing.put("signature_state","PENDING");preparing.put("execution_token",null);
            List<Map<String,Object>> scope=NativeOrgBatchTargetRecords.encode(preparing,fixture.objects,
                NativeOrgBatchPage.CUSTOMER_FINANCE,fixture.targetEvidence);
            for(int i=0;i<scope.size();i++) (i<2?online:historyScope).add(new HashMap<String,Object>(scope.get(i)));
            for(int i=0;i<rows.size();i++) (i<2?online:historyOutcome).add(new HashMap<String,Object>(rows.get(i)));
            historyScope.add(new HashMap<String,Object>(online.get(0)));historyOutcome.add(new HashMap<String,Object>(online.get(2)));
            directories.add("202611");directories.add("202612");
            fixture.request.put("business_state","PARTIAL");fixture.request.put("finished_at",EVENT);
            fixture.objects.get(0).put("unit_state","PARTIAL");fixture.objects.get(1).put("unit_state","UNCHANGED");
            fixture.objects.get(2).put("unit_state","UNCHANGED");
        }
        @Override protected boolean orgReceiptTableExists() { return available; }
        @Override public List<Map<String,Object>> list(String sql,SQLParameter parameters) {
            if(sql.contains("FROM qd_es_locator")) {
                List<Map<String,Object>> rows=new ArrayList<Map<String,Object>>();
                rows.add(EsignValues.row("archive_month","202611","request_closed",open?"N":"Y"));
                rows.add(EsignValues.row("archive_month","202612","request_closed",open?"N":"Y"));
                if(duplicateLocator) rows.add(EsignValues.row("archive_month","202612","request_closed","Y"));
                return rows;
            }
            if(sql.contains("FROM qd_es_batch_page_h202611")) { historyReads++;return filtered(historyScope,sql); }
            if(sql.contains("FROM qd_es_batch_page_h202612")) { historyReads++;return filtered(historyOutcome,sql); }
            if(sql.contains("FROM qd_es_batch_page WHERE")) return filtered(online,sql);
            throw new AssertionError("Unexpected receipt query: "+sql);
        }
        @Override public Map<String,Object> one(String sql,SQLParameter parameters) {
            require(sql.contains("FROM qd_es_archive"),"Unexpected receipt directory query");
            String month=String.valueOf(parameters.get(0));return directories.contains(month)?EsignValues.row("archive_month",month):null;
        }
        @Override public void requireHistoryTable(String base,String month) throws DAOException {
            require("qd_es_batch_page".equals(base) && ("202611".equals(month) || "202612".equals(month)),"Unexpected receipt history table");
            if(missingHistory)throw new DAOException("missing receipt history");
        }
        private static List<Map<String,Object>> filtered(List<Map<String,Object>> rows,String sql) {
            if(!sql.contains("row_kind IN ('S','U')"))return rows;
            List<Map<String,Object>> result=new ArrayList<Map<String,Object>>();
            for(Map<String,Object> row:rows) if("S".equals(row.get("row_kind")) || "U".equals(row.get("row_kind")))result.add(row);
            return result;
        }
    }
}
