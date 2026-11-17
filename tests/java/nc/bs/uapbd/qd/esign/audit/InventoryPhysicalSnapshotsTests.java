package nc.bs.uapbd.qd.esign.audit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;

/** Exercises inventory snapshot phases and relationships without an NC database. */
public final class InventoryPhysicalSnapshotsTests {
    public static void run() throws Exception {
        for (String type : Arrays.asList("IC_PURCHASE_IN", "IC_SALE_OUT", "IC_TRANS_IN", "IC_INITIAL_IN", "IC_LOC_ADJUST", "IC_GENERAL_IN", "IC_PRODUCT_IN", "IC_SUBCONT_IN", "IC_GENERAL_OUT", "IC_MATERIAL_OUT", "IC_TRANS_OUT", "IC_OUTSOURCE_IN", "IC_PRODUCTION_ORDER", "IC_SAPPLY")) {
            boolean hasLocations = !"IC_SAPPLY".equals(type);
            int rowCount = 3;
            Rows rows = new Rows(type);
            InventoryPhysicalSnapshots snapshots = new InventoryPhysicalSnapshots(rows);
            OperationContext update = context(type, "UPDATE", "A");
            AuditCaptureScope scope = new AuditCaptureScope(update, true);
            try {
                snapshots.before(scope, update);
                snapshots.after(scope, update, update.getObjects());
                require(scope.getBefore("item").size() == rowCount && scope.getAfter("item").size() == rowCount,
                        "Header, body and location must be captured in both phases");
                require(rows.locked == rowCount && rows.unlocked == rowCount, "Only the before phase must lock rows");
            } finally { scope.close(); }

            rows = new Rows(type);
            snapshots = new InventoryPhysicalSnapshots(rows);
            OperationContext insert = context(type, "INSERT", null);
            scope = new AuditCaptureScope(insert, true);
            try {
                snapshots.before(scope, insert);
                require(rows.locked == 0, "A new bill has no before snapshot");
                snapshots.after(scope, insert, Collections.singletonList(ref(type, "NEW", "CODE")));
                require("NEW".equals(rows.lastPk) && scope.getAfter("item").size() == rowCount,
                        "Insert must capture the actual returned bill identity");
            } finally { scope.close(); }

            rows = new Rows(type);
            rows.wrongOwner = true;
            reject(snapshots(rows), update, "Group or organization mismatch must fail");
            rows = new Rows(type);
            rows.wrongOrg = true;
            reject(snapshots(rows), update, "Organization mismatch must fail");
            rows = new Rows(type);
            rows.wrongHeaderIdentity = true;
            reject(snapshots(rows), update, "Header identity mismatch must fail");
            rows = new Rows(type);
            rows.wrongBodyEntity = true;
            reject(snapshots(rows), update, "Body from a different physical entity must fail");
            rows = new Rows(type);
            rows.missingBody = true;
            reject(snapshots(rows), update, "Missing body row set must fail");
            if (hasLocations) {
                rows = new Rows(type);
                rows.missingLocations = true;
                reject(snapshots(rows), update, "Missing location row set must fail");
                rows = new Rows(type);
                rows.orphanLocation = true;
                reject(snapshots(rows), update, "Location belonging to another body must fail");
            } else {
                rows = new Rows(type); rows.missingExtension = true;
                reject(snapshots(rows), update, "Missing extension query result must fail");
                rows = new Rows(type); rows.orphanExtension = true;
                reject(snapshots(rows), update, "Extension belonging to another body must fail");
                rows = new Rows(type); rows.duplicateExtension = true;
                reject(snapshots(rows), update, "Duplicate extension identity must fail");
            }
            rows = new Rows(type);
            rows.duplicateBody = true;
            reject(snapshots(rows), update, "Duplicate body identity must fail");
            rows = new Rows(type);
            scope = new AuditCaptureScope(update, false);
            try {
                snapshots(rows).before(scope, update);
                snapshots(rows).after(scope, update, update.getObjects());
                require(rows.locked == 0 && rows.unlocked == 0, "Disabled audit must not read business rows");
            } finally { scope.close(); }
            rows = new Rows(type);
            rows.wrongCode = true;
            scope = new AuditCaptureScope(update, true);
            try {
                try { snapshots(rows).after(scope, update, update.getObjects()); throw new AssertionError("Wrong code accepted"); }
                catch (BusinessException expected) { }
            } finally { scope.close(); }

            rows = new Rows(type);
            OperationContext delete = context(type, "DELETE", "A");
            scope = new AuditCaptureScope(delete, true);
            try {
                snapshots = snapshots(rows);
                snapshots.before(scope, delete);
                try { snapshots.after(scope, delete, delete.getObjects()); throw new AssertionError("Active deleted bill accepted"); }
                catch (BusinessException expected) { }
                rows.missingHeader = true;
                try { snapshots.after(scope, delete, delete.getObjects()); throw new AssertionError("Missing deletion verification accepted"); }
                catch (BusinessException expected) { }
                rows.missingHeader = false;
                rows.deleted = true;
                snapshots.after(scope, delete, delete.getObjects());
                require(scope.hasBefore("item") && !scope.hasAfter("item"), "Delete retains its before snapshot");
            } finally { scope.close(); }
        }
        Rows padded=new Rows("IC_SAPPLY");padded.padded=true;
        OperationContext paddedUpdate=context("IC_SAPPLY","UPDATE","A");
        AuditCaptureScope paddedScope=new AuditCaptureScope(paddedUpdate,true);
        try {
            snapshots(padded).before(paddedScope,paddedUpdate);
            List<SnapshotRow> recorded=paddedScope.getBefore("item");
            require("A".equals(recorded.get(0).getRowId()) && "O                   ".equals(recorded.get(0).getValues().get("pk_org"))
                    && "body".equals(recorded.get(1).getRowId()) && "A".equals(recorded.get(1).getParentRowId())
                    && "body                ".equals(recorded.get(2).getValues().get("cgeneralbid")),
                    "CHAR-padded outbound bill identities must match while preserving physical field values");
        } finally {paddedScope.close();}
        System.out.println("PASS InventoryPhysicalSnapshotsTests (inventory phases and row scope; no Oracle)");
    }

