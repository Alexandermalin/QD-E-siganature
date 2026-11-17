package nc.ui.uapbd.qd.esign;

import java.awt.event.ActionEvent;
import nc.ui.ic.m4455.action.SapplyBillApproveAction;
import nc.ui.pubapp.pub.common.context.PFlowContext;
import nc.ui.pubapp.uif2app.actions.pflow.AbstractScriptExcAction;
import nc.ui.pubapp.uif2app.model.BillManageModel;

/** Retains the original NC approval action and intercepts its confirmed PF call. */
public final class QdSapplyApproveAction extends SapplyBillApproveAction {
    private static final long serialVersionUID=1L;
    private final SapplyApprovalCoordinator coordinator=new SapplyApprovalCoordinator(new SapplyApprovalCoordinator.Host(){
        public AbstractScriptExcAction action(){return QdSapplyApproveAction.this;}
        public BillManageModel model(){return getModel();}
        public Class<?> nativeClass(){return SapplyBillApproveAction.class;}
        public boolean isComposite(){return QdSapplyApproveAction.this.isComposite;}
        public void performNative(ActionEvent event){QdSapplyApproveAction.super.actionPerformed(event);}
        public void runNative(ActionEvent event) throws Exception {QdSapplyApproveAction.super.doAction(event);}
        public void fillNative(PFlowContext flow){QdSapplyApproveAction.super.fillUpContext(flow);}
        public void nativeFlow(PFlowContext flow) throws Exception {QdSapplyApproveAction.super.procFlow(flow);}
    },"Approve","审批确认","APPROVE");
    @Override public void actionPerformed(ActionEvent event){coordinator.perform(event);}
    @Override public void doAction(ActionEvent event) throws Exception {coordinator.doAction(event);}
    @Override public void procFlow(PFlowContext flow) throws Exception {coordinator.procFlow(flow);}
}
