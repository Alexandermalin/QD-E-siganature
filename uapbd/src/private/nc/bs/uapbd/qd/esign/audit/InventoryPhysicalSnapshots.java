package nc.bs.uapbd.qd.esign.audit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;

/** Captures the persisted inventory bill and its detail and location rows. */
public final class InventoryPhysicalSnapshots {
    public static final String BATCH_REFERENCE_ENTITY = SnapshotRow.INVENTORY_BATCH_REFERENCE;
    private static final String ADAPTER = "nc.itf.ic.qd.esign.IInventoryOperationAdapter";
    interface Rows {
        List<SnapshotRow> read(String table, String key, String parent, String billPk, boolean lock)
                throws BusinessException;
        List<SnapshotRow> readBatchReferences(List<String> keys,String group,boolean lock,boolean sapply) throws BusinessException;
        List<SnapshotRow> readSapplyExtensions(List<String> bodyKeys,boolean lock) throws BusinessException;
    }

    private final Rows rows;

    public InventoryPhysicalSnapshots() {
        this(new Rows() {
            private final PhysicalSnapshotReader reader = new PhysicalSnapshotReader();
            public List<SnapshotRow> read(String table, String key, String parent, String billPk, boolean lock)
                    throws BusinessException {
                return reader.readInventory(table, key, parent, billPk, lock);
            }
            public List<SnapshotRow> readBatchReferences(List<String> keys,String group,boolean lock,boolean sapply) throws BusinessException {
                return sapply?reader.readSapplyBatchReferences(keys,group,lock):reader.readInventoryBatchReferences(keys,group,lock);
            }
            public List<SnapshotRow> readSapplyExtensions(List<String> bodyKeys,boolean lock) throws BusinessException {
                return reader.readSapplyExtensions(bodyKeys,lock);
            }
        });
    }

    InventoryPhysicalSnapshots(Rows rows) {
        if (rows == null) throw new IllegalArgumentException("Inventory snapshot reader is required");
        this.rows = rows;
    }

    public static boolean applies(String adapter, OperationContext context) {
        return context != null && (ADAPTER.equals(adapter)
                || "nc.itf.ic.qd.esign.IInventoryPfSaveAdapter".equals(adapter)
                || "nc.itf.ic.qd.esign.IInventoryPfStateAdapter".equals(adapter)
                || "nc.itf.ic.qd.esign.ISapplyLineOperationAdapter".equals(adapter));
    }

    public void before(AuditCaptureScope scope, OperationContext context) throws BusinessException {
        if ("INSERT".equals(context.getOperationType())) return;
        for (BusinessObjectRef ref : context.getObjects()) {
            if (scope.isEnabledFor(ref.getClientObjectId())) {
                List<SnapshotRow> bill=aggregate(scope,context,ref,true);
                List<SnapshotRow> references=new ArrayList<SnapshotRow>();
                if("DELETE".equals(context.getOperationType()))references.addAll(batchReferences(context,ref,bill));
                scope.before(ref.getClientObjectId(), bill);
                if(!references.isEmpty()) scope.beforeReferences(ref.getClientObjectId(),references);
            }
        }
    }

    public void after(AuditCaptureScope scope, OperationContext context, List<BusinessObjectRef> results)
            throws BusinessException {
        for (BusinessObjectRef ref : results) {
            if (!scope.isEnabledFor(ref.getClientObjectId())) continue;
            if ("DELETE".equals(context.getOperationType())) {
                List<SnapshotRow> remaining=rows.read(tablePrefix(ref) + "_h", "cgeneralhid", null, ref.getObjectPk(), false);
                if (remaining == null) throw new BusinessException("Inventory deletion verification returned no row set");
                if (!remaining.isEmpty()) {
                    throw new BusinessException("Native inventory deletion left an active bill");
                }
            } else {
                scope.after(ref.getClientObjectId(),aggregate(scope,context,ref,false));
            }
        }
    }

