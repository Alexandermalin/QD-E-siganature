package nc.impl.pu.qd.esign;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import nc.bs.dao.BaseDAO;
import nc.bs.framework.common.NCLocator;
import nc.itf.pu.m21.IOrderMaintain;
import nc.itf.pu.qd.esign.IPurchaseOrderOperationAdapter;
import nc.vo.pub.BusinessException;
import nc.vo.pu.m21.context.OrderContext;
import nc.vo.pu.m21.entity.OrderHeaderVO;
import nc.vo.pu.m21.entity.OrderVO;
import nc.vo.pu.qd.esign.PurchaseOrderOperationCommand;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;

/** 采购订单原生服务适配，保留 NC 自身业务校验和事务行为；当前仅开放有限增改删，不实现审批、流程动作或批量新增。 */
public class PurchaseOrderOperationAdapter implements IPurchaseOrderOperationAdapter {
    /** 在执行业务前核对命令类型、操作范围、授权对象和组织归属；不替代 NC 原生金额、状态等业务规则。 */
    public void validate(OperationContext context, Serializable value) throws BusinessException {
        if (context == null || !(value instanceof PurchaseOrderOperationCommand)) {
            fail("A typed purchase-order command and authoritative context are required");
        }
        String operation = context.getOperationType();
        if (!PurchaseOrderOperationCommand.INSERT.equals(operation)
                && !PurchaseOrderOperationCommand.UPDATE.equals(operation)
                && !PurchaseOrderOperationCommand.DELETE.equals(operation)) {
            fail("Purchase-order approval/PF and other operations are not implemented");
        }
        requireScope(context);
        PurchaseOrderOperationCommand command = (PurchaseOrderOperationCommand) value;
        OrderVO[] bills = command.getBills();
        if (bills == null || bills.length == 0 || bills.length != context.getObjects().size()) {
            fail("Purchase-order object set does not match the authorized request");
        }
        // 新增只允许单对象，避免未验证的批量返回顺序无法与客户端稳定标识可靠对应。
        boolean inserting = PurchaseOrderOperationCommand.INSERT.equals(operation);
        if (inserting && bills.length != 1) fail("Multiple order inserts are not yet mapped safely");
        if (PurchaseOrderOperationCommand.DELETE.equals(operation)) {
            OrderContext[] originals = command.getDeleteContexts();
            if (command.getSaveContext() != null || originals == null || originals.length != bills.length) {
                fail("Delete requires the original per-order NC contexts");
            }
        } else if (command.getSaveContext() == null || command.getDeleteContexts() != null) {
            fail("Save requires its original NC OrderContext");
        }
        Set<String> expected = authorizedPks(context, inserting);
        Set<String> seen = new HashSet<String>();
        for (OrderVO bill : bills) {
            if (bill == null || bill.getHVO() == null) fail("Missing purchase-order header");
            OrderHeaderVO head = bill.getHVO();
            checkOwner(context, head);
            String pk = key(head.getPk_order());
            if (inserting) {
                if (pk != null) fail("Insert must not reuse an existing order identity");
            } else {
                if (pk == null || !expected.contains(pk) || !seen.add(pk)) {
                    fail("Order primary keys differ from the authorized objects");
                }
                // 这里只做安全归属检查，不预校验金额、状态或重写原生业务校验。
                OrderHeaderVO stored = loadPersistedHeader(pk);
                if (stored == null || !pk.equals(key(stored.getPk_order()))) fail("Order is not readable");
                checkOwner(context, stored);
            }
        }
    }

    /** 重新校验已授权的类型化命令后，按固定动作分支委托 NC 原生业务服务；原生异常向调用方传播。 */
    public Serializable execute(OperationContext context, Serializable value) throws BusinessException {
        validate(context, value);
        PurchaseOrderOperationCommand command = (PurchaseOrderOperationCommand) value;
        IOrderMaintain nativeService = NCLocator.getInstance().lookup(IOrderMaintain.class);
        if (PurchaseOrderOperationCommand.INSERT.equals(context.getOperationType())) {
            return nativeService.insert(command.getBills(), command.getSaveContext());
        }
        if (PurchaseOrderOperationCommand.UPDATE.equals(context.getOperationType())) {
            return nativeService.update(command.getBills(), command.getSaveContext());
        }
        nativeService.delete(command.getBills(), command.getDeleteContexts());
        return null;
    }

