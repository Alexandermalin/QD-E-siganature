package nc.ui.uapbd.qd.esign;

import java.awt.Container;
import java.awt.event.ActionEvent;
import java.util.Collections;
import java.util.UUID;
import javax.swing.Action;
import nc.bs.framework.common.InvocationInfoProxy;
import nc.bs.uif2.BusinessExceptionAdapter;
import nc.ui.bd.pub.orginfo.model.OrgInfoBillManageModel;
import nc.ui.pub.bill.BillItem;
import nc.ui.pubapp.uif2app.actions.interceptor.CompositeActionInterceptor;
import nc.ui.uif2.UIState;
import nc.ui.uif2.actions.ActionInterceptor;
import nc.ui.uif2.actions.SaveAction;
import nc.ui.uif2.editor.BillForm;
import nc.vo.bd.material.MaterialVO;
import nc.vo.bd.material.prod.MaterialProdVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.pub.lang.UFBoolean;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.MaterialOrgPage;
import nc.vo.uapbd.qd.esign.MaterialProdImpact;

/** Intercepts a group material page's native edit save before its final update service call. */
public final class QdMaterialOrgInfoSaveAction extends SaveAction implements OperationContextProvider {
    private static final long serialVersionUID = 1L;
    private final QdMaterialOrgInfoModelService service;
    private QdMaterialListModelDataManager operationScope;
    private ActionEvent activeEvent;
    private SignatureLifecycleInterceptor activeInterceptor;
    private OperationContext draft;

    public QdMaterialOrgInfoSaveAction(QdMaterialOrgInfoModelService service) {
        this.service = service;
    }

    public void setOperationScope(QdMaterialListModelDataManager value) { operationScope = value; }

