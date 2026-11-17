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
import nc.itf.pu.qd.esign.IPurchaseOrderOperationAdapter;
import nc.itf.uap.pf.IplatFormEntry;
import nc.jdbc.framework.SQLParameter;
import nc.vo.jcom.lang.StringUtil;
import nc.vo.pf.change.PfUtilBaseTools;
import nc.vo.pu.m21.context.OrderContext;
import nc.vo.pu.m21.entity.OrderHeaderVO;
import nc.vo.pu.m21.entity.OrderVO;
import nc.vo.pu.qd.esign.PurchaseOrderOperationCommand;
import nc.vo.pu.qd.esign.PurchaseOrderPfCommand;
import nc.vo.pub.BusinessException;
import nc.vo.pub.ISuperVO;
import nc.vo.pub.VOStatus;
import nc.vo.pub.pf.WfTaskInfo;
import nc.vo.pub.workflownote.WorkflownoteVO;
import nc.vo.pubapp.pflow.PfUserObject;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uap.pf.PFRuntimeException;
import nc.vo.wfengine.pub.WFTask;

public final class PurchaseOrderPilotAdapter implements IPurchaseOrderOperationAdapter {
    private static final String NODE = "40040400";
    private static final String BILL_TYPE = "21";

    private final PurchaseOrderOperationAdapter legacy = new PurchaseOrderOperationAdapter();
    private final IplatFormEntry platform;
    private final OrderHeaderLookup headers;

    public PurchaseOrderPilotAdapter() {
        this(null, new OrderHeaderLookup() {
            public OrderHeaderVO find(String pk) throws BusinessException {
                SQLParameter parameters = new SQLParameter();
                parameters.addParam(pk);
                Collection<?> matches = new BaseDAO().retrieveByClause(OrderHeaderVO.class,
                        "pk_order = ?", parameters);
                if (matches == null || matches.isEmpty()) {
                    return null;
                }
                if (matches.size() != 1) {
                    throw new BusinessException("Purchase-order primary key is not unique");
                }
                return (OrderHeaderVO) matches.iterator().next();
            }
        });
    }

    PurchaseOrderPilotAdapter(IplatFormEntry platform, OrderHeaderLookup headers) {
        this.platform = platform;
        this.headers = headers;
    }

    interface OrderHeaderLookup {
        OrderHeaderVO find(String pk) throws BusinessException;
    }

    @Override
    public void validate(OperationContext context, Serializable command) throws BusinessException {
        if (isDelete(context) && command instanceof PurchaseOrderOperationCommand) {
            legacy.validate(context, command);
            return;
        }
        BusinessObjectRef ref = requireSaveContext(context);
        if (!(command instanceof PurchaseOrderPfCommand)) {
            throw new BusinessException("A purchase-order PF Save command is required");
        }
        PurchaseOrderPfCommand pf = (PurchaseOrderPfCommand) command;
        OrderHeaderVO header = requireHeader(pf.getOrder());
        boolean insert = isInsert(context);
        boolean revise = "REVISE".equals(pf.getActionName());
        if (!"SAVEBASE".equals(pf.getActionName()) && (!revise || insert)) {
            throw new BusinessException("The PF action does not match purchase-order Save");
        }
        int status = header.getStatus();
        if ((insert && status != VOStatus.NEW) || (!insert && status != VOStatus.UPDATED
                && status != VOStatus.UNCHANGED)) {
            throw new BusinessException("The order status does not match the authorized operation");
        }
        String pk = present(header.getPk_order());
        if ((pk != null && !pk.equals(header.getPk_order()))
                || (!insert && !ref.getObjectPk().equals(pk))) {
            throw new BusinessException("The order identity does not match the authorized operation");
        }
        OrderHeaderVO authoritative = header;
        if (insert) {
            requireOwner(context, header, null);
            if (pk != null && headers.find(pk) != null) {
                throw new BusinessException("Purchase-order Insert requires an unused primary key");
            }
        } else {
            authoritative = requirePersistedOrder(context, pk);
            // A light update may omit unchanged values, while an explicit null can be a change.
            Set<String> fields = header.usedAttributeNames();
            requireOwner(context, header, fields);
            if (!matchesHeaderField(header, fields, OrderHeaderVO.VTRANTYPECODE, authoritative.getVtrantypecode())
                    || !matchesHeaderField(header, fields, OrderHeaderVO.CTRANTYPEID,
                            authoritative.getCtrantypeid())) {
                throw new BusinessException("The order transaction type differs from the authorized purchase order");
            }
        }
        requireNativeType(pf.getBillType(), authoritative);
        requireNativeArguments(pf, authoritative, pk);
    }

