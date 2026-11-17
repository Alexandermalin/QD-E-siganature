package nc.impl.uapbd.qd.esign;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.transaction.TransactionSynchronizationRegistry;
import nc.bs.uapbd.qd.esign.core.TransactionRollback;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.itf.uapbd.qd.esign.IMaterialPageDeleteTransactionService;
import nc.itf.uapbd.qd.esign.IMaterialPageDeleteTransactionService.Claim;
import nc.itf.uapbd.qd.esign.IMaterialPageDeleteTransactionService.UnitKey;
import nc.vo.bd.material.fi.MaterialFiVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.uapbd.qd.esign.MaterialPageDeleteCommand;

/** Verifies dispatcher decisions with test doubles, not real NC transaction commits. */
public final class ArchivePageDeleteDispatcherTests {
    private ArchivePageDeleteDispatcherTests() { }
    private static final nc.bs.uapbd.qd.esign.core.TransactionBoundaryProbe NO_TRANSACTION=
        new nc.bs.uapbd.qd.esign.core.TransactionBoundaryProbe() { public void assertNoTransaction() { } };
    public static void run() throws Exception {
        Map<String,SuperVO> rows=new LinkedHashMap<String,SuperVO>();
        rows.put("a",new MaterialFiVO());rows.put("b",new MaterialFiVO());rows.put("c",new MaterialFiVO());
        MaterialPageDeleteCommand command=new MaterialPageDeleteCommand(rows);
        Claim claim=new Claim("token",Arrays.asList(new UnitKey("1","a"),new UnitKey("2","b"),new UnitKey("3","c")));
        Logger log=Logger.getLogger(MaterialPageDeleteServiceImpl.class.getName());Level level=log.getLevel();log.setLevel(Level.OFF);
        try {
            Fake service=new Fake();service.marked=rollbackMarker();
            MaterialPageDeleteServiceImpl.dispatchUnits(service,"req",claim,command,false,NO_TRANSACTION);
            require(service.events.equals(Arrays.asList("apply:1","verify:1","apply:2","record:2:UNKNOWN","apply:3","record:3:UNKNOWN","finish")),
                "A rollback marker and an unknown outcome must not be reported as completed rollback");
            service=new Fake();service.marked=rollbackMarker();service.failRecording=true;
            try { MaterialPageDeleteServiceImpl.dispatchUnits(service,"req",claim,command,false,NO_TRANSACTION);throw new AssertionError("Unrecorded failure was ignored"); }
            catch(BusinessException expected) { require(!service.events.contains("finish") && !service.events.contains("apply:3"),
                "A failed outcome write must leave the request running and stop subsequent units"); }
            service=new Fake();
            try { MaterialPageDeleteServiceImpl.dispatchUnits(service,"req",new Claim("token",Arrays.asList(new UnitKey("1","a"),new UnitKey("2","a"))),command,false,NO_TRANSACTION);
                throw new AssertionError("Duplicate claim was accepted"); }
            catch(BusinessException expected) { require(service.events.isEmpty(),"Invalid claim must be rejected before any business unit executes"); }
            service=new Fake();service.marked=rollbackMarker();
            MaterialPageDeleteServiceImpl.dispatchUnits(service,"req",claim,command,true,NO_TRANSACTION);
            require(service.events.equals(Arrays.asList("apply:1","verify:1","apply:2","record:2:UNKNOWN","skip:3","finish")),
                "Warehouse deletes must retain client row order and stop after the first failure");
            service=new Fake();service.marked=rollbackMarker();service.failVerificationFor="1";
            MaterialPageDeleteServiceImpl.dispatchUnits(service,"req",claim,command,true,NO_TRANSACTION);
            require(service.events.equals(Arrays.asList("apply:1","verify:1","record:1:UNKNOWN","skip:2","skip:3","finish")),
                "A returned call without durable unit evidence must stop later warehouse deletes");
            service=new Fake();service.marked=rollbackMarker();final int[] checks={0};
            try { MaterialPageDeleteServiceImpl.dispatchUnits(service,"req",claim,command,true,
                new nc.bs.uapbd.qd.esign.core.TransactionBoundaryProbe() {
                    public void assertNoTransaction() throws BusinessException {
                        if(++checks[0]>=2)throw new BusinessException("transaction still active");
                    }
                });throw new AssertionError("Active delete transaction was accepted for readback"); }
            catch(BusinessException expected) { require(service.events.equals(Arrays.asList("apply:1"))
                && expected.getSuppressed().length==1,"Active transaction must prevent readback and independent failure recording"); }
            Map<String,SuperVO> reordered=new LinkedHashMap<String,SuperVO>();
            reordered.put("c",rows.get("c"));reordered.put("a",rows.get("a"));reordered.put("b",rows.get("b"));
            service=new Fake();service.marked=rollbackMarker();
            Claim reorderClaim=new Claim("token",Arrays.asList(new UnitKey("3","c"),new UnitKey("1","a"),new UnitKey("2","b")));
            MaterialPageDeleteServiceImpl.dispatchUnits(service,"req",reorderClaim,new MaterialPageDeleteCommand(reordered),true,NO_TRANSACTION);
            require(service.events.equals(Arrays.asList("apply:3","record:3:UNKNOWN","skip:1","skip:2","finish")),
                "An uncertain warehouse commit must stop all later selected rows");
            service=new Fake();service.marked=rollbackMarker();service.failSkipping=true;
            try { MaterialPageDeleteServiceImpl.dispatchUnits(service,"req",claim,command,true,NO_TRANSACTION);throw new AssertionError("Unrecorded skip was ignored"); }
            catch(BusinessException expected) { require(!service.events.contains("finish"),"Failure to record a skip must keep the request open for reconciliation"); }
            service=new Fake();
            try { MaterialPageDeleteServiceImpl.dispatchUnits(service,"req",claim,new MaterialPageDeleteCommand(reordered),true,NO_TRANSACTION);
                throw new AssertionError("Altered warehouse selection order was accepted"); }
            catch(BusinessException expected) { require(service.events.isEmpty(),"Altered order must fail before the first unit"); }
            List<Map<String,Object>> stored=new ArrayList<Map<String,Object>>();
            stored.add(EsignValues.row("object_id","b","client_object_id","b"));
            stored.add(EsignValues.row("object_id","a","client_object_id","a"));
            stored.add(EsignValues.row("object_id","c","client_object_id","c"));
            MaterialPageDeleteTransactionServiceImpl.orderWarehouseObjects(
                EsignValues.row("node_code","10140WARH","button_code","Delete","operation_type","DELETE"),stored);
            MaterialPageDeleteServiceImpl.requireCommand(stored,command,true);
            require("a".equals(stored.get(0).get("client_object_id")),"Warehouse order must be independent of database collation");
            try { MaterialPageDeleteServiceImpl.requireCommand(stored,new MaterialPageDeleteCommand(reordered),true);
                throw new AssertionError("Preparation order mismatch was accepted"); }
            catch(BusinessException expected) { }
            require("PARTIAL".equals(MaterialPageDeleteTransactionServiceImpl.summarize(Arrays.asList(
                EsignValues.row("unit_state","SUCCESS"),EsignValues.row("unit_state","SKIPPED"))))
                && "FAILED".equals(MaterialPageDeleteTransactionServiceImpl.summarize(Arrays.asList(
                    EsignValues.row("unit_state","FAILED"),EsignValues.row("unit_state","SKIPPED"))))
                && "UNKNOWN".equals(MaterialPageDeleteTransactionServiceImpl.summarize(Arrays.asList(
                    EsignValues.row("unit_state","UNKNOWN"),EsignValues.row("unit_state","SKIPPED")))),
                "Skipped rows must preserve the correct batch success, failure or uncertain outcome");
            Map<String,Object> request=EsignValues.row("node_code","10140CUG","button_code","Delete",
                "operation_type","DELETE","pk_group","G");
            Map<String,Object> root=EsignValues.row("object_id","root","client_object_id","signed-root",
                "object_type","BD_CUSTOMER","object_pk","C","policy_owner_org","OWNER");
            Map<String,Object> related=EsignValues.row("object_id","child","client_object_id","signed-parent",
                "object_type","BD_CUSTOMER","object_pk","P","parent_object_id","root","policy_owner_org","OTHER");
            List<Map<String,Object>> roots=MaterialPageDeleteServiceImpl.partnerRoots(request,Arrays.asList(related,root));
            nc.vo.bd.cust.CustomerVO customer=new nc.vo.bd.cust.CustomerVO();customer.setPk_customer("C");
            customer.setPk_group("G");customer.setPk_org("OWNER");
            Map<String,SuperVO> selectedRows=new LinkedHashMap<String,SuperVO>();selectedRows.put("signed-root",customer);
            MaterialPageDeleteCommand selectedCommand=new MaterialPageDeleteCommand(selectedRows);
            MaterialPageDeleteServiceImpl.requireCommand(roots,selectedCommand,false);
            MaterialPageDeleteServiceImpl.requirePartnerCommands(request,roots,selectedCommand);
            selectedRows.put("signed-parent",customer);
            try { MaterialPageDeleteServiceImpl.requireCommand(roots,new MaterialPageDeleteCommand(selectedRows),false);
                throw new AssertionError("Related audit archive accepted as a delete command"); }
            catch(BusinessException expected) { }
            assignmentDispatch();
        } finally { log.setLevel(level); }
        System.out.println("PASS ArchivePageDeleteDispatcherTests (unit evidence, boundary and unknown; no NC transaction)");
    }
    private static void assignmentDispatch() throws Exception {
        final List<String> events=new ArrayList<String>();Logger log=Logger.getLogger("assignment-dispatch-test");
        Level old=log.getLevel();log.setLevel(Level.OFF);
        AssignmentDispatch.UnitAction action=new AssignmentDispatch.UnitAction() {
            public void apply(String id) { events.add("apply:"+id); }
            public void verify(String id) throws BusinessException {
                events.add("verify:"+id);if("2".equals(id))throw new BusinessException("unit not durable");
            }
            public void unknown(String id) { events.add("unknown:"+id); }
        };
        nc.bs.uapbd.qd.esign.core.TransactionBoundaryProbe boundary=new nc.bs.uapbd.qd.esign.core.TransactionBoundaryProbe() {
            public void assertNoTransaction() { }
        };
        try {
            AssignmentDispatch.run("req",Arrays.asList("1","2","3"),action,boundary,log);
            require(events.equals(Arrays.asList("apply:1","verify:1","apply:2","verify:2","unknown:2")),
                "Assignment continued after a unit without durable evidence");
            events.clear();
            try { AssignmentDispatch.run("req",Arrays.asList("1","1"),action,boundary,log);throw new AssertionError("Duplicate assignment claim accepted"); }
            catch(BusinessException expected) { require(events.isEmpty(),"Duplicate claim was checked after a write"); }
            final int[] checks={0};events.clear();
            try { AssignmentDispatch.run("req",Arrays.asList("1","2"),action,new nc.bs.uapbd.qd.esign.core.TransactionBoundaryProbe() {
                public void assertNoTransaction() throws BusinessException {
                    if(++checks[0]>=2)throw new BusinessException("transaction still active");
                }
            },log);throw new AssertionError("An active assignment transaction was accepted for readback"); }
            catch(BusinessException expected) { require(events.equals(Arrays.asList("apply:1")) && expected.getSuppressed().length==1,
                "Active transaction did not block readback and independent failure recording"); }
            nc.bs.uapbd.qd.esign.persistence.QdEsignStore store=new nc.bs.uapbd.qd.esign.persistence.QdEsignStore() {
                public Map<String,Object> one(String sql,nc.jdbc.framework.SQLParameter parameters) { return EsignValues.row("audit_count",Integer.valueOf(1)); }
            };
            Map<String,Object> request=EsignValues.row("request_id","R","pk_group","G","pk_org","B");
            Map<String,Object> unit=EsignValues.row("object_id","U","request_id","R","pk_group","G","pk_org","B",
                "unit_state","SUCCESS","unit_finished_at","2026-11-12 00:00:00","owner_audit_enabled","Y");
            AssignmentUnitVerification.require(store,request,unit,"SUCCESS","ALREADY_ASSIGNED");
            nc.bs.uapbd.qd.esign.persistence.QdEsignStore missingAudit=new nc.bs.uapbd.qd.esign.persistence.QdEsignStore() {
                public Map<String,Object> one(String sql,nc.jdbc.framework.SQLParameter parameters) { return EsignValues.row("audit_count",Integer.valueOf(0)); }
            };
            try { AssignmentUnitVerification.require(missingAudit,request,unit,"SUCCESS","ALREADY_ASSIGNED");
                throw new AssertionError("Missing assignment audit was accepted"); } catch(BusinessException expected) { }
            unit.put("unit_state","PENDING");
            try { AssignmentUnitVerification.require(store,request,unit,"SUCCESS","ALREADY_ASSIGNED");
                throw new AssertionError("Pending assignment was reported as committed"); } catch(BusinessException expected) { }
            unit.put("unit_state","ALREADY_ASSIGNED");AssignmentUnitVerification.require(store,request,unit,"SUCCESS","ALREADY_ASSIGNED");
            unit.put("pk_org","OTHER");
            try { AssignmentUnitVerification.require(store,request,unit,"SUCCESS","ALREADY_ASSIGNED");
                throw new AssertionError("An assignment unit crossed organizations"); } catch(BusinessException expected) { }
            unit.put("pk_org","B");unit.put("unit_state","SUCCESS");
            MaterialPageDeleteTransactionServiceImpl.requireCommittedMember(request,unit);
            unit.put("unit_finished_at",null);
            try { MaterialPageDeleteTransactionServiceImpl.requireCommittedMember(request,unit);
                throw new AssertionError("An unfinished delete was reported as committed"); } catch(BusinessException expected) { }
        } finally { log.setLevel(old); }
    }
    private static BusinessException rollbackMarker() {
        final Object key=new Object();final boolean[] marked={false};
        TransactionSynchronizationRegistry registry=(TransactionSynchronizationRegistry)java.lang.reflect.Proxy.newProxyInstance(
            TransactionSynchronizationRegistry.class.getClassLoader(),new Class<?>[]{TransactionSynchronizationRegistry.class},
            new java.lang.reflect.InvocationHandler(){public Object invoke(Object proxy,java.lang.reflect.Method method,Object[] args){
                if("getTransactionKey".equals(method.getName())) return key;
                if("setRollbackOnly".equals(method.getName())) { marked[0]=true;return null; }
                if("getRollbackOnly".equals(method.getName())) return Boolean.valueOf(marked[0]);
                throw new UnsupportedOperationException(method.getName()); }});
        return TransactionRollback.abort(registry,key,new BusinessException("audit failed"));
    }
    private static final class Fake implements IMaterialPageDeleteTransactionService {
        final List<String> events=new ArrayList<String>();BusinessException marked;boolean failRecording,failSkipping;String failVerificationFor;
        public Claim claim_RequiresNew(String id) { throw new UnsupportedOperationException(); }
        public void apply_RequiresNew(String id,String token,String objectId,SuperVO row) throws BusinessException {
            events.add("apply:"+objectId);
            if("2".equals(objectId)) throw marked;
            if("3".equals(objectId)) throw new BusinessException("transport outcome unknown");
        }
        public void verifyUnit_RequiresNew(String id,String token,String objectId) throws BusinessException {
            events.add("verify:"+objectId);if(objectId.equals(failVerificationFor))throw new BusinessException("unit not durable");
        }
        public void recordUncommitted_RequiresNew(String id,String token,String objectId,boolean rolledBack) throws BusinessException {
            events.add("record:"+objectId+":"+(rolledBack?"FAILED":"UNKNOWN"));if(failRecording) throw new BusinessException("record failed");
        }
        public void skip_RequiresNew(String id,String token,String objectId) throws BusinessException {
            events.add("skip:"+objectId);if(failSkipping) throw new BusinessException("skip recording failed");
        }
        public void finish_RequiresNew(String id,String token) { events.add("finish"); }
    }
    private static void require(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
}