    /** 将真实业务返回值还原为与请求客户端标识对应的对象引用，供网关检查完整性并回填持久化主键及编码。 */
    public List<BusinessObjectRef> describeResult(OperationContext context, Serializable result)
            throws BusinessException {
        requireScope(context);
        // 原生删除没有返回对象，沿用已校验授权对象身份；null 不代表漏回单据。
        if (PurchaseOrderOperationCommand.DELETE.equals(context.getOperationType())) {
            if (result != null) fail("Unexpected result from the native void delete");
            authorizedPks(context, false);
            return new ArrayList<BusinessObjectRef>(context.getObjects());
        }
        boolean inserting = PurchaseOrderOperationCommand.INSERT.equals(context.getOperationType());
        if (!inserting && !PurchaseOrderOperationCommand.UPDATE.equals(context.getOperationType())) {
            fail("Unsupported order result operation");
        }
        if (!(result instanceof OrderVO[])) fail("Native order save did not return typed objects");
        OrderVO[] bills = (OrderVO[]) result;
        if (bills.length != context.getObjects().size() || (inserting && bills.length != 1)) {
            fail("Native order result cannot be correlated safely");
        }
        Set<String> expected = authorizedPks(context, inserting);
        // 修改结果按真实主键关联授权对象，不能假定返回顺序与请求顺序相同。
        Map<String, OrderHeaderVO> heads = new HashMap<String, OrderHeaderVO>();
        for (OrderVO bill : bills) {
            if (bill == null || bill.getHVO() == null) fail("Native result is missing an order header");
            OrderHeaderVO head = bill.getHVO();
            checkOwner(context, head);
            String pk = key(head.getPk_order());
            if (pk == null || heads.put(pk, head) != null || (!inserting && !expected.contains(pk))) {
                fail("Native order result has an unexpected/duplicate primary key");
            }
        }
        List<BusinessObjectRef> refs = new ArrayList<BusinessObjectRef>();
        for (BusinessObjectRef old : context.getObjects()) {
            OrderHeaderVO head = inserting ? bills[0].getHVO() : heads.get(key(old.getObjectPk()));
            if (head == null) fail("Authorized order is missing from the native result");
            refs.add(new BusinessObjectRef(old.getClientObjectId(), old.getObjectType(),
                    head.getPk_order(), head.getVbillcode(), old.getParentClientObjectId()));
        }
        return refs;
    }

    /** 从原生数据库读取现存单据表头供归属校验，避免仅信任客户端提交的组织字段；测试可覆盖本方法提供替身。 */
    protected OrderHeaderVO loadPersistedHeader(String pk) throws BusinessException {
        return (OrderHeaderVO) new BaseDAO().retrieveByPK(OrderHeaderVO.class, pk);
    }
    /** 要求业务上下文具有非空集团和业务单元，避免缺少授权归属范围的调用。 */
    private static void requireScope(OperationContext context) throws BusinessException {
        if (context == null || key(context.getGroupId()) == null || key(context.getOrgId()) == null) {
            fail("An authorized group and business unit are required");
        }
    }
    /** 从已签署的对象集合构造唯一主键集合；新增与既存对象分别检查，防止混入未授权对象。 */
    private static Set<String> authorizedPks(OperationContext context, boolean inserting)
            throws BusinessException {
        Set<String> pks = new HashSet<String>();
        for (BusinessObjectRef ref : context.getObjects()) {
            if (!PurchaseOrderOperationCommand.OBJECT_TYPE.equals(ref.getObjectType())) {
                fail("Wrong authorized object type for the purchase-order adapter");
            }
            String pk = key(ref.getObjectPk());
            if (inserting ? pk != null : pk == null || !pks.add(pk)) fail("Invalid authorized order identities");
        }
        return pks;
    }
    /** 核对 VO 的真实集团及业务单元是否与授权上下文一致，归属检查不代替原生业务校验。 */
    private static void checkOwner(OperationContext context, OrderHeaderVO head) throws BusinessException {
        if (!key(context.getGroupId()).equals(key(head.getPk_group()))
                || !key(context.getOrgId()).equals(key(head.getPk_org()))) {
            fail("Order group/business unit does not match the authorized request");
        }
    }
    /** 规范化业务主键文本，将缺少或空白主键统一作为未持久化对象处理。 */
    private static String key(String s) { return s == null || s.trim().length() == 0 ? null : s.trim(); }
    /** 抛出明确失败，防止非法输入继续进入原生业务调用或使测试误判为成功。 */
    private static void fail(String message) throws BusinessException { throw new BusinessException(message); }
}