    @Override
    public Serializable execute(OperationContext context, Serializable command) throws BusinessException {
        if (isDelete(context) && command instanceof PurchaseOrderOperationCommand) {
            return legacy.execute(context, command);
        }
        validate(context, command);
        PurchaseOrderPfCommand pf = (PurchaseOrderPfCommand) command;
        IplatFormEntry nativeEntry = platform == null ? NCLocator.getInstance().lookup(IplatFormEntry.class)
                : platform;
        Object result = nativeEntry.processAction(pf.getActionName(), pf.getBillType(), pf.getNote(),
                pf.getOrder(), pf.getUserObject(), pf.getParameters());
        requireSavedOrderPk(context, context.getObjects().get(0), result);
        return (OrderVO[]) result;
    }

    @Override
    public List<BusinessObjectRef> describeResult(OperationContext context, Serializable result)
            throws BusinessException {
        if (isDelete(context)) {
            return legacy.describeResult(context, result);
        }
        BusinessObjectRef ref = requireSaveContext(context);
        String pk = requireSavedOrderPk(context, ref, result);
        // The core calls describeResult in the same transaction as PF, before audit backfill.
        OrderHeaderVO header = requirePersistedOrder(context, pk);
        if (present(header.getVbillcode()) == null) {
            throw new BusinessException("Purchase-order PF Save did not identify the saved order number");
        }
        return Collections.singletonList(new BusinessObjectRef(ref.getClientObjectId(), ref.getObjectType(),
                header.getPk_order(), header.getVbillcode(), ref.getParentClientObjectId(),
                ref.getDesiredOwnerOrg(), ref.getPolicyOwnerOrg(), ref.getTargetOrg()));
    }

    private static BusinessObjectRef requireSaveContext(OperationContext context) throws BusinessException {
        if (context == null || !NODE.equals(context.getNodeCode()) || !"Save".equals(context.getButtonCode())
                || (!isInsert(context) && !PurchaseOrderOperationCommand.UPDATE.equals(context.getOperationType()))
                || present(context.getGroupId()) == null || present(context.getOrgId()) == null
                || context.getObjects() == null || context.getObjects().size() != 1) {
            throw new BusinessException("The authorization must be for one purchase-order Save");
        }
        BusinessObjectRef ref = context.getObjects().get(0);
        if (ref == null || !PurchaseOrderOperationCommand.OBJECT_TYPE.equals(ref.getObjectType())
                || ref.getParentClientObjectId() != null || present(ref.getClientObjectId()) == null
                || (isInsert(context) != (present(ref.getObjectPk()) == null))) {
            throw new BusinessException("The authorization does not identify one purchase order");
        }
        return ref;
    }

    private static void requireNativeType(String type, OrderHeaderVO header) throws BusinessException {
        String transactionType = header.getVtrantypecode();
        String expected = present(transactionType) == null || StringUtil.isContainChinese(transactionType)
                ? BILL_TYPE : transactionType;
        if (!expected.equals(type) || !expected.equals(expected.trim())) {
            throw new BusinessException("The PF type does not match the purchase-order transaction type");
        }
        if (!BILL_TYPE.equals(type)) {
            try {
                if (!BILL_TYPE.equals(PfUtilBaseTools.getRealBilltype(type))) {
                    throw new BusinessException("The PF transaction type is outside purchase orders");
                }
            } catch (PFRuntimeException ex) {
                throw new BusinessException("Unable to resolve the purchase-order PF transaction type", ex);
            }
        }
    }

    private static void requireNativeArguments(PurchaseOrderPfCommand pf, OrderHeaderVO header, String pk)
            throws BusinessException {
        WorkflownoteVO note = pf.getNote();
        if (note != null) {
            requireWorkflowNote(note, pf.getBillType(), header, pk);
        }
        Object userObject = pf.getUserObject();
        if (userObject != null) {
            if (userObject.getClass() != PfUserObject.class) {
                throw new BusinessException("Invalid purchase-order PF user object");
            }
            PfUserObject user = (PfUserObject) userObject;
            Object orderContext = user.getUserObject();
            if (orderContext != null && orderContext.getClass() != OrderContext.class) {
                throw new BusinessException("Invalid purchase-order PF context");
            }
            Map<?, ?> checks = user.getBusinessCheckMap();
            if (checks != null) {
                for (Map.Entry<?, ?> check : checks.entrySet()) {
                    if (!(check.getKey() instanceof String) || !(check.getValue() instanceof Boolean)) {
                        throw new BusinessException("Invalid purchase-order PF check");
                    }
                }
            }
        }
        HashMap<?, ?> parameters = pf.getParameters();
        if (parameters != null) {
            for (Map.Entry<?, ?> parameter : parameters.entrySet()) {
                if (!"notechecked".equals(parameter.getKey()) || !"notechecked".equals(parameter.getValue())) {
                    throw new BusinessException("Unsupported parameter for purchase-order PF Save");
                }
            }
        }
    }

