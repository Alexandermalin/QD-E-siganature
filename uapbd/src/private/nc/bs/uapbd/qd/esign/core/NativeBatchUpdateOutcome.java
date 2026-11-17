package nc.bs.uapbd.qd.esign.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import nc.vo.bd.errorlog.ErrLogResult;
import nc.vo.bd.errorlog.ErrLogReturnValue;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;

/** Reconciles NC batch-update returned VOs with authoritative per-object before/after snapshots. */
public final class NativeBatchUpdateOutcome {
    private NativeBatchUpdateOutcome() { }
    public static final class Unit {
        private final String clientId,pk,state;
        Unit(String clientId,String pk,String state) { this.clientId=clientId;this.pk=pk;this.state=state; }
        public String getClientId() { return clientId; }
        public String getPk() { return pk; }
        public String getState() { return state; }
    }
    /** unchanged is calculated server-side; excluded must be collected from actual native validation stages. */
    public static List<Unit> reconcile(List<BusinessObjectRef> selected,Set<String> unchanged,Map<String,String> excluded,Class<? extends SuperVO> nativeType,
            ErrLogReturnValue result,Map<String,List<SnapshotRow>> before,Map<String,List<SnapshotRow>> after) throws BusinessException {
        EsignValues.require(selected!=null && !selected.isEmpty() && unchanged!=null && excluded!=null && nativeType!=null && result!=null
            && result.getErrLogResult()!=null && result.getTotalNum()==selected.size() && before!=null && after!=null,
            "原生批改结果或所选范围不完整。");
        Map<String,BusinessObjectRef> pks=new HashMap<String,BusinessObjectRef>();Set<String> clients=new HashSet<String>();
        for(BusinessObjectRef ref:selected) EsignValues.require(ref!=null && ref.getObjectPk()!=null && ref.getClientObjectId()!=null
            && pks.put(ref.getObjectPk(),ref)==null && clients.add(ref.getClientObjectId()),"原生批改所选档案重复或缺少标识。");
        EsignValues.require(pks.keySet().containsAll(unchanged) && pks.keySet().containsAll(excluded.keySet())
            && Collections.disjoint(unchanged,excluded.keySet()) && before.keySet().equals(clients) && after.keySet().equals(clients),
            "原生批改的未变化对象或快照范围不一致。");
        for(String stage:excluded.values()) EsignValues.require("PERMISSION".equals(stage) || "VALIDATION".equals(stage)
            || "BEFORE_EVENT".equals(stage),"原生批改排除对象缺少校验证据。");
        Set<String> successes=new HashSet<String>();Object returned=result.getReturnValue();
        EsignValues.require(returned==null || returned instanceof SuperVO[],"原生批改没有返回可核对的业务对象。");
        if(returned!=null) for(SuperVO row:(SuperVO[])returned) {
            String pk=row==null?null:row.getPrimaryKey();
            EsignValues.require(row!=null && nativeType.equals(row.getClass()) && pk!=null && pks.containsKey(pk)
                && !unchanged.contains(pk) && !excluded.containsKey(pk) && successes.add(pk),"原生批改返回了重复、未选择或状态不符的档案。");
        }
        ErrLogResult errors=result.getErrLogResult();int errorCount=errors.getErrorMessagegNum();
        EsignValues.require(errorCount>=0,"原生批改错误数量无效。");int failed=0;
        List<Unit> units=new ArrayList<Unit>();
        for(BusinessObjectRef ref:selected) {
            String pk=ref.getObjectPk(),id=ref.getClientObjectId();List<SnapshotRow> prior=before.get(id),current=after.get(id);
            EsignValues.require(prior!=null && !prior.isEmpty() && current!=null && !current.isEmpty(),"原生批改档案快照缺失。");
            String state;
            if(successes.contains(pk)) state="SUCCESS";
            else {
                EsignValues.require(PartnerStateSnapshots.sameRows(prior,current),"原生批改未确认成功的档案发生了变化，事务必须回滚。");
                if(unchanged.contains(pk)) state="UNCHANGED";
                else { EsignValues.require(excluded.containsKey(pk),"原生批改未提供该档案的明确结果，事务必须回滚。");state="FAILED";failed++; }
            }
            units.add(new Unit(id,pk,state));
        }
        EsignValues.require(errorCount==0 || failed>0,"原生批改存在无法归属的错误，事务必须回滚。");
        return Collections.unmodifiableList(units);
    }
}
