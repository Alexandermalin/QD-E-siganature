package nc.ui.uapbd.qd.esign;

import java.awt.Container;
import java.awt.event.ActionEvent;
import java.util.Collections;
import java.util.UUID;
import javax.swing.Action;
import nc.bs.framework.common.InvocationInfoProxy;
import nc.bs.uif2.BusinessExceptionAdapter;
import nc.ui.bd.material.baseinfo.action.MaterialSaveAction;
import nc.ui.bd.material.baseinfo.model.MaterialBaseInfoModel;
import nc.ui.pub.bill.BillItem;
import nc.ui.pubapp.uif2app.actions.interceptor.CompositeActionInterceptor;
import nc.ui.uif2.UIState;
import nc.ui.uif2.actions.ActionInterceptor;
import nc.ui.uif2.editor.BillForm;
import nc.vo.bd.material.MaterialVO;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;

/** 复用 NC 普通保存动作，在最终原生保存调用处交接授权。 */
final class QdMaterialSaveAction extends MaterialSaveAction implements OperationContextProvider {
    private static final long serialVersionUID = 1L;

    private final QdMaterialModelService service;
    private QdMaterialListModelDataManager operationScope;
    private ActionEvent activeEvent;
    private SignatureLifecycleInterceptor activeInterceptor;
    private OperationContext preparedDraft;

    QdMaterialSaveAction(QdMaterialModelService service) {
        this.service = service;
    }

    public void setOperationScope(QdMaterialListModelDataManager value) { operationScope = value; }

    @Override
    public void actionPerformed(ActionEvent event) {
        if (activeInterceptor != null) {
            // NC 的模态窗口可处理嵌套事件，不能让它们借用当前保存的授权。
            if (isWindowClosingSave(event)) {
                throw saveNotFinished();
            }
            return;
        }
        if (operation() == null) {
            super.actionPerformed(event);
            return;
        }

        ActionInterceptor original = getInterceptor();
        ElectronicSignatureActionInterceptor signing = new ElectronicSignatureActionInterceptor() {
            @Override
            public boolean beforeDoAction(Action action, ActionEvent actionEvent) {
                boolean accepted = super.beforeDoAction(action, actionEvent);
                if (accepted) {
                    service.acceptPreparation(preparedDraft);
                }
                return accepted;
            }
        };
        signing.setContextProvider(this);
        signing.setRequestConsumer(service);
        CompositeActionInterceptor chain = new CompositeActionInterceptor();
        // PubApp 按相反顺序执行 before，原生和已装插件的前置判断先执行。
        chain.addInterceptor(signing);
        if (original != null) {
            chain.addInterceptor(original);
        }
        activeEvent = event;
        Throwable failure = null;
        try {
            setInterceptor(chain);
            activeInterceptor = SignatureLifecycleInstaller.install(this, signing);
            super.actionPerformed(event);
        } catch (RuntimeException error) {
            failure = error;
            throw error;
        } catch (Error error) {
            failure = error;
            throw error;
        } finally {
            try {
                // NCAction 不一定为 Error 或被替换的链执行回调。
                signing.finishLifecycle(signing.lifecycleGeneration(), this, event, failure);
            } finally {
                service.finishAttempt();
                if (getInterceptor() == activeInterceptor || getInterceptor() == chain) {
                    setInterceptor(original);
                }
                activeInterceptor = null;
                activeEvent = null;
                preparedDraft = null;
            }
        }

        // FunNodeClosingHandler 的“是，保存”在 actionPerformed 返回后直接关闭页面。
        if (isWindowClosingSave(event) && operation() != null) {
            throw saveNotFinished();
        }
    }

    @Override
    public void doAction(ActionEvent event) throws Exception {
        String operation = operation();
        if ((activeInterceptor != null || operation != null)
                && (activeInterceptor == null || event != activeEvent
                        || getInterceptor() != activeInterceptor || operation == null
                        || getMaterialBaseInfoModel().getService() != service)) {
            throw new BusinessException("物料保存接入状态已变化，请重新发起保存。");
        }
        super.doAction(event);
    }

