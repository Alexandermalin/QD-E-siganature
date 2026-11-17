package nc.ui.uapbd.qd.esign;

import java.awt.event.ActionEvent;
import java.util.Collections;
import java.util.Map;
import nc.bs.uif2.BusinessExceptionAdapter;
import nc.ui.ic.general.model.ICGenBizModel;
import nc.ui.ic.pub.env.ICUIContext;
import nc.ui.pubapp.uif2app.AppUiState;
import nc.vo.ic.m4455.entity.SapplyBillVO;
import nc.vo.ic.m4455.entity.SapplyBillHeadVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.IAttributeMeta;
import nc.vo.pub.ISuperVO;
import nc.vo.pub.IVOMeta;
import nc.vo.pubapp.pflow.PFReturnObject;
import nc.vo.pubapp.pattern.model.meta.entity.bill.IBillMeta;
import nc.vo.uif2.LoginContext;

public final class SapplySaveCommitHandoffTests {
    private static final IBillMeta META = new IBillMeta() {
        public Map<String, Object> getBusinessAttribute() { return Collections.emptyMap(); }
        public IAttributeMeta[] getChildForeignKeys() { return new IAttributeMeta[0]; }
        public IVOMeta[] getChildren() { return new IVOMeta[0]; }
        public String getComponentName() { return "TEST"; }
        public IVOMeta getParent() { throw new UnsupportedOperationException(); }
        public Class<? extends ISuperVO> getVOClass(IVOMeta meta) { throw new UnsupportedOperationException(); }
        public IVOMeta getVOMeta(Class<? extends ISuperVO> type) { throw new UnsupportedOperationException(); }
    };

    public static void run() throws Exception {
        Model model = new Model();
        ActionEvent event = new ActionEvent(new Object(), 1, "SaveCommit");
        SapplySaveCommitHandoff handoff = new SapplySaveCommitHandoff(event, model, null);
        require(handoff.matches(event, model), "Native marker and exact model/event must match");
        ActionEvent unrelated = new ActionEvent(event.getSource(), 1, "SaveCommit");
        require(!handoff.matches(unrelated, model) && !handoff.matches(event, new Model()),
                "Another event or model must not reuse a save result");

        SapplyBillVO saved = bill("G", "O", "NEW");
        model.selected = new Object[]{saved};
        model.setAppUiState(AppUiState.ADD);
        require(!handoff.readyToSubmit(), "An aborted save keeps the form in edit mode");
        model.setAppUiState(AppUiState.NOT_EDIT);
        requireRejected(handoff, "A stale browse state must not substitute for a save result");

        PFReturnObject[] result = PFReturnObject.createReturnObjs(new SapplyBillVO[]{saved}, null);
        require(SapplySaveCommitHandoff.savedBill(result, false) == null,
                "Native cancellation must discard the result");
        require(SapplySaveCommitHandoff.savedBill(new Object[]{bill("G", "O", "~")}, true) == null,
                "A provisional key is not a persisted save identity");
        require(SapplySaveCommitHandoff.savedBill(new Object[]{saved, saved}, true) == null,
                "A compound entry saves exactly one bill");
        handoff.recordSave(SapplySaveCommitHandoff.savedBill(result, true));
        require(handoff.readyToSubmit(), "Submit the newly returned persisted bill");
        require(SapplySaveCommitHandoff.savedBill(new Object[]{saved}, true) != null,
                "A direct native bill result must also be supported");

        for (Object[] selection : new Object[][]{
                null, {saved, saved}, {bill("G", "O", "OLD")}, {bill("G", "OTHER", "NEW")},
                {bill("OTHER", "O", "NEW")}}) {
            model.selected = selection;
            requireRejected(handoff, "A different or ambiguous selection must not be submitted");
        }
        model.selected = new Object[]{bill("G", "O", "NEW ")};
        require(handoff.readyToSubmit(), "Refresh of the same saved bill preserves its identity");
        model.selected = new Object[0];
        model.current = saved;
        require(handoff.readyToSubmit() && handoff.selectedBills()[0] == saved,
                "Native submit falls back to the current bill when no operation rows are selected");
        try {
            handoff.requireBill(new SapplyBillVO[]{bill("G", "O", "OLD")});
            throw new AssertionError("The final native PF payload must also match the saved identity");
        } catch (BusinessException expected) { }
        handoff.requireBill(new SapplyBillVO[]{saved});

        try {
            new QdSapplySaveAction().actionPerformed(unrelated);
            throw new AssertionError("A synthetic composite save must not bypass the paired action");
        } catch (BusinessExceptionAdapter expected) { }
        try {
            new QdSapplyCommitAction().actionPerformed(unrelated);
            throw new AssertionError("A synthetic composite commit must not bypass the saved-bill check");
        } catch (BusinessExceptionAdapter expected) { }
        System.out.println("PASS SapplySaveCommitHandoffTests (native result, cancellation and selection checks)");
    }

    private static void requireRejected(SapplySaveCommitHandoff handoff, String message) throws Exception {
        try {
            handoff.readyToSubmit();
            throw new AssertionError(message);
        } catch (BusinessException expected) { }
    }

    private static SapplyBillVO bill(final String group, final String org, final String pk) {
        final SapplyBillHeadVO head = new SapplyBillHeadVO() {
            @Override public String getPk_group() { return group; }
            @Override public String getPk_org() { return org; }
            @Override public String getCgeneralhid() { return pk; }
        };
        return new SapplyBillVO() {
            @Override public IBillMeta getMetaData() { return META; }
            @Override public SapplyBillHeadVO getHead() { return head; }
        };
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static final class Model extends ICGenBizModel {
        Object[] selected;
        SapplyBillVO current;

        Model() {
            setIcUIContext(new ICUIContext(new LoginContext()));
            getContext().setNodeCode("40080801");
            getContext().setPk_group("G");
        }

        @Override public Object[] getSelectedOperaDatas() { return selected; }
        @Override public SapplyBillVO getSelectedData() { return current; }
    }
}
