package nc.impl.so.qd.esign;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import nc.bs.dao.BaseDAO;
import nc.bs.framework.common.NCLocator;
import nc.itf.so.m4331.IDeliveryAssitFunc;
import nc.itf.so.qd.esign.IDeliveryOperationAdapter;
import nc.itf.uap.pf.IplatFormEntry;
import nc.jdbc.framework.SQLParameter;
import nc.vo.pub.AggregatedValueObject;
import nc.vo.pub.BusinessException;
import nc.vo.pubapp.pflow.PFReturnObject;
import nc.vo.pubapp.pflow.PfUserObject;
import nc.vo.so.m4331.entity.DeliveryBVO;
import nc.vo.so.m4331.entity.DeliveryHVO;
import nc.vo.so.m4331.entity.DeliveryUserObject;
import nc.vo.so.m4331.entity.DeliveryVO;
import nc.vo.so.pub.SOParameterVO;
import nc.vo.so.qd.esign.DeliveryCommand;
import nc.vo.so.qd.esign.DeliveryStateResult;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.SalesOtherNodeScope;
import nc.vo.uapbd.qd.esign.SalesPfActionCommand;
import nc.vo.uapbd.qd.esign.SalesPfActionResult;

/**
 * 发货单维护（节点 40060402，正式清单第 229–238 行）的服务端固定委托适配器。
 *
 * <p>PF 六键保留原生流程平台：只把已核实的类型化 {@link SalesPfActionCommand} 交给
 * {@code IplatFormEntry.processAction}，动作、交易类型、单据、原生用户对象与流程参数全部重新核对，
 * 并以持久化状态确认业务事实。辅助四键直接委托 {@code nc.itf.so.m4331.IDeliveryAssitFunc}：
 * 整单关闭只接受 {@code closeDelivery(SOParameterVO)}；出库关闭必须先用持久化单据核对卡片行下标与
 * 授权行主键一致后才调用 {@code closeDeliveryRows(DeliveryVO,int[])}，避免表格排序或刷新后误关行。</p>
 *
 * <p>本批保持关闭的按钮（删除 / 整单打开 / 出库打开）在执行前由
 * {@link DeliveryActionRoute#requireEnabled()} 拒绝，不回落其他写入路径；适配器不自动重试，
 * 原生异常原样上抛，由网关的事务边界决定回滚。</p>
 */
public class DeliveryOperationAdapter implements IDeliveryOperationAdapter {
    /** PF 动作执行后核对持久化表头所需的最小字段集合。 */
    private static final String[] HEADER_FIELDS = new String[] {
        "cdeliveryid", "pk_group", "pk_org", "vbillcode", "dr", "vtrantypecode", "fstatusflag" };

    /** 在执行业务前核对固定白名单、授权对象与类型化命令；不替代 NC 原生金额、状态等业务规则。 */
    public void validate(OperationContext context, Serializable payload) throws BusinessException {
        DeliveryActionRoute route = DeliveryActionRoute.forContext(context);
        if (route == null) { throw new BusinessException("发货单受控入口只接受节点 40060402。"); }
        // 未完成受控接入或联调的动作在这里拒绝，不执行任何原生写入。
        route.requireEnabled();
        if (route.getKind().isDirectService()) {
            validateState(route, context, payload);
        } else {
            validatePf(route, context, payload);
        }
    }

    /** 重新核对已授权的类型化命令后，按固定动作分支委托 NC 原生业务服务；原生异常向调用方传播。 */
    public Serializable execute(OperationContext context, Serializable payload) throws BusinessException {
        DeliveryActionRoute route = DeliveryActionRoute.forContext(context);
        if (route == null) { throw new BusinessException("发货单受控入口只接受节点 40060402。"); }
        validate(context, payload);
        if (route.getKind().isDirectService()) { return executeState(route, payload); }
        return executePf(route, payload);
    }

    /**
     * 将真实业务返回值还原为与请求客户端标识对应的对象引用，供网关检查完整性并回填持久化主键及编码。
     *
     * <p>PF 动作返回 1 个根引用；直接服务动作按上下文顺序返回根引用与行动作的表体行引用，
     * 行主键沿用签名授权的行主键。</p>
     */
    public List<BusinessObjectRef> describeResult(OperationContext context, Serializable result)
            throws BusinessException {
        DeliveryActionRoute route = DeliveryActionRoute.forContext(context);
        if (route == null) { throw new BusinessException("发货单受控入口只接受节点 40060402。"); }
        if (result instanceof SalesPfActionResult) { return describePf(context, (SalesPfActionResult) result); }
        if (result instanceof DeliveryStateResult) { return describeState(context, (DeliveryStateResult) result); }
        throw new BusinessException("发货单原生结果类型不正确。");
    }

