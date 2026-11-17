package nc.impl.so.qd.esign;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import nc.bs.framework.common.NCLocator;
import nc.itf.so.m30.closemanage.ISaleOrderCloseManageMaintain;
import nc.itf.so.qd.esign.ISaleCloseOperationAdapter;
import nc.vo.pub.BusinessException;
import nc.vo.so.m30.entity.SaleOrderBVO;
import nc.vo.so.m30.entity.SaleOrderHVO;
import nc.vo.so.m30.entity.SaleOrderVO;
import nc.vo.so.m30.entity.SaleOrderViewVO;
import nc.vo.so.pub.SOParameterVO;
import nc.vo.so.qd.esign.SaleCloseCommand;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.SalesOtherNodeScope;

/**
 * 销售订单关闭管理（节点 40060303，正式清单第 217–228 行）的受控适配器。
 *
 * <p>只接受经固定路由解析出的 12 项动作与类型化命令 {@link SaleCloseCommand}：校验授权组织、
 * 单张订单/单行、关闭原因逐行一致后，按固定原生方法恰好调用一次。原生异常原样上抛，
 * 不自动重试、不吞掉 {@code IResumeException}；原生返回只用于核对对象身份，不当作业务成功回执。</p>
 *
 * <p>整单打开与出库打开因原生 ATP「继续确认」恢复协议未核实而保持关闭：路由在任何原生调用前
 * 抛业务异常，因此这两个分支在本批不会被真正执行。</p>
 */
public class SaleCloseOperationAdapter implements ISaleCloseOperationAdapter {
    /**
     * 在执行前核对固定路由、动作可执行状态、命令类型、授权组织、单笔单行与原因一致性；
     * 不替代 NC 原生状态机、下游单据数量、ATP 与权限校验。
     */
    public void validate(OperationContext context, Serializable payload) throws BusinessException {
        SaleCloseActionRoute route = SaleCloseActionRoute.forContext(context);
        if (route == null) {
            // 不是本节点也必须显式失败：静默降级会落入其他受控或原生写入路径。
            throw new BusinessException("销售订单关闭管理适配器只接受功能节点 40060303。");
        }
        route.requireEnabled();
        require(payload instanceof SaleCloseCommand, "SaleCloseCommand required");
        SaleCloseCommand command = (SaleCloseCommand) payload;
        require(SalesOtherNodeScope.supports(context.getGroupId(), context.getOrgId()),
                "销售订单关闭管理仅支持已核实集团与销售组织");
        require(SalesOtherNodeScope.supportsAction(context.getGroupId(), context.getOrgId(),
                context.getNodeCode(), context.getButtonCode(), context.getOperationType()),
                "销售订单关闭管理动作域与授权不一致");
        // 12 项进入原生动作后首先采集关闭/打开原因，原因逐行写入 vclosereason 后随单据提交；
        // 原因为空串会被原生阻断，取消则不写库，因此命令必须携带与每一行完全一致的非空原因。
        String reason = command.getCloseReason();
        require(reason != null && reason.trim().length() > 0, "关闭管理动作缺少关闭/打开原因");
        require(reason.equals(reason.trim()), "关闭/打开原因首尾不能包含空白");
        if (route.getKind().isBill()) {
            validateBill(route, context, command, reason);
        } else {
            validateRow(route, context, command, reason);
        }
    }

    /** 整单动作校验：恰好一张已存订单，含该单全部行，主键/组织与授权一致，每行原因与命令一致。 */
    private void validateBill(SaleCloseActionRoute route, OperationContext context, SaleCloseCommand command,
            String reason) throws BusinessException {
        require(command.getRows() == null, "整单动作不接受行命令");
        SaleOrderVO[] bills = command.getBills();
        require(bills != null && bills.length == 1, "整单动作只支持一张订单");
        SaleOrderVO bill = bills[0];
        require(bill != null && bill.getParentVO() != null && bill.getPrimaryKey() != null,
                "整单动作缺少订单表头或主键");
        require(route.getObjectPk().equals(bill.getPrimaryKey()), "命令订单主键与签名授权对象不一致");
        SaleOrderHVO header = bill.getParentVO();
        require(context.getGroupId().equals(header.getPk_group())
                && context.getOrgId().equals(header.getPk_org()), "订单组织与授权不一致");
        SaleOrderBVO[] lines = bill.getChildrenVO();
        require(lines != null && lines.length > 0, "整单动作缺少该单全部行，请重新查询后重试");
        for (int index = 0; index < lines.length; index++) {
            SaleOrderBVO line = lines[index];
            require(line != null, "整单动作的行数据为空");
            require(reason.equals(line.getVclosereason()), "行关闭/打开原因与命令原因不一致");
        }
    }

