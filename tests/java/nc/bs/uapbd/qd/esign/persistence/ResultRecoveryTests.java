package nc.bs.uapbd.qd.esign.persistence;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import nc.bs.dao.DAOException;
import nc.bs.framework.common.InvocationInfoProxy;
import nc.bs.uapbd.qd.esign.core.ObjectRelations;
import nc.bs.uapbd.qd.esign.security.ServerIdentity;
import nc.impl.uapbd.qd.esign.SignatureServiceImpl;
import nc.jdbc.framework.SQLParameter;
import nc.vo.pub.BusinessException;
import nc.vo.sm.UserVO;

/** 仓储结果恢复回归：使用显式读边界替身，不调用数据库、认证、业务服务或NC事务。 */
public final class ResultRecoveryTests {
    private static final String ID="0123456789abcdef0123456789abcdef";
    private static int assertions;
    public static int run() throws Exception {
        assertions=0;
        FakeStore f=new FakeStore(); f.online=request("202608","SUCCESS");
        f.object=row("object_type","REAL_TYPE","object_pk","REAL_PK","bill_code","REAL_CODE");
        Map<String,Object> values=row("request_id",ID,"object_id","object-1");
        f.enrich("qd_es_signlog",values);
        check("REAL_TYPE".equals(values.get("object_type")),"Request NULL must not suppress actual signature object type");
        values=row("request_id",ID,"object_id","object-1","object_type","FORGED","object_pk","FORGED","bill_code","FORGED");
        f.enrich("qd_es_audit",values);
        check("REAL_TYPE".equals(values.get("object_type"))&&"REAL_PK".equals(values.get("object_pk"))&&"REAL_CODE".equals(values.get("bill_code")),"Payload cannot override authoritative audit identity");
        f.object=null;
        try{f.enrich("qd_es_signlog",row("request_id",ID,"object_id","missing"));fail("Missing object accepted");}catch(DAOException expected){assertions++;}

        f=new FakeStore(); f.online=request("202611","READY");
        check(f.loadRequestResult(ID)==f.online&&f.historyQueries==0,"Active online result preserves original path and does not inspect history");
        f=archived("SUCCESS");
        Map<String,Object> result=f.loadRequestResult(ID);
        check(QdEsignStore.isArchivedResult(result)&&"SUCCESS".equals(result.get("business_state")),"Closed archived request result restored via locator");
        check(f.historyQueries>0&&!f.historyLocked,"History is read only without FOR UPDATE");
        f=archived("FAILED");
        check("FAILED".equals(f.loadRequestResult(ID).get("business_state")),"Failed archived result remains queryable");
        final ServerIdentity identity=identity("user","group");
        f=archived("SUCCESS");
        result=SignatureServiceImpl.ownedForExecution(f,ID,identity);
        check(QdEsignStore.isArchivedResult(result)&&f.onlineLockQueries==1&&!f.historyLocked,"Gateway locks only online, then reads archived SUCCESS for replay");
        f=archived("FAILED");
        try{SignatureServiceImpl.ownedForExecution(f,ID,identity);fail("Historical failed request entered gateway");}catch(BusinessException expected){assertions++;}
        f=archived("READY");
        try{SignatureServiceImpl.ownedForExecution(f,ID,identity);fail("Historical ready authorization reused");}catch(BusinessException expected){assertions++;}
        f=archived("RUNNING");
        try{f.loadRequestResult(ID);fail("Historical RUNNING request treated as a completed result");}catch(DAOException expected){assertions++;}
        f=archived("SUCCESS");
        try{SignatureServiceImpl.owned(f,ID,identity("another-user","group"),false);fail("Cross-user archived result exposed");}catch(BusinessException expected){assertions++;}
        f=archived("SUCCESS");
        try{SignatureServiceImpl.owned(f,ID,identity("user","another-group"),false);fail("Cross-group archived result exposed");}catch(BusinessException expected){assertions++;}
        f=archived("SUCCESS");f.closed=false;
        try{f.loadRequestResult(ID);fail("Open locator accepted");}catch(DAOException expected){assertions++;}
        f=archived("SUCCESS");f.missing.add("qd_es_request_h202608");
        try{f.loadRequestResult(ID);fail("Missing request month table ignored");}catch(DAOException expected){assertions++;}
        f=archived("SUCCESS");f.requests.get("202608").put("archive_month","202609");
        try{f.loadRequestResult(ID);fail("Wrong request archive location accepted");}catch(DAOException expected){assertions++;}
        f=archived("SUCCESS");f.requests.get("202608").put("finished_at",null);
        try{f.loadRequestResult(ID);fail("Missing terminal timestamp accepted");}catch(DAOException expected){assertions++;}

        f=archived("SUCCESS");result=f.loadRequestResult(ID);
        f.onlineObjects.add(object("object-1","root",null,"202608"));
        f.objects.put("202608",Arrays.asList(object("object-1","root",null,"202608")));
        f.months.add("202609");f.objects.put("202609",Arrays.asList(object("object-2","child","object-1","202609")));f.proofs.put("202609",Arrays.asList(row("object_id","object-2")));
        List<Map<String,Object>> restored=f.loadResultObjects(result);
        check(restored.size()==2&&ObjectRelations.restore(restored).get(1).getParentClientObjectId().equals("root"),"Cross-month objects restore parent relation and deduplicate identical online copy");
        check(!f.historyLocked,"Object history is never locked");
        f=archived("SUCCESS");f.onlineObjects.add(object("object-1","root",null,"202608"));
        Map<String,Object> conflict=object("object-1","root",null,"202608");conflict.put("bill_code","DIFFERENT");f.objects.put("202608",Arrays.asList(conflict));
        try{f.loadResultObjects(f.loadRequestResult(ID));fail("Conflicting duplicate object accepted");}catch(DAOException expected){assertions++;}
        f=archived("SUCCESS");Map<String,Object> wrongOrg=object("object-1","root",null,"202608");wrongOrg.put("pk_org","other-org");f.objects.put("202608",Arrays.asList(wrongOrg));
        try{f.loadResultObjects(f.loadRequestResult(ID));fail("Cross-org object replayed");}catch(DAOException expected){assertions++;}
        f=archived("SUCCESS");f.objects.put("202608",Arrays.asList(object("object-1","same",null,"202608"),object("object-2","same",null,"202608")));
        try{f.loadResultObjects(f.loadRequestResult(ID));fail("Duplicate client object accepted");}catch(DAOException expected){assertions++;}
        f=archived("SUCCESS");f.objects.put("202608",Arrays.asList(object("object-2","child","missing-parent","202608")));
        try{f.loadResultObjects(f.loadRequestResult(ID));fail("Missing parent replayed");}catch(DAOException expected){assertions++;}
        f=archived("SUCCESS");f.missing.add("qd_es_object_h202608");
        try{f.loadResultObjects(f.loadRequestResult(ID));fail("Missing object month table ignored");}catch(DAOException expected){assertions++;}
        f=archived("SUCCESS");
        try{f.loadResultObjects(f.loadRequestResult(ID));fail("Empty committed object result accepted");}catch(DAOException expected){assertions++;}
        f=new FakeStore();f.online=request("202608","SUCCESS");f.months.add("202608");f.objects.put("202608",Arrays.asList(object("object-1","root",null,"202608")));f.proofs.put("202608",Arrays.asList(row("object_id","object-1")));
        check(f.loadResultObjects(f.online).size()==1,"Online SUCCESS recovers objects already moved by an earlier archive batch");
        f=archived("SUCCESS");f.objects.put("202608",Arrays.asList(object("object-1","root",null,"202608")));f.proofs.put("202608",Arrays.asList(row("object_id","object-1"),row("object_id","missing-root")));
        try{f.loadResultObjects(f.loadRequestResult(ID));fail("A missing independent root object escaped evidence comparison");}catch(DAOException expected){assertions++;}
        f=archived("SUCCESS");f.objects.put("202608",Arrays.asList(object("object-1","root",null,"202608")));f.missing.add("qd_es_signlog_h202608");
        try{f.loadResultObjects(f.loadRequestResult(ID));fail("Missing signature evidence table ignored");}catch(DAOException expected){assertions++;}
        f=archived("SUCCESS");f.requests.get("202608").put("signature_state","NOT_REQUIRED");f.objects.put("202608",Arrays.asList(object("object-1","root",null,"202608")));
        check(f.loadResultObjects(f.loadRequestResult(ID)).size()==1&&"qd_es_audit".equals(f.proofBase),"Audit-only completed request uses audit evidence for exact object set");

        verifySapplyBatchRecovery();

        f=archived("PARTIAL");
        Map<String,Object> assignment=f.requests.get("202608");
        assignment.put("operation_type","ASSIGN");
        assignment.put("policy_mode","OWNER_V1");
        assignment.put("owner_object_count",3);
        f.objects.put("202608",Arrays.asList(
            assignmentUnit("object-1", "A", "FAILED", "Y", "Y"),
            assignmentUnit("object-2", "B", "SUCCESS", "N", "Y"),
            assignmentUnit("object-3", "C", "ALREADY_ASSIGNED", "N", "Y")));
        f.auditProofs.put("202608",Arrays.asList(row("object_id","object-2")));
        result=f.loadRequestResult(ID);
        check(f.loadAssignmentResultObjects(result).size()==3,
            "Archived partial assignment uses distinct per-unit sign and audit evidence, preserving duplicate and failed targets");
        f.auditProofs.put("202608",Arrays.asList(row("object_id","object-2"),row("object_id","object-3")));
        try{f.loadAssignmentResultObjects(result);fail("Duplicate assignment accepted a success audit");}catch(DAOException expected){assertions++;}
        f.auditProofs.put("202608",Arrays.asList(row("object_id","object-2")));
        result.put("owner_object_count",4);
        try{f.loadAssignmentResultObjects(result);fail("Missing unaudited assignment unit went unnoticed");}catch(DAOException expected){assertions++;}

        f=archived("PARTIAL");
        Map<String,Object> cancelMaterial=f.requests.get("202608");
        cancelMaterial.put("operation_type","CANCEL_ASSIGN");cancelMaterial.put("node_code","10140MAG");
        cancelMaterial.put("button_code","CancelAssign");cancelMaterial.put("policy_mode","OWNER_V1");
        cancelMaterial.put("owner_object_count",3);
        f.objects.put("202608",Arrays.asList(
            assignmentUnit("object-1","A","FAILED","Y","Y"),
            assignmentUnit("object-2","B","SUCCESS","N","Y"),
            assignmentUnit("object-3","C","ALREADY_UNASSIGNED","N","Y")));
        f.auditProofs.put("202608",Arrays.asList(row("object_id","object-2")));
        result=f.loadRequestResult(ID);
        check(f.loadAssignmentResultObjects(result).size()==3,"Archived material cancellation preserves per-target outcomes");
        f.auditProofs.put("202608",Arrays.asList(row("object_id","object-2"),row("object_id","object-3")));
        try{f.loadAssignmentResultObjects(result);fail("Unchanged material cancellation accepted a success audit");}
        catch(DAOException expected){assertions++;}

        f=archived("PARTIAL");
        Map<String,Object> deleted=f.requests.get("202608");
        deleted.put("operation_type","DELETE");deleted.put("node_code","10140MAG");deleted.put("button_code","FiDelete");
        deleted.put("policy_mode","OWNER_V1");deleted.put("owner_object_count",2);
        f.objects.put("202608",Arrays.asList(
            assignmentUnit("object-1","A","FAILED","Y","Y"),
            assignmentUnit("object-2","B","SUCCESS","N","Y")));
        f.auditProofs.put("202608",Arrays.asList(row("object_id","object-2")));
        result=f.loadRequestResult(ID);
        check(f.loadPageDeleteResultObjects(result).size()==2,
            "Archived partial page delete retains failed and successful units with their own evidence");
        f.auditProofs.put("202608",Arrays.asList(row("object_id","object-1"),row("object_id","object-2")));
        try{f.loadPageDeleteResultObjects(result);fail("Failed page delete accepted a success audit");}
        catch(DAOException expected){assertions++;}

        f=archived("PARTIAL");
        Map<String,Object> warehouseDelete=f.requests.get("202608");
        warehouseDelete.put("operation_type","DELETE");warehouseDelete.put("node_code","10140WARH");warehouseDelete.put("button_code","Delete");
        warehouseDelete.put("policy_mode","OWNER_V1");warehouseDelete.put("owner_object_count",3);
        Map<String,Object> warehouseSuccess=assignmentUnit("object-1","A","SUCCESS","Y","Y");warehouseSuccess.put("object_type","BD_STORDOC");
        Map<String,Object> warehouseFailure=assignmentUnit("object-2","A","FAILED","N","Y");warehouseFailure.put("object_type","BD_STORDOC");
        Map<String,Object> warehouseSkipped=assignmentUnit("object-3","A","SKIPPED","N","Y");warehouseSkipped.put("object_type","BD_STORDOC");
        f.objects.put("202608",Arrays.asList(warehouseSuccess,warehouseFailure,warehouseSkipped));
        f.auditProofs.put("202608",Arrays.asList(row("object_id","object-1")));
        result=f.loadRequestResult(ID);
        check(f.loadPageDeleteResultObjects(result).size()==3,"Archived warehouse delete preserves successful, failed and skipped units");
        f.auditProofs.put("202608",Arrays.asList(row("object_id","object-1"),row("object_id","object-3")));
        try{f.loadPageDeleteResultObjects(result);fail("Skipped warehouse deletion accepted a success audit");}
        catch(DAOException expected){assertions++;}

        f=archived("PARTIAL");
        Map<String,Object> customerFreeze=f.requests.get("202608");
        customerFreeze.put("operation_type","FREEZE");customerFreeze.put("node_code","10140CUO");customerFreeze.put("button_code","Freeze");
        customerFreeze.put("policy_mode","OWNER_V1");customerFreeze.put("owner_object_count",2);
        Map<String,Object> frozen=assignmentUnit("object-1","A","SUCCESS","Y","Y");frozen.put("object_type","BD_CUSTOMER");
        Map<String,Object> rejected=assignmentUnit("object-2","B","FAILED","N","Y");rejected.put("object_type","BD_CUSTOMER");
        f.objects.put("202608",Arrays.asList(frozen,rejected));f.auditProofs.put("202608",Arrays.asList(row("object_id","object-1")));
        result=f.loadRequestResult(ID);
        check(f.loadArchiveBatchResultObjects(result).size()==2,"Archived customer freeze must retain per-customer outcomes and proof");
        f.auditProofs.put("202608",Arrays.asList(row("object_id","object-1"),row("object_id","object-2")));
        try{f.loadArchiveBatchResultObjects(result);fail("Failed customer freeze accepted a success audit");}
        catch(DAOException expected){assertions++;}

        f=archived("SUCCESS");
        Map<String,Object> supplierFreeze=f.requests.get("202608");
        supplierFreeze.put("operation_type","FREEZE");supplierFreeze.put("node_code","10140SUG");supplierFreeze.put("button_code","Freeze");
        supplierFreeze.put("policy_mode","OWNER_V1");supplierFreeze.put("owner_object_count",2);
        Map<String,Object> changed=assignmentUnit("object-1","A","SUCCESS","Y","Y");changed.put("object_type","BD_SUPPLIER");
        Map<String,Object> unchanged=assignmentUnit("object-2","B","UNCHANGED","N","Y");unchanged.put("object_type","BD_SUPPLIER");
        f.objects.put("202608",Arrays.asList(changed,unchanged));f.auditProofs.put("202608",Arrays.asList(row("object_id","object-1")));
        result=f.loadRequestResult(ID);
        check(f.loadArchiveBatchResultObjects(result).size()==2,"Archived supplier freeze retains changed and unchanged target evidence");
        f.auditProofs.put("202608",Arrays.asList(row("object_id","object-1"),row("object_id","object-2")));
        try{f.loadArchiveBatchResultObjects(result);fail("Unchanged supplier target accepted a success audit");}
        catch(DAOException expected){assertions++;}

        f=archived("PARTIAL");
        Map<String,Object> organizationBatch=f.requests.get("202608");
        organizationBatch.put("operation_type","UPDATE");organizationBatch.put("node_code","10140CUG");organizationBatch.put("button_code","BatchUpdate");
        organizationBatch.put("policy_mode","OWNER_V1");organizationBatch.put("owner_object_count",2);
        String fingerprint="0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        Map<String,Object> partial=assignmentUnit("object-1","org","PARTIAL","Y","Y");partial.put("object_type","BD_CUSTOMER");
        partial.put("object_pk","A");partial.put("client_object_id","orgbulk:"+fingerprint+":A");
        Map<String,Object> untouched=assignmentUnit("object-2","org","UNCHANGED","N","Y");untouched.put("object_type","BD_CUSTOMER");
        untouched.put("object_pk","B");untouched.put("client_object_id","orgbulk:"+fingerprint+":B");
        f.objects.put("202608",Arrays.asList(partial,untouched));f.auditProofs.put("202608",Arrays.asList(row("object_id","object-1")));
        result=f.loadRequestResult(ID);
        check(f.loadArchiveBatchResultObjects(result).size()==2,"A partially changed organization batch root must retain its audit evidence");
        f.auditProofs.put("202608",Collections.<Map<String,Object>>emptyList());
        try{f.loadArchiveBatchResultObjects(result);fail("Partially changed organization root accepted without its audit");}
        catch(DAOException expected){assertions++;}
        f.auditProofs.put("202608",Arrays.asList(row("object_id","object-1")));partial.put("client_object_id","bulk:"+fingerprint+":A");
        try{f.loadArchiveBatchResultObjects(result);fail("Ordinary batch partial root accepted an organization audit");}
        catch(DAOException expected){assertions++;}

        f=archived("SUCCESS");
        Map<String,Object> bank=f.requests.get("202608");bank.put("operation_type","UPDATE");bank.put("node_code","10140CBA");
        bank.put("button_code","Edit");bank.put("policy_mode","OWNER_V1");bank.put("owner_object_count",2);
        Map<String,Object> bankFirst=assignmentUnit("object-1","org","SUCCESS","Y","Y");
        Map<String,Object> bankSecond=assignmentUnit("object-2","org","SUCCESS","Y","Y");
        bankFirst.put("client_object_id","A");bankSecond.put("client_object_id","B");
        bankFirst.put("object_type","BD_CUSTOMER_BANK");bankSecond.put("object_type","BD_CUSTOMER_BANK");
        bankFirst.put("object_pk","Z");bankSecond.put("object_pk","A");
        for(Map<String,Object> item:Arrays.asList(bankFirst,bankSecond)) {
            item.put("policy_owner_root","CUSTOMER");item.put("policy_owner_org","group");item.put("policy_owner_type","G");
            item.put("policy_scope_id","BANK");item.put("bill_code","CUSTOMER");item.put("owner_policy_version",1);
            item.put("owner_control_version",1);item.put("owner_reason_required","N");
        }
        f.objects.put("202608",Arrays.asList(bankFirst,bankSecond));
        f.proofs.put("202608",Arrays.asList(row("object_id","object-1")));
        f.auditProofs.put("202608",Arrays.asList(row("object_id","object-1")));
        result=f.loadRequestResult(ID);
        check(f.loadResultObjects(result).size()==2,"Shared account recovery must use the same stable representative as signing and audit");
        f.proofs.put("202608",Arrays.asList(row("object_id","object-2")));
        f.auditProofs.put("202608",Arrays.asList(row("object_id","object-2")));
        check(f.loadResultObjects(result).size()==2,"Historical shared account representative was not retained");
        f.auditProofs.put("202608",Arrays.asList(row("object_id","object-1")));
        try{f.loadResultObjects(result);fail("Shared account accepted different signature and audit representatives");}
        catch(DAOException expected){assertions++;}
        f.proofs.put("202608",Arrays.asList(row("object_id","object-1")));
        f.auditProofs.put("202608",Collections.<Map<String,Object>>emptyList());
        try{f.loadResultObjects(result);fail("Missing shared account representative audit was accepted");}
        catch(DAOException expected){assertions++;}

        f=archived("SUCCESS");
        Map<String,Object> owner=f.requests.get("202608");
        owner.put("operation_type","UPDATE");
        owner.put("policy_mode","OWNER_V1");
        owner.put("owner_object_count",1);
        owner.put("signature_state","NOT_REQUIRED");
        f.proofs.clear();
        f.objects.put("202608",Arrays.asList(object("object-1","root",null,"202608")));
        check(f.loadResultObjects(f.loadRequestResult(ID)).size()==1,"A completed all-switches-off owner guard still restores its objects");
        return assertions;
    }
    public static void main(String[] args) throws Exception { System.out.println("PASS ResultRecoveryTests assertions="+run()+" (offline repository boundaries; no DB/NC transaction invoked)"); }
    private static void verifySapplyBatchRecovery() throws DAOException {
        FakeStore store = archived("PARTIAL");
        Map<String,Object> request = store.requests.get("202608");
        request.put("node_code", "40080801");
        request.put("policy_mode", "SAPPLY_BATCH");
        request.put("signature_state", "NOT_REQUIRED");
        request.put("owner_object_count", Integer.valueOf(2));
        Map<String,Object> committed = object("object-1", "A", null, "202608");
        Map<String,Object> failed = object("object-2", "B", null, "202608");
        committed.put("unit_state", "SUCCESS");
        failed.put("unit_state", "FAILED");
        store.objects.put("202608", Arrays.asList(committed, failed));
        store.auditProofs.put("202608", Arrays.asList(row("object_id", "object-1")));
        Map<String,Object> result = store.loadRequestResult(ID);
        check(store.loadSapplyBatchResultObjects(result).size() == 2,
                "The archived audit-only batch restores all units and requires audit for the committed bill");
        store.auditProofs.put("202608", Arrays.asList(row("object_id", "object-1"), row("object_id", "object-2")));
        try {
            store.loadSapplyBatchResultObjects(result);
            fail("The archived batch accepted success audit for an uncommitted bill");
        } catch (DAOException expected) { assertions++; }
    }