    @Override
    protected void doAddSave(Object draft) throws Exception {
        if (activeInterceptor == null) {
            super.doAddSave(draft);
            return;
        }
        service.beginSave(draft, "INSERT");
        try {
            super.doAddSave(draft);
        } finally {
            service.endSave();
        }
    }

    @Override
    protected void doEditSave(Object draft) throws Exception {
        if (activeInterceptor == null) {
            super.doEditSave(draft);
            return;
        }
        service.beginSave(draft, "UPDATE");
        try {
            super.doEditSave(draft);
        } finally {
            service.endSave();
        }
    }

    @Override
    public OperationContext createContext(Action action, ActionEvent event) throws BusinessException {
        String operation = operation();
        if (action != this || event != activeEvent || operation == null) {
            throw new BusinessException("当前动作不属于已接入的物料普通保存。");
        }
        // 原生 getEditor().getValue() 会执行非空校验和清理表体，签名前只读组织及编码字段。
        MaterialBaseInfoModel model = getMaterialBaseInfoModel();
        String node = model.getContext().getNodeCode();
        String group = model.getContext().getPk_group();
        boolean groupNode = "10140MAG".equals(node);
        String desiredOwner = headText("pk_org");
        if (groupNode && desiredOwner == null && "INSERT".equals(operation)) { desiredOwner = group; }
        String org = groupNode ? group : ("INSERT".equals(operation) ? desiredOwner
            : operationScope == null ? null : operationScope.requireOperationOrganization());
        if (org == null || group == null) {
            throw new BusinessException("无法确定本次保存所属的集团或组织。");
        }
        String key = null;
        if ("UPDATE".equals(operation)) {
            Object selected = model.getSelectedData();
            if (!(selected instanceof MaterialVO)) {
                throw new BusinessException("无法确定待修改的物料。");
            }
            MaterialVO material = (MaterialVO) selected;
            key = material.getPrimaryKey();
            if (key == null || !group.equals(material.getPk_group())) {
                throw new BusinessException("待修改物料不属于当前集团。");
            }
            if (desiredOwner == null) { desiredOwner = material.getPk_org(); }
        }
        if (desiredOwner == null) { throw new BusinessException("无法确定物料所属组织。"); }
        BusinessObjectRef material = new BusinessObjectRef(UUID.randomUUID().toString(),
                "BD_MATERIAL", key, headText("code"), null, desiredOwner);
        preparedDraft = new OperationContext(null, null, group, org, node,
                groupNode ? "物料-集团" : "物料-业务单元", "Save", "保存",
                operation, null, Collections.singletonList(material), OperationContext.OWNER_POLICY_PROTOCOL);
        return preparedDraft;
    }

    @Override
    public Container getParentContainer() {
        return getModel().getContext().getEntranceUI();
    }

    @Override
    public String getCurrentUserDisplayName() {
        return InvocationInfoProxy.getInstance().getUserCode();
    }

    private String operation() {
        MaterialBaseInfoModel model = getMaterialBaseInfoModel();
        if (model == null || model.getContext() == null) {
            return null;
        }
        String node = model.getContext().getNodeCode();
        if (("10140MAG".equals(node) || "10140MAO".equals(node)) && model.getUiState() == UIState.EDIT) {
            return "UPDATE";
        }
        if (model.getUiState() == UIState.ADD && model.getCopyPk() == null && model.getCreateVersionPk() == null
                && ("10140MAG".equals(node) || "10140MAO".equals(node))) {
            return "INSERT";
        }
        return null;
    }

    private String headText(String field) {
        BillItem item = ((BillForm) getEditor()).getBillCardPanel().getHeadItem(field);
        Object value = item == null ? null : item.getValueObject();
        return value instanceof String && !((String) value).trim().isEmpty() ? (String) value : null;
    }

    private boolean isWindowClosingSave(ActionEvent event) {
        return event != null && event.getSource() == this && event.getID() == 0 && event.getActionCommand() == null;
    }

    private BusinessExceptionAdapter saveNotFinished() {
        return new BusinessExceptionAdapter(new BusinessException("物料尚未保存，窗口保持打开。"));
    }
}
