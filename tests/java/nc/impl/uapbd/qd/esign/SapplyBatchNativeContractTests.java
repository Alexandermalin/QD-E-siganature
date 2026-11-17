package nc.impl.uapbd.qd.esign;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import nc.bs.framework.aop.AspectManager;
import nc.bs.framework.aop.AspectMeta;
import nc.bs.framework.core.ComponentMeta;
import nc.bs.framework.core.Container;
import nc.bs.framework.core.TxAttribute;
import nc.bs.framework.server.ComponentMetaImpl;
import nc.bs.framework.server.PublicMetaRepo;
import nc.bs.pub.pf.IPfAfterAction;
import nc.bs.pub.pf.IPfBeforeAction;
import nc.bs.uapbd.qd.esign.audit.SapplyWorkflowBatchAspect;
import nc.bs.uapbd.qd.esign.audit.SapplyWorkflowBatchAspect.UnitCallback;
import nc.itf.uap.pf.IWorkflowMachine;
import nc.itf.uap.pf.IplatFormEntry;
import nc.itf.ic.qd.esign.IInventoryPfStateAdapter;
import nc.impl.ic.qd.esign.InventoryPfStateAdapter;
import nc.itf.uapbd.qd.esign.ISapplyBatchTransactionService;
import nc.vo.pub.BusinessException;
import org.granite.convert.ConverterManager;

public final class SapplyBatchNativeContractTests {
    public static void run() throws Exception {
        final Repo repo = new Repo();
        final AspectMeta expected = new AspectMeta(SapplyWorkflowBatchAspect.class);
        final AspectMeta[][] aspects = {{expected}};
        final AspectMeta[][] entryAspects = {{expected}};
        final AspectManager manager = proxy(AspectManager.class, new InvocationHandler() {
            public Object invoke(Object instance, Method method, Object[] args) {
                if ("getAspectMetas".equals(method.getName()) && args[0] == repo.workflow) return aspects[0];
                if ("getAspectMetas".equals(method.getName()) && args[0] == repo.entry) return entryAspects[0];
                throw new AssertionError("Unexpected aspect lookup: " + method.getName());
            }
        });
        Container container = proxy(Container.class, new InvocationHandler() {
            public Object invoke(Object instance, Method method, Object[] args) {
                if ("getExtension".equals(method.getName()) && args[0] == AspectManager.class) return manager;
                if ("getExtension".equals(method.getName()) && args[0] == ConverterManager.class) return null;
                throw new AssertionError("Unexpected container call: " + method.getName());
            }
        });

        rejectRegistration(repo, "CMT");
        repo.workflow = component(container, IWorkflowMachine.class, TxAttribute.NONE, null);
        rejectRegistration(repo, "CMT");
        repo.workflow.setTxAttribute(TxAttribute.CMT);
        rejectRegistration(repo, "IplatFormEntry");
        repo.entry = component(container, IplatFormEntry.class, TxAttribute.NONE, null);
        rejectRegistration(repo, "IplatFormEntry");
        repo.entry.setTxAttribute(TxAttribute.CMT);
        entryAspects[0] = new AspectMeta[0];
        rejectRegistration(repo, "IplatFormEntry");
        entryAspects[0] = new AspectMeta[]{expected};
        rejectRegistration(repo, "逐单处理服务");
        repo.callback = component(container, UnitCallback.class, TxAttribute.BMT, SapplyWorkflowBatchUnit.class);
        rejectRegistration(repo, "逐单处理服务");
        repo.callback.setTxAttribute(TxAttribute.CMT);
        repo.callback.setRemote(true);
        rejectRegistration(repo, "逐单处理服务");
        repo.callback.setRemote(false);
        repo.callback.setImplementation(Object.class);
        rejectRegistration(repo, "逐单处理服务");
        repo.callback.setImplementation(SapplyWorkflowBatchUnit.class);
        aspects[0] = new AspectMeta[0];
        rejectRegistration(repo, "切面");
        aspects[0] = new AspectMeta[]{expected, expected};
        rejectRegistration(repo, "切面");
        aspects[0] = new AspectMeta[]{expected};
        rejectRegistration(repo, "回执服务");
        repo.transactions = component(container, ISapplyBatchTransactionService.class, TxAttribute.NONE,
                SapplyBatchTransactionServiceImpl.class);
        rejectRegistration(repo, "回执服务");
        repo.transactions.setTxAttribute(TxAttribute.CMT);
        repo.transactions.setRemote(true);
        rejectRegistration(repo, "回执服务");
        repo.transactions.setRemote(false);
        repo.transactions.setImplementation(Object.class);
        rejectRegistration(repo, "回执服务");
        repo.transactions.setImplementation(SapplyBatchTransactionServiceImpl.class);
        rejectRegistration(repo,"库存适配服务");
        repo.adapter=component(container,IInventoryPfStateAdapter.class,TxAttribute.NONE,InventoryPfStateAdapter.class);
        rejectRegistration(repo,"库存适配服务");
        repo.adapter.setTxAttribute(TxAttribute.CMT);repo.adapter.setRemote(true);
        rejectRegistration(repo,"库存适配服务");
        repo.adapter.setRemote(false);repo.adapter.setImplementation(Object.class);
        rejectRegistration(repo,"库存适配服务");
        repo.adapter.setImplementation(InventoryPfStateAdapter.class);
        SapplyBatchNativeContract.requireRegistration(repo);
        repo.callback.setTxAttribute(TxAttribute.NONE);
        SapplyBatchNativeContract.requireRegistration(repo);

        SapplyBatchNativeContract.requireNoOuterCallbacks(null);
        SapplyBatchNativeContract.requireNoOuterCallbacks(new Object());
        Object standardInventoryCallback=Class.forName("nc.bs.ic.pf.PfBeforeAndAfterActionForIC").newInstance();
        if(!SapplyBatchNativeContract.requireNoOuterCallbacks(standardInventoryCallback))
            throw new AssertionError("The verified inventory callback was not identified");
        try {SapplyBatchNativeContract.requireNoOuterCallbacks(new CustomizedInventoryCallback());
            throw new AssertionError("An unverified inventory callback subclass was accepted");}
        catch(BusinessException expectedFailure) { }
        rejectCallback(IPfBeforeAction.class);
        rejectCallback(IPfAfterAction.class);
        System.out.println("PASS SapplyBatchNativeContractTests (exact NC registration and outer callbacks)");
    }

