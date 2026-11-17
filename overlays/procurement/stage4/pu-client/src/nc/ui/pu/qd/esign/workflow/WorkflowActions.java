package nc.ui.pu.qd.esign.workflow;

import java.awt.event.ActionEvent;
import java.util.Collections;
import java.util.List;
import nc.ui.pu.m25.action.processor.InvoiceMultiScriptRunner;
import nc.ui.pubapp.pub.common.context.PFlowContext;
import nc.ui.pubapp.uif2app.actions.pflow.MultiBillScriptRunner;
import nc.ui.pubapp.uif2app.actions.pflow.ScriptPFlowAction;

/** Supplies the PF guard around the original purchase action and its native batch worker. */
public final class WorkflowActions {
    private WorkflowActions() { }

    public static final class StoreReqCommit extends nc.ui.pu.m422x.action.SendApproveAction {
        private static final long serialVersionUID = 1L;

        @Override public void doAction(final ActionEvent event) throws Exception {
            WorkflowActionBridge.perform(this, "40040000", "Commit", event, new WorkflowActionBridge.NativeAction() {
                @Override public void run() throws Exception {
                    StoreReqCommit.super.doAction(event);
                }
            });
        }

        @Override public MultiBillScriptRunner getMultibillScriptRunner() {
            if (multibillScriptRunner == null) {
                multibillScriptRunner = new WorkflowRunner(flowContext, this, "40040000", "Commit");
            }
            return multibillScriptRunner;
        }
    }

    public static final class StoreReqUnCommit extends nc.ui.pu.pub.action.UnSaveScriptAction {
        private static final long serialVersionUID = 1L;

        @Override public void doAction(final ActionEvent event) throws Exception {
            WorkflowActionBridge.perform(this, "40040000", "UnCommit", event, new WorkflowActionBridge.NativeAction() {
                @Override public void run() throws Exception {
                    StoreReqUnCommit.super.doAction(event);
                }
            });
        }

        @Override public MultiBillScriptRunner getMultibillScriptRunner() {
            if (multibillScriptRunner == null) {
                multibillScriptRunner = new WorkflowRunner(flowContext, this, "40040000", "UnCommit");
            }
            return multibillScriptRunner;
        }
    }

    public static final class StoreReqApprove extends nc.ui.pu.m422x.action.StoreReqAppApproveAction {
        private static final long serialVersionUID = 1L;

        @Override public void doAction(final ActionEvent event) throws Exception {
            WorkflowActionBridge.perform(this, "40040000", "Approve", event, new WorkflowActionBridge.NativeAction() {
                @Override public void run() throws Exception {
                    StoreReqApprove.super.doAction(event);
                }
            });
        }

        @Override public MultiBillScriptRunner getMultibillScriptRunner() {
            if (multibillScriptRunner == null) {
                multibillScriptRunner = new WorkflowRunner(flowContext, this, "40040000", "Approve");
            }
            return multibillScriptRunner;
        }
    }

    public static final class StoreReqUnApprove extends nc.ui.pu.m422x.action.StoreReqAppUnApproveAction {
        private static final long serialVersionUID = 1L;

        @Override public void doAction(final ActionEvent event) throws Exception {
            WorkflowActionBridge.perform(this, "40040000", "UnApprove", event, new WorkflowActionBridge.NativeAction() {
                @Override public void run() throws Exception {
                    StoreReqUnApprove.super.doAction(event);
                }
            });
        }

        @Override public MultiBillScriptRunner getMultibillScriptRunner() {
            if (multibillScriptRunner == null) {
                multibillScriptRunner = new WorkflowRunner(flowContext, this, "40040000", "UnApprove");
            }
            return multibillScriptRunner;
        }
    }

    public static final class PraybillCommit extends nc.ui.pu.m20.action.PraybillSendApproveAction {
        private static final long serialVersionUID = 1L;

        @Override public void doAction(final ActionEvent event) throws Exception {
            WorkflowActionBridge.perform(this, "40040200", "Commit", event, new WorkflowActionBridge.NativeAction() {
                @Override public void run() throws Exception {
                    PraybillCommit.super.doAction(event);
                }
            });
        }

        @Override public MultiBillScriptRunner getMultibillScriptRunner() {
            if (multibillScriptRunner == null) {
                multibillScriptRunner = new WorkflowRunner(flowContext, this, "40040200", "Commit");
            }
            return multibillScriptRunner;
        }
    }

    public static final class PraybillUnCommit extends nc.ui.pu.pub.action.UnSaveScriptAction {
        private static final long serialVersionUID = 1L;

        @Override public void doAction(final ActionEvent event) throws Exception {
            WorkflowActionBridge.perform(this, "40040200", "UnCommit", event, new WorkflowActionBridge.NativeAction() {
                @Override public void run() throws Exception {
                    PraybillUnCommit.super.doAction(event);
                }
            });
        }

