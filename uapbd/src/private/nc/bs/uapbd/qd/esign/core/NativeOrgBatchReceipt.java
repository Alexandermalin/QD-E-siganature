package nc.bs.uapbd.qd.esign.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import nc.bs.uapbd.qd.esign.audit.SnapshotSupport;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.PartnerBatchDraft;

/** Completeness evidence for durable page outcomes, including roots with no matching organization page. */
public final class NativeOrgBatchReceipt {
    private static final Pattern PK=Pattern.compile("[A-Za-z0-9_-]{1,20}");
    private static final Pattern CLIENT=Pattern.compile("orgbulk:([a-f0-9]{64}):([A-Za-z0-9_-]{1,20})");
    private static final Pattern DIGEST=Pattern.compile("[a-f0-9]{64}");
    private NativeOrgBatchReceipt() { }

    /** The header must be stored even when the archive has no matching page rows. */
    public static final class Header {
        private final String clientId,rootPk,state,digest;private final int pageCount;
        public Header(String clientId,String rootPk,String state,int pageCount,String digest) {
            this.clientId=clientId;this.rootPk=rootPk;this.state=state;this.pageCount=pageCount;this.digest=digest;
        }
        public String getClientId() { return clientId; }
        public String getRootPk() { return rootPk; }
        public String getState() { return state; }
        public int getPageCount() { return pageCount; }
        public String getDigest() { return digest; }
    }
    public static final class Page {
        private final String clientId,pk,target,state,failureStage;
        public Page(String clientId,String pk,String target,String state,String failureStage) {
            this.clientId=clientId;this.pk=pk;this.target=target;this.state=state;this.failureStage=failureStage;
        }
        public String getClientId() { return clientId; }
        public String getPk() { return pk; }
        public String getTarget() { return target; }
        public String getState() { return state; }
        public String getFailureStage() { return failureStage; }
    }
    public static final class Evidence {
        private final String requestId,group,operationOrg;private final NativeOrgBatchPage page;
        private final List<String> targets;private final List<Header> headers;private final List<Page> pages;
        private Evidence(String requestId,String group,String operationOrg,NativeOrgBatchPage page,
                List<String> targets,List<Header> headers,List<Page> pages) {
            this.requestId=requestId;this.group=group;this.operationOrg=operationOrg;this.page=page;
            this.targets=Collections.unmodifiableList(new ArrayList<String>(targets));
            this.headers=Collections.unmodifiableList(new ArrayList<Header>(headers));
            this.pages=Collections.unmodifiableList(new ArrayList<Page>(pages));
        }
        public List<String> getTargets() { return targets; }
        public List<Header> getHeaders() { return headers; }
        public List<Page> getPages() { return pages; }
        public boolean matches(String id,String group,String org,NativeOrgBatchPage page) {
            return requestId.equals(id) && this.group.equals(group) && operationOrg.equals(org) && this.page==page;
        }
    }

    /** Make evidence only from the reconciled native outcome; the caller persists it in the business transaction. */
    public static Evidence capture(String requestId,String group,String operationOrg,NativeOrgBatchPage page,
            List<BusinessObjectRef> objects,NativeOrgBatchReconciliation.Result result) throws BusinessException {
        EsignValues.require(page!=null && result!=null && result.getPage()==page && result.matches(requestId,group,operationOrg,page.domain,objects)
            && result.getRoots()!=null && result.getPages()!=null,
            "组织页签回执缺少已核对的页签或执行结果。");
        Map<String,BusinessObjectRef> byClient=objects(requestId,group,operationOrg,page,objects);
        List<String> targets=targets(result.getTargets());
        String targetsDigest=targetsDigest(targets);
        Map<String,String> clientByPk=new HashMap<String,String>();
        for(BusinessObjectRef ref:objects) clientByPk.put(ref.getObjectPk(),ref.getClientObjectId());
        Map<String,String> states=new LinkedHashMap<String,String>();
        for(NativeOrgBatchOutcome.Root root:result.getRoots()) EsignValues.require(root!=null && byClient.containsKey(root.getClientId())
            && byClient.get(root.getClientId()).getObjectPk().equals(root.getPk()) && states.put(root.getClientId(),root.getState())==null,
            "组织页签回执主档与已授权范围不一致。");
        EsignValues.require(states.size()==byClient.size(),"组织页签回执缺少主档结果。");
        List<Page> pages=new ArrayList<Page>();
        for(NativeOrgBatchReconciliation.Page row:result.getPages()) {
            String client=row==null?null:clientByPk.get(row.getRoot());
            EsignValues.require(client!=null,"组织页签回执包含未授权主档。");
            pages.add(new Page(client,row.getPk(),row.getTarget(),row.getState(),row.getFailureStage()));
        }
        Map<String,List<Page>> grouped=pages(byClient,targets,pages);
        List<Header> headers=new ArrayList<Header>();
        for(BusinessObjectRef ref:objects) {
            String client=ref.getClientObjectId(),state=states.get(client);List<Page> rows=grouped.get(client);
            headers.add(new Header(client,ref.getObjectPk(),state,rows.size(),
                digest(requestId,group,operationOrg,page,targetsDigest,client,ref.getObjectPk(),state,rows)));
        }
        return verify(requestId,group,operationOrg,page,objects,states,targets,headers,pages);
    }

