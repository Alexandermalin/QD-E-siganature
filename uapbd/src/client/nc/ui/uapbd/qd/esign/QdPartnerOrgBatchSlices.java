package nc.ui.uapbd.qd.esign;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import nc.vo.pub.BusinessException;
import nc.vo.pub.lang.UFDate;
import nc.vo.pub.lang.UFDateTime;
import nc.vo.pub.lang.UFDouble;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.PartnerBatchDraft;
import nc.vo.uapbd.qd.esign.PartnerOrgBatchPlan;

/** Fix one independently signed draft for every fully verified organization-page slice. */
final class QdPartnerOrgBatchSlices {
    private QdPartnerOrgBatchSlices() { }

    static Prepared prepare(OperationContext context,QdPartnerOrgBatchPlan.Reader reader) throws BusinessException {
        List<PartnerOrgBatchPlan> plans=QdPartnerOrgBatchPlan.readAll(context,reader);
        List<Slice> result=new ArrayList<Slice>();PartnerBatchDraft base=context.getPartnerBatchDraft();
        for(PartnerOrgBatchPlan plan:plans) for(PartnerOrgBatchPlan.Slice slice:plan.getSlices()) {
            PartnerOrgBatchPlan.Page[] pages=slice.getPages().toArray(new PartnerOrgBatchPlan.Page[slice.getPages().size()]);
            PartnerBatchDraft draft;
            try { draft=new PartnerBatchDraft(base.getTabId(),base.getTabCode(),base.getSelectedAttribute(),base.getSelected(),
                base.getValues(),base.getTargetOrgs(),pages); }
            catch(IllegalArgumentException invalid) { throw new BusinessException("组织批改无法固定本批页签。",invalid); }
            result.add(new Slice(context,slice.getRoots(),draft));
        }
        if(result.isEmpty())throw new BusinessException("组织批改没有可授权的批次。");
        return new Prepared(result);
    }

    static final class Prepared {
        private final List<Slice> slices;
        private Prepared(List<Slice> slices) { this.slices=Collections.unmodifiableList(new ArrayList<Slice>(slices)); }
        List<Slice> slices() { return slices; }
    }

    static final class Slice {
        private final String group,org,node,domain;private final String[] roots;private final PartnerBatchDraft draft;
        private Slice(OperationContext context,List<String> roots,PartnerBatchDraft draft) {
            this.group=context.getGroupId();this.org=context.getOrgId();this.node=context.getNodeCode();
            this.domain=context.getObjects().get(0).getObjectType();this.roots=roots.toArray(new String[roots.size()]);this.draft=draft;
        }
        String[] roots() { return roots.clone(); }
        PartnerBatchDraft draft() { return draft; }

        /** Check the server-returned signed context before its request number is sent to the business RPC. */
        void requireAuthorized(OperationContext context) throws BusinessException {
            if(context==null || context.getRequestId()==null || !context.getRequestId().matches("[a-fA-F0-9]{32}")
                    || !same(group,context.getGroupId()) || !same(org,context.getOrgId()) || !same(node,context.getNodeCode())
                    || !"BatchUpdate".equals(context.getButtonCode()) || !"UPDATE".equals(context.getOperationType())
                    || !OperationContext.OWNER_POLICY_PROTOCOL.equals(context.getPolicyProtocol())
                    || context.getMaterialProdImpact()!=null || context.getArchiveTreeSaveDraft()!=null
                    || context.getPartnerMainSaveDraft()!=null || !sameDraft(draft,context.getPartnerBatchDraft())
                    || context.getObjects().size()!=roots.length)
                throw new BusinessException("组织批改授权与计划批次不一致。");
            Set<String> expected=new HashSet<String>(Arrays.asList(roots));String digest=null;
            for(BusinessObjectRef ref:context.getObjects()) {
                String pk=ref==null?null:ref.getObjectPk(),client=ref==null?null:ref.getClientObjectId();
                if(ref==null || !domain.equals(ref.getObjectType()) || !expected.remove(pk) || ref.getBillCode()==null
                        || ref.getParentClientObjectId()!=null || ref.getDesiredOwnerOrg()!=null
                        || (ref.getTargetOrg()!=null && !org.equals(ref.getTargetOrg()))
                        || client==null || !client.matches("orgbulk:[a-f0-9]{64}:[A-Za-z0-9_-]{1,20}"))
                    throw new BusinessException("组织批改授权档案不属于计划批次。");
                String current=client.substring(8,72);
                if(!client.equals("orgbulk:"+current+":"+pk) || (digest!=null && !digest.equals(current)))
                    throw new BusinessException("组织批改授权包含不同的影响范围。");
                digest=current;
            }
            if(!expected.isEmpty())throw new BusinessException("组织批改授权遗漏所选档案。");
        }
    }

