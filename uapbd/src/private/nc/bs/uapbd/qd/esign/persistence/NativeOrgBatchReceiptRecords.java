package nc.bs.uapbd.qd.esign.persistence;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.bs.uapbd.qd.esign.core.NativeOrgBatchPage;
import nc.bs.uapbd.qd.esign.core.NativeOrgBatchReceipt;
import nc.bs.uapbd.qd.esign.core.ObjectRelations;
import nc.bs.uapbd.qd.esign.core.PartnerBatchRoute;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.ArchiveBatchResult;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.PartnerBatchDraft;

/** Fixed row contract for organization-batch receipts. This codec does not write or read a database. */
public final class NativeOrgBatchReceiptRecords {
    public static final String TABLE="qd_es_batch_page";
    static final String TARGETS="@TARGETS",HEADER="@";
    private static final List<String> COLUMNS=Collections.unmodifiableList(Arrays.asList("record_id","request_id","object_id",
        "row_kind","item_key","target_org","result_state","failure_stage","page_count","evidence_hash","tab_code",
        "pk_group","pk_org","event_time","archive_month"));
    private static final Set<String> COLUMN_SET=Collections.unmodifiableSet(new HashSet<String>(COLUMNS));
    private static final Pattern UUID=Pattern.compile("[a-fA-F0-9]{32}");
    private static final Pattern CLIENT=Pattern.compile("orgbulk:([a-f0-9]{64}):([A-Za-z0-9_-]{1,20})");
    private NativeOrgBatchReceiptRecords() { }

    public static List<String> columns() { return COLUMNS; }
    static void requireRunning(Map<String,Object> request,List<Map<String,Object>> objects,NativeOrgBatchPage page)
            throws BusinessException { scope(request,objects,page,Mode.RUNNING); }

    /** The caller must insert every returned row in the same CMT as the native update and root outcomes. */
    public static List<Map<String,Object>> encode(Map<String,Object> request,List<Map<String,Object>> objects,
            NativeOrgBatchPage page,NativeOrgBatchReceipt.Evidence evidence,String eventTime) throws BusinessException {
        Scope scope=scope(request,objects,page,Mode.RUNNING);
        EsignValues.require(evidence!=null,"组织页签回执缺少已核对结果。");
        Map<String,String> states=new LinkedHashMap<String,String>();
        for(NativeOrgBatchReceipt.Header header:evidence.getHeaders())
            EsignValues.require(header!=null && states.put(header.getClientId(),header.getState())==null,
                "组织页签回执主档状态重复。");
        NativeOrgBatchReceipt.Evidence checked=NativeOrgBatchReceipt.verify(scope.id,scope.group,scope.org,page,scope.refs,states,
            evidence.getTargets(),evidence.getHeaders(),evidence.getPages());
        String month=month(eventTime);List<Map<String,Object>> result=new ArrayList<Map<String,Object>>();Set<String> ids=new HashSet<String>();
        for(String target:checked.getTargets())
            result.add(record(scope,page,TARGETS,"T",target,null,null,null,null,null,eventTime,month,ids));
        for(NativeOrgBatchReceipt.Header header:checked.getHeaders())
            result.add(record(scope,page,scope.objectByClient.get(header.getClientId()),"H",HEADER,null,header.getState(),null,
                Integer.valueOf(header.getPageCount()),header.getDigest(),eventTime,month,ids));
        for(NativeOrgBatchReceipt.Page row:checked.getPages())
            result.add(record(scope,page,scope.objectByClient.get(row.getClientId()),"P",row.getPk(),row.getTarget(),row.getState(),
                row.getFailureStage(),null,null,eventTime,month,ids));
        return Collections.unmodifiableList(result);
    }

