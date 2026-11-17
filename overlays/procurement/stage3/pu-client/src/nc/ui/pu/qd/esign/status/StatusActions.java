package nc.ui.pu.qd.esign.status;

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import nc.itf.pu.m25.IInvoiceMaintain;
import nc.ui.ml.NCLangRes;
import nc.ui.pu.m25.action.InvoiceNormalAction;
import nc.ui.pub.beans.MessageDialog;
import nc.ui.pubapp.pub.task.ISingleBillService;
import nc.ui.pubapp.uif2app.model.BillManageModel;
import nc.ui.pubapp.uif2app.view.BillForm;
import nc.ui.uif2.ShowStatusBarMsgUtil;
import nc.ui.uif2.model.AbstractAppModel;
import nc.vo.jcom.lang.StringUtil;
import nc.vo.ml.AbstractNCLangRes;
import nc.vo.ml.NCLangRes4VoTransl;
import nc.vo.pu.m21.entity.OrderVO;
import nc.vo.pu.m25.entity.InvoiceVO;
import nc.vo.pu.m25.pub.InvoiceServiceUtil;
import nc.vo.pu.pub.util.ObjectUtil;
import nc.vo.pub.lang.UFBoolean;
import nc.vo.pubapp.pattern.model.transfer.bill.ClientBillCombinServer;
import nc.vo.pubapp.pattern.model.transfer.bill.ClientBillToServer;

/** Replaces only the native status beans registered in the four procurement nodes. */
public final class StatusActions {
    private static final String INVOICE_RESOURCES = "4004050_0";

    private StatusActions() { }

    public static final class StoreReqClose extends nc.ui.pu.m422x.action.CloseAction {
        private static final long serialVersionUID = 1L;
        private AbstractAppModel model;

        @Override public void setModel(AbstractAppModel model) {
            super.setModel(model);
            this.model = model;
        }

        @Override public void doAction(final ActionEvent event) throws Exception {
            StatusActionBridge.perform(model, "BillClose", event, new StatusActionBridge.NativeAction() {
                @Override public void run() throws Exception {
                    StoreReqClose.super.doAction(event);
                }
            });
        }
    }

    public static final class StoreReqOpen extends nc.ui.pu.m422x.action.OpenAction {
        private static final long serialVersionUID = 1L;
        private AbstractAppModel model;

        @Override public void setModel(AbstractAppModel model) {
            super.setModel(model);
            this.model = model;
        }

        @Override public void doAction(final ActionEvent event) throws Exception {
            StatusActionBridge.perform(model, "BillOpen", event, new StatusActionBridge.NativeAction() {
                @Override public void run() throws Exception {
                    StoreReqOpen.super.doAction(event);
                }
            });
        }
    }

    public static final class StoreReqRowClose extends nc.ui.pu.m422x.action.RowCloseAction {
        private static final long serialVersionUID = 1L;
        private BillForm billForm;

        @Override public void setBillForm(BillForm billForm) {
            super.setBillForm(billForm);
            this.billForm = billForm;
        }

        @Override public void doAction(final ActionEvent event) throws Exception {
            StatusActionBridge.perform(billForm.getModel(), "LineClose", event, new StatusActionBridge.NativeAction() {
                @Override public void run() throws Exception {
                    StoreReqRowClose.super.doAction(event);
                }
            });
        }
    }

    public static final class StoreReqRowOpen extends nc.ui.pu.m422x.action.RowOpenAction {
        private static final long serialVersionUID = 1L;
        private BillForm billForm;

        @Override public void setBillForm(BillForm billForm) {
            super.setBillForm(billForm);
            this.billForm = billForm;
        }

        @Override public void doAction(final ActionEvent event) throws Exception {
            StatusActionBridge.perform(billForm.getModel(), "LineOpen", event, new StatusActionBridge.NativeAction() {
                @Override public void run() throws Exception {
                    StoreReqRowOpen.super.doAction(event);
                }
            });
        }
    }

    public static final class PraybillClose extends nc.ui.pu.m20.action.PraybillCloseAction {
        private static final long serialVersionUID = 1L;
        private AbstractAppModel model;

