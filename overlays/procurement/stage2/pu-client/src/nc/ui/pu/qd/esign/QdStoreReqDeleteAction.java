package nc.ui.pu.qd.esign;

import java.awt.event.ActionEvent;
import nc.ui.pu.m422x.action.DeleteAction;
import nc.ui.pubapp.pub.common.context.PFlowContext;

public final class QdStoreReqDeleteAction extends DeleteAction {
    private static final long serialVersionUID = 1L;
    private final ProcurementActionGuard guard = new ProcurementActionGuard(this, ProcurementActionGuard.Kind.STORE_REQ, true);

    @Override public void doAction(final ActionEvent event) throws Exception {
        guard.runAction(event, new ProcurementActionGuard.NativeCall() {
            @Override public void run() throws Exception {
                QdStoreReqDeleteAction.super.doAction(event);
            }
        });
    }

    @Override public void procFlow(final PFlowContext flow) throws Exception {
        guard.runFlow(new ProcurementActionGuard.NativeCall() {
            @Override public void run() throws Exception {
                QdStoreReqDeleteAction.super.procFlow(flow);
            }
        });
    }

    @Override protected void fillUpContext(PFlowContext flow) {
        super.fillUpContext(flow);
        guard.filled(flow);
    }

    @Override protected void beforeCheck(Object bill) {
        super.beforeCheck(bill);
        guard.beforeBatchBill(bill);
    }
}
