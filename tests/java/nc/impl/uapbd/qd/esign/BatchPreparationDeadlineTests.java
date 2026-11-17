package nc.impl.uapbd.qd.esign;

import nc.bs.dao.DAOException;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.bs.uapbd.qd.esign.persistence.QdEsignStore;
import nc.vo.pub.BusinessException;

/** Checks the signing window after a long preparation without performing a database update. */
public final class BatchPreparationDeadlineTests {
    private static final String ID="0123456789abcdef0123456789abcdef";
    private BatchPreparationDeadlineTests() { }
    public static void run() throws Exception {
        FakeStore store=new FakeStore();long start=1700000000000L,ready=start+20L*60L*1000L;
        SignatureServiceImpl.finishBatchPreparation(store,ID,start,ready);
        require(ID.equals(store.id) && EsignValues.parseUtc(store.expiry)==ready+10L*60L*1000L && store.calls==1,
            "A long batch preparation consumed the user's signing window");
        store.affected=0;
        try { SignatureServiceImpl.finishBatchPreparation(store,ID,start,ready);throw new AssertionError("Missing or no longer pending request accepted"); }
        catch(DAOException expected) { }
        store.affected=2;
        try { SignatureServiceImpl.finishBatchPreparation(store,ID,start,ready);throw new AssertionError("Multiple updated requests accepted"); }
        catch(DAOException expected) { }
        int calls=store.calls;
        try { SignatureServiceImpl.finishBatchPreparation(store,ID,ready,start);throw new AssertionError("Clock moving backward was accepted"); }
        catch(BusinessException expected) { }
        try { SignatureServiceImpl.finishBatchPreparation(store,ID,0,Long.MAX_VALUE);throw new AssertionError("Deadline overflow was accepted"); }
        catch(BusinessException expected) { }
        try { store.finishBatchPreparation("invalid",EsignValues.utc(ready));throw new AssertionError("Invalid request ID was accepted"); }
        catch(IllegalArgumentException expected) { }
        require(store.calls==calls,"An invalid deadline reached the store update");
        RoutedStore routed=new RoutedStore();
        java.util.Map<String,Object> request=EsignValues.row("request_id",ID,"policy_mode","OWNER_V1","node_code","10140MAG",
            "button_code","BatchUpdate","operation_type","UPDATE");
        SignatureServiceImpl.recordAttempt(routed,request,"SUCCESS",null,false,"2026-11-30 15:59:59");
        require(routed.calls==1,"Quick-batch authentication did not use the set-based evidence path");
        routed.rows=0;
        try { SignatureServiceImpl.recordAttempt(routed,request,"PASSWORD_INCORRECT",null,false,"2026-11-30 15:59:59");
            throw new AssertionError("A failed password attempt without signed objects was accepted"); } catch(BusinessException expected) { }
        java.util.Map<String,Object> values=EsignValues.row("name","new");
        nc.vo.uapbd.qd.esign.PartnerBatchDraft main=new nc.vo.uapbd.qd.esign.PartnerBatchDraft("TAB","baseinfo","name",
            new String[]{"name"},values);
        nc.vo.uapbd.qd.esign.PartnerBatchDraft org=new nc.vo.uapbd.qd.esign.PartnerBatchDraft("TAB","finance_info","name",
            new String[]{"name"},values,new String[]{"T"},new nc.vo.uapbd.qd.esign.PartnerOrgBatchPlan.Page[0]);
        nc.vo.uapbd.qd.esign.BusinessObjectRef ref=new nc.vo.uapbd.qd.esign.BusinessObjectRef("a","BD_CUSTOMER","A",null,null);
        nc.vo.uapbd.qd.esign.OperationContext draft=new nc.vo.uapbd.qd.esign.OperationContext(null,null,"G","ORG","10140CUG",
            "客户","BatchUpdate","批改","UPDATE",null,java.util.Collections.singletonList(ref),
            nc.vo.uapbd.qd.esign.OperationContext.OWNER_POLICY_PROTOCOL,null,null,null,main);
        SignatureServiceImpl.requireExecutablePreparation(draft,true);
        draft=new nc.vo.uapbd.qd.esign.OperationContext(null,null,"G","ORG","10140CUG","客户","BatchUpdate","批改","UPDATE",null,
            java.util.Collections.singletonList(ref),nc.vo.uapbd.qd.esign.OperationContext.OWNER_POLICY_PROTOCOL,null,null,null,org);
        try { SignatureServiceImpl.requireExecutablePreparation(draft,true);
            throw new AssertionError("An unimplemented organization execution reached request creation"); } catch(BusinessException expected) { }
        System.out.println("PASS BatchPreparationDeadlineTests (preparation window and guarded update; no NC or Oracle)");
    }
    private static void require(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
    private static final class FakeStore extends QdEsignStore {
        int calls,affected=1;String id,expiry;
        @Override protected int updateBatchPreparationDeadline(String requestId,String expiresAt) {
            calls++;id=requestId;expiry=expiresAt;return affected;
        }
    }
    private static final class RoutedStore extends QdEsignStore {
        int calls,rows=1;
        @Override public int recordPartnerBatchAttempt(String requestId,String attemptId,String status,boolean locked,String reason,String now) {
            require(ID.equals(requestId) && attemptId!=null && attemptId.length()==32,"Incorrect batch attempt identity");
            calls++;return rows;
        }
        @Override public java.util.List<java.util.Map<String,Object>> loadObjects(String id) {
            throw new AssertionError("Set-based batch signing fell back to per-object reads");
        }
    }
}