        @Override public void setModel(AbstractAppModel model) {
            super.setModel(model);
            this.model = model;
        }

        @Override public void doAction(final ActionEvent event) throws Exception {
            StatusActionBridge.perform(model, "BillClose", event, new StatusActionBridge.NativeAction() {
                @Override public void run() throws Exception {
                    PraybillClose.super.doAction(event);
                }
            });
        }
    }

    public static final class PraybillOpen extends nc.ui.pu.m20.action.PraybillOpenAction {
        private static final long serialVersionUID = 1L;
        private AbstractAppModel model;

        @Override public void setModel(AbstractAppModel model) {
            super.setModel(model);
            this.model = model;
        }

        @Override public void doAction(final ActionEvent event) throws Exception {
            StatusActionBridge.perform(model, "BillOpen", event, new StatusActionBridge.NativeAction() {
                @Override public void run() throws Exception {
                    PraybillOpen.super.doAction(event);
                }
            });
        }
    }

    public static final class PraybillRowClose extends nc.ui.pu.m20.action.PraybillCloseLineAction {
        private static final long serialVersionUID = 1L;
        private AbstractAppModel model;

        @Override public void setModel(AbstractAppModel model) {
            super.setModel(model);
            this.model = model;
        }

        @Override public void doAction(final ActionEvent event) throws Exception {
            StatusActionBridge.perform(model, "LineClose", event, new StatusActionBridge.NativeAction() {
                @Override public void run() throws Exception {
                    PraybillRowClose.super.doAction(event);
                }
            });
        }
    }

    public static final class PraybillRowOpen extends nc.ui.pu.m20.action.PraybillOpenLineAction {
        private static final long serialVersionUID = 1L;
        private AbstractAppModel model;

        @Override public void setModel(AbstractAppModel model) {
            super.setModel(model);
            this.model = model;
        }

        @Override public void doAction(final ActionEvent event) throws Exception {
            StatusActionBridge.perform(model, "LineOpen", event, new StatusActionBridge.NativeAction() {
                @Override public void run() throws Exception {
                    PraybillRowOpen.super.doAction(event);
                }
            });
        }
    }

    public static final class OrderClose extends nc.ui.pu.m21.action.CloseAction {
        private static final long serialVersionUID = 1L;
        private AbstractAppModel model;

        @Override public void setModel(AbstractAppModel model) {
            super.setModel(model);
            this.model = model;
        }

        @Override public void doAction(final ActionEvent event) throws Exception {
            StatusActionBridge.perform(model, "BillClose", event, new StatusActionBridge.NativeAction() {
                @Override public void run() throws Exception {
                    OrderClose.super.doAction(event);
                }
            });
        }
    }

    public static final class OrderOpen extends nc.ui.pu.m21.action.OpenAction {
        private static final long serialVersionUID = 1L;
        private AbstractAppModel model;

        @Override public void setModel(AbstractAppModel model) {
            super.setModel(model);
            this.model = model;
        }

        @Override public void doAction(final ActionEvent event) throws Exception {
            StatusActionBridge.perform(model, "BillOpen", event, new StatusActionBridge.NativeAction() {
                @Override public void run() throws Exception {
                    OrderOpen.super.doAction(event);
                }
            });
        }
    }

    public static final class OrderRowClose extends nc.ui.pu.m21.action.RowCloseAction {
        private static final long serialVersionUID = 1L;
        private BillForm billForm;

        @Override public void setBillForm(BillForm billForm) {
            super.setBillForm(billForm);
            this.billForm = billForm;
        }

        @Override public void doAction(final ActionEvent event) throws Exception {
            StatusActionBridge.perform(billForm.getModel(), "LineClose", event, new StatusActionBridge.NativeAction() {
                @Override public void run() throws Exception {
                    OrderRowClose.super.doAction(event);
                }
            });
        }
    }

    public static final class OrderRowOpen extends nc.ui.pu.m21.action.RowOpenAction {
        private static final long serialVersionUID = 1L;
        private BillForm billForm;

