package nc.ui.pu.qd.esign;

import java.awt.event.ActionEvent;
import java.io.Serializable;
import java.util.Properties;
import nc.bs.framework.common.InvocationInfoProxy;
import nc.bs.framework.common.NCLocator;
import nc.itf.uapbd.qd.esign.IOperationService;
import nc.itf.uapbd.qd.esign.ISignatureService;
import nc.ui.pu.m21.service.OrderDeleteService;
import nc.ui.uapbd.qd.esign.ElectronicSignatureActionInterceptor;
import nc.vo.pu.m21.entity.OrderHeaderVO;
import nc.vo.pu.m21.entity.OrderVO;
import nc.vo.pu.qd.esign.PurchaseOrderOperationCommand;
import nc.vo.pub.BusinessException;
import nc.vo.pubapp.pattern.model.meta.entity.bill.AbstractBillMeta;
import nc.vo.pubapp.pattern.model.meta.entity.bill.IBillMeta;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.OperationResponse;
import nc.vo.uapbd.qd.esign.PolicyDecision;
import nc.vo.uapbd.qd.esign.SignaturePreparation;
import nc.vo.uapbd.qd.esign.SignatureResult;

public final class PurchaseOrderDeleteTest {
    private static IOperationService executor;

    public static final class TestLocator extends NCLocator {
        @Override protected void init(Properties properties) { }

        @Override public Object lookup(String name) {
            check(IOperationService.class.getName().equals(name), "unexpected service lookup: " + name);
            return executor;
        }
    }

    public static void main(String[] args) throws Exception {
        InvocationInfoProxy invocation = InvocationInfoProxy.getInstance();
        String property = NCLocator.LOCATOR_PROVIDER_PROPERTY;
        String previous = invocation.getProperty(property);
        invocation.setProperty(property, TestLocator.class.getName());
        try {
            auditedDeleteKeepsNativeOrderAndContexts();
            offUsesNativeDelete();
            authorizationCannotDispatchAgainstChangedOrder();
            uncertainDispatchHasNoNativeFallback();
            batchBlocksGuardedOrders();
        } finally {
            invocation.setProperty(property, previous);
        }
    }

    private static void auditedDeleteKeepsNativeOrderAndContexts() throws Exception {
        final Fixture fixture = new Fixture(true, false);
        executor = new IOperationService() {
            @Override public OperationResponse execute(String requestId, Serializable payload) {
                fixture.executions++;
                check("request".equals(requestId), "authorized request ID");
                PurchaseOrderOperationCommand command = (PurchaseOrderOperationCommand) payload;
                check(command.getBills().length == 1 && command.getBills()[0] == fixture.order,
                        "original order is the delete command payload");
                check(command.getDeleteContexts().length == 1 && command.getDeleteContexts()[0] != null,
                        "explicit default native delete context");
                return new OperationResponse(requestId, false, null, fixture.draft.getObjects());
            }
        };
        check(fixture.gate.begin(fixture.event), "audit-only authorization");
        check(fixture.gate.dispatch(fixture.order, fixture.nativeDelete) == fixture.order, "native return shape");
        fixture.gate.finish(fixture.event, null);
        check(fixture.executions == 1 && fixture.nativeCalls == 0, "only guarded deletion executed");
    }

    private static void offUsesNativeDelete() throws Exception {
        Fixture fixture = new Fixture(false, false);
        executor = null;
        check(fixture.gate.begin(fixture.event), "policy-off bypass");
        check(fixture.gate.dispatch(fixture.order, fixture.nativeDelete) == fixture.order, "native return shape");
        fixture.gate.finish(fixture.event, null);
        check(fixture.nativeCalls == 1, "original delete service used");
    }

    private static void authorizationCannotDispatchAgainstChangedOrder() throws Exception {
        Fixture fixture = new Fixture(true, false);
        executor = null;
        check(fixture.gate.begin(fixture.event), "audit-only authorization");
        fixture.order.getHVO().setPk_org("other-org");
        try {
            fixture.gate.dispatch(fixture.order, fixture.nativeDelete);
            throw new AssertionError("changed organization reached deletion");
        } catch (BusinessException expected) {
            fixture.gate.finish(fixture.event, expected);
        }
        check(fixture.nativeCalls == 0 && fixture.signature.failures == 1, "unused authorization closed as failure");
    }

