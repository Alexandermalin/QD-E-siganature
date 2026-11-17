package nc.ui.uapbd.qd.esign;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import nc.vo.ic.m4455.entity.SapplyBillHeadVO;
import nc.vo.ic.m4455.entity.SapplyBillVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.IAttributeMeta;
import nc.vo.pub.ISuperVO;
import nc.vo.pub.IVOMeta;
import nc.vo.pubapp.pattern.model.entity.bill.AbstractBill;
import nc.vo.pubapp.pattern.model.meta.entity.bill.IBillMeta;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.SapplyBatchOutcome;
import nc.vo.uapbd.qd.esign.SapplyBatchScope;

/** Offline checks for a single inventory state authorization and one-time dispatch. */
public final class InventoryStateHandoffTests {
    public static void run() throws Exception {
        for (String[] route : new String[][] { {"40080602","IC_PURCHASE_IN"}, {"40080802","IC_SALE_OUT"}, {"40080618","IC_TRANS_IN"}, {"40080402","IC_INITIAL_IN"}, {"40080608","IC_GENERAL_IN"}, {"40080604","IC_PRODUCT_IN"}, {"40080606","IC_SUBCONT_IN"}, {"40080808","IC_GENERAL_OUT"}, {"40080804","IC_MATERIAL_OUT"}, {"40080820","IC_TRANS_OUT"}, {"40080602400","IC_OUTSOURCE_IN"}, {"40080604400","IC_PRODUCTION_ORDER"} }) {
            for (String button : new String[] { "Delete", "Sign", "CancelSign" }) {
                InventoryStateHandoff handoff = new InventoryStateHandoff();
                OperationContext context = context(route[0],route[1],button,"A");
                require(handoff.supports(context) && handoff.bind(context), "Valid state request was rejected");
                try { handoff.dispatch("G", "O", "OTHER"); throw new AssertionError("Different bill was dispatched"); }
                catch (BusinessException expected) { }
                require("R".equals(handoff.dispatch("G", "O", "A")) && handoff.wasDispatched("R"),
                        "Authorized state request was not dispatched");
                try { handoff.dispatch("G", "O", "A"); throw new AssertionError("State request dispatched twice"); }
                catch (BusinessException expected) { }
                handoff.release("OTHER");
                require(handoff.wasDispatched("R"), "Another request released the active authorization");
                handoff.release("R");
                require(!handoff.wasDispatched("R"), "State authorization was not released");
                require(!handoff.supports(context(route[0],route[1],button,null)), "Missing bill key was accepted");
            }
        }
        InventoryStateHandoff handoff = new InventoryStateHandoff();
        require(handoff.supports(context("40081014", "IC_LOC_ADJUST", "Delete", "A")),
                "Approved location deletion must support a handoff");
        require(!handoff.supports(context("40081014", "IC_LOC_ADJUST", "Sign", "A"))
                && !handoff.supports(context("40081014", "IC_LOC_ADJUST", "CancelSign", "A")),
                "Unscoped location sign actions must not support a handoff");
        require(handoff.supports(context("40080801", "IC_SAPPLY", "Delete", "A"))
                && handoff.supports(context("40080801", "IC_SAPPLY", "Commit", "A"))
                && handoff.supports(context("40080801", "IC_SAPPLY", "UnCommit", "A"))
                && !handoff.supports(context("40080801", "IC_SAPPLY", "Sign", "A"))
                && !handoff.supports(context("40080801", "IC_SAPPLY", "CancelSign", "A")),
                "Outbound-request handoff supports its scoped actions");
        OperationContext one = context(true, "Sign", "A");
        BusinessObjectRef second = new BusinessObjectRef("C1", "IC_PURCHASE_IN", "B", null, null);
        OperationContext batch = new OperationContext("R", "B", "G", "O", "40080602", "采购入库", "Sign", "签字",
                "SIGN", null, Arrays.asList(one.getObjects().get(0), second));
        require(!handoff.supports(batch), "Unverified batch state action was accepted");
        OperationContext wrong = new OperationContext("R", "B", "G", "O", "40080802", "销售出库", "Sign", "签字",
                "SIGN", null, one.getObjects());
        require(!handoff.supports(wrong), "Inventory node and bill type were mixed");
        verifySapplyBatch();
        System.out.println("PASS InventoryStateHandoffTests (one-time state scope; no NC session)");
    }

