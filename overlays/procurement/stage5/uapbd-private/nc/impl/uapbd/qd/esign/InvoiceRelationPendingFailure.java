package nc.impl.uapbd.qd.esign;

import java.util.Map;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.bs.uapbd.qd.esign.persistence.QdEsignStore;
import nc.jdbc.framework.SQLParameter;
import nc.vo.pub.BusinessException;

/** Called after the core failure handler has locked and verified the owned RUNNING request. */
public final class InvoiceRelationPendingFailure {
    private InvoiceRelationPendingFailure() { }

    public static boolean retain(QdEsignStore store, Map<String, Object> request) throws BusinessException {
        if (!"RUNNING".equals(request.get("business_state")) || !"40041000".equals(request.get("node_code"))) {
            return false;
        }
        Object button = request.get("button_code");
        Object operation = request.get("operation_type");
        if (!("RelevanceInvoice".equals(button) && "RELATE_INVOICE".equals(operation))
                && !("UnRelevanceInvoice".equals(button) && "UNRELATE_INVOICE".equals(operation))) return false;
        String id = EsignValues.text(request, "request_id");
        SQLParameter params = new SQLParameter();
        params.addParam(id);
        Map<String, Object> open = store.one(
                "select request_id from qd_es_inv_ext where request_id = ? and resolved_at is null", params);
        if (open == null || open.isEmpty()) return false;
        store.updateRequest(id, EsignValues.row("error_code", "EXTERNAL_RECONCILE_REQUIRED"));
        return true;
    }
}
