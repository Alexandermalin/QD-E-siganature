package nc.impl.uapbd.qd.esign;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import nc.bs.framework.aop.AspectManager;
import nc.bs.framework.aop.AspectMeta;
import nc.bs.framework.aop.AspectType;
import nc.bs.framework.core.AbstractMeta;
import nc.bs.framework.core.ComponentMeta;
import nc.bs.framework.core.TxAttribute;
import nc.bs.framework.server.BusinessAppServer;
import nc.bs.framework.server.PublicMetaRepo;
import nc.bs.pub.pf.IPfAfterAction;
import nc.bs.pub.pf.IPfBeforeAction;
import nc.bs.pub.pf.PfUtilTools;
import nc.bs.uapbd.qd.esign.audit.SapplyWorkflowBatchAspect;
import nc.bs.uapbd.qd.esign.audit.SapplyWorkflowBatchAspect.UnitCallback;
import nc.itf.uap.pf.IWorkflowMachine;
import nc.itf.uap.pf.IplatFormEntry;
import nc.itf.ic.qd.esign.IInventoryPfStateAdapter;
import nc.itf.uapbd.qd.esign.ISapplyBatchTransactionService;
import nc.vo.pub.BusinessException;

/** Validates the local native registration and the configured outer PF callbacks. */
final class SapplyBatchNativeContract {
    private SapplyBatchNativeContract() { }

    static boolean require(String billType) throws BusinessException {
        if (billType == null || billType.trim().isEmpty() || !billType.equals(billType.trim())) {
            throw new BusinessException("出库申请批量流程缺少有效交易类型。");
        }
        requireRegistration();
        try {
            return requireNoOuterCallbacks(PfUtilTools.getBizRuleImpl(billType));
        } catch (RuntimeException error) {
            throw new BusinessException("无法确认出库申请交易类型的原生批量回调。", error);
        } catch (LinkageError error) {
            throw new BusinessException("当前 NC 版本无法核对出库申请交易类型的原生批量回调。", error);
        }
    }

    static void requireRegistration() throws BusinessException {
        try {
            requireRegistration(BusinessAppServer.getInstance().getPublicComponentMetaRepo());
        } catch (RuntimeException error) {
            throw new BusinessException("无法读取出库申请批量流程的 NC 服务注册。", error);
        } catch (LinkageError error) {
            throw new BusinessException("当前 NC 版本无法核对出库申请批量流程的服务注册。", error);
        }
    }

    static void requireRegistration(PublicMetaRepo repo) throws BusinessException {
        requireNativeComponent(repo, IWorkflowMachine.class);
        requireNativeComponent(repo, IplatFormEntry.class);
        ComponentMeta callback = repo.getComponentMeta(UnitCallback.class.getName());
        if (!isLocalImplementation(callback, UnitCallback.class, SapplyWorkflowBatchUnit.class)
                || (callback.getTxAttribute() != TxAttribute.CMT && callback.getTxAttribute() != TxAttribute.NONE)) {
            throw new BusinessException("出库申请批量逐单处理服务尚未按本地事务契约注册。");
        }
        ComponentMeta transactions = repo.getComponentMeta(ISapplyBatchTransactionService.class.getName());
        if (!isLocalImplementation(transactions, ISapplyBatchTransactionService.class, SapplyBatchTransactionServiceImpl.class)
                || transactions.getTxAttribute() != TxAttribute.CMT) {
            throw new BusinessException("出库申请批量请求与回执服务尚未按本地 NC CMT 契约注册。");
        }
        ComponentMeta adapter=repo.getComponentMeta(IInventoryPfStateAdapter.class.getName());
        Class<?> implementation=adapter instanceof AbstractMeta?((AbstractMeta)adapter).getImplementation():null;
        if(!hasInterface(adapter,IInventoryPfStateAdapter.class) || adapter.isRemote()
                || adapter.getTxAttribute()!=TxAttribute.CMT || implementation==null
                || !"nc.impl.ic.qd.esign.InventoryPfStateAdapter".equals(implementation.getName()))
            throw new BusinessException("出库申请批量库存适配服务尚未按本地 NC CMT 契约注册。");
    }

