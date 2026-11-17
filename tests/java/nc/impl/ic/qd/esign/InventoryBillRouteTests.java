package nc.impl.ic.qd.esign;

import nc.bs.uapbd.qd.esign.core.InventoryTransactionRoute;

/** Prevent the inventory adapter's route checks from drifting from the gateway's transaction checks. */
public final class InventoryBillRouteTests {
    public static void run() {
        for (String node : new String[] { "40080602", "40080802", "40080618", "40080402", "40081014", "40080608", "40080604", "40080606", "40080808", "40080804", "40080820", "40080602400", "40080604400", "40080801", "OTHER", null }) {
            for (String button : new String[] { "Save", "Delete", "Sign", "CancelSign", "OTHER", null }) {
                for (String op : new String[] { "INSERT", "UPDATE", "IMPORT", "DELETE", "SIGN", "CANCEL_SIGN", "OTHER", null }) {
                    if (InventoryBillRoute.matches(node, button, op) != InventoryTransactionRoute.matches(node, button, op)) {
                        throw new AssertionError("Inventory gateway and module routes differ: " + node + "/" + button + "/" + op);
                    }
                }
            }
        }
        if (InventoryBillRoute.matches("40080402", "Save", "IMPORT")) {
            throw new AssertionError("Initial import must remain closed until batch authorization and auditing are integrated");
        }
        if (!InventoryBillRoute.matches("40080801", "Save", "INSERT")
                || !InventoryBillRoute.matches("40080801", "Save", "UPDATE")
                || !InventoryBillRoute.matches("40080801", "Delete", "DELETE")
                || InventoryBillRoute.matches("40080801", "Sign", "SIGN")
                || InventoryBillRoute.matches("40080801", "Commit", "COMMIT")
                || !InventoryBillRoute.matches("40080801", "Commit", "UPDATE")
                || !InventoryBillRoute.matches("40080801", "UnCommit", "UPDATE")
                || InventoryBillRoute.matches("40080801", "Approve", "APPROVE")
                || !"IC_SAPPLY".equals(InventoryBillRoute.objectType("40080801"))) {
            throw new AssertionError("Outbound request exposes its candidate save/delete/submit/withdraw routes");
        }
        if (!InventoryBillRoute.matches("40080604400", "Save", "INSERT")
                || !InventoryBillRoute.matches("40080604400", "Sign", "SIGN")
                || !"IC_PRODUCTION_ORDER".equals(InventoryBillRoute.objectType("40080604400"))
                || !"IC_OUTSOURCE_IN".equals(InventoryBillRoute.objectType("40080602400"))) {
            throw new AssertionError("Custom nodes must keep distinct node and object authorization scopes");
        }
        if (!InventoryBillRoute.matches("40081014", "Save", "INSERT")
                || !InventoryBillRoute.matches("40081014", "Save", "UPDATE")
                || !InventoryBillRoute.matches("40081014", "Delete", "DELETE")
                || InventoryBillRoute.matches("40081014", "Sign", "SIGN")
                || InventoryBillRoute.matches("40081014", "CancelSign", "CANCEL_SIGN")) {
            throw new AssertionError("Location adjustment must expose only its approved save/delete routes");
        }
        System.out.println("PASS InventoryBillRouteTests (module and gateway route consistency)");
    }
}