    /** 行动作校验：恰好一行视图，行所属订单与授权订单一致，行主键命中授权行，原因与命令一致。 */
    private void validateRow(SaleCloseActionRoute route, OperationContext context, SaleCloseCommand command,
            String reason) throws BusinessException {
        require(command.getBills() == null, "行动作不接受整单命令");
        SaleOrderViewVO[] rows = command.getRows();
        require(rows != null && rows.length == 1, "行动作只支持一行");
        SaleOrderViewVO row = rows[0];
        require(row != null && row.getHead() != null && row.getBody() != null, "行动作缺少行视图表头或表体");
        SaleOrderHVO header = row.getHead();
        require(header.getPrimaryKey() != null && route.getObjectPk().equals(header.getPrimaryKey()),
                "行视图所属订单与签名授权不一致");
        require(context.getGroupId().equals(header.getPk_group())
                && context.getOrgId().equals(header.getPk_org()), "行视图组织与授权不一致");
        SaleOrderBVO line = row.getBody();
        String linePk = line.getPrimaryKey();
        require(linePk != null && linePk.trim().length() > 0, "行动作缺少授权行主键");
        List<String> authorizedLines = route.getLinePks();
        if (!authorizedLines.isEmpty()) {
            // 受控路径只接受单笔单行：授权了多行或多行中的其他行都必须重新签名。
            require(authorizedLines.size() == 1, "行动作只支持一行授权");
            require(authorizedLines.get(0).equals(linePk), "命令行主键与签名授权行不一致");
        }
        require(reason.equals(line.getVclosereason()), "行关闭/打开原因与命令原因不一致");
    }

    /**
     * 重新校验后按固定原生方法恰好调用一次，并用返回对象核对身份。
     * 原生异常原样上抛，不自动重试；返回数组不是逐单成功回执，空/多余/重复/身份不符均拒绝。
     */
    public Serializable execute(OperationContext context, Serializable payload) throws BusinessException {
        SaleCloseActionRoute route = SaleCloseActionRoute.forContext(context);
        if (route == null) {
            throw new BusinessException("销售订单关闭管理适配器只接受功能节点 40060303。");
        }
        validate(context, payload);
        SaleCloseCommand command = (SaleCloseCommand) payload;
        SaleCloseActionRoute.Kind kind = route.getKind();
        ISaleOrderCloseManageMaintain service = closeManage();
        if (SaleCloseActionRoute.Kind.BILL_CLOSE == kind) {
            return verifyOrders(service.closeSaleOrder(command.getBills()), route);
        }
        if (SaleCloseActionRoute.Kind.BILL_OPEN == kind) {
            // 原生客户端恒传 false：true 会跳过 ATP 检查；本批 BILL_OPEN 未启用，不会到达这里。
            return verifyParameters(service.openSaleOrder(command.getBills(), false), route, command);
        }
        if (SaleCloseActionRoute.Kind.LINE_CLOSE == kind) {
            return verifyViews(service.closeSaleOrderRow(command.getRows()), route, command);
        }
        if (SaleCloseActionRoute.Kind.LINE_OPEN == kind) {
            return verifyViews(service.openSaleOrderRow(command.getRows()), route, command);
        }
        // 发货/出库/开票/结算的关闭与打开：原生 service 内部对选中行做 changeToSaleOrderVO()，
        // 这里在适配器内完成等价的单行转换后调用固定整单数组方法。
        SaleOrderVO[] bills = changeToOrders(command.getRows());
        if (SaleCloseActionRoute.Kind.SEND_CLOSE == kind) {
            return verifyViews(service.closeSaleOrderSend(bills), route, command);
        }
        if (SaleCloseActionRoute.Kind.SEND_OPEN == kind) {
            return verifyViews(service.openSaleOrderSend(bills), route, command);
        }
        if (SaleCloseActionRoute.Kind.OUT_CLOSE == kind) {
            return verifyViews(service.closeSaleOrderOut(bills), route, command);
        }
        if (SaleCloseActionRoute.Kind.OUT_OPEN == kind) {
            // 原生实现未引用该布尔（历史预留）；本批 OUT_OPEN 未启用，不会到达这里。
            return verifyParameters(service.openSaleOrderOut(bills, false), route, command);
        }
        if (SaleCloseActionRoute.Kind.INVOICE_CLOSE == kind) {
            return verifyViews(service.closeSaleOrderInvoice(bills), route, command);
        }
        if (SaleCloseActionRoute.Kind.INVOICE_OPEN == kind) {
            return verifyViews(service.openSaleOrderInvoice(bills), route, command);
        }
        if (SaleCloseActionRoute.Kind.SETTLE_CLOSE == kind) {
            return verifyViews(service.closeSaleOrderSettle(bills), route, command);
        }
        return verifyViews(service.openSaleOrderSettle(bills), route, command);
    }

