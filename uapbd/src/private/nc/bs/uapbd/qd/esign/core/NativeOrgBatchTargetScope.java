package nc.bs.uapbd.qd.esign.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import nc.bs.uapbd.qd.esign.audit.SnapshotSupport;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.PartnerBatchDraft;

/** Target evidence created at request preparation, before a business result or rollback can exist. */
public final class NativeOrgBatchTargetScope {
    private static final Pattern PK=Pattern.compile("[A-Za-z0-9_-]{1,20}");
    private static final Pattern CLIENT=Pattern.compile("orgbulk:([a-f0-9]{64}):([A-Za-z0-9_-]{1,20})");
    private static final Pattern DIGEST=Pattern.compile("[a-f0-9]{64}");
    private NativeOrgBatchTargetScope() { }

    public static final class Evidence {
        private final NativeOrgBatchPage page;private final List<String> targets;private final String digest;
        private Evidence(NativeOrgBatchPage page,List<String> targets,String digest) {
            this.page=page;this.targets=Collections.unmodifiableList(new ArrayList<String>(targets));this.digest=digest;
        }
        public NativeOrgBatchPage getPage() { return page; }
        public List<String> getTargets() { return targets; }
        public String getDigest() { return digest; }
    }

    /** Capture only from the server-prepared page, target selection and signed physical impact. */
    public static Evidence capture(String requestId,String group,String org,PartnerOrgBatchScope.Prepared prepared)
            throws BusinessException {
        EsignValues.require(prepared!=null && prepared.getPayload()!=null && prepared.getImpact()!=null
            && prepared.getEvidence()!=null && prepared.getPayload().getPage()==prepared.getImpact().getPage()
            && prepared.getPayload().getTargets().equals(prepared.getImpact().getTargets()),"组织批改目标范围尚未核实。");
        NativeOrgBatchPage page=prepared.getImpact().getPage();List<BusinessObjectRef> objects=prepared.getObjects();
        NativeBatchRequestScope.verifyOrganization(group,org,page.domain,objects,prepared.getPayload().getChanges(),
            prepared.getImpact(),prepared.getEvidence());
        return make(requestId,group,org,page,objects,prepared.getImpact().getTargets());
    }

    /** Recover the exact stored target set using the complete request objects, without rereading changed business rows. */
    public static Evidence restore(String requestId,String group,String org,NativeOrgBatchPage page,
            List<BusinessObjectRef> objects,List<String> targets,String digest) throws BusinessException {
        EsignValues.require(digest!=null && DIGEST.matcher(digest).matches(),"组织批改目标范围摘要无效。");
        Evidence result=make(requestId,group,org,page,objects,targets);
        EsignValues.require(result.digest.equals(digest),"组织批改目标或授权档案范围不完整。");return result;
    }
    /** The real capture path requires the prepared native impact; this seam is only for offline fixtures. */
    static Evidence captureForTest(String requestId,String group,String org,NativeOrgBatchPage page,
            List<BusinessObjectRef> objects,List<String> targets) throws BusinessException {
        return make(requestId,group,org,page,objects,targets);
    }
    private static Evidence make(String requestId,String group,String org,NativeOrgBatchPage page,List<BusinessObjectRef> objects,
            List<String> targets) throws BusinessException {
        EsignValues.require(requestId!=null && requestId.matches("[a-fA-F0-9]{32}") && validPk(group) && validPk(org)
            && page!=null && objects!=null && !objects.isEmpty() && objects.size()<=PartnerBatchDraft.MAX_SINGLE_TRANSACTION_OBJECTS
            && targets!=null && !targets.isEmpty(),
            "组织批改目标证据缺少请求、组织或档案。");
        Set<String> roots=new TreeSet<String>(),clients=new HashSet<String>();String fingerprint=null;
        for(BusinessObjectRef ref:objects) {
            String pk=ref==null?null:ref.getObjectPk(),client=ref==null?null:ref.getClientObjectId();
            Matcher parsed=client==null?null:CLIENT.matcher(client);
            EsignValues.require(ref!=null && page.domain.equals(ref.getObjectType()) && validPk(pk) && roots.add(pk)
                && parsed!=null && parsed.matches() && pk.equals(parsed.group(2)) && clients.add(client)
                && (fingerprint==null || fingerprint.equals(parsed.group(1))) && ref.getParentClientObjectId()==null
                && ref.getDesiredOwnerOrg()==null && (ref.getTargetOrg()==null || org.equals(ref.getTargetOrg())),
                "组织批改目标证据包含重复或不属于本请求的档案。");
            fingerprint=parsed.group(1);
        }
        Set<String> selected=new TreeSet<String>();
        for(String target:targets) EsignValues.require(validPk(target) && selected.add(target),"组织批改目标无效或重复。");
        List<String> sorted=new ArrayList<String>(selected);
        StringBuilder data=new StringBuilder(pack("org-batch-target-scope-v1",requestId,group,org,page.domain,page.tabCode,
            page.metadataId,fingerprint,Integer.toString(roots.size())));
        for(String root:roots) data.append(pack(root));
        data.append(pack(Integer.toString(sorted.size())));for(String target:sorted)data.append(pack(target));
        return new Evidence(page,sorted,SnapshotSupport.sha256(data.toString()));
    }
    private static boolean validPk(String value) { return value!=null && PK.matcher(value).matches(); }
    private static String pack(String... values) {
        StringBuilder result=new StringBuilder();
        for(String value:values) result.append(value==null?-1:value.length()).append(':').append(value==null?"":value);
        return result.toString();
    }
}
