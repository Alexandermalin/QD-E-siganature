package nc.test.qd.esign;

import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import nc.impl.so.qd.esign.SaleOrderOperationAdapter;
import nc.impl.so.qd.esign.SaleOrderPfSaveAdapter;
import nc.itf.uap.pf.IplatFormEntry;
import nc.vo.pub.BusinessException;
import nc.vo.pub.IAttributeMeta;
import nc.vo.pub.ISuperVO;
import nc.vo.pub.IVOMeta;
import nc.vo.pubapp.pattern.model.meta.entity.bill.IBillMeta;
import nc.vo.pubapp.pflow.PFReturnObject;
import nc.vo.pubapp.pflow.PfUserObject;
import nc.vo.so.m30.entity.SaleOrderHVO;
import nc.vo.so.m30.entity.OffsetTempVO;
import nc.vo.so.m30.entity.SaleOrderUserObject;
import nc.vo.so.m30.entity.SaleOrderVO;
import nc.vo.so.m30.sobalance.entity.SoBalanceHVO;
import nc.vo.so.m30.sobalance.entity.SoBalanceVO;
import nc.vo.so.qd.esign.SaleOrderPfSaveCommand;
import nc.vo.so.qd.esign.SaleOrderPfSaveResult;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.SaleOrderPilotScope;

/** Offline contract checks only; no NC platform, database, or transaction is simulated. */
public final class SaleOrderPfSaveAdapterTests {
    private interface Checked { void run() throws Exception; }
    private static int assertions;
    private static final String G = SaleOrderPilotScope.GROUP_ID;
    private static final String O = SaleOrderPilotScope.ORG_ID;
    private static final IBillMeta FIXTURE_META = new IBillMeta() {
        public Map<String, Object> getBusinessAttribute() { return Collections.emptyMap(); }
        public IAttributeMeta[] getChildForeignKeys() { return new IAttributeMeta[0]; }
        public IVOMeta[] getChildren() { return new IVOMeta[0]; }
        public String getComponentName() { return "OFFLINE_SALES_FIXTURE"; }
        public IVOMeta getParent() { throw new UnsupportedOperationException(); }
        public Class<? extends ISuperVO> getVOClass(IVOMeta meta) { throw new UnsupportedOperationException(); }
        public IVOMeta getVOMeta(Class<? extends ISuperVO> type) { throw new UnsupportedOperationException(); }
    };
    private static final IVOMeta CHILD_META = (IVOMeta) Proxy.newProxyInstance(IVOMeta.class.getClassLoader(),
            new Class<?>[] { IVOMeta.class }, new InvocationHandler() {
                public Object invoke(Object proxy, Method method, Object[] args) {
                    if ("equals".equals(method.getName())) { return Boolean.valueOf(proxy == args[0]); }
                    throw new UnsupportedOperationException();
                }
            });
    private static final IBillMeta CHILD_FIXTURE_META = new IBillMeta() {
        public Map<String, Object> getBusinessAttribute() { return Collections.emptyMap(); }
        public IAttributeMeta[] getChildForeignKeys() { return new IAttributeMeta[0]; }
        public IVOMeta[] getChildren() { return new IVOMeta[] { CHILD_META }; }
        public String getComponentName() { return "OFFLINE_BALANCE_FIXTURE"; }
        public IVOMeta getParent() { throw new UnsupportedOperationException(); }
        public Class<? extends ISuperVO> getVOClass(IVOMeta meta) { throw new UnsupportedOperationException(); }
        public IVOMeta getVOMeta(Class<? extends ISuperVO> type) { throw new UnsupportedOperationException(); }
    };

    public static void main(String[] args) throws Exception { run(); }

