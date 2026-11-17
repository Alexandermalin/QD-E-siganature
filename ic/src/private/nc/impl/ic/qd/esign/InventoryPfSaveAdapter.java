package nc.impl.ic.qd.esign;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import nc.bs.dao.BaseDAO;
import nc.bs.framework.common.NCLocator;
import nc.itf.ic.qd.esign.IInventoryPfSaveAdapter;
import nc.itf.uap.pf.IplatFormEntry;
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
import nc.vo.ic.pub.define.BillOperator;
import nc.vo.ic.qd.esign.InventoryPfSaveCommand;
import nc.vo.ic.qd.esign.InventoryPfSaveResult;
import nc.vo.pub.AggregatedValueObject;
import nc.vo.pub.BusinessException;
import nc.vo.pub.billtype.BilltypeVO;
import nc.vo.pubapp.pflow.PFReturnObject;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.InventoryBillScope;

/** Delegates a single authorized inventory save to the native PF WRITE entry. */
public class InventoryPfSaveAdapter implements IInventoryPfSaveAdapter {
    public void validate(OperationContext context, Serializable value) throws BusinessException {
        require(context != null && text(context.getGroupId()) != null && text(context.getOrgId()) != null
                && "Save".equals(context.getButtonCode()) && context.getObjects().size() == 1,
                "One authorized inventory save is required");
        String operation = context.getOperationType();
        boolean inserting = "INSERT".equals(operation);
        require(inserting || "UPDATE".equals(operation), "Only inventory insert and update use PF save");
        BusinessObjectRef ref = context.getObjects().get(0);
        String type=InventoryBillRoute.objectType(context.getNodeCode());
        require(type!=null && type.equals(ref.getObjectType()) && ref.getParentClientObjectId() == null
                && ref.getDesiredOwnerOrg() == null && ref.getTargetOrg() == null,
                "Inventory node and authorized object type do not match");
        require(value instanceof InventoryPfSaveCommand, "A typed inventory PF command is required");
        InventoryPfSaveCommand command = (InventoryPfSaveCommand) value;
        ICBillVO bill = command.getBill();
        require(InventoryBillRoute.isBill(type,bill),
                "Inventory PF command has the wrong bill type");
        require(bill.getHead() != null && command.getUserObject() != null,
                "Inventory PF bill and native user object are required");
        require((inserting ? BillOperator.New : BillOperator.Edit).equals(command.getUserObject().getBillaction()),
                "Inventory PF bill action differs from the authorized operation");
        ICBillHeadVO head = bill.getHead();
        checkOwner(context, head);
        String billType = command.getBillType();
        checkTransactionType(context, type, billType, head, inserting);
        String pk = text(head.getCgeneralhid());
        String authorizedPk = text(ref.getObjectPk());
        if (inserting) {
            require(authorizedPk == null, "A new inventory bill must not refer to a saved object");
            if (pk != null) require(loadHeader(type, pk) == null, "The new inventory key already exists");
        } else {
            require(pk != null && pk.equals(authorizedPk), "Inventory bill differs from the signed object");
            ICBillHeadVO stored = loadHeader(type, pk);
            require(stored != null && pk.equals(text(stored.getCgeneralhid())), "Inventory bill is not readable");
            checkOwner(context, stored);
            checkTransactionType(context, type, billType, stored, false);
            require(InventoryTransactionTypes.sameIdentity(head, stored),
                    "Inventory transaction type identity differs from the stored bill");
        }
    }

