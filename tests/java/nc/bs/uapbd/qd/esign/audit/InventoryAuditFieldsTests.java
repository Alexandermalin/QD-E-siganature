package nc.bs.uapbd.qd.esign.audit;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import nc.bs.dao.DAOException;

/** Checks the inventory physical-column allowlists and schema drift detection. */
public final class InventoryAuditFieldsTests {
    public static void run() throws Exception {
        for (String table : Arrays.asList("ic_purchasein_h", "ic_purchasein_b", "ic_purchasein_l",
                "ic_saleout_h", "ic_saleout_b", "ic_saleout_l", "ic_transin_h", "ic_transin_b", "ic_transin_l",
                "ic_openbal_h", "ic_openbal_b", "ic_openbal_l", "ic_locadjust_h", "ic_locadjust_b", "ic_locadjust_l",
                "ic_generalin_h", "ic_generalin_b", "ic_generalin_l", "ic_finprodin_h", "ic_finprodin_b", "ic_finprodin_l",
                "ic_subcontin_h", "ic_subcontin_b", "ic_subcontin_l", "ic_generalout_h", "ic_generalout_b", "ic_generalout_l",
                "ic_material_h", "ic_material_b", "ic_material_l", "ic_transout_h", "ic_transout_b", "ic_transout_l",
                "ic_sapply_h", "ic_sapply_b")) {
            Set<String> fields = InventoryAuditFields.forTable(table);
            require(fields.contains("cgeneralhid") && fields.contains("dr") && fields.contains("ts"),
                    "Bill identity and lifecycle columns must be available");
            if (table.endsWith("_b")) require(fields.contains("cgeneralbid"), "Body identity must be available");
            if (table.endsWith("_l")) {
                require(fields.contains("cgeneralbid") && fields.contains("cgenerallid"),
                        "Location identity and parent must be available");
            }
            for (String field : fields) require(!field.isEmpty() && !SnapshotSupport.sensitive(field),
                    "Sensitive or empty inventory audit field");
            Map<String, String> available = new LinkedHashMap<String, String>();
            available.put("cgeneralhid", "bill");
            available.put("dr", "deleted");
            Map<String, String> selected = PhysicalSnapshotReader.restrictColumns(table, available, fields);
            require(selected.equals(available), "Known physical columns must be retained");
            available.put("unreviewed_custom_field", "custom");
            try {
                PhysicalSnapshotReader.restrictColumns(table, available, fields);
                throw new AssertionError("Unreviewed inventory schema column was accepted");
            } catch (DAOException expected) { }
            require(PhysicalSnapshotReader.restrictColumns(table, available, null) == available,
                    "Existing non-inventory readers must retain their projection behavior");
        }
        require(InventoryAuditFields.forTable("ic_purchasein_h").contains("bc_03")
                && InventoryAuditFields.forTable("ic_saleout_b").contains("norigtax"),
                "Known QDNC extension columns must remain available");
        require(InventoryAuditFields.forTable("ic_locadjust_b").containsAll(Arrays.asList("clocationid", "clocationinid", "pk_batchcode")),
                "Location adjustment must preserve both locations and the batch reference");
        require(InventoryAuditFields.forTable("ic_sapply_b").contains("pk_batchcode"),
                "Outbound request body must retain its batch reference");
        Set<String> outboundExtension=InventoryAuditFields.forTable("ic_sapply_e");
        require(outboundExtension.size()==6 && outboundExtension.containsAll(Arrays.asList("cgeneralbid","dr","ts",
                "noutboundnum","noutboundastnum","noutboundrsnum")),"Outbound dynamic quantity fields must be captured");
        try { InventoryAuditFields.forTable("ic_sapply_l"); throw new AssertionError("Unsupported outbound-request location table accepted"); }
        catch (IllegalArgumentException expected) { }
        Set<String> batch=InventoryAuditFields.forTable("scm_batchcode");
        require(batch.containsAll(Arrays.asList("pk_batchcode","pk_group","vbatchcode","cmaterialoid")),
                "Batch identity, group, code and material must be available");
        for(String field:batch) require(!field.isEmpty() && !SnapshotSupport.sensitive(field),"Unsafe batch reference field");
        Map<String,String> batchColumns=new LinkedHashMap<String,String>();
        batchColumns.put("pk_batchcode","batch");batchColumns.put("pk_group","group");
        require(PhysicalSnapshotReader.restrictColumns("scm_batchcode",batchColumns,batch).equals(batchColumns),
                "Known batch reference fields must be retained");
        batchColumns.put("unreviewed_field","custom");
        try { PhysicalSnapshotReader.restrictColumns("scm_batchcode",batchColumns,batch); throw new AssertionError("Unreviewed batch column accepted"); }
        catch(DAOException expected) { }
        try { InventoryAuditFields.forTable("ic_other_h"); throw new AssertionError("Unknown table accepted"); }
        catch (IllegalArgumentException expected) { }
        System.out.println("PASS InventoryAuditFieldsTests (approved fields and schema drift; no Oracle)");
    }

    private static void require(boolean valid, String message) {
        if (!valid) throw new AssertionError(message);
    }
}
