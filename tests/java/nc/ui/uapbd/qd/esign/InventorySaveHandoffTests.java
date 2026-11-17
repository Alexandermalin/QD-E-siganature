package nc.ui.uapbd.qd.esign;

import java.util.Collections;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;

/** Tests one-time authorization handoff for inventory saves. */
public final class InventorySaveHandoffTests {
    public static void run() throws Exception {
        for (String[] route : new String[][] { {"40080602","IC_PURCHASE_IN"}, {"40080802","IC_SALE_OUT"}, {"40080618","IC_TRANS_IN"}, {"40080402","IC_INITIAL_IN"}, {"40081014","IC_LOC_ADJUST"}, {"40080608","IC_GENERAL_IN"}, {"40080604","IC_PRODUCT_IN"}, {"40080606","IC_SUBCONT_IN"}, {"40080808","IC_GENERAL_OUT"}, {"40080804","IC_MATERIAL_OUT"}, {"40080820","IC_TRANS_OUT"}, {"40080602400","IC_OUTSOURCE_IN"}, {"40080604400","IC_PRODUCTION_ORDER"}, {"40080801","IC_SAPPLY"} }) {
            String node = route[0], type = route[1];
            InventorySaveHandoff handoff = new InventorySaveHandoff();
            OperationContext insert = context(node, type, "INSERT", null, "REQ");
            require(handoff.supports(insert) && handoff.bind(insert), "New bill should bind once");
            require(!handoff.bind(insert), "Repeated binding must fail");
            require("REQ".equals(handoff.dispatch("G", "O", null)), "New bill may receive its key from PF");
            require(handoff.wasDispatched("REQ"), "Dispatch must identify the request");
            try { handoff.dispatch("G", "O", null); throw new AssertionError("Repeated dispatch accepted"); }
            catch (BusinessException expected) { }
            handoff.release("OTHER");
            require(handoff.context() != null, "Another request must not clear the authorization");
            handoff.release("REQ");
            require(handoff.context() == null && !handoff.wasDispatched("REQ"), "Release must clear state");

            OperationContext update = context(node, type, "UPDATE", "PK", "REQ2");
            require(handoff.bind(update), "Existing bill should bind");
            try { handoff.dispatch("G", "O", "OTHER"); throw new AssertionError("Different bill accepted"); }
            catch (BusinessException expected) { }
            try { handoff.dispatch("G", "OTHER", "PK"); throw new AssertionError("Different organization accepted"); }
            catch (BusinessException expected) { }
            require("REQ2".equals(handoff.dispatch("G", "O", "PK")), "Signed bill should dispatch");
            handoff.reset();
            require(!handoff.supports(context(node, type, "UPDATE", null, "REQ")), "Update requires a key");
            require(!handoff.supports(context(node, type, "INSERT", "PK", "REQ")), "Insert cannot sign an existing key");
            require(!handoff.supports(context("OTHER", type, "INSERT", null, "REQ")), "Unknown node must fail");
            require(!handoff.supports(context(node, type, "DELETE", "PK", "REQ")), "Delete is not PF save");
            BusinessObjectRef extra = new BusinessObjectRef("item", type, null, null, null, null, null, "OTHER");
            OperationContext targeted = new OperationContext("REQ", "batch", "G", "O", node, "inventory", "Save", "save",
                    "INSERT", null, Collections.singletonList(extra));
            require(!handoff.supports(targeted), "Inventory save must reject a different target organization");
        }
        System.out.println("PASS InventorySaveHandoffTests (one-time scope and dispatch; no NC session)");
    }

    private static OperationContext context(String node, String type, String operation, String pk, String request) {
        return new OperationContext(request, "batch", "G", "O", node, "inventory", "Save", "save", operation,
                null, Collections.singletonList(new BusinessObjectRef("item", type, pk, null, null)));
    }
    private static void require(boolean valid, String message) { if (!valid) throw new AssertionError(message); }
}
