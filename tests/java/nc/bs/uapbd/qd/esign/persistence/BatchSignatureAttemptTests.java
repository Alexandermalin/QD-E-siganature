package nc.bs.uapbd.qd.esign.persistence;

import java.util.Map;
import nc.bs.dao.DAOException;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.jdbc.framework.SQLParameter;

/** Exercises set-based signature evidence, count checks and locator behavior without Oracle. */
public final class BatchSignatureAttemptTests {
    private static final String REQUEST="0123456789abcdef0123456789abcdef",ATTEMPT="abcdef0123456789abcdef0123456789";
    private BatchSignatureAttemptTests() { }
    public static void run() throws Exception {
        Store store=new Store();store.counts=counts(10000,10000,10000,7500);store.written=7500;
        int saved=store.recordPartnerBatchAttempt(REQUEST,ATTEMPT,"SUCCESS",false,"复核","2026-11-30 16:00:00");
        require(saved==7500 && store.writes==1 && store.locators==1 && "202612".equals(store.month),
            "A large mixed-policy batch lost signature rows or cross-month locator");
        require(store.sql.contains("LOWER(RAWTOHEX(SYS_GUID()))") && store.sql.contains("o.owner_sign_required='Y'")
            && store.sql.contains("o.pk_group=r.pk_group") && store.sql.contains("o.pk_org=r.pk_org")
            && store.sql.contains("o.policy_owner_org") && store.sql.contains("r.policy_mode='OWNER_V1'")
            && store.sql.contains("r.signature_state='PENDING'") && store.sql.contains("o.relation_type='ROOT'")
            && store.sql.contains("o.target_org=r.pk_org") && store.sql.contains("o.object_type=CASE")
            && store.sql.contains("o.unit_state='PENDING'") && store.sql.contains("o.policy_owner_type IN ('G','O')")
            && store.parameters.getParameters().size()==7 && ATTEMPT.equals(store.parameters.get(0))
            && "SUCCESS".equals(store.parameters.get(1)) && "N".equals(store.parameters.get(2))
            && "复核".equals(store.parameters.get(3)) && "202612".equals(store.parameters.get(5))
            && REQUEST.equals(store.parameters.get(6)),"Set-based attempt lost authoritative identities or parameter order");
        reject(counts(10000,9999,9999,7500));
        reject(counts(2,2,1,1));
        reject(counts(2,2,2,3));
        reject(counts(200000,200000,200000,200000));
        reject(EsignValues.row("expected_count",null,"actual_count",1,"valid_count",1,"sign_count",1));
        store=new Store();store.counts=counts(2,2,2,0);
        require(store.recordPartnerBatchAttempt(REQUEST,ATTEMPT,"CANCELLED",false,null,"2026-11-30 15:59:59")==0
            && store.writes==0 && store.locators==0,"A batch without sign-required objects created misleading evidence");
        store=new Store();store.counts=counts(2,2,2,2);store.written=1;
        try { store.recordPartnerBatchAttempt(REQUEST,ATTEMPT,"SUCCESS",false,null,"2026-11-30 15:59:59");
            throw new AssertionError("Partial signature row insertion was accepted"); } catch(DAOException expected) { }
        require(store.locators==0,"A mismatched signature write advanced the archive locator");
        store=new Store();store.counts=counts(1,1,1,1);store.written=1;store.failLocator=true;
        try { store.recordPartnerBatchAttempt(REQUEST,ATTEMPT,"SUCCESS",false,null,"2026-11-30 15:59:59");
            throw new AssertionError("A failed locator update was swallowed"); } catch(DAOException expected) { }
        try { store.recordPartnerBatchAttempt(REQUEST,ATTEMPT,"UNTRUSTED",false,null,"2026-11-30 15:59:59");
            throw new AssertionError("An unsupported signature status was accepted"); } catch(IllegalArgumentException expected) { }
        try { store.recordPartnerBatchAttempt(REQUEST,ATTEMPT,"SUCCESS",true,null,"2026-11-30 15:59:59");
            throw new AssertionError("A successful signature was recorded as a locked account"); } catch(IllegalArgumentException expected) { }
        System.out.println("PASS BatchSignatureAttemptTests (set-based signing evidence and count checks; no NC or Oracle)");
    }
    private static void reject(Map<String,Object> counts) throws Exception {
        Store store=new Store();store.counts=counts;
        try { store.recordPartnerBatchAttempt(REQUEST,ATTEMPT,"SUCCESS",false,null,"2026-11-30 15:59:59");
            throw new AssertionError("Incomplete batch signature scope was accepted"); } catch(DAOException expected) { }
        require(store.writes==0 && store.locators==0,"Invalid batch scope reached evidence writes");
    }
    private static Map<String,Object> counts(int expected,int actual,int valid,int sign) {
        return EsignValues.row("expected_count",expected,"actual_count",actual,"valid_count",valid,"sign_count",sign);
    }
    private static void require(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
    private static final class Store extends QdEsignStore {
        Map<String,Object> counts;int writes,locators,written;String month,sql;SQLParameter parameters;boolean failLocator;
        @Override public Map<String,Object> one(String query,SQLParameter args) {
            if(query.contains("nls_database_parameters")) return EsignValues.row("value","AL32UTF8");
            require(query.contains("LEFT JOIN qd_es_object") && query.contains("owner_sign_required IN ('Y','N')")
                && query.contains("o.pk_group=r.pk_group") && query.contains("o.pk_org=r.pk_org")
                && query.contains("r.button_code='BatchUpdate'"),"Signature count query lost scope or owner validation");
            return counts;
        }
        @Override protected int executePartnerBatchAttemptSql(String statement,SQLParameter args) {
            writes++;sql=statement;parameters=args;return written;
        }
        @Override protected void ensurePartnerBatchAttemptLocator(String requestId,String value) throws DAOException {
            require(REQUEST.equals(requestId),"Locator was written for another request");locators++;month=value;
            if(failLocator) throw new DAOException("locator failure");
        }
    }
}