        @Override public MultiBillScriptRunner getMultibillScriptRunner() {
            if (multibillScriptRunner == null) {
                multibillScriptRunner = new WorkflowRunner(flowContext, this, "40040200", "UnCommit");
            }
            return multibillScriptRunner;
        }
    }

    public static final class PraybillApprove extends nc.ui.pu.m20.action.PraybillApproveAction {
        private static final long serialVersionUID = 1L;

        @Override public void doAction(final ActionEvent event) throws Exception {
            WorkflowActionBridge.perform(this, "40040200", "Approve", event, new WorkflowActionBridge.NativeAction() {
                @Override public void run() throws Exception {
                    PraybillApprove.super.doAction(event);
                }
            });
        }

        @Override public MultiBillScriptRunner getMultibillScriptRunner() {
            if (multibillScriptRunner == null) {
                multibillScriptRunner = new WorkflowRunner(flowContext, this, "40040200", "Approve");
            }
            return multibillScriptRunner;
        }
    }

    public static final class PraybillUnApprove extends nc.ui.pu.m20.action.PraybillUnApproveAction {
        private static final long serialVersionUID = 1L;

        @Override public void doAction(final ActionEvent event) throws Exception {
            WorkflowActionBridge.perform(this, "40040200", "UnApprove", event, new WorkflowActionBridge.NativeAction() {
                @Override public void run() throws Exception {
                    PraybillUnApprove.super.doAction(event);
                }
            });
        }

        @Override public MultiBillScriptRunner getMultibillScriptRunner() {
            if (multibillScriptRunner == null) {
                multibillScriptRunner = new WorkflowRunner(flowContext, this, "40040200", "UnApprove");
            }
            return multibillScriptRunner;
        }
    }

    public static final class OrderCommit extends nc.ui.pu.m21.action.SendApproveAction {
        private static final long serialVersionUID = 1L;

        @Override public void doAction(final ActionEvent event) throws Exception {
            WorkflowActionBridge.perform(this, "40040400", "Commit", event, new WorkflowActionBridge.NativeAction() {
                @Override public void run() throws Exception {
                    OrderCommit.super.doAction(event);
                }
            });
        }

        @Override public MultiBillScriptRunner getMultibillScriptRunner() {
            if (multibillScriptRunner == null) {
                multibillScriptRunner = new WorkflowRunner(flowContext, this, "40040400", "Commit");
            }
            return multibillScriptRunner;
        }
    }

    public static final class OrderUnCommit extends nc.ui.pu.pub.action.UnSaveScriptAction {
        private static final long serialVersionUID = 1L;

        @Override public void doAction(final ActionEvent event) throws Exception {
            WorkflowActionBridge.perform(this, "40040400", "UnCommit", event, new WorkflowActionBridge.NativeAction() {
                @Override public void run() throws Exception {
                    OrderUnCommit.super.doAction(event);
                }
            });
        }

        @Override public MultiBillScriptRunner getMultibillScriptRunner() {
            if (multibillScriptRunner == null) {
                multibillScriptRunner = new WorkflowRunner(flowContext, this, "40040400", "UnCommit");
            }
            return multibillScriptRunner;
        }
    }

    public static final class OrderApprove extends nc.ui.pu.m21.action.OrderApproveAction {
        private static final long serialVersionUID = 1L;

        @Override public void doAction(final ActionEvent event) throws Exception {
            WorkflowActionBridge.perform(this, "40040400", "Approve", event, new WorkflowActionBridge.NativeAction() {
                @Override public void run() throws Exception {
                    OrderApprove.super.doAction(event);
                }
            });
        }

        @Override public MultiBillScriptRunner getMultibillScriptRunner() {
            if (multibillScriptRunner == null) {
                multibillScriptRunner = new WorkflowRunner(flowContext, this, "40040400", "Approve");
            }
            return multibillScriptRunner;
        }
    }

    public static final class OrderUnApprove extends nc.ui.pu.m21.action.OrderUnApproveAction {
        private static final long serialVersionUID = 1L;

        @Override public void doAction(final ActionEvent event) throws Exception {
            WorkflowActionBridge.perform(this, "40040400", "UnApprove", event, new WorkflowActionBridge.NativeAction() {
                @Override public void run() throws Exception {
                    OrderUnApprove.super.doAction(event);
                }
            });
        }

        @Override public MultiBillScriptRunner getMultibillScriptRunner() {
            if (multibillScriptRunner == null) {
                multibillScriptRunner = new WorkflowRunner(flowContext, this, "40040400", "UnApprove");
            }
            return multibillScriptRunner;
        }
    }

