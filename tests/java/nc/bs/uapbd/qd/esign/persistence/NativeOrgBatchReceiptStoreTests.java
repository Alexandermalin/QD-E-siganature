package nc.bs.uapbd.qd.esign.persistence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.transaction.Status;
import javax.transaction.Synchronization;
import javax.transaction.TransactionSynchronizationRegistry;
import nc.bs.dao.DAOException;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.bs.uapbd.qd.esign.core.NativeOrgBatchPage;
import nc.bs.uapbd.qd.esign.core.NativeOrgBatchReceipt;
import nc.bs.uapbd.qd.esign.core.NativeOrgBatchSnapshotTests;
import nc.bs.uapbd.qd.esign.core.TransactionRollback;
import nc.jdbc.framework.SQLParameter;
import nc.vo.pub.BusinessException;

/** Tests transaction confinement and failure propagation with explicit substitutes; no NC transaction or Oracle. */
public final class NativeOrgBatchReceiptStoreTests {
    private static final String ID="0123456789abcdef0123456789abcdef",TOKEN="abcdefabcdefabcdefabcdefabcdefab";
    private static final String EVENT="2026-11-30 16:01:01";
    private NativeOrgBatchReceiptStoreTests() { }
    public static void run() throws Exception {
        schemaGuards();
        Store store=new Store();Tx tx=new Tx();Object key=tx.getTransactionKey();
        NativeOrgBatchReceiptStore.Prepared prepared=verifiedPreflight(store,tx,key);
        NativeOrgBatchReceiptStore.writeGuarded(store,tx,key,prepared,store.fixture.evidence,EVENT);
        NativeOrgBatchReceiptStore.requireCompletedGuarded(store,tx,key,prepared);
        require(!tx.rollback && store.schemaChecks==1 && store.requestLocks==2 && store.begins==1 && store.rows.size()==15,
            "Receipt was not preflighted and written once in the claimed transaction");
        store.fixture.request.put("business_state","PARTIAL");store.fixture.request.put("finished_at",EVENT);
        store.fixture.objects.get(0).put("unit_state","PARTIAL");
        store.fixture.objects.get(1).put("unit_state","UNCHANGED");store.fixture.objects.get(2).put("unit_state","UNCHANGED");
        NativeOrgBatchReceipt.Evidence restored=NativeOrgBatchReceiptRecords.decode(store.fixture.request,store.fixture.objects,
            NativeOrgBatchPage.CUSTOMER_FINANCE,store.outcomeRows());
        require(restored.getTargets().size()==2 && restored.getPages().size()==3,"Written receipt could not be restored");

        store=new Store();tx=new Tx();key=tx.getTransactionKey();
        prepared=NativeOrgBatchReceiptStore.preflightGuarded(store,tx,key,ID,TOKEN,NativeOrgBatchPage.CUSTOMER_FINANCE);
        try { NativeOrgBatchReceiptStore.writeGuarded(store,tx,key,prepared,store.fixture.evidence,EVENT);
            throw new AssertionError("Receipt was written without checking the locked selection"); }
        catch(BusinessException expected) { require(tx.rollback && store.outcomeRows().isEmpty(),"Missing scope check did not fail closed"); }

        store=new Store();tx=new Tx();key=tx.getTransactionKey();
        prepared=NativeOrgBatchReceiptStore.preflightGuarded(store,tx,key,ID,TOKEN,NativeOrgBatchPage.CUSTOMER_FINANCE);
        try { NativeOrgBatchReceiptStore.matchSelectionGuarded(store,tx,key,prepared,
                NativeOrgBatchSnapshotTests.emptyReceiptFixture().preparedEvidence);
            throw new AssertionError("A different locked page selection was accepted"); }
        catch(BusinessException expected) { require(tx.rollback && store.outcomeRows().isEmpty(),
            "A different locked selection was not rejected before native execution"); }

        store=new Store();tx=new Tx();key=tx.getTransactionKey();
        prepared=verifiedPreflight(store,tx,key);
        try { NativeOrgBatchReceiptStore.requireCompletedGuarded(store,tx,key,prepared);
            throw new AssertionError("An unwritten receipt was accepted before transaction return"); }
        catch(BusinessException expected) { require(tx.rollback && TransactionRollback.isMarked(expected) && store.outcomeRows().isEmpty(),
            "An unwritten receipt did not mark the business transaction for rollback"); }

        store=new Store();tx=new Tx();key=tx.getTransactionKey();
        prepared=verifiedPreflight(store,tx,key);tx.key=new Object();
        try { NativeOrgBatchReceiptStore.requireCompletedGuarded(store,tx,tx.getTransactionKey(),prepared);
            throw new AssertionError("A receipt completion check crossed transactions"); }
        catch(BusinessException expected) { require(!tx.rollback && !TransactionRollback.isMarked(expected),
            "A foreign receipt completion check falsely confirmed rollback"); }

        store=new Store();tx=new Tx();key=tx.getTransactionKey();store.failAt=2;
        prepared=verifiedPreflight(store,tx,key);
        try { NativeOrgBatchReceiptStore.writeGuarded(store,tx,key,prepared,store.fixture.evidence,EVENT);
            throw new AssertionError("Mid-write failure was accepted"); }
        catch(BusinessException expected) { require(tx.rollback && TransactionRollback.isMarked(expected) && store.outcomeRows().size()==2,
            "Mid-write failure did not mark the current transaction for rollback"); }
        store=new Store();tx=new Tx();key=tx.getTransactionKey();store.wrongCount=true;
        prepared=verifiedPreflight(store,tx,key);
        try { NativeOrgBatchReceiptStore.writeGuarded(store,tx,key,prepared,store.fixture.evidence,EVENT);
            throw new AssertionError("Incomplete receipt count was accepted"); }
        catch(BusinessException expected) { require(tx.rollback && TransactionRollback.isMarked(expected),"Count mismatch did not mark rollback"); }

        store=new Store();tx=new Tx();key=tx.getTransactionKey();store.storageReady=false;
        rejectPreflight(store,tx,key,TOKEN,NativeOrgBatchPage.CUSTOMER_FINANCE);
        store=new Store();tx=new Tx();key=tx.getTransactionKey();store.existing=true;
        rejectPreflight(store,tx,key,TOKEN,NativeOrgBatchPage.CUSTOMER_FINANCE);
        store=new Store();tx=new Tx();key=tx.getTransactionKey();store.rows.clear();
        rejectPreflight(store,tx,key,TOKEN,NativeOrgBatchPage.CUSTOMER_FINANCE);
        store=new Store();tx=new Tx();key=tx.getTransactionKey();
        for(java.util.Iterator<Map<String,Object>> it=store.rows.iterator();it.hasNext();)if("W".equals(it.next().get("row_kind")))it.remove();
        rejectPreflight(store,tx,key,TOKEN,NativeOrgBatchPage.CUSTOMER_FINANCE);
        store=new Store();tx=new Tx();key=tx.getTransactionKey();
        for(java.util.Iterator<Map<String,Object>> it=store.rows.iterator();it.hasNext();)if("V".equals(it.next().get("row_kind"))) { it.remove();break; }
        rejectPreflight(store,tx,key,TOKEN,NativeOrgBatchPage.CUSTOMER_FINANCE);
        store=new Store();tx=new Tx();key=tx.getTransactionKey();
        rejectPreflight(store,tx,key,"ffffffffffffffffffffffffffffffff",NativeOrgBatchPage.CUSTOMER_FINANCE);
        store=new Store();tx=new Tx();key=tx.getTransactionKey();
        rejectPreflight(store,tx,key,TOKEN,NativeOrgBatchPage.SUPPLIER_FINANCE);

        store=new Store();tx=new Tx();key=tx.getTransactionKey();
        prepared=verifiedPreflight(store,tx,key);
        store.fixture.request.put("business_state","FAILED");
        try { NativeOrgBatchReceiptStore.writeGuarded(store,tx,key,prepared,store.fixture.evidence,EVENT);
            throw new AssertionError("Changed request was accepted after preflight"); }
        catch(BusinessException expected) { require(tx.rollback && store.outcomeRows().isEmpty(),"Changed claim did not roll back before writing"); }

        store=new Store();tx=new Tx();key=tx.getTransactionKey();
        prepared=verifiedPreflight(store,tx,key);
        store.rows.get(1).put("item_key","OTHER");
        try { NativeOrgBatchReceiptStore.writeGuarded(store,tx,key,prepared,store.fixture.evidence,EVENT);
            throw new AssertionError("Changed prepared target scope was accepted"); }
        catch(BusinessException expected) { require(tx.rollback && store.outcomeRows().isEmpty(),"Changed targets did not mark rollback"); }

        store=new Store();tx=new Tx();key=tx.getTransactionKey();
        prepared=verifiedPreflight(store,tx,key);
        for(Map<String,Object> row:store.rows)if("V".equals(row.get("row_kind"))) { row.put("target_org","NO_ROWS");break; }
        try { NativeOrgBatchReceiptStore.writeGuarded(store,tx,key,prepared,store.fixture.evidence,EVENT);
            throw new AssertionError("A transferred prepared page was accepted after preflight"); }
        catch(BusinessException expected) { require(tx.rollback && store.outcomeRows().isEmpty(),"Changed prepared pages did not mark rollback"); }

        store=new Store();tx=new Tx();key=tx.getTransactionKey();
        prepared=verifiedPreflight(store,tx,key);
        tx.key=new Object();
        try { NativeOrgBatchReceiptStore.writeGuarded(store,tx,tx.getTransactionKey(),prepared,store.fixture.evidence,EVENT);
            throw new AssertionError("Preflight ticket crossed transactions"); }
        catch(BusinessException expected) { require(!TransactionRollback.isMarked(expected) && !tx.rollback && store.outcomeRows().isEmpty(),
            "A different transaction was falsely reported as the original rollback"); }

        final Store threadStore=new Store();final Tx threadTx=new Tx();final Object threadKey=threadTx.getTransactionKey();
        final NativeOrgBatchReceiptStore.Prepared foreign=verifiedPreflight(threadStore,threadTx,threadKey);final boolean[] denied={false};
        Thread thread=new Thread(new Runnable() { public void run() {
            try { NativeOrgBatchReceiptStore.writeGuarded(threadStore,threadTx,threadKey,foreign,threadStore.fixture.evidence,EVENT); }
            catch(BusinessException expected) { denied[0]=!TransactionRollback.isMarked(expected); }
        }});thread.start();thread.join();require(denied[0] && threadStore.outcomeRows().isEmpty(),"Preflight ticket crossed threads");

        store=new Store();tx=new Tx();key=tx.getTransactionKey();
        prepared=verifiedPreflight(store,tx,key);
        NativeOrgBatchReceiptStore.writeGuarded(store,tx,key,prepared,store.fixture.evidence,EVENT);
        try { NativeOrgBatchReceiptStore.writeGuarded(store,tx,key,prepared,store.fixture.evidence,EVENT);
            throw new AssertionError("A preflight ticket was reused"); }
        catch(BusinessException expected) { require(tx.rollback && TransactionRollback.isMarked(expected),"Repeated write did not mark rollback"); }
        System.out.println("PASS NativeOrgBatchReceiptStoreTests (preflight, transaction identity and failure rollback; test doubles only)");
    }
    private static NativeOrgBatchReceiptStore.Prepared verifiedPreflight(Store store,Tx tx,Object key) throws Exception {
        NativeOrgBatchReceiptStore.Prepared prepared=NativeOrgBatchReceiptStore.preflightGuarded(store,tx,key,ID,TOKEN,
            NativeOrgBatchPage.CUSTOMER_FINANCE);
        NativeOrgBatchReceiptStore.matchSelectionGuarded(store,tx,key,prepared,store.fixture.preparedEvidence);
        return prepared;
    }
    private static void rejectPreflight(Store store,Tx tx,Object key,String token,NativeOrgBatchPage page) throws Exception {
        try { NativeOrgBatchReceiptStore.preflightGuarded(store,tx,key,ID,token,page);throw new AssertionError("Unsafe receipt preflight accepted"); }
        catch(BusinessException expected) { require(tx.rollback && store.outcomeRows().isEmpty(),"Preflight failure did not mark rollback before writing"); }
    }
    private static void schemaGuards() throws Exception {
        SchemaStore store=new SchemaStore();store.requireOrgReceiptStorage();
        require(store.locks==1 && store.projections==1,"Receipt storage preflight skipped its schema lock or column projection");
        for(int mode=1;mode<=7;mode++) {
            store=new SchemaStore();store.mode=mode;
            try { store.requireOrgReceiptStorage();throw new AssertionError("Missing or rolled-back receipt schema accepted"); }
            catch(DAOException expected) { }
        }
    }
    private static final class SchemaStore extends QdEsignStore {
        int mode,locks,projections;
        @Override protected void lockOrgReceiptSchema() { locks++; }
        @Override protected boolean orgReceiptTableExists() { return mode!=4; }
        @Override public Map<String,Object> one(String sql,SQLParameter parameters) {
            if(sql.contains("owner_count")) {
                require(sql.contains("version_no='1.3.1-SEL'") && sql.contains("'1.3.1-SEL-RB'"),
                    "Receipt writer omitted the selection capability or rollback marker");
                return EsignValues.row("owner_count",Integer.valueOf(mode==1?0:1),
                    "receipt_count",Integer.valueOf(mode==2?0:1),"selection_count",Integer.valueOf(mode==6?0:mode==7?2:1),
                    "rollback_count",Integer.valueOf(mode==3?1:0));
            }
            if(sql.contains("guard_count")) return EsignValues.row("guard_count",Integer.valueOf(mode==5?1:2));
            throw new AssertionError("Unexpected receipt preflight query");
        }
        @Override public List<Map<String,Object>> list(String sql,SQLParameter parameters) {
            require(sql.contains("FROM qd_es_batch_page WHERE 1=0") && sql.contains("record_id,request_id,object_id"),
                "Receipt schema projection changed");projections++;return new ArrayList<Map<String,Object>>();
        }
    }
    private static final class Store extends QdEsignStore {
        final NativeOrgBatchSnapshotTests.ReceiptFixture fixture;final List<Map<String,Object>> rows=new ArrayList<Map<String,Object>>();
        boolean storageReady=true,existing,wrongCount;int schemaChecks,requestLocks,begins,failAt=-1;
        Store() throws Exception {
            fixture=NativeOrgBatchSnapshotTests.receiptFixture();Map<String,Object> preparing=new HashMap<String,Object>(fixture.request);
            preparing.put("business_state","PENDING");preparing.put("signature_state","PENDING");preparing.put("execution_token",null);
            for(Map<String,Object> row:NativeOrgBatchTargetRecords.encode(preparing,fixture.objects,NativeOrgBatchPage.CUSTOMER_FINANCE,
                    fixture.targetEvidence))rows.add(new HashMap<String,Object>(row));
            for(Map<String,Object> row:NativeOrgBatchPreparedRecords.encode(preparing,fixture.objects,NativeOrgBatchPage.CUSTOMER_FINANCE,
                    fixture.preparedEvidence))rows.add(new HashMap<String,Object>(row));
        }
        @Override protected void requireOrgReceiptStorage() throws DAOException {
            schemaChecks++;if(!storageReady)throw new DAOException("receipt storage unavailable");
        }
        @Override public Map<String,Object> loadRequest(String id,boolean lock) {
            require(ID.equals(id) && lock,"Claimed request was not locked");requestLocks++;return fixture.request;
        }
        @Override public List<Map<String,Object>> loadObjects(String id) { require(ID.equals(id),"Wrong request objects");return fixture.objects; }
        @Override protected List<Map<String,Object>> loadOrgReceiptRows(String id) { return rows; }
        @Override protected void requireNoOrgOutcomeRows(String id) throws DAOException {
            if(existing || !outcomeRows().isEmpty())throw new DAOException("duplicate receipt");
        }
        @Override protected void beginOrgOutcomeRows(String id,String month) throws DAOException {
            require(ID.equals(id) && "202612".equals(month),"Receipt locator scope is wrong");
            requireNoOrgOutcomeRows(id);begins++;
        }
        @Override protected void insertOrgReceiptRow(Map<String,Object> row) throws DAOException {
            if(outcomeRows().size()==failAt)throw new DAOException("injected insert failure");rows.add(new HashMap<String,Object>(row));
        }
        @Override protected long countOrgReceiptRows(String id) { return rows.size()-(wrongCount?1:0); }
        List<Map<String,Object>> outcomeRows() {
            List<Map<String,Object>> result=new ArrayList<Map<String,Object>>();
            for(Map<String,Object> row:rows) if("T".equals(row.get("row_kind")) || "H".equals(row.get("row_kind"))
                    || "P".equals(row.get("row_kind")))result.add(row);
            return result;
        }
    }
    private static final class Tx implements TransactionSynchronizationRegistry {
        Object key=new Object();boolean rollback;
        public Object getTransactionKey() { return key; }
        public int getTransactionStatus() { return rollback?Status.STATUS_MARKED_ROLLBACK:Status.STATUS_ACTIVE; }
        public void setRollbackOnly() { rollback=true; }
        public boolean getRollbackOnly() { return rollback; }
        public Object getResource(Object key) { return null; }
        public void putResource(Object key,Object value) { }
        public void registerInterposedSynchronization(Synchronization synchronization) { }
    }
    private static void require(boolean value,String message) { if(!value)throw new AssertionError(message); }
}
