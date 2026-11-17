package nc.impl.ic.qd.esign;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import nc.bs.dao.BaseDAO;
import nc.bs.framework.common.NCLocator;
import nc.bs.framework.common.InvocationInfoProxy;
import nc.itf.ic.qd.esign.IInventoryPfStateAdapter;
import nc.itf.uap.pf.IplatFormEntry;
import nc.vo.ic.general.define.ICBillFlag;
import nc.vo.ic.general.define.ICBillHeadVO;
import nc.vo.ic.general.define.ICBillVO;
import nc.vo.ic.m45.entity.PurchaseInHeadVO;
import nc.vo.ic.m4c.entity.SaleOutHeadVO;
import nc.vo.ic.m4e.entity.TransInHeadVO;
import nc.vo.ic.m40.entity.InitializtionInHeadVO;
import nc.vo.ic.m4q.entity.LocAdjustHeadVO;
import nc.vo.ic.m4a.entity.GeneralInHeadVO;
import nc.vo.ic.m46.entity.FinProdInHeadVO;
import nc.vo.ic.m47.entity.SubcontInHeadVO;
import nc.vo.ic.m4i.entity.GeneralOutHeadVO;
import nc.vo.ic.m4d.entity.MaterialOutHeadVO;
import nc.vo.ic.m4y.entity.TransOutHeadVO;
import nc.vo.ic.qd.esign.InventoryPfStateCommand;
import nc.vo.ic.qd.esign.InventoryPfStateResult;
import nc.vo.pub.AggregatedValueObject;
import nc.vo.pub.BusinessException;
import nc.vo.pub.billtype.BilltypeVO;
import nc.vo.pubapp.pflow.PFReturnObject;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.InventoryBillScope;
import nc.vo.uapbd.qd.esign.SapplyLineScope;

/** Delegates a single inventory state action to the original PF entry. */
public class InventoryPfStateAdapter implements IInventoryPfStateAdapter {
    public void validate(OperationContext context, Serializable value) throws BusinessException {
        validateAndLoad(context, value, false);
    }

    private ICBillHeadVO validateAndLoad(OperationContext context, Serializable value, boolean batch) throws BusinessException {
        BusinessObjectRef ref = route(context);
        require(value instanceof InventoryPfStateCommand, "A typed inventory PF state command is required");
        InventoryPfStateCommand command = (InventoryPfStateCommand) value;
        String objectType=ref.getObjectType();
        if (InventoryBillScope.SAPPLY.equals(objectType)) SapplyPfCommandGuard.validate(context, command, currentUserId(), batch);
        ICBillVO bill = command.getBill();
        require(InventoryBillRoute.isBill(objectType,bill),
                "Inventory PF state command has the wrong bill type");
        ICBillHeadVO head = bill.getHead();
        require(head != null && ref.getObjectPk().equals(text(head.getCgeneralhid())),
                "Inventory bill differs from the authorized object");
        checkOwner(context, head);
        checkTransactionType(context, objectType, command.getBillType(), head);
        ICBillHeadVO persisted = loadHeader(objectType, ref.getObjectPk());
        require(active(persisted) && ref.getObjectPk().equals(text(persisted.getCgeneralhid())),
                "Inventory bill is not readable");
        checkOwner(context, persisted);
        checkTransactionType(context, objectType, command.getBillType(), persisted);
        require(InventoryTransactionTypes.sameIdentity(head, persisted),
                "Inventory transaction type identity differs from the stored bill");
        return persisted;
    }

    public Serializable execute(OperationContext context, Serializable value) throws BusinessException {
        PreparedAction prepared = prepare(context, value, false);
        InventoryPfStateCommand command = prepared.command;
        Object raw = platformEntry().processAction(prepared.action, command.getBillType(), command.getWorkflowNote(),
                command.getBill(), command.getUserObject(), command.getParameters());
        return finish(context, prepared, raw);
    }

    public InventoryPfStateResult executeSapplyUnit(OperationContext context, InventoryPfStateCommand command,
            String nativeAction, NativeCall nativeCall) throws Throwable {
        PreparedAction prepared = prepare(context, command, true);
        require(InventoryBillScope.SAPPLY.equals(context.getObjects().get(0).getObjectType())
                && nativeCall != null && prepared.action.equals(nativeAction), "Inventory PF batch action differs from the authorization");
        return finish(context, prepared, nativeCall.proceed());
    }