    public static final class InvoiceCommit extends nc.ui.pu.m25.action.InvoiceSendApproveAction {
        private static final long serialVersionUID = 1L;

        @Override public void doAction(final ActionEvent event) throws Exception {
            WorkflowActionBridge.perform(this, "40041000", "Commit", event, new WorkflowActionBridge.NativeAction() {
                @Override public void run() throws Exception {
                    InvoiceCommit.super.doAction(event);
                }
            });
        }

        @Override public MultiBillScriptRunner getMultibillScriptRunner() {
            if (multibillScriptRunner == null) {
                multibillScriptRunner = new WorkflowRunner(flowContext, this, "40041000", "Commit");
            }
            return multibillScriptRunner;
        }
    }

    public static final class InvoiceUnCommit extends nc.ui.pu.pub.action.UnSaveScriptAction {
        private static final long serialVersionUID = 1L;

        @Override public void doAction(final ActionEvent event) throws Exception {
            WorkflowActionBridge.perform(this, "40041000", "UnCommit", event, new WorkflowActionBridge.NativeAction() {
                @Override public void run() throws Exception {
                    InvoiceUnCommit.super.doAction(event);
                }
            });
        }

        @Override public MultiBillScriptRunner getMultibillScriptRunner() {
            if (multibillScriptRunner == null) {
                multibillScriptRunner = new WorkflowRunner(flowContext, this, "40041000", "UnCommit");
            }
            return multibillScriptRunner;
        }
    }

    public static final class InvoiceApprove extends nc.ui.pu.m25.action.InvoiceApproveAction {
        private static final long serialVersionUID = 1L;

        @Override public void doAction(final ActionEvent event) throws Exception {
            WorkflowActionBridge.perform(this, "40041000", "Approve", event, new WorkflowActionBridge.NativeAction() {
                @Override public void run() throws Exception {
                    InvoiceApprove.super.doAction(event);
                }
            });
        }

        @Override public MultiBillScriptRunner getMultibillScriptRunner() {
            if (multibillScriptRunner == null) {
                multibillScriptRunner = new InvoiceApprovalRunner(flowContext, this);
            }
            return multibillScriptRunner;
        }
    }

    public static final class InvoiceUnApprove extends nc.ui.pu.m25.action.InvoiceUnApproveAction {
        private static final long serialVersionUID = 1L;

        @Override public void doAction(final ActionEvent event) throws Exception {
            WorkflowActionBridge.perform(this, "40041000", "UnApprove", event, new WorkflowActionBridge.NativeAction() {
                @Override public void run() throws Exception {
                    InvoiceUnApprove.super.doAction(event);
                }
            });
        }

        @Override public MultiBillScriptRunner getMultibillScriptRunner() {
            if (multibillScriptRunner == null) {
                multibillScriptRunner = new WorkflowRunner(flowContext, this, "40041000", "UnApprove");
            }
            return multibillScriptRunner;
        }
    }

    private static final class WorkflowRunner extends MultiBillScriptRunner {
        private final ScriptPFlowAction owner;
        private final String node;
        private final String button;

        WorkflowRunner(PFlowContext context, ScriptPFlowAction owner, String node, String button) {
            super(context);
            this.owner = owner;
            this.node = node;
            this.button = button;
            setScriptAction(owner);
            setProcessedBillNumOnce(owner.getProcessedBillNumOnce());
        }

        @Override protected List<Object> taskRun(List<String> detail, List<Object> oldSuccessful) {
            try (WorkflowActionBridge.Scope scope = openWorker(owner, node, button, detail)) {
                return scope == null ? Collections.<Object>emptyList() : super.taskRun(detail, oldSuccessful);
            }
        }
    }

    // The invoice runner adds original invoice-specific messages to batch approval results.
    private static final class InvoiceApprovalRunner extends InvoiceMultiScriptRunner {
        private final ScriptPFlowAction owner;

        InvoiceApprovalRunner(PFlowContext context, ScriptPFlowAction owner) {
            super(context, owner);
            this.owner = owner;
        }

        @Override protected List<Object> taskRun(List<String> detail, List<Object> oldSuccessful) {
            try (WorkflowActionBridge.Scope scope = openWorker(owner, "40041000", "Approve", detail)) {
                return scope == null ? Collections.<Object>emptyList() : super.taskRun(detail, oldSuccessful);
            }
        }
    }

    private static WorkflowActionBridge.Scope openWorker(ScriptPFlowAction owner,
            String node, String button, List<String> detail) {
        // The NC worker can outlive the Swing action and needs its own thread-local PF guard.
        try {
            return WorkflowActionBridge.open(owner, node, button, null);
        } catch (Exception failure) {
            detail.add(failure.getMessage() == null ? failure.toString() : failure.getMessage());
            return null;
        }
    }
}
