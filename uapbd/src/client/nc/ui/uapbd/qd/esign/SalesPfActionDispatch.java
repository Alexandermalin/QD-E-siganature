package nc.ui.uapbd.qd.esign;

import nc.bs.framework.common.NCLocator;
import nc.itf.uapbd.qd.esign.IOperationService;
import nc.ui.pubapp.pub.common.context.PFlowContext;
import nc.ui.pubapp.uif2app.actions.pflow.AbstractScriptExcAction;
import nc.vo.pub.AggregatedValueObject;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.OperationResponse;
import nc.vo.uapbd.qd.esign.SalesOtherNodeScope;
import nc.vo.uapbd.qd.esign.SalesPfActionCommand;
import nc.vo.uapbd.qd.esign.SalesPfActionResult;

/**
 * 发货单 / 销售发票 PF 动作的受控执行辅助。
 *
 * <p>在原生动作已完成输入/确认并进入 {@code procFlow} 后，用一次性授权把类型化命令交给网关，
 * 替代原生最终 PF 调用，同时保留流程准备与结果处理语义。与销售订单的同名工具保持相同约束：
 * 单张已存单据、固定动作、无额外流程参数、不把返回 VO 当成成功。</p>
 */
final class SalesPfActionDispatch {
    /** 供各动作类回调原生 protected fillUpContext，组装真实流程参数。 */
    interface ContextFiller { void fillUp(PFlowContext flow) throws Exception; }

    /** 已核实的 NC65 客户端 AbstractScriptExcAction 字节码哈希；平台类变化时拒绝受控写入，避免复用错误校验语义。 */
    private static final String SUPPORTED_SCRIPT_ACTION =
            "CDB5FDAFC986099128BC9E79A14511303F03561EE99614C90BFD6E1B1F523D68";

    private SalesPfActionDispatch() { }

