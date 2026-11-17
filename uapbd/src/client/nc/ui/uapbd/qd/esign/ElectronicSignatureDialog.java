package nc.ui.uapbd.qd.esign;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.util.Arrays;
import javax.swing.BorderFactory;
import javax.swing.WindowConstants;
import nc.itf.uapbd.qd.esign.ISignatureService;
import nc.ui.pub.beans.UIButton;
import nc.ui.pub.beans.UIDialog;
import nc.ui.pub.beans.UILabel;
import nc.ui.pub.beans.UIPanel;
import nc.ui.pub.beans.UIPasswordField;
import nc.ui.pub.beans.UIScrollPane;
import nc.ui.pub.beans.UITextArea;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.ArchiveBatchResult;
import nc.vo.uapbd.qd.esign.CustomerAssignmentResult;
import nc.vo.uapbd.qd.esign.SupplierAssignmentResult;
import nc.vo.uapbd.qd.esign.MaterialCancelAssignmentResult;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.SignaturePreparation;
import nc.vo.uapbd.qd.esign.SignatureResult;

/** NC 模态电子签名确认窗口，允许在同一请求内重试密码；每次调用后清理凭据。 */
public final class ElectronicSignatureDialog extends UIDialog {
    /** serialVersionUID：Java 序列化版本号，保持跨 RPC 或 Swing 组件序列化的兼容标识。 */
    private static final long serialVersionUID = 1L;
    /** service：当前使用的服务实例或测试服务替身。 */
    private final ISignatureService service;
    /** preparation：服务端返回的签名前置准备信息。 */
    private final SignaturePreparation preparation;
    /** cancellation：窗口与控制器共用的一次取消请求协调器。 */
    private final RequestCancellation cancellation;
    /** password：单次认证密码输入控件，结束及销毁时清空。 */
    private final UIPasswordField password = new UIPasswordField();
    /** reason：本次操作事由输入区域。 */
    private final UITextArea reason = new UITextArea(4, 36);
    /** validation：签名输入校验提示控件。 */
    private final UILabel validation = new UILabel(" ");
    /** confirm：提交认证按钮。 */
    private final UIButton confirm = new UIButton("确认");
    /** cancel：取消签名按钮。 */
    private final UIButton cancel = new UIButton("取消");
    /** busy：是否有后台任务正在执行，用于防止并发提交及提前关闭。 */
    private boolean busy;
    /** finished：当前确认尝试是否已经结束。 */
    private boolean finished;
    /** signatureResult：本次认证 RPC 的结果。 */
    private SignatureResult signatureResult;
    /** failure：最近一次失败原因，保留原始异常。 */
    private Throwable failure;

    /** 保存本次准备结果与共用取消协调器，构建单次模态确认窗口并显示服务端要求的事由规则。 */
    ElectronicSignatureDialog(Container parent, String userDisplay,
            SignaturePreparation preparation, ISignatureService service,
            RequestCancellation cancellation) {
        super(parent, preparation.getPolicy().isSignatureRequired() ? "电子签名" : "填写操作事由", true);
        this.preparation = preparation;
        this.service = service;
        this.cancellation = cancellation;
        password.setAllowOtherCharacter(true);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        buildContent(userDisplay);
    }

