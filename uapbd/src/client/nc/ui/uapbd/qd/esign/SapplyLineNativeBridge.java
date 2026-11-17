package nc.ui.uapbd.qd.esign;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import javax.swing.SwingUtilities;
import nc.ui.ic.m4455.action.RowCloseAction;
import nc.ui.ic.m4455.action.RowOpenAction;
import nc.ui.pubapp.uif2app.model.BillManageModel;
import nc.ui.uif2.NCAction;
import nc.vo.ic.m4455.entity.SapplyBillVO;
import nc.vo.pub.BusinessException;

/** Accesses the original row-selection helpers only for the verified local NC client version. */
final class SapplyLineNativeBridge {
    private static final String CLOSE_HASH="36D1E15B17B03E5F420A95F0EE0238AD352BB6FFA81970AAF9D07B7C362E3660";
    private static final String OPEN_HASH="FD56A530F7015516451B9210287DA4231F575C592055F11735B0FB5DD558513C";
    private static final String ACTION_HASH="9B7186A9D0640EBA94F0DE8C2C9B9E1DD94F291E79333D4D9CF317419F708A0F";
    private SapplyLineNativeBridge() { }

    static void verify(Class<?> nativeAction) throws BusinessException {
        try {
            if(nativeAction!=RowCloseAction.class && nativeAction!=RowOpenAction.class)
                throw new IllegalStateException("Unknown outbound-request row action");
            InventoryPfPreparation.verifyClass(NCAction.class,ACTION_HASH);
            InventoryPfPreparation.verifyClass(nativeAction,nativeAction==RowCloseAction.class?CLOSE_HASH:OPEN_HASH);
            method(nativeAction,"getSelectIndex");method(nativeAction,"doBeforeAction");
            method(nativeAction,"getSelectedVO");method(nativeAction,"getOldVO");method(nativeAction,"getAppModel");
        } catch(Exception failure) { throw new BusinessException("当前 NC 出库申请行操作客户端版本尚未核对。",failure); }
    }

    static Selection select(NCAction action,Class<?> nativeAction,BillManageModel model,boolean showEmptyHint) throws Exception {
        verify(nativeAction);
        if(!SwingUtilities.isEventDispatchThread() || action==null || model==null || !nativeAction.isInstance(action))
            throw new BusinessException("出库申请行操作必须在已验证的客户端动作中执行。");
        invoke(nativeAction,action,"getSelectIndex");
        if(showEmptyHint && !Boolean.TRUE.equals(invoke(nativeAction,action,"doBeforeAction")))return null;
        Object selected=invoke(nativeAction,action,"getSelectedVO");
        Object original=invoke(nativeAction,action,"getOldVO");
        if(selected==null && showEmptyHint)return null;
        if(!(selected instanceof SapplyBillVO) || !(original instanceof SapplyBillVO)
                || invoke(nativeAction,action,"getAppModel")!=model)
            throw new BusinessException("无法核对出库申请当前单据和原生编辑模型。");
        return new Selection((SapplyBillVO)selected,(SapplyBillVO)original);
    }

    private static Method method(Class<?> type,String name) throws Exception {
        Method result=type.getDeclaredMethod(name);result.setAccessible(true);return result;
    }
    private static Object invoke(Class<?> type,Object target,String name) throws Exception {
        try{return method(type,name).invoke(target);}
        catch(InvocationTargetException failure){
            Throwable cause=failure.getCause();
            if(cause instanceof Exception)throw (Exception)cause;
            if(cause instanceof Error)throw (Error)cause;
            throw new BusinessException("无法读取出库申请行选择。",cause);
        }
    }
    static final class Selection {
        final SapplyBillVO selected,original;
        Selection(SapplyBillVO selected,SapplyBillVO original){this.selected=selected;this.original=original;}
    }
}
