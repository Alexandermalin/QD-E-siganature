package nc.ui.pu.qd.esign;

import java.awt.event.ActionEvent;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Set;
import nc.bs.framework.common.InvocationInfoProxy;
import nc.bs.framework.common.NCLocator;
import nc.bs.uif2.validation.IValidationService;
import nc.bs.uif2.validation.ValidationException;
import nc.itf.uap.pf.IplatFormEntry;
import nc.itf.uapbd.qd.esign.ISignatureService;
import nc.ui.pubapp.pub.common.context.PFlowContext;
import nc.ui.pubapp.uif2app.actions.pflow.ScriptPFlowAction;
import nc.ui.pubapp.uif2app.model.BillManageModel;
import nc.vo.pu.m20.entity.PraybillHeaderVO;
import nc.vo.pu.m20.entity.PraybillVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.IVOMeta;
import nc.vo.pubapp.pattern.exception.ExceptionUtils;
import nc.vo.pubapp.pattern.model.meta.entity.bill.IBillMeta;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.OperationResponse;
import nc.vo.uapbd.qd.esign.PolicyDecision;
import nc.vo.uapbd.qd.esign.SignaturePreparation;
import nc.vo.uapbd.qd.esign.SignatureResult;
import nc.vo.uif2.LoginContext;

public final class ProcurementPfResultTest {
    public static void main(String[] args) throws Exception {
        PraybillVO saved = bill("PRAY-1", "ORG");
        PraybillVO[] nativeResult = { saved };
        OperationContext insert = request("INSERT", null);
        require(nativeResult == ProcurementActionGuard.Kind.PRAYBILL.result(insert,
                response("REQ", false, nativeResult, "PRAY-1")), "Native save array must reach the caller");
        PraybillVO[] sparseResult = { bill("PRAY-1", null) };
        require(sparseResult == ProcurementActionGuard.Kind.PRAYBILL.result(insert,
                response("REQ", false, sparseResult, "PRAY-1")), "Sparse native response is backed by the committed ref");
        OperationContext delete = request("DELETE", "PRAY-1");
        require(ProcurementActionGuard.Kind.PRAYBILL.result(delete,
                response("REQ", false, null, "PRAY-1")) == null, "Null is native DISCARD success");

        reject(insert, response("OTHER", false, nativeResult, "PRAY-1"));
        reject(insert, response("REQ", true, nativeResult, "PRAY-1"));
        reject(insert, response("REQ", false, nativeResult, "OTHER"));
        reject(insert, response("REQ", false, new Object[] { saved }, "PRAY-1"));
        reject(insert, response("REQ", false, new PraybillVO[] { bill("PRAY-1", "OTHER") }, "PRAY-1"));
        reject(delete, response("REQ", false, nativeResult, "PRAY-1"));
        reject(delete, response("REQ", false, null, "OTHER"));
        reject(delete, response("REQ", true, null, "PRAY-1"));
        routeOnlyThePreparedNativeCall();
        batchRequiresPolicyOffForTheExactBill();
        reportsNativeValidationBeforePreparingAnInvalidSave();
    }

