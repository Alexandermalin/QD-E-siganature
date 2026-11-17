package nc.impl.purp.qd.esign;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import nc.bs.dao.BaseDAO;
import nc.bs.framework.common.NCLocator;
import nc.itf.pp.supplierprice.ISupplierPriceService;
import nc.itf.purp.qd.esign.ISupplierPriceBatchOperationAdapter;
import nc.jdbc.framework.SQLParameter;
import nc.jdbc.framework.processor.ArrayProcessor;
import nc.vo.bd.meta.BatchOperateVO;
import nc.vo.pp.supplierprice.entity.SupplierPriceVO;
import nc.vo.purp.qd.esign.SupplierPriceBatchCommand;
import nc.vo.purp.qd.esign.SupplierPriceEditDeleteCommand;
import nc.vo.purp.qd.esign.SupplierPriceOperationCommand;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;

public final class SupplierPriceBatchAdapter implements ISupplierPriceBatchOperationAdapter {
    private static final String INSERT = SupplierPriceOperationCommand.INSERT;
    private static final String UPDATE = SupplierPriceOperationCommand.UPDATE;
    private static final String DELETE = SupplierPriceBatchCommand.DELETE;
    private static final String STAGE_DELETE = SupplierPriceEditDeleteCommand.STAGE_DELETE;

    private final ISupplierPriceService service;
    private final PriceLookup prices;

    public SupplierPriceBatchAdapter() {
        this(null, new PriceLookup() {
            public SupplierPriceVO find(String pk) throws BusinessException {
                SQLParameter parameter = new SQLParameter();
                parameter.addParam(pk);
                // Include soft-deleted rows to verify the native DeleteTable result in this transaction.
                Object[] data = (Object[]) new BaseDAO().executeQuery(
                        "select pk_supplierprice, pk_group, pk_org, dr from purp_supplierprice where pk_supplierprice = ?",
                        parameter, new ArrayProcessor());
                if (data == null || data.length == 0) {
                    return null;
                }
                SupplierPriceVO row = new SupplierPriceVO();
                row.setPk_supplierprice((String) data[0]);
                row.setPk_group((String) data[1]);
                row.setPk_org((String) data[2]);
                row.setDr(data[3] == null ? null : Integer.valueOf(((Number) data[3]).intValue()));
                return row;
            }
        });
    }

    SupplierPriceBatchAdapter(ISupplierPriceService service, PriceLookup prices) {
        this.service = service;
        this.prices = prices;
    }

    interface PriceLookup {
        SupplierPriceVO find(String pk) throws BusinessException;
    }

    @Override
    public void validate(OperationContext context, Serializable command) throws BusinessException {
        if (isStaged(context)) {
            validateStaged(context, command, true);
            return;
        }
        List<BoundRow> rows = bind(context, requireBatch(context, command), false);
        validatePersistedInputs(context, rows);
    }

    @Override
    public Map<String, String> auditOperations(OperationContext context, Serializable command) throws BusinessException {
        Map<String, String> result = new LinkedHashMap<String, String>();
        if (isStaged(context)) {
            for (BusinessObjectRef ref : validateStaged(context, command, false)) {
                result.put(ref.getClientObjectId(), STAGE_DELETE);
            }
            return result;
        }
        for (BoundRow row : bind(context, requireBatch(context, command), false)) {
            result.put(row.ref.getClientObjectId(), row.operation);
        }
        return result;
    }

