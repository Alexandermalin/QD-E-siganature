package nc.impl.ic.qd.esign;

import java.util.Collections;
import java.util.Map;
import javax.transaction.Status;
import javax.transaction.Synchronization;
import javax.transaction.TransactionSynchronizationRegistry;
import nc.impl.pubapp.pattern.rule.processer.AroundProcesser;
import nc.vo.ic.m40.entity.InitializtionInHeadVO;
import nc.vo.ic.m40.entity.InitializtionInVO;
import nc.vo.pub.IAttributeMeta;
import nc.vo.pub.ISuperVO;
import nc.vo.pub.IVOMeta;
import nc.vo.pubapp.pattern.model.meta.entity.bill.IBillMeta;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;

/** Tests the candidate native return hook without registering it in NC or invoking business services. */
public final class InventoryInitialImportCaptureTests {
    private static final IBillMeta META = new IBillMeta() {
        public Map<String,Object> getBusinessAttribute() { return Collections.emptyMap(); }
        public IAttributeMeta[] getChildForeignKeys() { return new IAttributeMeta[0]; }
        public IVOMeta[] getChildren() { return new IVOMeta[0]; }
        public String getComponentName() { return "INITIAL_IMPORT_CAPTURE_TEST"; }
        public IVOMeta getParent() { throw new UnsupportedOperationException(); }
        public Class<? extends ISuperVO> getVOClass(IVOMeta value) { throw new UnsupportedOperationException(); }
        public IVOMeta getVOMeta(Class<? extends ISuperVO> value) { throw new UnsupportedOperationException(); }
    };

