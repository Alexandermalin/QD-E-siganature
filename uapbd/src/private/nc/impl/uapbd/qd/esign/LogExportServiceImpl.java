package nc.impl.uapbd.qd.esign;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import nc.bs.uapbd.qd.esign.persistence.LogAccess;
import nc.bs.uapbd.qd.esign.persistence.QdLogRepository;
import nc.itf.uapbd.qd.esign.ILogExportService;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.log.CsvLogWriter;
import nc.vo.uapbd.qd.esign.log.LogKind;
import nc.vo.uapbd.qd.esign.log.LogQuery;

/** 服务端授权和限量的 CSV 导出实现，超过十万行或 64MiB 明确失败，不返回被静默截断的导出文件。 */
public final class LogExportServiceImpl implements ILogExportService {
    /**
     * MAX_ROWS：一次 CSV 导出的最大记录数，超过时失败。
     * MAX_BYTES：一次 CSV 导出的最大字节数，超过时失败。
     */
    private static final int MAX_ROWS=100000, MAX_BYTES=64*1024*1024;
    /** 审计导出版本 2；复用同一权限及组织过滤路径。 */
    public byte[] exportAuditV2(LogQuery filter, boolean currentPage) throws BusinessException {
        return exportCsv(LogKind.AUDIT, filter, currentPage);
    }
    /** 审计已加载页导出版本 2。 */
    public byte[] exportAuditLoadedV2(LogQuery filter, String[] eventIds) throws BusinessException {
        return exportLoadedCsv(LogKind.AUDIT, filter, eventIds);
    }
    /** 签名日志使用显式版本，避免旧服务端忽略组织筛选。 */
    public byte[] exportSignatureV2(LogQuery filter, boolean currentPage) throws BusinessException {
        return exportCsv(LogKind.SIGNATURE, filter, currentPage);
    }
    /** 按已加载签名事件导出并重验筛选。 */
    public byte[] exportSignatureLoadedV2(LogQuery filter, String[] eventIds) throws BusinessException {
        return exportLoadedCsv(LogKind.SIGNATURE, filter, eventIds);
    }
    /** 按原筛选导出当前页或受限的全部结果；全部导出使用一次查询的读一致性，超限明确失败。 */
    public byte[] exportCsv(LogKind kind,LogQuery filter,boolean currentPage) throws BusinessException {
        LogAccess access=LogAccess.resolve(kind,"EXPORT"); QdLogRepository repository=new QdLogRepository();
        List<Map<String,Object>> rows=currentPage
            ? repository.query(kind,filter,access.groupId,access.organizations,access.includeGroupSecurity)
            : repository.queryForExport(kind,filter,access.groupId,access.organizations,MAX_ROWS,access.includeGroupSecurity);
        return serialize(kind,kind==LogKind.SECURITY?rows:repository.withOrganizationNames(rows,access.groupId));
    }
    /** 精确导出已加载页的 1 至 1000 个唯一事件 ID，重验原筛选及权限并读取当前值；缺失或越权均失败，不宣称保留之前加载时的旧值。 */
    public byte[] exportLoadedCsv(LogKind kind,LogQuery filter,String[] eventIds) throws BusinessException {
        LogAccess access=LogAccess.resolve(kind,"EXPORT");
        QdLogRepository repository=new QdLogRepository();
        List<Map<String,Object>> rows=repository.queryLoaded(kind,filter,access.groupId,access.organizations,eventIds,access.includeGroupSecurity);
        return serialize(kind,kind==LogKind.SECURITY?rows:repository.withOrganizationNames(rows,access.groupId));
    }
    /** 按固定列顺序序列化 CSV，并以受限输出流检查累计字节数；失败时不返回不完整导出内容。 */
    private byte[] serialize(LogKind kind,List<Map<String,Object>> rows) throws BusinessException {
        List<String> columns=kind==LogKind.SECURITY
            ? Arrays.asList("event_time","user_code","user_name","event_type","result_code","client_ip","terminal_name","source_event_id")
            : Arrays.asList("event_time","user_code","user_name","node_name","operation_type","bill_code","result_code","reason","pk_org","qd_org_name","policy_owner_org","qd_owner_org_name","target_org","qd_target_org_name","node_code","button_code","client_ip","terminal_name","request_id","batch_id");
        List<String> titles=kind==LogKind.SECURITY
            ? Arrays.asList("事件时间(UTC)","操作账号","操作人","安全事件","结果","客户端IP","终端名","原生日志编号")
            : Arrays.asList("事件时间(UTC)","操作账号","操作人","业务节点","操作类型","单据编码","结果","事由","操作组织主键","操作组织","策略所属组织主键","策略所属组织","目标组织主键","目标组织","节点编码","按钮编码","客户端IP","终端名","请求编号","批次编号");
        final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        OutputStream limited=new OutputStream() {
            /** 向输出流写入 CSV 内容并检查累计字节上限，超限抛出 IOException。 */
            public void write(int value) throws IOException {
                if(bytes.size()>=MAX_BYTES) { throw new IOException("CSV exceeds 64 MiB; narrow the filter"); } bytes.write(value);
            }
            /** 向输出流写入 CSV 内容并检查累计字节上限，超限抛出 IOException。 */
            public void write(byte[] data,int offset,int length) throws IOException {
                if(length>MAX_BYTES-bytes.size()) { throw new IOException("CSV exceeds 64 MiB; narrow the filter"); } bytes.write(data,offset,length);
            }
        };
        List<Map<String,Object>> displayRows=rows;
        if(kind!=LogKind.SECURITY){
            displayRows=new java.util.ArrayList<Map<String,Object>>();
            for(Map<String,Object> row:rows){
                Map<String,Object> copy=new java.util.LinkedHashMap<String,Object>(row);
                if(copy.get("policy_owner_org")==null)copy.put("policy_owner_org","未记录");
                if(copy.get("target_org")==null)copy.put("target_org","未记录");
                displayRows.add(copy);
            }
        }
        try { CsvLogWriter.write(limited,columns,titles,displayRows,MAX_ROWS); }
        catch(IOException failure) { BusinessException e=new BusinessException("CSV export failed: " + failure.getMessage()); e.initCause(failure); throw e; }
        return bytes.toByteArray();
    }
}