    @Override
    public Serializable execute(OperationContext context, Serializable command) throws BusinessException {
        if (isStaged(context)) {
            validateStaged(context, command, true);
            return null;
        }
        SupplierPriceBatchCommand typed = requireBatch(context, command);
        List<BoundRow> inputs = bind(context, typed, false);
        validatePersistedInputs(context, inputs);
        BatchOperateVO batch = typed.getBatch();
        int[] addIndices = copy(batch.getAddIndexs());
        int[] updateIndices = copy(batch.getUpdIndexs());
        int[] deleteIndices = copy(batch.getDelIndexs());
        ISupplierPriceService nativeService = service == null
                ? NCLocator.getInstance().lookup(ISupplierPriceService.class) : service;
        BatchOperateVO result = nativeService.batchSave(batch);
        if (result != batch || !Arrays.equals(addIndices, result.getAddIndexs())
                || !Arrays.equals(updateIndices, result.getUpdIndexs())
                || !Arrays.equals(deleteIndices, result.getDelIndexs())) {
            throw new BusinessException("Supplier-price Save did not preserve the native batch and model indices");
        }
        List<BoundRow> output = bind(context, typed, true);
        for (int index = 0; index < inputs.size(); index++) {
            BoundRow input = inputs.get(index);
            BoundRow returned = output.get(index);
            // SupplierPriceBP.fillTctpk sets the new PK on the original add VO before BatchSaveAction
            // replaces the add array. Check that identity against the returned row for this model index.
            String expected = INSERT.equals(input.operation) ? input.row.getPk_supplierprice() : input.pk;
            if (!Objects.equals(expected, returned.pk) || !input.operation.equals(returned.operation)
                    || !input.ref.getClientObjectId().equals(returned.ref.getClientObjectId())) {
                throw new BusinessException("Supplier-price Save returned an unexpected row for its model index");
            }
            requirePersisted(context, returned.pk, DELETE.equals(returned.operation));
        }
        return result;
    }

    @Override
    public List<BusinessObjectRef> describeResult(OperationContext context, Serializable result)
            throws BusinessException {
        throw new BusinessException("A validated supplier-price batch command is required to identify its results");
    }

    @Override
    public List<BusinessObjectRef> describeResult(OperationContext context, Serializable command, Serializable result)
            throws BusinessException {
        if (isStaged(context)) {
            if (result != null) {
                throw new BusinessException("Supplier-price editor deletion must not return a persisted result");
            }
            return validateStaged(context, command, false);
        }
        SupplierPriceBatchCommand typed = requireBatch(context, command);
        if (result != typed.getBatch()) {
            throw new BusinessException("The original supplier-price result envelope is required");
        }
        Map<String, BusinessObjectRef> refs = new HashMap<String, BusinessObjectRef>();
        for (BoundRow row : bind(context, typed, true)) {
            BusinessObjectRef ref = row.ref;
            refs.put(ref.getClientObjectId(), new BusinessObjectRef(ref.getClientObjectId(), ref.getObjectType(),
                    row.pk, ref.getBillCode(), null, ref.getDesiredOwnerOrg(), ref.getPolicyOwnerOrg(),
                    ref.getTargetOrg()));
        }
        List<BusinessObjectRef> ordered = new ArrayList<BusinessObjectRef>();
        for (BusinessObjectRef ref : context.getObjects()) {
            ordered.add(refs.get(ref.getClientObjectId()));
        }
        return ordered;
    }

    private static SupplierPriceBatchCommand requireBatch(OperationContext context, Serializable command)
            throws BusinessException {
        requireScope(context);
        boolean save = "Save".equals(context.getButtonCode())
                && SupplierPriceBatchCommand.BATCH_SAVE.equals(context.getOperationType());
        boolean delete = "Delete".equals(context.getButtonCode()) && DELETE.equals(context.getOperationType());
        if ((!save && !delete) || !(command instanceof SupplierPriceBatchCommand)) {
            throw new BusinessException("Supplier-price operation does not match an authorized batch");
        }
        SupplierPriceBatchCommand typed = (SupplierPriceBatchCommand) command;
        BatchOperateVO batch = typed.getBatch();
        if (batch == null || batch.getClass() != BatchOperateVO.class
                || (delete && (size(batch.getAddObjs()) != 0 || size(batch.getUpdObjs()) != 0))) {
            throw new BusinessException("Supplier-price operation requires the original matching native batch");
        }
        return typed;
    }

