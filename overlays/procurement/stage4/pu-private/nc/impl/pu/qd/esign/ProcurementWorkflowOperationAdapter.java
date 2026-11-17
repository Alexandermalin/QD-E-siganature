package nc.impl.pu.qd.esign;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import nc.bs.dao.BaseDAO;
import nc.bs.framework.common.InvocationInfoProxy;
import nc.bs.framework.common.NCLocator;
import nc.itf.pu.qd.esign.IProcurementWorkflowOperationAdapter;
import nc.itf.uap.pf.IWorkflowMachine;
import nc.itf.uap.pf.IplatFormEntry;
import nc.jdbc.framework.SQLParameter;
import nc.vo.jcom.lang.StringUtil;
import nc.vo.pf.change.PfUtilBaseTools;
import nc.vo.pu.m21.context.OrderContext;
import nc.vo.pu.m25.env.InvoiceUIToBSEnv;
import nc.vo.pu.qd.esign.ProcurementWorkflowCommand;
import nc.vo.pu.qd.esign.ProcurementWorkflowRoute;
import nc.vo.pu.qd.esign.ProcurementWorkflowRoute.Action;
import nc.vo.pub.BusinessException;
import nc.vo.pub.ISuperVO;
import nc.vo.pub.IVOMeta;
import nc.vo.pub.SuperVO;
import nc.vo.pub.VOStatus;
import nc.vo.pub.pf.WfTaskInfo;
import nc.vo.pub.workflownote.WorkflownoteVO;
import nc.vo.pubapp.pattern.model.entity.bill.AbstractBill;
import nc.vo.pubapp.pflow.PFReturnObject;
import nc.vo.pubapp.pflow.PfUserObject;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.wfengine.pub.WFTask;
import nc.vo.wfengine.pub.WfTaskOrInstanceStatus;

import static nc.vo.pu.qd.esign.ProcurementWorkflowRoute.field;
import static nc.vo.pu.qd.esign.ProcurementWorkflowRoute.validKey;

public final class ProcurementWorkflowOperationAdapter implements IProcurementWorkflowOperationAdapter {
    private static final String GROUP = "pk_group";
    private static final String ORG = "pk_org";
    private static final String BILL_CODE = "vbillcode";
    private static final String TRANSACTION_CODE = "vtrantypecode";
    private static final String TRANSACTION_ID = "ctrantypeid";
    // Persistent values of POEnumBillStatus.
    private static final int FREE = 0;
    private static final int COMMITTED = 1;
    private static final int APPROVING = 2;
    private static final int APPROVED = 3;
    private static final int REJECTED = 4;

    private final IplatFormEntry platform;
    private final HeaderLookup records;
    private final BillTypeLookup billTypes;
    private final TaskLookup tasks;

    public ProcurementWorkflowOperationAdapter() {
        this(null, new HeaderLookup() {
            public SuperVO find(Class<? extends SuperVO> type, String key, String pk) throws BusinessException {
                SQLParameter parameters = new SQLParameter();
                parameters.addParam(pk);
                Collection<?> matches = new BaseDAO().retrieveByClause(type,
                        key + " = ? and (dr = 0 or dr is null)", parameters);
                if (matches == null || matches.isEmpty()) return null;
                if (matches.size() != 1) throw new BusinessException("采购审批单据或明细的主键不唯一。");
                return type.cast(matches.iterator().next());
            }
        }, new BillTypeLookup() {
            public String baseType(String type) {
                return PfUtilBaseTools.getRealBilltype(type);
            }
        });
    }

    ProcurementWorkflowOperationAdapter(IplatFormEntry platform, HeaderLookup records, BillTypeLookup billTypes) {
        this(platform, records, billTypes, new TaskLookup() {
            public WFTask find(String pk) throws BusinessException {
                return NCLocator.getInstance().lookup(IWorkflowMachine.class).getWFTask(pk);
            }
        });
    }

