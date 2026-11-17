package nc.bs.uapbd.qd.esign.persistence;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.bs.uapbd.qd.esign.core.NativeOrgBatchPage;
import nc.bs.uapbd.qd.esign.core.PartnerBatchRoute;
import nc.vo.pub.BusinessException;

/** Shared internal loader for online and approved monthly organization-batch evidence. */
final class NativeOrgBatchRecordReader {
    private NativeOrgBatchRecordReader() { }
    static List<Map<String,Object>> read(QdEsignStore store,Map<String,Object> request,boolean targetOnly) throws BusinessException {
        String id=request==null?null:EsignValues.text(request,"request_id");boolean finished=request!=null && request.get("finished_at")!=null;
        EsignValues.require(store!=null && id!=null && id.matches("[a-fA-F0-9]{32}") && store.orgReceiptTableExists(),
            "组织批改证据请求或存储不可用。");
        List<Map<String,Object>> locators=store.list("SELECT archive_month,request_closed FROM qd_es_locator WHERE request_id=? ORDER BY archive_month",
            QdEsignStore.parameters(id));
        EsignValues.require(locators!=null,"组织批改证据月份定位查询失败。");Set<String> months=new LinkedHashSet<String>();
        for(Map<String,Object> row:locators) {
            String month=row==null?null:EsignValues.text(row,"archive_month");
            try { QdTableMetadata.historyTable(NativeOrgBatchReceiptRecords.TABLE,month); }
            catch(IllegalArgumentException invalid) { throw new BusinessException("组织批改证据月份定位无效。",invalid); }
            EsignValues.require((finished?"Y":"N").equals(EsignValues.text(row,"request_closed")) && months.add(month),
                "组织批改证据月份定位状态不一致或重复。");
        }
        EsignValues.require(!months.isEmpty(),"组织批改证据缺少月份定位。");
        String filter=targetOnly?" AND row_kind IN ('S','U')":"";
        List<Map<String,Object>> rows=new ArrayList<Map<String,Object>>();
        add(rows,store.list("SELECT "+QdTableMetadata.columnList(NativeOrgBatchReceiptRecords.TABLE)
            +" FROM qd_es_batch_page WHERE request_id=?"+filter,QdEsignStore.parameters(id)),months,null);
        if(finished) for(String month:months) {
            Map<String,Object> directory=store.one("SELECT archive_month FROM qd_es_archive WHERE archive_month=?",QdEsignStore.parameters(month));
            if(directory==null)continue;
            EsignValues.require(month.equals(EsignValues.text(directory,"archive_month")),"组织批改证据归档目录不一致。");
            store.requireHistoryTable(NativeOrgBatchReceiptRecords.TABLE,month);
            String table=QdTableMetadata.historyTable(NativeOrgBatchReceiptRecords.TABLE,month);
            add(rows,store.list("SELECT "+QdTableMetadata.columnList(NativeOrgBatchReceiptRecords.TABLE)+" FROM "+table
                +" WHERE request_id=? AND archive_month=?"+filter,QdEsignStore.parameters(id,month)),months,month);
        }
        EsignValues.require(!rows.isEmpty(),"组织批改证据记录缺失。");
        Map<String,Map<String,Object>> unique=new LinkedHashMap<String,Map<String,Object>>();
        Set<String> expected=new HashSet<String>(NativeOrgBatchReceiptRecords.columns());
        for(Map<String,Object> row:rows) {
            EsignValues.require(row.keySet().equals(expected),"组织批改证据列不完整。");
            String recordId=EsignValues.text(row,"record_id");
            EsignValues.require(NativeOrgBatchReceiptRecords.uuid(recordId),"组织批改证据标识无效。");
            Map<String,Object> prior=unique.get(recordId);
            EsignValues.require(prior==null || NativeOrgBatchReceiptRecords.same(prior,row),"组织批改证据在线与历史副本冲突。");
            if(prior==null)unique.put(recordId,row);
        }
        return new ArrayList<Map<String,Object>>(unique.values());
    }
    static NativeOrgBatchPage page(Map<String,Object> request,List<Map<String,Object>> rows) throws BusinessException {
        String domain=PartnerBatchRoute.domain(EsignValues.text(request,"node_code"));
        NativeOrgBatchPage page=NativeOrgBatchPage.require(domain,EsignValues.text(rows.get(0),"tab_code"));
        for(Map<String,Object> row:rows) EsignValues.require(page.tabCode.equals(EsignValues.text(row,"tab_code")),
            "组织批改证据包含多个页签。");
        return page;
    }
    private static void add(List<Map<String,Object>> target,List<Map<String,Object>> source,Set<String> months,String historyMonth)
            throws BusinessException {
        EsignValues.require(source!=null,"组织批改证据查询未返回结果集。");
        for(Map<String,Object> row:source) {
            String month=row==null?null:EsignValues.text(row,"archive_month");
            EsignValues.require(month!=null && months.contains(month) && (historyMonth==null || historyMonth.equals(month)),
                "组织批改证据记录不属于对应月份定位。");
            target.add(row);
        }
    }
}
