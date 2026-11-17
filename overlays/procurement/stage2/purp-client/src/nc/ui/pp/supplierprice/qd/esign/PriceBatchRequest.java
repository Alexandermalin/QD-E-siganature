package nc.ui.pp.supplierprice.qd.esign;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import nc.vo.bd.meta.BatchOperateVO;
import nc.vo.pp.supplierprice.entity.SupplierPriceVO;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;

final class PriceBatchRequest {
    static final String NODE = "40050200";
    static final String STAGE_DELETE = "STAGE_DELETE";
    private static final String TYPE = "PURP_SUPPLIER_PRICE";
    private static final Object[] EMPTY = new Object[0];
    final OperationContext context;
    final String[] addClientIds;

    private PriceBatchRequest(OperationContext context, String[] addClientIds) {
        this.context = context;
        this.addClientIds = addClientIds;
    }

    static PriceBatchRequest forBatch(String button, BatchOperateVO batch) throws BusinessException {
        if (batch == null) throw new BusinessException("供应商价目表缺少本次保存批次。");
        Object[] adds = rows(batch.getAddObjs());
        Object[] updates = rows(batch.getUpdObjs());
        Object[] deletes = rows(batch.getDelObjs());
        boolean save = "Save".equals(button);
        if (!save && (!"Delete".equals(button) || adds.length != 0 || updates.length != 0)) {
            throw new BusinessException("供应商价目表删除入口收到非删除批次。");
        }
        Builder builder = new Builder();
        String[] added = new String[adds.length];
        for (int i = 0; i < adds.length; i++) added[i] = builder.add(adds[i], true, false);
        for (Object value : updates) builder.add(value, false, false);
        for (Object value : deletes) builder.add(value, false, false);
        return builder.finish(button, save ? "BATCH_SAVE" : "DELETE", added);
    }

    static PriceBatchRequest forEditDelete(Object[] selection) throws BusinessException {
        Builder builder = new Builder();
        for (Object value : rows(selection)) builder.add(value, false, true);
        return builder.finish("Delete", STAGE_DELETE, new String[0]);
    }

    static boolean hasChanges(BatchOperateVO batch) {
        return batch != null && (rows(batch.getAddObjs()).length != 0 || rows(batch.getUpdObjs()).length != 0
                || rows(batch.getDelObjs()).length != 0);
    }

    private static Object[] rows(Object[] rows) { return rows == null ? EMPTY : rows; }

    private static final class Builder {
        private final List<BusinessObjectRef> objects = new ArrayList<BusinessObjectRef>();
        private final Set<String> existing = new HashSet<String>();
        private String group;
        private String org;

        private String add(Object value, boolean insert, boolean staged) throws BusinessException {
            if (!(value instanceof SupplierPriceVO)) throw new BusinessException("供应商价目表行类型无效。");
            SupplierPriceVO row = (SupplierPriceVO) value;
            String rowGroup = clean(row.getPk_group());
            String rowOrg = clean(row.getPk_org());
            String pk = clean(row.getPk_supplierprice());
            if (rowGroup == null || rowOrg == null || (group != null && (!group.equals(rowGroup) || !org.equals(rowOrg)))) {
                throw new BusinessException("同一受控价目批次须属于同一集团及业务单元。");
            }
            if ((insert && pk != null) || (!insert && !staged && pk == null) || (pk != null && !existing.add(pk))) {
                throw new BusinessException("供应商价目表批次的行主键不匹配或重复。");
            }
            group = rowGroup;
            org = rowOrg;
            String id = UUID.randomUUID().toString();
            objects.add(new BusinessObjectRef(id, TYPE, pk, null, null));
            return id;
        }

        private PriceBatchRequest finish(String button, String operation, String[] added) throws BusinessException {
            if (objects.isEmpty()) throw new BusinessException("请先选择要操作的供应商价目行。");
            OperationContext context = new OperationContext(null, null, group, org, NODE, "供应商价目表维护",
                    button, "Save".equals(button) ? "保存" : "删除", operation, null, objects);
            return new PriceBatchRequest(context, added);
        }
    }

    private static String clean(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
