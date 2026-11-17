package nc.impl.so.qd.esign;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import nc.bs.framework.common.NCLocator;
import nc.itf.so.qd.esign.ISaleInvoiceOperationAdapter;
import nc.itf.uap.pf.IplatFormEntry;
import nc.vo.pub.AggregatedValueObject;
import nc.vo.pub.BusinessException;
import nc.vo.pubapp.pflow.PFReturnObject;
import nc.vo.pubapp.pflow.PfUserObject;
import nc.vo.so.m32.entity.SaleInvoiceHVO;
import nc.vo.so.m32.entity.SaleInvoiceVO;
import nc.vo.so.m32.entity.SaleinvoiceUserObject;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.SalesOtherNodeScope;
import nc.vo.uapbd.qd.esign.SalesPfActionCommand;
import nc.vo.uapbd.qd.esign.SalesPfActionResult;

/**
 * 销售发票维护（节点 40060501，第 239–244 行）的固定服务端委托适配器。
 *
 * <p>委托原始 PF 入口 {@code IplatFormEntry.processAction}，不重新实现原生业务规则：
 * 保存保留组合校验器与 {@code SaleinvoiceUserObject}，提交/收回保留 {@code filledUpInFlow} 语义，
 * 审批/取消审批保留审批说明与原生下游、核销、会计平台凭证限制。删除按钮本批关闭，执行前拒绝。</p>
 *
 * <p>原生返回的 VO 不证明业务成功：执行后按真实主键回读表头，删除以「查不到或 dr=1」为准，
 * 其余动作以「表头存在且状态字段可读」为准，并把持久化单号回填结果。原生异常原样上抛、不重试。</p>
 */
public class SaleInvoiceOperationAdapter implements ISaleInvoiceOperationAdapter {
    /** 在执行业务前核对固定路由、启用边界、命令类型、授权对象、组织、交易类型与流程参数。 */
    public void validate(OperationContext context, Serializable payload) throws BusinessException {
        SaleInvoiceActionRoute route = SaleInvoiceActionRoute.forContext(context);
        if (route == null) { throw new BusinessException("销售发票受控动作需要本节点上下文。"); }
        // 未完成受控接入的按钮（本批为删除）必须在任何载荷核对与原生调用之前拒绝。
        route.requireEnabled();
        require(payload instanceof SalesPfActionCommand, "SalesPfActionCommand required");
        SalesPfActionCommand command = (SalesPfActionCommand) payload;
        AggregatedValueObject[] bills = command.getBills();
        require(bills != null && bills.length == 1, "销售发票 PF 受控动作只支持一张已核实的发票");
        AggregatedValueObject rawBill = bills[0];
        require(rawBill instanceof SaleInvoiceVO, "销售发票单据类型不是 nc.vo.so.m32.entity.SaleInvoiceVO");
        SaleInvoiceVO bill = (SaleInvoiceVO) rawBill;
        SaleInvoiceHVO header = bill.getParentVO();
        require(header != null && bill.getPrimaryKey() != null, "缺少原生命中发票主键或表头");
        require(route.getObjectPk().equals(bill.getPrimaryKey()), "发票主键与签名授权对象不一致");
        require(context.getGroupId() != null && context.getGroupId().equals(header.getPk_group())
                && context.getOrgId() != null && context.getOrgId().equals(header.getPk_org()),
                "发票集团或财务组织与授权不一致");
        // 本机配置只登记基本单据类型 32；交易类型编码若存在必须与表头一致，禁止迁就其他单据。
        String headerType = header.getVtrantypecode();
        String billType = command.getBillType();
        require(billType == null || (billType.matches("32(?:-[A-Za-z0-9_-]+)?")
                && (billType.equals(headerType) || (headerType == null && "32".equals(billType)))),
                "销售发票交易类型与授权不一致");
        // 证据：SaleInvoiceSaveAction.setUserObj() 每次新建 SaleinvoiceUserObject(isclientsave=true) 并装入
        // PfUserObject；其余动作由原生流程上下文提供同一类型的用户对象（真实 NC 会话待核实）。
        PfUserObject flowUser = command.getUserObject();
        require(flowUser != null && flowUser.getUserObject() instanceof SaleinvoiceUserObject,
                "销售发票缺少原生流程用户对象 SaleinvoiceUserObject");
        require(flowUser.getBusinessCheckMap() == null || flowUser.getBusinessCheckMap().isEmpty(),
                "销售发票 PF 动作不接受跳过检查参数");
        // 原生流程参数只允许已核实的白名单键，禁止任意脚本或流程载荷；审批窗口说明另由 workflownote 承载。
        for (Map.Entry<String, Object> entry : command.getParameters().entrySet()) {
            String key = entry.getKey();
            require("notechecked".equals(key) || "silently".equals(key) || "nosendmessage".equals(key)
                    || "besideapprove".equals(key), "销售发票 PF 参数未在白名单内：" + key);
            require(entry.getValue() instanceof String || entry.getValue() instanceof Boolean,
                    "销售发票 PF 参数类型不受支持：" + key);
        }
        // 审批/取消审批允许原生 WorkflownoteVO（含意见、决议、加签与指定审批人）；其余动作允许其为空。
    }

