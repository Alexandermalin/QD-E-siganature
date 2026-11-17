package nc.impl.pu.qd.esign;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import nc.bs.dao.BaseDAO;
import nc.bs.framework.common.NCLocator;
import nc.itf.pu.qd.esign.IProcurementBillOperationAdapter;
import nc.itf.uap.pf.IplatFormEntry;
import nc.jdbc.framework.SQLParameter;
import nc.vo.jcom.lang.StringUtil;
import nc.vo.pf.change.PfUtilBaseTools;
import nc.vo.pu.m20.context.PraybillContext;
import nc.vo.pu.m20.entity.PraybillHeaderVO;
import nc.vo.pu.m20.entity.PraybillVO;
import nc.vo.pu.m25.entity.InvoiceHeaderVO;
import nc.vo.pu.m25.entity.InvoiceVO;
import nc.vo.pu.m25.env.InvoiceUIToBSEnv;
import nc.vo.pu.m422x.entity.StoreReqAppHeaderVO;
import nc.vo.pu.m422x.entity.StoreReqAppVO;
import nc.vo.pu.pf.PUPFParameter;
import nc.vo.pu.qd.esign.ProcurementBillPfCommand;
import nc.vo.pub.BusinessException;
import nc.vo.pub.ISuperVO;
import nc.vo.pub.SuperVO;
import nc.vo.pub.VOStatus;
import nc.vo.pub.pf.WfTaskInfo;
import nc.vo.pub.workflownote.WorkflownoteVO;
import nc.vo.pubapp.pattern.model.entity.bill.AbstractBill;
import nc.vo.pubapp.pflow.PfUserObject;
import nc.vo.uap.pf.PFRuntimeException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.wfengine.pub.WFTask;

public final class ProcurementBillOperationAdapter implements IProcurementBillOperationAdapter {
    private static final String GROUP = "pk_group";
    private static final String ORG = "pk_org";
    private static final String TRANSACTION_CODE = "vtrantypecode";
    private static final String TRANSACTION_ID = "ctrantypeid";
    private static final String BILL_CODE = "vbillcode";
    private static final String SAVE = "SAVEBASE";
    private static final String REVISE = "SAVEREVISE";
    private static final String DISCARD = "DISCARD";

    private final IplatFormEntry platform;
    private final HeaderLookup headers;
    private final BillTypeLookup billTypes;

    public ProcurementBillOperationAdapter() {
        this(null, new HeaderLookup() {
            public SuperVO find(Class<? extends SuperVO> header, String pkColumn, String pk) throws BusinessException {
                SQLParameter parameters = new SQLParameter();
                parameters.addParam(pk);
                Collection<?> matches = new BaseDAO().retrieveByClause(header,
                        pkColumn + " = ? and (dr = 0 or dr is null)", parameters);
                if (matches == null || matches.isEmpty()) {
                    return null;
                }
                if (matches.size() != 1) {
                    throw new BusinessException("采购单据表头主键不唯一。");
                }
                return header.cast(matches.iterator().next());
            }
        }, new BillTypeLookup() {
            public String baseType(String type) {
                return PfUtilBaseTools.getRealBilltype(type);
            }
        });
    }

    ProcurementBillOperationAdapter(IplatFormEntry platform, HeaderLookup headers, BillTypeLookup billTypes) {
        this.platform = platform;
        this.headers = headers;
        this.billTypes = billTypes;
    }

    interface HeaderLookup {
        SuperVO find(Class<? extends SuperVO> header, String pkColumn, String pk) throws BusinessException;
    }

    interface BillTypeLookup {
        String baseType(String type);
    }

    @Override
    public void validate(OperationContext context, Serializable command) throws BusinessException {
        Route route = Route.forContext(context);
        requireCommand(route, context, command);
    }

    @Override
    public Serializable execute(OperationContext context, Serializable command) throws BusinessException {
        Route route = Route.forContext(context);
        ProcurementBillPfCommand pf = requireCommand(route, context, command);
        IplatFormEntry nativeEntry = platform == null ? NCLocator.getInstance().lookup(IplatFormEntry.class) : platform;
        Object result = nativeEntry.processAction(pf.getActionName(), pf.getBillType(), pf.getNote(),
                pf.getBill(), pf.getUserObject(), pf.getParameters());
        if (isDelete(context)) {
            requireDeleted(route, ref(context).getObjectPk(), result);
            return null;
        }
        requireSaved(route, context, result);
        return (Serializable) result;
    }