    /** Recovered headers, pages and persisted root states must come from the same completed request. */
    public static Evidence verify(String requestId,String group,String operationOrg,NativeOrgBatchPage page,
            List<BusinessObjectRef> objects,Map<String,String> rootStates,List<String> storedTargets,List<Header> headers,List<Page> pageRows)
            throws BusinessException {
        Map<String,BusinessObjectRef> expected=objects(requestId,group,operationOrg,page,objects);
        List<String> targets=targets(storedTargets);
        String targetsDigest=targetsDigest(targets);
        EsignValues.require(rootStates!=null && rootStates.keySet().equals(expected.keySet()) && headers!=null && pageRows!=null,
            "组织页签回执缺少完整主档状态或明细。");
        Map<String,List<Page>> grouped=pages(expected,targets,pageRows);Map<String,Header> actual=new LinkedHashMap<String,Header>();
        for(Header header:headers) {
            BusinessObjectRef ref=header==null?null:expected.get(header.getClientId());
            EsignValues.require(ref!=null && ref.getObjectPk().equals(header.getRootPk())
                && actual.put(header.getClientId(),header)==null && header.getPageCount()>=0
                && header.getDigest()!=null && DIGEST.matcher(header.getDigest()).matches(),"组织页签回执表头重复或无效。");
            List<Page> rows=grouped.get(header.getClientId());String state=state(rows);
            EsignValues.require(rows.size()==header.getPageCount() && state.equals(header.getState())
                && state.equals(rootStates.get(header.getClientId()))
                && header.getDigest().equals(digest(requestId,group,operationOrg,page,targetsDigest,header.getClientId(),header.getRootPk(),state,rows)),
                "组织页签回执明细缺失或与主档状态不一致。");
        }
        EsignValues.require(actual.size()==expected.size(),"组织页签回执缺少主档表头。");
        List<Header> orderedHeaders=new ArrayList<Header>();List<Page> orderedPages=new ArrayList<Page>();
        for(BusinessObjectRef ref:objects) {
            orderedHeaders.add(actual.get(ref.getClientObjectId()));orderedPages.addAll(grouped.get(ref.getClientObjectId()));
        }
        return new Evidence(requestId,group,operationOrg,page,targets,orderedHeaders,orderedPages);
    }

