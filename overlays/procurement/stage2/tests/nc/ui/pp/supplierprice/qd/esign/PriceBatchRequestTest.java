package nc.ui.pp.supplierprice.qd.esign;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import nc.itf.uapbd.qd.esign.IPolicyService;
import nc.vo.bd.meta.BatchOperateVO;
import nc.vo.pp.supplierprice.entity.SupplierPriceVO;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.PolicyDecision;

public final class PriceBatchRequestTest {
    public static void main(String[] args) throws Exception {
        BatchOperateVO batch = new BatchOperateVO();
        batch.setAddObjs(new Object[] {new Price(null, "ORG"), new Price(null, "ORG")});
        batch.setUpdObjs(new Object[] {new Price("existing", "ORG")});
        batch.setDelObjs(new Object[] {new Price("delete", "ORG")});
        PriceBatchRequest save = PriceBatchRequest.forBatch("Save", batch);
        check("BATCH_SAVE".equals(save.context.getOperationType()), "mixed Save action domain");
        check(save.context.getObjects().size() == 4 && save.addClientIds.length == 2, "all affected price rows bound");
        check(save.addClientIds[1].equals(save.context.getObjects().get(1).getClientObjectId()), "added row ordinal");
        check("delete".equals(save.context.getObjects().get(3).getObjectPk()), "deleted row identity");
        PriceBatchRequest staged = PriceBatchRequest.forEditDelete(new Object[] {new Price(null, "ORG")});
        check("STAGE_DELETE".equals(staged.context.getOperationType()) && staged.context.getObjects().get(0).getObjectPk() == null,
                "new editor row retained in staged Delete authorization");
        batch.setDelObjs(new Object[] {new Price("existing", "ORG")});
        expectInvalid(batch, "conflicting update and delete identity");
        batch.setDelObjs(new Object[] {new Price("delete", "OTHER")});
        expectInvalid(batch, "mixed business units");
        largeBatchesPreserveNativeSaveOnlyWhenUncontrolled();
    }

    private static void expectInvalid(BatchOperateVO batch, String message) throws Exception {
        try {
            PriceBatchRequest.forBatch("Save", batch);
            throw new AssertionError(message);
        } catch (BusinessException expected) { }
    }

    private static void largeBatchesPreserveNativeSaveOnlyWhenUncontrolled() throws Exception {
        Object[] adds = new Object[1001];
        for (int i = 0; i < adds.length; i++) adds[i] = new Price(null, "ORG");
        BatchOperateVO batch = new BatchOperateVO();
        batch.setAddObjs(adds);
        PriceBatchRequest request = PriceBatchRequest.forBatch("Save", batch);
        check(SupplierPriceActionScope.nativeLargeBatch(request.context, policy(false)),
                "uncontrolled large batch uses native Save");
        try {
            SupplierPriceActionScope.nativeLargeBatch(request.context, policy(true));
            throw new AssertionError("controlled batch above core limit was allowed");
        } catch (BusinessException expected) { }
    }

    private static IPolicyService policy(final boolean enabled) {
        return (IPolicyService) Proxy.newProxyInstance(IPolicyService.class.getClassLoader(),
                new Class<?>[] {IPolicyService.class}, new InvocationHandler() {
                    @Override public Object invoke(Object proxy, Method method, Object[] args) {
                        check("resolve".equals(method.getName()) && "ORG".equals(args[0])
                                && "40050200".equals(args[1]) && "Save".equals(args[2]), "policy resolution scope");
                        return new PolicyDecision(true, enabled, false, false, true, 1L);
                    }
                });
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static final class Price extends SupplierPriceVO {
        private static final long serialVersionUID = 1L;
        private final String id;
        private final String org;

        Price(String id, String org) {
            this.id = id;
            this.org = org;
        }

        @Override public String getPk_supplierprice() { return id; }
        @Override public String getPk_group() { return "GROUP"; }
        @Override public String getPk_org() { return org; }
    }
}