    private List<SnapshotRow> aggregate(AuditCaptureScope scope,OperationContext context,
            BusinessObjectRef ref, boolean lock) throws BusinessException {
        String prefix = tablePrefix(ref);
        String pk = ref.getObjectPk();
        if (pk == null || pk.trim().isEmpty()) throw new BusinessException("Inventory snapshot bill identity is missing");
        List<SnapshotRow> headers=verifiedHeaders(context,ref,lock);
        List<SnapshotRow> result = new ArrayList<SnapshotRow>(headers);
        if (!lock && ref.getBillCode() != null && !ref.getBillCode().equals(result.get(0).getValues().get("vbillcode"))) {
            throw new BusinessException("Persisted inventory code does not match the native result");
        }
        List<SnapshotRow> details = rows.read(prefix + "_b", "cgeneralbid", "cgeneralhid", pk, lock);
        if("IC_SAPPLY".equals(ref.getObjectType()))details=normalizeSapplyRows(details);
        // Outbound-request dynamic body fields are stored in ic_sapply_e, not a location table.
        List<SnapshotRow> locations = "IC_SAPPLY".equals(ref.getObjectType())
                ? java.util.Collections.<SnapshotRow>emptyList()
                : rows.read(prefix + "_l", "cgenerallid", "cgeneralbid", pk, lock);
        if (details == null || locations == null) throw new BusinessException("Inventory detail snapshot is unavailable");
        Set<String> bodyIds = new HashSet<String>();
        for (SnapshotRow detail : details) {
            if (detail == null || !(prefix + "_b").equals(detail.getEntity()) || blank(detail.getRowId())
                    || !pk.equals(detail.getParentRowId()) || !bodyIds.add(detail.getRowId())) {
                throw new BusinessException("Inventory body rows do not match the bill");
            }
        }
        List<SnapshotRow> extensions=java.util.Collections.emptyList();
        if("IC_SAPPLY".equals(ref.getObjectType())) {
            extensions=normalizeSapplyRows(rows.readSapplyExtensions(new ArrayList<String>(bodyIds),lock));
            if(extensions==null)throw new BusinessException("出库申请表体扩展快照不可用。");
            Set<String> extensionIds=new HashSet<String>();
            for(SnapshotRow extension:extensions) {
                if(extension==null || !"ic_sapply_e".equals(extension.getEntity()) || blank(extension.getRowId())
                        || !bodyIds.contains(extension.getRowId()) || !extension.getRowId().equals(extension.getParentRowId())
                        || !extension.getRowId().equals(rtrim(extension.getValues().get("cgeneralbid")))
                        || !extensionIds.add(extension.getRowId()))
                    throw new BusinessException("出库申请表体扩展行与授权单据不一致。");
            }
        }
        Set<String> locationIds = new HashSet<String>();
        for (SnapshotRow location : locations) {
            if (location == null || !(prefix + "_l").equals(location.getEntity()) || blank(location.getRowId())
                    || !bodyIds.contains(location.getParentRowId()) || !locationIds.add(location.getRowId())) {
                throw new BusinessException("Inventory location rows do not match the bill body");
            }
        }
        result.addAll(details);
        result.addAll(extensions);
        result.addAll(locations);
        return result;
    }

    private List<SnapshotRow> verifiedHeaders(OperationContext context,BusinessObjectRef ref,boolean lock) throws BusinessException {
        String prefix=tablePrefix(ref),pk=ref.getObjectPk();
        if(pk==null || pk.trim().isEmpty())throw new BusinessException("Inventory snapshot bill identity is missing");
        List<SnapshotRow> headers=rows.read(prefix+"_h","cgeneralhid",null,pk,lock);
        boolean sapply="IC_SAPPLY".equals(ref.getObjectType());if(sapply)headers=normalizeSapplyRows(headers);
        if (headers == null || headers.size() != 1 || headers.get(0) == null
                || !(prefix + "_h").equals(headers.get(0).getEntity())
                || !pk.equals(headers.get(0).getRowId()) || headers.get(0).getParentRowId() != null
                || !context.getGroupId().equals(headers.get(0).getValues().get("pk_group"))
                || !context.getOrgId().equals(sapply?rtrim(headers.get(0).getValues().get("pk_org")):headers.get(0).getValues().get("pk_org"))) {
            throw new BusinessException("Persisted inventory bill identity or authorized group/organization is inconsistent");
        }
        return headers;
    }

