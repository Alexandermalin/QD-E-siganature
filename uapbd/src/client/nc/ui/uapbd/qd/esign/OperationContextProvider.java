package nc.ui.uapbd.qd.esign;

import java.awt.Container;
import java.awt.event.ActionEvent;
import javax.swing.Action;
import nc.vo.uapbd.qd.esign.OperationContext;

/** 经逐节点验证后实现的上下文提供接口，应使用真实按钮编码和稳定的子对象标识；界面显示身份不替代服务端认证身份。 */
public interface OperationContextProvider {
    /** 根据本次真实动作和选择对象构造草稿，使用稳定对象标识，不能加入认证秘密或伪造服务端身份。 */
    OperationContext createContext(Action action, ActionEvent event) throws Exception;
    /** 返回用于显示 NC 窗口和消息的父容器。 */
    Container getParentContainer();

    /** 返回仅供界面展示的当前用户名称，服务端仍从原生上下文取得实际认证身份。 */
    String getCurrentUserDisplayName();
}