    private static void uncertainDispatchHasNoNativeFallback() throws Exception {
        final Fixture fixture = new Fixture(true, false);
        executor = new IOperationService() {
            @Override public OperationResponse execute(String requestId, Serializable payload) {
                fixture.executions++;
                throw new IllegalStateException("connection lost");
            }
        };
        check(fixture.gate.begin(fixture.event), "audit-only authorization");
        try {
            fixture.gate.dispatch(fixture.order, fixture.nativeDelete);
            throw new AssertionError("uncertain result treated as success");
        } catch (BusinessException expected) {
            try {
                fixture.gate.dispatch(fixture.order, fixture.nativeDelete);
                throw new AssertionError("uncertain request was dispatched again");
            } catch (BusinessException duplicate) {
                fixture.gate.finish(fixture.event, expected);
            }
        }
        check(fixture.executions == 1 && fixture.nativeCalls == 0, "uncertain result had no fallback");
    }

    private static void batchBlocksGuardedOrders() throws Exception {
        Fixture off = new Fixture(false, false);
        QdPurchaseOrderDeleteAction.requireBatchBypass(off.draft, off.signature);
        Fixture guarded = new Fixture(false, true);
        try {
            QdPurchaseOrderDeleteAction.requireBatchBypass(guarded.draft, guarded.signature);
            throw new AssertionError("guard-required batch was allowed to use native service");
        } catch (BusinessException expected) {
            check(guarded.signature.cancellations == 1, "unused guard request cancelled");
        }
    }

    private static final class Fixture {
        final OrderVO order = new OrderVO() {
            @Override public IBillMeta getMetaData() {
                return new AbstractBillMeta();
            }
        };
        final OperationContext draft;
        final SignatureService signature;
        final QdPurchaseOrderDeleteAction.DeleteAuthorization gate;
        final ActionEvent event = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "Delete");
        int nativeCalls;
        int executions;
        final OrderDeleteService<OrderVO> nativeDelete = new OrderDeleteService<OrderVO>() {
            @Override public OrderVO operateBill(OrderVO bill) {
                nativeCalls++;
                return bill;
            }
        };

        Fixture(boolean audit, boolean guard) throws Exception {
            OrderHeaderVO head = new OrderHeaderVO() {
                private String org = "org";
                @Override public String getPk_group() { return "group"; }
                @Override public String getPk_org() { return org; }
                @Override public void setPk_org(String newOrg) { org = newOrg; }
                @Override public String getPk_order() { return "order"; }
                @Override public String getVbillcode() { return "PO-001"; }
            };
            order.setHVO(head);
            draft = QdPurchaseOrderDeleteAction.draftFor(order);
            signature = new SignatureService(audit, guard);
            ElectronicSignatureActionInterceptor signing = new ElectronicSignatureActionInterceptor() {
                @Override protected void reportFailure(Throwable failure) {
                    throw new AssertionError("unexpected signing error", failure);
                }
            };
            signing.setSignatureService(signature);
            gate = new QdPurchaseOrderDeleteAction.DeleteAuthorization(draft, signing);
        }
    }

    private static final class SignatureService implements ISignatureService {
        final boolean audit;
        final boolean guard;
        int cancellations;
        int failures;

        SignatureService(boolean audit, boolean guard) {
            this.audit = audit;
            this.guard = guard;
        }

        @Override public SignaturePreparation prepare(OperationContext draft) {
            check("40040400".equals(draft.getNodeCode()) && "Delete".equals(draft.getButtonCode()),
                    "purchase Delete route");
            check("DELETE".equals(draft.getOperationType()) && "group".equals(draft.getGroupId())
                    && "org".equals(draft.getOrgId()), "purchase Delete scope");
            check(draft.getObjects().size() == 1 && "order".equals(draft.getObjects().get(0).getObjectPk())
                    && "PU_PURCHASE_ORDER".equals(draft.getObjects().get(0).getObjectType()), "order object");
            PolicyDecision policy = new PolicyDecision(true, false, false, audit, true, 1L);
            return audit || guard
                    ? new SignaturePreparation(draft.withRequest("request", "batch", "group"), policy, "READY", guard)
                    : new SignaturePreparation(draft, policy, "BYPASS");
        }

        @Override public SignatureResult confirm(String requestId, char[] password, String reason) {
            throw new AssertionError("unexpected password dialog");
        }
        @Override public void cancel(String requestId, String reason) { cancellations++; }
        @Override public void reportBusinessFailure(String requestId, String reason) { failures++; }
        @Override public SignatureResult getResult(String requestId) {
            return new SignatureResult(requestId, "NOT_REQUIRED", false, "READY");
        }
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
