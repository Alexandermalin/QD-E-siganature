package nc.ui.uapbd.qd.esign;

import java.awt.Container;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Collections;
import java.util.Map;
import javax.swing.Action;
import nc.itf.pubapp.pub.exception.IResumeException;
import nc.itf.uapbd.qd.esign.ISignatureService;
import nc.bs.uif2.BusinessExceptionAdapter;
import nc.ui.ic.general.action.GeneralDeleteAction;
import nc.ui.ic.m4c.action.SaleOutDeleteAction;
import nc.ui.ic.m4q.action.LocAdjDeleteAction;
import nc.ui.ic.m4q.model.LocAdjustGenBizModel;
import nc.ui.ic.pub.env.ICUIContext;
import nc.ui.pubapp.pub.common.context.PFlowContext;
import nc.ui.pubapp.uif2app.actions.pflow.AbstractScriptExcAction;
import nc.ui.pubapp.uif2app.model.BillManageModel;
import nc.ui.uif2.actions.ActionInterceptor;
import nc.vo.ic.m4q.entity.LocAdjustVO;
import nc.vo.ic.m4q.entity.LocAdjustHeadVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.IAttributeMeta;
import nc.vo.pub.ISuperVO;
import nc.vo.pub.IVOMeta;
import nc.vo.pubapp.pattern.model.meta.entity.bill.IBillMeta;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.PolicyDecision;
import nc.vo.uapbd.qd.esign.SignaturePreparation;
import nc.vo.uapbd.qd.esign.SignatureResult;
import nc.vo.uif2.LoginContext;