    /** 重新核对后按固定动作委托原生 PF，恰好一次；随后以持久化事实核对业务结果。 */
    public Serializable execute(OperationContext context, Serializable payload) throws BusinessException {
        SaleInvoiceActionRoute route = SaleInvoiceActionRoute.forContext(context);
        if (route == null) { throw new BusinessException("销售发票受控动作需要本节点上下文。"); }
        // validate 内含 requireEnabled，删除分支在这里就被拒绝，不会触达原生 PF。
        validate(context, payload);
        SalesPfActionCommand command = (SalesPfActionCommand) payload;
        SaleInvoiceVO bill = (SaleInvoiceVO) command.getBills()[0];
        String key = bill.getPrimaryKey();
        String billType = command.getBillType() == null ? "32" : command.getBillType();
        HashMap<String, Object> parameters = new HashMap<String, Object>(command.getParameters());
        Object raw = platformEntry().processAction(route.getKind().getNativeAction(), billType,
                command.getWorkflownote(), bill, command.getUserObject(), parameters);
        Serializable nativeResult = raw instanceof Serializable ? (Serializable) raw : null;
        SaleInvoiceHVO persisted = loadHeader(key);
        if (SaleInvoiceActionRoute.Kind.PF_DELETE == route.getKind()) {
            require(persisted == null || isDeleted(persisted), "原生删除后发票仍然有效");
            String code = persisted == null
                    ? (bill.getParentVO() == null ? null : bill.getParentVO().getVbillcode())
                    : persisted.getVbillcode();
            return new SalesPfActionResult(nativeResult, key, code, true);
        }
        require(persisted != null, "原生流程动作后未找到发票");
        require(persisted.getFstatusflag() != null, "原生流程动作后发票状态不可核对");
        SaleInvoiceVO returned = firstReturnedInvoice(raw);
        if (returned != null) { require(key.equals(returned.getPrimaryKey()), "原生 PF 返回发票与授权不一致"); }
        return new SalesPfActionResult(nativeResult, key, persisted.getVbillcode(), false);
    }

    /** 将真实结果还原为唯一根对象引用，供网关检查完整性并回填持久化主键与单号。 */
    public List<BusinessObjectRef> describeResult(OperationContext context, Serializable result)
            throws BusinessException {
        require(result instanceof SalesPfActionResult, "原生 PF 结果类型不正确");
        SalesPfActionResult outcome = (SalesPfActionResult) result;
        require(outcome.getPrimaryKey() != null && outcome.getPrimaryKey().trim().length() > 0,
                "原生 PF 结果缺少发票主键");
        if (context != null && context.getObjects() != null && context.getObjects().size() == 1) {
            BusinessObjectRef authorized = context.getObjects().get(0);
            require(authorized != null && outcome.getPrimaryKey().equals(authorized.getObjectPk()),
                    "原生 PF 结果与授权发票不一致");
        }
        return Collections.singletonList(new BusinessObjectRef("result", SalesOtherNodeScope.SALE_INVOICE,
                outcome.getPrimaryKey(), outcome.getBillCode(), null));
    }

    /** 从原生 PF 返回对象中取唯一发票，用于核对返回身份；非单据结果返回 null。 */
    private static SaleInvoiceVO firstReturnedInvoice(Object raw) throws BusinessException {
        Object value = raw;
        if (value instanceof PFReturnObject[]) {
            PFReturnObject[] wrapped = (PFReturnObject[]) value;
            require(wrapped.length == 1 && wrapped[0] != null, "原生 PF 返回包装不唯一");
            value = wrapped[0].getBills();
        } else if (value instanceof PFReturnObject) {
            value = ((PFReturnObject) value).getBills();
        }
        if (value instanceof SaleInvoiceVO[]) {
            SaleInvoiceVO[] bills = (SaleInvoiceVO[]) value;
            require(bills.length == 1 && bills[0] != null, "原生 PF 返回发票不唯一");
            return bills[0];
        }
        return null;
    }

    /** 真实删除以主键查不到或逻辑删除（dr=1）为准，不以成功回调代替。 */
    private static boolean isDeleted(SaleInvoiceHVO header) {
        Integer dr = header.getDr();
        return dr != null && dr.intValue() == 1;
    }

    /** 验证指定前置条件，不满足时立即抛出相应异常，阻止后续处理。 */
    private static void require(boolean condition, String message) throws BusinessException {
        if (!condition) { throw new BusinessException(message); }
    }

    /** 原生 PF 入口定位；离线测试可覆盖以断言动作名、交易类型、用户对象与流程参数。 */
    protected IplatFormEntry platformEntry() { return NCLocator.getInstance().lookup(IplatFormEntry.class); }

    /** 读取持久化表头以核对业务事实；离线测试可覆盖。 */
    protected SaleInvoiceHVO loadHeader(String key) throws BusinessException {
        return (SaleInvoiceHVO) new nc.bs.dao.BaseDAO().retrieveByPK(SaleInvoiceHVO.class, key,
                new String[] { "csaleinvoiceid", "pk_group", "pk_org", "vbillcode", "dr",
                        "vtrantypecode", "fstatusflag" });
    }
}
