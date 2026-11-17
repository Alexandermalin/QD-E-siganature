package nc.impl.ic.qd.esign;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import nc.bs.dao.BaseDAO;
import nc.bs.framework.common.NCLocator;
import nc.itf.ic.m45.self.IPurchaseInMaintain;
import nc.itf.ic.m4c.self.ISaleOutMaintain;
import nc.itf.ic.qd.esign.IInventoryOperationAdapter;
import nc.vo.ic.general.define.ICBillHeadVO;
import nc.vo.ic.general.define.ICBillVO;
import nc.vo.ic.m45.entity.PurchaseInHeadVO;
import nc.vo.ic.m4c.entity.SaleOutHeadVO;
import nc.vo.ic.qd.esign.InventoryOperationCommand;
import nc.vo.pub.BusinessException;
import nc.vo.pub.billtype.BilltypeVO;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;

/** 只委托已核对的采购入库与销售出库服务，校验对象集合、新旧位置及真实归属，不支持任意服务动态调用。 */
public class InventoryOperationAdapter implements IInventoryOperationAdapter {
    /** 在执行业务前核对命令类型、操作范围、授权对象和组织归属；不替代 NC 原生金额、状态等业务规则。 */
    public void validate(OperationContext context, Serializable value) throws BusinessException {
        requireScope(context);
        if (!(value instanceof InventoryOperationCommand)) fail("A typed inventory command is required");
        InventoryOperationCommand command = (InventoryOperationCommand) value;
        String operation = context.getOperationType();
        if (!InventoryOperationCommand.INSERT.equals(operation) && !InventoryOperationCommand.UPDATE.equals(operation)
                && !InventoryOperationCommand.DELETE.equals(operation) && !InventoryOperationCommand.SIGN.equals(operation)
                && !InventoryOperationCommand.CANCEL_SIGN.equals(operation)) {
            fail("Inventory PF/compound and other operations are not implemented");
        }
        ICBillVO[] bills = bills(command, false);
        ICBillVO[] origins = bills(command, true);
        boolean inserting = InventoryOperationCommand.INSERT.equals(operation);
        if (bills == null || bills.length == 0 || bills.length != context.getObjects().size()) {
            fail("Inventory object set does not match the authorized request");
        }
        // 未验证多对象新增的结果关联与回滚，因此当前只允许单对象新增。
        if (inserting && bills.length != 1) fail("Multiple inventory inserts are not yet mapped safely");
        Set<String> expected = authorizedPks(context, command.getObjectType(), inserting);
        checkBills(context, command.getObjectType(), bills, expected, inserting, true);
        if (InventoryOperationCommand.UPDATE.equals(operation)) {
            if (origins == null || origins.length != bills.length) fail("Update requires the original NC inventory objects");
            checkBills(context, command.getObjectType(), origins, expected, false, false);
            // 保留原生新旧单据逐位置配对关系，只有主键集合相同并不足以保证配对正确。
            for (int i = 0; i < bills.length; i++) {
                if (!key(bills[i].getHead().getCgeneralhid()).equals(key(origins[i].getHead().getCgeneralhid()))) {
                    fail("Inventory new/original objects must retain native positional pairing");
                }
                if (!InventoryTransactionTypes.sameIdentity(bills[i].getHead(), origins[i].getHead())) {
                    fail("Inventory new/original transaction type identity differs");
                }
            }
        } else if (origins != null && origins.length != 0) {
            fail("Original inventory objects are only accepted for update");
        }
    }

    /** 重新校验已授权的类型化命令后，按固定动作分支委托 NC 原生业务服务；原生异常向调用方传播。 */
    public Serializable execute(OperationContext context, Serializable value) throws BusinessException {
        validate(context, value);
        InventoryOperationCommand command = (InventoryOperationCommand) value;
        String op = context.getOperationType();
        if (InventoryOperationCommand.PURCHASE_IN.equals(command.getObjectType())) {
            IPurchaseInMaintain nativeService = NCLocator.getInstance().lookup(IPurchaseInMaintain.class);
            if (InventoryOperationCommand.INSERT.equals(op)) return nativeService.insert(command.getPurchaseBills());
            if (InventoryOperationCommand.UPDATE.equals(op)) return nativeService.update(command.getPurchaseBills(), command.getPurchaseOrigins());
            if (InventoryOperationCommand.SIGN.equals(op)) return nativeService.sign(command.getPurchaseBills());
            if (InventoryOperationCommand.CANCEL_SIGN.equals(op)) return nativeService.cancelSign(command.getPurchaseBills());
            nativeService.delete(command.getPurchaseBills());
        } else {
            ISaleOutMaintain nativeService = NCLocator.getInstance().lookup(ISaleOutMaintain.class);
            if (InventoryOperationCommand.INSERT.equals(op)) return nativeService.insert(command.getSaleBills());
            if (InventoryOperationCommand.UPDATE.equals(op)) return nativeService.update(command.getSaleBills(), command.getSaleOrigins());
            if (InventoryOperationCommand.SIGN.equals(op)) return nativeService.sign(command.getSaleBills());
            if (InventoryOperationCommand.CANCEL_SIGN.equals(op)) return nativeService.cancelSign(command.getSaleBills());
            nativeService.delete(command.getSaleBills());
        }
        return null;
    }