    private static List<SnapshotRow> normalizeSapplyRows(List<SnapshotRow> rows) {
        if(rows==null)return null;List<SnapshotRow> result=new ArrayList<SnapshotRow>();
        for(SnapshotRow row:rows)result.add(row==null?null:new SnapshotRow(row.getEntity(),rtrim(row.getRowId()),
                rtrim(row.getParentRowId()),row.getValues(),row.getTypes(),row.getLabels(),row.hasPhysicalColumns()));
        return result;
    }
    private static String rtrim(String value) {
        if(value==null)return null;int end=value.length();while(end>0 && value.charAt(end-1)==' ')end--;
        return value.substring(0,end);
    }

    /** Preserve the shared batch record as a reference, never as a deleted bill-owned row. */
    private List<SnapshotRow> batchReferences(OperationContext context,BusinessObjectRef ref,List<SnapshotRow> bill)
            throws BusinessException {
        String bodyTable=tablePrefix(ref)+"_b";Set<String> keys=new TreeSet<String>();
        for(SnapshotRow row:bill) if(bodyTable.equals(row.getEntity())) {
            if(!row.getValues().containsKey("pk_batchcode"))
                throw new BusinessException("Inventory body lacks the batch reference column");
            String key=row.getValues().get("pk_batchcode");
            if(key==null || key.trim().isEmpty() || "~".equals(key.trim())) continue;
            if(!key.equals(key.trim())) throw new BusinessException("Inventory batch reference is malformed");
            keys.add(key);
        }
        if(keys.isEmpty()) return java.util.Collections.emptyList();
        boolean sapply="IC_SAPPLY".equals(ref.getObjectType());
        List<SnapshotRow> found=rows.readBatchReferences(new ArrayList<String>(keys),context.getGroupId(),true,sapply);
        if(found==null) throw new BusinessException("Inventory batch reference rows are unavailable");
        Map<String,SnapshotRow> indexed=new LinkedHashMap<String,SnapshotRow>();
        for(SnapshotRow row:found) {
            if(row==null || !"scm_batchcode".equals(row.getEntity()) || !keys.contains(row.getRowId())
                    || row.getParentRowId()!=null || !row.getRowId().equals(sapply?rtrim(row.getValues().get("pk_batchcode")):row.getValues().get("pk_batchcode"))
                    || !context.getGroupId().equals(row.getValues().get("pk_group"))
                    || indexed.put(row.getRowId(),row)!=null)
                throw new BusinessException("Inventory batch reference identity or group is inconsistent");
        }
        if(indexed.size()!=keys.size()) throw new BusinessException("Inventory batch reference is missing or outside the authorized group");
        List<SnapshotRow> result=new ArrayList<SnapshotRow>();
        for(String key:keys) {
            SnapshotRow row=indexed.get(key);
            result.add(new SnapshotRow(BATCH_REFERENCE_ENTITY,row.getRowId(),null,row.getValues(),row.getTypes(),row.getLabels(),true));
        }
        return result;
    }

    private String tablePrefix(BusinessObjectRef ref) throws BusinessException {
        if ("IC_PURCHASE_IN".equals(ref.getObjectType())) return "ic_purchasein";
        if ("IC_SALE_OUT".equals(ref.getObjectType())) return "ic_saleout";
        if ("IC_TRANS_IN".equals(ref.getObjectType())) return "ic_transin";
        if ("IC_INITIAL_IN".equals(ref.getObjectType())) return "ic_openbal";
        if ("IC_LOC_ADJUST".equals(ref.getObjectType())) return "ic_locadjust";
        if ("IC_GENERAL_IN".equals(ref.getObjectType())) return "ic_generalin";
        if ("IC_PRODUCT_IN".equals(ref.getObjectType())) return "ic_finprodin";
        if ("IC_SUBCONT_IN".equals(ref.getObjectType())) return "ic_subcontin";
        if ("IC_GENERAL_OUT".equals(ref.getObjectType())) return "ic_generalout";
        if ("IC_MATERIAL_OUT".equals(ref.getObjectType())) return "ic_material";
        if ("IC_TRANS_OUT".equals(ref.getObjectType())) return "ic_transout";
        if ("IC_OUTSOURCE_IN".equals(ref.getObjectType())) return "ic_purchasein";
        if ("IC_PRODUCTION_ORDER".equals(ref.getObjectType())) return "ic_finprodin";
        if ("IC_SAPPLY".equals(ref.getObjectType())) return "ic_sapply";
        throw new BusinessException("Unsupported inventory snapshot object");
    }

    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}