    private PreparedAction prepare(OperationContext context, Serializable value, boolean batch) throws BusinessException {
        ICBillHeadVO before = validateAndLoad(context, value, batch);
        InventoryPfStateCommand command = (InventoryPfStateCommand) value;
        BusinessObjectRef ref = context.getObjects().get(0);
        String objectType=ref.getObjectType();
        // Delete can remove the row permanently; freeze the server-read code before native execution.
        String code = text(before.getVbillcode());
        if ("DELETE".equals(context.getOperationType())) {
            require(code != null, "The persisted inventory bill number is missing before deletion");
        }
        String submittedTypePk = InventoryTransactionTypes.identity(command.getBill().getHead());
        String button = context.getButtonCode();
        String action = "Delete".equals(button) ? "DELETE" : "Sign".equals(button) ? "SIGN"
                : "CancelSign".equals(button) ? "CANCELSIGN" : "Commit".equals(button) ? "SAVE"
                : "UnCommit".equals(button) ? "UNSAVE" : "Approve".equals(button) ? "APPROVE"
                : "UnApprove".equals(button) ? "UNAPPROVE" : null;
        require(action != null, "Unsupported inventory PF state action");
        boolean sapplyFlow = InventoryBillScope.SAPPLY.equals(objectType)
                && ("Commit".equals(button) || "UnCommit".equals(button));
        boolean sapplyApproval = InventoryBillScope.SAPPLY.equals(objectType)
                && ("Approve".equals(button) || "UnApprove".equals(button));
        if(sapplyApproval)action=nc.vo.ic.qd.esign.SapplyNativeAction.approval(button,currentUserId());
        int[] flowFlags = sapplyFlow || sapplyApproval ? sapplyStatusFlags() : null;
        if (flowFlags!=null) require(flowFlags.length == 4, "Inventory workflow status definitions are unavailable");
        if (sapplyFlow || sapplyApproval) require(flowFlags!=null, "Inventory workflow status definitions are unavailable");
        if (sapplyFlow) SapplyFlowTransitions.before(button, before.getFbillflag(), flowFlags[0], flowFlags[1], flowFlags[2]);
        if (sapplyApproval) SapplyFlowTransitions.beforeApproval(button,before.getFbillflag(),flowFlags[0],flowFlags[1],flowFlags[2],flowFlags[3]);
        return new PreparedAction(action, command, code, submittedTypePk, flowFlags);
    }

    private InventoryPfStateResult finish(OperationContext context, PreparedAction prepared, Object raw)
            throws BusinessException {
        BusinessObjectRef ref = context.getObjects().get(0);
        String objectType = ref.getObjectType(), pk = ref.getObjectPk();
        String button = context.getButtonCode(), billType = prepared.command.getBillType();
        String code = prepared.beforeCode, submittedTypePk = prepared.submittedTypePk;
        int[] flowFlags = prepared.flowFlags;
        require(raw == null || raw instanceof Serializable, "Native inventory PF result is not serializable");
        checkReturnedBill(raw, context, objectType, pk, billType, submittedTypePk);
        ICBillHeadVO persisted = loadHeader(objectType, pk);
        boolean deleted = "DELETE".equals(prepared.action);
        if (deleted) {
            if (persisted != null) {
                require(pk.equals(text(persisted.getCgeneralhid())), "Native inventory deletion returned a different bill");
                checkOwner(context, persisted);
                checkTransactionType(context, objectType, billType, persisted);
                require(InventoryTransactionTypes.sameIdentity(submittedTypePk, persisted),
                        "Deleted inventory transaction type identity differs from the authorization");
            }
            require(deleted(persisted), "Native inventory deletion left an active bill or an unknown deletion state");
        } else {
            require(active(persisted) && pk.equals(text(persisted.getCgeneralhid())),
                    "Native inventory action did not return the persisted bill");
            checkOwner(context, persisted);
            checkTransactionType(context, objectType, billType, persisted);
            require(InventoryTransactionTypes.sameIdentity(submittedTypePk, persisted),
                    "Inventory transaction type identity changed during the native action");
            Integer flag = persisted.getFbillflag();
            if ("Commit".equals(button) || "UnCommit".equals(button))
                SapplyFlowTransitions.after(button, flag, flowFlags[0], flowFlags[1], flowFlags[2]);
            else if ("Approve".equals(button) || "UnApprove".equals(button))
                SapplyFlowTransitions.afterApproval(button,flag,flowFlags[0],flowFlags[1],flowFlags[2],flowFlags[3]);
            else {
                int expected = expectedStatus("SIGN".equals(prepared.action));
                require(flag != null && flag.intValue() == expected, "Native inventory action did not produce the expected status");
            }
            code = text(persisted.getVbillcode());
        }
        require(code != null, "Inventory action did not identify the persisted bill number");
        return new InventoryPfStateResult((Serializable) raw, pk, code, deleted);
    }

    private static final class PreparedAction {
        final String action, beforeCode, submittedTypePk;
        final InventoryPfStateCommand command;
        final int[] flowFlags;

