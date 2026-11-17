package nc.bs.uapbd.qd.esign.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;

/** Aggregates independently reconciled page rows without hiding mixed outcomes under one archive. */
public final class NativeOrgBatchOutcome {
    private NativeOrgBatchOutcome() { }
    public static final class Root {
        private final String clientId,pk,state;private final int succeeded,failed,unchanged;
        private Root(String clientId,String pk,int succeeded,int failed,int unchanged) {
            this.clientId=clientId;this.pk=pk;this.succeeded=succeeded;this.failed=failed;this.unchanged=unchanged;
            this.state=failed>0?(succeeded>0?"PARTIAL":"FAILED"):succeeded>0?"SUCCESS":"UNCHANGED";
        }
        public String getClientId() { return clientId; }
        public String getPk() { return pk; }
        public String getState() { return state; }
        public int getSucceeded() { return succeeded; }
        public int getFailed() { return failed; }
        public int getUnchanged() { return unchanged; }
        public int getPageCount() { return succeeded+failed+unchanged; }
    }
    private static final class Counts { int succeeded,failed,unchanged; }

    /** Inputs must come from the checked impact and native per-page snapshot reconciliation. */
    public static List<Root> group(String group,String domain,List<BusinessObjectRef> roots,NativeOrgBatchSelection.Impact impact,
            List<NativeBatchUpdateOutcome.Unit> pages) throws BusinessException {
        EsignValues.require(impact!=null && impact.matches(group,domain,roots) && pages!=null,
            "组织页签批改缺少已核实的主档或页签结果。");
        Map<String,NativeOrgBatchSelection.Row> expected=new HashMap<String,NativeOrgBatchSelection.Row>();
        for(NativeOrgBatchSelection.Row row:impact.getRows()) EsignValues.require(expected.put(row.getId(),row)==null,
            "组织页签批改实际行重复。");
        Map<String,Counts> totals=new LinkedHashMap<String,Counts>();
        for(BusinessObjectRef ref:roots) EsignValues.require(totals.put(ref.getObjectPk(),new Counts())==null,
            "组织页签批改主档结果重复。");
        Set<String> clientIds=new HashSet<String>();
        for(NativeBatchUpdateOutcome.Unit unit:pages) {
            NativeOrgBatchSelection.Row row=unit==null?null:expected.remove(unit.getPk());
            EsignValues.require(row!=null && row.getId().equals(unit.getClientId()) && clientIds.add(unit.getClientId()),
                "组织页签批改结果包含重复或未授权的行。");
            Counts count=totals.get(row.getRoot());EsignValues.require(count!=null,"组织页签批改结果缺少主档。");
            if("SUCCESS".equals(unit.getState())) count.succeeded++;
            else if("FAILED".equals(unit.getState())) count.failed++;
            else if("UNCHANGED".equals(unit.getState())) count.unchanged++;
            else throw new BusinessException("组织页签批改单行结果不明确。");
        }
        EsignValues.require(expected.isEmpty(),"组织页签批改缺少所选行的结果。");
        List<Root> result=new ArrayList<Root>();
        for(BusinessObjectRef ref:roots) {
            Counts count=totals.get(ref.getObjectPk());
            result.add(new Root(ref.getClientObjectId(),ref.getObjectPk(),count.succeeded,count.failed,count.unchanged));
        }
        return Collections.unmodifiableList(result);
    }
}
