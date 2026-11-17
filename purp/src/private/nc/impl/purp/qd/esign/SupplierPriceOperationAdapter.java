package nc.impl.purp.qd.esign;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import nc.bs.dao.BaseDAO;
import nc.bs.framework.common.NCLocator;
import nc.itf.pp.supplierprice.ISupplierPriceService;
import nc.itf.purp.qd.esign.ISupplierPriceOperationAdapter;
import nc.vo.bd.meta.BatchOperateVO;
import nc.vo.pp.supplierprice.entity.SupplierPriceVO;
import nc.vo.pub.BusinessException;
import nc.vo.purp.qd.esign.SupplierPriceOperationCommand;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;

/** 只委托已核对的 NC 供应商价格 batchSave 契约，拒绝混合操作与未验证的批量模式，不伪造单据编码。 */
public class SupplierPriceOperationAdapter implements ISupplierPriceOperationAdapter {
    /** 在执行业务前核对命令类型、操作范围、授权对象和组织归属；不替代 NC 原生金额、状态等业务规则。 */
    public void validate(OperationContext context, Serializable value) throws BusinessException {
        requireScope(context);
        if (!(value instanceof SupplierPriceOperationCommand)) fail("A typed supplier-price command is required");
        String operation = context.getOperationType();
        checkOperation(operation);
        BatchOperateVO batch = ((SupplierPriceOperationCommand) value).getBatch();
        if (batch == null) fail("The original supplier-price batch is required");
        Object[] items = selected(batch, operation);
        if (items == null || items.length == 0 || items.length != context.getObjects().size()) {
            fail("Supplier-price objects do not match the authorized request");
        }
        boolean inserting = SupplierPriceOperationCommand.INSERT.equals(operation);
        // 当前不启用多对象批量；原生行下标、结果关联及回滚需要另行验证。
        if (items.length != 1) fail("Multi-object supplier-price results and rollback require a separately verified adapter");
        Set<String> expected = authorizedPks(context, inserting);
        Set<String> seen = new HashSet<String>();
        for (Object item : items) {
            if (!(item instanceof SupplierPriceVO)) fail("Only native SupplierPriceVO rows are accepted");
            SupplierPriceVO price = (SupplierPriceVO) item;
            checkOwner(context, price);
            String pk = key(price.getPk_supplierprice());
            if (inserting) {
                if (pk != null) fail("Insert must not reuse a supplier-price primary key");
            } else {
                if (pk == null || !expected.contains(pk) || !seen.add(pk)) fail("Unauthorized/duplicate supplier-price identity");
                // 检查数据库中实际保存的组织归属；原生业务规则仍交由 NC 校验。
                SupplierPriceVO stored = loadPersistedPrice(pk);
                if (stored == null || !pk.equals(key(stored.getPk_supplierprice()))) fail("Supplier price is not readable");
                checkOwner(context, stored);
            }
        }
    }

    /** 重新校验已授权的类型化命令后，按固定动作分支委托 NC 原生业务服务；原生异常向调用方传播。 */
    public Serializable execute(OperationContext context, Serializable value) throws BusinessException {
        validate(context, value);
        return NCLocator.getInstance().lookup(ISupplierPriceService.class)
                .batchSave(((SupplierPriceOperationCommand) value).getBatch());
    }

    /** 将真实业务返回值还原为与请求客户端标识对应的对象引用，供网关检查完整性并回填持久化主键及编码。 */
    public List<BusinessObjectRef> describeResult(OperationContext context, Serializable result)
            throws BusinessException {
        requireScope(context);
        checkOperation(context.getOperationType());
        if (context.getObjects().size() != 1) fail("Multi-object supplier-price results are not verified");
        boolean inserting = SupplierPriceOperationCommand.INSERT.equals(context.getOperationType());
        Set<String> expected = authorizedPks(context, inserting);
        if (!(result instanceof BatchOperateVO)) fail("Native supplier-price batch did not return a result");
        if (SupplierPriceOperationCommand.DELETE.equals(context.getOperationType())) {
            return new ArrayList<BusinessObjectRef>(context.getObjects());
        }
        Object[] items = selected((BatchOperateVO) result, context.getOperationType());
        if (items == null || items.length != context.getObjects().size() || (inserting && items.length != 1)) {
            fail("Native supplier-price result cannot be correlated safely");
        }
        Map<String, SupplierPriceVO> rows = new HashMap<String, SupplierPriceVO>();
        for (Object item : items) {
            if (!(item instanceof SupplierPriceVO)) fail("Native supplier-price result has the wrong row type");
            SupplierPriceVO row = (SupplierPriceVO) item;
            checkOwner(context, row);
            String pk = key(row.getPk_supplierprice());
            if (pk == null || rows.put(pk, row) != null || (!inserting && !expected.contains(pk))) {
                fail("Unexpected/duplicate supplier-price result identity");
            }
        }
        List<BusinessObjectRef> refs = new ArrayList<BusinessObjectRef>();
        for (BusinessObjectRef old : context.getObjects()) {
            SupplierPriceVO row = inserting ? (SupplierPriceVO) items[0] : rows.get(key(old.getObjectPk()));
            if (row == null) fail("Authorized supplier-price row is absent from the native result");
            // 供应商价格行没有已验证的单据编码字段，不推测或伪造编码。
            refs.add(new BusinessObjectRef(old.getClientObjectId(), old.getObjectType(),
                    row.getPk_supplierprice(), null, old.getParentClientObjectId()));
        }
        return refs;
    }