    private static List<BoundRow> bind(OperationContext context, SupplierPriceBatchCommand command,
            boolean result) throws BusinessException {
        Map<String, BusinessObjectRef> byId = indexRefs(context.getObjects());
        Map<String, BusinessObjectRef> byPk = new HashMap<String, BusinessObjectRef>();
        for (BusinessObjectRef ref : byId.values()) {
            if (ref.getObjectPk() != null) {
                byPk.put(ref.getObjectPk(), ref);
            }
        }
        BatchOperateVO batch = command.getBatch();
        Set<Integer> currentIndices = new HashSet<Integer>();
        checkIndices(batch.getAddObjs(), batch.getAddIndexs(), currentIndices);
        checkIndices(batch.getUpdObjs(), batch.getUpdIndexs(), currentIndices);
        // Deleted row positions are captured at removal time and can repeat across edit operations.
        checkIndices(batch.getDelObjs(), batch.getDelIndexs(), null);

        String[] addIds = command.getAddClientObjectIds();
        if (size(batch.getAddObjs()) != addIds.length) {
            throw new BusinessException("Supplier-price additions do not match their client object IDs");
        }
        List<BoundRow> bindings = new ArrayList<BoundRow>();
        Set<String> matchedIds = new HashSet<String>();
        Set<String> matchedPks = new HashSet<String>();
        Set<SupplierPriceVO> addRows = Collections.newSetFromMap(new IdentityHashMap<SupplierPriceVO, Boolean>());
        for (int index = 0; index < addIds.length; index++) {
            SupplierPriceVO row = requireActiveRow(context, batch.getAddObjs()[index]);
            String pk = row.getPk_supplierprice();
            BusinessObjectRef ref = byId.get(addIds[index]);
            if (ref == null || ref.getObjectPk() != null || !addRows.add(row)
                    || (result ? (!validKey(pk) || !matchedPks.add(pk)) : pk != null && !pk.isEmpty())) {
                throw new BusinessException("Invalid or ambiguous supplier-price addition identity");
            }
            addBinding(bindings, matchedIds, new BoundRow(ref, row, INSERT, pk));
        }
        bindPersisted(context, batch.getUpdObjs(), UPDATE, byPk, matchedIds, matchedPks, bindings);
        bindPersisted(context, batch.getDelObjs(), DELETE, byPk, matchedIds, matchedPks, bindings);
        if (bindings.size() != byId.size()) {
            throw new BusinessException("Supplier-price batch does not identify the exact authorized row set");
        }
        return bindings;
    }

    private static void bindPersisted(OperationContext context, Object[] rows, String operation,
            Map<String, BusinessObjectRef> byPk, Set<String> matchedIds, Set<String> matchedPks,
            List<BoundRow> bindings) throws BusinessException {
        if (rows == null) {
            return;
        }
        for (Object raw : rows) {
            SupplierPriceVO row = requireActiveRow(context, raw);
            String pk = row.getPk_supplierprice();
            BusinessObjectRef ref = byPk.get(pk);
            if (ref == null || !matchedPks.add(pk)) {
                throw new BusinessException("Unexpected or duplicate persisted supplier-price identity");
            }
            addBinding(bindings, matchedIds, new BoundRow(ref, row, operation, pk));
        }
    }

    private static void addBinding(List<BoundRow> rows, Set<String> matchedIds, BoundRow row)
            throws BusinessException {
        if (!matchedIds.add(row.ref.getClientObjectId())) {
            throw new BusinessException("Duplicate supplier-price client object identity");
        }
        rows.add(row);
    }

    private void validatePersistedInputs(OperationContext context, List<BoundRow> rows) throws BusinessException {
        for (BoundRow row : rows) {
            if (!INSERT.equals(row.operation)) {
                requirePersisted(context, row.pk, false);
            }
        }
    }

    private List<BusinessObjectRef> validateStaged(OperationContext context, Serializable command,
            boolean checkPersisted) throws BusinessException {
        requireScope(context);
        if (!(command instanceof SupplierPriceEditDeleteCommand)) {
            throw new BusinessException("A typed supplier-price editor deletion command is required");
        }
        Map<String, BusinessObjectRef> submitted = indexRefs(((SupplierPriceEditDeleteCommand) command).getObjects());
        Map<String, BusinessObjectRef> authorized = indexRefs(context.getObjects());
        if (!authorized.keySet().equals(submitted.keySet())) {
            throw new BusinessException("Supplier-price editor deletion does not match the authorized selection");
        }
        for (BusinessObjectRef ref : context.getObjects()) {
            String pk = ref.getObjectPk();
            if (!Objects.equals(pk, submitted.get(ref.getClientObjectId()).getObjectPk())) {
                throw new BusinessException("Supplier-price editor deletion has a different row identity");
            }
            if (checkPersisted && pk != null) {
                requirePersisted(context, pk, false);
            }
        }
        return new ArrayList<BusinessObjectRef>(context.getObjects());
    }

