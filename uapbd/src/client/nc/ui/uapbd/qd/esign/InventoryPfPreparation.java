package nc.ui.uapbd.qd.esign;

import java.awt.Container;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import javax.swing.SwingUtilities;
import nc.ui.pf.clientutils.PfUtilClientAssistor;
import nc.ui.pub.pf.MessageProcessorContext;
import nc.ui.pub.pf.PfUtilClient;
import nc.ui.pubapp.uif2app.actions.pflow.AbstractScriptExcAction;
import nc.ui.pubapp.uif2app.actions.pflow.DeleteScriptAction;
import nc.ui.pubapp.uif2app.actions.pflow.ScriptPFlowAction;
import nc.ui.ic.general.action.GeneralDeleteAction;
import nc.ui.ic.m4c.action.SaleOutDeleteAction;
import nc.ui.ic.m4q.action.LocAdjDeleteAction;
import nc.ui.pubapp.uif2app.model.PFlowProxy;
import nc.vo.pf.change.PfUtilBaseTools;
import nc.vo.pub.AggregatedValueObject;
import nc.vo.pub.BusinessException;
import nc.vo.pub.workflownote.WorkflownoteVO;
import nc.vo.uif2.LoginContext;

/** The native client-side PF checks surrounding a single inventory invocation. */
final class InventoryPfPreparation {
    private static final String ACTION_HASH = "CDB5FDAFC986099128BC9E79A14511303F03561EE99614C90BFD6E1B1F523D68";
    private static final String PROXY_HASH = "26416FF16DE227259A26FEA613EEC92FAD3995593805838FA0096304B5EC9F21";
    private static final String DELETE_HASH = "0155F284202B066ADC2E292C960E89CDA9539551CC3259D8456B718259EAF876";
    private static final String SCRIPT_FLOW_HASH = "5412A1C17AEC6A92FAC1D3E93842DCF5F8E14ED08F0E13D553FF3B9CED4B6CEB";
    private static final String GENERAL_DELETE_HASH = "946D341271A8977FB10D061213CED8BF699276AB04D8CE79072B2C466C732A0D";
    private static final String SALE_DELETE_HASH = "7530FE4C6BDAE64AA10E91CAAF1C73D4D330094D09ACD5DBCE516564B4417451";
    private static final String LOCATION_DELETE_HASH = "B16FD6DFA96753C936025B0AA125ECA7912F7B6843C8CB01D8BF0D389333A8B3";
    private final WorkflownoteVO note;
    private final HashMap<String, Object> parameters;
    private final boolean accepted;

    private InventoryPfPreparation(WorkflownoteVO note, HashMap<String, Object> parameters, boolean accepted) {
        this.note = note;
        this.parameters = parameters;
        this.accepted = accepted;
    }

    boolean isAccepted() { return accepted; }
    WorkflownoteVO getNote() { return note; }
    Map<String, Object> getParameters() { return new HashMap<String, Object>(parameters); }

    static void verifyNativeContract() throws BusinessException {
        try {
            SaleOrderPfPreparation.verifyNativeContract();
            verifyClass(AbstractScriptExcAction.class, ACTION_HASH);
            verifyClass(PFlowProxy.class, PROXY_HASH);
            validationMethod();
            executedField();
            proxySuccessField();
            SaleOrderPfPreparation.method("actionAboutWorkflow", Container.class, String.class, String.class,
                    AggregatedValueObject.class, HashMap.class, Integer.TYPE);
        } catch (Exception failure) {
            throw new BusinessException("当前 NC 库存流程客户端版本尚未核对，请联系管理员。", failure);
        }
    }

    /** The confirmed-delete bridge relies on the exact native confirmation and doSuperAction contract. */
    static void verifyNativeDeleteContract(Class<?> nativeAction) throws BusinessException {
        try {
            verifyClass(DeleteScriptAction.class, DELETE_HASH);
            verifyClass(ScriptPFlowAction.class, SCRIPT_FLOW_HASH);
            verifyClass(GeneralDeleteAction.class, GENERAL_DELETE_HASH);
            if (nativeAction == SaleOutDeleteAction.class) verifyClass(SaleOutDeleteAction.class, SALE_DELETE_HASH);
            else if (nativeAction == LocAdjDeleteAction.class) verifyClass(LocAdjDeleteAction.class, LOCATION_DELETE_HASH);
            else if (nativeAction != GeneralDeleteAction.class) throw new IllegalStateException("Unsupported native delete action");
        }
        catch (Exception failure) {
            throw new BusinessException("当前 NC 库存删除确认流程版本尚未核对，请联系管理员。", failure);
        }
    }

    /** Match the pinned ScriptPFlowAction message when its native permission check rejects deletion. */
    static void showNativeDataPermissionDenied(LoginContext context) {
        String title = nc.ui.ml.NCLangRes.getInstance().getStrByID("uif2", "ExceptionHandlerWithDLG-000000");
        nc.ui.uif2.ShowStatusBarMsgUtil.showErrorMsg(title, nc.ui.uif2.IShowMsgConstant.getDataPermissionInfo(), context);
    }