    private static ComponentMetaImpl component(Container container, Class<?> type, TxAttribute tx, Class<?> implementation) {
        ComponentMetaImpl meta = new ComponentMetaImpl(container);
        meta.setName(type.getName());
        meta.setPublic(true);
        meta.addInterface(type);
        meta.setTxAttribute(tx);
        if (implementation != null) meta.setImplementation(implementation);
        return meta;
    }

    private static void rejectRegistration(Repo repo, String message) throws Exception {
        try { SapplyBatchNativeContract.requireRegistration(repo); }
        catch (BusinessException expected) {
            if (expected.getMessage().contains(message)) return;
            throw new AssertionError(expected);
        }
        throw new AssertionError("invalid registration accepted: " + message);
    }

    private static void rejectCallback(Class<?> type) throws Exception {
        Object handler = proxy(type, new InvocationHandler() {
            public Object invoke(Object instance, Method method, Object[] args) {
                throw new AssertionError("preflight must not call native callbacks");
            }
        });
        try { SapplyBatchNativeContract.requireNoOuterCallbacks(handler); }
        catch (BusinessException expected) { return; }
        throw new AssertionError("unverified native callback accepted: " + type.getName());
    }

    private static final class CustomizedInventoryCallback extends nc.bs.ic.pf.PfBeforeAndAfterActionForIC { }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }

    private static final class Repo extends PublicMetaRepo {
        ComponentMetaImpl workflow;
        ComponentMetaImpl entry;
        ComponentMetaImpl callback;
        ComponentMetaImpl transactions;
        ComponentMetaImpl adapter;

        @Override public ComponentMeta getComponentMeta(String name) {
            if (IWorkflowMachine.class.getName().equals(name)) return workflow;
            if (IplatFormEntry.class.getName().equals(name)) return entry;
            if (UnitCallback.class.getName().equals(name)) return callback;
            if (ISapplyBatchTransactionService.class.getName().equals(name)) return transactions;
            if (IInventoryPfStateAdapter.class.getName().equals(name)) return adapter;
            throw new AssertionError("Unexpected NC registration lookup: " + name);
        }
    }
}
