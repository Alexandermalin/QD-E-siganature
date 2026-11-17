package nc.bs.uapbd.qd.esign.core;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.PartnerBatchDraft;
import nc.vo.uapbd.qd.esign.PartnerOrgBatchPlan;

/** Distinguishes persisted main-tab and organization-tab batch authorizations. */
public final class PartnerOrgBatchRouteGuard {
    private PartnerOrgBatchRouteGuard() { }
    public static boolean isOrganization(List<BusinessObjectRef> refs,Serializable command) throws BusinessException {
        EsignValues.require(refs!=null && !refs.isEmpty() && command!=null && command.getClass()==PartnerBatchDraft.class,
            "快速批改缺少授权对象或有效载荷。");
        PartnerBatchDraft draft=(PartnerBatchDraft)command;Boolean organization=null;String fingerprint=null,domain=null;
        Set<String> roots=new HashSet<String>(),clients=new HashSet<String>();
        for(BusinessObjectRef ref:refs) {
            String id=ref==null?null:ref.getClientObjectId(),pk=ref==null?null:ref.getObjectPk();
            boolean org=id!=null && id.matches("orgbulk:[a-f0-9]{64}:[A-Za-z0-9_-]{1,20}");
            boolean main=id!=null && id.matches("bulk:[a-f0-9]{64}:[A-Za-z0-9_-]{1,20}");
            EsignValues.require(ref!=null && (org || main) && validPk(pk) && id.endsWith(":"+pk)
                && ("BD_CUSTOMER".equals(ref.getObjectType()) || "BD_SUPPLIER".equals(ref.getObjectType()) || "BD_MATERIAL".equals(ref.getObjectType()))
                && roots.add(pk) && clients.add(id) && (organization==null || organization.booleanValue()==org)
                && (domain==null || domain.equals(ref.getObjectType())),"快速批改授权对象混合、重复或无效。");
            String digest=id.substring(org?8:5,org?72:69);
            EsignValues.require(fingerprint==null || fingerprint.equals(digest),"快速批改混入其他授权范围。");
            fingerprint=digest;domain=ref.getObjectType();organization=Boolean.valueOf(org);
        }
        boolean org=organization.booleanValue();String[] targets=draft.getTargetOrgs();
        EsignValues.require(org==(targets.length>0) && (org?draft.hasPageSelection():!draft.hasPageSelection()),
            "快速批改载荷与已授权的主档或组织页签不一致。");
        if(org) {
            Set<String> targetSet=new HashSet<String>(Arrays.asList(targets)),pages=new HashSet<String>();
            PartnerOrgBatchPlan.Page[] selected=draft.getSelectedPages();
            EsignValues.require(targetSet.size()==targets.length && selected!=null
                && selected.length<=PartnerBatchDraft.MAX_SINGLE_TRANSACTION_PAGE_ROWS,"组织页签目标或行数无效。");
            for(String target:targetSet)EsignValues.require(validPk(target),"组织页签目标无效。");
            for(PartnerOrgBatchPlan.Page page:selected) EsignValues.require(page!=null && validPk(page.getId())
                && pages.add(page.getId()) && roots.contains(page.getRoot()) && targetSet.contains(page.getTarget()),
                "组织页签包含重复、未选择或不属于目标组织的行。");
        }
        return org;
    }
    private static boolean validPk(String value) { return value!=null && value.matches("[A-Za-z0-9_-]{1,20}"); }
}