    /** 将真实业务返回值还原为与请求客户端标识对应的对象引用，供网关检查完整性并回填持久化主键及编码。 */
    public List<BusinessObjectRef> describeResult(OperationContext context, Serializable result)
            throws BusinessException {
        requireScope(context);
        String type = context.getObjects().get(0).getObjectType();
        if (!InventoryOperationCommand.PURCHASE_IN.equals(type) && !InventoryOperationCommand.SALE_OUT.equals(type)) {
            fail("Unsupported authorized inventory type");
        }
        boolean inserting = InventoryOperationCommand.INSERT.equals(context.getOperationType());
        Set<String> expected = authorizedPks(context, type, inserting);
        if (InventoryOperationCommand.DELETE.equals(context.getOperationType())) {
            if (result != null) fail("Unexpected result from the native void delete");
            return new ArrayList<BusinessObjectRef>(context.getObjects());
        }
        String op = context.getOperationType();
        if (!inserting && !InventoryOperationCommand.UPDATE.equals(op) && !InventoryOperationCommand.SIGN.equals(op)
                && !InventoryOperationCommand.CANCEL_SIGN.equals(op)) fail("Unsupported inventory result operation");
        if (InventoryOperationCommand.PURCHASE_IN.equals(type) ? !(result instanceof nc.vo.ic.m45.entity.PurchaseInVO[])
                : !(result instanceof nc.vo.ic.m4c.entity.SaleOutVO[])) fail("Native inventory result has the wrong type");
        ICBillVO[] returned = (ICBillVO[]) result;
        if (returned.length != context.getObjects().size() || (inserting && returned.length != 1)) {
            fail("Native inventory result cannot be correlated safely");
        }
        // 按主键映射真实返回表头，同时拒绝重复或越权结果。
        Map<String, ICBillHeadVO> heads = new HashMap<String, ICBillHeadVO>();
        for (ICBillVO bill : returned) {
            if (bill == null || bill.getHead() == null) fail("Native result is missing an inventory header");
            ICBillHeadVO head = bill.getHead();
            checkOwner(context, head);
            checkTransactionType(context, type, head, false);
            String pk = key(head.getCgeneralhid());
            if (pk == null || heads.put(pk, head) != null || (!inserting && !expected.contains(pk))) {
                fail("Unexpected/duplicate inventory identity in the native result");
            }
        }
        List<BusinessObjectRef> refs = new ArrayList<BusinessObjectRef>();
        for (BusinessObjectRef old : context.getObjects()) {
            ICBillHeadVO head = inserting ? returned[0].getHead() : heads.get(key(old.getObjectPk()));
            if (head == null) fail("Authorized inventory object is absent from the native result");
            refs.add(new BusinessObjectRef(old.getClientObjectId(), old.getObjectType(), head.getCgeneralhid(),
                    head.getVbillcode(), old.getParentClientObjectId()));
        }
        return refs;
    }