    private static Map<String,BusinessObjectRef> objects(String requestId,String group,String operationOrg,NativeOrgBatchPage page,
            List<BusinessObjectRef> objects) throws BusinessException {
        EsignValues.require(requestId!=null && requestId.matches("[a-fA-F0-9]{32}") && validPk(group) && validPk(operationOrg)
            && page!=null && objects!=null && !objects.isEmpty() && objects.size()<=PartnerBatchDraft.MAX_SINGLE_TRANSACTION_OBJECTS,
            "组织页签回执缺少请求、集团或授权对象。");
        Map<String,BusinessObjectRef> expected=new LinkedHashMap<String,BusinessObjectRef>();Set<String> pks=new HashSet<String>();
        String fingerprint=null;
        for(BusinessObjectRef ref:objects) {
            String client=ref==null?null:ref.getClientObjectId(),pk=ref==null?null:ref.getObjectPk();
            Matcher parsed=client==null?null:CLIENT.matcher(client);
            EsignValues.require(ref!=null && page.domain.equals(ref.getObjectType()) && validPk(pk) && pks.add(pk)
                && ref.getParentClientObjectId()==null && ref.getDesiredOwnerOrg()==null
                && (ref.getTargetOrg()==null || operationOrg.equals(ref.getTargetOrg()))
                && parsed!=null && parsed.matches() && pk.equals(parsed.group(2)) && expected.put(client,ref)==null,
                "组织页签回执包含重复或无效的授权档案。");
            String current=parsed.group(1);
            EsignValues.require(fingerprint==null || fingerprint.equals(current),"组织页签回执混入了其他授权范围。");fingerprint=current;
        }
        return expected;
    }
    private static Map<String,List<Page>> pages(Map<String,BusinessObjectRef> expected,List<String> targets,List<Page> rows) throws BusinessException {
        EsignValues.require(rows!=null,"组织页签回执明细缺失。");
        Map<String,List<Page>> grouped=new LinkedHashMap<String,List<Page>>();
        for(String client:expected.keySet()) grouped.put(client,new ArrayList<Page>());
        Set<String> ids=new HashSet<String>(),allowedTargets=new HashSet<String>(targets);
        for(Page row:rows) {
            List<Page> target=row==null?null:grouped.get(row.getClientId());
            EsignValues.require(target!=null && validPk(row.getPk()) && ids.add(row.getPk()) && allowedTargets.contains(row.getTarget())
                && (("FAILED".equals(row.getState()) && failureStage(row.getFailureStage()))
                    || (("SUCCESS".equals(row.getState()) || "UNCHANGED".equals(row.getState())) && row.getFailureStage()==null)),
                "组织页签回执包含重复、未授权或状态不明的明细。");
            target.add(row);
        }
        Comparator<Page> order=new Comparator<Page>() { public int compare(Page a,Page b) {
            int result=a.getTarget().compareTo(b.getTarget());return result!=0?result:a.getPk().compareTo(b.getPk());
        }};
        for(List<Page> values:grouped.values()) Collections.sort(values,order);
        return grouped;
    }
    private static String state(List<Page> rows) {
        boolean success=false,failed=false;
        for(Page row:rows) { if("SUCCESS".equals(row.getState())) success=true;else if("FAILED".equals(row.getState())) failed=true; }
        return failed?(success?"PARTIAL":"FAILED"):success?"SUCCESS":"UNCHANGED";
    }
    private static String digest(String requestId,String group,String operationOrg,NativeOrgBatchPage page,String targetsDigest,String client,String root,
            String state,List<Page> rows) {
        StringBuilder value=new StringBuilder(pack("org-batch-receipt-v1",requestId,group,operationOrg,page.domain,page.tabCode,
            page.metadataId,targetsDigest,client,root,state,Integer.toString(rows.size())));
        for(Page row:rows) value.append(pack(row.getPk(),row.getTarget(),row.getState(),row.getFailureStage()));
        return SnapshotSupport.sha256(value.toString());
    }
    private static String pack(String... values) {
        StringBuilder result=new StringBuilder();
        for(String value:values) result.append(value==null?-1:value.length()).append(':').append(value==null?"":value);
        return result.toString();
    }
    private static boolean validPk(String value) { return value!=null && PK.matcher(value).matches(); }
    private static List<String> targets(List<String> values) throws BusinessException {
        EsignValues.require(values!=null && !values.isEmpty(),"组织页签回执缺少完整目标组织。");
        Set<String> sorted=new java.util.TreeSet<String>();
        for(String value:values) EsignValues.require(validPk(value) && sorted.add(value),"组织页签回执目标组织无效或重复。");
        return Collections.unmodifiableList(new ArrayList<String>(sorted));
    }
    private static String targetsDigest(List<String> targets) {
        StringBuilder value=new StringBuilder(pack("org-batch-targets-v1",Integer.toString(targets.size())));
        for(String target:targets) value.append(pack(target));
        return SnapshotSupport.sha256(value.toString());
    }
    private static boolean failureStage(String value) {
        return "PERMISSION".equals(value) || "VALIDATION".equals(value) || "BEFORE_EVENT".equals(value);
    }
}
