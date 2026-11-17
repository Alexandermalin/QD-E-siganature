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
import nc.vo.pf.change.PfUtilBaseTools;
import nc.vo.pub.AggregatedValueObject;
import nc.vo.pub.BusinessException;
import nc.vo.pub.workflownote.WorkflownoteVO;

/**
 * 发货单 / 销售发票等后续节点共用的原生 PF 客户端桥（版本钉死）。
 *
 * <p>与销售订单保存试点同一套已核实语义：反射调用本机 NC65 的 {@code PfUtilClient.hintBeforeAction}
 * 与 {@code actionAboutApproveflow} 完成原生流程准备；客户端字节码哈希不符时拒绝受控写入。
 * 本类不写业务数据，只产出原生流程参数与审批说明，最终写入由服务端适配器执行。</p>
 */
final class SalesPfPreparation {
    private static final String SUPPORTED_PF_CLIENT = "1448DB245D5179F80268369D754CAD0336FE4028CEA52EF53319CFB859826689";
    /** 已核实的原生流程动作：保存写、删除、提交、收回、审批与取消审批；其他流程动作仍拒绝。 */
    private static final java.util.Set<String> SUPPORTED_ACTIONS = java.util.Collections.unmodifiableSet(
            new java.util.HashSet<String>(java.util.Arrays.asList(
                    "WRITE", "DELETE", "SAVE", "UNSAVE", "APPROVE", "UNAPPROVE")));

    private final WorkflownoteVO note;
    private final HashMap<String, Object> parameters;
    private final boolean accepted;

    private SalesPfPreparation(WorkflownoteVO note, HashMap<String, Object> parameters, boolean accepted) {
        this.note = note;
        this.parameters = parameters;
        this.accepted = accepted;
    }

    boolean isAccepted() { return accepted; }
    WorkflownoteVO getNote() { return note; }
    Map<String, Object> getParameters() { return new HashMap<String, Object>(parameters); }

    static void verifyNativeContract() throws BusinessException {
        try {
            InputStream input = PfUtilClient.class.getResourceAsStream("PfUtilClient.class");
            if (input == null) { throw new IllegalStateException("NC PF client class bytes unavailable"); }
            byte[] digest;
            try {
                MessageDigest sha = MessageDigest.getInstance("SHA-256");
                byte[] buf = new byte[8192];
                int n;
                while ((n = input.read(buf)) != -1) { sha.update(buf, 0, n); }
                digest = sha.digest();
            } finally { input.close(); }
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) { hex.append(String.format("%02X", b & 0xff)); }
            if (!SUPPORTED_PF_CLIENT.equals(hex.toString())) { throw new IllegalStateException("NC PF client version differs"); }
            field("m_isSuccess"); field("m_checkFlag"); field("isOpenedInDialog");
            method("hintBeforeAction", Container.class, String.class, String.class);
            method("actionAboutApproveflow", Container.class, String.class, String.class,
                    Object.class, HashMap.class, Integer.TYPE);
        } catch (Exception failure) {
            throw new BusinessException("当前节点不支持当前 NC 流程客户端版本，请停止测试并核对接入。", failure);
        }
    }

    /** 执行原生流程准备；返回对象未 accepted 表示用户在原生对话框取消，调用方不得继续写入。 */
    static SalesPfPreparation prepare(Container parent, String action, String billType,
            AggregatedValueObject bill, Map<?, ?> originalParameters, String nodeName) throws BusinessException {
        verifyNativeContract();
        if (!SwingUtilities.isEventDispatchThread() || !SUPPORTED_ACTIONS.contains(action) || billType == null
                || bill == null || (originalParameters != null && !originalParameters.isEmpty())) {
            throw new BusinessException(nodeName + "受控流程只支持已核实的原生单笔动作，不支持额外流程参数。");
        }
        HashMap<String, Object> params = new HashMap<String, Object>();
        try {
            field("m_isSuccess").setBoolean(null, true);
            field("m_checkFlag").setBoolean(null, true);
            field("isOpenedInDialog").setBoolean(null, false);
            boolean accepted = ((Boolean) method("hintBeforeAction", Container.class, String.class, String.class)
                    .invoke(null, parent, action, billType)).booleanValue();
            if (!accepted) {
                field("m_isSuccess").setBoolean(null, false);
                return new SalesPfPreparation(null, params, false);
            }
            boolean startsApproval = PfUtilBaseTools.isSaveAction(action, billType);
            // 审批/取消审批走原生审批窗口：打开或取消窗口不产生业务请求，点击「确定」后才由调用方签名并写入。
            boolean approveAction = "APPROVE".equals(action) || "UNAPPROVE".equals(action)
                    || PfUtilBaseTools.isApproveAction(action, billType);
            if (!startsApproval && !approveAction && (PfUtilBaseTools.isStartAction(action, billType)
                    || PfUtilBaseTools.isSignalAction(action, billType))) {
                throw new BusinessException("当前动作涉及该节点尚未接入的流程动作。");
            }
            WorkflownoteVO note = (startsApproval || approveAction)
                    ? (WorkflownoteVO) method("actionAboutApproveflow", Container.class, String.class,
                        String.class, Object.class, HashMap.class, Integer.TYPE)
                        .invoke(null, parent, action, billType, bill, params, Integer.valueOf(0)) : null;
            if (!PfUtilClient.isSuccess()) { return new SalesPfPreparation(note, params, false); }
            // 保存类动作不接管加签/指定审批人；审批类动作按原生结果原样传递（真实分支待联调）。
            if (!approveAction && note != null && PfUtilClientAssistor.isAddApproverOrAppoint(note)) {
                throw new BusinessException(nodeName + "保存动作暂不支持加签或指定审批人。");
            }
            if (note == null) { params.put("notechecked", "notechecked"); }
            if (!supportedParameters(params)) {
                throw new BusinessException(nodeName + "流程携带了受控路径不支持的扩展参数。");
            }
            return new SalesPfPreparation(note, params, true);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof BusinessException) { throw (BusinessException) cause; }
            throw new BusinessException("NC " + nodeName + "流程准备失败。", cause);
        } catch (BusinessException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new BusinessException("无法执行 NC " + nodeName + "流程准备。", failure);
        }
    }

    /** 原生流程准备成功后的客户端结果确认；失败必须让调用方按「结果未知」处理，不得宣称成功。 */
    void completed() throws BusinessException {
        try {
            field("m_isSuccess").setBoolean(null, true);
            if (note != null) { MessageProcessorContext.setHandledWithProcessor(note); }
        } catch (Exception failure) {
            throw new BusinessException("业务已提交，但客户端无法确认流程结果，请先重新查询单据。", failure);
        }
    }

    /** 流程参数白名单：只允许服务端与客户端共同列出的键，且值必须是文本或布尔。 */
    private static boolean supportedParameters(HashMap<String, Object> parameters) {
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            String key = entry.getKey();
            if (!("notechecked".equals(key) || "silently".equals(key) || "nosendmessage".equals(key)
                    || "besideapprove".equals(key))) { return false; }
            Object value = entry.getValue();
            if (!(value instanceof String || value instanceof Boolean)) { return false; }
        }
        return true;
    }

    static Field field(String name) throws Exception {
        Field field = PfUtilClient.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    static Method method(String name, Class<?>... types) throws Exception {
        Method method = PfUtilClient.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method;
    }
}