    /** 按命令对象类型取新单据或原始单据数组，拒绝采购入库与销售出库载荷交叉混用。 */
    private static ICBillVO[] bills(InventoryOperationCommand command, boolean originals) throws BusinessException {
        if (InventoryOperationCommand.PURCHASE_IN.equals(command.getObjectType())) {
            if (command.getSaleBills() != null || command.getSaleOrigins() != null) fail("Mixed inventory payload");
            return originals ? command.getPurchaseOrigins() : command.getPurchaseBills();
        }
        if (InventoryOperationCommand.SALE_OUT.equals(command.getObjectType())) {
            if (command.getPurchaseBills() != null || command.getPurchaseOrigins() != null) fail("Mixed inventory payload");
            return originals ? command.getSaleOrigins() : command.getSaleBills();
        }
        fail("Unknown inventory object type");
        return null;
    }
    /** 检查库存单据的数量、唯一主键、类型与真实组织归属，必要时比对数据库已存表头。 */
    private void checkBills(OperationContext context, String type, ICBillVO[] bills, Set<String> expected,
            boolean inserting, boolean verifyStored) throws BusinessException {
        Set<String> seen = new HashSet<String>();
        for (ICBillVO bill : bills) {
            if (bill == null || bill.getHead() == null) fail("Missing inventory header");
            ICBillHeadVO head = bill.getHead();
            checkOwner(context, head);
            checkTransactionType(context, type, head, inserting);
            String pk = key(head.getCgeneralhid());
            if (inserting) {
                if (pk != null) fail("Insert must not reuse an existing inventory identity");
            } else {
                if (pk == null || !expected.contains(pk) || !seen.add(pk)) fail("Unauthorized/duplicate inventory identity");
                if (verifyStored) {
                    ICBillHeadVO stored = loadPersistedHeader(type, pk);
                    if (stored == null || !pk.equals(key(stored.getCgeneralhid()))) fail("Inventory object is not readable");
                    checkOwner(context, stored);
                    checkTransactionType(context, type, stored, false);
                    if (!InventoryTransactionTypes.sameIdentity(head, stored)) {
                        fail("Inventory transaction type identity differs from the stored bill");
                    }
                }
            }
        }
    }
    /** 从原生数据库读取现存单据表头供归属校验，避免仅信任客户端提交的组织字段；测试可覆盖本方法提供替身。 */
    protected ICBillHeadVO loadPersistedHeader(String type, String pk) throws BusinessException {
        Class<?> headerType = InventoryOperationCommand.PURCHASE_IN.equals(type) ? PurchaseInHeadVO.class : SaleOutHeadVO.class;
        return (ICBillHeadVO) new BaseDAO().retrieveByPK(headerType, pk);
    }
    /** 从已签署的对象集合构造唯一主键集合；新增与既存对象分别检查，防止混入未授权对象。 */
    private static Set<String> authorizedPks(OperationContext context, String type, boolean inserting)
            throws BusinessException {
        Set<String> pks = new HashSet<String>();
        for (BusinessObjectRef ref : context.getObjects()) {
            if (!type.equals(ref.getObjectType())) fail("Authorized inventory objects have mixed/wrong types");
            String pk = key(ref.getObjectPk());
            if (inserting ? pk != null : pk == null || !pks.add(pk)) fail("Invalid authorized inventory identities");
        }
        return pks;
    }
    /** 要求业务上下文具有非空集团和业务单元，避免缺少授权归属范围的调用。 */
    private static void requireScope(OperationContext context) throws BusinessException {
        if (context == null || key(context.getGroupId()) == null || key(context.getOrgId()) == null) {
            fail("An authorized group and business unit are required");
        }
        if (!InventoryBillRoute.matches(context.getNodeCode(), context.getButtonCode(),
                context.getOperationType())) fail("Inventory node, button and operation do not match");
        String type = InventoryBillRoute.objectType(context.getNodeCode());
        for (BusinessObjectRef ref : context.getObjects()) {
            if (!type.equals(ref.getObjectType()) || ref.getParentClientObjectId() != null
                    || ref.getDesiredOwnerOrg() != null || ref.getTargetOrg() != null) {
                fail("Inventory authorization contains an unexpected object or relationship");
            }
        }
    }
    /** 核对 VO 的真实集团及业务单元是否与授权上下文一致，归属检查不代替原生业务校验。 */
    private static void checkOwner(OperationContext context, ICBillHeadVO head) throws BusinessException {
        if (!key(context.getGroupId()).equals(key(head.getPk_group())) || !key(context.getOrgId()).equals(key(head.getPk_org()))) {
            fail("Inventory group/business unit does not match the authorized request");
        }
    }
    /** Use the same fail-closed node transaction boundary as the PF adapters. */
    protected BilltypeVO loadTransactionType(String pk) throws BusinessException {
        return InventoryTransactionTypes.loadLocked(pk);
    }
    private void checkTransactionType(OperationContext context, String objectType, ICBillHeadVO head, boolean draft) throws BusinessException {
        String billType = InventoryBillRoute.nativeBillType(objectType);
        InventoryTransactionTypes.require(context, objectType, billType, head, draft, new InventoryTransactionTypes.Lookup() {
            public BilltypeVO lock(String pk) throws BusinessException { return loadTransactionType(pk); }
        });
    }
    /** 规范化业务主键文本，将缺少或空白主键统一作为未持久化对象处理。 */
    private static String key(String s) { return s == null || s.trim().length() == 0 ? null : s.trim(); }
    /** 抛出明确失败，防止非法输入继续进入原生业务调用或使测试误判为成功。 */
    private static void fail(String message) throws BusinessException { throw new BusinessException(message); }
}
