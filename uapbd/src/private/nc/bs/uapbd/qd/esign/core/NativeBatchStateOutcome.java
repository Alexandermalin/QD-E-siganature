package nc.bs.uapbd.qd.esign.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import nc.bs.bd.service.ErrLogElement;
import nc.bs.bd.service.ValueObjWithErrLog;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;

/** Classifies native batch state results without inferring success from an absent error. */
public final class NativeBatchStateOutcome {
    private NativeBatchStateOutcome() { }
    public static final class Unit {
        private final String clientId,pk,state,error;
        Unit(String clientId,String pk,String state,String error) { this.clientId=clientId;this.pk=pk;this.state=state;this.error=error; }
        public String getClientId() { return clientId; }
        public String getPk() { return pk; }
        public String getState() { return state; }
        public String getError() { return error; }
    }
    /** alreadyTarget contains persisted rows whose state already equaled the requested state before the native call. */
    public static List<Unit> reconcile(List<BusinessObjectRef> objects,Set<String> alreadyTarget,ValueObjWithErrLog outcome)
            throws BusinessException {
        EsignValues.require(objects!=null && !objects.isEmpty() && alreadyTarget!=null && outcome!=null,"原生批量状态结果不完整。");
        Map<String,BusinessObjectRef> expected=new HashMap<String,BusinessObjectRef>();Set<String> clients=new HashSet<String>();
        for(BusinessObjectRef ref:objects) EsignValues.require(ref!=null && present(ref.getObjectPk()) && present(ref.getClientObjectId())
            && expected.put(ref.getObjectPk(),ref)==null && clients.add(ref.getClientObjectId()),"批量档案对象缺少唯一标识。");
        EsignValues.require(expected.keySet().containsAll(alreadyTarget),"批量操作的原始状态范围不一致。");
        Map<String,String> states=new HashMap<String,String>(),errors=new HashMap<String,String>();
        SuperVO[] saved=outcome.getVos();
        if(saved!=null) for(SuperVO vo:saved) {
            String pk=vo==null?null:vo.getPrimaryKey();
            EsignValues.require(present(pk) && expected.containsKey(pk) && !alreadyTarget.contains(pk)
                && states.put(pk,"SUCCESS")==null,"原生批量操作返回了重复、未选择或状态异常的档案。");
        }
        List<ErrLogElement> failed=outcome.getErrLogList();
        if(failed!=null) for(ErrLogElement error:failed) {
            String pk=error==null?null:error.getErrPK();
            EsignValues.require(present(pk) && expected.containsKey(pk) && states.put(pk,"FAILED")==null,
                "原生批量错误无法与所选档案唯一对应。");
            errors.put(pk,error.getErrReason());
        }
        List<Unit> result=new ArrayList<Unit>();
        for(BusinessObjectRef ref:objects) {
            String pk=ref.getObjectPk(),state=states.get(pk);
            if(state==null && alreadyTarget.contains(pk)) state="UNCHANGED";
            EsignValues.require(state!=null,"原生批量操作未返回档案结果，事务必须回滚。");
            result.add(new Unit(ref.getClientObjectId(),pk,state,errors.get(pk)));
        }
        return Collections.unmodifiableList(result);
    }
    private static boolean present(String value) { return value!=null && !value.trim().isEmpty(); }
}
