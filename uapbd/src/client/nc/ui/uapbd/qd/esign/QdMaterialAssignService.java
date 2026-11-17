package nc.ui.uapbd.qd.esign;

import java.awt.Container;
import java.awt.KeyboardFocusManager;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import nc.bs.framework.common.InvocationInfoProxy;
import nc.bs.framework.common.NCLocator;
import nc.itf.uapbd.qd.esign.IMaterialAssignmentService;
import nc.itf.uapbd.qd.esign.ISignatureService;
import nc.itf.bd.pub.assign.AssignStatus;
import nc.ui.bd.material.baseinfo.model.MaterialAssignService;
import nc.vo.bd.errorlog.ErrLogReturnValue;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.MaterialAssignmentResult;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.SignaturePreparation;
import nc.vo.uif2.LoginContext;

/** Used only by the original fast assignment dialog for explicitly selected materials. */
public final class QdMaterialAssignService extends MaterialAssignService {
    private LoginContext context;
    private QdMaterialListModelDataManager operationScope;

    @Override public void setContext(LoginContext context) {
        super.setContext(context);
        this.context = context;
    }

    public void setOperationScope(QdMaterialListModelDataManager operationScope) {
        this.operationScope = operationScope;
    }

    boolean boundTo(LoginContext login,QdMaterialListModelDataManager scope) {
        return login!=null && context==login && operationScope==scope;
    }

    @Override public ErrLogReturnValue assignByPks(String[] materials, String[] targets, boolean filterByPermission)
            throws BusinessException {
        if (materials == null || targets == null || materials.length == 0 || targets.length == 0) {
            throw new BusinessException("请选择物料和目标业务单元。");
        }
        if ((long)materials.length * targets.length > 1000) {
            throw new BusinessException("一次最多分配 1000 个物料和目标组织组合。");
        }
        String node = context.getNodeCode(), group = context.getPk_group();
        boolean groupNode = "10140MAG".equals(node);
        String org = groupNode ? group : operationScope.requireOperationOrganization();
        List<BusinessObjectRef> refs = new ArrayList<BusinessObjectRef>();
        for (String material : materials) {
            for (String target : targets) {
                refs.add(new BusinessObjectRef(UUID.randomUUID().toString(), "BD_MATERIAL", material, null,
                    null, null, null, target));
            }
        }
        OperationContext draft = new OperationContext(null, null, group, org, node,
            groupNode ? "物料-集团" : "物料-业务单元", "Assign", "分配", "ASSIGN", null,
            refs, OperationContext.OWNER_POLICY_PROTOCOL);
        ISignatureService signature = NCLocator.getInstance().lookup(ISignatureService.class);
        SignaturePreparation prepared = signature.prepare(draft);
        if (prepared == null || !prepared.requiresExecutionGuard()) {
            throw new BusinessException("物料分配入口尚未启用。");
        }
        String request = prepared.getContext().getRequestId();
        if (!confirm(prepared, signature)) { return null; }
        IMaterialAssignmentService assignment = NCLocator.getInstance().lookup(IMaterialAssignmentService.class);
        MaterialAssignmentResult result;
        try {
            result = assignment.execute(request);
        } catch (BusinessException | RuntimeException error) {
            try { result = assignment.getResult(request); }
            catch (BusinessException | RuntimeException readFailure) {
                error.addSuppressed(readFailure);
                throw new BusinessException("请按请求号 " + request + " 核对分配结果。", error);
            }
            if ("READY".equals(result.getState()) || "PENDING".equals(result.getState())) {
                throw new BusinessException("请求 " + request + " 未进入分配执行。 " + error.getMessage(), error);
            }
        }
        showResult(result);
        // The NC worker closes its progress window and leaves result presentation to this receipt.
        return null;
    }

