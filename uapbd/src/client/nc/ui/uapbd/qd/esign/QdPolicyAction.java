package nc.ui.uapbd.qd.esign;

import java.awt.Container;
import java.awt.event.ActionEvent;
import nc.ui.uif2.NCAction;

/** 策略管理的附加菜单动作，负责打开窗口并传入当前业务单元；示例接线不会自动启用真实业务节点。 */
public final class QdPolicyAction extends NCAction {
    /** serialVersionUID：Java 序列化版本号，保持跨 RPC 或 Swing 组件序列化的兼容标识。 */
    private static final long serialVersionUID = 1L;
    /** parentContainer：用于显示 NC 窗口和消息的父容器。 */
    private Container parentContainer;
    /** businessUnitId：打开策略窗口时使用的业务单元主键。 */
    private String businessUnitId;
    /** 初始化策略管理菜单的名称和动作属性，具体组织及父容器由接线注入。 */
    public QdPolicyAction() { setBtnName("电子签名策略"); setCode("QD_ES_POLICY"); }
    /** 配置窗口或消息展示所用的父容器。 */
    public void setParentContainer(Container value) { parentContainer = value; }
    /** 设置策略窗口默认业务单元，不替代服务端权限校验。 */
    public void setBusinessUnitId(String value) { businessUnitId = value; }
    /** 执行 NC 菜单入口或原生业务事件监听，实际授权和业务结果仍由对应服务判断。 */
    @Override public void doAction(ActionEvent event) {
        QdPolicyDialog dialog = new QdPolicyDialog(parentContainer, businessUnitId);
        try { dialog.showModal(); } finally { dialog.dispose(); }
    }
}