    public static void run() throws Exception {
        QdInitialInImportCaptureRule rule = new QdInitialInImportCaptureRule();
        rule.process(null); // The globally registered rule must be inert outside a controlled capture.
        Tx transaction = new Tx();
        InventoryInitialImportCapture scope = InventoryInitialImportCapture.open(context("R", "40080402", null), transaction);
        try {
            AroundProcesser<InitializtionInVO> nativeProcessor = new AroundProcesser<InitializtionInVO>(null);
            nativeProcessor.addAfterRule(rule);
            nativeProcessor.after(new InitializtionInVO[] { bill("PK-1", "BILL-1", "G", "O", "TYPE-1", "40-01") });
            InventoryInitialImportCapture.Result result = scope.finish();
            require("PK-1".equals(result.primaryKey) && "BILL-1".equals(result.billCode)
                    && "TYPE-1".equals(result.transactionTypePk) && "40-01".equals(result.transactionTypeCode),
                    "Capture must preserve the native insert identity and type");
            try { scope.finish(); throw new AssertionError("Capture result was consumed twice"); }
            catch (IllegalStateException expected) { }
            require(transaction.rollbackOnly, "Repeated consumption must mark the transaction for rollback");
        } finally { closeFailed(scope, transaction); }
        scope.close();
        rule.process(null);

        scope = InventoryInitialImportCapture.open(context("R", "40080402", null), transaction);
        try {
            rule.process(new InitializtionInVO[] { bill("PK-1", "BILL-1", "G", "O", "TYPE-1", "40") });
            scope.finish();
        } finally { scope.close(); }
        require(!transaction.rollbackOnly, "A verified capture must not mark its transaction for rollback");
        transaction.key = "UNRELATED-TX";
        try { scope.finish(); throw new AssertionError("A closed capture was reusable"); }
        catch (IllegalStateException expected) {
            require(expected.getSuppressed().length == 1 && !transaction.rollbackOnly,
                    "A stale capture must not mark an unrelated transaction for rollback");
        }
        transaction.key = "TX";

        scope = InventoryInitialImportCapture.open(context("R", "40080402", null), transaction);
        try {
            try { scope.finish(); throw new AssertionError("Missing native hook was accepted"); }
            catch (IllegalStateException expected) { }
            require(transaction.rollbackOnly, "A missing hook must mark the transaction for rollback");
        } finally { closeFailed(scope, transaction); }

        final Throwable[] wrongThreadFailure = new Throwable[1];
        scope = InventoryInitialImportCapture.open(context("R", "40080402", null), transaction);
        try {
            final InventoryInitialImportCapture ownerScope = scope;
            Thread wrongThread = new Thread(new Runnable() { public void run() {
                try { ownerScope.finish(); }
                catch (Throwable failure) { wrongThreadFailure[0] = failure; }
            }});
            wrongThread.start(); wrongThread.join();
            require(wrongThreadFailure[0] instanceof IllegalStateException && !transaction.rollbackOnly,
                    "A foreign thread must not mark its transaction through this capture");
            try { scope.finish(); throw new AssertionError("A cross-thread capture remained usable"); }
            catch (IllegalStateException expected) { }
            require(transaction.rollbackOnly, "The owning transaction must fail after cross-thread misuse");
        } finally { closeFailed(scope, transaction); }

        scope = InventoryInitialImportCapture.open(context("R", "40080402", null), transaction);
        try {
            rule.process(new InitializtionInVO[] { bill("PK-1", "BILL-1", "G", "O", "TYPE-1", "40") });
            try { rule.process(new InitializtionInVO[] { bill("PK-2", "BILL-2", "G", "O", "TYPE-1", "40") });
                throw new AssertionError("Two native inserts were accepted for one request"); }
            catch (IllegalStateException expected) { }
            try { scope.finish(); throw new AssertionError("Ambiguous capture remained usable"); }
            catch (IllegalStateException expected) { }
            require(transaction.rollbackOnly, "Ambiguous native results must mark the transaction for rollback");
        } finally { closeFailed(scope, transaction); }

        scope = InventoryInitialImportCapture.open(context("R", "40080402", null), transaction);
        try {
            try { rule.process(new InitializtionInVO[] { bill("PK-1", "BILL-1", "OTHER", "O", "TYPE-1", "40") });
                throw new AssertionError("A bill from another group was captured"); }
            catch (IllegalStateException expected) { }
            try { scope.finish(); throw new AssertionError("Invalid capture remained usable"); }
            catch (IllegalStateException expected) { }
            require(transaction.rollbackOnly, "A cross-group result must mark the transaction for rollback");
        } finally { closeFailed(scope, transaction); }

        scope = InventoryInitialImportCapture.open(context("R", "40080402", null), transaction);
        try {
            try { rule.process(new InitializtionInVO[] { bill("PK-1", null, "G", "O", "TYPE-1", "40") });
                throw new AssertionError("Missing native bill number was accepted"); }
            catch (IllegalStateException expected) { }
            try { scope.finish(); throw new AssertionError("Incomplete identity remained usable"); }
            catch (IllegalStateException expected) { }
            require(transaction.rollbackOnly, "An incomplete identity must mark the transaction for rollback");
        } finally { closeFailed(scope, transaction); }

        scope = InventoryInitialImportCapture.open(context("R", "40080402", null), transaction);
        try {
            rule.process(new InitializtionInVO[] { bill("PK-1", "BILL-1", "G", "O", "TYPE-1", "40") });
            transaction.rollbackOnly = true;
            try { scope.finish(); throw new AssertionError("Rollback-only result was accepted"); }
            catch (IllegalStateException expected) { }
        } finally { closeFailed(scope, transaction); }

        scope = InventoryInitialImportCapture.open(context("R", "40080402", null), transaction);
        try {
            transaction.key = "OTHER-TX";
            try { rule.process(new InitializtionInVO[] { bill("PK-1", "BILL-1", "G", "O", "TYPE-1", "40") });
                throw new AssertionError("A different transaction was accepted"); }
            catch (IllegalStateException expected) { }
            require(transaction.rollbackOnly, "A changed current transaction must be marked for rollback inside the hook");
            transaction.key = "TX";
            try { scope.finish(); throw new AssertionError("Changed transaction capture remained usable"); }
            catch (IllegalStateException expected) { }
        } finally { closeFailed(scope, transaction); }

        final Throwable[] workerFailure = new Throwable[1];
        scope = InventoryInitialImportCapture.open(context("R", "40080402", null), transaction);
        try {
            try { InventoryInitialImportCapture.open(context("R", "40080402", null), transaction);
                throw new AssertionError("Nested capture accepted"); }
            catch (IllegalStateException expected) { }
            final QdInitialInImportCaptureRule workerRule = rule;
            Thread worker = new Thread(new Runnable() { public void run() {
                try { workerRule.process(new InitializtionInVO[] { bill("PK-1", "BILL-1", "G", "O", "TYPE-1", "40") }); }
                catch (Throwable failure) { workerFailure[0] = failure; }
            }});
            worker.start(); worker.join();
            require(workerFailure[0] == null, "An unrelated thread must not inherit a capture");
            try { scope.finish(); throw new AssertionError("Cross-thread hook was accepted as a capture"); }
            catch (IllegalStateException expected) { }
            require(transaction.rollbackOnly, "A missing same-thread result must mark the transaction for rollback");
        } finally { closeFailed(scope, transaction); }

        scope = InventoryInitialImportCapture.open(context("R", "40080402", null), transaction);
        try {
            transaction.failMark = true;
            try { rule.process(new InitializtionInVO[] { bill("PK-1", null, "G", "O", "TYPE-1", "40") });
                throw new AssertionError("A marker failure was ignored"); }
            catch (IllegalStateException expected) {
                require(expected.getSuppressed().length == 1, "Rollback marker failure must remain attached to the original error");
            }
        } finally { closeFailed(scope, transaction); }

        scope = InventoryInitialImportCapture.open(context("R", "40080402", null), transaction);
        closeFailed(scope, transaction);

        try { InventoryInitialImportCapture.open(context(null, "40080402", null), transaction);
            throw new AssertionError("Missing request identity accepted"); }
        catch (IllegalArgumentException expected) { }
        try { InventoryInitialImportCapture.open(context("R", "40080602", null), transaction);
            throw new AssertionError("Another node was accepted"); }
        catch (IllegalArgumentException expected) { }
        try { InventoryInitialImportCapture.open(context("R", "40080402", "~"), transaction);
            throw new AssertionError("A saved or sentinel object identity was accepted"); }
        catch (IllegalArgumentException expected) { }
        OperationContext manualSave = new OperationContext("R", "B", "G", "O", "40080402", "initial", "Save", "Save", "INSERT", null,
                Collections.singletonList(new BusinessObjectRef("item", "IC_INITIAL_IN", null, null, null)));
        try { InventoryInitialImportCapture.open(manualSave, transaction);
            throw new AssertionError("Manual-save authorization was accepted as an import"); }
        catch (IllegalArgumentException expected) { }
        transaction.rollbackOnly = true;
        try { InventoryInitialImportCapture.open(context("R", "40080402", null), transaction);
            throw new AssertionError("Rollback-only transaction accepted"); }
        catch (IllegalStateException expected) { }
        System.out.println("PASS InventoryInitialImportCaptureTests (native after-insert identity and transaction scope; no NC)");
    }

