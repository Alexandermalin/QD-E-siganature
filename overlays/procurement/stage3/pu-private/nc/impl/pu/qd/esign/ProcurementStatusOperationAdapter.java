package nc.impl.pu.qd.esign;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import nc.bs.dao.BaseDAO;
import nc.bs.framework.common.NCLocator;
import nc.itf.pu.m20.IPraybillMaintain;
import nc.itf.pu.m21.IOrderClose;
import nc.itf.pu.m21.IOrderMaintain;
import nc.itf.pu.m25.IInvoiceMaintain;
import nc.itf.pu.m422x.IStoreReqAppClose;
import nc.itf.pu.qd.esign.IProcurementStatusOperationAdapter;
import nc.jdbc.framework.SQLParameter;
import nc.vo.pu.m20.entity.PraybillVO;
import nc.vo.pu.m21.entity.OrderVO;
import nc.vo.pu.m25.entity.InvoiceVO;
import nc.vo.pu.m422x.entity.StoreReqAppVO;
import nc.vo.pu.qd.esign.ProcurementStatusCommand;
import nc.vo.pu.qd.esign.ProcurementStatusRoute;
import nc.vo.pub.BusinessException;
import nc.vo.pub.ISuperVO;
import nc.vo.pub.IVOMeta;
import nc.vo.pub.SuperVO;
import nc.vo.pub.VOStatus;
import nc.vo.pub.lang.UFBoolean;
import nc.vo.pubapp.pattern.model.entity.bill.AbstractBill;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;

public final class ProcurementStatusOperationAdapter implements IProcurementStatusOperationAdapter {
    // Values persisted by the native NC status and order line activation enums.
    private static final Integer APPROVED = Integer.valueOf(3);
    private static final Integer CLOSED = Integer.valueOf(5);
    private static final Integer ORDER_ROW_ACTIVE = Integer.valueOf(0);
    private static final Integer ORDER_ROW_CLOSED = Integer.valueOf(1);

    private final ServiceLookup services;
    private final RecordLookup records;

    public ProcurementStatusOperationAdapter() {
        this(new ServiceLookup() {
            public Object find(Class<?> service) {
                return NCLocator.getInstance().lookup(service);
            }
        }, new RecordLookup() {
            public Collection<?> find(Class<? extends SuperVO> type, String key, String pk) throws BusinessException {
                SQLParameter parameters = new SQLParameter();
                parameters.addParam(pk);
                Collection<?> matches = new BaseDAO().retrieveByClause(type,
                        key + " = ? and (dr = 0 or dr is null)", parameters);
                return matches == null ? Collections.emptyList() : matches;
            }
        });
    }

    ProcurementStatusOperationAdapter(ServiceLookup services, RecordLookup records) {
        this.services = services;
        this.records = records;
    }

    interface ServiceLookup {
        Object find(Class<?> service);
    }

    interface RecordLookup {
        Collection<?> find(Class<? extends SuperVO> type, String key, String pk) throws BusinessException;
    }

    @Override
    public void validate(OperationContext context, Serializable command) throws BusinessException {
        ProcurementStatusRoute route = requireRoute(context);
        requireCommand(route, context, command);
    }

    @Override
    public Serializable execute(OperationContext context, Serializable command) throws BusinessException {
        ProcurementStatusRoute route = requireRoute(context);
        Map<String, Set<String>> selectedRows = requireCommand(route, context, command);
        Object[] args = ((ProcurementStatusCommand) command).getArguments();
        Serializable result = dispatch(route, services.find(route.service()), args);
        describeResult(context, result);
        if (route.isRow()) {
            for (Map.Entry<String, Set<String>> selection : selectedRows.entrySet()) {
                Map<String, SuperVO> persisted = persistedRows(route, route.rowType(), selection.getKey());
                for (String row : selection.getValue()) {
                    requireState(route, requireRecord(persisted.get(row), route.rowType(), row));
                }
            }
        }
        return result;
    }