    private static void reportsNativeValidationBeforePreparingAnInvalidSave() throws Exception {
        final PraybillVO invalid = bill("PRAY-1", null);
        final ValidationException requiredOrg = new ValidationException();
        final PFlowContext flow = new PFlowContext();
        flow.setActionName("SAVEBASE");
        flow.setBillType("20");
        flow.setBillVos(new PraybillVO[] { invalid });
        final ScriptPFlowAction action = new ScriptPFlowAction(flow) {
            private static final long serialVersionUID = 1L;
            private final ProcurementActionGuard guard =
                    new ProcurementActionGuard(this, ProcurementActionGuard.Kind.PRAYBILL, false);

            @Override public void doAction(ActionEvent event) throws Exception {
                guard.runAction(event, new ProcurementActionGuard.NativeCall() {
                    @Override public void run() throws Exception {
                        guard.runFlow(new ProcurementActionGuard.NativeCall() {
                            @Override public void run() throws Exception { procFlow(flow); }
                        });
                    }
                });
            }

            @Override protected void fillUpContext(PFlowContext prepared) { guard.filled(prepared); }
            @Override protected Object getFullBill(Object value) { return value; }
        };
        LoginContext login = new LoginContext();
        login.setNodeCode("40040200");
        login.setPk_group("GROUP");
        BillManageModel model = new BillManageModel();
        model.setContext(login);
        action.setModel(model);
        action.setFullOldVOs(new PraybillVO[] { invalid });
        action.setValidationService(new IValidationService() {
            @Override public void validate(Object value) throws ValidationException { throw requiredOrg; }
        });

        InvocationInfoProxy invocation = InvocationInfoProxy.getInstance();
        String property = NCLocator.LOCATOR_PROVIDER_PROPERTY;
        String previous = invocation.getProperty(property);
        invocation.setProperty(property, NativeLocator.class.getName());
        try {
            action.doAction(new ActionEvent(action, ActionEvent.ACTION_PERFORMED, "Save"));
            throw new AssertionError("Native validation must reject the invalid save");
        } catch (Exception failure) {
            require(ExceptionUtils.unmarsh(failure) == requiredOrg,
                    "Native required-field error must precede authorization scope checks");
        } finally {
            invocation.setProperty(property, previous);
        }
    }

    private static OperationContext request(String operation, String pk) {
        return new OperationContext("REQ", null, "GROUP", "ORG", "40040200", "请购单维护",
                "DELETE".equals(operation) ? "Delete" : "Save", null, operation, null,
                Collections.singletonList(object(pk)));
    }

    private static OperationResponse response(String requestId, boolean replayed,
            java.io.Serializable result, String pk) {
        return new OperationResponse(requestId, replayed, result, Collections.singletonList(object(pk)));
    }

    private static BusinessObjectRef object(String pk) {
        return new BusinessObjectRef("REF", "PU_PRAYBILL", pk, "CODE", null);
    }

    private static PraybillVO bill(final String pk, final String org) {
        PraybillHeaderVO header = new PraybillHeaderVO() {
            private static final long serialVersionUID = 1L;
            @Override public String getPrimaryKey() { return pk; }
            @Override public Object getAttributeValue(String name) {
                if ("pk_group".equals(name)) return "GROUP";
                if ("pk_org".equals(name)) return org;
                if ("vbillcode".equals(name)) return "CODE";
                throw new AssertionError(name);
            }
            @Override public Set<String> usedAttributeNames() {
                return Collections.singleton("pk_group");
            }
        };
        PraybillVO bill = new LocalPraybill();
        bill.setHVO(header);
        return bill;
    }