    /** Read only already-authorized online/history rows; identical overlap is allowed, conflicts are not. */
    public static NativeOrgBatchReceipt.Evidence decode(Map<String,Object> request,List<Map<String,Object>> objects,
            NativeOrgBatchPage page,List<Map<String,Object>> rows) throws BusinessException {
        Scope scope=scope(request,objects,page,Mode.FINISHED);
        List<String> targets=new ArrayList<String>();List<NativeOrgBatchReceipt.Header> headers=new ArrayList<NativeOrgBatchReceipt.Header>();
        List<NativeOrgBatchReceipt.Page> pages=new ArrayList<NativeOrgBatchReceipt.Page>();
        for(Map<String,Object> row:verifiedRows(scope,page,rows)) {
            String objectId=text(row,"object_id"),kind=text(row,"row_kind"),key=text(row,"item_key"),
                target=text(row,"target_org"),state=text(row,"result_state"),stage=text(row,"failure_stage"),hash=text(row,"evidence_hash");
            Object count=row.get("page_count");
            if("T".equals(kind)) {
                EsignValues.require(TARGETS.equals(objectId) && key!=null && target==null && state==null && stage==null
                    && count==null && hash==null,"组织页签回执目标记录无效。");targets.add(key);
            } else {
                BusinessObjectRef ref=scope.refByObject.get(objectId);
                EsignValues.require(ref!=null,"组织页签回执记录不属于该请求的档案。");
                if("H".equals(kind)) {
                    EsignValues.require(HEADER.equals(key) && target==null && stage==null && hash!=null,
                        "组织页签回执主档记录无效。");
                    headers.add(new NativeOrgBatchReceipt.Header(ref.getClientObjectId(),ref.getObjectPk(),state,integer(count),hash));
                } else {
                    EsignValues.require("P".equals(kind) && key!=null && target!=null && count==null && hash==null,
                        "组织页签回执页签记录无效。");
                    pages.add(new NativeOrgBatchReceipt.Page(ref.getClientObjectId(),key,target,state,stage));
                }
            }
        }
        NativeOrgBatchReceipt.Evidence result=NativeOrgBatchReceipt.verify(scope.id,scope.group,scope.org,page,scope.refs,scope.states,
            targets,headers,pages);
        EsignValues.require(overall(result).equals(text(request,"business_state")),"组织页签回执批次状态与逐档案结果不一致。");
        return result;
    }

    /** One record group shares an event instant; identical online/history overlap is accepted. */
    static List<Map<String,Object>> verifiedRows(Scope scope,NativeOrgBatchPage page,List<Map<String,Object>> rows)
            throws BusinessException {
        EsignValues.require(scope!=null && page!=null && rows!=null,"组织页签记录缺失。");
        Map<String,Map<String,Object>> unique=new LinkedHashMap<String,Map<String,Object>>();String commonEvent=null,commonMonth=null;
        for(Map<String,Object> row:rows) {
            EsignValues.require(row!=null && row.keySet().equals(COLUMN_SET),"组织页签记录列不完整。");
            String id=text(row,"record_id"),event=text(row,"event_time"),archive=text(row,"archive_month");
            if(commonEvent==null) { commonEvent=event;commonMonth=month(event); }
            EsignValues.require(uuid(id) && scope.id.equals(text(row,"request_id")) && scope.group.equals(text(row,"pk_group"))
                && scope.org.equals(text(row,"pk_org")) && page.tabCode.equals(text(row,"tab_code")) && archive!=null
                && commonEvent.equals(event) && commonMonth.equals(archive),"组织页签记录的请求、组织或归档月份不一致。");
            Map<String,Object> prior=unique.get(id);
            EsignValues.require(prior==null || same(prior,row),"组织页签记录在线与历史副本冲突。");
            if(prior==null) unique.put(id,new LinkedHashMap<String,Object>(row));
        }
        return new ArrayList<Map<String,Object>>(unique.values());
    }

    static Map<String,Object> record(Scope scope,NativeOrgBatchPage page,String objectId,String kind,String key,String target,
            String state,String stage,Integer count,String hash,String event,String month,Set<String> ids) throws BusinessException {
        String id=EsignValues.id();EsignValues.require(ids.add(id),"组织页签回执记录标识重复。");
        return Collections.unmodifiableMap(EsignValues.row("record_id",id,"request_id",scope.id,"object_id",objectId,
            "row_kind",kind,"item_key",key,"target_org",target,"result_state",state,"failure_stage",stage,
            "page_count",count,"evidence_hash",hash,"tab_code",page.tabCode,"pk_group",scope.group,"pk_org",scope.org,
            "event_time",event,"archive_month",month));
    }