    @Override
    public List<BusinessObjectRef> describeResult(OperationContext context, Serializable result) throws BusinessException {
        Route route = Route.forContext(context);
        BusinessObjectRef ref = ref(context);
        if (isDelete(context)) {
            requireDeleted(route, ref.getObjectPk(), result);
            return Collections.singletonList(ref);
        }
        SuperVO saved = requireSaved(route, context, result);
        return Collections.singletonList(new BusinessObjectRef(ref.getClientObjectId(), ref.getObjectType(),
                string(saved, route.pkColumn), string(saved, BILL_CODE), ref.getParentClientObjectId(),
                ref.getDesiredOwnerOrg(), ref.getPolicyOwnerOrg(), ref.getTargetOrg()));
    }

    private ProcurementBillPfCommand requireCommand(Route route, OperationContext context, Serializable command)
            throws BusinessException {
        if (!(command instanceof ProcurementBillPfCommand)) {
            throw new BusinessException("采购保存或删除需要对应的流程平台命令。");
        }
        ProcurementBillPfCommand pf = (ProcurementBillPfCommand) command;
        String action = pf.getActionName();
        boolean insert = isInsert(context);
        if (isDelete(context) ? !DISCARD.equals(action)
                : !SAVE.equals(action) && !(route == Route.PRAY && !insert && REVISE.equals(action))) {
            throw new BusinessException("采购流程平台动作与已授权的操作不匹配。");
        }

        SuperVO header = route.headerOf(pf.getBill());
        String pk = string(header, route.pkColumn);
        if (insert && blank(pk)) {
            pk = null;
        }
        if ((pk != null && !validKey(pk)) || (!insert && !ref(context).getObjectPk().equals(pk))) {
            throw new BusinessException("采购单据主键与已授权的单据不匹配。");
        }
        int status = header.getStatus();
        if (insert ? status != VOStatus.NEW
                : status != VOStatus.UNCHANGED && status != VOStatus.UPDATED
                    && !(isDelete(context) && status == VOStatus.DELETED)) {
            throw new BusinessException("采购单据状态与已授权的操作不匹配。");
        }

        SuperVO authoritative = header;
        if (insert) {
            requireOwner(context, header, null);
            if (pk != null && find(route, pk) != null) {
                throw new BusinessException("新增采购单据使用了已有单据主键。");
            }
        } else {
            authoritative = requirePersisted(route, context, pk);
            requireCompatibleHeader(context, header, authoritative);
        }
        String type = nativeType(route, authoritative);
        if (!type.equals(pf.getBillType())) {
            throw new BusinessException("采购流程平台单据类型与已授权的单据不匹配。");
        }
        requireNativeArguments(route, context, pf, authoritative, pk);
        return pf;
    }

    private SuperVO requireSaved(Route route, OperationContext context, Object result) throws BusinessException {
        if (!(result instanceof AbstractBill[]) || result.getClass().getComponentType() != route.billClass
                || ((AbstractBill[]) result).length != 1) {
            throw new BusinessException("采购保存须返回一张对应类型的已保存单据。");
        }
        SuperVO returned = route.headerOf(((AbstractBill[]) result)[0]);
        String pk = string(returned, route.pkColumn);
        if (!validKey(pk) || !isInsert(context) && !ref(context).getObjectPk().equals(pk)) {
            throw new BusinessException("采购保存返回的单据主键与已授权的单据不匹配。");
        }
        SuperVO saved = requirePersisted(route, context, pk);
        requireCompatibleHeader(context, returned, saved);
        nativeType(route, saved);
        if (blank(string(saved, BILL_CODE))) {
            throw new BusinessException("采购保存未取得已持久化的单据号。");
        }
        return saved;
    }

    private void requireDeleted(Route route, String pk, Object result) throws BusinessException {
        if (result != null || find(route, pk) != null) {
            throw new BusinessException("采购删除后仍可找到有效单据，或流程平台返回了意外结果。");
        }
    }

    private SuperVO requirePersisted(Route route, OperationContext context, String pk) throws BusinessException {
        SuperVO persisted = find(route, pk);
        if (persisted == null || persisted.getClass() != route.headerClass || !pk.equals(string(persisted, route.pkColumn))) {
            throw new BusinessException("找不到已授权的采购单据。");
        }
        requireOwner(context, persisted, null);
        return persisted;
    }

    private SuperVO find(Route route, String pk) throws BusinessException {
        return headers.find(route.headerClass, route.pkColumn, pk);
    }

    private static void requireCompatibleHeader(OperationContext context, SuperVO submitted, SuperVO persisted)
            throws BusinessException {
        // A light VO omits unchanged fields. A supplied null still represents a change.
        Set<String> fields = submitted.usedAttributeNames();
        requireOwner(context, submitted, fields);
        if (!matches(submitted, fields, TRANSACTION_CODE, string(persisted, TRANSACTION_CODE))
                || !matches(submitted, fields, TRANSACTION_ID, string(persisted, TRANSACTION_ID))) {
            throw new BusinessException("采购单据交易类型与已授权的单据不匹配。");
        }
    }