    /** 构建签名窗口的身份展示、事由和密码控件，并绑定确认与取消事件。 */
    private void buildContent(String userDisplay) {
        OperationContext context = preparation.getContext();
        UIPanel content = new UIPanel(new BorderLayout(10, 10));
        content.setBorder(BorderFactory.createEmptyBorder(14, 16, 12, 16));
        UIPanel fields = new UIPanel(new GridBagLayout());
        row(fields, 0, "当前登录人员", new UILabel(text(userDisplay)));
        row(fields, 1, "业务功能", new UILabel(text(context.getNodeName()) + " [" + context.getNodeCode() + "]"));
        row(fields, 2, "当前操作", new UILabel(text(context.getButtonName()) + " [" + context.getOperationType() + "]"));
        UITextArea objects = new UITextArea(3, 36);
        objects.setEditable(false);
        objects.setLineWrap(true);
        objects.setWrapStyleWord(true);
        StringBuilder summary = new StringBuilder();
        int shown = 0;
        boolean customerAssignment=CustomerAssignmentResult.isRoute(context.getNodeCode(),context.getButtonCode(),context.getOperationType());
        boolean supplierAssignment=SupplierAssignmentResult.isRoute(context.getNodeCode(),context.getButtonCode(),context.getOperationType());
        boolean assignment=customerAssignment || supplierAssignment || "ASSIGN".equals(context.getOperationType())
            || MaterialCancelAssignmentResult.isRoute(context.getNodeCode(),context.getButtonCode(),context.getOperationType());
        boolean supplierTargets = ArchiveBatchResult.isSupplierFreeze(context.getNodeCode(),context.getButtonCode(),context.getOperationType());
        boolean treeSave = context.getArchiveTreeSaveDraft()!=null;
        boolean partnerBatch = ArchiveBatchResult.isPartnerBatchUpdate(context.getNodeCode(),context.getButtonCode(),context.getOperationType())
            && context.getPartnerBatchDraft()!=null;
        boolean productionCascade = "PrSave".equals(context.getButtonCode())
            && ("10140MAG".equals(context.getNodeCode()) || "10140MAO".equals(context.getNodeCode()));
        for (BusinessObjectRef object : context.getObjects()) {
            if (!assignment && !supplierTargets && !productionCascade && !treeSave && !partnerBatch && shown++ >= 12) { summary.append("其余对象请在业务列表中核对。\n"); break; }
            if(productionCascade && object.getParentClientObjectId()!=null) summary.append("关联物料 ");
            if(treeSave && object.getParentClientObjectId()!=null) summary.append("下级档案 ");
            summary.append(object.getObjectType()).append("：")
                .append(object.getBillCode() == null
                    ? (object.getObjectPk() == null ? "新增对象（正式编码保存后回写）" : object.getObjectPk())
                    : object.getBillCode());
            if (assignment || supplierTargets) { summary.append(" → ").append(object.getTargetOrg()); }
            if(customerAssignment && object.getClientObjectId().startsWith("ca:D:")) summary.append("（系统追加）");
            summary.append('\n');
        }
        summary.append("本批次共 ").append(context.getObjects().size()).append(" 个对象。");
        if (preparation.getPolicy().isSignatureRequired()) { summary.append("签名适用于本批次。"); }
        objects.setText(summary.toString());
        objects.setCaretPosition(0);
        row(fields, 3, customerAssignment ? "客户和目标组织" : supplierAssignment ? "供应商和目标组织" : assignment ? "物料和目标组织" : supplierTargets ? "供应商和目标组织" : productionCascade ? "受影响物料" : treeSave ? "受影响档案" : "单据上下文", new UIScrollPane(objects));
        int nextRow=4;
        if(partnerBatch) {
            UITextArea changes=new UITextArea(3,36);changes.setEditable(false);changes.setLineWrap(true);changes.setWrapStyleWord(true);
            changes.setText(BatchSignatureSummary.format(context.getPartnerBatchDraft()));changes.setCaretPosition(0);
            row(fields,nextRow++,"批改字段与值",new UIScrollPane(changes));
            if(context.getPartnerBatchDraft().hasPageSelection()) {
                final OperationContext current=context;UIButton details=new UIButton("查看本批全部页签行");
                details.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent event) {
                    javax.swing.JOptionPane.showMessageDialog(ElectronicSignatureDialog.this,
                        new QdPartnerOrgPageSelectionPanel(current.getObjects(),current.getPartnerBatchDraft().getSelectedPages()),
                        "本批页签范围",javax.swing.JOptionPane.PLAIN_MESSAGE);
                }});
                row(fields,nextRow++,"页签范围",details);
            }
        }
        if (preparation.getPolicy().isSignatureRequired()) { row(fields,nextRow++,"登录密码",password); }
        reason.setMaxLength(2000);
        reason.setLineWrap(true);
        reason.setWrapStyleWord(true);
        reason.setText(context.getReason() == null ? "" : context.getReason());
        row(fields,nextRow,preparation.getPolicy().isReasonRequired() ? "操作事由 *" : "操作事由",
                new UIScrollPane(reason));
        content.add(fields, BorderLayout.CENTER);
        UIPanel footer = new UIPanel(new BorderLayout());
        footer.add(validation, BorderLayout.NORTH);
        UIPanel buttons = new UIPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(confirm);
        buttons.add(cancel);
        footer.add(buttons, BorderLayout.SOUTH);
        content.add(footer, BorderLayout.SOUTH);
        setContentPane(content);
        confirm.addActionListener(new ActionListener() {
            /** 响应当前控件绑定的界面事件，将处理交给相应查询、保存、导出或关闭路径。 */
            public void actionPerformed(ActionEvent event) { submit(); }
        });
        cancel.addActionListener(new ActionListener() {
            /** 响应当前控件绑定的界面事件，将处理交给相应查询、保存、导出或关闭路径。 */
            public void actionPerformed(ActionEvent event) { closeCancel(); }
        });
        getRootPane().setDefaultButton(confirm);
        setSize(660,partnerBatch && context.getPartnerBatchDraft().hasPageSelection()?590:partnerBatch?550:470);
    }

    /** 按表单布局添加一行标签和输入控件。 */
    private void row(UIPanel panel, int index, String title, java.awt.Component field) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = index; c.gridx = 0; c.anchor = GridBagConstraints.NORTHWEST;
        c.insets = new Insets(5, 0, 5, 12);
        panel.add(new UILabel(title), c);
        c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(5, 0, 5, 0);
        panel.add(field, c);
    }

    /** 检查输入并发起一次后台认证，期间禁用重复提交并清空凭据。 */
    private void submit() {
        if (busy || finished) { return; }
        final String operationReason = reason.getText();
        if (preparation.getPolicy().isReasonRequired()
                && (operationReason == null || operationReason.trim().length() == 0)) {
            validation.setText("请输入操作事由。");
            reason.requestFocusInWindow();
            return;
        }
        if (operationReason != null && (operationReason.length() > 2000
                || operationReason.getBytes(java.nio.charset.Charset.forName("UTF-8")).length > 4000)) {
            validation.setText("操作事由最多 2000 字符且 UTF-8 不超过 4000 字节。");
            return;
        }
        final char[] supplied = preparation.getPolicy().isSignatureRequired() ? password.getPassword() : new char[0];
        if (preparation.getPolicy().isSignatureRequired() && (supplied == null || supplied.length == 0)) {
            if (supplied != null) { Arrays.fill(supplied, '\0'); }
            validation.setText("请输入当前登录人员的密码。");
            password.requestFocusInWindow();
            return;
        }
        busy = true;
        failure = null;
        signatureResult = null;
        confirm.setEnabled(false);
        cancel.setEnabled(false);
        password.setText("");
        try {
            new NcClientWorker<SignatureResult>(this) {
                /** 在携带原生身份的后台线程执行本次远程调用，异常交由统一完成回调处理。 */
                protected SignatureResult callService() throws Exception {
                    try { return service.confirm(preparation.getContext().getRequestId(), supplied, operationReason); }
                    finally { Arrays.fill(supplied, '\0'); }
                }
                /** 在 Swing 界面线程处理成功结果并更新本次请求对应的控件或表格。 */
                protected void completed(SignatureResult result) {
                    signatureResult = result;
                    if (result == null || !preparation.getContext().getRequestId().equals(result.getRequestId())) {
                        failure = new BusinessException("签名服务返回了不匹配的请求，已终止本次操作。");
                    } else if (!result.isReadyForExecution() && !canRetry(result)) {
                        failure = new BusinessException(result.isLocked() ? "账号已锁定，已终止本次操作。"
                                : "确认未成功或请求已结束，请重新发起操作。结果：" + result.getStatus());
                    }
                }
                /** 在界面线程处理后台失败并提示用户，不把失败结果当作成功数据展示。 */
                protected void failed(Throwable error) { failure = error; }
                /** 无论成功或失败都执行的结束钩子，用于清理凭据、解除忙碌状态和刷新按钮可用性。 */
                protected void settled() { Arrays.fill(supplied, '\0'); finishAttempt(); }
            }.execute();
        } catch (Exception error) {
            failure = error;
            Arrays.fill(supplied, '\0');
            finishAttempt();
        }
    }

    /** 密码错误时保留窗口；其他结果结束请求并返回调用方。 */
    private void finishAttempt() {
        password.setText(""); busy = false;
        if (failure == null && canRetry(signatureResult)) {
            validation.setText("密码错误，还可尝试 " + signatureResult.getRemainingAttempts() + " 次。");
            confirm.setEnabled(true);
            cancel.setEnabled(true);
            password.requestFocusInWindow();
            return;
        }
        if (failure != null && cancellation != null) { cancelRequestIfNeeded(); }
        finished = true;
        if (failure == null && signatureResult != null && signatureResult.isReadyForExecution()) { super.closeOK(); }
        else { super.closeCancel(); }
    }

    static boolean canRetry(SignatureResult result) {
        return result != null && "PASSWORD_INCORRECT".equals(result.getStatus())
                && "PENDING".equals(result.getBusinessState()) && !result.isLocked()
                && result.getRemainingAttempts() != null
                && result.getRemainingAttempts().intValue() > 0
                && result.getRemainingAttempts().intValue() < 5;
    }

    /** 处理取消或关闭请求；后台调用期间按窗口规则阻止提前结束，并清理仍可取消的授权。 */
    @Override
    public void closeCancel() {
        if (busy) { return; }
        cancelRequestIfNeeded();
        password.setText("");
        super.closeCancel();
    }

    /** 按确认窗口约定结束成功流程，不允许绕过实际认证直接把界面关闭当作成功。 */
    @Override
    public void closeOK() {
        if (finished && failure == null && signatureResult != null && signatureResult.isReadyForExecution()) {
            super.closeOK();
        }
    }

    /** 将窗口关闭事件纳入本窗口的忙碌状态和取消逻辑，其他事件继续交给父类。 */
    @Override
    protected void processWindowEvent(WindowEvent event) {
        if (event.getID() == WindowEvent.WINDOW_CLOSING) { closeCancel(); return; }
        super.processWindowEvent(event);
    }

    /** 销毁窗口时清理密码并尝试结束未完成请求；父窗口关闭也可能触发本方法。 */
    @Override
    public void dispose() {
        // 模态窗口的父窗口关闭时也可能触发 dispose，仍需清理未完成请求。
        if (cancellation != null) { cancelRequestIfNeeded(); }
        if (password != null) { password.setText(""); }
        super.dispose();
    }

    /** 对尚未完成认证的请求执行共用的一次取消尝试，并保留取消失败。 */
    private void cancelRequestIfNeeded() {
        if (finished || busy) { return; }
        finished = true;
        try {
            cancellation.cancel(reason.getText());
        } catch (Exception error) {
            if (failure == null) { failure = error; }
            else { failure.addSuppressed(error); }
        }
    }

    /** 返回本地确认结果、事由及异常，不返回或保留密码。 */
    public SignatureDialogOutcome getOutcome() {
        return new SignatureDialogOutcome(signatureResult, reason.getText(), failure);
    }

    /** 把可空展示文本转为空字符串，避免窗口标签显示 null。 */
    private String text(String value) { return value == null || value.length() == 0 ? "未提供" : value; }
}