    enum Mode { PREPARING,RUNNING,FINISHED,READABLE }
    static final class Scope {
        final String id,group,org;final List<BusinessObjectRef> refs;
        final Map<String,String> objectByClient,states;final Map<String,BusinessObjectRef> refByObject;
        Scope(String id,String group,String org,List<BusinessObjectRef> refs,Map<String,String> objectByClient,
                Map<String,String> states,Map<String,BusinessObjectRef> refByObject) {
            this.id=id;this.group=group;this.org=org;this.refs=refs;this.objectByClient=objectByClient;this.states=states;this.refByObject=refByObject;
        }
    }
    static Scope scope(Map<String,Object> request,List<Map<String,Object>> objects,NativeOrgBatchPage page,Mode mode)
            throws BusinessException {
        EsignValues.require(request!=null && objects!=null && !objects.isEmpty() && objects.size()<=PartnerBatchDraft.MAX_SINGLE_TRANSACTION_OBJECTS
            && page!=null && mode!=null,"组织页签回执缺少请求或档案。");
        String id=text(request,"request_id"),group=text(request,"pk_group"),org=text(request,"pk_org"),state=text(request,"business_state"),
            node=text(request,"node_code"),button=text(request,"button_code"),operation=text(request,"operation_type"),
            signature=text(request,"signature_state");boolean finished=request.get("finished_at")!=null;
        boolean preparing=!finished && request.get("execution_token")==null
            && (("PENDING".equals(signature) && "PENDING".equals(state))
                || ("NOT_REQUIRED".equals(signature) && ("PENDING".equals(state) || "READY".equals(state))));
        boolean running=!finished && "RUNNING".equals(state) && ("SUCCESS".equals(signature) || "NOT_REQUIRED".equals(signature));
        boolean terminal=finished && ("SUCCESS".equals(state) || "PARTIAL".equals(state) || "FAILED".equals(state)
            || "CANCELLED".equals(state) || "EXPIRED".equals(state));
        boolean completed=terminal && ("SUCCESS".equals(state) || "PARTIAL".equals(state) || "FAILED".equals(state))
            && ("SUCCESS".equals(signature) || "NOT_REQUIRED".equals(signature));
        boolean readable=signatureMatchesState(signature,state) && (terminal || (!finished && ("PENDING".equals(state)
            || "READY".equals(state) || "RUNNING".equals(state) || "UNKNOWN".equals(state))));
        boolean validMode=mode==Mode.PREPARING?preparing:mode==Mode.RUNNING?running:mode==Mode.FINISHED?completed:readable;
        EsignValues.require(uuid(id) && group!=null && org!=null && OperationContext.OWNER_POLICY_PROTOCOL.equals(text(request,"policy_mode"))
            && ArchiveBatchResult.isPartnerBatchUpdate(node,button,operation) && page.domain.equals(PartnerBatchRoute.domain(node))
            && validMode
            && integer(request.get("owner_object_count"))==objects.size(),"组织页签回执请求状态或授权范围不正确。");
        if(finished) month(text(request,"finished_at"));
        Map<String,String> objectByClient=new LinkedHashMap<String,String>(),states=new LinkedHashMap<String,String>();
        Set<String> objectIds=new HashSet<String>();String fingerprint=null;
        for(Map<String,Object> row:objects) {
            String objectId=row==null?null:text(row,"object_id"),client=row==null?null:text(row,"client_object_id"),
                unit=row==null?null:text(row,"unit_state"),pk=row==null?null:text(row,"object_pk");
            Matcher parsed=client==null?null:CLIENT.matcher(client);
            EsignValues.require(row!=null && uuid(objectId) && objectIds.add(objectId) && client!=null
                && objectByClient.put(client,objectId)==null && id.equals(text(row,"request_id"))
                && group.equals(text(row,"pk_group")) && org.equals(text(row,"pk_org"))
                && page.domain.equals(text(row,"object_type")) && org.equals(text(row,"target_org"))
                && text(row,"policy_owner_org")!=null && pk!=null && parsed!=null && parsed.matches() && pk.equals(parsed.group(2))
                && (fingerprint==null || fingerprint.equals(parsed.group(1))) && text(row,"bill_code")!=null
                && "ROOT".equals(text(row,"relation_type")) && row.get("parent_object_id")==null && row.get("desired_owner_org")==null
                && ((mode==Mode.PREPARING || mode==Mode.RUNNING)?"PENDING".equals(unit):unit!=null),"组织页签回执对象与请求不一致。");
            fingerprint=parsed.group(1);
            states.put(client,unit);
        }
        List<BusinessObjectRef> refs=ObjectRelations.restore(objects);Map<String,BusinessObjectRef> refByObject=new HashMap<String,BusinessObjectRef>();
        for(BusinessObjectRef ref:refs) refByObject.put(objectByClient.get(ref.getClientObjectId()),ref);
        return new Scope(id,group,org,refs,objectByClient,states,refByObject);
    }
    static int integer(Object value) throws BusinessException {
        EsignValues.require(value instanceof Number,"组织页签回执数量无效。");
        try { int result=new BigDecimal(String.valueOf(value)).intValueExact();
            EsignValues.require(result>=0,"组织页签回执数量无效。");return result;
        } catch(ArithmeticException e) { throw new BusinessException("组织页签回执数量超出范围。",e); }
        catch(NumberFormatException e) { throw new BusinessException("组织页签回执数量无效。",e); }
    }
    private static String overall(NativeOrgBatchReceipt.Evidence result) {
        boolean success=false,failed=false;
        for(NativeOrgBatchReceipt.Header header:result.getHeaders()) {
            success|="SUCCESS".equals(header.getState()) || "PARTIAL".equals(header.getState());
            failed|="FAILED".equals(header.getState()) || "PARTIAL".equals(header.getState());
        }
        return failed?(success?"PARTIAL":"FAILED"):"SUCCESS";
    }
    private static boolean signatureMatchesState(String signature,String state) {
        boolean ready="SUCCESS".equals(signature) || "NOT_REQUIRED".equals(signature);
        if("PENDING".equals(state))return "PENDING".equals(signature) || "NOT_REQUIRED".equals(signature);
        if("READY".equals(state) || "RUNNING".equals(state) || "UNKNOWN".equals(state)
                || "SUCCESS".equals(state) || "PARTIAL".equals(state))return ready;
        if("FAILED".equals(state))return ready || "ACCOUNT_LOCKED".equals(signature) || "ACCOUNT_UNAVAILABLE".equals(signature);
        if("CANCELLED".equals(state))return ready || "CANCELLED".equals(signature);
        if("EXPIRED".equals(state))return ready || "EXPIRED".equals(signature);
        return false;
    }
    static String month(String event) throws BusinessException {
        try { return QdUtcClock.beijingMonth(QdUtcClock.parseUtc(event)); }
        catch(IllegalArgumentException e) { throw new BusinessException("组织页签回执时间无效。",e); }
    }
    static boolean same(Map<String,Object> a,Map<String,Object> b) {
        for(String key:COLUMNS) {
            Object x=a.get(key),y=b.get(key);
            if(x==null || y==null) { if(x!=y)return false; }
            else if(x instanceof Number && y instanceof Number) {
                try { if(new BigDecimal(String.valueOf(x)).compareTo(new BigDecimal(String.valueOf(y)))!=0)return false; }
                catch(NumberFormatException e) { return false; }
            } else if(!x.equals(y)) return false;
        }
        return true;
    }
    static String text(Map<String,Object> row,String key) { return EsignValues.text(row,key); }
    static boolean uuid(String value) { return value!=null && UUID.matcher(value).matches(); }
}