    public static void run() throws Exception {
        assertions = 0;
        final OperationContext context = context("40060301", "Save", "INSERT", O, null);
        final SaleOrderPfSaveCommand command = command(order("NEW", G, O, "30", "SO-1"), params(), user());
        final Fake adapter = new Fake();
        adapter.returnValue = new SaleOrderVO[] { order("NEW", G, O, "30", "SO-1") };
        adapter.saved = head("NEW", G, O, "30", "SO-1");
        SaleOrderPfSaveResult result = (SaleOrderPfSaveResult) adapter.execute(context, command);
        check(adapter.calls == 1, "native PF entry called exactly once");
        check("NEW".equals(result.getPrimaryKey()) && "SO-1".equals(result.getBillCode()), "persisted identity returned");
        check("NEW".equals(adapter.describeResult(context, result).get(0).getObjectPk()), "request receives persisted key");

        final Fake wrapped = new Fake();
        wrapped.saved = head("NEW", G, O, "30", "SO-1");
        wrapped.returnValue = PFReturnObject.createReturnObjs(
                new SaleOrderVO[] { order("NEW", G, O, "30", "SO-1") }, null);
        wrapped.execute(context, command);
        check(wrapped.calls == 1, "native PF return wrapper accepted");

        final Fake routed = new Fake();
        routed.saved = head("NEW", G, O, "30", "SO-1");
        routed.returnValue = new SaleOrderVO[] { order("NEW", G, O, "30", "SO-1") };
        SaleOrderOperationAdapter installedRoute = new SaleOrderOperationAdapter() {
            @Override protected SaleOrderPfSaveAdapter pfAdapter() { return routed; }
        };
        installedRoute.validate(context, command);
        Serializable routedResult = installedRoute.execute(context, command);
        check(routed.calls == 1 && "NEW".equals(installedRoute.describeResult(context, routedResult).get(0).getObjectPk()),
                "installed sales adapter routes the pilot to PF once and returns the saved identity");

        PfUserObject withoutBalance = user();
        ((SaleOrderUserObject) withoutBalance.getUserObject()).setSoBalanceVO(null);
        new Fake().validate(context, command(order("NEW", G, O, "30", "SO-1"), params(), withoutBalance));
        check(true, "a missing balance is also accepted");
        PfUserObject emptyChildBalance = user();
        ((SaleOrderUserObject) emptyChildBalance.getUserObject()).setSoBalanceVO(emptyBalanceWithChildMeta());
        new Fake().validate(context, command(order("NEW", G, O, "30", "SO-1"), params(), emptyChildBalance));
        check(true, "native balance with a child type and no rows is accepted");

        expectFailure(new Checked() { public void run() throws Exception {
            PfUserObject unsafe = user();
            ((SaleOrderUserObject) unsafe.getUserObject()).setOffsetTempVO(new OffsetTempVO());
            new Fake().validate(context, command(order("NEW", G, O, "30", "SO-1"), params(), unsafe));
        }});
        expectFailure(new Checked() { public void run() throws Exception {
            PfUserObject unsafe = user();
            ((SaleOrderUserObject) unsafe.getUserObject()).getSoBalanceVO().setParentVO(new SoBalanceHVO());
            new Fake().validate(context, command(order("NEW", G, O, "30", "SO-1"), params(), unsafe));
        }});
        expectFailure(new Checked() { public void run() throws Exception {
            PfUserObject unsafe = user();
            ((SaleOrderUserObject) unsafe.getUserObject()).setSoBalanceVO(balanceWithChild());
            new Fake().validate(context, command(order("NEW", G, O, "30", "SO-1"), params(), unsafe));
        }});
        expectFailure(new Checked() { public void run() throws Exception {
            PfUserObject unsafe = user();
            ((SaleOrderUserObject) unsafe.getUserObject()).getSoBalanceVO().setListenerflag(new nc.vo.pub.lang.UFBoolean(true));
            new Fake().validate(context, command(order("NEW", G, O, "30", "SO-1"), params(), unsafe));
        }});
        expectFailure(new Checked() { public void run() throws Exception {
            PfUserObject unsafe = user();
            ((SaleOrderUserObject) unsafe.getUserObject()).setThisGatheringMny(new nc.vo.pub.lang.UFDouble("1"));
            new Fake().validate(context, command(order("NEW", G, O, "30", "SO-1"), params(), unsafe));
        }});

        expectFailure(new Checked() { public void run() throws Exception {
            new SaleOrderOperationAdapter().validate(context, null);
        }});
        expectFailure(new Checked() { public void run() throws Exception {
            new Fake().validate(context, command(order("NEW", G, "OTHER", "30", "SO-1"), params(), user()));
        }});
        expectFailure(new Checked() { public void run() throws Exception {
            new Fake().validate(context("40060301", "Save", "INSERT", "OTHER", null),
                    command(order("NEW", G, "OTHER", "30", "SO-1"), params(), user()));
        }});
        expectFailure(new Checked() { public void run() throws Exception {
            new Fake().validate(context("40060301", "Save", "UPDATE", O, "OLD"), command);
        }});
        expectFailure(new Checked() { public void run() throws Exception {
            new Fake().validate(context, command(order(null, G, O, "30", "SO-1"), params(), user()));
        }});
        expectFailure(new Checked() { public void run() throws Exception {
            new Fake().validate(context, command(order("NEW", G, O, "OTHER", "SO-1"), params(), user(), "OTHER"));
        }});
        expectFailure(new Checked() { public void run() throws Exception {
            Map<String, Object> invalid = params(); invalid.put("nolockandconsist", "Y");
            new Fake().validate(context, command(order("NEW", G, O, "30", "SO-1"), invalid, user()));
        }});
        expectFailure(new Checked() { public void run() throws Exception {
            PfUserObject unsafe = user(); unsafe.setBusinessCheckMap(Collections.singletonMap("skip", Boolean.TRUE));
            new Fake().validate(context, command(order("NEW", G, O, "30", "SO-1"), params(), unsafe));
        }});
        expectFailure(new Checked() { public void run() throws Exception {
            Fake existing = new Fake(); existing.existing = head("NEW", G, O, "30", "SO-1");
            existing.validate(context, command);
        }});
        expectFailure(new Checked() { public void run() throws Exception {
            Fake wrong = new Fake(); wrong.saved = head("NEW", G, O, "30", "SO-1");
            wrong.returnValue = new SaleOrderVO[] { order("OTHER", G, O, "30", "SO-1") };
            wrong.execute(context, command);
        }});
        System.out.println("PASS SaleOrderPfSaveAdapterTests assertions=" + assertions
                + " (native PF, database, and transaction not invoked)");
    }