    ProcurementWorkflowOperationAdapter(IplatFormEntry platform, HeaderLookup records, BillTypeLookup billTypes,
            TaskLookup tasks) {
        this.platform = platform;
        this.records = records;
        this.billTypes = billTypes;
        this.tasks = tasks;
    }

    interface HeaderLookup {
        SuperVO find(Class<? extends SuperVO> type, String key, String pk) throws BusinessException;
    }

    interface BillTypeLookup {
        String baseType(String type);
    }

    interface TaskLookup {
        WFTask find(String pk) throws BusinessException;
    }

    @Override
    public void validate(OperationContext context, Serializable command) throws BusinessException {
        ProcurementWorkflowRoute route = route(context);
        requireCommand(route, context, command);
    }

    @Override
    public Serializable execute(OperationContext context, Serializable command) throws BusinessException {
        ProcurementWorkflowRoute route = route(context);
        ProcurementWorkflowCommand pf = requireCommand(route, context, command);
        IplatFormEntry entry = platform == null ? NCLocator.getInstance().lookup(IplatFormEntry.class) : platform;
        // The native batch entry uses a separate transaction per bill. The single PF entry joins the audit CMT.
        Object result = entry.processAction(pf.getActionName(), pf.getBillType(), pf.getNote(), pf.getBill(),
                pf.getUserObject(), pf.getParameters());
        requireResult(route, context, result);
        return (Serializable) result;
    }

    @Override
    public List<BusinessObjectRef> describeResult(OperationContext context, Serializable result) throws BusinessException {
        ProcurementWorkflowRoute route = route(context);
        SuperVO persisted = requireResult(route, context, result);
        BusinessObjectRef ref = context.getObjects().get(0);
        return Collections.singletonList(new BusinessObjectRef(ref.getClientObjectId(), ref.getObjectType(),
                persisted.getPrimaryKey(), field(persisted, BILL_CODE), null, ref.getDesiredOwnerOrg(),
                ref.getPolicyOwnerOrg(), ref.getTargetOrg()));
    }

    private ProcurementWorkflowCommand requireCommand(ProcurementWorkflowRoute route, OperationContext context,
            Serializable command) throws BusinessException {
        if (!(command instanceof ProcurementWorkflowCommand)) {
            throw new BusinessException("采购审批需要对应的流程平台命令。");
        }
        ProcurementWorkflowCommand pf = (ProcurementWorkflowCommand) command;
        Action action = Action.forButton(context.getButtonCode());
        if (!matchesAction(action, pf.getActionName())) {
            throw new BusinessException("采购流程平台动作与已授权按钮不匹配。");
        }
        String pk = context.getObjects().get(0).getObjectPk();
        SuperVO header = route.header(pf.getBill());
        if (!pk.equals(header.getPrimaryKey())) {
            throw new BusinessException("采购审批单据与授权主键不匹配。");
        }
        SuperVO persisted = requireHeader(route, context, pk);
        requireUnchanged(header, persisted);
        requireChildren(route, pf.getBill(), pk);
        if (!nativeBillType(route, persisted).equals(pf.getBillType())) {
            throw new BusinessException("采购流程平台单据类型与已授权的单据不匹配。");
        }
        requireNote(route, context, pf, pf.getNote(), persisted);
        requireUserObject(route, action, pf.getUserObject());
        requireParameters(route, pf);
        return pf;
    }

    private static ProcurementWorkflowRoute route(OperationContext context) throws BusinessException {
        if (context == null || !validKey(context.getGroupId()) || !validKey(context.getOrgId())
                || context.getObjects().size() != 1) {
            throw new BusinessException("采购审批操作须对应一张已授权的单据。");
        }
        ProcurementWorkflowRoute route = ProcurementWorkflowRoute.forNode(context.getNodeCode());
        Action action = Action.forButton(context.getButtonCode());
        BusinessObjectRef ref = context.getObjects().get(0);
        if (!action.name().equals(context.getOperationType()) || ref == null
                || !route.objectType().equals(ref.getObjectType()) || ref.getParentClientObjectId() != null
                || !validKey(ref.getObjectPk()) || !ref.getObjectPk().equals(ref.getClientObjectId())) {
            throw new BusinessException("采购审批操作与已授权的节点、按钮或单据不匹配。");
        }
        return route;
    }