    private static Map<String, BusinessObjectRef> indexRefs(List<BusinessObjectRef> refs) throws BusinessException {
        Map<String, BusinessObjectRef> result = new HashMap<String, BusinessObjectRef>();
        Set<String> pks = new HashSet<String>();
        if (refs == null || refs.isEmpty()) {
            throw new BusinessException("Supplier-price authorization requires at least one row");
        }
        for (BusinessObjectRef ref : refs) {
            if (ref == null || !SupplierPriceBatchCommand.OBJECT_TYPE.equals(ref.getObjectType())
                    || !validKey(ref.getClientObjectId()) || ref.getParentClientObjectId() != null
                    || (ref.getObjectPk() != null && (!validKey(ref.getObjectPk()) || !pks.add(ref.getObjectPk())))
                    || result.put(ref.getClientObjectId(), ref) != null) {
                throw new BusinessException("Invalid or duplicate supplier-price authorization identity");
            }
        }
        return result;
    }

    private static void checkIndices(Object[] rows, int[] indices, Set<Integer> distinct) throws BusinessException {
        if (size(rows) != (indices == null ? 0 : indices.length)) {
            throw new BusinessException("Supplier-price batch rows do not match their native model indices");
        }
        if (indices != null) {
            for (int index : indices) {
                if (index < 0 || (distinct != null && !distinct.add(Integer.valueOf(index)))) {
                    throw new BusinessException("Invalid supplier-price model index");
                }
            }
        }
    }

    private void requirePersisted(OperationContext context, String pk, boolean deleted) throws BusinessException {
        SupplierPriceVO row = prices.find(pk);
        if (row == null || !pk.equals(row.getPk_supplierprice()) || !ownedBy(context, row)
                || (deleted ? !Integer.valueOf(1).equals(row.getDr()) : !isActive(row))) {
            throw new BusinessException("Supplier-price persisted row has an unexpected identity, owner or delete state");
        }
    }

    private static SupplierPriceVO requireActiveRow(OperationContext context, Object raw) throws BusinessException {
        if (raw == null || raw.getClass() != SupplierPriceVO.class) {
            throw new BusinessException("Only native supplier-price rows are accepted");
        }
        SupplierPriceVO row = (SupplierPriceVO) raw;
        if (!ownedBy(context, row) || !isActive(row)) {
            throw new BusinessException("Supplier price is outside the authorized group or organization, or deleted");
        }
        return row;
    }

    private static boolean ownedBy(OperationContext context, SupplierPriceVO row) {
        return context.getGroupId().equals(row.getPk_group()) && context.getOrgId().equals(row.getPk_org());
    }

    private static boolean isActive(SupplierPriceVO row) {
        return row.getDr() == null || row.getDr().intValue() == 0;
    }

    private static boolean isStaged(OperationContext context) {
        return context != null && "Delete".equals(context.getButtonCode()) && STAGE_DELETE.equals(context.getOperationType());
    }

    private static void requireScope(OperationContext context) throws BusinessException {
        if (context == null || !"40050200".equals(context.getNodeCode()) || !validKey(context.getGroupId())
                || !validKey(context.getOrgId())) {
            throw new BusinessException("Supplier-price authorization requires node 40050200 and a group and organization");
        }
    }

    private static boolean validKey(String value) {
        return value != null && !value.isEmpty() && value.equals(value.trim());
    }

    private static int size(Object[] rows) {
        return rows == null ? 0 : rows.length;
    }

    private static int[] copy(int[] indices) {
        return indices == null ? null : indices.clone();
    }

    private static final class BoundRow {
        final BusinessObjectRef ref;
        final SupplierPriceVO row;
        final String operation;
        final String pk;

        BoundRow(BusinessObjectRef ref, SupplierPriceVO row, String operation, String pk) {
            this.ref = ref;
            this.row = row;
            this.operation = operation;
            this.pk = pk;
        }
    }
}