    /** 查询现存供应商价格行的实际归属，防止提交载荷伪造组织；测试可覆盖本方法提供替身。 */
    protected SupplierPriceVO loadPersistedPrice(String pk) throws BusinessException {
        return (SupplierPriceVO) new BaseDAO().retrieveByPK(SupplierPriceVO.class, pk);
    }
    /** 根据增改删动作提取对应原生批处理行组，其他动作组只要含行就拒绝，避免混合授权。 */
    private static Object[] selected(BatchOperateVO batch, String op) throws BusinessException {
        boolean add = SupplierPriceOperationCommand.INSERT.equals(op);
        boolean update = SupplierPriceOperationCommand.UPDATE.equals(op);
        boolean delete = SupplierPriceOperationCommand.DELETE.equals(op);
        if ((!add && nonempty(batch.getAddObjs())) || (!update && nonempty(batch.getUpdObjs()))
                || (!delete && nonempty(batch.getDelObjs()))) fail("Mixed price operations need a separately verified adapter");
        return add ? batch.getAddObjs() : update ? batch.getUpdObjs() : batch.getDelObjs();
    }
    /** 判断数组是否实际包含元素，区分空载荷与有效业务行。 */
    private static boolean nonempty(Object[] values) { return values != null && values.length != 0; }
    /** 从已签署的对象集合构造唯一主键集合；新增与既存对象分别检查，防止混入未授权对象。 */
    private static Set<String> authorizedPks(OperationContext context, boolean inserting) throws BusinessException {
        Set<String> pks = new HashSet<String>();
        for (BusinessObjectRef ref : context.getObjects()) {
            if (!SupplierPriceOperationCommand.OBJECT_TYPE.equals(ref.getObjectType())) fail("Wrong authorized supplier-price type");
            String pk = key(ref.getObjectPk());
            if (inserting ? pk != null : pk == null || !pks.add(pk)) fail("Invalid authorized supplier-price identities");
        }
        return pks;
    }
    /** 仅接受当前供应商价格适配器已明确支持的增改删动作。 */
    private static void checkOperation(String op) throws BusinessException {
        if (!SupplierPriceOperationCommand.INSERT.equals(op) && !SupplierPriceOperationCommand.UPDATE.equals(op)
                && !SupplierPriceOperationCommand.DELETE.equals(op)) fail("Supplier-price operation is not implemented");
    }
    /** 要求业务上下文具有非空集团和业务单元，避免缺少授权归属范围的调用。 */
    private static void requireScope(OperationContext context) throws BusinessException {
        if (context == null || key(context.getGroupId()) == null || key(context.getOrgId()) == null) {
            fail("An authorized group and business unit are required");
        }
    }
    /** 核对 VO 的真实集团及业务单元是否与授权上下文一致，归属检查不代替原生业务校验。 */
    private static void checkOwner(OperationContext context, SupplierPriceVO row) throws BusinessException {
        if (!key(context.getGroupId()).equals(key(row.getPk_group())) || !key(context.getOrgId()).equals(key(row.getPk_org()))) {
            fail("Supplier-price group/business unit does not match the authorized request");
        }
    }
    /** 规范化业务主键文本，将缺少或空白主键统一作为未持久化对象处理。 */
    private static String key(String s) { return s == null || s.trim().length() == 0 ? null : s.trim(); }
    /** 抛出明确失败，防止非法输入继续进入原生业务调用或使测试误判为成功。 */
    private static void fail(String message) throws BusinessException { throw new BusinessException(message); }
}