    public Serializable execute(OperationContext context, Serializable value) throws BusinessException {
        validate(context, value);
        InventoryPfSaveCommand command = (InventoryPfSaveCommand) value;
        String submittedPk = text(command.getBill().getHead().getCgeneralhid());
        String submittedTypePk = InventoryTransactionTypes.identity(command.getBill().getHead());
        Object raw = platformEntry().processAction("WRITE", command.getBillType(), command.getWorkflowNote(),
                command.getBill(), command.getUserObject(), command.getParameters());
        require(raw instanceof Serializable, "Native inventory PF result is not serializable");
        ICBillVO saved = savedBill(raw);
        String type=context.getObjects().get(0).getObjectType();
        require(InventoryBillRoute.isBill(type,saved),
                "Native inventory PF returned the wrong bill type");
        ICBillHeadVO head = saved.getHead();
        require(head != null && text(head.getCgeneralhid()) != null, "Native inventory PF did not return a bill key");
        checkOwner(context, head);
        checkTransactionType(context, type, command.getBillType(), head, false);
        if (submittedTypePk != null || !"INSERT".equals(context.getOperationType())) {
            require(InventoryTransactionTypes.sameIdentity(submittedTypePk, head),
                    "Native inventory PF returned a different transaction type identity");
        }
        String pk = text(head.getCgeneralhid());
        if (submittedPk != null) require(submittedPk.equals(pk), "Native inventory PF returned a different bill");
        ICBillHeadVO persisted = loadHeader(type, pk);
        require(persisted != null && pk.equals(text(persisted.getCgeneralhid())),
                "Native inventory PF did not persist the returned bill");
        checkOwner(context, persisted);
        checkTransactionType(context, type, command.getBillType(), persisted, false);
        require(InventoryTransactionTypes.sameIdentity(head, persisted),
                "Persisted inventory transaction type identity differs from the native result");
        String code = text(persisted.getVbillcode());
        require(code != null && code.equals(text(head.getVbillcode())),
                "Native inventory PF result does not match the persisted bill number");
        return new InventoryPfSaveResult((Serializable) raw, pk, code);
    }

    public List<BusinessObjectRef> describeResult(OperationContext context, Serializable result) throws BusinessException {
        require(context != null && context.getObjects().size() == 1 && result instanceof InventoryPfSaveResult,
                "Invalid inventory PF save result");
        InventoryPfSaveResult saved = (InventoryPfSaveResult) result;
        String pk = text(saved.getPrimaryKey());
        String code = text(saved.getBillCode());
        require(saved.getNativeResult() != null && pk != null && code != null,
                "Saved inventory identity is missing");
        BusinessObjectRef original = context.getObjects().get(0);
        String type=InventoryBillRoute.objectType(context.getNodeCode());
        require(type!=null && type.equals(original.getObjectType()) && "Save".equals(context.getButtonCode())
                && original.getParentClientObjectId() == null && original.getDesiredOwnerOrg() == null
                && original.getTargetOrg() == null, "Inventory PF result is outside the authorized route");
        if ("INSERT".equals(context.getOperationType())) {
            require(text(original.getObjectPk()) == null, "New inventory result has an existing authorized key");
        } else {
            require("UPDATE".equals(context.getOperationType())
                    && pk.equals(text(original.getObjectPk())),
                    "Inventory result does not match the authorized bill");
        }
        return Collections.singletonList(new BusinessObjectRef(original.getClientObjectId(), original.getObjectType(),
                pk, code, original.getParentClientObjectId()));
    }

    protected IplatFormEntry platformEntry() { return NCLocator.getInstance().lookup(IplatFormEntry.class); }

    protected BilltypeVO loadTransactionType(String pk) throws BusinessException {
        return InventoryTransactionTypes.loadLocked(pk);
    }

    private void checkTransactionType(OperationContext context, String type, String billType,
            ICBillHeadVO head, boolean draft) throws BusinessException {
        InventoryTransactionTypes.require(context, type, billType, head, draft, new InventoryTransactionTypes.Lookup() {
            public BilltypeVO lock(String pk) throws BusinessException { return loadTransactionType(pk); }
        });
    }

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

    private static ICBillVO savedBill(Object value) throws BusinessException {
        if (value instanceof PFReturnObject[]) {
            PFReturnObject[] wrappers = (PFReturnObject[]) value;
            require(wrappers.length == 1 && wrappers[0] != null, "Unexpected inventory PF result wrapper");
            value = wrappers[0].getBills();
        } else if (value instanceof PFReturnObject) {
            value = ((PFReturnObject) value).getBills();
        }
        if (value instanceof AggregatedValueObject[]) {
            AggregatedValueObject[] bills = (AggregatedValueObject[]) value;
            require(bills.length == 1, "Inventory PF result must contain one bill");
            value = bills[0];
        }
        require(value instanceof ICBillVO, "Unexpected inventory PF result type");
        return (ICBillVO) value;
    }

    private static void checkOwner(OperationContext context, ICBillHeadVO head) throws BusinessException {
        require(context.getGroupId().equals(text(head.getPk_group())) && context.getOrgId().equals(text(head.getPk_org())),
                "Inventory bill group or organization differs from the authorization");
    }

    private static String text(String value) { return value == null || value.trim().isEmpty() ? null : value.trim(); }
    private static void require(boolean valid, String message) throws BusinessException {
        if (!valid) throw new BusinessException(message);
    }
}
