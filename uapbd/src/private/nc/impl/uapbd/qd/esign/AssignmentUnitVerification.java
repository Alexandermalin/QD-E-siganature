package nc.impl.uapbd.qd.esign;

import java.util.Arrays;
import java.util.Map;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.bs.uapbd.qd.esign.persistence.QdEsignStore;
import nc.jdbc.framework.SQLParameter;
import nc.vo.pub.BusinessException;

/** Checks the durable unit and its required audit after an independent assignment transaction. */
final class AssignmentUnitVerification {
    private AssignmentUnitVerification() { }
    static void require(QdEsignStore store,Map<String,Object> request,Map<String,Object> unit,String... allowed) throws BusinessException {
        EsignValues.require(store!=null && request!=null && unit!=null && allowed!=null
            && Arrays.asList(allowed).contains(EsignValues.text(unit,"unit_state")) && unit.get("unit_finished_at")!=null
            && unit.get("unit_error_code")==null && EsignValues.text(unit,"object_id")!=null
            && EsignValues.text(request,"request_id")!=null
            && EsignValues.text(request,"request_id").equals(EsignValues.text(unit,"request_id"))
            && EsignValues.text(request,"pk_group")!=null && EsignValues.text(request,"pk_group").equals(EsignValues.text(unit,"pk_group"))
            && EsignValues.text(request,"pk_org")!=null && EsignValues.text(request,"pk_org").equals(EsignValues.text(unit,"pk_org")),
            "分配单元的提交结果尚未确认。");
        if("SUCCESS".equals(EsignValues.text(unit,"unit_state")) && EsignValues.yes(unit,"owner_audit_enabled")) {
            SQLParameter params=new SQLParameter();params.addParam(EsignValues.text(request,"request_id"));
            params.addParam(EsignValues.text(unit,"object_id"));params.addParam(EsignValues.text(request,"pk_group"));
            params.addParam(EsignValues.text(request,"pk_org"));
            Map<String,Object> result=store.one("SELECT COUNT(*) audit_count FROM qd_es_audit WHERE request_id=? AND object_id=?"
                +" AND pk_group=? AND pk_org=? AND result_code='SUCCESS'",params);
            EsignValues.require(result!=null && EsignValues.number(result,"audit_count")==1,"分配单元的审计结果尚未确认。");
        }
    }
}
