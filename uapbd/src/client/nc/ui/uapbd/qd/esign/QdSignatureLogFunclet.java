package nc.ui.uapbd.qd.esign;

import nc.vo.uapbd.qd.esign.log.LogKind;

/** 电子签名日志的 NC 功能入口。 */
public final class QdSignatureLogFunclet extends QdLogFunclet {
    private static final long serialVersionUID = 1L;

    public QdSignatureLogFunclet() {
        super("QD_ES_SIGNLOG", LogKind.SIGNATURE);
    }
}