    private static void verifySapplyBatch() throws Exception {
        BusinessObjectRef first = new BusinessObjectRef("first", "IC_SAPPLY", "A", "CODE-A", null);
        BusinessObjectRef second = new BusinessObjectRef("second", "IC_SAPPLY", "B", "CODE-B", null);
        InventoryStateHandoff handoff = new InventoryStateHandoff();
        OperationContext draft = new OperationContext(null, null, "G", "O", "40080801", "出库申请",
                "Commit", "提交", "UPDATE", null, Arrays.asList(first, second));
        OperationContext authorized = new OperationContext("R", "R", "G", "O", "40080801", "出库申请",
                "Commit", "提交", "UPDATE", null, Arrays.asList(first, second), SapplyBatchScope.POLICY_MODE);
        require(handoff.supports(draft) && !handoff.bind(draft) && handoff.bind(authorized),
                "Only a prepared batch can bind for dispatch");
        try {
            handoff.dispatch("G", "O", "A");
            throw new AssertionError("Batch authorized a single ordinary operation");
        } catch (BusinessException expected) { }
        try {
            handoff.dispatchBatch(Collections.singleton("A"));
            throw new AssertionError("Partial selection dispatched");
        } catch (BusinessException expected) { }
        require("R".equals(handoff.dispatchBatch(new HashSet<String>(Arrays.asList("B", "A")))),
                "Dispatch matches the complete selection by primary key");
        try {
            handoff.dispatchBatch(new HashSet<String>(Arrays.asList("A", "B")));
            throw new AssertionError("Same authorization dispatched again");
        } catch (BusinessException expected) { }
        SapplyBatchOutcome result = new SapplyBatchOutcome("R", "PARTIAL", Arrays.asList(
                new SapplyBatchOutcome.Unit(second, "FAILED", "PF_REJECTED"),
                new SapplyBatchOutcome.Unit(first, "SUCCESS", null)));
        require(Collections.singleton("A").equals(SapplyBatchClientResult.committedKeys(authorized, result)),
                "Only the successful bill can refresh the model");
        try {
            SapplyBatchClientResult.committedKeys(authorized, new SapplyBatchOutcome("R", "SUCCESS", Arrays.asList(
                    new SapplyBatchOutcome.Unit(first, "SUCCESS", null), new SapplyBatchOutcome.Unit(first, "SUCCESS", null))));
            throw new AssertionError("Duplicate receipt accepted");
        } catch (BusinessException expected) { }
        SapplyBillVO a = bill("A"), b = bill("B"), c = bill("C");
        AbstractBill[] ordered = SapplyBatchClientResult.originalsInResultOrder(new SapplyBillVO[]{a, b, c},
                Arrays.asList(bill("C"), bill("B")));
        require(ordered != null && ordered.length == 2 && ordered[0] == c && ordered[1] == b,
                "The native model updater must align successful old bills by primary key");
        require(SapplyBatchClientResult.originalsInResultOrder(new SapplyBillVO[]{a, b, c},
                Collections.singletonList(bill("OTHER"))) == null, "A foreign result cannot update the model");
    }

    private static SapplyBillVO bill(final String pk) {
        return new SapplyBillVO() {
            @Override public IBillMeta getMetaData() { return META; }
            @Override public SapplyBillHeadVO getHead() {
                return new SapplyBillHeadVO() { @Override public String getCgeneralhid() { return pk; } };
            }
        };
    }

    private static final IBillMeta META = new IBillMeta() {
        public Map<String,Object> getBusinessAttribute() { return Collections.emptyMap(); }
        public IAttributeMeta[] getChildForeignKeys() { return new IAttributeMeta[0]; }
        public IVOMeta[] getChildren() { return new IVOMeta[0]; }
        public String getComponentName() { return "SAPPLY_BATCH_RESULT_TEST"; }
        public IVOMeta getParent() { throw new UnsupportedOperationException(); }
        public Class<? extends ISuperVO> getVOClass(IVOMeta meta) { throw new UnsupportedOperationException(); }
        public IVOMeta getVOMeta(Class<? extends ISuperVO> type) { throw new UnsupportedOperationException(); }
    };
    private static OperationContext context(boolean purchase, String button, String pk) {
        return context(purchase?"40080602":"40080802",purchase?"IC_PURCHASE_IN":"IC_SALE_OUT",button,pk);
    }
    private static OperationContext context(String node,String type,String button,String pk) {
        String op = "Delete".equals(button) ? "DELETE" : "Sign".equals(button) ? "SIGN"
                : "CancelSign".equals(button) ? "CANCEL_SIGN" : "UPDATE";
        return new OperationContext("R", "B", "G", "O", node, "inventory",
                button, button, op, null, Collections.singletonList(new BusinessObjectRef("C0",type,pk,null,null)));
    }
    private static void require(boolean valid, String message) { if (!valid) throw new AssertionError(message); }
}
