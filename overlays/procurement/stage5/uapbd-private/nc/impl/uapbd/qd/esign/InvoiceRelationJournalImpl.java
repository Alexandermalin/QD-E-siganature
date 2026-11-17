package nc.impl.uapbd.qd.esign;

import java.util.List;
import java.util.Map;

import nc.bs.dao.BaseDAO;
import nc.bs.dao.DAOException;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.bs.uapbd.qd.esign.core.RequestRules;
import nc.bs.uapbd.qd.esign.persistence.QdEsignStore;
import nc.bs.uapbd.qd.esign.security.EsignPermission;
import nc.bs.uapbd.qd.esign.security.ServerIdentity;
import nc.itf.uapbd.qd.esign.IInvoiceRelationJournal;
import nc.jdbc.framework.SQLParameter;
import nc.jdbc.framework.processor.ArrayProcessor;
import nc.jdbc.framework.processor.ColumnProcessor;
import nc.jdbc.framework.processor.ResultSetProcessor;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.OperationContext;

public final class InvoiceRelationJournalImpl implements IInvoiceRelationJournal {
    static final String INVOICE_NODE = "40041000";
    private static final String EXTERNAL_STARTED = "EXTERNAL_STARTED";
    private final Sql sql;

    public InvoiceRelationJournalImpl() {
        this(new Sql() {
            public int update(String statement, SQLParameter parameters) throws DAOException {
                return new BaseDAO().executeUpdate(statement, parameters);
            }

            public Object query(String statement, SQLParameter parameters, ResultSetProcessor processor)
                    throws DAOException {
                return new BaseDAO().executeQuery(statement, parameters, processor);
            }
        });
    }

    InvoiceRelationJournalImpl(Sql sql) {
        this.sql = sql;
    }

    @Override
    public void start_RequiresNew(OperationContext context, String invoicePk, String externalNumbers)
            throws BusinessException {
        EsignValues.require(context != null, "缺少采购发票签章请求。");
        String requestId = context.getRequestId();
        requireIdentifier(requestId, 64);
        requireIdentifier(invoicePk, 20);
        EsignValues.require(externalNumbers != null && externalNumbers.trim().length() > 0
                && externalNumbers.length() <= 1000, "数电发票号码缺失或超长。");

        ServerIdentity actor = ServerIdentity.current();
        QdEsignStore store = new QdEsignStore();
        Map<String, Object> request = SignatureServiceImpl.owned(store, requestId, actor, false);
        String button = EsignValues.text(request, "button_code");
        EsignValues.require("RUNNING".equals(request.get("business_state"))
                && RequestRules.mayExecute(EsignValues.text(request, "signature_state"), "READY")
                && INVOICE_NODE.equals(request.get("node_code"))
                && ("RelevanceInvoice".equals(button) || "UnRelevanceInvoice".equals(button))
                && button.equals(context.getButtonCode()), "采购发票签章请求不可执行。");
        List<Map<String, Object>> objects = store.loadObjects(requestId);
        EsignValues.require(objects != null && objects.size() == 1
                && "PU_PURCHASE_INVOICE".equals(objects.get(0).get("object_type"))
                && invoicePk.equals(objects.get(0).get("object_pk")), "签章对象与采购发票不一致。");

        String organization = invoiceOrganization(actor, invoicePk);
        EsignPermission.requireBusiness(actor, INVOICE_NODE, button, organization);
        assertClear(invoicePk);
        String now = EsignValues.utc();
        requireOne(sql.update("insert into qd_es_inv_ext (request_id, invoice_pk, group_id, org_id, user_id, "
                        + "button_code, external_numbers, phase, started_at, updated_at) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                parameters(requestId, invoicePk, actor.groupId, organization, actor.userId, button,
                        externalNumbers, EXTERNAL_STARTED, now, now)));
    }

    @Override
    public void checkpoint_RequiresNew(String requestId, String phase) throws BusinessException {
        requireIdentifier(requestId, 64);
        EsignValues.require(EXTERNAL_ACCEPTED.equals(phase) || EXTERNAL_REJECTED.equals(phase)
                || EXTERNAL_UNKNOWN.equals(phase), "无效的数电发票外部回传结果。");
        requireOne(sql.update("update qd_es_inv_ext set phase = ?, updated_at = ? "
                        + "where request_id = ? and phase = ? and resolved_at is null",
                parameters(phase, EsignValues.utc(), requestId, EXTERNAL_STARTED)));
    }

    @Override
    public void finish(String requestId) throws BusinessException {
        requireIdentifier(requestId, 64);
        String now = EsignValues.utc();
        requireOne(sql.update("update qd_es_inv_ext set updated_at = ?, resolved_at = ?, "
                        + "resolved_by = user_id, resolution_note = 'NC_CMT' "
                        + "where request_id = ? and phase = ? and resolved_at is null",
                parameters(now, now, requestId, EXTERNAL_ACCEPTED)));
    }

    @Override
    public void assertClear(String invoicePk) throws BusinessException {
        requireIdentifier(invoicePk, 20);
        Object pending = sql.query("select active_invoice_pk from qd_es_inv_ext where active_invoice_pk = ?",
                parameters(invoicePk), new ColumnProcessor(1));
        EsignValues.require(pending == null, "此采购发票有待核对的数电发票请求，请联系管理员核对后再操作。");
    }

    static String invoiceOrganization(ServerIdentity actor, String invoicePk) throws BusinessException {
        requireIdentifier(invoicePk, 20);
        Object[] invoice = (Object[]) new BaseDAO().executeQuery(
                "select pk_org from po_invoice where pk_invoice = ? and pk_group = ? and (dr = 0 or dr is null)",
                parameters(invoicePk, actor.groupId), new ArrayProcessor());
        EsignValues.require(invoice != null && invoice.length == 1 && invoice[0] instanceof String
                && ((String) invoice[0]).trim().length() > 0, "无法核对采购发票权限。");
        return ((String) invoice[0]).trim();
    }

    private static SQLParameter parameters(String... values) {
        SQLParameter result = new SQLParameter();
        for (String value : values) result.addParam(value);
        return result;
    }

    private static void requireIdentifier(String value, int maximum) throws BusinessException {
        EsignValues.require(value != null && value.length() > 0 && value.length() <= maximum
                && value.equals(value.trim()), "缺少有效的采购发票或签章请求编号。");
    }

    private static void requireOne(int count) throws BusinessException {
        EsignValues.require(count == 1, "数电发票核对记录已变化，请按请求编号核对。");
    }

    interface Sql {
        int update(String statement, SQLParameter parameters) throws DAOException;

        Object query(String statement, SQLParameter parameters, ResultSetProcessor processor) throws DAOException;
    }
}