    private static void requireOwner(OperationContext context, SuperVO header, Set<String> fields)
            throws BusinessException {
        Object dr = header.getAttributeValue("dr");
        if (!matches(header, fields, GROUP, context.getGroupId()) || !matches(header, fields, ORG, context.getOrgId())
                || dr != null && !Integer.valueOf(0).equals(dr)) {
            throw new BusinessException("采购单据不属于已授权的集团和业务单元。");
        }
    }

    private static boolean matches(SuperVO header, Set<String> fields, String field, String expected) {
        Object value = header.getAttributeValue(field);
        return Objects.equals(expected, value) || fields != null && value == null && !fields.contains(field);
    }

    private String nativeType(Route route, SuperVO header) throws BusinessException {
        String code = string(header, TRANSACTION_CODE);
        if (blank(code) || StringUtil.isContainChinese(code) || route.billType.equals(code)) {
            return route.billType;
        }
        try {
            if (code.equals(code.trim()) && route.billType.equals(billTypes.baseType(code))) {
                return code;
            }
        } catch (PFRuntimeException ex) {
            throw new BusinessException("无法解析采购流程平台交易类型。", ex);
        }
        throw new BusinessException("采购流程平台交易类型超出已授权的单据范围。");
    }

    private static void requireNativeArguments(Route route, OperationContext context, ProcurementBillPfCommand pf,
            SuperVO header, String pk) throws BusinessException {
        requireNote(route, context, pf, header, pk);
        requireUserObject(route, pf.getActionName(), pf.getUserObject());
        HashMap<?, ?> parameters = pf.getParameters();
        if (parameters == null) {
            return;
        }
        for (Map.Entry<?, ?> parameter : parameters.entrySet()) {
            Object name = parameter.getKey();
            Object value = parameter.getValue();
            if ("notechecked".equals(name) && "notechecked".equals(value)) {
                continue;
            }
            if ("reload_vo".equals(name) && !isInsert(context) && value == pf.getBill()) {
                continue;
            }
            throw new BusinessException("采购流程平台参数超出已授权的操作范围。");
        }
    }

    private static void requireNote(Route route, OperationContext context, ProcurementBillPfCommand pf,
            SuperVO header, String pk) throws BusinessException {
        WorkflownoteVO note = pf.getNote();
        if (note == null) {
            return;
        }
        String transactionId = string(header, TRANSACTION_ID);
        if (note.getClass() != WorkflownoteVO.class || !matchesWhenPresent(note.getBillid(), pk)
                || !matchesWhenPresent(note.getPk_group(), context.getGroupId())
                || !matchesWhenPresent(note.getPk_org(), context.getOrgId())
                || !matchesNoteType(note.getPk_billtype(), route, pf, transactionId)) {
            throw new BusinessException("采购流程平台工作流单据与已授权的单据不匹配。");
        }
        WfTaskInfo taskInfo = note.getTaskInfo();
        if (taskInfo == null) {
            return;
        }
        if (taskInfo.getClass() != WfTaskInfo.class) {
            throw new BusinessException("采购流程平台工作流任务信息类型不正确。");
        }
        WFTask task = taskInfo.getTask();
        if (task != null && (task.getClass() != WFTask.class || !matchesWhenPresent(task.getBillID(), pk)
                || !matchesWhenPresent(task.getPk_org(), context.getOrgId())
                || !matchesNoteType(task.getBillType(), route, pf, transactionId))) {
            throw new BusinessException("采购流程平台工作流任务与已授权的单据不匹配。");
        }
    }

    private static boolean matchesNoteType(String type, Route route, ProcurementBillPfCommand pf, String transactionId) {
        return blank(type) || route.billType.equals(type) || pf.getBillType().equals(type) || type.equals(transactionId);
    }

    private static boolean matchesWhenPresent(String value, String expected) {
        return blank(value) || value.equals(expected);
    }