    @Override
    public void actionPerformed(ActionEvent event) {
        if (activeInterceptor != null) {
            if (isClosingSave(event)) {
                throw saveNotFinished();
            }
            return;
        }
        if (!isMaterialEdit()) {
            super.actionPerformed(event);
            return;
        }

        ActionInterceptor original = getInterceptor();
        ElectronicSignatureActionInterceptor signing = new ElectronicSignatureActionInterceptor() {
            @Override
            public boolean beforeDoAction(Action action, ActionEvent actionEvent) {
                boolean accepted = super.beforeDoAction(action, actionEvent);
                if (accepted) {
                    service.acceptPreparation(draft);
                }
                return accepted;
            }
        };
        signing.setContextProvider(this);
        signing.setRequestConsumer(service);
        CompositeActionInterceptor chain = new CompositeActionInterceptor();
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
                signing.finishLifecycle(signing.lifecycleGeneration(), this, event, failure);
            } finally {
                service.finishAttempt();
                if (getInterceptor() == activeInterceptor || getInterceptor() == chain) {
                    setInterceptor(original);
                }
                activeInterceptor = null;
                activeEvent = null;
                draft = null;
            }
        }
        if (isClosingSave(event) && isMaterialEdit()) {
            throw saveNotFinished();
        }
    }

    @Override
    public void doAction(ActionEvent event) throws Exception {
        if ((activeInterceptor != null || isMaterialEdit())
                && (activeInterceptor == null || event != activeEvent || getInterceptor() != activeInterceptor
                    || !isMaterialEdit() || model().getService() != service)) {
            throw new BusinessException("物料页签保存接入状态已变化，请重新发起保存。");
        }
        super.doAction(event);
    }

    @Override
    protected void doEditSave(Object value) throws Exception {
        if (activeInterceptor == null) {
            super.doEditSave(value);
            return;
        }
        requireParent(service.requireRow(value));
        service.beginSave(value);
        try {
            super.doEditSave(value);
        } finally {
            service.endSave();
        }
    }

    @Override
    public OperationContext createContext(Action action, ActionEvent event) throws BusinessException {
        if (action != this || event != activeEvent || !isMaterialEdit() || model().getService() != service) {
            throw new BusinessException("当前动作不属于已接入的物料页签保存。");
        }
        SuperVO row = service.requireRow(model().getSelectedData());
        MaterialVO material = requireParent(row);
        String group = model().getContext().getPk_group();
        String node = model().getContext().getNodeCode();
        String org = "10140MAG".equals(node) ? group : operationScope == null ? null : operationScope.requireOperationOrganization();
        if (org == null) { throw new BusinessException("无法确定物料页签的实际操作组织。"); }
        BusinessObjectRef object = new BusinessObjectRef(UUID.randomUUID().toString(), service.getObjectType(),
            row.getPrimaryKey(), material.getCode(), null);
        MaterialProdImpact impact="PrSave".equals(service.getButton())?productionImpact((MaterialProdVO)row):null;
        draft = new OperationContext(null, null, group, org, node,
            "10140MAG".equals(node) ? "物料-集团" : "物料-业务单元", service.getButton(),
            MaterialOrgPage.forButton(service.getButton()).label,
            "UPDATE", null, Collections.singletonList(object), OperationContext.OWNER_POLICY_PROTOCOL,impact);
        return draft;
    }

    private MaterialProdImpact productionImpact(MaterialProdVO selected) throws BusinessException {
        if(!(getEditor() instanceof BillForm)) throw new BusinessException("无法读取物料生产信息表单。");
        BillForm form=(BillForm)getEditor();
        BillItem org=form.getBillCardPanel().getHeadItem("pk_org"),cost=form.getBillCardPanel().getHeadItem("pk_marcostclass"),
            feature=form.getBillCardPanel().getHeadItem("classfeature");
        Object orgValue=org==null?selected.getPk_org():org.getValueObject();
        Object costValue=cost==null?selected.getPk_marcostclass():cost.getValueObject();
        Object featureValue=feature==null?selected.getClassfeature():feature.getValueObject();
        if(!(orgValue instanceof String) || (costValue!=null && !(costValue instanceof String)))
            throw new BusinessException("无法确定生产信息的目标组织或成本类。");
        return new MaterialProdImpact((String)orgValue,(String)costValue,productionFeature(featureValue));
    }
    static boolean productionFeature(Object raw) throws BusinessException {
        if(raw instanceof Boolean) return ((Boolean)raw).booleanValue();
        if(raw instanceof UFBoolean) return ((UFBoolean)raw).booleanValue();
        if("Y".equals(raw) || "true".equalsIgnoreCase(String.valueOf(raw))) return true;
        if("N".equals(raw) || "false".equalsIgnoreCase(String.valueOf(raw))) return false;
        throw new BusinessException("无法确定生产成本类特征设置，请检查表单后重新保存。");
    }

    private MaterialVO requireParent(SuperVO row) throws BusinessException {
        String group = model().getContext().getPk_group();
        Object value = model().getBaseInfoVO();
        if (!(value instanceof MaterialVO) || group == null || !group.equals(row.getAttributeValue("pk_group"))) {
            throw new BusinessException("无法确定物料页签数据行所属的集团物料。");
        }
        MaterialVO material = (MaterialVO) value;
        if (!group.equals(material.getPk_group()) || material.getPk_org() == null || material.getPrimaryKey() == null
                || !material.getPrimaryKey().equals(row.getAttributeValue("pk_material"))) {
            throw new BusinessException("物料页签数据行与当前集团物料不一致。");
        }
        return material;
    }

    private OrgInfoBillManageModel model() { return (OrgInfoBillManageModel) getModel(); }

    private boolean isMaterialEdit() {
        return getModel() instanceof OrgInfoBillManageModel && model().getContext() != null
            && ("10140MAG".equals(model().getContext().getNodeCode()) || "10140MAO".equals(model().getContext().getNodeCode()))
            && model().getUiState() == UIState.EDIT;
    }

    @Override
    public Container getParentContainer() { return model().getContext().getEntranceUI(); }

    @Override
    public String getCurrentUserDisplayName() { return InvocationInfoProxy.getInstance().getUserCode(); }

    private boolean isClosingSave(ActionEvent event) {
        return event != null && event.getSource() == this && event.getID() == 0 && event.getActionCommand() == null;
    }

    private BusinessExceptionAdapter saveNotFinished() {
        return new BusinessExceptionAdapter(new BusinessException("物料页签尚未保存，窗口保持打开。"));
    }
}