    /**
     * 复刻原生 {@code AbstractScriptExcAction.validate()} 的私有实现：
     * 覆盖 {@code procFlow} 后必须显式执行同一版本的原生校验器，不能因为签名接管而跳过业务流程校验。
     * 没有配置校验器时为空操作。
     */
    static void runNativeValidation(nc.ui.pubapp.uif2app.actions.pflow.AbstractScriptExcAction action)
            throws BusinessException {
        if (action.getValidationService() == null) { return; }
        verifyNativeScriptAction();
        try {
            java.lang.reflect.Method validate = nc.ui.pubapp.uif2app.actions.pflow.AbstractScriptExcAction.class
                    .getDeclaredMethod("validate");
            validate.setAccessible(true);
            validate.invoke(action);
        } catch (java.lang.reflect.InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof BusinessException) { throw (BusinessException) cause; }
            if (cause instanceof RuntimeException) { throw (RuntimeException) cause; }
            throw new BusinessException("NC 流程动作校验失败。", cause);
        } catch (Exception failure) {
            throw new BusinessException("无法执行 NC 流程动作校验。", failure);
        }
    }

    /** 客户端流程动作字节码必须与已核实版本一致，防止平台升级后按旧语义调用私有校验入口。 */
    private static void verifyNativeScriptAction() throws BusinessException {
        try {
            java.io.InputStream input = nc.ui.pubapp.uif2app.actions.pflow.AbstractScriptExcAction.class
                    .getResourceAsStream("AbstractScriptExcAction.class");
            if (input == null) { throw new IllegalStateException("NC script action bytes unavailable"); }
            byte[] digest;
            try {
                java.security.MessageDigest sha = java.security.MessageDigest.getInstance("SHA-256");
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) { sha.update(buffer, 0, read); }
                digest = sha.digest();
            } finally { input.close(); }
            StringBuilder hex = new StringBuilder();
            for (byte value : digest) { hex.append(String.format("%02X", value & 0xff)); }
            if (!SUPPORTED_SCRIPT_ACTION.equals(hex.toString())) {
                throw new IllegalStateException("NC script action version differs");
            }
        } catch (Exception failure) {
            throw new BusinessException("当前节点不支持当前 NC 流程动作版本，已终止受控操作。", failure);
        }
    }

    /**
     * 受控执行固定 PF 动作；不满足授权与流程核对时抛业务异常，不回落原生写入。
     *
     * @param action 原生动作实例，用于结果回填
     * @param flow 原生流程上下文
     * @param authorized 已签名的授权上下文
     * @param node 固定节点编码
     * @param button 真实按钮编码
     * @param expectedAction 已核实的原生 PF 动作
     * @param defaultBillType 交易类型缺省值（该节点固定功能型）
     * @param billClassName 原生单据 VO 全限定类名，用于核对单据类型
     */
    static void dispatch(AbstractScriptExcAction action, PFlowContext flow, OperationContext authorized,
            SalesNodeActionHandoff handoff, String node, String button, String expectedAction,
            String defaultBillType, String billClassName, ContextFiller filler) throws Exception {
        if (authorized == null) { throw new BusinessException(SalesOtherNodeScope.nodeName(node) + "流程动作缺少签名授权。"); }
        if (flow != action.getFlowContext() || !expectedAction.equals(flow.getActionName())
                || (flow.getUserId() != null && flow.getUserId().length() != 0)
                || !empty(action.getPreActionNames()) || !empty(action.getAfterActionNames())) {
            throw new BusinessException(SalesOtherNodeScope.nodeName(node) + "流程动作与签名授权不一致，请重新操作。");
        }
        filler.fillUp(flow);
        // 覆盖 procFlow 会跳过原生 AbstractScriptExcAction 内的私有 validate()；这里按同一版本复刻调用，
        // 保证原生流程动作校验器（PowerValidateService / CompositeValidation 等）没有被静默绕过。
        runNativeValidation(action);
        AggregatedValueObject[] bills = flow.getBillVos();
        if (flow.isBatch() || bills == null || bills.length != 1 || bills[0] == null
                || !billClassName.equals(bills[0].getClass().getName())) {
            throw new BusinessException(SalesOtherNodeScope.nodeName(node) + "受控流程动作只支持一张已核实的单据。");
        }
        AggregatedValueObject bill = bills[0];
        String billPk = primaryKey(bill);
        if (billPk == null || authorized.getOrgId() == null
                || authorized.getObjects() == null || authorized.getObjects().size() != 1
                || !authorized.getObjects().get(0).getObjectPk().equals(billPk)) {
            throw new BusinessException("流程动作的单据主键与签名授权不一致。");
        }
        String billType = flow.getTrantype() != null ? flow.getTrantype()
                : (flow.getBillType() != null ? flow.getBillType() : defaultBillType);
        SalesPfPreparation prep = SalesPfPreparation.prepare(flow.getParent(), expectedAction, billType,
                bill, flow.getEParam(), SalesOtherNodeScope.nodeName(node));
        if (!prep.isAccepted()) {
            throw new BusinessException("NC " + SalesOtherNodeScope.nodeName(node) + "流程已取消，单据未处理。");
        }
        SalesPfActionCommand command = new SalesPfActionCommand(billType,
                new AggregatedValueObject[] { bill }, flow.getUserObj(), prep.getNote(), prep.getParameters());
        IOperationService gateway = NCLocator.getInstance().lookup(IOperationService.class);
        String requestId = handoff.dispatch(button, authorized.getGroupId(), authorized.getOrgId(), billPk);
        OperationResponse response = gateway.execute(requestId, command);
        requireKnownResult(requestId, response, billPk, node);
        SalesPfActionResult outcome = (SalesPfActionResult) response.getBusinessResult();
        prep.completed();
        applyNativeResult(action, outcome);
    }

    /** 只接受与本次请求对应、非重放、返回真实单据结果的成功；否则按未知结果处理。 */
    private static void requireKnownResult(String requestId, OperationResponse response, String key, String node)
            throws BusinessException {
        if (response == null || response.isReplayed() || !requestId.equals(response.getRequestId())
                || !(response.getBusinessResult() instanceof SalesPfActionResult)) {
            throw new BusinessException("无法确认" + SalesOtherNodeScope.nodeName(node) + "流程结果，请先重新查询单据再处理。");
        }
        SalesPfActionResult outcome = (SalesPfActionResult) response.getBusinessResult();
        if (!key.equals(outcome.getPrimaryKey())) {
            throw new BusinessException("无法确认" + SalesOtherNodeScope.nodeName(node) + "流程结果，请先重新查询单据再处理。");
        }
    }

    /** 交由原生动作自己的结果处理刷新界面；返回值缺失时不做无依据刷新。 */
    private static void applyNativeResult(AbstractScriptExcAction action, SalesPfActionResult outcome)
            throws Exception {
        Object result = outcome.getNativeResult();
        if (result == null) { return; }
        Object[] returned;
        if (result.getClass().isArray()) { returned = (Object[]) result; }
        else {
            returned = (Object[]) java.lang.reflect.Array.newInstance(result.getClass(), 1);
            returned[0] = result;
        }
        action.processReturnObjs(returned);
    }

    /** 构造一次性请求上下文：单张已存单据、真实按钮与固定动作域。 */
    static OperationContext context(String group, String org, String node, String button, String operationType,
            AggregatedValueObject bill) throws BusinessException {
        String billPk = bill == null ? null : primaryKey(bill);
        if (billPk == null || org == null || !SalesOtherNodeScope.isPilotButton(node, button)) {
            throw new BusinessException("无法构造受控请求：单据、组织或按钮不受支持。");
        }
        BusinessObjectRef root = new BusinessObjectRef(java.util.UUID.randomUUID().toString(),
                SalesOtherNodeScope.objectType(node), billPk, null, null);
        java.util.List<BusinessObjectRef> objects = new java.util.ArrayList<BusinessObjectRef>();
        objects.add(root);
        return new OperationContext(null, null, group, org, node, SalesOtherNodeScope.nodeName(node),
                button, SalesOtherNodeScope.buttonName(button), operationType, null, objects);
    }

    private static boolean empty(java.util.List<?> values) { return values == null || values.isEmpty(); }

    /** 从聚合 VO 的表头取真实主键；表头不是标准 SuperVO 时拒绝受控流程。 */
    private static String primaryKey(AggregatedValueObject bill) throws BusinessException {
        Object parent = bill.getParentVO();
        if (!(parent instanceof nc.vo.pub.SuperVO)) {
            throw new BusinessException("单据表头不是标准 NC VO，无法核对授权对象。");
        }
        return ((nc.vo.pub.SuperVO) parent).getPrimaryKey();
    }
}
