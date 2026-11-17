package nc.ui.uapbd.qd.esign;

import nc.vo.uapbd.qd.esign.log.LogKind;

/** 登录与安全事件日志的 NC 功能入口。 */
public final class QdSecurityLogFunclet extends QdLogFunclet {
    private static final long serialVersionUID = 1L;

    public QdSecurityLogFunclet() {
        super("QD_ES_SECURITY", LogKind.SECURITY);
    }
}