    private static void requireWorkflowNote(WorkflownoteVO note, String type, OrderHeaderVO header, String pk)
            throws BusinessException {
        if (note.getClass() != WorkflownoteVO.class || !matchesWhenPresent(note.getBillid(), pk)
                || !matchesWhenPresent(note.getPk_group(), header.getPk_group())
                || !matchesWhenPresent(note.getPk_org(), header.getPk_org())
                || !matchesNoteType(note.getPk_billtype(), type, header.getCtrantypeid())) {
            throw new BusinessException("The PF workflow note belongs to a different order");
        }
        WfTaskInfo taskInfo = note.getTaskInfo();
        if (taskInfo == null) {
            return;
        }
        if (taskInfo.getClass() != WfTaskInfo.class) {
            throw new BusinessException("Invalid purchase-order PF workflow task information");
        }
        WFTask task = taskInfo.getTask();
        if (task != null && (task.getClass() != WFTask.class || !matchesWhenPresent(task.getBillID(), pk)
                || !matchesWhenPresent(task.getPk_org(), header.getPk_org())
                || !matchesNoteType(task.getBillType(), type, header.getCtrantypeid()))) {
            throw new BusinessException("The PF workflow task belongs to a different order");
        }
    }

    private static boolean matchesNoteType(String noteType, String pfType, String transactionTypeId) {
        return present(noteType) == null || pfType.equals(noteType) || BILL_TYPE.equals(noteType)
                || noteType.equals(transactionTypeId);
    }

    private static String requireSavedOrderPk(OperationContext context, BusinessObjectRef ref, Object result)
            throws BusinessException {
        if (!(result instanceof OrderVO[]) || ((OrderVO[]) result).length != 1) {
            throw new BusinessException("Purchase-order PF Save must return one saved order");
        }
        OrderHeaderVO header = requireHeader(((OrderVO[]) result)[0]);
        boolean insert = isInsert(context);
        // The native insert response may omit ownership fields. Check the stored owner in describeResult.
        requireOwner(context, header, insert ? Collections.<String>emptySet() : header.usedAttributeNames());
        String pk = present(header.getPk_order());
        if (pk == null || !pk.equals(header.getPk_order())
                || (!insert && !pk.equals(ref.getObjectPk()))) {
            throw new BusinessException("Purchase-order PF Save returned an unexpected order identity");
        }
        return pk;
    }

    private OrderHeaderVO requirePersistedOrder(OperationContext context, String pk) throws BusinessException {
        OrderHeaderVO persisted = headers.find(pk);
        if (persisted == null || !pk.equals(persisted.getPk_order())) {
            throw new BusinessException("The authorized purchase order does not exist");
        }
        requireOwner(context, persisted, null);
        return persisted;
    }

    private static OrderHeaderVO requireHeader(OrderVO order) throws BusinessException {
        if (order == null || order.getClass() != OrderVO.class) {
            throw new BusinessException("A typed purchase order is required");
        }
        ISuperVO parent = order.getParent();
        if (parent == null || parent.getClass() != OrderHeaderVO.class) {
            throw new BusinessException("A typed purchase-order header is required");
        }
        return (OrderHeaderVO) parent;
    }

    private static void requireOwner(OperationContext context, OrderHeaderVO header, Set<String> fields)
            throws BusinessException {
        if (!matchesHeaderField(header, fields, OrderHeaderVO.PK_GROUP, context.getGroupId())
                || !matchesHeaderField(header, fields, OrderHeaderVO.PK_ORG, context.getOrgId())
                || (header.getDr() != null && header.getDr().intValue() != 0)) {
            throw new BusinessException("The order is outside the authorized group and business unit");
        }
    }

    private static boolean matchesHeaderField(OrderHeaderVO header, Set<String> fields, String name, String expected) {
        Object value = header.getAttributeValue(name);
        return Objects.equals(expected, value) || (fields != null && value == null && !fields.contains(name));
    }

    private static boolean matchesWhenPresent(String value, String expected) {
        return present(value) == null || value.equals(expected);
    }

    private static boolean isInsert(OperationContext context) {
        return PurchaseOrderOperationCommand.INSERT.equals(context.getOperationType());
    }

    private static boolean isDelete(OperationContext context) {
        return context != null && NODE.equals(context.getNodeCode()) && "Delete".equals(context.getButtonCode())
                && PurchaseOrderOperationCommand.DELETE.equals(context.getOperationType());
    }

    private static String present(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