    private static boolean matchesAction(Action action, String wireAction) {
        if (action.pfAction().equals(wireAction)) return true;
        if ((action != Action.APPROVE && action != Action.UNAPPROVE)
                || wireAction == null || !wireAction.startsWith(action.pfAction())) return false;
        String loginUser = InvocationInfoProxy.getInstance().getUserId();
        return validKey(loginUser) && loginUser.length() == 20 && wireAction.equals(action.pfAction() + loginUser);
    }

    private SuperVO requireHeader(ProcurementWorkflowRoute route, OperationContext context, String pk)
            throws BusinessException {
        SuperVO persisted = records.find(route.headerClass(), route.headerKey(), pk);
        if (persisted == null || persisted.getClass() != route.headerClass() || !pk.equals(persisted.getPrimaryKey())
                || !context.getGroupId().equals(field(persisted, GROUP))
                || !context.getOrgId().equals(field(persisted, ORG))) {
            throw new BusinessException("找不到属于已授权集团及业务单元的采购单据。");
        }
        return persisted;
    }

    private void requireChildren(ProcurementWorkflowRoute route, AbstractBill bill, String pk) throws BusinessException {
        for (IVOMeta meta : bill.getMetaData().getChildren()) {
            ISuperVO[] children = bill.getChildren(meta);
            if (children == null || children.length == 0) continue;
            Set<String> keys = new HashSet<String>();
            for (ISuperVO child : children) {
                if (!(child instanceof SuperVO) || !route.isChildClass(child.getClass())
                        || !validKey(child.getPrimaryKey()) || !keys.add(child.getPrimaryKey())) {
                    throw new BusinessException("采购审批单据的明细类型或主键不匹配。");
                }
                SuperVO row = (SuperVO) child;
                SuperVO persisted = records.find(row.getClass(), meta.getPrimaryAttribute().getName(), row.getPrimaryKey());
                if (persisted == null || !row.getPrimaryKey().equals(persisted.getPrimaryKey())
                        || !pk.equals(field(persisted, route.headerKey()))) {
                    throw new BusinessException("采购审批明细不属于已授权的单据。");
                }
                requireUnchanged(row, persisted);
            }
        }
    }

    private static void requireUnchanged(SuperVO submitted, SuperVO persisted) throws BusinessException {
        // The native light-VO builder can mark static attributes UPDATED even when their values are unchanged.
        int status = submitted.getStatus();
        if ((status != VOStatus.UNCHANGED && status != VOStatus.UPDATED)
                || !Objects.equals(submitted.getAttributeValue("ts"), persisted.getAttributeValue("ts"))) {
            throw new BusinessException("采购审批单据或明细已变化，请刷新后重试。");
        }
        for (String field : submitted.usedAttributeNames()) {
            if (!Objects.equals(persisted.getAttributeValue(field), submitted.getAttributeValue(field))) {
                throw new BusinessException("采购审批操作包含超出授权范围的字段修改。");
            }
        }
    }

    private String nativeBillType(ProcurementWorkflowRoute route, SuperVO header) throws BusinessException {
        String type = field(header, TRANSACTION_CODE);
        if (!validKey(type) || route.billType().equals(type) || StringUtil.isContainChinese(type)) {
            return route.billType();
        }
        if (route.billType().equals(billTypes.baseType(type))) return type;
        throw new BusinessException("采购单据交易类型超出已授权的范围。");
    }

