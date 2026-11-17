package nc.bs.uapbd.qd.esign.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import nc.bs.uapbd.qd.esign.audit.SnapshotSupport;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.PartnerBatchDraft;
import nc.vo.uapbd.qd.esign.PartnerOrgBatchPlan;

/** Preparation-time page identities, bound to the same request and signed target scope. */
public final class NativeOrgBatchPreparedScope {
    private NativeOrgBatchPreparedScope() { }
    public static final class Evidence {
        private final NativeOrgBatchTargetScope.Evidence target;private final List<PartnerOrgBatchPlan.Page> pages;
        private final String digest;
        private Evidence(NativeOrgBatchTargetScope.Evidence target,List<PartnerOrgBatchPlan.Page> pages,String digest) {
            this.target=target;this.pages=Collections.unmodifiableList(new ArrayList<PartnerOrgBatchPlan.Page>(pages));this.digest=digest;
        }
        public NativeOrgBatchTargetScope.Evidence getTarget() { return target; }
        public List<PartnerOrgBatchPlan.Page> getPages() { return pages; }
        public String getDigest() { return digest; }
    }

    /** Only the server-prepared physical selection may be persisted as signing evidence. */
    public static Evidence capture(String requestId,String group,String org,PartnerOrgBatchScope.Prepared prepared)
            throws BusinessException {
        NativeOrgBatchTargetScope.Evidence target=NativeOrgBatchTargetScope.capture(requestId,group,org,prepared);
        List<PartnerOrgBatchPlan.Page> pages=new ArrayList<PartnerOrgBatchPlan.Page>();
        for(NativeOrgBatchSelection.Row row:prepared.getImpact().getRows())
            pages.add(new PartnerOrgBatchPlan.Page(row.getId(),row.getRoot(),row.getTarget()));
        return make(requestId,group,org,target,prepared.getObjects(),pages);
    }

    /** Restore without rereading business rows, which may have changed after the request. */
    public static Evidence restore(String requestId,String group,String org,NativeOrgBatchTargetScope.Evidence target,
            List<BusinessObjectRef> objects,List<PartnerOrgBatchPlan.Page> pages,String digest) throws BusinessException {
        EsignValues.require(digest!=null && digest.matches("[a-f0-9]{64}"),"组织批改准备页签摘要无效。");
        Evidence result=make(requestId,group,org,target,objects,pages);
        EsignValues.require(result.digest.equals(digest),"组织批改准备页签行或归属不完整。");return result;
    }

    static Evidence captureForTest(String requestId,String group,String org,NativeOrgBatchTargetScope.Evidence target,
            List<BusinessObjectRef> objects,NativeOrgBatchSelection.Impact impact) throws BusinessException {
        EsignValues.require(impact!=null && impact.getPage()==target.getPage(),"测试页签范围无效。");
        List<PartnerOrgBatchPlan.Page> pages=new ArrayList<PartnerOrgBatchPlan.Page>();
        for(NativeOrgBatchSelection.Row row:impact.getRows())pages.add(new PartnerOrgBatchPlan.Page(row.getId(),row.getRoot(),row.getTarget()));
        return make(requestId,group,org,target,objects,pages);
    }

    /** Every native outcome must correspond to exactly one previously prepared page and owner. */
    public static void requireOutcome(String requestId,String group,String org,List<BusinessObjectRef> objects,
            Evidence selected,NativeOrgBatchReceipt.Evidence outcome) throws BusinessException {
        EsignValues.require(selected!=null && selected.target!=null && outcome!=null
            && outcome.matches(requestId,group,org,selected.target.getPage())
            && selected.target.getTargets().equals(outcome.getTargets()),"组织批改结果与准备范围不一致。");
        restore(requestId,group,org,selected.target,objects,selected.pages,selected.digest);
        Map<String,String> roots=new HashMap<String,String>();
        for(BusinessObjectRef ref:objects)EsignValues.require(ref!=null && ref.getClientObjectId()!=null
            && roots.put(ref.getClientObjectId(),ref.getObjectPk())==null,"组织批改结果档案无效或重复。");
        Map<String,PartnerOrgBatchPlan.Page> expected=new HashMap<String,PartnerOrgBatchPlan.Page>();
        for(PartnerOrgBatchPlan.Page page:selected.pages)expected.put(page.getId(),page);
        for(NativeOrgBatchReceipt.Page page:outcome.getPages()) {
            PartnerOrgBatchPlan.Page prior=page==null?null:expected.remove(page.getPk());
            EsignValues.require(prior!=null && prior.getRoot().equals(roots.get(page.getClientId()))
                && prior.getTarget().equals(page.getTarget()),"组织批改结果包含未准备或已转移的页签行。");
        }
        EsignValues.require(expected.isEmpty(),"组织批改结果遗漏了准备时固定的页签行。");
    }

    private static Evidence make(String requestId,String group,String org,NativeOrgBatchTargetScope.Evidence target,
            List<BusinessObjectRef> objects,List<PartnerOrgBatchPlan.Page> pages) throws BusinessException {
        EsignValues.require(target!=null && target.getPage()!=null && pages!=null
            && pages.size()<=PartnerBatchDraft.MAX_SINGLE_TRANSACTION_PAGE_ROWS,"组织批改缺少准备页签或超过单事务上限。");
        NativeOrgBatchTargetScope.Evidence checked=NativeOrgBatchTargetScope.restore(requestId,group,org,target.getPage(),objects,
            target.getTargets(),target.getDigest());
        Set<String> roots=new HashSet<String>(),targets=new HashSet<String>(checked.getTargets()),ids=new HashSet<String>();
        for(BusinessObjectRef ref:objects)roots.add(ref.getObjectPk());
        List<PartnerOrgBatchPlan.Page> copy=new ArrayList<PartnerOrgBatchPlan.Page>();
        for(PartnerOrgBatchPlan.Page page:pages) {
            EsignValues.require(page!=null && validPk(page.getId()) && ids.add(page.getId())
                && roots.contains(page.getRoot()) && targets.contains(page.getTarget()),"组织批改准备页签无效、重复或不属于授权范围。");
            copy.add(new PartnerOrgBatchPlan.Page(page.getId(),page.getRoot(),page.getTarget()));
        }
        Collections.sort(copy,new Comparator<PartnerOrgBatchPlan.Page>() { public int compare(PartnerOrgBatchPlan.Page a,PartnerOrgBatchPlan.Page b) {
            return a.getId().compareTo(b.getId());
        }});
        StringBuilder data=new StringBuilder(pack("org-batch-prepared-pages-v1",checked.getDigest(),Integer.toString(copy.size())));
        for(PartnerOrgBatchPlan.Page page:copy)data.append(pack(page.getId(),page.getRoot(),page.getTarget()));
        return new Evidence(checked,copy,SnapshotSupport.sha256(data.toString()));
    }
    private static boolean validPk(String value) { return value!=null && value.matches("[A-Za-z0-9_-]{1,20}"); }
    private static String pack(String... values) {
        StringBuilder result=new StringBuilder();for(String value:values)
            result.append(value==null?-1:value.length()).append(':').append(value==null?"":value);
        return result.toString();
    }
}