    private static boolean sameDraft(PartnerBatchDraft expected,PartnerBatchDraft actual) {
        if(actual==null || !actual.hasPageSelection() || !same(expected.getTabId(),actual.getTabId())
                || !same(expected.getTabCode(),actual.getTabCode()) || !same(expected.getSelectedAttribute(),actual.getSelectedAttribute())
                || !Arrays.equals(expected.getSelected(),actual.getSelected())
                || !new HashSet<String>(Arrays.asList(expected.getTargetOrgs())).equals(new HashSet<String>(Arrays.asList(actual.getTargetOrgs())))
                || !sameValues(expected.getValues(),actual.getValues()))return false;
        Map<String,PartnerOrgBatchPlan.Page> pages=new HashMap<String,PartnerOrgBatchPlan.Page>();
        for(PartnerOrgBatchPlan.Page page:expected.getSelectedPages())pages.put(page.getId(),page);
        PartnerOrgBatchPlan.Page[] received=actual.getSelectedPages();if(received.length!=pages.size())return false;
        for(PartnerOrgBatchPlan.Page page:received) {
            PartnerOrgBatchPlan.Page prior=pages.remove(page.getId());
            if(prior==null || !same(prior.getRoot(),page.getRoot()) || !same(prior.getTarget(),page.getTarget()))return false;
        }
        return pages.isEmpty();
    }
    private static boolean sameValues(Map<String,Object> left,Map<String,Object> right) {
        if(!left.keySet().equals(right.keySet()))return false;
        for(String key:left.keySet())if(!sameValue(left.get(key),right.get(key)))return false;
        return true;
    }
    private static boolean sameValue(Object left,Object right) {
        if(left==null || right==null)return left==right;if(left.getClass()!=right.getClass())return false;
        if(left instanceof java.sql.Timestamp) {
            java.sql.Timestamp a=(java.sql.Timestamp)left,b=(java.sql.Timestamp)right;
            return a.getTime()==b.getTime() && a.getNanos()==b.getNanos();
        }
        if(left instanceof java.util.Date)return ((java.util.Date)left).getTime()==((java.util.Date)right).getTime();
        if(left instanceof UFDateTime)return ((UFDateTime)left).getMillis()==((UFDateTime)right).getMillis();
        if(left instanceof UFDate)return ((UFDate)left).getMillis()==((UFDate)right).getMillis();
        if(left instanceof UFDouble) {
            UFDouble a=(UFDouble)left,b=(UFDouble)right;
            return a.getPower()==b.getPower() && a.toBigDecimal().toString().equals(b.toBigDecimal().toString());
        }
        if(left instanceof Float)return Float.floatToIntBits(((Float)left).floatValue())==Float.floatToIntBits(((Float)right).floatValue());
        if(left instanceof Double)return Double.doubleToLongBits(((Double)left).doubleValue())==Double.doubleToLongBits(((Double)right).doubleValue());
        return left.equals(right);
    }
    private static boolean same(String left,String right) { return left==null?right==null:left.equals(right); }
}