    private static OperationContext context(String request, String node, String pk) {
        return new OperationContext(request, "B", "G", "O", node, "initial", "Save", "Save", "IMPORT", null,
                Collections.singletonList(new BusinessObjectRef("item", "IC_INITIAL_IN", pk, null, null)));
    }
    private static InitializtionInVO bill(final String pk, final String code, final String group, final String org,
            final String typePk, final String typeCode) {
        final InitializtionInHeadVO head = new InitializtionInHeadVO() {
            public String getCgeneralhid() { return pk; }
            public String getVbillcode() { return code; }
            public String getPk_group() { return group; }
            public String getPk_org() { return org; }
            public String getCtrantypeid() { return typePk; }
            public String getVtrantypecode() { return typeCode; }
        };
        return new InitializtionInVO() {
            public IBillMeta getMetaData() { return META; }
            public InitializtionInHeadVO getHead() { return head; }
        };
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void closeFailed(InventoryInitialImportCapture scope, Tx transaction) {
        try {
            scope.close();
            throw new AssertionError("An unfinished capture was silently closed");
        } catch (IllegalStateException expected) {
            if (transaction.failMark) require(expected.getSuppressed().length == 1,
                    "A failed rollback marker must remain visible during cleanup");
            else require(transaction.rollbackOnly, "An unfinished capture must mark the current transaction for rollback");
        } finally { transaction.failMark = false; transaction.rollbackOnly = false; }
    }
    private static final class Tx implements TransactionSynchronizationRegistry {
        Object key = "TX"; boolean rollbackOnly, failMark;
        public Object getTransactionKey() { return key; }
        public int getTransactionStatus() { return Status.STATUS_ACTIVE; }
        public boolean getRollbackOnly() { return rollbackOnly; }
        public void setRollbackOnly() { if (failMark) throw new IllegalStateException("fixture marker failure"); rollbackOnly = true; }
        public void putResource(Object key, Object value) { throw new UnsupportedOperationException(); }
        public Object getResource(Object key) { throw new UnsupportedOperationException(); }
        public void registerInterposedSynchronization(Synchronization sync) { throw new UnsupportedOperationException(); }
    }
}