        PreparedAction(String action, InventoryPfStateCommand command, String beforeCode, String submittedTypePk,
                int[] flowFlags) {
            this.action = action;
            this.command = command;
            this.beforeCode = beforeCode;
            this.submittedTypePk = submittedTypePk;
            this.flowFlags = flowFlags;
        }
    }

    public List<BusinessObjectRef> describeResult(OperationContext context, Serializable value) throws BusinessException {
        BusinessObjectRef ref = route(context);
        require(value instanceof InventoryPfStateResult, "Invalid inventory PF state result");
        InventoryPfStateResult result = (InventoryPfStateResult) value;
        require(ref.getObjectPk().equals(text(result.getPrimaryKey())) && text(result.getBillCode()) != null
                && result.isDeleted() == "DELETE".equals(context.getOperationType()),
                "Inventory PF state result differs from the authorization");
        return Collections.singletonList(new BusinessObjectRef(ref.getClientObjectId(), ref.getObjectType(),
                ref.getObjectPk(), result.getBillCode(), null));
    }

    protected IplatFormEntry platformEntry() { return NCLocator.getInstance().lookup(IplatFormEntry.class); }
    protected BilltypeVO loadTransactionType(String pk) throws BusinessException {
        return InventoryTransactionTypes.loadLocked(pk);
    }
    private void checkTransactionType(OperationContext context, String type, String billType,
            ICBillHeadVO head) throws BusinessException {
        InventoryTransactionTypes.require(context, type, billType, head, false, new InventoryTransactionTypes.Lookup() {
            public BilltypeVO lock(String pk) throws BusinessException { return loadTransactionType(pk); }
        });
    }
    protected int expectedStatus(boolean sign) throws BusinessException {
        try { return sign ? ICBillFlag.getSignFlag() : ICBillFlag.getFreeFlag(); }
        catch (LinkageError failure) {
            throw new BusinessException("无法读取 NC 库存单据状态定义。", failure);
        }
    }
    protected int[] sapplyStatusFlags() throws BusinessException {
        try { return new int[] { ICBillFlag.getFreeFlag(), ICBillFlag.getAuditingFlag(), ICBillFlag.getAuditedFlag(), ICBillFlag.getNoPassFlag() }; }
        catch (LinkageError failure) { throw new BusinessException("无法读取 NC 库存流程状态定义。", failure); }
    }
    protected String currentUserId() { return InvocationInfoProxy.getInstance().getUserId(); }
    protected ICBillHeadVO loadHeader(boolean purchase, String pk) throws BusinessException {
        return (ICBillHeadVO) new BaseDAO().retrieveByPK(purchase ? PurchaseInHeadVO.class : SaleOutHeadVO.class, pk);
    }
    protected ICBillHeadVO loadTransInHeader(String pk) throws BusinessException {
        return (ICBillHeadVO) new BaseDAO().retrieveByPK(TransInHeadVO.class,pk);
    }
    protected ICBillHeadVO loadInitialInHeader(String pk) throws BusinessException {
        return (ICBillHeadVO) new BaseDAO().retrieveByPK(InitializtionInHeadVO.class,pk);
    }
    protected ICBillHeadVO loadLocAdjustHeader(String pk) throws BusinessException {
        return (ICBillHeadVO) new BaseDAO().retrieveByPK(LocAdjustHeadVO.class,pk);
    }
    protected ICBillHeadVO loadGeneralInHeader(String pk) throws BusinessException {
        return (ICBillHeadVO) new BaseDAO().retrieveByPK(GeneralInHeadVO.class,pk);
    }
    protected ICBillHeadVO loadProductInHeader(String pk) throws BusinessException {
        return (ICBillHeadVO) new BaseDAO().retrieveByPK(FinProdInHeadVO.class,pk);
    }
    protected ICBillHeadVO loadSubcontInHeader(String pk) throws BusinessException {
        return (ICBillHeadVO) new BaseDAO().retrieveByPK(SubcontInHeadVO.class,pk);
    }
    protected ICBillHeadVO loadGeneralOutHeader(String pk) throws BusinessException {
        return (ICBillHeadVO) new BaseDAO().retrieveByPK(GeneralOutHeadVO.class,pk);
    }
    protected ICBillHeadVO loadMaterialOutHeader(String pk) throws BusinessException {
        return (ICBillHeadVO) new BaseDAO().retrieveByPK(MaterialOutHeadVO.class,pk);
    }
    protected ICBillHeadVO loadTransOutHeader(String pk) throws BusinessException {
        return (ICBillHeadVO) new BaseDAO().retrieveByPK(TransOutHeadVO.class,pk);
    }
    protected ICBillHeadVO loadSapplyHeader(String pk) throws BusinessException {
        return SapplyBillDataAccess.readHead(pk);
    }
    private ICBillHeadVO loadHeader(String type,String pk) throws BusinessException {
        if(InventoryBillScope.OUTSOURCE_IN.equals(type)) return loadHeader(true,pk);
        if(InventoryBillScope.PRODUCTION_ORDER.equals(type)) return loadProductInHeader(pk);
        if(InventoryBillScope.TRANS_IN.equals(type)) return loadTransInHeader(pk);
        if(InventoryBillScope.INITIAL_IN.equals(type)) return loadInitialInHeader(pk);
        if(InventoryBillScope.LOC_ADJUST.equals(type)) return loadLocAdjustHeader(pk);
        if(InventoryBillScope.GENERAL_IN.equals(type)) return loadGeneralInHeader(pk);
        if(InventoryBillScope.PRODUCT_IN.equals(type)) return loadProductInHeader(pk);
        if(InventoryBillScope.SUBCONT_IN.equals(type)) return loadSubcontInHeader(pk);
        if(InventoryBillScope.GENERAL_OUT.equals(type)) return loadGeneralOutHeader(pk);
        if(InventoryBillScope.MATERIAL_OUT.equals(type)) return loadMaterialOutHeader(pk);
        if(InventoryBillScope.TRANS_OUT.equals(type)) return loadTransOutHeader(pk);
        if(InventoryBillScope.SAPPLY.equals(type)) return loadSapplyHeader(pk);
        require(InventoryBillScope.PURCHASE_IN.equals(type)||InventoryBillScope.SALE_OUT.equals(type),"Unsupported inventory header type");
        return loadHeader(InventoryBillScope.PURCHASE_IN.equals(type),pk);
    }

