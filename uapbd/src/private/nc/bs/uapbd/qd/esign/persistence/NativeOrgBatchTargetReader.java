package nc.bs.uapbd.qd.esign.persistence;

import java.util.List;
import java.util.Map;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.bs.uapbd.qd.esign.core.NativeOrgBatchPage;
import nc.bs.uapbd.qd.esign.core.NativeOrgBatchTargetScope;
import nc.vo.pub.BusinessException;

/** Internal target-scope reader; callers must authorize the request, archives, page and every target. */
public final class NativeOrgBatchTargetReader {
    private final QdEsignStore store;
    public NativeOrgBatchTargetReader() { this(new QdEsignStore()); }
    NativeOrgBatchTargetReader(QdEsignStore store) { if(store==null)throw new IllegalArgumentException("store");this.store=store; }
    public NativeOrgBatchTargetScope.Evidence read(Map<String,Object> request,List<Map<String,Object>> objects) throws BusinessException {
        EsignValues.require(request!=null && objects!=null,"组织批改目标查询缺少请求或档案。");
        List<Map<String,Object>> rows=NativeOrgBatchRecordReader.read(store,request,true);
        NativeOrgBatchPage page=NativeOrgBatchRecordReader.page(request,rows);
        return NativeOrgBatchTargetRecords.decode(request,objects,page,rows);
    }
}
