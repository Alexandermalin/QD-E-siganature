package nc.vo.uapbd.qd.esign.log;

/** 日志类别枚举，用于在固定的签名、业务审计和安全日志查询路径之间选择，不能用于任意表名访问。 */
public enum LogKind {
// 依次为签名、业务审计和原生安全事实；类别到表的映射由服务端固定。
SIGNATURE, AUDIT, SECURITY }