    private static InventoryPhysicalSnapshots snapshots(Rows rows) { return new InventoryPhysicalSnapshots(rows); }
    private static void reject(InventoryPhysicalSnapshots snapshots, OperationContext context, String message) throws Exception {
        AuditCaptureScope scope = new AuditCaptureScope(context, true);
        try {
            try { snapshots.before(scope, context); throw new AssertionError(message); }
            catch (BusinessException expected) { }
        } finally { scope.close(); }
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static OperationContext context(String type, String operation, String pk) {
        return new OperationContext("request", "batch", "G", "O", "node", "node", "button", "button",
                operation, null, Collections.singletonList(ref(type, pk, pk == null ? null : "CODE")));
    }
    private static BusinessObjectRef ref(String type, String pk, String code) {
        return new BusinessObjectRef("item", type, pk, code, null);
    }
    private static SnapshotRow row(String table, String id, String parent, Map<String,String> values) {
        return new SnapshotRow(table, id, parent, values, Collections.<String,String>emptyMap(),
                Collections.<String,String>emptyMap(), true);
    }
    private static final class Rows implements InventoryPhysicalSnapshots.Rows {
        final String prefix;
        boolean wrongOwner, wrongOrg, wrongHeaderIdentity, wrongBodyEntity, missingHeader, missingBody, missingLocations;
        boolean orphanLocation, duplicateBody, wrongCode, deleted;
        boolean missingExtension,orphanExtension,duplicateExtension,padded;
        int locked, unlocked;
        String lastPk;
        Rows(String type) { prefix = "IC_SAPPLY".equals(type) ? "ic_sapply" : "IC_PURCHASE_IN".equals(type) || "IC_OUTSOURCE_IN".equals(type) ? "ic_purchasein" : "IC_SALE_OUT".equals(type) ? "ic_saleout" : "IC_INITIAL_IN".equals(type) ? "ic_openbal" : "IC_LOC_ADJUST".equals(type) ? "ic_locadjust" : "IC_GENERAL_IN".equals(type) ? "ic_generalin" : "IC_PRODUCT_IN".equals(type) || "IC_PRODUCTION_ORDER".equals(type) ? "ic_finprodin" : "IC_SUBCONT_IN".equals(type) ? "ic_subcontin" : "IC_GENERAL_OUT".equals(type) ? "ic_generalout" : "IC_MATERIAL_OUT".equals(type) ? "ic_material" : "IC_TRANS_OUT".equals(type) ? "ic_transout" : "ic_transin"; }
        public List<SnapshotRow> read(String table, String key, String parent, String pk, boolean lock) {
            if (lock) locked++; else unlocked++;
            lastPk = pk;
            List<SnapshotRow> result = new ArrayList<SnapshotRow>();
            if (table.equals(prefix + "_h")) {
                require("cgeneralhid".equals(key) && parent == null, "Unexpected header mapping");
                if (missingHeader) return null;
                if (deleted) return result;
                Map<String,String> values = new HashMap<String,String>();
                values.put("pk_group", wrongOwner ? "OTHER" : "G");
                values.put("pk_org", wrongOrg ? "OTHER" : padded?pad("O"):"O");
                values.put("vbillcode", wrongCode ? "OTHER" : "CODE");
                result.add(row(table, wrongHeaderIdentity ? "OTHER" : padded?pad(pk):pk, null, values));
            } else if (table.equals(prefix + "_b")) {
                require("cgeneralbid".equals(key) && "cgeneralhid".equals(parent), "Unexpected body mapping");
                if (missingBody) return null;
                Map<String,String> values = new HashMap<String,String>(); values.put("pk_batchcode", null);
                result.add(row(wrongBodyEntity ? "other_table" : table, padded?pad("body"):"body", padded?pad(pk):pk, values));
                if (duplicateBody) result.add(row(table, "body", pk, values));
            } else if (table.equals(prefix + "_l")) {
                require(!"ic_sapply".equals(prefix), "Outbound requests must not query a nonexistent location entity");
                require("cgenerallid".equals(key) && "cgeneralbid".equals(parent), "Unexpected location mapping");
                if (missingLocations) return null;
                result.add(row(table, "location", orphanLocation ? "OTHER" : "body", Collections.<String,String>emptyMap()));
            } else throw new AssertionError("Unexpected table " + table);
            return Collections.unmodifiableList(result);
        }
        public List<SnapshotRow> readBatchReferences(List<String> keys,String group,boolean lock,boolean sapply) {
            throw new AssertionError("This fixture has no batch reference");
        }
        public List<SnapshotRow> readSapplyExtensions(List<String> keys,boolean lock) {
            if(lock)locked++;else unlocked++;
            if(missingExtension)return null;
            if(keys.isEmpty())return Collections.emptyList();
            String id=orphanExtension?"OTHER":keys.get(0);Map<String,String> values=new HashMap<String,String>();
            values.put("cgeneralbid",padded?pad(id):id);List<SnapshotRow> rows=new ArrayList<SnapshotRow>();
            rows.add(row("ic_sapply_e",padded?pad(id):id,padded?pad(id):id,values));if(duplicateExtension)rows.add(rows.get(0));return rows;
        }
        private static String pad(String value){return String.format("%-20s",value);}
    }
}
