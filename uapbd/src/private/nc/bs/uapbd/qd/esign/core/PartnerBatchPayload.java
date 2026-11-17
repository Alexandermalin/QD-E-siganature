package nc.bs.uapbd.qd.esign.core;

import nc.md.MDBaseQueryFacade;
import nc.md.model.IBean;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.PartnerBatchDraft;
import nc.vo.uapbd.qd.esign.PartnerOrgBatchPlan;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Resolve a candidate main-tab payload using server-side NC configuration and physical metadata. */
public final class PartnerBatchPayload {
    private PartnerBatchPayload() { }
    interface BeanReader { IBean bean(String id) throws BusinessException; }
    public static final class Organization {
        private final NativeOrgBatchPage page;private final NativeBatchChangeSet changes;private final List<String> targets;
        private final PartnerOrgBatchPlan.Page[] selectedPages;
        private Organization(NativeOrgBatchPage page,NativeBatchChangeSet changes,List<String> targets,PartnerOrgBatchPlan.Page[] selectedPages) {
            this.page=page;this.changes=changes;this.targets=Collections.unmodifiableList(new ArrayList<String>(targets));
            this.selectedPages=selectedPages==null?null:selectedPages.clone();
        }
        public NativeOrgBatchPage getPage() { return page; }
        public NativeBatchChangeSet getChanges() { return changes; }
        public List<String> getTargets() { return targets; }
        public PartnerOrgBatchPlan.Page[] getSelectedPages() { return selectedPages==null?null:selectedPages.clone(); }
    }
    public static NativeBatchChangeSet resolve(String domain,PartnerBatchDraft draft) throws BusinessException {
        EsignValues.require(draft!=null,"快速批改缺少字段和值。");
        // Organization targets need their own page-row authorization and native transaction path.
        EsignValues.require(draft.getTargetOrgs().length==0 && !draft.hasPageSelection(),"组织页签快速批改的原生执行链尚未完成。");
        String md,table;
        if("BD_CUSTOMER".equals(domain)) { md="e4f48eaf-5567-4383-a370-a59cb3e8a451";table="bd_customer"; }
        else if("BD_SUPPLIER".equals(domain)) { md="720dcc7c-ff19-48f4-b9c5-b90906682f45";table="bd_supplier"; }
        else if("BD_MATERIAL".equals(domain)) { md="c7dc0ccd-8872-4eee-8882-160e8f49dfad";table="bd_material"; }
        else throw new BusinessException("快速批改档案类型尚未接入。");
        String[] selected=draft.getSelected();NativeBatchConfiguration.Verified config;
        if(draft.getTabId()==null) {
            EsignValues.require("BD_SUPPLIER".equals(domain),"当前快速批改内置页签尚未接入。");
            config=NativeBatchConfiguration.readBuiltInSupplier(draft.getTabCode(),draft.getSelectedAttribute(),selected);
        } else config=NativeBatchConfiguration.read(domain,draft.getTabId(),draft.getSelectedAttribute(),selected,NativeBatchConfiguration.database());
        IBean bean=MDBaseQueryFacade.getInstance().getBeanByID(md);
        return NativeBatchChangeSet.create(selected,draft.getValues(),NativeBatchMetadataFields.verify(bean,table,selected,config));
    }

    /** Preparation for a future organization-tab path; the existing main-tab executor remains closed to it. */
    public static Organization resolveOrganization(String domain,PartnerBatchDraft draft) throws BusinessException {
        return resolveOrganization(domain,draft,NativeBatchConfiguration.database(),new BeanReader() {
            public IBean bean(String id) throws BusinessException { return MDBaseQueryFacade.getInstance().getBeanByID(id); }
        });
    }
    static Organization resolveOrganization(String domain,PartnerBatchDraft draft,NativeBatchConfiguration.Reader configReader,
            BeanReader beans) throws BusinessException {
        EsignValues.require(draft!=null && beans!=null,"组织页签批改缺少载荷或元数据。");
        NativeOrgBatchPage page=NativeOrgBatchPage.require(domain,draft.getTabCode());
        List<String> targets=NativeOrgBatchSelection.targetIds(draft.getTargetOrgs());
        String[] selected=draft.getSelected();NativeBatchConfiguration.Verified config;
        if(draft.getTabId()==null) config=NativeBatchConfiguration.readBuiltInSupplierOrganization(page,draft.getSelectedAttribute(),selected);
        else config=NativeBatchConfiguration.readOrganization(page,draft.getTabId(),draft.getTabCode(),draft.getSelectedAttribute(),selected,configReader);
        IBean bean=beans.bean(page.metadataId);
        NativeBatchChangeSet changes=NativeBatchChangeSet.create(selected,draft.getValues(),
            NativeBatchMetadataFields.verify(bean,page.table,selected,config));
        return new Organization(page,changes,targets,draft.getSelectedPages());
    }
}
