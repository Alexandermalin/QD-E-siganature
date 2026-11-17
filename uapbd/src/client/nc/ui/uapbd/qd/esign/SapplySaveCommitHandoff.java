package nc.ui.uapbd.qd.esign;

import java.awt.event.ActionEvent;
import nc.ui.pubapp.uif2app.AppUiState;
import nc.ui.pubapp.uif2app.model.BillManageModel;
import nc.ui.uif2.UIState;
import nc.ui.uif2.components.ITabbedPaneAwareComponent;
import nc.ui.uif2.editor.IEditor;
import nc.vo.ic.general.define.ICBillHeadVO;
import nc.vo.ic.m4455.entity.SapplyBillVO;
import nc.vo.pub.BusinessException;
import nc.vo.pubapp.pflow.PFReturnObject;

/** Identity returned by a successful WRITE and passed to the next SAVE in this button invocation. */
final class SapplySaveCommitHandoff {
    private static final String EVENT_SOURCE = "SaveAndCommitScriptAction";
    private final ActionEvent event;
    private final BillManageModel model;
    private final IEditor commitEditor;
    private SavedBill saved;

    SapplySaveCommitHandoff(ActionEvent event, BillManageModel model, IEditor commitEditor) {
        this.event = event;
        this.model = model;
        this.commitEditor = commitEditor;
        event.setSource(EVENT_SOURCE);
    }

    static boolean isCompositeEvent(ActionEvent event) {
        return event != null && EVENT_SOURCE.equals(event.getSource());
    }

    boolean matches(ActionEvent candidate, BillManageModel candidateModel) {
        return candidate == event && model == candidateModel && isCompositeEvent(candidate);
    }

    void recordSave(SavedBill result) {
        saved = result;
    }

    boolean readyToSubmit() throws BusinessException {
        if (model.getUiState() != UIState.NOT_EDIT) return false;
        selectedBills();
        return true;
    }

    Object[] selectedBills() throws BusinessException {
        if (model.getUiState() != UIState.NOT_EDIT || model.getAppUiState() != AppUiState.NOT_EDIT
                || model.getContext() == null
                || !"40080801".equals(model.getContext().getNodeCode())
                || saved == null || !saved.group.equals(key(model.getContext().getPk_group()))) {
            throw unconfirmed();
        }
        boolean card = commitEditor instanceof ITabbedPaneAwareComponent
                && ((ITabbedPaneAwareComponent) commitEditor).isComponentVisible();
        Object[] selected = card ? null : model.getSelectedOperaDatas();
        if (selected == null || selected.length == 0) selected = new Object[]{model.getSelectedData()};
        requireBill(selected);
        return selected;
    }

    void requireBill(Object[] bills) throws BusinessException {
        if (saved == null || !saved.matches(singleBill(bills))) throw unconfirmed();
    }

    static SavedBill savedBill(Object[] returned, boolean success) {
        if (!success || returned == null || returned.length != 1) return null;
        Object value = returned[0];
        if (value instanceof PFReturnObject) value = singleBill(((PFReturnObject) value).getBills());
        ICBillHeadVO head = value instanceof SapplyBillVO ? ((SapplyBillVO) value).getHead() : null;
        if (head == null) return null;
        String group = key(head.getPk_group()), org = key(head.getPk_org()), pk = key(head.getCgeneralhid());
        return group == null || org == null || pk == null ? null : new SavedBill(group, org, pk);
    }

    private static Object singleBill(Object[] bills) {
        return bills != null && bills.length == 1 ? bills[0] : null;
    }

    private static String key(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() || "~".equals(trimmed) ? null : trimmed;
    }

    static BusinessException unconfirmed() {
        return new BusinessException("无法确认本次保存的出库申请单，请查询单据后再提交。");
    }

    static final class SavedBill {
        private final String group;
        private final String org;
        private final String pk;

        private SavedBill(String group, String org, String pk) {
            this.group = group;
            this.org = org;
            this.pk = pk;
        }

        private boolean matches(Object value) {
            if (!(value instanceof SapplyBillVO)) return false;
            ICBillHeadVO head = ((SapplyBillVO) value).getHead();
            return head != null && group.equals(key(head.getPk_group()))
                    && org.equals(key(head.getPk_org())) && pk.equals(key(head.getCgeneralhid()));
        }
    }
}
