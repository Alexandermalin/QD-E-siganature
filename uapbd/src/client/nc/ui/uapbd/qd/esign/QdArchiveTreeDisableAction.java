package nc.ui.uapbd.qd.esign;

import java.awt.Container;
import java.awt.event.ActionEvent;
import java.util.Collections;
import java.util.UUID;
import javax.swing.Action;
import nc.bs.bd.util.IEnableMsgConstant;
import nc.bs.framework.common.InvocationInfoProxy;
import nc.bs.framework.common.NCLocator;
import nc.itf.uapbd.qd.esign.IOperationService;
import nc.ui.bd.pub.actions.ManageModeActionInterceptor;
import nc.ui.pubapp.uif2app.actions.interceptor.CompositeActionInterceptor;
import nc.ui.uif2.ShowStatusBarMsgUtil;
import nc.ui.uif2.actions.ActionInterceptor;
import nc.ui.uif2.actions.TreeDisableAction;
import nc.ui.uif2.model.HierachicalDataAppModel;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.uapbd.qd.esign.ArchiveTreeDisableCommand;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.OperationResponse;

/** Preserves NC tree permission checks and confirmation before preparing the controlled disable. */
public final class QdArchiveTreeDisableAction extends TreeDisableAction
        implements OperationContextProvider, AuthorizedRequestConsumer {
    private static final long serialVersionUID = 1L;
    private final ManageModeActionInterceptor manageMode = new ManageModeActionInterceptor();
    private ActionEvent activeEvent;
    private SuperVO activeRoot;
    private OperationContext authorized;
    private String dispatched;

    public QdArchiveTreeDisableAction() { setInterceptor(manageMode); }

    @Override public void setTreeModel(HierachicalDataAppModel model) {
        super.setTreeModel(model);
        manageMode.setModel(model);
    }

    @Override public void doAction(ActionEvent event) throws Exception {
        if (activeEvent != null) throw new BusinessException("分类停用正在处理，请勿重复操作。");
        activeEvent = event;
        try { super.doAction(event); }
        finally { activeEvent = null; activeRoot = null; authorized = null; dispatched = null; }
    }

    @Override public SuperVO doDisable(SuperVO value) throws Exception {
        if (activeEvent == null || activeRoot != null || value == null) {
            throw new BusinessException("分类停用缺少有效的原生操作上下文。");
        }
        activeRoot = value;
        ActionInterceptor original = getInterceptor();
        ElectronicSignatureActionInterceptor signing = new ElectronicSignatureActionInterceptor();
        signing.setContextProvider(this);
        signing.setRequestConsumer(this);
        CompositeActionInterceptor chain = new CompositeActionInterceptor();
        chain.addInterceptor(signing);
        SignatureLifecycleInterceptor outer = null;
        Throwable failure = null;
        boolean accepted = false;
        try {
            setInterceptor(chain);
            outer = SignatureLifecycleInstaller.install(this, signing);
            accepted = outer.beforeDoAction(this, activeEvent);
            if (!accepted) throw new BusinessException("分类停用已取消或授权未通过。");
            OperationContext request = authorized;
            if (request == null || !sameCurrentScope(request, value) || dispatched != null) {
                throw new BusinessException("分类停用范围已变化，请重新操作。");
            }
            String requestId = request.getRequestId();
            dispatched = requestId;
            OperationResponse response = NCLocator.getInstance().lookup(IOperationService.class)
                .execute(requestId, new ArchiveTreeDisableCommand(value));
            if (response == null || response.isReplayed() || !requestId.equals(response.getRequestId())
                    || response.getBusinessResult() == null || response.getBusinessResult().getClass() != value.getClass()
                    || !value.getPrimaryKey().equals(((SuperVO)response.getBusinessResult()).getPrimaryKey())) {
                throw new BusinessException("无法确认分类停用结果，请重新查询后核对。");
            }
            outer.afterDoActionSuccessed(this, activeEvent);
            ShowStatusBarMsgUtil.showStatusBarMsg(IEnableMsgConstant.getDisableSuccessMsg(), getTreeModel().getContext());
            return (SuperVO)response.getBusinessResult();
        } catch (Exception error) {
            failure = error;
            if (accepted && outer != null) outer.afterDoActionFailed(this, activeEvent, error);
            throw error;
        } catch (Error error) {
            failure = error;
            throw error;
        } finally {
            signing.finishLifecycle(signing.lifecycleGeneration(), this, activeEvent, failure);
            setInterceptor(original);
            activeRoot = null;
            authorized = null;
            dispatched = null;
        }
    }

    @Override public OperationContext createContext(Action action, ActionEvent event) throws BusinessException {
        if (action != this || event != activeEvent || activeRoot == null || getTreeModel() == null
                || getTreeModel().getContext() == null) throw new BusinessException("无法确定本次分类停用。");
        String node = getTreeModel().getContext().getNodeCode();
        String domain = domain(node), group = getTreeModel().getContext().getPk_group();
        String org = groupNode(node) ? group : getTreeModel().getContext().getPk_org();
        if (domain == null || group == null || org == null || activeRoot.getPrimaryKey() == null
                || !group.equals(activeRoot.getAttributeValue("pk_group"))) {
            throw new BusinessException("无法确定所选分类的节点、集团或操作组织。");
        }
        Object code = activeRoot.getAttributeValue("code");
        BusinessObjectRef root = new BusinessObjectRef(UUID.randomUUID().toString(), domain, activeRoot.getPrimaryKey(),
            code instanceof String ? (String)code : null, null);
        return new OperationContext(null, null, group, org, node, "10140RACK".equals(node) ? "货位" : "基础档案分类", "Disable", "停用",
            "DISABLE", null, Collections.singletonList(root), OperationContext.OWNER_POLICY_PROTOCOL);
    }

    @Override public boolean isAvailable(OperationContext context) {
        if (context == null || !"DISABLE".equals(context.getOperationType()) || !"Disable".equals(context.getButtonCode())
                || !OperationContext.OWNER_POLICY_PROTOCOL.equals(context.getPolicyProtocol())
                || domain(context.getNodeCode()) == null || context.getGroupId() == null || context.getOrgId() == null) return false;
        int roots = 0;
        for (BusinessObjectRef object : context.getObjects()) {
            if (!domain(context.getNodeCode()).equals(object.getObjectType()) || object.getObjectPk() == null) return false;
            if (object.getParentClientObjectId() == null) roots++;
        }
        return roots == 1;
    }

    @Override public boolean bind(OperationContext context) {
        if (authorized != null || !isAvailable(context) || context.getRequestId() == null || activeRoot == null
                || !sameCurrentScope(context, activeRoot)) return false;
        authorized = context;
        return true;
    }
    @Override public boolean wasDispatched(String requestId) { return requestId != null && requestId.equals(dispatched); }
    @Override public void release(String requestId) {
        if (authorized != null && authorized.getRequestId().equals(requestId)) { authorized = null; dispatched = null; }
    }
    @Override public Container getParentContainer() { return getTreeModel().getContext().getEntranceUI(); }
    @Override public String getCurrentUserDisplayName() { return InvocationInfoProxy.getInstance().getUserCode(); }

    private boolean sameCurrentScope(OperationContext context, SuperVO value) {
        if (getTreeModel() == null || getTreeModel().getContext() == null || value == null) return false;
        String node = getTreeModel().getContext().getNodeCode();
        String group = getTreeModel().getContext().getPk_group();
        String org = groupNode(node) ? group : getTreeModel().getContext().getPk_org();
        if (!context.getNodeCode().equals(node) || !context.getGroupId().equals(group) || !context.getOrgId().equals(org)) return false;
        Object selected = getTreeModel().getSelectedData();
        if (!(selected instanceof SuperVO) || !value.getPrimaryKey().equals(((SuperVO)selected).getPrimaryKey())) return false;
        for (BusinessObjectRef ref : context.getObjects()) {
            if (ref.getParentClientObjectId() == null) return ref.getObjectPk().equals(value.getPrimaryKey());
        }
        return false;
    }
    private static boolean groupNode(String node) {
        return "10140CCLG".equals(node) || "10140SCLG".equals(node) || "10140MCLG".equals(node);
    }
    private static String domain(String node) {
        if ("10140CCLG".equals(node) || "10140CCLO".equals(node)) return "BD_CUSTCLASS";
        if ("10140SCLG".equals(node) || "10140SCLO".equals(node)) return "BD_SUPPLIERCLASS";
        if ("10140MCLG".equals(node) || "10140MCLO".equals(node)) return "BD_MARBASCLASS";
        if ("10140RACK".equals(node)) return "BD_RACK";
        return null;
    }
}