    @Override
    public List<BusinessObjectRef> describeResult(OperationContext context, Serializable result) throws BusinessException {
        ProcurementStatusRoute route = requireRoute(context);
        Map<String, BusinessObjectRef> refs = requireRefs(route, context);
        Map<String, AbstractBill> returned = route.typedBills(result);
        if (!returned.keySet().equals(refs.keySet())) {
            throw new BusinessException("采购状态操作返回的单据与授权范围不一致。");
        }
        List<BusinessObjectRef> descriptions = new ArrayList<BusinessObjectRef>();
        for (BusinessObjectRef ref : refs.values()) {
            String pk = ref.getObjectPk();
            SuperVO persisted = requireHeader(route, context, pk);
            requireReturnedOwner(context, route.header(returned.get(pk)));
            if (!route.isRow()) {
                requireState(route, persisted);
            }
            descriptions.add(new BusinessObjectRef(ref.getClientObjectId(), ref.getObjectType(), pk,
                    ProcurementStatusRoute.text(persisted, "vbillcode"), null, ref.getDesiredOwnerOrg(),
                    ref.getPolicyOwnerOrg(), ref.getTargetOrg()));
        }
        return descriptions;
    }

    private Map<String, Set<String>> requireCommand(ProcurementStatusRoute route, OperationContext context,
            Serializable command) throws BusinessException {
        if (!(command instanceof ProcurementStatusCommand)) {
            throw new BusinessException("采购状态操作缺少对应业务命令。");
        }
        Map<String, BusinessObjectRef> refs = requireRefs(route, context);
        Map<String, AbstractBill> submitted = route.bills((ProcurementStatusCommand) command);
        if (!submitted.keySet().equals(refs.keySet())) {
            throw new BusinessException("采购状态操作提交的单据与授权范围不一致。");
        }
        Map<String, Set<String>> selections = new LinkedHashMap<String, Set<String>>();
        for (Map.Entry<String, AbstractBill> input : submitted.entrySet()) {
            String pk = input.getKey();
            AbstractBill bill = input.getValue();
            Map<String, SuperVO> selected = route.isRow() ? route.rows(bill) : null;
            BusinessObjectRef ref = refs.get(pk);
            if (!route.clientId(pk, selected).equals(ref.getClientObjectId())) {
                throw new BusinessException("采购状态操作的单据或选中明细与签名范围不一致。");
            }
            SuperVO persisted = requireHeader(route, context, pk);
            requireUnchangedFields(route.header(bill), persisted, route == ProcurementStatusRoute.INVOICE_FREEZE);
            requireChildren(route, bill, pk);
            if (selected != null) {
                selections.put(pk, new HashSet<String>(selected.keySet()));
            }
        }
        return selections;
    }

    private void requireChildren(ProcurementStatusRoute route, AbstractBill bill, String pk) throws BusinessException {
        for (IVOMeta meta : bill.getMetaData().getChildren()) {
            ISuperVO[] children = bill.getChildren(meta);
            if (children == null || children.length == 0) {
                continue;
            }
            String key = meta.getPrimaryAttribute().getName();
            Set<String> seen = new HashSet<String>();
            Map<String, SuperVO> persisted = null;
            for (ISuperVO child : children) {
                if (!(child instanceof SuperVO) || !route.isChildType(child.getClass(), key)
                        || !ProcurementStatusRoute.validKey(child.getPrimaryKey()) || !seen.add(child.getPrimaryKey())) {
                    throw new BusinessException("采购状态操作的明细类型或主键不正确。");
                }
                SuperVO light = (SuperVO) child;
                if (persisted == null) {
                    persisted = persistedRows(route, light.getClass(), pk);
                }
                SuperVO row = requireRecord(persisted.get(light.getPrimaryKey()), light.getClass(), light.getPrimaryKey());
                requireUnchangedFields(light, row, false);
            }
        }
    }

    private SuperVO requireHeader(ProcurementStatusRoute route, OperationContext context, String pk) throws BusinessException {
        Collection<?> matches = records.find(route.headerType(), route.headerKey(), pk);
        if (matches.size() != 1) {
            throw new BusinessException("找不到唯一的有效采购单据。");
        }
        SuperVO persisted = requireRecord(matches.iterator().next(), route.headerType(), pk);
        if (!context.getGroupId().equals(persisted.getAttributeValue("pk_group"))
                || !context.getOrgId().equals(persisted.getAttributeValue("pk_org"))) {
            throw new BusinessException("采购单据不属于已授权的集团和业务单元。");
        }
        return persisted;
    }

