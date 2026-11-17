package nc.impl.pu.qd.esign;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import nc.vo.pu.m25.entity.InvoiceHeaderVO;
import nc.vo.pu.m25.entity.InvoiceVO;
import nc.vo.pu.m25.entity.InvoiceVOMeta;
import nc.vo.pu.m25.enumeration.InvoiceBillAction;
import nc.vo.pu.m25.env.InvoiceUIToBSEnv;
import nc.vo.pu.qd.esign.InvoiceApCommand;
import nc.vo.pub.BusinessException;
import nc.vo.pub.IAttributeMeta;
import nc.vo.pub.IVOMeta;
import nc.vo.pub.IVOMetaStatisticInfo;
import nc.vo.pub.JavaType;
import nc.vo.pub.SuperVO;
import nc.vo.pub.VOStatus;
import nc.vo.pub.lang.UFBoolean;
import nc.vo.pubapp.pattern.model.meta.entity.bill.BillMetaFactory;
import nc.vo.pubapp.pattern.model.meta.entity.bill.IBillMeta;
import nc.vo.pubapp.pflow.PfUserObject;
import nc.vo.uapbd.qd.esign.OperationContext;

public final class InvoiceApOperationAdapterTest {
    private static final String GROUP = "1001A100000000000001";
    private static final String ORG = "1001A100000000000002";
    private static final String PK = "1001A100000000000003";
    private static final String PAYABLE = "1001A100000000000004";

    public static void main(String[] args) throws Exception {
        verify(false);
        verify(true);
        Fixture noPayable = new Fixture(false);
        noPayable.changePayables = false;
        reject(noPayable, noPayable.command, true);
        Fixture noCancel = new Fixture(true);
        noCancel.changePayables = false;
        reject(noCancel, noCancel.command, true);
        Fixture orphan = new Fixture(true);
        orphan.keepPayableHeader = true;
        reject(orphan, orphan.command, true);
        Fixture wrongResult = new Fixture(false);
        wrongResult.returned = new InvoiceVO[] {invoice(PAYABLE, true)};
        reject(wrongResult, wrongResult.command, true);
        Fixture changedInvoice = new Fixture(false);
        InvoiceVO unrelated = invoice(PAYABLE, false);
        reject(changedInvoice, new InvoiceApCommand("SENDAP", "25", unrelated, false), false);
        Fixture wrongAction = new Fixture(false);
        reject(wrongAction, new InvoiceApCommand("CANCELSENDAP", "25", wrongAction.before, false), false);
        Fixture stale = new Fixture(false);
        InvoiceVO edited = invoice(PK, false);
        edited.getParentVO().setVbillcode("CHANGED");
        reject(stale, new InvoiceApCommand("SENDAP", "25", edited, false), false);
    }

    private static void verify(boolean cancel) throws Exception {
        Fixture fixture = new Fixture(cancel);
        fixture.adapter.validate(fixture.context, fixture.command);
        Serializable result = fixture.adapter.execute(fixture.context, fixture.command);
        check(result == fixture.returned, "PF result was replaced");
        check(PK.equals(fixture.adapter.describeResult(fixture.context, result).get(0).getObjectPk()), "Wrong invoice");
        check("PU-25".equals(fixture.adapter.describeResult(fixture.context, result).get(0).getBillCode()), "Wrong bill number");
        reject(fixture, fixture.command, false);
        check(fixture.calls == 1, "The completed operation was dispatched again");
    }

    private static void reject(Fixture fixture, InvoiceApCommand command, boolean dispatched) throws Exception {
        int oldCalls = fixture.calls;
        try {
            fixture.adapter.execute(fixture.context, command);
            throw new AssertionError("Invalid AP outcome was accepted");
        } catch (BusinessException expected) {
            check(fixture.calls == oldCalls + (dispatched ? 1 : 0), "Unexpected PF call count");
        }
    }

    private static final class Fixture implements InvoiceApOperationAdapter.Records, InvoiceApOperationAdapter.Platform {
        final boolean cancel;
        final InvoiceVO before;
        final InvoiceVO after;
        final OperationContext context;
        final InvoiceApCommand command;
        final InvoiceApOperationAdapter adapter;
        Serializable returned;
        int calls;
        boolean changePayables = true;
        boolean keepPayableHeader;

