package nc.ui.pu.qd.esign;

import java.awt.event.ActionEvent;
import nc.ui.pu.m20.action.PraybillSaveAction;
import nc.ui.pubapp.pub.common.context.PFlowContext;

public final class QdPraybillSaveAction extends PraybillSaveAction {
    private static final long serialVersionUID = 1L;
    private final ProcurementActionGuard guard = new ProcurementActionGuard(this, ProcurementActionGuard.Kind.PRAYBILL, false);

    @Override public void actionPerformed(ActionEvent event) {
        try {
            super.actionPerformed(event);
        } catch (RuntimeException failure) {
            if (!ProcurementActionGuard.isSaveCancelled(failure)) throw failure;
        }
        guard.checkClosingSave(event);
    }

    @Override protected void processExceptionHandler(Exception failure) {
        if (!ProcurementActionGuard.isSaveCancelled(failure)) super.processExceptionHandler(failure);
    }

    @Override public void doAction(final ActionEvent event) throws Exception {
        guard.runAction(event, new ProcurementActionGuard.NativeCall() {
            @Override public void run() throws Exception {
                QdPraybillSaveAction.super.doAction(event);
            }
        });
    }

    @Override public void procFlow(final PFlowContext flow) throws Exception {
        guard.runFlow(new ProcurementActionGuard.NativeCall() {
            @Override public void run() throws Exception {
                QdPraybillSaveAction.super.procFlow(flow);
            }
        });
    }

    @Override protected void fillUpContext(PFlowContext flow) {
        super.fillUpContext(flow);
        guard.filled(flow);
    }
}