    private Map<String, SuperVO> persistedRows(ProcurementStatusRoute route, Class<? extends SuperVO> type,
            String parentPk) throws BusinessException {
        Map<String, SuperVO> rows = new HashMap<String, SuperVO>();
        for (Object result : records.find(type, route.headerKey(), parentPk)) {
            if (result == null || result.getClass() != type) {
                throw new BusinessException("采购单据明细类型不正确。");
            }
            SuperVO row = type.cast(result);
            if (!parentPk.equals(row.getAttributeValue(route.headerKey())) || rows.put(row.getPrimaryKey(), row) != null) {
                throw new BusinessException("采购明细不属于已授权单据，或主键不唯一。");
            }
        }
        return rows;
    }

    private static SuperVO requireRecord(Object result, Class<? extends SuperVO> type, String pk) throws BusinessException {
        if (result == null || result.getClass() != type) {
            throw new BusinessException("找不到已授权的有效采购单据或明细。");
        }
        SuperVO persisted = type.cast(result);
        if (!pk.equals(persisted.getPrimaryKey())
                || persisted.getAttributeValue("dr") != null && !Integer.valueOf(0).equals(persisted.getAttributeValue("dr"))) {
            throw new BusinessException("找不到已授权的有效采购单据或明细。");
        }
        return persisted;
    }

    private static void requireUnchangedFields(SuperVO submitted, SuperVO persisted, boolean invoiceFreeze)
            throws BusinessException {
        int status = submitted.getStatus();
        if (status != VOStatus.UNCHANGED && !(invoiceFreeze && status == VOStatus.UPDATED)
                || !Objects.equals(persisted.getAttributeValue("ts"), submitted.getAttributeValue("ts"))) {
            throw new BusinessException("采购单据或明细已变化，或包含超出授权范围的编辑。");
        }
        for (String field : submitted.usedAttributeNames()) {
            Object value = submitted.getAttributeValue(field);
            if (invoiceFreeze && "vfrozenreason".equals(field) && value instanceof String) {
                continue;
            }
            if (!Objects.equals(persisted.getAttributeValue(field), value)) {
                throw new BusinessException("采购状态操作包含超出授权范围的字段修改。");
            }
        }
    }

    private static void requireReturnedOwner(OperationContext context, SuperVO returned) throws BusinessException {
        Object group = returned.getAttributeValue("pk_group");
        Object org = returned.getAttributeValue("pk_org");
        if (group != null && !group.equals(context.getGroupId()) || org != null && !org.equals(context.getOrgId())) {
            throw new BusinessException("采购状态操作返回的单据集团或业务单元不匹配。");
        }
    }

    private static ProcurementStatusRoute requireRoute(OperationContext context) throws BusinessException {
        if (context == null || !ProcurementStatusRoute.validKey(context.getGroupId())
                || !ProcurementStatusRoute.validKey(context.getOrgId())) {
            throw new BusinessException("采购状态操作缺少已授权的集团或业务单元。");
        }
        ProcurementStatusRoute route = ProcurementStatusRoute.forAction(context.getNodeCode(), context.getButtonCode());
        if (!route.operationType().equals(context.getOperationType())) {
            throw new BusinessException("采购状态操作类型与按钮授权不匹配。");
        }
        return route;
    }

    private static Map<String, BusinessObjectRef> requireRefs(ProcurementStatusRoute route, OperationContext context)
            throws BusinessException {
        Map<String, BusinessObjectRef> refs = new LinkedHashMap<String, BusinessObjectRef>();
        for (BusinessObjectRef ref : context.getObjects()) {
            if (ref == null || !route.objectType().equals(ref.getObjectType()) || ref.getParentClientObjectId() != null
                    || !ProcurementStatusRoute.validKey(ref.getObjectPk())
                    || !ProcurementStatusRoute.validKey(ref.getClientObjectId())
                    || refs.put(ref.getObjectPk(), ref) != null) {
                throw new BusinessException("采购状态操作对象与节点授权不匹配。");
            }
        }
        if (refs.isEmpty()) {
            throw new BusinessException("采购状态操作缺少已授权单据。");
        }
        return refs;
    }