    private static BusinessObjectRef route(OperationContext context) throws BusinessException {
        require(context != null && text(context.getGroupId()) != null && text(context.getOrgId()) != null
                && context.getObjects().size() == 1 && InventoryBillRoute.matches(context.getNodeCode(),
                    context.getButtonCode(), context.getOperationType())
                && !SapplyLineScope.matches(context.getNodeCode(),context.getButtonCode(),context.getOperationType())
                && !"Save".equals(context.getButtonCode()), "Unsupported inventory PF state route");
        BusinessObjectRef ref = context.getObjects().get(0);
        String type = InventoryBillRoute.objectType(context.getNodeCode());
        require(type.equals(ref.getObjectType()) && text(ref.getObjectPk()) != null
                && ref.getObjectPk().equals(text(ref.getObjectPk())) && ref.getParentClientObjectId() == null
                && ref.getDesiredOwnerOrg() == null && ref.getTargetOrg() == null,
                "Invalid inventory PF state authorization");
        return ref;
    }
    private static void checkOwner(OperationContext context, ICBillHeadVO head) throws BusinessException {
        require(context.getGroupId().equals(text(head.getPk_group())) && context.getOrgId().equals(text(head.getPk_org())),
                "Inventory bill group or organization differs from the authorization");
    }
    private static boolean active(ICBillHeadVO head) {
        if (head == null) return false;
        Object dr = head.getAttributeValue("dr");
        return dr == null || dr instanceof Number && ((Number) dr).intValue() == 0;
    }
    private static boolean deleted(ICBillHeadVO head) {
        if (head == null) return true;
        Object dr = head.getAttributeValue("dr");
        return dr instanceof Number && ((Number) dr).intValue() == 1;
    }
    private void checkReturnedBill(Object raw, OperationContext context, String objectType, String pk, String billType,
            String submittedTypePk) throws BusinessException {
        if (raw == null) return;
        Object bill = raw;
        if (bill instanceof PFReturnObject[]) {
            PFReturnObject[] wrappers = (PFReturnObject[]) bill;
            require(wrappers.length == 1 && wrappers[0] != null, "Unexpected inventory PF result wrapper");
            bill = wrappers[0].getBills();
        } else if (bill instanceof PFReturnObject) bill = ((PFReturnObject) bill).getBills();
        if (bill instanceof AggregatedValueObject[]) {
            AggregatedValueObject[] bills = (AggregatedValueObject[]) bill;
            require(bills.length == 1, "Inventory PF result must contain one bill");
            bill = bills[0];
        }
        require(InventoryBillRoute.isBill(objectType,bill),
                "Unexpected inventory PF state result type");
        ICBillHeadVO head = ((ICBillVO) bill).getHead();
        require(head != null && pk.equals(text(head.getCgeneralhid())), "Native inventory PF returned a different bill");
        checkOwner(context, head);
        checkTransactionType(context, objectType, billType, head);
        require(InventoryTransactionTypes.sameIdentity(submittedTypePk, head),
                "Native inventory PF returned a different transaction type identity");
    }
    private static String text(String value) { return value == null || value.trim().isEmpty() ? null : value.trim(); }
    private static void require(boolean valid, String message) throws BusinessException {
        if (!valid) throw new BusinessException(message);
    }
}
