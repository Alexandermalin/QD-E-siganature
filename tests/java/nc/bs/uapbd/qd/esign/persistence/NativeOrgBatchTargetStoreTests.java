package nc.bs.uapbd.qd.esign.persistence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.transaction.Status;
import javax.transaction.Synchronization;
import javax.transaction.TransactionSynchronizationRegistry;
import nc.bs.dao.DAOException;
import nc.bs.uapbd.qd.esign.core.NativeOrgBatchPage;
import nc.bs.uapbd.qd.esign.core.NativeOrgBatchSnapshotTests;
import nc.bs.uapbd.qd.esign.core.TransactionRollback;
import nc.vo.pub.BusinessException;

/** Preparation-time target persistence boundaries with test substitutes, not an NC/Oracle transaction. */
public final class NativeOrgBatchTargetStoreTests {
    private static final String ID="0123456789abcdef0123456789abcdef";
    private NativeOrgBatchTargetStoreTests() { }
    public static void run() throws Exception {
        Store store=new Store();Tx tx=new Tx();Object key=tx.getTransactionKey();
        NativeOrgBatchTargetStore.Prepared ticket=NativeOrgBatchTargetStore.beginGuarded(store,tx,key,ID,NativeOrgBatchPage.CUSTOMER_FINANCE);
        store.inserted=true;NativeOrgBatchTargetStore.writeGuarded(store,tx,key,ticket,store.fixture.preparedEvidence);
        NativeOrgBatchTargetStore.requireCompletedGuarded(store,tx,key,ticket);
        require(!tx.rollback && store.schemaChecks==1 && store.requestLocks==2 && store.begins==1 && store.rows.size()==7,
            "Prepared targets and page identities were not written in the same transaction");
        require(NativeOrgBatchTargetRecords.decode(store.fixture.request,store.fixture.objects,NativeOrgBatchPage.CUSTOMER_FINANCE,
            NativeOrgBatchPreparedRecords.targetRows(store.rows))
            .getTargets().size()==2,"Prepared target rows could not be recovered");
        require(NativeOrgBatchPreparedRecords.decode(store.fixture.request,store.fixture.objects,NativeOrgBatchPage.CUSTOMER_FINANCE,
            store.fixture.targetEvidence,NativeOrgBatchPreparedRecords.pageRows(store.rows)).getPages().size()==3,
            "Prepared page rows could not be recovered");

        store=new Store();tx=new Tx();key=tx.getTransactionKey();
        ticket=NativeOrgBatchTargetStore.beginGuarded(store,tx,key,ID,NativeOrgBatchPage.CUSTOMER_FINANCE);
        try { NativeOrgBatchTargetStore.requireCompletedGuarded(store,tx,key,ticket);
            throw new AssertionError("An unwritten target scope was accepted before transaction return"); }
        catch(BusinessException expected) { require(tx.rollback && TransactionRollback.isMarked(expected) && store.rows.isEmpty(),
            "An unwritten target scope did not mark the preparation transaction for rollback"); }

        store=new Store();tx=new Tx();key=tx.getTransactionKey();
        ticket=NativeOrgBatchTargetStore.beginGuarded(store,tx,key,ID,NativeOrgBatchPage.CUSTOMER_FINANCE);tx.key=new Object();
        try { NativeOrgBatchTargetStore.requireCompletedGuarded(store,tx,tx.getTransactionKey(),ticket);
            throw new AssertionError("A target completion check crossed transactions"); }
        catch(BusinessException expected) { require(!tx.rollback && !TransactionRollback.isMarked(expected),
            "A foreign target completion check falsely confirmed rollback"); }

        store=new Store();tx=new Tx();key=tx.getTransactionKey();store.inserted=true;rejectBegin(store,tx,key);
        store=new Store();tx=new Tx();key=tx.getTransactionKey();store.existing=true;rejectBegin(store,tx,key);
        store=new Store();tx=new Tx();key=tx.getTransactionKey();store.fixture.request.put("execution_token","already-claimed");rejectBegin(store,tx,key);
        store=new Store();tx=new Tx();key=tx.getTransactionKey();
        ticket=NativeOrgBatchTargetStore.beginGuarded(store,tx,key,ID,NativeOrgBatchPage.CUSTOMER_FINANCE);
        try { NativeOrgBatchTargetStore.writeGuarded(store,tx,key,ticket,store.fixture.preparedEvidence);
            throw new AssertionError("Target scope was written before objects"); }
        catch(BusinessException expected) { require(tx.rollback && store.rows.isEmpty(),"Missing objects did not mark rollback"); }

        store=new Store();tx=new Tx();key=tx.getTransactionKey();store.failAt=1;
        ticket=NativeOrgBatchTargetStore.beginGuarded(store,tx,key,ID,NativeOrgBatchPage.CUSTOMER_FINANCE);store.inserted=true;
        try { NativeOrgBatchTargetStore.writeGuarded(store,tx,key,ticket,store.fixture.preparedEvidence);
            throw new AssertionError("Target insert failure was accepted"); }
        catch(BusinessException expected) { require(tx.rollback && TransactionRollback.isMarked(expected) && store.rows.size()==1,
            "Target insert failure did not mark rollback"); }
        store=new Store();tx=new Tx();key=tx.getTransactionKey();store.failAt=5;
        ticket=NativeOrgBatchTargetStore.beginGuarded(store,tx,key,ID,NativeOrgBatchPage.CUSTOMER_FINANCE);store.inserted=true;
        try { NativeOrgBatchTargetStore.writeGuarded(store,tx,key,ticket,store.fixture.preparedEvidence);
            throw new AssertionError("A partial prepared page insert was accepted"); }
        catch(BusinessException expected) { require(tx.rollback && TransactionRollback.isMarked(expected) && store.rows.size()==5,
            "Partial page evidence did not mark the preparation transaction for rollback"); }
        store=new Store();tx=new Tx();key=tx.getTransactionKey();store.wrongCount=true;
        ticket=NativeOrgBatchTargetStore.beginGuarded(store,tx,key,ID,NativeOrgBatchPage.CUSTOMER_FINANCE);store.inserted=true;
        try { NativeOrgBatchTargetStore.writeGuarded(store,tx,key,ticket,store.fixture.preparedEvidence);
            throw new AssertionError("Incomplete prepared target count was accepted"); }
        catch(BusinessException expected) { require(tx.rollback && TransactionRollback.isMarked(expected),"Target count mismatch did not mark rollback"); }

        store=new Store();tx=new Tx();key=tx.getTransactionKey();
        ticket=NativeOrgBatchTargetStore.beginGuarded(store,tx,key,ID,NativeOrgBatchPage.CUSTOMER_FINANCE);store.inserted=true;
        store.fixture.request.put("created_at","2026-11-30 15:59:58");
        try { NativeOrgBatchTargetStore.writeGuarded(store,tx,key,ticket,store.fixture.preparedEvidence);
            throw new AssertionError("Changed preparation request was accepted"); }
        catch(BusinessException expected) { require(tx.rollback && store.rows.isEmpty(),"Changed preparation did not mark rollback"); }
        store=new Store();tx=new Tx();key=tx.getTransactionKey();
        ticket=NativeOrgBatchTargetStore.beginGuarded(store,tx,key,ID,NativeOrgBatchPage.CUSTOMER_FINANCE);store.inserted=true;
        store.fixture.objects.get(0).put("target_org","OTHER");
        try { NativeOrgBatchTargetStore.writeGuarded(store,tx,key,ticket,store.fixture.preparedEvidence);
            throw new AssertionError("Changed prepared archive scope was accepted"); }
        catch(BusinessException expected) { require(tx.rollback && store.rows.isEmpty(),"Changed archive scope did not mark rollback"); }

        store=new Store();tx=new Tx();key=tx.getTransactionKey();
        ticket=NativeOrgBatchTargetStore.beginGuarded(store,tx,key,ID,NativeOrgBatchPage.CUSTOMER_FINANCE);store.inserted=true;tx.key=new Object();
        try { NativeOrgBatchTargetStore.writeGuarded(store,tx,tx.getTransactionKey(),ticket,store.fixture.preparedEvidence);
            throw new AssertionError("Target ticket crossed transactions"); }
        catch(BusinessException expected) { require(!TransactionRollback.isMarked(expected) && !tx.rollback && store.rows.isEmpty(),
            "A different transaction was falsely reported as the original rollback"); }

        final Store foreignStore=new Store();final Tx foreignTx=new Tx();final Object foreignKey=foreignTx.getTransactionKey();
        final NativeOrgBatchTargetStore.Prepared foreign=NativeOrgBatchTargetStore.beginGuarded(foreignStore,foreignTx,foreignKey,ID,
            NativeOrgBatchPage.CUSTOMER_FINANCE);foreignStore.inserted=true;final boolean[] denied={false};
        Thread thread=new Thread(new Runnable() { public void run() {
            try { NativeOrgBatchTargetStore.writeGuarded(foreignStore,foreignTx,foreignKey,foreign,foreignStore.fixture.preparedEvidence); }
            catch(BusinessException expected) { denied[0]=!TransactionRollback.isMarked(expected); }
        }});thread.start();thread.join();require(denied[0] && foreignStore.rows.isEmpty(),"Target ticket crossed threads");

        store=new Store();tx=new Tx();key=tx.getTransactionKey();
        ticket=NativeOrgBatchTargetStore.beginGuarded(store,tx,key,ID,NativeOrgBatchPage.CUSTOMER_FINANCE);store.inserted=true;
        NativeOrgBatchTargetStore.writeGuarded(store,tx,key,ticket,store.fixture.preparedEvidence);
        try { NativeOrgBatchTargetStore.writeGuarded(store,tx,key,ticket,store.fixture.preparedEvidence);
            throw new AssertionError("Target preparation ticket was reused"); }
        catch(BusinessException expected) { require(tx.rollback && TransactionRollback.isMarked(expected),"Ticket reuse did not mark rollback"); }
        System.out.println("PASS NativeOrgBatchTargetStoreTests (preparation scope and rollback; test doubles only)");
    }
    private static void rejectBegin(Store store,Tx tx,Object key) throws Exception {
        try { NativeOrgBatchTargetStore.beginGuarded(store,tx,key,ID,NativeOrgBatchPage.CUSTOMER_FINANCE);
            throw new AssertionError("Invalid target preparation accepted"); }
        catch(BusinessException expected) { require(tx.rollback && store.rows.isEmpty(),"Invalid preparation did not mark rollback"); }
    }
    private static final class Store extends QdEsignStore {
        final NativeOrgBatchSnapshotTests.ReceiptFixture fixture;final List<Map<String,Object>> rows=new ArrayList<Map<String,Object>>();
        boolean inserted,existing,wrongCount;int schemaChecks,requestLocks,begins,failAt=-1;
        Store() throws Exception {
            fixture=NativeOrgBatchSnapshotTests.receiptFixture();fixture.request.put("business_state","PENDING");
            fixture.request.put("signature_state","PENDING");fixture.request.put("execution_token",null);
        }
        @Override protected void requireOrgReceiptStorage() { schemaChecks++; }
        @Override public Map<String,Object> loadRequest(String id,boolean lock) {
            require(ID.equals(id) && lock,"Preparation request was not locked");requestLocks++;return fixture.request;
        }
        @Override public List<Map<String,Object>> loadObjects(String id) {
            return inserted?fixture.objects:Collections.<Map<String,Object>>emptyList();
        }
        @Override protected void requireNoOrgReceiptRows(String id) throws DAOException {
            if(existing || !rows.isEmpty())throw new DAOException("existing target scope");
        }
        @Override protected void beginOrgReceiptRows(String id,String month) throws DAOException {
            require(ID.equals(id) && "202611".equals(month),"Prepared target locator month is wrong");
            requireNoOrgReceiptRows(id);begins++;
        }
        @Override protected void insertOrgReceiptRow(Map<String,Object> row) throws DAOException {
            if(rows.size()==failAt)throw new DAOException("injected target insert failure");rows.add(new HashMap<String,Object>(row));
        }
        @Override protected long countOrgReceiptRows(String id) { return rows.size()-(wrongCount?1:0); }
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
