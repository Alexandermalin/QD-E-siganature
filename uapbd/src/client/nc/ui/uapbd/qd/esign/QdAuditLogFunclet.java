package nc.ui.uapbd.qd.esign;

import nc.vo.uapbd.qd.esign.log.LogKind;

/** 业务审计日志的 NC 功能入口。 */
public final class QdAuditLogFunclet extends QdLogFunclet {
    private static final long serialVersionUID = 1L;

    public QdAuditLogFunclet() {
        super("QD_ES_AUDITLOG", LogKind.AUDIT);
    }
}