    /** Invoke the original private validation path, including the native beforeCheck callback. */
    static void validate(AbstractScriptExcAction action) throws Exception {
        try { validationMethod().invoke(action); }
        catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new BusinessException("无法执行库存单据原生校验。", cause);
        }
    }

    /** Match the original state-action retry flag on the pinned native action class. */
    static void setExecuted(AbstractScriptExcAction action, boolean value) throws BusinessException {
        try { executedField().setBoolean(action, value); }
        catch (Exception failure) { throw new BusinessException("无法更新 NC 库存流程执行状态。", failure); }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    static InventoryPfPreparation prepare(Container parent, String action, String billType,
            AggregatedValueObject bill, Map<?, ?> originalParameters) throws BusinessException {
        verifyNativeContract();
        String approvalButton=nc.vo.ic.qd.esign.SapplyNativeAction.approvalButton(action);
        if (!SwingUtilities.isEventDispatchThread() || !("WRITE".equals(action) || "DELETE".equals(action)
                || "SIGN".equals(action) || "CANCELSIGN".equals(action)
                || "SAVE".equals(action) || "UNSAVE".equals(action)
                || approvalButton!=null) || billType == null || bill == null) {
            throw new BusinessException("库存流程上下文无效，请重新操作。");
        }
        boolean explicitApproval=approvalButton!=null;
        if (!"WRITE".equals(action) && !explicitApproval && PfUtilBaseTools.isApproveAction(action, billType)) {
            throw new BusinessException("当前库存操作涉及尚未接入的原生审批流程。");
        }
        HashMap<String, Object> params = originalParameters == null ? new HashMap<String, Object>()
                : new HashMap((Map) originalParameters);
        if (explicitApproval && (params.get("silently") != null || params.get("nosendmessage") != null)) {
            throw new BusinessException("出库申请审批不支持跳过原生确认窗口的流程参数。");
        }
        try {
            SaleOrderPfPreparation.field("m_isSuccess").setBoolean(null, true);
            SaleOrderPfPreparation.field("m_checkFlag").setBoolean(null, true);
            SaleOrderPfPreparation.field("isOpenedInDialog").setBoolean(null, false);
            boolean accepted = ((Boolean) SaleOrderPfPreparation.method("hintBeforeAction", Container.class,
                    String.class, String.class).invoke(null, parent, action, billType)).booleanValue();
            if (!accepted) {
                SaleOrderPfPreparation.field("m_isSuccess").setBoolean(null, false);
                proxySuccessField().setBoolean(null, false);
                return new InventoryPfPreparation(null, params, false);
            }
            WorkflownoteVO note = null;
            Object silently = params.get("silently");
            if (silently == null && params.get("nosendmessage") == null) {
                if (PfUtilBaseTools.isSaveAction(action, billType) || PfUtilBaseTools.isApproveAction(action, billType)) {
                    note = (WorkflownoteVO) SaleOrderPfPreparation.method("actionAboutApproveflow", Container.class,
                            String.class, String.class, Object.class, HashMap.class, Integer.TYPE)
                            .invoke(null, parent, action, billType, bill, params, Integer.valueOf(0));
                } else if (PfUtilBaseTools.isStartAction(action, billType) || PfUtilBaseTools.isSignalAction(action, billType)) {
                    note = (WorkflownoteVO) SaleOrderPfPreparation.method("actionAboutWorkflow", Container.class,
                            String.class, String.class, AggregatedValueObject.class, HashMap.class, Integer.TYPE)
                            .invoke(null, parent, action, billType, bill, params, Integer.valueOf(0));
                }
                if (!PfUtilClient.isSuccess()) {
                    proxySuccessField().setBoolean(null, false);
                    return new InventoryPfPreparation(note, params, false);
                }
            }
            if (!explicitApproval && note != null && PfUtilClientAssistor.isAddApproverOrAppoint(note)) {
                throw new BusinessException("当前库存保存涉及加签或指定审批人，请核对流程接入。");
            }
            if ("Approve".equals(approvalButton) && note == null) {
                throw new BusinessException("出库申请审批未取得原生确认结果，请核对流程配置。");
            }
            if (note == null && silently == null) params.put("notechecked", "notechecked");
            return new InventoryPfPreparation(note, params, true);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof BusinessException) throw (BusinessException) cause;
            throw new BusinessException("NC 库存流程准备失败。", cause);
        } catch (BusinessException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new BusinessException("无法执行 NC 库存流程准备。", failure);
        }
    }

    void completed() throws BusinessException {
        completeNativeResult(note);
    }

    static void completeNativeResult(WorkflownoteVO note) throws BusinessException {
        try {
            SaleOrderPfPreparation.field("m_isSuccess").setBoolean(null, true);
            proxySuccessField().setBoolean(null, true);
            if (note != null) MessageProcessorContext.setHandledWithProcessor(note);
        } catch (Exception failure) {
            throw new BusinessException("库存单据已提交，但客户端无法确认流程结果，请先查询单据。", failure);
        }
    }

    private static Method validationMethod() throws Exception {
        Method method = AbstractScriptExcAction.class.getDeclaredMethod("validate");
        method.setAccessible(true);
        return method;
    }

    private static Field executedField() throws Exception {
        Field field = AbstractScriptExcAction.class.getDeclaredField("isExecuted");
        field.setAccessible(true);
        return field;
    }

    private static Field proxySuccessField() throws Exception {
        Field field = PFlowProxy.class.getDeclaredField("success");
        field.setAccessible(true);
        return field;
    }

    static void verifyClass(Class<?> type, String expected) throws Exception {
        InputStream input = type.getResourceAsStream(type.getSimpleName() + ".class");
        if (input == null) throw new IllegalStateException("Native class bytes are unavailable");
        byte[] digest;
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) sha.update(buffer, 0, count);
            digest = sha.digest();
        } finally { input.close(); }
        StringBuilder actual = new StringBuilder();
        for (byte value : digest) actual.append(String.format("%02X", value & 0xff));
        if (!expected.equals(actual.toString())) throw new IllegalStateException("Native class version differs: " + type.getName());
    }
}
