package nc.impl.ic.qd.esign;

import nc.vo.uapbd.qd.esign.InventoryBillScope;
import nc.vo.ic.m45.entity.PurchaseInVO;
import nc.vo.ic.m4c.entity.SaleOutVO;
import nc.vo.ic.m4e.entity.TransInVO;
import nc.vo.ic.m40.entity.InitializtionInVO;
import nc.vo.ic.m4q.entity.LocAdjustVO;
import nc.vo.ic.m4a.entity.GeneralInVO;
import nc.vo.ic.m46.entity.FinProdInVO;
import nc.vo.ic.m47.entity.SubcontInVO;
import nc.vo.ic.m4i.entity.GeneralOutVO;
import nc.vo.ic.m4d.entity.MaterialOutVO;
import nc.vo.ic.m4y.entity.TransOutVO;
import nc.vo.ic.m4455.entity.SapplyBillVO;
import nc.vo.ic.general.define.ICBillHeadVO;

/** Fixed inventory PF routes. */
final class InventoryBillRoute {
    private InventoryBillRoute() { }

    static String objectType(String node) {
        return InventoryBillScope.objectType(node);
    }
    static boolean matches(String node, String button, String operation) {
        return InventoryBillScope.matches(node,button,operation);
    }
    static boolean isBill(String type,Object bill) {
        return InventoryBillScope.PURCHASE_IN.equals(type) && bill instanceof PurchaseInVO
            || InventoryBillScope.SALE_OUT.equals(type) && bill instanceof SaleOutVO
            || InventoryBillScope.TRANS_IN.equals(type) && bill instanceof TransInVO
            || InventoryBillScope.INITIAL_IN.equals(type) && bill instanceof InitializtionInVO
            || InventoryBillScope.LOC_ADJUST.equals(type) && bill instanceof LocAdjustVO
            || InventoryBillScope.GENERAL_IN.equals(type) && bill instanceof GeneralInVO
            || InventoryBillScope.PRODUCT_IN.equals(type) && bill instanceof FinProdInVO
            || InventoryBillScope.SUBCONT_IN.equals(type) && bill instanceof SubcontInVO
            || InventoryBillScope.GENERAL_OUT.equals(type) && bill instanceof GeneralOutVO
            || InventoryBillScope.MATERIAL_OUT.equals(type) && bill instanceof MaterialOutVO
            || InventoryBillScope.TRANS_OUT.equals(type) && bill instanceof TransOutVO
            || InventoryBillScope.OUTSOURCE_IN.equals(type) && bill instanceof PurchaseInVO
            || InventoryBillScope.PRODUCTION_ORDER.equals(type) && bill instanceof FinProdInVO
            || InventoryBillScope.SAPPLY.equals(type) && bill instanceof SapplyBillVO;
    }
    static String nativeBillType(String type) {
        if(InventoryBillScope.PURCHASE_IN.equals(type)) return "45";
        if(InventoryBillScope.SALE_OUT.equals(type)) return "4C";
        if(InventoryBillScope.TRANS_IN.equals(type)) return "4E";
        if(InventoryBillScope.INITIAL_IN.equals(type)) return "40";
        if(InventoryBillScope.LOC_ADJUST.equals(type)) return "4Q";
        if(InventoryBillScope.GENERAL_IN.equals(type)) return "4A";
        if(InventoryBillScope.PRODUCT_IN.equals(type)) return "46";
        if(InventoryBillScope.SUBCONT_IN.equals(type)) return "47";
        if(InventoryBillScope.GENERAL_OUT.equals(type)) return "4I";
        if(InventoryBillScope.MATERIAL_OUT.equals(type)) return "4D";
        // The local type enum and historical site bill-type record use uppercase 4Y.
        if(InventoryBillScope.TRANS_OUT.equals(type)) return "4Y";
        if(InventoryBillScope.OUTSOURCE_IN.equals(type)) return "45";
        if(InventoryBillScope.PRODUCTION_ORDER.equals(type)) return "46";
        if(InventoryBillScope.SAPPLY.equals(type)) return "4455";
        return null;
    }

    /** Until node-specific transaction types are verified, only the base type is authorized. */
    static boolean matchesBillType(String objectType, String billType, ICBillHeadVO head) {
        return matchesBillType(objectType, billType, head, false);
    }

    /** A draft may lack its type before native creation, but persisted bills may not. */
    static boolean matchesNewBillType(String objectType, String billType, ICBillHeadVO head) {
        return matchesBillType(objectType, billType, head, true);
    }

    private static boolean matchesBillType(String objectType, String billType, ICBillHeadVO head, boolean draft) {
        // These nodes authorize only their exact registered subtype, never a direct base-type route.
        if (InventoryBillScope.OUTSOURCE_IN.equals(objectType) || InventoryBillScope.PRODUCTION_ORDER.equals(objectType)) return false;
        String expected = nativeBillType(objectType);
        String submitted = text(billType);
        if (expected == null || submitted == null || head == null
                || !billType.equals(submitted) || !submitted.equals(expected)) return false;
        String actual = text(head.getVtrantypecode());
        return actual == null ? draft : actual.equals(submitted);
    }

    private static String text(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