    private void requireNote(ProcurementWorkflowRoute route, OperationContext context,
            ProcurementWorkflowCommand pf, WorkflownoteVO note, SuperVO persisted) throws BusinessException {
        if (note == null) return;
        String pk = persisted.getPrimaryKey();
        String transactionId = field(persisted, TRANSACTION_ID);
        if (note.getClass() != WorkflownoteVO.class || !matchesWorkflowValue(note.getBillid(), pk)
                || !matchesWorkflowValue(note.getPk_group(), context.getGroupId())
                || !matchesWorkflowValue(note.getPk_org(), context.getOrgId())
                || !noteType(route, pf, transactionId, note.getPk_billtype())) {
            throw new BusinessException("采购审批工作流意见与已授权的单据不匹配。");
        }
        WfTaskInfo info = note.getTaskInfo();
        WFTask task = info == null ? null : info.getTask();
        if (info != null && info.getClass() != WfTaskInfo.class
                || task != null && (task.getClass() != WFTask.class || !matchesWorkflowValue(task.getBillID(), pk)
                    || !matchesWorkflowValue(task.getPk_org(), context.getOrgId())
                    || !noteType(route, pf, transactionId, task.getBillType()))) {
            throw new BusinessException("采购审批工作流任务与已授权的单据不匹配。");
        }
        requireWorkItem(route, context, pf, note, task, persisted);
    }

    private void requireWorkItem(ProcurementWorkflowRoute route, OperationContext context,
            ProcurementWorkflowCommand pf, WorkflownoteVO note, WFTask task, SuperVO header) throws BusinessException {
        String login = InvocationInfoProxy.getInstance().getUserId();
        String itemPk = note.getPk_checkflow();
        String noteTaskPk = note.getPk_wf_task();
        if (!matchesWorkflowValue(note.getCheckman(), login)) {
            throw new BusinessException("采购审批意见的处理人与当前用户不匹配。");
        }
        if (!present(itemPk)) {
            if (present(noteTaskPk) || present(note.getAgencyuser())
                    || task != null && (Action.forButton(context.getButtonCode()) != Action.COMMIT
                        || present(task.getWfProcessInstancePK())
                        || present(task.getActivityInstancePK()) || present(task.getApproveAgent())
                        || !matchesWorkflowValue(task.getOperator(), login)
                        || task.getWorknoteVO() != null && task.getWorknoteVO() != note)) {
                throw new BusinessException("采购审批工作流任务缺少对应的有效工作项。");
            }
            // NC checkWorkFlow prepares a new task PK before a work item is persisted.
            // A prepared COMMIT may keep that PK, but must never reuse a stored task.
            if (task != null && present(task.getTaskPK())
                    && (!validKey(task.getTaskPK()) || tasks.find(task.getTaskPK()) != null)) {
                throw new BusinessException("采购首次提交任务已存在或主键无效，请刷新后重试。");
            }
            return;
        }
        if (!validKey(itemPk) || !validKey(login)) {
            throw new BusinessException("采购审批工作流工作项或处理人无效。");
        }
        WorkflownoteVO current = (WorkflownoteVO) records.find(WorkflownoteVO.class, "pk_checkflow", itemPk);
        if (current == null || !itemPk.equals(current.getPk_checkflow()) || !login.equals(current.getCheckman())
                || !header.getPrimaryKey().equals(current.getBillid()) || !"N".equals(current.getIsmsgbind())
                || "BIZ".equals(current.getActiontype()) || !unfinished(current.getApprovestatus())
                || !matchesWorkflowValue(current.getPk_group(), context.getGroupId())
                || !matchesWorkflowValue(current.getPk_org(), context.getOrgId())
                || !noteType(route, pf, field(header, TRANSACTION_ID), current.getPk_billtype())
                || !matchesWorkflowValue(noteTaskPk, current.getPk_wf_task())
                || !matchesWorkflowValue(note.getAgencyuser(), current.getAgencyuser())
                || !matchesWorkflowValue(note.getSenderman(), current.getSenderman())
                || !matchesWorkflowValue(note.getBillVersionPK(), current.getBillVersionPK())) {
            throw new BusinessException("采购审批工作流工作项与已授权单据或当前用户不匹配。");
        }
        String taskPk = current.getPk_wf_task();
        WFTask nativeTask = present(taskPk) && validKey(taskPk) ? tasks.find(taskPk) : null;
        if (nativeTask == null || !taskPk.equals(nativeTask.getTaskPK()) || !unfinished(nativeTask.getStatus())
                || task != null && !matchesTask(task, nativeTask, current)) {
            throw new BusinessException("采购审批工作流任务与已授权工作项不匹配。");
        }
    }