    @Override public ErrLogReturnValue assignByCondition(String where, AssignStatus status, String[] targets)
            throws BusinessException {
        if(context==null || context.getPk_group()==null
            || !("10140MAG".equals(context.getNodeCode()) || "10140MAO".equals(context.getNodeCode()))
            || "10140MAO".equals(context.getNodeCode()) && operationScope==null)
            throw new BusinessException("物料分配缺少原生上下文。");
        QdAssignmentConditionSelection.Selection selected=QdAssignmentConditionSelection.resolve(where,status,targets,
            new QdAssignmentConditionSelection.Query() {
                public String[] find(String condition,AssignStatus assignStatus,String[] organizations) throws BusinessException {
                    return QdMaterialAssignService.this.queryPksByCondition(condition,assignStatus,organizations);
                }
            });
        return assignByPks(selected.objects(),selected.targets(),true);
    }

    private boolean confirm(final SignaturePreparation prepared, final ISignatureService signature) throws BusinessException {
        if (!prepared.getPolicy().isSignatureRequired() && !prepared.getPolicy().isReasonRequired()) { return true; }
        final RequestCancellation cancel = new RequestCancellation(signature, prepared.getContext().getRequestId());
        final SignatureDialogOutcome[] outcome = new SignatureDialogOutcome[1];
        final String user = InvocationInfoProxy.getInstance().getUserCode();
        Runnable display = new Runnable() {
            public void run() {
                ElectronicSignatureDialog dialog = new ElectronicSignatureDialog(dialogParent(), user, prepared, signature, cancel);
                try {
                    dialog.showModal();
                    outcome[0] = dialog.getOutcome();
                } finally {
                    dialog.dispose();
                }
            }
        };
        try {
            if (SwingUtilities.isEventDispatchThread()) { display.run(); }
            else { SwingUtilities.invokeAndWait(display); }
            if (outcome[0] != null && outcome[0].isConfirmed()) { return true; }
            cancel.cancel(null);
            if (outcome[0] != null && outcome[0].getFailure() != null) {
                throw new BusinessException("物料分配确认失败。", outcome[0].getFailure());
            }
            return false;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new BusinessException("物料分配确认被中断。", error);
        } catch (InvocationTargetException error) {
            try { cancel.cancel(null); }
            catch (Exception cancelFailure) { error.addSuppressed(cancelFailure); }
            throw new BusinessException("无法打开物料分配确认窗口。", error.getCause());
        } catch (BusinessException error) {
            throw error;
        } catch (Exception error) {
            throw new BusinessException("无法确认或取消本次物料分配。", error);
        }
    }

    private Container dialogParent() {
        Container active = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
        return active == null ? context.getEntranceUI() : active;
    }

    private void showResult(final MaterialAssignmentResult result) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                StringBuilder text = new StringBuilder("请求号 ").append(result.getRequestId())
                    .append("\n批次状态 ").append(stateLabel(result.getState())).append("\n\n");
                for (MaterialAssignmentResult.Unit unit : result.getUnits()) {
                    text.append(unit.getCode() == null ? unit.getMaterial() : unit.getCode())
                        .append("  →  ").append(unit.getTarget()).append("  ").append(stateLabel(unit.getState())).append('\n');
                }
                JTextArea details = new JTextArea(text.toString(), 12, 56);
                details.setEditable(false);
                details.setCaretPosition(0);
                JOptionPane.showMessageDialog(dialogParent(), new JScrollPane(details), "物料分配结果",
                    "SUCCESS".equals(result.getState()) ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
            }
        });
    }

    private static String stateLabel(String state) {
        if ("SUCCESS".equals(state)) { return "成功"; }
        if ("ALREADY_ASSIGNED".equals(state)) { return "已分配"; }
        if ("FAILED".equals(state)) { return "失败，未提交"; }
        if ("PARTIAL".equals(state)) { return "部分成功"; }
        if ("CANCELLED".equals(state)) { return "已取消"; }
        if ("EXPIRED".equals(state)) { return "已过期"; }
        return "待核对";
    }
}