    private static final class Fake extends SaleOrderPfSaveAdapter {
        SaleOrderHVO existing;
        SaleOrderHVO saved;
        Serializable returnValue;
        int calls;
        @Override protected SaleOrderHVO loadHeader(String key) { return calls == 0 ? existing : saved; }
        @Override protected IplatFormEntry platformEntry() {
            return (IplatFormEntry) Proxy.newProxyInstance(IplatFormEntry.class.getClassLoader(),
                    new Class<?>[] { IplatFormEntry.class }, new InvocationHandler() {
                public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                    if (!"processAction".equals(method.getName())) { throw new AssertionError(method.getName()); }
                    check("WRITE".equals(args[0]) && "30".equals(args[1]), "native WRITE and type retained");
                    calls++;
                    return returnValue;
                }
            });
        }
    }

    private static OperationContext context(String node, String button, String action, String org, String pk) {
        return new OperationContext("R", "R", G, org, node, "Sales", button, "Save", action, null,
                Collections.singletonList(new BusinessObjectRef("draft", "SO_SALE_ORDER", pk, null, null)));
    }
    private static Map<String, Object> params() {
        Map<String, Object> map = new HashMap<String, Object>(); map.put("notechecked", "notechecked"); return map;
    }
    private static PfUserObject user() {
        PfUserObject pf = new PfUserObject(); SaleOrderUserObject sales = new SaleOrderUserObject();
        sales.setIsclientsave(true); sales.setSoBalanceVO(emptyBalance()); pf.setUserObject(sales); return pf;
    }
    private static SoBalanceVO emptyBalance() {
        return new SoBalanceVO() { public IBillMeta getMetaData() { return FIXTURE_META; } };
    }
    private static SoBalanceVO balanceWithChild() {
        return new SoBalanceVO() {
            public IBillMeta getMetaData() { return CHILD_FIXTURE_META; }
            public ISuperVO[] getChildren(IVOMeta meta) { return new ISuperVO[] { null }; }
        };
    }
    private static SoBalanceVO emptyBalanceWithChildMeta() {
        return new SoBalanceVO() { public IBillMeta getMetaData() { return CHILD_FIXTURE_META; } };
    }
    private static SaleOrderPfSaveCommand command(SaleOrderVO bill, Map<String, Object> params, PfUserObject user) {
        return command(bill, params, user, "30");
    }
    private static SaleOrderPfSaveCommand command(SaleOrderVO bill, Map<String, Object> params,
            PfUserObject user, String type) {
        return new SaleOrderPfSaveCommand(type, bill, user, null, params);
    }
    private static SaleOrderHVO head(final String pk, final String group, final String org,
            final String type, final String code) {
        SaleOrderHVO head = new SaleOrderHVO() {
            public String getPk_group() { return group; }
            public String getPk_org() { return org; }
            public String getVtrantypecode() { return type; }
            public String getVbillcode() { return code; }
            public String getPrimaryKey() { return pk; }
        };
        head.setStatus(2);
        return head;
    }
    private static SaleOrderVO order(final String pk, final String group, final String org,
            final String type, final String code) {
        final SaleOrderHVO header = head(pk, group, org, type, code);
        return new SaleOrderVO() {
            public IBillMeta getMetaData() { return FIXTURE_META; }
            public SaleOrderHVO getParentVO() { return header; }
            public String getPrimaryKey() { return pk; }
        };
    }
    private static void expectFailure(Checked action) throws Exception {
        try { action.run(); } catch (BusinessException expected) { assertions++; return; }
        throw new AssertionError("Invalid sales PF request was accepted");
    }
    private static void check(boolean ok, String message) {
        if (!ok) { throw new AssertionError(message); } assertions++;
    }
}