        @Override public void setBillForm(BillForm billForm) {
            super.setBillForm(billForm);
            this.billForm = billForm;
        }

        @Override public void doAction(final ActionEvent event) throws Exception {
            StatusActionBridge.perform(billForm.getModel(), "LineOpen", event, new StatusActionBridge.NativeAction() {
                @Override public void run() throws Exception {
                    OrderRowOpen.super.doAction(event);
                }
            });
        }
    }

    public static final class OrderFreeze extends nc.ui.pu.m21.action.FreezeAction {
        private static final long serialVersionUID = 1L;
        private final ThreadLocal<Boolean> nativeCaller = new ThreadLocal<Boolean>();

        @Override public void doAction(final ActionEvent event) throws Exception {
            performOrderAction(getModel(), "Freeze", event, nativeCaller, new StatusActionBridge.NativeAction() {
                @Override public void run() throws Exception {
                    OrderFreeze.super.doAction(event);
                }
            });
        }

        @Override protected ISingleBillService<Object> getSingleBillService() {
            return checkOrderWorker(getModel(), "Freeze", nativeCaller, super.getSingleBillService());
        }
    }

    public static final class OrderUnfreeze extends nc.ui.pu.m21.action.UnfreezeAction {
        private static final long serialVersionUID = 1L;
        private final ThreadLocal<Boolean> nativeCaller = new ThreadLocal<Boolean>();

        @Override public void doAction(final ActionEvent event) throws Exception {
            performOrderAction(getModel(), "UnFreeze", event, nativeCaller, new StatusActionBridge.NativeAction() {
                @Override public void run() throws Exception {
                    OrderUnfreeze.super.doAction(event);
                }
            });
        }

        @Override protected ISingleBillService<Object> getSingleBillService() {
            return checkOrderWorker(getModel(), "UnFreeze", nativeCaller, super.getSingleBillService());
        }
    }

    private static void performOrderAction(BillManageModel model, String action, ActionEvent event,
            ThreadLocal<Boolean> nativeCaller, StatusActionBridge.NativeAction original) throws Exception {
        Object[] selected = model.getSelectedOperaDatas();
        if (selected != null && selected.length > 1) {
            StatusActionBridge.requireNativeBatch(model, action);
        }
        Boolean previous = nativeCaller.get();
        nativeCaller.set(Boolean.TRUE);
        try {
            StatusActionBridge.perform(model, action, event, original);
        } finally {
            if (previous == null) {
                nativeCaller.remove();
            } else {
                nativeCaller.set(previous);
            }
        }
    }

    private static ISingleBillService<Object> checkOrderWorker(final AbstractAppModel model, final String action,
            final ThreadLocal<Boolean> nativeCaller, final ISingleBillService<Object> original) {
        // MultiBillAction caches this callback in its runner and reuses it on the SwingWorker thread.
        return new ISingleBillService<Object>() {
            @Override public Object operateBill(Object bill) throws Exception {
                if (nativeCaller.get() == null) {
                    StatusActionBridge.requireNativeBatchBill(model, action, (OrderVO) bill);
                }
                return original.operateBill(bill);
            }
        };
    }

    // InvoiceServiceUtil returns a cached service, so the native action's lookup cannot be scoped.
    public static final class InvoiceFreeze extends nc.ui.pu.m25.action.InvoiceFrozenAction {
        private static final long serialVersionUID = 1L;

