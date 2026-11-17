package nc.impl.pu.qd.esign;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import nc.bs.dao.BaseDAO;
import nc.bs.framework.common.NCLocator;
import nc.itf.pu.qd.esign.IInvoiceApOperationAdapter;
import nc.itf.uap.pf.IplatFormEntry;
import nc.jdbc.framework.SQLParameter;
import nc.vo.arap.payable.PayableBillItemVO;
import nc.vo.arap.payable.PayableBillVO;
import nc.vo.jcom.lang.StringUtil;
import nc.vo.pf.change.PfUtilBaseTools;
import nc.vo.pu.m25.entity.InvoiceHeaderVO;
import nc.vo.pu.m25.entity.InvoiceItemVO;
import nc.vo.pu.m25.entity.InvoiceSettleItemVO;
import nc.vo.pu.m25.entity.InvoiceVO;
import nc.vo.pu.m25.enumeration.InvoiceBillAction;
import nc.vo.pu.m25.env.InvoiceUIToBSEnv;
import nc.vo.pu.qd.esign.InvoiceApCommand;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.pub.VOStatus;
import nc.vo.pub.lang.UFBoolean;
import nc.vo.pubapp.pflow.PFReturnObject;
import nc.vo.pubapp.pflow.PfUserObject;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;

import static nc.vo.pu.qd.esign.InvoiceApCommand.validKey;

public final class InvoiceApOperationAdapter implements IInvoiceApOperationAdapter {
    private static final String BILL_TYPE = "25";
    private static final String AP_TYPE = "F1";
    private static final String INVOICE_KEY = "pk_invoice";
    private static final String PAYABLE_KEY = "pk_payablebill";
    private static final Integer APPROVED = Integer.valueOf(3); // POEnumBillStatus.APPROVE persisted value
    private final Records records;
    private final Platform platform;

    public InvoiceApOperationAdapter() {
        this(new DatabaseRecords(), new Platform() {
            @Override public Object run(String action, String billType, InvoiceVO bill, PfUserObject user,
                    Map<String, Object> parameters) throws BusinessException {
                return NCLocator.getInstance().lookup(IplatFormEntry.class).processAction(action, billType, null,
                        bill, user, new HashMap<String, Object>(parameters));
            }
        });
    }

    InvoiceApOperationAdapter(Records records, Platform platform) {
        this.records = records;
        this.platform = platform;
    }

    interface Records {
        InvoiceHeaderVO invoice(String pk) throws BusinessException;
        void verifyLines(InvoiceVO bill, String invoicePk) throws BusinessException;
        Set<String> payables(String invoicePk, String group) throws BusinessException;
        boolean payableExists(String payablePk) throws BusinessException;
    }

    interface Platform {
        Object run(String action, String billType, InvoiceVO bill, PfUserObject user,
                Map<String, Object> parameters) throws BusinessException;
    }

    @Override public void validate(OperationContext context, Serializable command) throws BusinessException {
        requireCommand(context, command);
    }

    @Override public Serializable execute(OperationContext context, Serializable command) throws BusinessException {
        InvoiceApCommand pf = requireCommand(context, command);
        boolean cancel = InvoiceApCommand.CANCEL.equals(pf.getAction());
        String pk = context.getObjects().get(0).getObjectPk();
        Set<String> before = records.payables(pk, context.getGroupId());
        if (before.isEmpty() == cancel) {
            throw new BusinessException("采购发票与现有应付单不一致，请刷新后核对。");
        }

        InvoiceUIToBSEnv env = new InvoiceUIToBSEnv();
        env.setBManual(UFBoolean.TRUE);
        env.setTrigger(cancel ? InvoiceBillAction.CANCELSENDAP : InvoiceBillAction.SENDAP);
        PfUserObject user = new PfUserObject();
        user.setUserObject(env);
        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("notechecked", "notechecked");
        if (pf.isReload()) parameters.put("reload_vo", pf.getBill());
        Object result = platform.run(pf.getAction(), pf.getBillType(), pf.getBill(), user, parameters);
        InvoiceHeaderVO persisted = requireOutcome(context, result);
        Set<String> after = records.payables(persisted.getPk_invoice(), context.getGroupId());
        if (after.isEmpty() != cancel) {
            throw new BusinessException("采购发票应付状态与实际应付单不一致，请按请求编号核对。");
        }
        if (cancel) for (String payable : before) {
            if (records.payableExists(payable)) {
                throw new BusinessException("取消传应付后原应付单仍存在，请按请求编号核对。");
            }
        }
        return (Serializable) result;
    }

