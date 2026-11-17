package nc.ui.uapbd.qd.esign;

import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import javax.swing.WindowConstants;
import nc.ui.pub.beans.UIButton;
import nc.ui.pub.beans.UIDialog;

/** 为已有 QdPolicyAction 接线保留弹窗宿主。正式功能入口直接嵌入策略面板。 */
public final class QdPolicyDialog extends UIDialog {
    private static final long serialVersionUID = 1L;
    private final QdPolicyPanel policy;

    public QdPolicyDialog(Container owner, String currentOrg) {
        super(owner, "电子签名策略管理", true);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        UIButton closeButton = new UIButton("关闭");
        policy = new QdPolicyPanel(currentOrg, closeButton);
        closeButton.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent event) { closeCancel(); }
        });
        setContentPane(policy);
        setSize(1120, 620);
        setResizable(true);
    }

    @Override public void closeCancel() {
        if (policy.canClose("关闭窗口")) { super.closeCancel(); }
    }

    @Override protected void processWindowEvent(WindowEvent event) {
        if (event.getID() == WindowEvent.WINDOW_CLOSING) { closeCancel(); return; }
        super.processWindowEvent(event);
    }
}
