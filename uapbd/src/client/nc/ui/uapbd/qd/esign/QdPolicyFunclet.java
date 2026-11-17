package nc.ui.uapbd.qd.esign;

import java.awt.BorderLayout;
import javax.swing.JTabbedPane;
import nc.funcnode.ui.AbstractFunclet;
import nc.vo.sm.funcreg.FuncRegisterVO;

/** 在电子签名策略页内展示业务单元和基础档案集团策略。 */
public class QdPolicyFunclet extends AbstractFunclet {
    private static final long serialVersionUID = 1L;
    private final boolean groupPolicy;
    private QdPolicyPanel policy;
    private QdPolicyPanel groupPanel;

    public QdPolicyFunclet() { this(false); }

    protected QdPolicyFunclet(boolean groupPolicy) { this.groupPolicy = groupPolicy; }

    @Override public String preOpenCheck(FuncRegisterVO registration) {
        String functionCode = groupPolicy ? "QD_ES_BD_POLICY" : "QD_ES_POLICY";
        if (registration == null || !functionCode.equals(registration.getFuncode())) {
            return "电子签名策略入口与功能编码不匹配，应注册为 " + functionCode + "。";
        }
        return super.preOpenCheck(registration);
    }

    @Override public void init() {
        policy = new QdPolicyPanel(null, null, groupPolicy);
        setLayout(new BorderLayout());
        if (groupPolicy) {
            add(policy, BorderLayout.CENTER);
            return;
        }
        groupPanel = new QdPolicyPanel(null, null, true);
        JTabbedPane scopes = new JTabbedPane();
        scopes.addTab("业务单元策略", policy);
        scopes.addTab("基础档案集团策略", groupPanel);
        add(scopes, BorderLayout.CENTER);
    }

    @Override public boolean funcletCloseing() {
        return (policy == null || policy.canClose("关闭页签"))
            && (groupPanel == null || groupPanel.canClose("关闭页签"));
    }
}
