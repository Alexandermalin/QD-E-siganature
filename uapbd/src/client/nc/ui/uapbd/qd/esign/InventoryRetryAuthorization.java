package nc.ui.uapbd.qd.esign;

import nc.itf.uapbd.qd.esign.ISignatureService;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.SignaturePreparation;
import nc.vo.uapbd.qd.esign.SignatureResult;

/** Creates and closes a fresh authorization for a recoverable native inventory retry. */
final class InventoryRetryAuthorization {
    interface Prompt {
        SignatureDialogOutcome show(SignaturePreparation preparation, RequestCancellation cancellation);
    }

    private final ISignatureService service;
    private final Prompt prompt;
    private final InventoryAuthorizationHandoff handoff;
    private OperationContext context;
    private RequestCancellation cancellation;

    InventoryRetryAuthorization(ISignatureService service, Prompt prompt) {
        this(service, prompt, new InventorySaveHandoff());
    }

    InventoryRetryAuthorization(ISignatureService service, Prompt prompt, InventoryAuthorizationHandoff handoff) {
        if (service == null || prompt == null || handoff == null)
            throw new IllegalArgumentException("Retry authorization dependencies required");
        this.service = service;
        this.prompt = prompt;
        this.handoff = handoff;
    }

    /** Returns false when the user cancels the fresh confirmation. */
    boolean prepare(OperationContext draft) throws Exception {
        if (context != null || cancellation != null) throw new IllegalStateException("Retry authorization already prepared");
        SignaturePreparation preparation = service.prepare(draft);
        if (preparation == null || preparation.getContext() == null || preparation.getPolicy() == null) {
            throw new BusinessException("库存重试的签名准备结果不完整。");
        }
        if (!preparation.getPolicy().isControlled() && !preparation.requiresExecutionGuard()) {
            throw new BusinessException("库存策略已变化，请重新发起保存。");
        }
        context = preparation.getContext();
        if (context.getRequestId() == null || context.getRequestId().trim().isEmpty()) {
            context = null;
            throw new BusinessException("库存重试缺少请求编号。");
        }
        cancellation = new RequestCancellation(service, context.getRequestId());
        boolean ready = false;
        Throwable failure = null;
        try {
            if (!sameScope(draft, context)) throw new BusinessException("库存重试的授权范围已变化。");
            if (preparation.getPolicy().isSignatureRequired() || preparation.getPolicy().isReasonRequired()) {
                SignatureDialogOutcome outcome = prompt.show(preparation, cancellation);
                if (outcome == null || !outcome.isConfirmed()) {
                    if (outcome != null && outcome.getFailure() != null) {
                        throw new BusinessException("库存重试签名未完成。", outcome.getFailure());
                    }
                    return false;
                }
                SignatureResult signature = outcome.getSignatureResult();
                if (!context.getRequestId().equals(signature.getRequestId())) {
                    throw new BusinessException("库存重试签名结果与请求不一致。");
                }
                context = withReason(context, outcome.getReason());
            }
            if (!handoff.bind(context)) throw new BusinessException("库存重试授权无法交接。");
            ready = true;
            return true;
        } catch (Exception error) {
            failure = error;
            throw error;
        } catch (Error error) {
            failure = error;
            throw error;
        } finally {
            if (!ready) {
                try { close(null); }
                catch (Exception cleanup) {
                    if (failure != null) failure.addSuppressed(cleanup);
                    else throw cleanup;
                }
            }
        }
    }

    OperationContext context() { return context; }
    String dispatch(String group, String org, String pk) throws BusinessException { return handoff.dispatch(group, org, pk); }
    boolean wasDispatched() { return context != null && handoff.wasDispatched(context.getRequestId()); }

    /** Report a client failure if still pending, then let the server decide whether cancellation applies. */
    void close(Throwable failure) throws Exception {
        if (context == null) return;
        Exception cleanup = null;
        try {
            if (failure != null) service.reportBusinessFailure(context.getRequestId(), "BUSINESS_EXCEPTION");
        } catch (Exception error) { cleanup = error; }
        try { cancellation.cancel(context.getReason()); }
        catch (Exception error) {
            if (cleanup == null) cleanup = error;
            else cleanup.addSuppressed(error);
        } finally {
            handoff.release(context.getRequestId());
            context = null;
            cancellation = null;
        }
        if (cleanup != null) throw cleanup;
    }

    private static boolean sameScope(OperationContext draft, OperationContext result) {
        if (draft == null || !same(draft.getGroupId(), result.getGroupId()) || !same(draft.getOrgId(), result.getOrgId())
                || !same(draft.getNodeCode(), result.getNodeCode()) || !same(draft.getButtonCode(), result.getButtonCode())
                || !same(draft.getOperationType(), result.getOperationType())
                || draft.getObjects().size() != 1 || result.getObjects().size() != 1) return false;
        BusinessObjectRef before = draft.getObjects().get(0), after = result.getObjects().get(0);
        return same(before.getClientObjectId(), after.getClientObjectId())
                && same(before.getObjectType(), after.getObjectType()) && same(before.getObjectPk(), after.getObjectPk())
                && same(before.getParentClientObjectId(), after.getParentClientObjectId())
                && same(before.getDesiredOwnerOrg(), after.getDesiredOwnerOrg())
                && same(before.getPolicyOwnerOrg(), after.getPolicyOwnerOrg())
                && same(before.getTargetOrg(), after.getTargetOrg());
    }

    private static boolean same(String left, String right) { return left == null ? right == null : left.equals(right); }

    private static OperationContext withReason(OperationContext original, String reason) {
        return new OperationContext(original.getRequestId(), original.getBatchId(), original.getGroupId(), original.getOrgId(),
                original.getNodeCode(), original.getNodeName(), original.getButtonCode(), original.getButtonName(),
                original.getOperationType(), reason, original.getObjects(), original.getPolicyProtocol(),
                original.getMaterialProdImpact(), original.getArchiveTreeSaveDraft(), original.getPartnerMainSaveDraft(),
                original.getPartnerBatchDraft());
    }
}