    /**
     * 将真实业务返回还原为与请求客户端标识一一对应的对象引用，供网关核对完整性并回填主键与单号。
     * 根对象编码取原生命中返回表头，缺省保留原引用；行对象编码为空。
     */
    public List<BusinessObjectRef> describeResult(OperationContext context, Serializable result)
            throws BusinessException {
        SaleCloseActionRoute route = SaleCloseActionRoute.forContext(context);
        if (route == null) {
            throw new BusinessException("销售订单关闭管理适配器只接受功能节点 40060303。");
        }
        String billCode = resultBillCode(route, context, result);
        List<BusinessObjectRef> refs = new ArrayList<BusinessObjectRef>();
        for (BusinessObjectRef ref : context.getObjects()) {
            boolean root = ref.getParentClientObjectId() == null;
            refs.add(new BusinessObjectRef(ref.getClientObjectId(), ref.getObjectType(),
                    root ? route.getObjectPk() : ref.getObjectPk(),
                    root ? billCode : null,
                    ref.getParentClientObjectId()));
        }
        return refs;
    }

    /** 整单结果核对：唯一订单、主键与授权一致、结果带该单全部行。 */
    private static Serializable verifyOrders(SaleOrderVO[] returned, SaleCloseActionRoute route)
            throws BusinessException {
        require(returned != null && returned.length == 1, "原生命中整单动作未返回唯一订单");
        SaleOrderVO bill = returned[0];
        require(bill != null, "原生命中整单动作返回了空订单");
        require(route.getObjectPk().equals(bill.getPrimaryKey()), "原生命中返回订单与授权不一致");
        require(bill.getChildrenVO() != null, "原生命中整单动作结果缺少该单行");
        return returned;
    }

    /** 行级结果核对：唯一行视图、表头主键为授权订单、表体主键为授权行。 */
    private static Serializable verifyViews(SaleOrderViewVO[] returned, SaleCloseActionRoute route,
            SaleCloseCommand command) throws BusinessException {
        require(returned != null && returned.length == 1, "原生命中行级动作未返回唯一行视图");
        SaleOrderViewVO view = returned[0];
        require(view != null && view.getHead() != null && view.getBody() != null,
                "原生命中行级动作结果缺少表头或表体");
        require(route.getObjectPk().equals(view.getHead().getPrimaryKey()), "原生命中行视图不属已授权订单");
        SaleOrderViewVO requested = command.getRows()[0];
        require(requested.getBody().getPrimaryKey().equals(view.getBody().getPrimaryKey()),
                "原生命中行视图与授权行不一致");
        return returned;
    }

    /** 打开动作结果核对：{@code SOParameterVO[]} 无成功标志，只按其中订单/行视图主键核对身份。 */
    private static Serializable verifyParameters(SOParameterVO[] returned, SaleCloseActionRoute route,
            SaleCloseCommand command) throws BusinessException {
        require(returned != null && returned.length == 1, "原生命中打开动作未返回唯一参数结果");
        SOParameterVO parameter = returned[0];
        require(parameter != null, "原生命中打开动作返回了空参数结果");
        if (parameter.getVo() != null) {
            require(parameter.getVo() instanceof SaleOrderVO, "原生命中打开动作结果类型不正确");
            require(route.getObjectPk().equals(((SaleOrderVO) parameter.getVo()).getPrimaryKey()),
                    "原生命中返回订单与授权不一致");
            return returned;
        }
        if (parameter.getView() != null) {
            require(parameter.getView() instanceof SaleOrderViewVO, "原生命中打开动作行视图类型不正确");
            SaleOrderViewVO view = (SaleOrderViewVO) parameter.getView();
            require(view.getHead() != null && route.getObjectPk().equals(view.getHead().getPrimaryKey()),
                    "原生命中返回行视图不属已授权订单");
            SaleOrderViewVO requested = command.getRows() == null ? null : command.getRows()[0];
            require(view.getBody() != null && requested != null && view.getBody().getPrimaryKey()
                    .equals(requested.getBody().getPrimaryKey()), "原生命中返回行视图与授权行不一致");
            return returned;
        }
        throw new BusinessException("原生命中打开动作结果缺少订单或行视图。");
    }