    @Override public List<BusinessObjectRef> describeResult(OperationContext context, Serializable result)
            throws BusinessException {
        BusinessObjectRef ref = scope(context);
        InvoiceHeaderVO head = requireOutcome(context, result);
        return Collections.singletonList(new BusinessObjectRef(ref.getClientObjectId(), ref.getObjectType(),
                head.getPk_invoice(), head.getVbillcode(), null, ref.getDesiredOwnerOrg(),
                ref.getPolicyOwnerOrg(), ref.getTargetOrg()));
    }

    private InvoiceApCommand requireCommand(OperationContext context, Serializable command) throws BusinessException {
        BusinessObjectRef ref = scope(context);
        if (!(command instanceof InvoiceApCommand)) throw new BusinessException("缺少采购发票应付命令。");
        InvoiceApCommand pf = (InvoiceApCommand) command;
        InvoiceHeaderVO submitted = InvoiceApCommand.header(pf.getBill());
        if (!ref.getObjectPk().equals(submitted.getPk_invoice()) || !context.getOperationType().equals(pf.getAction())) {
            throw new BusinessException("应付命令与已授权采购发票或动作不匹配。");
        }
        InvoiceHeaderVO persisted = requireInvoice(context, ref.getObjectPk());
        unchanged(submitted, persisted);
        if (!nativeType(persisted).equals(pf.getBillType())) throw new BusinessException("采购发票交易类型不匹配。");
        boolean cancel = InvoiceApCommand.CANCEL.equals(pf.getAction());
        if (sent(persisted) != cancel || !cancel && !APPROVED.equals(persisted.getFbillstatus())) {
            throw new BusinessException("采购发票的当前状态不允许执行该应付动作，请刷新后核对。");
        }
        records.verifyLines(pf.getBill(), ref.getObjectPk());
        return pf;
    }

    private static BusinessObjectRef scope(OperationContext context) throws BusinessException {
        if (context == null || !InvoiceApCommand.NODE.equals(context.getNodeCode())
                || !InvoiceApCommand.actionFor(context.getButtonCode()).equals(context.getOperationType())
                || !validKey(context.getGroupId()) || !validKey(context.getOrgId()) || context.getObjects().size() != 1) {
            throw new BusinessException("采购发票应付操作需要一张已授权的采购发票。");
        }
        BusinessObjectRef ref = context.getObjects().get(0);
        if (ref == null || !InvoiceApCommand.OBJECT.equals(ref.getObjectType()) || !validKey(ref.getObjectPk())
                || !ref.getObjectPk().equals(ref.getClientObjectId()) || ref.getParentClientObjectId() != null) {
            throw new BusinessException("应付命令超出采购发票授权范围。");
        }
        return ref;
    }

    private InvoiceHeaderVO requireInvoice(OperationContext context, String pk) throws BusinessException {
        InvoiceHeaderVO persisted = records.invoice(pk);
        if (persisted == null || !pk.equals(persisted.getPk_invoice()) || !context.getGroupId().equals(persisted.getPk_group())
                || !context.getOrgId().equals(persisted.getPk_org())) {
            throw new BusinessException("采购发票不存在或所属集团和业务单元已变化。");
        }
        return persisted;
    }

    private InvoiceHeaderVO requireOutcome(OperationContext context, Object result) throws BusinessException {
        Object returned = result;
        if (returned instanceof Object[] && ((Object[]) returned).length == 1) returned = ((Object[]) returned)[0];
        if (returned != null && returned.getClass() == PFReturnObject.class) {
            Object[] bills = ((PFReturnObject) returned).getBills();
            returned = bills != null && bills.length == 1 ? bills[0] : null;
        }
        InvoiceHeaderVO head = InvoiceApCommand.header(returned instanceof InvoiceVO ? (InvoiceVO) returned : null);
        String pk = context.getObjects().get(0).getObjectPk();
        InvoiceHeaderVO persisted = requireInvoice(context, pk);
        boolean expected = InvoiceApCommand.SEND.equals(context.getOperationType());
        if (!pk.equals(head.getPk_invoice()) || !matches(head.getPk_group(), persisted.getPk_group())
                || !matches(head.getPk_org(), persisted.getPk_org())
                || head.getBapflag() != null && sent(head) != expected || sent(persisted) != expected) {
            throw new BusinessException("应付流程平台未返回已授权采购发票的持久化结果。");
        }
        return persisted;
    }

    private static boolean sent(InvoiceHeaderVO header) { return UFBoolean.TRUE.equals(header.getBapflag()); }

    private static boolean matches(String submitted, String persisted) {
        return submitted == null || submitted.equals(persisted);
    }