        @Override public void doAction(ActionEvent event) throws Exception {
            InvoiceVO[] selected = getSelectedVOs();
            if (selected == null || selected.length == 0) {
                return;
            }
            int length = selected[0].getMetaData().getParent().getAttribute("vfrozenreason").getColumn().getLength();
            AbstractNCLangRes messages = NCLangRes4VoTransl.getNCLangRes();
            Object reason = MessageDialog.showInputDlg(null, messages.getStrByID("common", "UC000-0000444"),
                    messages.getStrByID(INVOICE_RESOURCES, "04004050-0002"), null, length);
            if (reason == null) {
                return;
            }

            InvoiceVO[] eligible = filterInvoices(this, selected, true);
            if (eligible.length == 0) {
                showMessage();
                return;
            }
            String freezeReason = ObjectUtil.isEmptyWithTrim(reason) ? null : reason.toString();
            InvoiceVO[] changed = new InvoiceVO[eligible.length];
            for (int i = 0; i < eligible.length; i++) {
                changed[i] = (InvoiceVO) eligible[i].clone();
                if (freezeReason != null) {
                    changed[i].getParentVO().setVfrozenreason(freezeReason);
                }
            }
            final InvoiceVO[] light = new ClientBillToServer<InvoiceVO>().construct(eligible, changed);
            InvoiceVO[] result = (InvoiceVO[]) StatusActionBridge.dispatch(getModel(), "Freeze", event,
                    IInvoiceMaintain.class.getName(), "freezeByLightVOs", new Object[] { light },
                    new StatusActionBridge.NativeCall() {
                        @Override public Object run() throws Exception {
                            return InvoiceServiceUtil.getMaintainSerivce().freezeByLightVOs(light);
                        }
                    });
            // Native server deltas can omit the submitted reason because it is already in the input.
            if (freezeReason != null) {
                for (InvoiceVO bill : eligible) {
                    bill.getParentVO().setVfrozenreason(freezeReason);
                }
            }
            updateInvoices(this, eligible, result, "04004050-0117");
            showMessage();
        }
    }

    public static final class InvoiceUnfreeze extends nc.ui.pu.m25.action.InvoiceUnFrozenAction {
        private static final long serialVersionUID = 1L;

        @Override public void doAction(ActionEvent event) throws Exception {
            InvoiceVO[] selected = getSelectedVOs();
            if (selected == null || selected.length == 0) {
                return;
            }
            InvoiceVO[] eligible = filterInvoices(this, selected, false);
            if (eligible.length == 0) {
                showMessage();
                return;
            }
            final InvoiceVO[] light = new ClientBillToServer<InvoiceVO>().construct(eligible, eligible);
            InvoiceVO[] result = (InvoiceVO[]) StatusActionBridge.dispatch(getModel(), "UnFreeze", event,
                    IInvoiceMaintain.class.getName(), "unFreezeByLightVOs", new Object[] { light },
                    new StatusActionBridge.NativeCall() {
                        @Override public Object run() throws Exception {
                            return InvoiceServiceUtil.getMaintainSerivce().unFreezeByLightVOs(light);
                        }
                    });
            updateInvoices(this, eligible, result, "04004050-0116");
            showMessage();
        }
    }

    private static InvoiceVO[] filterInvoices(InvoiceNormalAction action, InvoiceVO[] selected, boolean freeze) {
        List<InvoiceVO> eligible = new ArrayList<InvoiceVO>();
        StringBuilder skipped = new StringBuilder();
        for (int i = 0; i < selected.length; i++) {
            UFBoolean frozen = selected[i].getParentVO().getBfrozen();
            boolean isFrozen = frozen != null && frozen.booleanValue();
            if (isFrozen == freeze) {
                String message = freeze ? "04004050-0105" : "04004050-0106";
                skipped.append(NCLangRes.getInstance().getStrByID(INVOICE_RESOURCES, message, null,
                        new String[] { String.valueOf(i + 1) }));
            } else {
                eligible.add(selected[i]);
            }
        }
        action.setMessage(skipped.toString());
        return eligible.toArray(new InvoiceVO[eligible.size()]);
    }

    private static void updateInvoices(InvoiceNormalAction action, InvoiceVO[] eligible,
            InvoiceVO[] result, String successMessage) throws Exception {
        new ClientBillCombinServer<InvoiceVO>().combine(eligible, result);
        action.getModel().directlyUpdate(eligible);
        if (StringUtil.isEmptyWithTrim(action.getMessage())) {
            ShowStatusBarMsgUtil.showStatusBarMsg(NCLangRes.getInstance().getStrByID(INVOICE_RESOURCES, successMessage),
                    action.getModel().getContext());
        }
    }
}