/** Checks native confirmation, authorization and original interceptor ordering without showing UI. */
public final class InventoryDeleteConfirmationTests {
    private static final IBillMeta META = new IBillMeta() {
        public Map<String,Object> getBusinessAttribute() { return Collections.emptyMap(); }
        public IAttributeMeta[] getChildForeignKeys() { return new IAttributeMeta[0]; }
        public IVOMeta[] getChildren() { return new IVOMeta[0]; }
        public String getComponentName() { return "LOC_ADJUST_CONFIRM_TEST"; }
        public IVOMeta getParent() { throw new UnsupportedOperationException(); }
        public Class<? extends ISuperVO> getVOClass(IVOMeta meta) { throw new UnsupportedOperationException(); }
        public IVOMeta getVOMeta(Class<? extends ISuperVO> type) { throw new UnsupportedOperationException(); }
    };
    public static void run() throws Exception {
        InventoryPfPreparation.verifyNativeDeleteContract(GeneralDeleteAction.class);
        InventoryPfPreparation.verifyNativeDeleteContract(SaleOutDeleteAction.class);
        InventoryPfPreparation.verifyNativeDeleteContract(LocAdjDeleteAction.class);
        Probe cancelled = new Probe(); cancelled.confirm = false; cancelled.actionPerformed(cancelled.event);
        cancelled.expect("original-before", "confirm", "original-success");
        require(cancelled.signers == 0 && cancelled.nativeCalls == 0, "Cancelling confirmation must not authenticate or dispatch");
        require(cancelled.getInterceptor() == cancelled.original, "Original interceptor must remain installed");

        Probe rejected = new Probe(); rejected.acceptSignature = false; rejected.actionPerformed(rejected.event);
        rejected.expect("original-before", "confirm", "permission", "sign-before", "original-success");
        require(rejected.signers == 1 && rejected.nativeCalls == 0, "Rejected signature must not dispatch");
        require(rejected.getInterceptor() == rejected.original, "Rejected signature must restore the original chain");

        Probe success = new Probe(); success.actionPerformed(success.event);
        success.expect("original-before", "confirm", "permission", "sign-before", "native", "sign-success", "original-success");
        require(success.nativeCalls == 1 && success.getInterceptor() == success.original,
                "Confirmed deletion must execute once and restore the original chain");

        Probe failure = new Probe(); failure.failNative = true; failure.actionPerformed(failure.event);
        failure.expect("original-before", "confirm", "permission", "sign-before", "native", "sign-failed", "original-failed");
        require(failure.original.error == failure.nativeError && failure.getInterceptor() == failure.original,
                "Native failure must reach the original interceptor unchanged");

        Probe shortCircuit = new Probe(); shortCircuit.original.accept = false; shortCircuit.actionPerformed(shortCircuit.event);
        shortCircuit.expect("original-before");
        require(shortCircuit.signers == 0 && shortCircuit.nativeCalls == 0,
                "A native interceptor rejection must stop before confirmation and signing");

        Probe beforeReentry = new Probe(); beforeReentry.reenterBefore = true; beforeReentry.confirm = false;
        beforeReentry.actionPerformed(beforeReentry.event);
        beforeReentry.expect("original-before", "before-reentry-rejected", "confirm", "original-success");
        require(beforeReentry.nativeCalls == 0, "The original before callback cannot dispatch a nested delete");

        Probe duplicate = new Probe();
        nc.ui.pubapp.uif2app.actions.interceptor.CompositeActionInterceptor chain =
                new nc.ui.pubapp.uif2app.actions.interceptor.CompositeActionInterceptor();
        chain.addInterceptor(duplicate.original); chain.addInterceptor(new ElectronicSignatureActionInterceptor());
        duplicate.setInterceptor(chain);
        try { duplicate.actionPerformed(duplicate.event); throw new AssertionError("Duplicate signing chain was accepted"); }
        catch (BusinessExceptionAdapter expected) { }
        require(duplicate.events.isEmpty() && duplicate.signers == 0, "Duplicate signing must fail before the original chain");

        Probe changedChain = new Probe(); changedChain.installSigningDuringBefore = true;
        changedChain.actionPerformed(changedChain.event);
        changedChain.expect("original-before", "changed-chain-failed");
        require(changedChain.nativeCalls == 0 && changedChain.signers == 0,
                "A signing interceptor inserted during before must be rejected before confirmation");

        Probe reentrant = new Probe(); reentrant.reenter = true; reentrant.confirm = false;
        reentrant.actionPerformed(reentrant.event);
        reentrant.expect("original-before", "confirm", "reentry-rejected", "original-success");
        require(reentrant.nativeCalls == 0 && reentrant.signers == 0, "Reentry must not start another delete");
        Probe repeated = new Probe(); repeated.confirm = false; repeated.repeatAfterSuccess = true;
        repeated.actionPerformed(repeated.event);
        repeated.expect("original-before", "confirm", "original-success", "repeat-rejected");
        require(repeated.signers == 0 && repeated.nativeCalls == 0, "One native action must not consume confirmation twice");
        try { reentrant.doAction(reentrant.event); throw new AssertionError("Direct delete bypassed the action lifecycle"); }
        catch (BusinessException expected) { }
        try { InventoryPfPreparation.verifyNativeDeleteContract(String.class); throw new AssertionError("Unknown native delete class accepted"); }
        catch (BusinessException expected) { }
        for (boolean sign : new boolean[] { false, true }) for (boolean audit : new boolean[] { false, true }) {
            Probe policy = new Probe(); policy.realSigning = true; policy.signRequired = sign; policy.auditEnabled = audit;
            policy.actionPerformed(policy.event);
            List<String> expected = new ArrayList<String>(Arrays.asList("original-before", "confirm", "permission", "prepare"));
            if (sign) expected.add("dialog");
            expected.add("native");
            if (sign || audit) expected.add("dispatch");
            expected.add("original-success");
            require(policy.events.equals(expected), "Unexpected policy combination order: " + policy.events);
            require(policy.service.prepares == 1 && policy.service.failures == 0 && policy.service.cancels == 0
                    && policy.nativeCalls == 1, "Policy combination must finish without a false failure");

            Probe denied = new Probe(); denied.realSigning = true; denied.signRequired = sign; denied.auditEnabled = audit;
            denied.permission = false; denied.actionPerformed(denied.event);
            denied.expect("original-before", "confirm", "permission", "permission-denied", "original-success");
            require(denied.signers == 0 && denied.service.prepares == 0 && denied.nativeCalls == 0,
                    "Initial permission denial must not prepare a request or invoke the native delete");

            Probe revoked = new Probe(); revoked.realSigning = true; revoked.signRequired = sign; revoked.auditEnabled = audit;
            revoked.recheckPermission = false; revoked.actionPerformed(revoked.event);
            List<String> deniedAfterAuthorization = new ArrayList<String>(Arrays.asList("original-before", "confirm", "permission", "prepare"));
            if (sign) deniedAfterAuthorization.add("dialog");
            deniedAfterAuthorization.add("native"); deniedAfterAuthorization.add("native-permission-denied");
            if (sign || audit) deniedAfterAuthorization.add("failure");
            deniedAfterAuthorization.add("original-success");
            require(revoked.events.equals(deniedAfterAuthorization), "Unexpected permission recheck order: " + revoked.events);
            require(revoked.service.failures == (sign || audit ? 1 : 0) && revoked.service.cancels == 0
                    && revoked.service.prepares == 1 && revoked.nativeCalls == 1 && revoked.getInterceptor() == revoked.original,
                    "Revocation must finish the authorization without dispatch or a false missing-dispatch error");
            require(sign || audit ? "BUSINESS_EXCEPTION".equals(revoked.service.failureCode) : revoked.service.failureCode == null,
                    "Permission revocation must not be classified as a missing dispatch");
        }
        Probe permissionError = new Probe(); permissionError.failPermission = true;
        permissionError.actionPerformed(permissionError.event);
        permissionError.expect("original-before", "confirm", "permission", "original-failed");
        require(permissionError.original.error == permissionError.permissionError && permissionError.signers == 0,
                "A native permission-check exception must be preserved before signing");
        Probe missingRecheck = new Probe(); missingRecheck.realSigning = true; missingRecheck.auditEnabled = true;
        missingRecheck.omitRecheck = true; missingRecheck.actionPerformed(missingRecheck.event);
        missingRecheck.expect("original-before", "confirm", "permission", "prepare", "native", "failure", "original-failed");
        require(missingRecheck.original.error instanceof BusinessException && missingRecheck.service.failures == 1,
                "Missing native permission recheck must fail closed");
        Probe noRecheckReturn = new Probe(); noRecheckReturn.realSigning = true; noRecheckReturn.auditEnabled = true;
        noRecheckReturn.omitRecheck = true; noRecheckReturn.returnWithoutRecheck = true;
        noRecheckReturn.actionPerformed(noRecheckReturn.event);
        noRecheckReturn.expect("original-before", "confirm", "permission", "prepare", "native", "failure", "original-failed");
        require(noRecheckReturn.original.error instanceof BusinessException && noRecheckReturn.service.failures == 1,
                "A native return without permission recheck must fail closed");
        Probe duplicateRecheck = new Probe(); duplicateRecheck.realSigning = true; duplicateRecheck.auditEnabled = true;
        duplicateRecheck.duplicateRecheck = true; duplicateRecheck.actionPerformed(duplicateRecheck.event);
        duplicateRecheck.expect("original-before", "confirm", "permission", "prepare", "native", "failure", "original-failed");
        require(duplicateRecheck.service.failures == 1, "A repeated native permission observation must fail closed");
        try { missingRecheck.coordinator.processFlow(null); throw new AssertionError("Delete without recheck reached the flow"); }
        catch (BusinessException expected) { }
        Probe realCancel = new Probe(); realCancel.realSigning = true; realCancel.signRequired = true;
        realCancel.cancelSignature = true; realCancel.actionPerformed(realCancel.event);
        realCancel.expect("original-before", "confirm", "permission", "prepare", "dialog", "cancel", "original-success");
        require(realCancel.nativeCalls == 0 && realCancel.service.cancels == 1 && realCancel.service.failures == 0,
                "Cancelling the real signing lifecycle must cancel once without dispatch");
        System.out.println("PASS InventoryDeleteConfirmationTests (confirmation before signing and original callbacks; no NC session)");
    }

    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }

    private static final class Probe extends LocAdjDeleteAction {
        private static final long serialVersionUID = 1L;
        final List<String> events = new ArrayList<String>();
        final RecordingInterceptor original = new RecordingInterceptor();
        final ActionEvent event = new ActionEvent(this, 1, "delete");
        final BusinessException nativeError = new BusinessException("fixture native failure");
        final RuntimeException permissionError = new IllegalStateException("fixture permission failure");
        final LocAdjustHeadVO head = new LocAdjustHeadVO() {
            public String getCgeneralhid() { return "A"; }
            public String getPk_group() { return "G"; }
            public String getPk_org() { return "O"; }
            public String getVbillcode() { return "CODE"; }
        };
        final LocAdjustVO bill = new LocAdjustVO() {
            public IBillMeta getMetaData() { return META; }
            public LocAdjustHeadVO getHead() { return head; }
        };
        final LocAdjustGenBizModel model = new LocAdjustGenBizModel() {
            public Object[] getSelectedOperaDatas() { return new Object[] { bill }; }
        };
        final FakeService service = new FakeService();
        final InventoryStateCoordinator coordinator;
        boolean confirm = true, acceptSignature = true, failNative, reenter, reenterBefore, repeatAfterSuccess;
        boolean realSigning, signRequired, auditEnabled, cancelSignature, installSigningDuringBefore;
        boolean permission = true, recheckPermission = true, failPermission, omitRecheck, duplicateRecheck, returnWithoutRecheck;
        int signers, nativeCalls;

        Probe() {
            model.setIcUIContext(new ICUIContext(new LoginContext()));
            model.getContext().setNodeCode("40081014"); model.getContext().setPk_group("G");
            setModel(model); setInterceptor(original);
            coordinator = new InventoryStateCoordinator(new InventoryStateCoordinator.Host() {
                public AbstractScriptExcAction action() { return Probe.this; }
                public BillManageModel model() { return model; }
                public void performNative(ActionEvent e) { Probe.super.actionPerformed(e); }
                public void fillNativeContext(PFlowContext flow) { throw new AssertionError("Fixture does not dispatch PF"); }
                public boolean isComposite() { return false; }
                public boolean resumeNative(IResumeException error) { return false; }
            }, "40081014", "货位调整", "IC_LOC_ADJUST", LocAdjustVO.class, "Delete", "删除", "DELETE", "DELETE",
                new InventoryStateCoordinator.DeleteConfirmation() {
                    public boolean confirm(Container parent) {
                        events.add("confirm");
                        if (reenter) {
                            try { doAction(event); throw new AssertionError("Reentrant delete was accepted"); }
                            catch (BusinessException expected) { events.add("reentry-rejected"); }
                            catch (Exception other) { throw new AssertionError(other); }
                        }
                        return confirm;
                    }
                }, new InventoryStateCoordinator.SigningFactory() {
                    public ElectronicSignatureActionInterceptor create() {
                        signers++; return realSigning ? new RealSigning() : new FakeSigning();
                    }
                });
        }
        @Override public void actionPerformed(ActionEvent event) { coordinator.perform(event); }
        @Override public void doAction(final ActionEvent event) throws Exception {
            coordinator.deleteAfterConfirmation(event, new InventoryStateCoordinator.ConfirmedDelete() {
                public boolean hasDataPermission() {
                    events.add("permission");
                    if (failPermission) throw permissionError;
                    return permission;
                }
                public void showPermissionDenied() { events.add("permission-denied"); }
                public void run() throws Exception {
                    events.add("native"); nativeCalls++;
                    if (!omitRecheck) coordinator.observeDeletePermission(recheckPermission);
                    if (duplicateRecheck) coordinator.observeDeletePermission(recheckPermission);
                    if (!recheckPermission) { events.add("native-permission-denied"); return; }
                    if (omitRecheck) {
                        if (returnWithoutRecheck) return;
                        coordinator.processFlow(null);
                    }
                    if (failNative) throw nativeError;
                    if (realSigning && (signRequired || auditEnabled)) {
                        require("R".equals(coordinator.dispatchCurrent("G", "O", "A")), "Authorization was not dispatched");
                        events.add("dispatch");
                    }
                }
            });
        }
        void expect(String... expected) {
            require(events.equals(Arrays.asList(expected)), "Unexpected callback sequence: " + events);
        }
        private final class FakeSigning extends ElectronicSignatureActionInterceptor {
            @Override public boolean beforeDoAction(Action action, ActionEvent event) {
                events.add("sign-before"); return acceptSignature;
            }
            @Override public void afterDoActionSuccessed(Action action, ActionEvent event) { events.add("sign-success"); }
            @Override public boolean afterDoActionFailed(Action action, ActionEvent event, Throwable error) {
                require(error == nativeError, "Signing must observe the original native failure");
                events.add("sign-failed"); return true;
            }
        }
        private final class RealSigning extends ElectronicSignatureActionInterceptor {
            RealSigning() { setSignatureService(service); }
            @Override protected SignatureDialogOutcome showSignatureDialog(SignaturePreparation prepared,
                    RequestCancellation cancellation) {
                events.add("dialog");
                if (cancelSignature) {
                    try { cancellation.cancel(null); }
                    catch (Exception error) { return new SignatureDialogOutcome(null, null, error); }
                    return new SignatureDialogOutcome(null, null, null);
                }
                return new SignatureDialogOutcome(new SignatureResult("R", "SUCCESS", false, "READY"), null, null);
            }
            @Override protected void reportFailure(Throwable error) { throw new AssertionError("Unexpected signing failure", error); }
        }
        private final class FakeService implements ISignatureService {
            int prepares, cancels, failures;
            String failureCode;
            public SignaturePreparation prepare(OperationContext draft) {
                events.add("prepare"); prepares++;
                PolicyDecision policy = new PolicyDecision(true, signRequired, false, auditEnabled, true, 1);
                return new SignaturePreparation(policy.isControlled() ? draft.withRequest("R", "B", "G") : draft,
                        policy, policy.isControlled() ? "PENDING" : "BYPASS");
            }
            public SignatureResult confirm(String id, char[] password, String reason) { throw new AssertionError("No password UI in fixture"); }
            public void cancel(String id, String reason) { events.add("cancel"); cancels++; }
            public void reportBusinessFailure(String id, String code) { events.add("failure"); failures++; failureCode = code; }
            public SignatureResult getResult(String id) { return new SignatureResult(id, "SUCCESS", false, "READY"); }
        }
        private final class RecordingInterceptor implements ActionInterceptor {
            boolean accept = true; Throwable error;
            public boolean beforeDoAction(Action action, ActionEvent event) {
                events.add("original-before");
                if (reenterBefore) {
                    try { doAction(event); throw new AssertionError("Before-callback reentry was accepted"); }
                    catch (BusinessException expected) { events.add("before-reentry-rejected"); }
                    catch (Exception other) { throw new AssertionError(other); }
                }
                if (installSigningDuringBefore) {
                    setInterceptor(new ElectronicSignatureActionInterceptor() {
                        @Override public boolean afterDoActionFailed(Action action, ActionEvent event, Throwable error) {
                            events.add("changed-chain-failed"); return false;
                        }
                    });
                }
                return accept;
            }
            public void afterDoActionSuccessed(Action action, ActionEvent event) {
                events.add("original-success");
                if (repeatAfterSuccess) {
                    try { doAction(event); throw new AssertionError("Repeated delete was accepted"); }
                    catch (BusinessException expected) { events.add("repeat-rejected"); }
                    catch (Exception other) { throw new AssertionError(other); }
                }
            }
            public boolean afterDoActionFailed(Action action, ActionEvent event, Throwable error) {
                this.error = error; events.add("original-failed"); return false;
            }
        }
    }
}
