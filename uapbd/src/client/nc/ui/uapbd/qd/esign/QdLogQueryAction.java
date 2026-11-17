package nc.ui.uapbd.qd.esign;

import java.awt.Container;
import java.awt.event.ActionEvent;
import nc.ui.uif2.NCAction;
import nc.vo.uapbd.qd.esign.log.LogKind;

/** 按日志类别打开查询窗口的菜单入口；查询和导出权限始终由服务端检查，客户端入口可见性不等同于授权。 */
public final class QdLogQueryAction extends NCAction {
    /** serialVersionUID：Java 序列化版本号，保持跨 RPC 或 Swing 组件序列化的兼容标识。 */
    private static final long serialVersionUID = 1L;
    /** parentContainer：用于显示 NC 窗口和消息的父容器。 */
    private Container parentContainer;
    /** logKind：菜单入口配置的日志类别。 */
    private LogKind logKind = LogKind.SIGNATURE;
    /** 初始化日志管理菜单入口，默认展示签名类别，具体日志类别可由接线配置。 */
    public QdLogQueryAction() { setBtnName("电子签名日志"); setCode("QD_ES_SIGNLOG"); }
    /** 配置窗口或消息展示所用的父容器。 */
    public void setParentContainer(Container value) { parentContainer = value; }
    /** 设置当前菜单对应的固定日志类别。 */
    public void setLogKind(LogKind value) {
        if (value == null) { throw new IllegalArgumentException("LogKind required"); }
        logKind = value;
        setBtnName(value == LogKind.AUDIT ? "业务审计日志" : value == LogKind.SECURITY ? "登录与安全事件日志" : "电子签名日志");
        setCode(value == LogKind.AUDIT ? "QD_ES_AUDITLOG" : value == LogKind.SECURITY ? "QD_ES_SECURITY" : "QD_ES_SIGNLOG");
    }
    /** 执行 NC 菜单入口或原生业务事件监听，实际授权和业务结果仍由对应服务判断。 */
    @Override public void doAction(ActionEvent event) {
        QdLogQueryDialog dialog = new QdLogQueryDialog(parentContainer, logKind);
        try {
            dialog.startInitialQuery();
            dialog.showModal();
        } finally {
            dialog.dispose();
        }
    }
}