    private static void reject(OperationContext request, OperationResponse response) throws Exception {
        try {
            ProcurementActionGuard.Kind.PRAYBILL.result(request, response);
            throw new AssertionError("Mismatched, malformed or replayed PF result must fail closed");
        } catch (BusinessException expected) {
            require(expected.getMessage().contains("REQ"), "Unknown-result message must identify the request");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void routeOnlyThePreparedNativeCall() throws Exception {
        final PraybillVO selected = bill("PRAY-1", "ORG");
        PraybillVO other = bill("PRAY-2", "ORG");
        InvocationInfoProxy invocation = InvocationInfoProxy.getInstance();
        String property = NCLocator.LOCATOR_PROVIDER_PROPERTY;
        String previous = invocation.getProperty(property);
        invocation.setProperty(property, NativeLocator.class.getName());
        try (ProcurementPfLocator.Scope scope = ProcurementPfLocator.open(
                ProcurementActionGuard.Kind.PRAYBILL, new ProcurementPfLocator.Dispatch() {
                    @Override public Object execute(Object[] args, IplatFormEntry nativePf) {
                        require(args[3] == selected, "The authorized dispatch uses the prepared bill");
                        return null;
                    }
                })) {
            IplatFormEntry pf = NCLocator.getInstance().lookup(IplatFormEntry.class);
            scope.expect("DISCARD", "20", selected);
            rejectCall(pf, "DISCARD", "20", other);
            rejectCall(pf, "DISCARD", "OTHER", selected);
            rejectCall(pf, "SAVEBASE", "20", selected);
            require("native".equals(pf.processAction("SAVE", "20", null, other, null, null)),
                    "Other PF activity retains its own native path");
            require(pf.processAction("DISCARD", "20", null, selected, null, null) == null,
                    "The authorized null is returned unchanged");
            rejectCall(pf, "DISCARD", "20", selected);
            scope.expect("SAVEREVISE", "20", selected);
            require(pf.processAction("SAVEREVISE", "20", null, selected, null, null) == null,
                    "Native revision routes through the same PF boundary");
        } finally {
            require(NativeLocator.class.getName().equals(invocation.getProperty(property)),
                    "Closing the PF scope restores the prior locator");
            invocation.setProperty(property, previous);
        }
    }

    private static void rejectCall(IplatFormEntry pf, String action, String type, PraybillVO bill) throws Exception {
        try {
            pf.processAction(action, type, null, bill, null, null);
            throw new AssertionError("An unprepared or repeated write must never reach PF");
        } catch (BusinessException expected) {
            // Rejected before dispatch.
        }
    }

    private static void batchRequiresPolicyOffForTheExactBill() throws Exception {
        OperationContext draft = ProcurementActionGuard.Kind.PRAYBILL.draft("GROUP", "DELETE", bill("PRAY-1", "ORG"));
        PolicyDecision off = new PolicyDecision(true, false, false, false, true, 1L);
        StubSignatures bypass = new StubSignatures(new SignaturePreparation(draft, off, "BYPASS"));
        ProcurementActionGuard.requireBatchBypass(draft, bypass);
        OperationContext another = ProcurementActionGuard.Kind.PRAYBILL.draft("GROUP", "DELETE", bill("PRAY-2", "ORG"));
        rejectBatch(draft, new StubSignatures(new SignaturePreparation(another, off, "BYPASS")));
        StubSignatures guarded = new StubSignatures(new SignaturePreparation(
                draft.withRequest("GUARD", null, "GROUP"), off, "READY", true));
        rejectBatch(draft, guarded);
        require("GUARD".equals(guarded.canceledRequest), "A batch cannot leave its unused guard pending");
    }

    private static void rejectBatch(OperationContext draft, StubSignatures service) throws Exception {
        try {
            ProcurementActionGuard.requireBatchBypass(draft, service);
            throw new AssertionError("Native batch requires policy-off for the confirmed bill");
        } catch (BusinessException expected) {
            // No native batch can follow this check.
        }
    }

    private static final class StubSignatures implements ISignatureService {
        private final SignaturePreparation preparation;
        private String canceledRequest;

        StubSignatures(SignaturePreparation preparation) { this.preparation = preparation; }
        @Override public SignaturePreparation prepare(OperationContext draft) { return preparation; }
        @Override public void cancel(String requestId, String reason) { canceledRequest = requestId; }
        @Override public SignatureResult confirm(String requestId, char[] password, String reason) {
            throw new AssertionError("Batch preflight does not prompt for a signature");
        }
        @Override public void reportBusinessFailure(String requestId, String reason) {
            throw new AssertionError("Batch preflight did not dispatch a write");
        }
        @Override public SignatureResult getResult(String requestId) { throw new AssertionError(requestId); }
    }

    public static final class NativeLocator extends NCLocator {
        @Override protected void init(java.util.Properties properties) { }

        @Override public Object lookup(String name) {
            require(IplatFormEntry.class.getName().equals(name), "PF lookup delegated to the original locator");
            return Proxy.newProxyInstance(IplatFormEntry.class.getClassLoader(), new Class<?>[] { IplatFormEntry.class },
                    new InvocationHandler() {
                        @Override public Object invoke(Object proxy, Method method, Object[] args) {
                            return "native";
                        }
                    });
        }
    }

    private static final class LocalPraybill extends PraybillVO {
        private static final long serialVersionUID = 1L;
        @Override public IBillMeta getMetaData() {
            return (IBillMeta) Proxy.newProxyInstance(IBillMeta.class.getClassLoader(),
                    new Class<?>[] { IBillMeta.class }, new InvocationHandler() {
                        @Override public Object invoke(Object proxy, Method method, Object[] args) {
                            if ("getChildren".equals(method.getName())) return new IVOMeta[0];
                            throw new AssertionError(method.getName());
                        }
                    });
        }
    }
}