        Fixture(boolean cancel) throws Exception {
            this.cancel = cancel;
            before = InvoiceApOperationAdapterTest.invoice(PK, cancel);
            after = InvoiceApOperationAdapterTest.invoice(PK, !cancel);
            returned = new InvoiceVO[] {after};
            String button = cancel ? "CancelToAP" : "SendToAP";
            String action = cancel ? "CANCELSENDAP" : "SENDAP";
            context = InvoiceApCommand.draft(button, GROUP, before);
            command = new InvoiceApCommand(action, "25", before, true);
            adapter = new InvoiceApOperationAdapter(this, this);
        }

        @Override public InvoiceHeaderVO invoice(String pk) {
            check(PK.equals(pk), "Unexpected invoice lookup");
            return (calls == 0 ? before : after).getParentVO();
        }

        @Override public void verifyLines(InvoiceVO bill, String invoicePk) {
            check(PK.equals(invoicePk), "Unexpected detail lookup");
        }

        @Override public Set<String> payables(String invoicePk, String group) {
            check(PK.equals(invoicePk) && GROUP.equals(group), "Unexpected payable scope");
            boolean live = calls == 0 || !changePayables ? cancel : !cancel;
            return live ? Collections.singleton(PAYABLE) : Collections.<String>emptySet();
        }

        @Override public boolean payableExists(String payablePk) {
            check(PAYABLE.equals(payablePk), "Unexpected original payable identity");
            return keepPayableHeader;
        }

        @Override public Object run(String action, String billType, InvoiceVO bill, PfUserObject user,
                Map<String, Object> parameters) {
            check("25".equals(billType) && bill == before, "Native PF call shape changed");
            InvoiceUIToBSEnv env = (InvoiceUIToBSEnv) user.getUserObject();
            check(action.equals(env.getTrigger().name()) && UFBoolean.TRUE.equals(env.getBManual()), "Wrong AP environment");
            check(env.getTrigger() == (cancel ? InvoiceBillAction.CANCELSENDAP : InvoiceBillAction.SENDAP),
                    "Unexpected AP trigger");
            check(parameters.get("reload_vo") == bill, "Native reload hint lost");
            check("notechecked".equals(parameters.get("notechecked")), "Native note marker lost");
            calls++;
            return returned;
        }
    }

    @SuppressWarnings("unchecked")
    private static InvoiceVO invoice(String key, boolean sent) throws Exception {
        Field cache = BillMetaFactory.class.getDeclaredField("classIndex");
        cache.setAccessible(true);
        Map<Class<?>, IBillMeta> classes = (Map<Class<?>, IBillMeta>) cache.get(BillMetaFactory.getInstance());
        classes.put(InvoiceVOMeta.class, proxy(IBillMeta.class, new InvocationHandler() {
            @Override public Object invoke(Object proxy, Method method, Object[] args) { return new IVOMeta[0]; }
        }));
        InvoiceHeaderVO header = new InvoiceHeaderVO();
        Field metadata = SuperVO.class.getDeclaredField("vometa");
        metadata.setAccessible(true);
        final IAttributeMeta keyAttribute = proxy(IAttributeMeta.class, new InvocationHandler() {
            @Override public Object invoke(Object proxy, Method method, Object[] args) {
                if ("getName".equals(method.getName())) return "pk_invoice";
                if ("getJavaType".equals(method.getName())) return JavaType.Object;
                if ("isSerializable".equals(method.getName())) return Boolean.TRUE;
                if ("isStatic".equals(method.getName())) return Boolean.FALSE;
                throw new AssertionError(method.getName());
            }
        });
        final IVOMetaStatisticInfo statistics = proxy(IVOMetaStatisticInfo.class, new InvocationHandler() {
            @Override public Object invoke(Object proxy, Method method, Object[] args) { return new IAttributeMeta[0]; }
        });
        metadata.set(header, proxy(IVOMeta.class, new InvocationHandler() {
            @Override public Object invoke(Object proxy, Method method, Object[] args) {
                return "getStatisticInfo".equals(method.getName()) ? statistics : keyAttribute;
            }
        }));
        header.setPk_invoice(key);
        header.setPk_group(GROUP);
        header.setPk_org(ORG);
        header.setVbillcode("PU-25");
        header.setFbillstatus(Integer.valueOf(3));
        header.setBapflag(sent ? UFBoolean.TRUE : UFBoolean.FALSE);
        header.setStatus(VOStatus.UNCHANGED);
        InvoiceVO invoice = new InvoiceVO();
        invoice.setParentVO(header);
        return invoice;
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }
}