    private static void requireState(ProcurementStatusRoute route, SuperVO persisted) throws BusinessException {
        String field;
        Object expected;
        switch (route) {
            case STORE_BILL_CLOSE:
            case PRAY_BILL_CLOSE: field = "fbillstatus"; expected = CLOSED; break;
            case STORE_BILL_OPEN:
            case PRAY_BILL_OPEN: field = "fbillstatus"; expected = APPROVED; break;
            case STORE_LINE_CLOSE: field = "bclose"; expected = UFBoolean.TRUE; break;
            case STORE_LINE_OPEN: field = "bclose"; expected = UFBoolean.FALSE; break;
            case PRAY_LINE_CLOSE: field = "browclose"; expected = UFBoolean.TRUE; break;
            case PRAY_LINE_OPEN: field = "browclose"; expected = UFBoolean.FALSE; break;
            case ORDER_BILL_CLOSE: field = "bfinalclose"; expected = UFBoolean.TRUE; break;
            case ORDER_BILL_OPEN: field = "bfinalclose"; expected = UFBoolean.FALSE; break;
            case ORDER_LINE_CLOSE: field = "fisactive"; expected = ORDER_ROW_CLOSED; break;
            case ORDER_LINE_OPEN: field = "fisactive"; expected = ORDER_ROW_ACTIVE; break;
            case ORDER_FREEZE:
            case INVOICE_FREEZE: field = "bfrozen"; expected = UFBoolean.TRUE; break;
            case ORDER_UNFREEZE:
            case INVOICE_UNFREEZE: field = "bfrozen"; expected = UFBoolean.FALSE; break;
            default: throw new IllegalStateException(route.name());
        }
        if (!expected.equals(persisted.getAttributeValue(field))) {
            throw new BusinessException("采购状态操作后未取得已授权的目标状态。");
        }
    }

    private static Serializable dispatch(ProcurementStatusRoute route, Object service, Object[] args)
            throws BusinessException {
        switch (route) {
            case STORE_BILL_CLOSE: return ((IStoreReqAppClose) service).billClose((StoreReqAppVO[]) args[0]);
            case STORE_BILL_OPEN: return ((IStoreReqAppClose) service).billOpen((StoreReqAppVO[]) args[0]);
            case STORE_LINE_CLOSE: return ((IStoreReqAppClose) service).rowClose((StoreReqAppVO[]) args[0]);
            case STORE_LINE_OPEN: return ((IStoreReqAppClose) service).rowOpen((StoreReqAppVO[]) args[0]);
            case PRAY_BILL_CLOSE: return ((IPraybillMaintain) service).closeBill((PraybillVO[]) args[0]);
            case PRAY_BILL_OPEN: return ((IPraybillMaintain) service).openBill((PraybillVO[]) args[0]);
            case PRAY_LINE_CLOSE: return ((IPraybillMaintain) service).closeBillRow((PraybillVO[]) args[0]);
            case PRAY_LINE_OPEN: return ((IPraybillMaintain) service).openBillRow((PraybillVO[]) args[0]);
            case ORDER_BILL_CLOSE:
            case ORDER_LINE_CLOSE: return ((IOrderClose) service).close((OrderVO[]) args[0], (Integer) args[1], (Boolean) args[2]);
            case ORDER_BILL_OPEN:
            case ORDER_LINE_OPEN: return ((IOrderClose) service).open((OrderVO[]) args[0], (Integer) args[1], (Boolean) args[2]);
            case ORDER_FREEZE: return ((IOrderMaintain) service).freezeOrder((OrderVO[]) args[0], (String) args[1]);
            case ORDER_UNFREEZE: return ((IOrderMaintain) service).unfreezeOrder((OrderVO[]) args[0]);
            case INVOICE_FREEZE: return ((IInvoiceMaintain) service).freezeByLightVOs((InvoiceVO[]) args[0]);
            case INVOICE_UNFREEZE: return ((IInvoiceMaintain) service).unFreezeByLightVOs((InvoiceVO[]) args[0]);
            default: throw new IllegalStateException(route.name());
        }
    }
}
