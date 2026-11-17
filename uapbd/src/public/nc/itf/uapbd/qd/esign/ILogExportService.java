package nc.itf.uapbd.qd.esign;

import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.log.LogKind;
import nc.vo.uapbd.qd.esign.log.LogQuery;

/** 日志 CSV 的远程导出契约，返回受行数和字节数限制的内容；客户端仅负责保存到用户选定的位置。 */
public interface ILogExportService {
    /** 按原筛选导出当前页或受限的全部结果；全部导出使用一次查询的读一致性，超限明确失败。 */
    byte[] exportCsv(LogKind kind, LogQuery filter, boolean currentPage) throws BusinessException;
    /** 精确导出已加载页的 1 至 1000 个唯一事件 ID，重验原筛选及权限并读取当前值；缺失或越权均失败，不宣称保留之前加载时的旧值。 */
    byte[] exportLoadedCsv(LogKind kind,LogQuery filter,String[] eventIds) throws BusinessException;
    /** 审计导出版本 2，执行组织筛选及 EXPORT 授权。 */
    byte[] exportAuditV2(LogQuery filter, boolean currentPage) throws BusinessException;
    /** 审计已加载页导出版本 2，重验筛选和权限。 */
    byte[] exportAuditLoadedV2(LogQuery filter, String[] eventIds) throws BusinessException;
    /** 签名日志版本 2：导出时复核组织与导出权限。 */
    byte[] exportSignatureV2(LogQuery filter, boolean currentPage) throws BusinessException;
    /** 签名日志版本 2：按已加载记录重新校验筛选并导出。 */
    byte[] exportSignatureLoadedV2(LogQuery filter, String[] eventIds) throws BusinessException;
}
