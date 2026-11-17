package nc.ui.uapbd.qd.esign;

import java.awt.event.ActionEvent;
import nc.ui.ic.m4455.action.SapplyBillUnApproveAction;
import nc.ui.pubapp.pub.common.context.PFlowContext;
import nc.ui.pubapp.uif2app.actions.pflow.AbstractScriptExcAction;
import nc.ui.pubapp.uif2app.model.BillManageModel;

/** Retains the original NC unapproval action and intercepts its confirmed PF call. */
public final class QdSapplyUnApproveAction extends SapplyBillUnApproveAction {
    private static final long serialVersionUID=1L;
    private final SapplyApprovalCoordinator coordinator=new SapplyApprovalCoordinator(new SapplyApprovalCoordinator.Host(){
        public AbstractScriptExcAction action(){return QdSapplyUnApproveAction.this;}
        public BillManageModel model(){return getModel();}
        public Class<?> nativeClass(){return SapplyBillUnApproveAction.class;}
        public boolean isComposite(){return QdSapplyUnApproveAction.this.isComposite;}
        public void performNative(ActionEvent event){QdSapplyUnApproveAction.super.actionPerformed(event);}
        public void runNative(ActionEvent event) throws Exception {QdSapplyUnApproveAction.super.doAction(event);}
        public void fillNative(PFlowContext flow){QdSapplyUnApproveAction.super.fillUpContext(flow);}
        public void nativeFlow(PFlowContext flow) throws Exception {QdSapplyUnApproveAction.super.procFlow(flow);}
    },"UnApprove","取消审批","UNAPPROVE");
    @Override public void actionPerformed(ActionEvent event){coordinator.perform(event);}
    @Override public void doAction(ActionEvent event) throws Exception {coordinator.doAction(event);}
    @Override public void procFlow(PFlowContext flow) throws Exception {coordinator.procFlow(flow);}
}
