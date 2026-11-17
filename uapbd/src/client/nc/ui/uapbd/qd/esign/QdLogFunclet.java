package nc.ui.uapbd.qd.esign;

import java.awt.BorderLayout;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import nc.funcnode.ui.AbstractFunclet;
import nc.vo.sm.funcreg.FuncRegisterVO;
import nc.vo.uapbd.qd.esign.log.LogKind;

/** 在 NC 功能页签内直接展示固定类别的日志。 */
abstract class QdLogFunclet extends AbstractFunclet {
    private static final long serialVersionUID = 1L;
    private final String functionCode;
    private final LogKind kind;
    private QdLogQueryPanel logs;

    QdLogFunclet(String functionCode, LogKind kind) {
        this.functionCode = functionCode;
        this.kind = kind;
    }

    @Override public final String preOpenCheck(FuncRegisterVO registration) {
        if (registration == null || !functionCode.equals(registration.getFuncode())) {
            return "电子签名管理入口与功能编码不匹配，应注册为 " + functionCode + "。";
        }
        return super.preOpenCheck(registration);
    }

    @Override public final void init() {
        final QdLogQueryPanel panel = new QdLogQueryPanel(kind, null);
        panel.addHierarchyListener(new HierarchyListener() {
            @Override public void hierarchyChanged(HierarchyEvent event) {
                if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && panel.isShowing()) {
                    panel.removeHierarchyListener(this);
                    panel.initializeTimeRange();
                    panel.startInitialQuery();
                }
            }
        });
        logs = panel;
        setLayout(new BorderLayout());
        add(logs, BorderLayout.CENTER);
    }

    @Override public final boolean funcletCloseing() {
        return logs == null || logs.canClose();
    }
}