    private static Map<String,Object> request(String month,String state){return row("request_id",ID,"user_id","user","pk_group","group","pk_org","org","signature_state","SUCCESS","business_state",state,"finished_at","2026-08-01 00:00:00","archive_month",month);}
    private static Map<String,Object> object(String id,String client,String parent,String month){return row("object_id",id,"request_id",ID,"client_object_id",client,"object_type","TYPE","object_pk","PK-"+id,"bill_code","CODE-"+id,"parent_object_id",parent,"pk_group","group","pk_org","org","created_at","2026-08-01 00:00:00","archive_month",month);}
    private static Map<String,Object> assignmentUnit(String id,String target,String state,String sign,String audit){
        Map<String,Object> row=object(id,id,null,"202608");
        row.put("object_type","BD_MATERIAL");row.put("object_pk","MATERIAL");row.put("target_org",target);
        row.put("unit_state",state);row.put("owner_sign_required",sign);row.put("owner_audit_enabled",audit);
        return row;
    }
    private static FakeStore archived(String state){FakeStore f=new FakeStore();f.months.add("202608");f.requests.put("202608",request("202608",state));f.proofs.put("202608",Arrays.asList(row("object_id","object-1")));return f;}
    private static Map<String,Object> row(Object... values){Map<String,Object> map=new LinkedHashMap<String,Object>();for(int i=0;i<values.length;i+=2)map.put(String.valueOf(values[i]),values[i+1]);return map;}
    private static void check(boolean value,String message){if(!value)fail(message);assertions++;}
    private static void fail(String message){throw new AssertionError(message);}
    /** 仅构造服务器身份fixture，保存/恢复本线程原上下文，不调用用户查询或认证。 */
    private static ServerIdentity identity(String user,String group) throws Exception {
        InvocationInfoProxy p=InvocationInfoProxy.getInstance();String oldUser=p.getUserId(),oldGroup=p.getGroupId();
        try{
            p.setUserId(user);p.setGroupId(group);
            Constructor<ServerIdentity> c=ServerIdentity.class.getDeclaredConstructor(InvocationInfoProxy.class,UserVO.class);c.setAccessible(true);
            return c.newInstance(p,new UserVO(){private static final long serialVersionUID=1L;public String getUser_code(){return "fixture";}public String getUser_name(){return "fixture";}});
        }finally{p.setUserId(oldUser);p.setGroupId(oldGroup);}
    }
    /** 只替换数据库读边界；由生产恢复器执行实际路由、校验、去重与终态拒绝。 */
    private static final class FakeStore extends QdEsignStore {
        Map<String,Object> online,object;
        final List<String> months=new ArrayList<String>();
        final Map<String,Map<String,Object>> requests=new HashMap<String,Map<String,Object>>();
        final List<Map<String,Object>> onlineObjects=new ArrayList<Map<String,Object>>();
        final Map<String,List<Map<String,Object>>> objects=new HashMap<String,List<Map<String,Object>>>();
        final Map<String,List<Map<String,Object>>> proofs=new HashMap<String,List<Map<String,Object>>>();
        final Map<String,List<Map<String,Object>>> auditProofs=new HashMap<String,List<Map<String,Object>>>();
        final Set<String> missing=new HashSet<String>();
        boolean closed=true,historyLocked;int historyQueries,onlineLockQueries;String proofBase;
        public Map<String,Object> loadRequest(String id,boolean lock){if(lock)onlineLockQueries++;return online;}
        public List<Map<String,Object>> loadObjects(String id){return onlineObjects;}
        void enrich(String table,Map<String,Object> values) throws DAOException {enrichLog(table,values);}
        public void requireHistoryTable(String base,String month) throws DAOException {if(missing.contains(QdTableMetadata.historyTable(base,month)))throw new DAOException("Missing approved fixture history table");}
        public Map<String,Object> one(String sql,SQLParameter p){
            observe(sql);
            if(sql.contains(" FROM qd_es_request_h")){return requests.get(month(sql));}
            if(sql.contains(" FROM qd_es_archive")){return row("archive_month","fixture");}
            if(sql.contains(" FROM qd_es_object WHERE")){return object;}
            throw new AssertionError("Unexpected read: "+sql);
        }
        public List<Map<String,Object>> list(String sql,SQLParameter p){
            observe(sql);
            if(sql.contains(" FROM qd_es_locator")){List<Map<String,Object>> rows=new ArrayList<Map<String,Object>>();for(String month:months)rows.add(row("archive_month",month,"request_closed",closed?"Y":"N"));return rows;}
            if(sql.contains(" FROM qd_es_object_h")){List<Map<String,Object>> rows=objects.get(month(sql));return rows==null?Collections.<Map<String,Object>>emptyList():rows;}
            if(sql.startsWith("SELECT object_id FROM qd_es_signlog")||sql.startsWith("SELECT object_id FROM qd_es_audit")){
                proofBase=sql.contains("qd_es_signlog")?"qd_es_signlog":"qd_es_audit";
                Map<String,List<Map<String,Object>>> source="qd_es_audit".equals(proofBase)&&!auditProofs.isEmpty()?auditProofs:proofs;
                List<Map<String,Object>> rows=sql.contains("_h20")?source.get(month(sql)):null;
                return rows==null?Collections.<Map<String,Object>>emptyList():rows;
            }
            throw new AssertionError("Unexpected read: "+sql);
        }
        private void observe(String sql){if(sql.contains("_h20")){historyQueries++;if(sql.contains("FOR UPDATE"))historyLocked=true;}}
        private static String month(String sql){int at=sql.indexOf("_h20");return sql.substring(at+2,at+8);}
    }
}
