package nc.ui.uapbd.qd.esign;

import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import javax.swing.WindowConstants;
import nc.ui.pub.beans.UIButton;
import nc.ui.pub.beans.UIDialog;
import nc.vo.uapbd.qd.esign.log.LogKind;
import nc.vo.uapbd.qd.esign.log.LogQuery;

/** 关联日志及原 QdLogQueryAction 的弹窗宿主。正式菜单在页签中使用同一面板。 */
public final class QdLogQueryDialog extends UIDialog {
    private static final long serialVersionUID = 1L;
    private final QdLogQueryPanel logs;

    public QdLogQueryDialog(Container parent, LogKind kind) {
        super(parent, title(kind), true);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        UIButton close = new UIButton("关闭");
        logs = new QdLogQueryPanel(kind, close);
        logs.initializeTimeRange();
        close.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent event) { closeCancel(); }
        });
        setContentPane(logs);
        setSize(1160, 690);
    }

    public void startInitialQuery() { logs.startInitialQuery(); }

    void startRelatedQuery(LogQuery source, String requestId) {
        logs.startRelatedQuery(source, requestId);
    }

    @Override public void closeCancel() {
        if (logs.canClose()) { super.closeCancel(); }
    }

    @Override protected void processWindowEvent(WindowEvent event) {
        if (event.getID() == WindowEvent.WINDOW_CLOSING) { closeCancel(); return; }
        super.processWindowEvent(event);
    }

    private static String title(LogKind kind) {
        return kind == LogKind.AUDIT ? "业务审计日志" : kind == LogKind.SECURITY ? "登录与安全事件日志" : "电子签名日志";
    }
}
