package nc.impl.purp.qd.esign;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nc.itf.pp.supplierprice.ISupplierPriceService;
import nc.itf.purp.qd.esign.ISupplierPriceBatchOperationAdapter;
import nc.itf.uapbd.qd.esign.spi.PerObjectAuditOperations;
import nc.vo.bd.meta.BatchOperateVO;
import nc.vo.pp.supplierprice.entity.SupplierPriceVO;
import nc.vo.purp.qd.esign.SupplierPriceBatchCommand;
import nc.vo.purp.qd.esign.SupplierPriceEditDeleteCommand;
import nc.vo.pub.BusinessException;
import nc.vo.pub.IVOMeta;
import nc.vo.pub.SuperVO;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;

public final class SupplierPriceBatchAdapterTest {
    private static final IVOMeta ROW_METADATA = (IVOMeta) Proxy.newProxyInstance(IVOMeta.class.getClassLoader(),
            new Class<?>[] { IVOMeta.class }, new InvocationHandler() {
                public Object invoke(Object proxy, Method method, Object[] args) {
                    check("getAttribute".equals(method.getName()), "unexpected native VO metadata access");
                    return null;
                }
            });

    public static void main(String[] args) throws Exception {
        exposesAuditOperationsOnRegisteredInterface();
        savesMixedBatchOnceAndCorrelatesUsingOriginalRows();
        rejectsUnboundRowsBeforeNativeSave();
        rejectsReorderedNativeRowsAgainstUnchangedModelIndices();
        deletesPersistedRowsOnlyWhenNativeSoftDeleteSucceeds();
        authorizesEditModeStagingWithoutNativeWrite();
        rejectsOutOfScopeAndSoftDeletedInputs();
    }

    private static void exposesAuditOperationsOnRegisteredInterface() {
        check(PerObjectAuditOperations.class.isAssignableFrom(ISupplierPriceBatchOperationAdapter.class),
                "an NC proxy exposing only the registered interface would hide the audit operations");
    }

    private static void savesMixedBatchOnceAndCorrelatesUsingOriginalRows() throws Exception {
        Fixture f = new Fixture();
        SupplierPriceBatchCommand command = mixedCommand(f);
        OperationContext context = mixedContext();
        Map<String, String> operations = f.adapter.auditOperations(context, command);
        check(operations.size() == 4 && "INSERT".equals(operations.get("new-a"))
                && "INSERT".equals(operations.get("new-b")) && "UPDATE".equals(operations.get("update"))
                && "DELETE".equals(operations.get("delete")), "audit operations did not reflect native partitions");
        Serializable result = f.adapter.execute(context, command);
        check(result == command.getBatch() && f.calls == 1, "expected one call and exact native envelope");
        BatchOperateVO returned = (BatchOperateVO) result;
        check(Arrays.equals(returned.getAddIndexs(), new int[] { 7, 4 })
                && Arrays.equals(returned.getUpdIndexs(), new int[] { 2 })
                && Arrays.equals(returned.getDelIndexs(), new int[] { 4 }), "native model indices changed");
        List<BusinessObjectRef> refs = f.adapter.describeResult(context, command, result);
        check(refs.size() == 4, "missing rows from described result");
        checkRef(refs.get(0), "new-b", "native-2");
        checkRef(refs.get(1), "delete", "pk-delete");
        checkRef(refs.get(2), "new-a", "native-1");
        checkRef(refs.get(3), "update", "pk-update");
    }

    private static void rejectsUnboundRowsBeforeNativeSave() throws Exception {
        final Fixture f = new Fixture();
        final SupplierPriceBatchCommand command = mixedCommand(f);
        command.getBatch().setDelObjs(command.getBatch().getUpdObjs());
        reject(new CheckedAction() {
            public void run() throws BusinessException {
                f.adapter.execute(mixedContext(), command);
            }
        });
        check(f.calls == 0, "duplicate persisted PK passed into native save");

        final SupplierPriceBatchCommand reusedAdd = mixedCommand(f);
        reusedAdd.getBatch().getAddObjs()[1] = reusedAdd.getBatch().getAddObjs()[0];
        reject(new CheckedAction() {
            public void run() throws BusinessException {
                f.adapter.execute(mixedContext(), reusedAdd);
            }
        });
        check(f.calls == 0, "aliased addition passed into native save");
    }

    private static void rejectsReorderedNativeRowsAgainstUnchangedModelIndices() throws Exception {
        final Fixture f = new Fixture();
        f.reorderAdds = true;
        final SupplierPriceBatchCommand command = mixedCommand(f);
        reject(new CheckedAction() {
            public void run() throws BusinessException {
                f.adapter.execute(mixedContext(), command);
            }
        });
        check(f.calls == 1, "native result mismatch was not checked after the write");
    }

    private static void deletesPersistedRowsOnlyWhenNativeSoftDeleteSucceeds() throws Exception {
        final Fixture f = new Fixture();
        f.prices.put("one", row("one"));
        f.prices.put("two", row("two"));
        BatchOperateVO batch = new BatchOperateVO();
        batch.setDelObjs(new Object[] { row("one"), row("two") });
        batch.setDelIndexs(new int[] { 3, 8 });
        final SupplierPriceBatchCommand command = new SupplierPriceBatchCommand(batch, null);
        final OperationContext context = context("Delete", "DELETE", ref("two", "two"), ref("one", "one"));
        f.skipSoftDelete = true;
        reject(new CheckedAction() {
            public void run() throws BusinessException {
                f.adapter.execute(context, command);
            }
        });
        f.skipSoftDelete = false;
        Serializable result = f.adapter.execute(context, command);
        check(result == batch && f.calls == 2, "delete did not use native batch envelope");
        checkRef(f.adapter.describeResult(context, command, result).get(0), "two", "two");
    }