    /** PF 流程动作命令校验：单张已存发货单、主键与组织一致、交易类型匹配、原生用户对象与流程参数白名单。 */
    private void validatePf(DeliveryActionRoute route, OperationContext context, Serializable payload)
            throws BusinessException {
        require(payload instanceof SalesPfActionCommand, "发货单 PF 动作参数类型无效。");
        SalesPfActionCommand command = (SalesPfActionCommand) payload;
        AggregatedValueObject[] raw = command.getBills();
        require(raw != null && raw.length == 1, "发货单 PF 受控动作只支持一张单据。");
        require(raw[0] instanceof DeliveryVO, "发货单 PF 动作的单据类型不是 DeliveryVO。");
        DeliveryVO bill = (DeliveryVO) raw[0];
        DeliveryHVO header = bill.getParentVO();
        String key = bill.getPrimaryKey();
        require(key != null && header != null, "发货单 PF 动作缺少原生命中单据主键或表头。");
        require(route.getObjectPk().equals(key), "发货单主键与签名授权对象不一致。");
        require(context.getGroupId().equals(header.getPk_group())
                && context.getOrgId().equals(header.getPk_org()), "发货单组织与签名授权不一致。");
        // 交易类型必须是该节点固定的基本单据类型 4331，允许已核实的 4331-* 细分类型且与单据表头一致。
        String billType = command.getBillType();
        String type = header.getVtrantypecode();
        require(billType == null || (billType.matches("4331(?:-[A-Za-z0-9_-]+)?")
                && (billType.equals(type) || (type == null && "4331".equals(billType)))),
                "发货单交易类型与签名授权不一致。");
        PfUserObject userObject = command.getUserObject();
        require(userObject != null && userObject.getUserObject() instanceof DeliveryUserObject,
                "发货单缺少原生流程用户对象。");
        require(userObject.getBusinessCheckMap() == null || userObject.getBusinessCheckMap().isEmpty(),
                "发货单 PF 动作不接受跳过检查参数。");
        for (Map.Entry<String, Object> entry : command.getParameters().entrySet()) {
            String name = entry.getKey();
            require("notechecked".equals(name) || "silently".equals(name) || "nosendmessage".equals(name)
                    || "besideapprove".equals(name), "发货单 PF 参数未在白名单内：" + name);
            require(entry.getValue() instanceof String || entry.getValue() instanceof Boolean,
                    "发货单 PF 参数类型不受支持：" + name);
        }
        require(route.getKind().isPf(), "发货单动作不是已核实的 PF 动作。");
    }

    /** 按已核实的原生 PF 入口执行固定动作，并以持久化事实核对业务结果，避免把返回原 VO 当作成功。 */
    private Serializable executePf(DeliveryActionRoute route, Serializable payload) throws BusinessException {
        SalesPfActionCommand command = (SalesPfActionCommand) payload;
        DeliveryVO bill = (DeliveryVO) command.getBills()[0];
        String key = bill.getPrimaryKey();
        String billType = command.getBillType() == null ? "4331" : command.getBillType();
        Map<String, Object> parameters = command.getParameters();
        HashMap<String, Object> rawParameters = parameters.isEmpty() ? new HashMap<String, Object>()
                : new HashMap<String, Object>(parameters);
        // 恰好一次原生 PF 调用；原生异常不重试、不吞掉，由网关事务边界处理。
        Object raw = platformEntry().processAction(route.getKind().getPfAction(), billType,
                command.getWorkflownote(), bill, command.getUserObject(), rawParameters);
        Serializable nativeResult = raw instanceof Serializable ? (Serializable) raw : null;
        DeliveryHVO persisted = loadHeader(key);
        if (route.getKind() == DeliveryActionRoute.Kind.PF_DELETE) {
            require(persisted == null || isDeleted(persisted), "原生删除后发货单仍然有效。");
            String code = persisted == null ? bill.getParentVO().getVbillcode() : persisted.getVbillcode();
            return new SalesPfActionResult(nativeResult, key, code, true);
        }
        require(persisted != null, "原生流程动作后未找到发货单。");
        require(persisted.getFstatusflag() != null, "原生流程动作后发货单状态不可核对。");
        DeliveryVO returned = firstReturnedDelivery(raw);
        if (returned != null) { require(key.equals(returned.getPrimaryKey()), "原生 PF 返回发货单与授权不一致。"); }
        return new SalesPfActionResult(nativeResult, key, persisted.getVbillcode(), false);
    }