    private static void requireNativeComponent(PublicMetaRepo repo, Class<?> type) throws BusinessException {
        ComponentMeta component = repo == null ? null : repo.getComponentMeta(type.getName());
        if (!hasInterface(component, type) || component.getTxAttribute() != TxAttribute.CMT
                || component.getContainer() == null) {
            throw new BusinessException("出库申请批量流程缺少原生 NC CMT 服务。" + type.getSimpleName());
        }
        AspectManager manager = component.getContainer().getExtension(AspectManager.class);
        requireAspect(type, manager == null ? null : manager.getAspectMetas(component));
    }

    private static boolean hasInterface(ComponentMeta meta, Class<?> type) {
        return meta != null && meta.isPublic() && meta.getInterfaces() != null
                && Arrays.asList(meta.getInterfaces()).contains(type);
    }

    private static boolean isLocalImplementation(ComponentMeta meta, Class<?> contract, Class<?> implementation) {
        return hasInterface(meta, contract) && !meta.isRemote() && meta instanceof AbstractMeta
                && ((AbstractMeta) meta).getImplementation() == implementation;
    }

    private static void requireAspect(Class<?> type, AspectMeta[] aspects) throws BusinessException {
        int count = 0;
        if (aspects != null) {
            for (AspectMeta aspect : aspects) {
                if (aspect != null && aspect.getAspectClass() == SapplyWorkflowBatchAspect.class
                        && aspect.getAspectType() == AspectType.aspect) count++;
            }
        }
        if (count != 1) throw new BusinessException("出库申请原生批量切面缺少唯一的 NC 注册。" + type.getSimpleName());
    }

    static boolean requireNoOuterCallbacks(Object handler) throws BusinessException {
        if (handler instanceof IPfBeforeAction || handler instanceof IPfAfterAction) {
            if (isVerifiedInventoryCallback(handler)) return true;
            throw new BusinessException("该出库申请交易类型配置了原生批量前后回调，现场尚未验证回调的单据范围和事务行为，暂不能执行受控批量。");
        }
        return false;
    }

    private static boolean isVerifiedInventoryCallback(Object handler) throws BusinessException {
        Class<?> type=handler.getClass();
        if(!"nc.bs.ic.pf.PfBeforeAndAfterActionForIC".equals(type.getName()))return false;
        try {
            return matches(type,"nc.bs.ic.pf.PfBeforeAndAfterActionForIC",
                    "1088DA38675FADF3DFBD7F5A5952F39E82DD5A0E4CB029A3FAB773D47C8E0FBC")
                    && matches(type.getSuperclass(),"nc.bs.ic.pf.PfActionCheckForIC",
                    "EAC13A60EE6829B90B78B38A82D5ECFE7D8F2264BE7FCAF20647335D6AC89BC0")
                    && matches(type.getSuperclass().getSuperclass(),"nc.bs.scmpub.pf.PfBeforeAndAfterAction",
                    "307323F9690EAB5E31BCF48899EDA3EE7470BA845BDCA482E4FAFECF08FE6F67");
        } catch(Exception failure) {
            throw new BusinessException("无法核对出库申请库存标准批量回调版本。",failure);
        }
    }

    private static boolean matches(Class<?> type,String name,String expected) throws Exception {
        if(type==null || !name.equals(type.getName()))return false;
        InputStream input=type.getResourceAsStream(type.getSimpleName()+".class");
        if(input==null)throw new IllegalStateException("Native callback class bytes are unavailable");
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try {byte[] buffer=new byte[8192];int length;while((length=input.read(buffer))!=-1)digest.update(buffer,0,length);}
        finally {input.close();}
        char[] hex="0123456789ABCDEF".toCharArray();StringBuilder actual=new StringBuilder();
        for(byte value:digest.digest()){actual.append(hex[(value>>>4)&15]);actual.append(hex[value&15]);}
        return expected.equals(actual.toString());
    }
}