    /** 从原生结果中取根对象编码：整单取返回表头，行级取返回行视图表头，缺省保留原引用编码。 */
    private static String resultBillCode(SaleCloseActionRoute route, OperationContext context, Serializable result)
            throws BusinessException {
        SaleCloseActionRoute.ResultKind resultKind = route.getKind().getResultKind();
        if (SaleCloseActionRoute.ResultKind.ORDER_ARRAY == resultKind) {
            require(result instanceof SaleOrderVO[], "原生命中整单结果类型不正确");
            SaleOrderVO[] bills = (SaleOrderVO[]) result;
            require(bills.length == 1 && bills[0] != null, "原生命中整单结果不唯一");
            require(route.getObjectPk().equals(bills[0].getPrimaryKey()), "原生命中整单结果与授权订单不一致");
            SaleOrderHVO header = bills[0].getParentVO();
            String code = header == null ? null : header.getVbillcode();
            return code == null ? authorizedBillCode(context) : code;
        }
        if (SaleCloseActionRoute.ResultKind.VIEW_ARRAY == resultKind) {
            require(result instanceof SaleOrderViewVO[], "原生命中行级结果类型不正确");
            SaleOrderViewVO[] views = (SaleOrderViewVO[]) result;
            require(views.length == 1 && views[0] != null && views[0].getHead() != null,
                    "原生命中行级结果不唯一");
            require(route.getObjectPk().equals(views[0].getHead().getPrimaryKey()),
                    "原生命中行级结果与授权订单不一致");
            String code = views[0].getHead().getVbillcode();
            return code == null ? authorizedBillCode(context) : code;
        }
        require(result instanceof SOParameterVO[], "原生命中打开结果类型不正确");
        SOParameterVO[] parameters = (SOParameterVO[]) result;
        for (int index = 0; index < parameters.length; index++) {
            SOParameterVO parameter = parameters[index];
            if (parameter == null) {
                continue;
            }
            if (parameter.getVo() instanceof SaleOrderVO
                    && route.getObjectPk().equals(((SaleOrderVO) parameter.getVo()).getPrimaryKey())) {
                SaleOrderHVO header = ((SaleOrderVO) parameter.getVo()).getParentVO();
                String code = header == null ? null : header.getVbillcode();
                return code == null ? authorizedBillCode(context) : code;
            }
            if (parameter.getView() instanceof SaleOrderViewVO) {
                SaleOrderViewVO view = (SaleOrderViewVO) parameter.getView();
                if (view.getHead() != null && route.getObjectPk().equals(view.getHead().getPrimaryKey())) {
                    String code = view.getHead().getVbillcode();
                    return code == null ? authorizedBillCode(context) : code;
                }
            }
        }
        throw new BusinessException("原生命中打开结果未包含已授权订单。");
    }

    /** 授权上下文中根对象的原始单据编码，作为原生返回编码缺失时的回退值。 */
    private static String authorizedBillCode(OperationContext context) {
        for (BusinessObjectRef ref : context.getObjects()) {
            if (ref.getParentClientObjectId() == null) {
                return ref.getBillCode();
            }
        }
        return null;
    }

    /** 行动作按原生 service 等价方式把单行视图转成整单聚合，保留行主键与行原因。 */
    private static SaleOrderVO[] changeToOrders(SaleOrderViewVO[] rows) {
        SaleOrderVO[] bills = new SaleOrderVO[rows.length];
        for (int index = 0; index < rows.length; index++) {
            bills[index] = rows[index].changeToSaleOrderVO();
        }
        return bills;
    }

    /** 验证指定前置条件，不满足时立即抛出相应异常，阻止后续处理。 */
    private static void require(boolean condition, String message) throws BusinessException {
        if (!condition) {
            throw new BusinessException(message);
        }
    }

    /** 原生关闭管理服务定位；离线测试可覆盖以断言调用次数与入参，生产环境不访问数据库。 */
    protected ISaleOrderCloseManageMaintain closeManage() {
        return NCLocator.getInstance().lookup(ISaleOrderCloseManageMaintain.class);
    }
}