    private static String nativeType(InvoiceHeaderVO head) throws BusinessException {
        String type = head.getVtrantypecode();
        if (!validKey(type) || BILL_TYPE.equals(type) || StringUtil.isContainChinese(type)) return BILL_TYPE;
        if (BILL_TYPE.equals(PfUtilBaseTools.getRealBilltype(type))) return type;
        throw new BusinessException("采购发票交易类型超出应付授权范围。");
    }

    private static void unchanged(SuperVO submitted, SuperVO persisted) throws BusinessException {
        int status = submitted.getStatus();
        if ((status != VOStatus.UNCHANGED && status != VOStatus.UPDATED)
                || !Objects.equals(submitted.getAttributeValue("ts"), persisted.getAttributeValue("ts"))) {
            throw new BusinessException("采购发票或明细已发生变化，请刷新后重试。");
        }
        for (String field : submitted.usedAttributeNames()) {
            if (!Objects.equals(submitted.getAttributeValue(field), persisted.getAttributeValue(field))) {
                throw new BusinessException("采购发票应付操作包含超出授权范围的修改。");
            }
        }
    }

    private static final class DatabaseRecords implements Records {
        @Override public InvoiceHeaderVO invoice(String pk) throws BusinessException {
            return unique(query(InvoiceHeaderVO.class, INVOICE_KEY, pk));
        }

        @Override public void verifyLines(InvoiceVO bill, String invoicePk) throws BusinessException {
            List<InvoiceItemVO> items = query(InvoiceItemVO.class, INVOICE_KEY, invoicePk);
            for (InvoiceItemVO item : items) {
                if ("4812".equals(item.getCsourcetypecode())) {
                    throw new BusinessException("应付发票生成的采购发票由原 4812 流程处理。");
                }
            }
            verifyChildren(bill.getChildrenVO(), items);
            verifyChildren(bill.getInvoiceSettleItemVO(), query(InvoiceSettleItemVO.class, INVOICE_KEY, invoicePk));
        }

        @Override public Set<String> payables(String invoicePk, String group) throws BusinessException {
            Set<String> keys = new LinkedHashSet<String>();
            for (PayableBillItemVO item : query(PayableBillItemVO.class, "top_billid", invoicePk)) {
                String pk = item.getPk_payablebill();
                if (!validKey(pk) || !validKey(item.getPk_payableitem()) || !group.equals(item.getPk_group())) {
                    throw badPayable();
                }
                keys.add(pk);
            }
            for (String pk : keys) {
                PayableBillVO head = unique(query(PayableBillVO.class, PAYABLE_KEY, pk));
                if (head == null || !group.equals(head.getPk_group()) || !AP_TYPE.equals(head.getPk_billtype())
                        || !validKey(head.getPk_org()) || !validKey(head.getBillno())) throw badPayable();
                List<PayableBillItemVO> lines = query(PayableBillItemVO.class, PAYABLE_KEY, pk);
                if (lines.isEmpty()) throw badPayable();
                for (PayableBillItemVO line : lines) {
                    if (!validKey(line.getPk_payableitem()) || !invoicePk.equals(line.getTop_billid())
                            || !group.equals(line.getPk_group())) throw badPayable();
                }
            }
            return keys;
        }

        @Override public boolean payableExists(String payablePk) throws BusinessException {
            return unique(query(PayableBillVO.class, PAYABLE_KEY, payablePk)) != null;
        }

        private static <T extends SuperVO> void verifyChildren(T[] submitted, List<T> persisted) throws BusinessException {
            if (submitted == null) return;
            Map<String, T> current = new HashMap<String, T>();
            for (T row : persisted) current.put(row.getPrimaryKey(), row);
            for (T row : submitted) {
                T match = row == null ? null : current.remove(row.getPrimaryKey());
                if (match == null || row.getClass() != match.getClass()) {
                    throw new BusinessException("采购发票应付操作包含未经授权的明细。");
                }
                unchanged(row, match);
            }
        }

        private static <T extends SuperVO> List<T> query(Class<T> type, String key, String pk) throws BusinessException {
            SQLParameter parameters = new SQLParameter();
            parameters.addParam(pk);
            Collection<?> matches = new BaseDAO().retrieveByClause(type,
                    key + " = ? and (dr = 0 or dr is null)", parameters);
            List<T> rows = new ArrayList<T>();
            if (matches != null) for (Object match : matches) rows.add(type.cast(match));
            return rows;
        }

        private static <T> T unique(List<T> rows) throws BusinessException {
            if (rows.size() > 1) throw new BusinessException("采购发票应付单据的主键不唯一。");
            return rows.isEmpty() ? null : rows.get(0);
        }

        private static BusinessException badPayable() {
            return new BusinessException("无法唯一核对采购发票的应付单来源、集团或单据类型。");
        }
    }
}