    private static boolean matchesTask(WFTask task, WFTask nativeTask, WorkflownoteVO current) {
        WorkflownoteVO nested = task.getWorknoteVO();
        return matchesWorkflowValue(task.getTaskPK(), nativeTask.getTaskPK())
                && matchesWorkflowValue(task.getWfProcessDefPK(), nativeTask.getWfProcessDefPK())
                && matchesWorkflowValue(task.getWfProcessInstancePK(), nativeTask.getWfProcessInstancePK())
                && matchesWorkflowValue(task.getActivityInstancePK(), nativeTask.getActivityInstancePK())
                && matchesWorkflowValue(task.getBillversionPK(), current.getBillVersionPK())
                && matchesWorkflowValue(task.getOperator(), current.getCheckman())
                && matchesWorkflowValue(task.getApproveAgent(), current.getAgencyuser())
                && matchesWorkflowValue(task.getSenderman(), current.getSenderman())
                && (nested == null || nested.getClass() == WorkflownoteVO.class
                    && current.getPk_checkflow().equals(nested.getPk_checkflow())
                    && matchesWorkflowValue(nested.getBillid(), current.getBillid())
                    && matchesWorkflowValue(nested.getPk_wf_task(), current.getPk_wf_task())
                    && matchesWorkflowValue(nested.getCheckman(), current.getCheckman())
                    && matchesWorkflowValue(nested.getAgencyuser(), current.getAgencyuser()));
    }

    private static boolean unfinished(Integer state) {
        return state != null && (state.intValue() == WfTaskOrInstanceStatus.Started.getIntValue()
                || state.intValue() == WfTaskOrInstanceStatus.Suspended.getIntValue());
    }

    private static boolean matchesWorkflowValue(String submitted, String authoritative) {
        return !present(submitted) || submitted.equals(authoritative);
    }

    private static boolean present(String value) {
        return value != null && !value.isEmpty() && !"~".equals(value);
    }

    private static boolean noteType(ProcurementWorkflowRoute route, ProcurementWorkflowCommand pf,
            String transactionId, String value) {
        return matchesWorkflowValue(value, transactionId) || route.billType().equals(value) || pf.getBillType().equals(value);
    }

    private static boolean sameWhenPresent(String value, String expected) {
        return value == null || value.isEmpty() || value.equals(expected);
    }

    private static void requireUserObject(ProcurementWorkflowRoute route, Action action, Object raw)
            throws BusinessException {
        if (raw == null) return;
        if (raw instanceof PfUserObject[]) {
            PfUserObject[] users = (PfUserObject[]) raw;
            if (users.length == 1) {
                requireSingleUserObject(route, action, users[0]);
                return;
            }
            throw new BusinessException("采购审批流程平台业务参数超出当前单据范围。");
        }
        requireSingleUserObject(route, action, raw);
    }

