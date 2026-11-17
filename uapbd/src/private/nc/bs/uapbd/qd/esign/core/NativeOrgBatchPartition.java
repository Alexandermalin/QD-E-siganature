package nc.bs.uapbd.qd.esign.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.PartnerBatchDraft;
import nc.vo.uapbd.qd.esign.PartnerOrgBatchPlan;
import nc.vo.uapbd.qd.esign.PartnerOrgBatchManifest;

/** Splits an already permission-checked selection by physical page identities, not only by root count. */
public final class NativeOrgBatchPartition {
    private NativeOrgBatchPartition() { }
    private static final class Mutable {
        final Set<String> roots=new LinkedHashSet<String>();final List<PartnerOrgBatchPlan.Page> pages=new ArrayList<PartnerOrgBatchPlan.Page>();
    }

    public static PartnerOrgBatchPlan plan(String group,String org,String node,NativeOrgBatchSelection.Impact impact)
            throws BusinessException {
        return plan(group,org,node,impact,PartnerBatchDraft.MAX_SINGLE_TRANSACTION_OBJECTS,
            PartnerBatchDraft.MAX_SINGLE_TRANSACTION_PAGE_ROWS);
    }

    /** The smaller limits are an offline test seam; actual limits are fixed by the public entry point. */
    static PartnerOrgBatchPlan plan(String group,String org,String node,NativeOrgBatchSelection.Impact impact,int maxRoots,int maxPages)
            throws BusinessException {
        EsignValues.require(group!=null && group.matches("[A-Za-z0-9_-]{1,20}") && org!=null
            && org.matches("[A-Za-z0-9_-]{1,20}") && impact!=null && group.equals(impact.getGroup())
            && impact.getPage()!=null && impact.getDomain().equals(PartnerBatchRoute.domain(node))
            && impact.getPage().domain.equals(impact.getDomain()) && maxRoots>0 && maxPages>0
            && maxRoots<=PartnerBatchDraft.MAX_SINGLE_TRANSACTION_OBJECTS
            && maxPages<=PartnerBatchDraft.MAX_SINGLE_TRANSACTION_PAGE_ROWS,
            "组织批改分批计划的集团、节点或上限无效。");
        List<Mutable> parts=new ArrayList<Mutable>();Set<String> seenRoots=new HashSet<String>();Mutable current=null;
        for(NativeOrgBatchSelection.Row row:impact.getRows()) {
            if(current==null || current.pages.size()==maxPages
                    || (!current.roots.contains(row.getRoot()) && current.roots.size()==maxRoots)) {
                current=new Mutable();parts.add(current);
            }
            current.roots.add(row.getRoot());current.pages.add(new PartnerOrgBatchPlan.Page(row.getId(),row.getRoot(),row.getTarget()));
            seenRoots.add(row.getRoot());
        }
        for(String root:impact.getRoots()) if(!seenRoots.contains(root)) {
            if(current==null || current.roots.size()==maxRoots) { current=new Mutable();parts.add(current); }
            current.roots.add(root);
        }
        List<PartnerOrgBatchPlan.Slice> result=new ArrayList<PartnerOrgBatchPlan.Slice>();
        for(Mutable part:parts) result.add(new PartnerOrgBatchPlan.Slice(new ArrayList<String>(part.roots),part.pages));
        EsignValues.require(!result.isEmpty(),"组织批改分批计划缺少所选档案。");
        List<PartnerOrgBatchPlan.Page> fullSelection=new ArrayList<PartnerOrgBatchPlan.Page>();
        for(NativeOrgBatchSelection.Row row:impact.getRows())
            fullSelection.add(new PartnerOrgBatchPlan.Page(row.getId(),row.getRoot(),row.getTarget()));
        String manifest=PartnerOrgBatchManifest.fingerprint(group,org,node,impact.getPage().tabCode,impact.getFingerprint(),
            impact.getRoots(),impact.getTargets(),fullSelection);
        return new PartnerOrgBatchPlan(group,org,node,impact.getPage().tabCode,impact.getFingerprint(),impact.getTargets(),result,
            (long)impact.getRows().size(),manifest);
    }
}