    private static void authorizesEditModeStagingWithoutNativeWrite() throws Exception {
        Fixture f = new Fixture();
        f.prices.put("existing", row("existing"));
        List<BusinessObjectRef> staged = Arrays.asList(ref("a", null), ref("b", "existing"));
        OperationContext context = context("Delete", "STAGE_DELETE", staged.get(1), staged.get(0));
        SupplierPriceEditDeleteCommand command = new SupplierPriceEditDeleteCommand(staged);
        check("STAGE_DELETE".equals(f.adapter.auditOperations(context, command).get("a")),
                "staging audited as a persisted delete");
        check(f.adapter.execute(context, command) == null && f.calls == 0, "staging wrote supplier prices");
        checkRef(f.adapter.describeResult(context, command, null).get(0), "b", "existing");
    }

    private static void rejectsOutOfScopeAndSoftDeletedInputs() throws Exception {
        final Fixture f = new Fixture();
        final SupplierPriceBatchCommand command = mixedCommand(f);
        f.prices.get("pk-update").setPk_org("another-org");
        reject(new CheckedAction() {
            public void run() throws BusinessException {
                f.adapter.execute(mixedContext(), command);
            }
        });
        f.prices.get("pk-update").setPk_org("org");
        f.prices.get("pk-update").setDr(Integer.valueOf(1));
        reject(new CheckedAction() {
            public void run() throws BusinessException {
                f.adapter.execute(mixedContext(), command);
            }
        });
        check(f.calls == 0, "out-of-scope or deleted row passed into native save");
    }

    private static SupplierPriceBatchCommand mixedCommand(Fixture f) {
        f.prices.put("pk-update", row("pk-update"));
        f.prices.put("pk-delete", row("pk-delete"));
        BatchOperateVO batch = new BatchOperateVO();
        batch.setAddObjs(new Object[] { row(null), row(null) });
        batch.setUpdObjs(new Object[] { row("pk-update") });
        batch.setDelObjs(new Object[] { row("pk-delete") });
        batch.setAddIndexs(new int[] { 7, 4 });
        batch.setUpdIndexs(new int[] { 2 });
        batch.setDelIndexs(new int[] { 4 });
        return new SupplierPriceBatchCommand(batch, new String[] { "new-a", "new-b" });
    }

    private static OperationContext mixedContext() {
        return context("Save", "BATCH_SAVE", ref("new-b", null), ref("delete", "pk-delete"), ref("new-a", null),
                ref("update", "pk-update"));
    }

    private static OperationContext context(String button, String operation, BusinessObjectRef... refs) {
        return new OperationContext("request", "batch", "group", "org", "40050200", "Supplier Prices", button,
                button, operation, null, Arrays.asList(refs));
    }

    private static BusinessObjectRef ref(String id, String pk) {
        return new BusinessObjectRef(id, "PURP_SUPPLIER_PRICE", pk, null, null);
    }

    private static SupplierPriceVO row(String pk) {
        SupplierPriceVO row = new SupplierPriceVO();
        try {
            Field metadata = SuperVO.class.getDeclaredField("vometa");
            metadata.setAccessible(true);
            metadata.set(row, ROW_METADATA);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
        row.setPk_supplierprice(pk);
        row.setPk_group("group");
        row.setPk_org("org");
        row.setDr(Integer.valueOf(0));
        return row;
    }

    private static void checkRef(BusinessObjectRef ref, String id, String pk) {
        check(id.equals(ref.getClientObjectId()) && pk.equals(ref.getObjectPk()), "incorrect correlation for " + id);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void reject(CheckedAction action) throws Exception {
        try {
            action.run();
            throw new AssertionError("Expected BusinessException");
        } catch (BusinessException expected) {
            // Expected validation failure.
        }
    }

    private interface CheckedAction {
        void run() throws BusinessException;
    }

    private static final class Fixture implements SupplierPriceBatchAdapter.PriceLookup, InvocationHandler {
        final Map<String, SupplierPriceVO> prices = new HashMap<String, SupplierPriceVO>();
        final SupplierPriceBatchAdapter adapter = new SupplierPriceBatchAdapter(
                (ISupplierPriceService) Proxy.newProxyInstance(ISupplierPriceService.class.getClassLoader(),
                        new Class<?>[] { ISupplierPriceService.class }, this), this);
        int calls;
        boolean reorderAdds;
        boolean skipSoftDelete;

        public SupplierPriceVO find(String pk) {
            return prices.get(pk);
        }

        public Object invoke(Object proxy, Method method, Object[] args) {
            check("batchSave".equals(method.getName()), "unexpected native call");
            calls++;
            BatchOperateVO batch = (BatchOperateVO) args[0];
            Object[] adds = batch.getAddObjs();
            if (adds != null) {
                Object[] result = new Object[adds.length];
                for (int index = 0; index < adds.length; index++) {
                    String pk = "native-" + (index + 1);
                    ((SupplierPriceVO) adds[index]).setPk_supplierprice(pk);
                    prices.put(pk, row(pk));
                    result[reorderAdds ? adds.length - 1 - index : index] = row(pk);
                }
                batch.setAddObjs(result);
            }
            if (batch.getDelObjs() != null && !skipSoftDelete) {
                for (Object deleted : batch.getDelObjs()) {
                    prices.get(((SupplierPriceVO) deleted).getPk_supplierprice()).setDr(Integer.valueOf(1));
                }
            }
            return batch;
        }
    }
}