    private static void requireSingleUserObject(ProcurementWorkflowRoute route, Action action, Object raw)
            throws BusinessException {
        if (raw == null) return;
        if (raw.getClass() != PfUserObject.class && raw.getClass() != InvoiceUIToBSEnv.class) {
            throw new BusinessException("采购审批流程平台业务参数类型不正确。");
        }
        PfUserObject user = (PfUserObject) raw;
        requireBusinessChecks(user);
        Object value = user.getUserObject();
        if (raw.getClass() == PfUserObject.class && value == null) return;
        if (route == ProcurementWorkflowRoute.ORDER && action == Action.COMMIT
                && raw.getClass() == PfUserObject.class && value != null && value.getClass() == OrderContext.class) {
            return;
        }
        Object environment = raw.getClass() == InvoiceUIToBSEnv.class ? raw : value;
        if (route != ProcurementWorkflowRoute.INVOICE || environment.getClass() != InvoiceUIToBSEnv.class) {
            throw new BusinessException("采购审批流程平台业务环境超出授权范围。");
        }
        InvoiceUIToBSEnv invoice = (InvoiceUIToBSEnv) environment;
        requireBusinessChecks(invoice);
        if (invoice.getUserObject() != null || invoice.getSttlInfoMap() != null || invoice.isAutoSettleSendAP()
                || invoice.getTrigger() != null && !action.pfAction().equals(invoice.getTrigger().name())) {
            throw new BusinessException("采购发票流程平台业务环境超出授权范围。");
        }
    }

    private static void requireBusinessChecks(PfUserObject user) throws BusinessException {
        Map<?, ?> checks = user.getBusinessCheckMap();
        if (checks == null) return;
        for (Map.Entry<?, ?> check : checks.entrySet()) {
            if (!(check.getKey() instanceof String) || !(check.getValue() instanceof Boolean)) {
                throw new BusinessException("采购审批流程平台业务确认参数类型不正确。");
            }
        }
    }

    private static void requireParameters(ProcurementWorkflowRoute route, ProcurementWorkflowCommand pf)
            throws BusinessException {
        Map<?, ?> parameters = pf.getParameters();
        if (parameters == null) return;
        for (Map.Entry<?, ?> parameter : parameters.entrySet()) {
            Object key = parameter.getKey();
            Object value = parameter.getValue();
            if ("notechecked".equals(key) && key.equals(value)
                    || "reload_vo".equals(key) && route.header(pf.getBill()).getPrimaryKey()
                        .equals(route.header(value).getPrimaryKey())
                    || "worknote".equals(key) && value == null) continue;
            throw new BusinessException("采购审批流程平台附加参数超出授权范围。");
        }
    }

    private SuperVO requireResult(ProcurementWorkflowRoute route, OperationContext context, Object result)
            throws BusinessException {
        Object returned = result;
        if (result instanceof Object[] && ((Object[]) result).length == 1) returned = ((Object[]) result)[0];
        if (returned != null && returned.getClass() == PFReturnObject.class) {
            Object[] bills = ((PFReturnObject) returned).getBills();
            returned = bills != null && bills.length == 1 ? bills[0] : null;
        }
        SuperVO header = route.header(returned);
        String pk = context.getObjects().get(0).getObjectPk();
        if (!pk.equals(header.getPrimaryKey()) || !sameWhenPresent(field(header, GROUP), context.getGroupId())
                || !sameWhenPresent(field(header, ORG), context.getOrgId())) {
            throw new BusinessException("采购审批结果与已授权单据不匹配。");
        }
        SuperVO persisted = requireHeader(route, context, pk);
        String statusField = route == ProcurementWorkflowRoute.ORDER ? "forderstatus" : "fbillstatus";
        Object status = persisted.getAttributeValue(statusField);
        Object returnedStatus = header.getAttributeValue(statusField);
        if (!(status instanceof Integer) || returnedStatus != null && !status.equals(returnedStatus)
                || !isOutcome(Action.forButton(context.getButtonCode()), ((Integer) status).intValue())) {
            throw new BusinessException("采购审批流程平台未返回对应的持久化状态。");
        }
        return persisted;
    }

    private static boolean isOutcome(Action action, int state) {
        switch (action) {
            case COMMIT: return state == COMMITTED || state == APPROVING || state == APPROVED;
            case UNCOMMIT: return state == FREE;
            case APPROVE: return state == FREE || state == APPROVING || state == APPROVED || state == REJECTED;
            case UNAPPROVE: return state == FREE || state == COMMITTED || state == APPROVING;
            default: throw new IllegalArgumentException(action.name());
        }
    }
}