    /** 真实删除以主键查不到或逻辑删除（dr=1）为准，不以成功回调代替。 */
    private static boolean isDeleted(DeliveryHVO header) {
        Object dr = header.getAttributeValue("dr");
        return dr instanceof Number && ((Number) dr).intValue() == 1;
    }

    /** 从原生 PF 返回对象中取唯一单据，用于核对返回身份；非单据结果返回 null。 */
    private static DeliveryVO firstReturnedDelivery(Object raw) throws BusinessException {
        Object value = raw;
        if (value instanceof PFReturnObject[]) {
            PFReturnObject[] wrapped = (PFReturnObject[]) value;
            require(wrapped.length == 1 && wrapped[0] != null, "原生 PF 返回包装不唯一。");
            value = wrapped[0].getBills();
        } else if (value instanceof PFReturnObject) {
            value = ((PFReturnObject) value).getBills();
        }
        if (!(value instanceof DeliveryVO[])) { return null; }
        DeliveryVO[] bills = (DeliveryVO[]) value;
        require(bills.length == 1 && bills[0] != null, "原生 PF 返回单据不唯一。");
        return bills[0];
    }

    /** PF 结果引用：保留上下文客户端标识，回填原生命中结果的主键与持久化单号。 */
    private List<BusinessObjectRef> describePf(OperationContext context, SalesPfActionResult outcome)
            throws BusinessException {
        String key = outcome.getPrimaryKey();
        require(key != null && key.trim().length() > 0, "原生 PF 结果缺少发货单主键。");
        List<BusinessObjectRef> refs = context.getObjects();
        require(refs != null && refs.size() == 1, "发货单 PF 结果只能对应一个已授权根对象。");
        BusinessObjectRef root = refs.get(0);
        require(SalesOtherNodeScope.DELIVERY.equals(root.getObjectType())
                && root.getParentClientObjectId() == null, "发货单 PF 结果对象类型与授权不一致。");
        String code = outcome.getBillCode() == null ? root.getBillCode() : outcome.getBillCode();
        return Collections.singletonList(new BusinessObjectRef(root.getClientObjectId(),
                SalesOtherNodeScope.DELIVERY, key, code, null));
    }

    /** 直接服务结果引用：根引用使用原生命中主键，行引用沿用签名授权的表体行主键。 */
    private List<BusinessObjectRef> describeState(OperationContext context, DeliveryStateResult outcome)
            throws BusinessException {
        String key = outcome.getPrimaryKey();
        require(key != null && key.trim().length() > 0, "原生命中服务结果缺少发货单主键。");
        List<BusinessObjectRef> refs = context.getObjects();
        require(refs != null && !refs.isEmpty(), "发货单受控动作缺少已授权的业务对象。");
        List<BusinessObjectRef> out = new ArrayList<BusinessObjectRef>();
        for (BusinessObjectRef ref : refs) {
            boolean root = ref.getParentClientObjectId() == null;
            String pk = root ? key : ref.getObjectPk();
            require(pk != null && pk.trim().length() > 0, "发货单结果缺少真实对象主键。");
            if (root) { require(key.equals(pk), "原生命中结果与授权发货单不一致。"); }
            String code = root ? outcome.getBillCode() : ref.getBillCode();
            out.add(new BusinessObjectRef(ref.getClientObjectId(), ref.getObjectType(), pk, code,
                    ref.getParentClientObjectId()));
        }
        return out;
    }

