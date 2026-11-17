package nc.ui.uapbd.qd.esign;

import java.util.Collections;
import nc.itf.uapbd.qd.esign.ISignatureService;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.PolicyDecision;
import nc.vo.uapbd.qd.esign.SignaturePreparation;
import nc.vo.uapbd.qd.esign.SignatureResult;

/** Tests fresh retry authorization and cleanup without a server or a modal window. */
public final class InventoryRetryAuthorizationTests {
    public static void run() throws Exception {
        OperationContext draft = draft();
        Fake service = new Fake();
        Prompt prompt = new Prompt();
        InventoryRetryAuthorization retry = new InventoryRetryAuthorization(service, prompt);
        require(retry.prepare(draft) && prompt.calls == 1, "A signed retry must request a fresh confirmation");
        require("why".equals(retry.context().getReason()), "Confirmed reason must accompany the retry");
        require("R2".equals(retry.dispatch("G", "O", "A")) && retry.wasDispatched(), "Retry must dispatch once");
        try { retry.dispatch("G", "O", "A"); throw new AssertionError("Retry dispatched twice"); }
        catch (BusinessException expected) { }
        retry.close(null);
        require(service.cancels == 1 && service.failures == 0 && retry.context() == null,
                "Completed retry must release its request");

        service = new Fake(); prompt = new Prompt(); prompt.cancel = true;
        retry = new InventoryRetryAuthorization(service, prompt);
        require(!retry.prepare(draft) && service.cancels == 1 && retry.context() == null,
                "Canceled retry must close the pending request");

        service = new Fake(); prompt = new Prompt(); prompt.wrongRequest = true;
        retry = new InventoryRetryAuthorization(service, prompt);
        try { retry.prepare(draft); throw new AssertionError("A signature for another request was accepted"); }
        catch (BusinessException expected) { }
        require(service.cancels == 1, "Mismatched signature request must be closed");

        service = new Fake(); service.wrongGroup = true;
        retry = new InventoryRetryAuthorization(service, new Prompt());
        try { retry.prepare(draft); throw new AssertionError("Changed authorization scope was accepted"); }
        catch (BusinessException expected) { }
        require(service.cancels == 1, "Changed scope must close the new request");

        service = new Fake(); service.auditOnly = true; prompt = new Prompt();
        retry = new InventoryRetryAuthorization(service, prompt);
        require(retry.prepare(draft) && prompt.calls == 0, "Audit-only retry should not request a password");
        retry.dispatch("G", "O", "A");
        retry.close(new BusinessException("business failed"));
        require(service.failures == 1 && service.cancels == 1, "Failed retry must report and release the request");

        service = new Fake(); prompt = new Prompt();
        OperationContext stateDraft = new OperationContext(null, null, "G", "O", "40080602", "采购入库",
                "Sign", "签字", "SIGN", null,
                Collections.singletonList(new BusinessObjectRef("item", "IC_PURCHASE_IN", "A", "CODE", null)));
        retry = new InventoryRetryAuthorization(service, prompt, new InventoryStateHandoff());
        require(retry.prepare(stateDraft) && "R2".equals(retry.dispatch("G", "O", "A")),
                "State retry must obtain and dispatch its own authorization");
        retry.close(null);
        require(service.cancels == 1, "State retry must release its request");

        service = new Fake(); service.bypass = true;
        retry = new InventoryRetryAuthorization(service, new Prompt());
        try { retry.prepare(draft); throw new AssertionError("Changed policy was accepted mid-retry"); }
        catch (BusinessException expected) { }

        service = new Fake(); service.cancelFails = true;
        retry = new InventoryRetryAuthorization(service, new Prompt());
        require(retry.prepare(draft), "Retry should prepare before cleanup failure");
        try { retry.close(null); throw new AssertionError("Cleanup failure was hidden"); }
        catch (BusinessException expected) { }
        require(retry.context() == null, "Cleanup failure must still clear local state");
        System.out.println("PASS InventoryRetryAuthorizationTests (fresh authorization and cleanup; no NC session)");
    }

    private static OperationContext draft() {
        return new OperationContext(null, null, "G", "O", "40080602", "采购入库", "Save", "保存", "UPDATE", null,
                Collections.singletonList(new BusinessObjectRef("item", "IC_PURCHASE_IN", "A", "CODE", null)));
    }
    private static void require(boolean valid, String message) { if (!valid) throw new AssertionError(message); }
    private static final class Prompt implements InventoryRetryAuthorization.Prompt {
        int calls;
        boolean cancel, wrongRequest;
        public SignatureDialogOutcome show(SignaturePreparation preparation, RequestCancellation cancellation) {
            calls++;
            if (cancel) return new SignatureDialogOutcome(null, null, null);
            String request = wrongRequest ? "OTHER" : preparation.getContext().getRequestId();
            return new SignatureDialogOutcome(new SignatureResult(request, "SUCCESS", false, "READY"), "why", null);
        }
    }
    private static final class Fake implements ISignatureService {
        int cancels, failures;
        boolean wrongGroup, auditOnly, bypass, cancelFails;
        public SignaturePreparation prepare(OperationContext draft) {
            OperationContext context = draft.withRequest("R2", "R2", wrongGroup ? "OTHER" : "G");
            PolicyDecision policy = new PolicyDecision(!bypass, !auditOnly, false, true, true, 1);
            return new SignaturePreparation(context, policy, "READY");
        }
        public SignatureResult confirm(String id, char[] password, String reason) { throw new AssertionError("Prompt fixture handles confirmation"); }
        public void cancel(String id, String reason) throws BusinessException {
            cancels++;
            if (cancelFails) throw new BusinessException("cancel failed");
        }
        public void reportBusinessFailure(String id, String code) { failures++; }
        public SignatureResult getResult(String id) { throw new AssertionError("Not used"); }
    }
}
