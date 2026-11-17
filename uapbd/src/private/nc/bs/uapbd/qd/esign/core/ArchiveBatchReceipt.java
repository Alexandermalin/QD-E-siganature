package nc.bs.uapbd.qd.esign.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.ArchiveBatchResult;
import nc.vo.uapbd.qd.esign.ArchiveBatchUpdateResult;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;

/** Rejects missing, duplicate or fabricated per-object outcomes from native batch adapters. */
public final class ArchiveBatchReceipt {
    private final Map<String,ArchiveBatchResult.Unit> byClient;
    private final String state;
    private ArchiveBatchReceipt(Map<String,ArchiveBatchResult.Unit> units,String state) {
        this.byClient=Collections.unmodifiableMap(units);this.state=state;
    }
    public String getState() { return state; }
    public ArchiveBatchResult.Unit get(String clientId) { return byClient.get(clientId); }

    public static ArchiveBatchReceipt verify(List<BusinessObjectRef> objects,ArchiveBatchResult result) throws BusinessException {
        EsignValues.require(objects!=null && !objects.isEmpty() && result!=null && result.getUnits()!=null,
            "档案批量执行结果不完整。");
        return verifyUnits(objects,result.getUnits(),false);
    }
    public static ArchiveBatchReceipt verifyUpdate(List<BusinessObjectRef> objects,ArchiveBatchUpdateResult result) throws BusinessException {
        EsignValues.require(objects!=null && !objects.isEmpty() && result!=null && result.getNativeResult()!=null && result.getUnits()!=null,
            "档案批改执行结果不完整。");
        return verifyUnits(objects,result.getUnits(),false);
    }
    /** Only a verified page receipt can authorize a partially written archive outcome. */
    public static ArchiveBatchReceipt verifyOrganization(String requestId,String group,String org,NativeOrgBatchPage page,
            List<BusinessObjectRef> objects,NativeOrgBatchReceipt.Evidence evidence) throws BusinessException {
        EsignValues.require(evidence!=null && evidence.matches(requestId,group,org,page),"组织批改回执不属于当前请求。");
        List<ArchiveBatchResult.Unit> units=new java.util.ArrayList<ArchiveBatchResult.Unit>();
        for(NativeOrgBatchReceipt.Header header:evidence.getHeaders())
            units.add(new ArchiveBatchResult.Unit(header.getClientId(),header.getRootPk(),header.getState()));
        return verifyUnits(objects,units,true);
    }
    private static ArchiveBatchReceipt verifyUnits(List<BusinessObjectRef> objects,List<ArchiveBatchResult.Unit> result,boolean allowPartial)
            throws BusinessException {
        EsignValues.require(objects!=null && !objects.isEmpty() && result!=null,"档案批量执行结果不完整。");
        Map<String,String> expected=new LinkedHashMap<String,String>();
        for(BusinessObjectRef ref:objects) EsignValues.require(ref!=null && ref.getClientObjectId()!=null
            && ref.getObjectPk()!=null && expected.put(ref.getClientObjectId(),ref.getObjectPk())==null,"档案批量授权对象不完整。");
        Map<String,ArchiveBatchResult.Unit> units=new LinkedHashMap<String,ArchiveBatchResult.Unit>();
        boolean success=false,failed=false;
        for(ArchiveBatchResult.Unit unit:result) {
            EsignValues.require(unit!=null && unit.getClientId()!=null && expected.containsKey(unit.getClientId())
                && expected.get(unit.getClientId()).equals(unit.getObjectPk()) && units.put(unit.getClientId(),unit)==null,
                "档案批量结果与授权对象不一致。");
            String value=unit.getState();
            if("SUCCESS".equals(value)) success=true;
            else if("FAILED".equals(value)) failed=true;
            else if(allowPartial && "PARTIAL".equals(value)) { success=true;failed=true; }
            else EsignValues.require("UNCHANGED".equals(value),"档案批量结果状态不正确。");
        }
        EsignValues.require(units.size()==expected.size(),"档案批量结果缺少所选对象。");
        return new ArchiveBatchReceipt(units,failed?(success?"PARTIAL":"FAILED"):"SUCCESS");
    }
}