    /** 直接服务动作的类型化命令校验：单张已存发货单、组织一致、原生参数白名单与行下标一致性。 */
    private void validateState(DeliveryActionRoute route, OperationContext context, Serializable payload)
            throws BusinessException {
        require(payload instanceof DeliveryCommand, "发货单直接服务动作参数类型无效。");
        DeliveryCommand command = (DeliveryCommand) payload;
        DeliveryVO[] bills = command.getBills();
        require(bills != null && bills.length == 1, "发货单直接服务动作只支持一张单据。");
        DeliveryVO bill = bills[0];
        DeliveryHVO header = bill == null ? null : bill.getParentVO();
        String key = bill == null ? null : bill.getPrimaryKey();
        require(key != null && header != null, "发货单直接服务动作缺少原生命中单据主键或表头。");
        require(route.getObjectPk().equals(key), "发货单主键与签名授权对象不一致。");
        require(context.getGroupId().equals(header.getPk_group())
                && context.getOrgId().equals(header.getPk_org()), "发货单组织与签名授权不一致。");
        // 本批未开放任何业务检查继续分支：未确认的跳过标记必须为空。
        require(command.getBusinessCheckCode() == null && command.getBusinessCheckValue() == null,
                "发货单直接服务动作不接受未绑定的业务检查继续标记。");
        if (route.getKind() == DeliveryActionRoute.Kind.STATE_BILL_CLOSE) {
            validateBillCloseParameters(route, command);
        } else if (route.getKind() == DeliveryActionRoute.Kind.STATE_ROW_CLOSE) {
            validateRowClose(route, command);
        } else {
            throw new BusinessException("发货单直接服务动作尚未接入受控执行。");
        }
    }

    /** 出库关闭：卡片行下标必须小于持久化表体行数，且该下标对应的行主键等于签名授权行主键。 */
    private void validateRowClose(DeliveryActionRoute route, DeliveryCommand command) throws BusinessException {
        int[] indexes = command.getRowIndexes();
        require(indexes != null && indexes.length == 1, "出库关闭只支持一个卡片行下标。");
        int index = indexes[0];
        require(index >= 0, "出库关闭行下标不能为负。");
        require(command.getParameters() == null || command.getParameters().length == 0,
                "出库关闭不接受原生参数载体。");
        require(route.getRowPk() != null, "出库关闭缺少已授权的表体行主键。");
        DeliveryVO persisted = loadBill(route.getObjectPk());
        require(persisted != null, "出库关闭无法读取持久化发货单。");
        DeliveryBVO[] rows = persisted.getChildrenVO();
        require(rows != null && index < rows.length, "出库关闭行下标超出持久化发货单表体范围。");
        DeliveryBVO row = rows[index];
        require(row != null && row.getPrimaryKey() != null, "出库关闭行下标未对应有效表体行。");
        require(route.getRowPk().equals(row.getPrimaryKey()),
                "出库关闭行下标对应的行主键与签名授权不一致，请重新查询后再处理。");
    }

    /** 按已核实的原生直接服务调用并核对返回单据身份，避免把返回原 VO 当成业务成功。 */
    private Serializable executeState(DeliveryActionRoute route, Serializable payload) throws BusinessException {
        DeliveryCommand command = (DeliveryCommand) payload;
        DeliveryVO[] returned;
        if (route.getKind() == DeliveryActionRoute.Kind.STATE_BILL_CLOSE) {
            SOParameterVO parameter = command.getParameters()[0];
            returned = assitFunc().closeDelivery(parameter);
        } else {
            DeliveryVO persisted = loadBill(route.getObjectPk());
            require(persisted != null, "出库关闭无法读取持久化发货单。");
            int[] indexes = command.getRowIndexes();
            // 执行前再次核对行下标与授权行主键，防止校验与执行之间行集合变化。
            DeliveryBVO[] rows = persisted.getChildrenVO();
            require(rows != null && indexes[0] >= 0 && indexes[0] < rows.length && rows[indexes[0]] != null
                    && route.getRowPk().equals(rows[indexes[0]].getPrimaryKey()),
                    "出库关闭行下标与授权行主键不一致，请重新查询后再处理。");
            returned = assitFunc().closeDeliveryRows(persisted, indexes);
        }
        DeliveryVO covered = null;
        if (returned != null) {
            for (DeliveryVO value : returned) {
                if (value != null && route.getObjectPk().equals(value.getPrimaryKey())) {
                    require(covered == null, "原生命中服务返回了重复发货单。");
                    covered = value;
                }
            }
        }
        require(covered != null, "原生命中服务未返回已授权的发货单。");
        String code = covered.getParentVO() == null ? null : covered.getParentVO().getVbillcode();
        if (code == null) {
            DeliveryHVO persisted = loadHeader(route.getObjectPk());
            code = persisted == null ? null : persisted.getVbillcode();
        }
        return new DeliveryStateResult(returned, route.getObjectPk(), code);
    }