    private static void requireUserObject(Route route, String action, Object value) throws BusinessException {
        if (value == null) {
            return;
        }
        Class<?> type = value.getClass();
        boolean storeSave = route == Route.STORE && SAVE.equals(action);
        if (storeSave ? type != PUPFParameter.class
                : type != PfUserObject.class && !(route == Route.INVOICE && type == InvoiceUIToBSEnv.class)) {
            throw new BusinessException("采购流程平台业务参数类型不正确。");
        }
        PfUserObject user = (PfUserObject) value;
        requireBusinessChecks(user);
        Object environment = user.getUserObject();
        if (route == Route.INVOICE) {
            if (type == InvoiceUIToBSEnv.class && environment == null) {
                requireInvoiceEnvironment(action, (InvoiceUIToBSEnv) value);
                return;
            }
            if (type == PfUserObject.class && environment != null && environment.getClass() == InvoiceUIToBSEnv.class) {
                InvoiceUIToBSEnv invoice = (InvoiceUIToBSEnv) environment;
                requireBusinessChecks(invoice);
                requireInvoiceEnvironment(action, invoice);
                return;
            }
        } else if (route == Route.PRAY && REVISE.equals(action) && environment != null
                && environment.getClass() == PraybillContext.class) {
            return;
        }
        if (environment != null) {
            throw new BusinessException("采购流程平台业务环境类型不正确。");
        }
    }

    private static void requireInvoiceEnvironment(String action, InvoiceUIToBSEnv invoice) throws BusinessException {
        if (invoice.getUserObject() != null || invoice.getSttlInfoMap() != null || invoice.isAutoSettleSendAP()
                || invoice.getTrigger() != null && !action.equals(invoice.getTrigger().name())) {
            throw new BusinessException("采购发票流程平台业务环境超出已授权的操作范围。");
        }
    }

    private static void requireBusinessChecks(PfUserObject user) throws BusinessException {
        Map<?, ?> checks = user.getBusinessCheckMap();
        if (checks == null) {
            return;
        }
        for (Map.Entry<?, ?> check : checks.entrySet()) {
            if (!(check.getKey() instanceof String) || !(check.getValue() instanceof Boolean)) {
                throw new BusinessException("采购流程平台业务确认参数类型不正确。");
            }
        }
    }

    private static boolean isInsert(OperationContext context) {
        return "INSERT".equals(context.getOperationType());
    }

    private static boolean isDelete(OperationContext context) {
        return "DELETE".equals(context.getOperationType());
    }

    private static BusinessObjectRef ref(OperationContext context) {
        return context.getObjects().get(0);
    }

    private static boolean validKey(String key) {
        return !blank(key) && key.equals(key.trim());
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String string(SuperVO header, String field) {
        return (String) header.getAttributeValue(field);
    }

    private enum Route {
        STORE("40040000", "422X", "PU_STORE_REQ", "pk_storereq", StoreReqAppVO.class, StoreReqAppHeaderVO.class),
        PRAY("40040200", "20", "PU_PRAYBILL", "pk_praybill", PraybillVO.class, PraybillHeaderVO.class),
        INVOICE("40041000", "25", "PU_PURCHASE_INVOICE", "pk_invoice", InvoiceVO.class, InvoiceHeaderVO.class);

        private final String node;
        private final String billType;
        private final String objectType;
        private final String pkColumn;
        private final Class<? extends AbstractBill> billClass;
        private final Class<? extends SuperVO> headerClass;

        Route(String node, String billType, String objectType, String pkColumn,
                Class<? extends AbstractBill> billClass, Class<? extends SuperVO> headerClass) {
            this.node = node;
            this.billType = billType;
            this.objectType = objectType;
            this.pkColumn = pkColumn;
            this.billClass = billClass;
            this.headerClass = headerClass;
        }

        static Route forContext(OperationContext context) throws BusinessException {
            if (context == null || !validKey(context.getGroupId()) || !validKey(context.getOrgId())
                    || context.getObjects() == null || context.getObjects().size() != 1
                    || (isDelete(context) ? !"Delete".equals(context.getButtonCode())
                        : !"Save".equals(context.getButtonCode())
                            || !isInsert(context) && !"UPDATE".equals(context.getOperationType()))) {
                throw new BusinessException("采购流程平台操作须对应一张已授权保存或删除的单据。");
            }
            BusinessObjectRef ref = ref(context);
            for (Route route : values()) {
                if (route.node.equals(context.getNodeCode()) && ref != null && route.objectType.equals(ref.getObjectType())
                        && !blank(ref.getClientObjectId()) && ref.getParentClientObjectId() == null
                        && (isInsert(context) ? ref.getObjectPk() == null : validKey(ref.getObjectPk()))) {
                    return route;
                }
            }
            throw new BusinessException("采购节点和单据对象类型与授权范围不匹配。");
        }

        SuperVO headerOf(AbstractBill bill) throws BusinessException {
            if (bill == null || bill.getClass() != billClass) {
                throw new BusinessException("采购单据类型与授权范围不匹配。");
            }
            ISuperVO header = bill.getParent();
            if (header == null || header.getClass() != headerClass) {
                throw new BusinessException("采购单据表头类型与授权范围不匹配。");
            }
            return headerClass.cast(header);
        }
    }
}