    /** 整单关闭参数校验：只允许 setVos(单张同主键) 与空的 businessCheckMap。 */
    private void validateBillCloseParameters(DeliveryActionRoute route, DeliveryCommand command)
            throws BusinessException {
        require(command.getRowIndexes() == null || command.getRowIndexes().length == 0,
                "整单关闭不接受卡片行下标。");
        SOParameterVO[] parameters = command.getParameters();
        require(parameters != null && parameters.length == 1 && parameters[0] != null,
                "整单关闭缺少原生参数载体。");
        SOParameterVO parameter = parameters[0];
        require(parameter.getVo() == null && parameter.getView() == null && parameter.getViews() == null
                && parameter.getUserObject() == null,
                "整单关闭原生参数只允许 vos 与 businessCheckMap。");
        Map<String, Boolean> checks = parameter.getBusinessCheckMap();
        require(checks == null || checks.isEmpty(), "整单关闭本批不开放跳过业务检查参数。");
        AggregatedValueObject[] vos = parameter.getVos();
        require(vos != null && vos.length == 1, "整单关闭原生参数只支持一张发货单。");
        require(vos[0] instanceof DeliveryVO, "整单关闭原生参数的发货单类型不正确。");
        DeliveryVO nativeBill = (DeliveryVO) vos[0];
        require(nativeBill.getPrimaryKey() != null && route.getObjectPk().equals(nativeBill.getPrimaryKey()),
                "整单关闭原生参数中的发货单与签名授权不一致。");
        require(nativeBill.getParentVO() != null, "整单关闭原生参数缺少发货单表头。");
        require(route.getKind() == DeliveryActionRoute.Kind.STATE_BILL_CLOSE, "发货单整单关闭动作不正确。");
    }

    private static void require(boolean condition, String message) throws BusinessException {
        if (!condition) { throw new BusinessException(message); }
    }

    /** 直接服务定位；离线测试可覆盖以断言原生调用次数与参数。 */
    protected IDeliveryAssitFunc assitFunc() {
        return (IDeliveryAssitFunc) NCLocator.getInstance().lookup(IDeliveryAssitFunc.class);
    }

    /** 原生 PF 入口定位；离线测试可覆盖以断言动作、交易类型与流程参数。 */
    protected IplatFormEntry platformEntry() {
        return (IplatFormEntry) NCLocator.getInstance().lookup(IplatFormEntry.class);
    }

    /** 读取持久化表头以核对业务事实；离线测试可覆盖。 */
    protected DeliveryHVO loadHeader(String key) throws BusinessException {
        return (DeliveryHVO) new BaseDAO().retrieveByPK(DeliveryHVO.class, key, HEADER_FIELDS);
    }

    /**
     * 读取持久化发货单（表头加全部表体，按行号排序）用于核对卡片行下标；离线测试可覆盖。
     *
     * <p>服务端 {@code DeliveryAssitFuncImpl.getRowSelectedBill} 直接把客户端下标用于
     * {@code childrenVO[int]}，既不校验上界也不按行主键定位；本适配器因此必须按持久化表体重新核对
     * 每个下标对应的行主键与签名授权行主键一致后才调用原生行关闭。</p>
     */
    protected DeliveryVO loadBill(String key) throws BusinessException {
        DeliveryHVO header = loadHeader(key);
        if (header == null) { return null; }
        SQLParameter parameter = new SQLParameter();
        parameter.addParam(key);
        Collection<?> values = new BaseDAO().retrieveByClause(DeliveryBVO.class, "cdeliveryid=?", "crowno", parameter);
        List<DeliveryBVO> rows = new ArrayList<DeliveryBVO>();
        if (values != null) {
            for (Object value : values) {
                require(value instanceof DeliveryBVO, "发货单表体类型无效。");
                DeliveryBVO row = (DeliveryBVO) value;
                Object dr = row.getAttributeValue("dr");
                if (dr instanceof Number && ((Number) dr).intValue() == 1) { continue; }
                rows.add(row);
            }
        }
        DeliveryVO bill = new DeliveryVO();
        bill.setParentVO(header);
        bill.setChildrenVO(rows.toArray(new DeliveryBVO[rows.size()]));
        return bill;
    }
}
