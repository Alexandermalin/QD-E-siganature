package nc.bs.uapbd.qd.esign.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import nc.bs.bd.assignservice.AssignOrgFilterUtil;
import nc.bs.framework.common.NCLocator;
import nc.bs.uapbd.qd.esign.audit.SnapshotSupport;
import nc.bs.uapbd.qd.esign.persistence.QdEsignStore;
import nc.itf.org.IOrgUnitQryService;
import nc.jdbc.framework.SQLParameter;
import nc.vo.org.OrgVO;
import nc.vo.pub.BusinessException;

/** Candidate expansion of NC customer assignment targets, including automatic credit-control regions. */
public final class CustomerAssignmentTargets {
    private CustomerAssignmentTargets() { }
    public interface Reader {
        boolean organizationInGroup(String group,String org) throws BusinessException;
        List<String> financeOrganizations(List<String> selected) throws BusinessException;
        List<String> groupCreditRegions(String group) throws BusinessException;
        List<String> financeCreditRegions(String financeOrg) throws BusinessException;
        String creditRegionGroup(String region) throws BusinessException;
    }
    public static final class Selection {
        private final List<String> selected,targets;private final String fingerprint;
        private Selection(Set<String> selected,Set<String> targets,String fingerprint) {
            this.selected=Collections.unmodifiableList(new ArrayList<String>(selected));
            this.targets=Collections.unmodifiableList(new ArrayList<String>(targets));this.fingerprint=fingerprint;
        }
        public List<String> getSelected() { return selected; }
        public List<String> getTargets() { return targets; }
        public String getFingerprint() { return fingerprint; }
    }
    /** The reader reproduces NC's unfiltered region queries; invalid or cross-group results fail closed. */
    public static Selection select(String group,String operation,List<String> requested,Reader reader) throws BusinessException {
        EsignValues.require(validPk(group) && ("ASSIGN".equals(operation) || "CANCEL_ASSIGN".equals(operation))
            && requested!=null && !requested.isEmpty() && requested.size()<=1000 && reader!=null,"客户分配缺少集团、动作或目标组织。");
        Set<String> selected=new TreeSet<String>();
        for(String org:requested) EsignValues.require(validPk(org) && selected.add(org)
            && (group.equals(org) || reader.organizationInGroup(group,org)),"客户分配目标组织重复、无效或跨集团。");
        List<String> actualFinance=reader.financeOrganizations(new ArrayList<String>(selected));
        EsignValues.require(actualFinance!=null,"无法核实客户分配的财务组织。");Set<String> finance=new TreeSet<String>();
        for(String org:actualFinance) EsignValues.require(validPk(org) && selected.contains(org) && finance.add(org),
            "客户分配的财务组织与目标范围不一致。");
        Set<String> targets=new TreeSet<String>(selected),groupRegions=new TreeSet<String>();
        Map<String,Set<String>> mapped=new TreeMap<String,Set<String>>();
        if(!finance.isEmpty()) {
            if("ASSIGN".equals(operation)) groupRegions=regions(group,reader.groupCreditRegions(group),reader);
            targets.addAll(groupRegions);
            for(String org:finance) {
                Set<String> regions=regions(group,reader.financeCreditRegions(org),reader);mapped.put(org,regions);targets.addAll(regions);
            }
        }
        EsignValues.require(targets.size()<=1000,"客户分配连带信用控制域超过当前授权上限。");
        StringBuilder evidence=new StringBuilder(pack("customer-assign-v1",group,operation));
        evidence.append(pack(Integer.toString(selected.size()))).append(pack(selected.toArray(new String[selected.size()])));
        evidence.append(pack(Integer.toString(finance.size()))).append(pack(finance.toArray(new String[finance.size()])));
        evidence.append(pack(Integer.toString(groupRegions.size()))).append(pack(groupRegions.toArray(new String[groupRegions.size()])));
        evidence.append(pack(Integer.toString(mapped.size())));
        for(Map.Entry<String,Set<String>> item:mapped.entrySet())
            evidence.append(pack(item.getKey(),Integer.toString(item.getValue().size())))
                .append(pack(item.getValue().toArray(new String[item.getValue().size()])));
        return new Selection(selected,targets,SnapshotSupport.sha256(evidence.toString()));
    }
    public static void verify(Selection previous,Selection current) throws BusinessException {
        EsignValues.require(previous!=null && current!=null && previous.fingerprint.equals(current.fingerprint),
            "客户分配目标组织或信用控制域已变化，请重新发起操作。");
    }
    private static Set<String> regions(String group,List<String> values,Reader reader) throws BusinessException {
        EsignValues.require(values!=null,"无法核实客户分配信用控制域。");Set<String> result=new TreeSet<String>();
        for(String region:values) {
            EsignValues.require(validPk(region) && group.equals(reader.creditRegionGroup(region)),"客户分配信用控制域无效或跨集团。");
            result.add(region); // Native assignment also deduplicates region IDs.
        }
        return result;
    }
    private static boolean validPk(String value) { return value!=null && value.matches("[A-Za-z0-9_-]{1,20}"); }
    private static String pack(String... values) {
        StringBuilder result=new StringBuilder();for(String value:values) result.append(value==null?-1:value.length()).append(':').append(value==null?"":value);
        return result.toString();
    }

    /** These fixed reads match the native service, including the absence of dr filters in its region queries. */
    public static Reader database() {
        return new Reader() {
            private final QdEsignStore store=new QdEsignStore();
            public boolean organizationInGroup(String group,String org) throws BusinessException { return store.isOrganizationInGroup(group,org); }
            public List<String> financeOrganizations(List<String> selected) throws BusinessException {
                IOrgUnitQryService service=NCLocator.getInstance().lookup(IOrgUnitQryService.class);
                EsignValues.require(service!=null,"无法获取原生组织查询服务。");OrgVO[] orgs=service.getOrgs(selected.toArray(new String[selected.size()]));
                EsignValues.require(orgs!=null,"原生组织查询结果不完整。");Set<String> found=new HashSet<String>();
                for(OrgVO org:orgs) EsignValues.require(org!=null && org.getPk_org()!=null && selected.contains(org.getPk_org())
                    && found.add(org.getPk_org()),"原生组织查询包含重复或未选择的组织。");
                EsignValues.require(found.size()==selected.size(),"原生组织查询缺少客户分配目标。");
                return AssignOrgFilterUtil.getFilteredOrgIDsByOrgType("FINANCEORGTYPE000000",orgs);
            }
            public List<String> groupCreditRegions(String group) throws BusinessException {
                SQLParameter parameters=new SQLParameter();parameters.addParam(group);parameters.addParam(1);
                return ids(store.list("SELECT pk_ccregion FROM org_crectlregion WHERE pk_group=? AND layertype=? AND enablestate='2'",parameters));
            }
            public List<String> financeCreditRegions(String financeOrg) throws BusinessException {
                SQLParameter parameters=new SQLParameter();parameters.addParam(financeOrg);
                return ids(store.list("SELECT pk_ccregion FROM org_ccr_financeorg WHERE pk_financeorg=?",parameters));
            }
            public String creditRegionGroup(String region) throws BusinessException {
                SQLParameter parameters=new SQLParameter();parameters.addParam(region);
                List<Map<String,Object>> rows=store.list("SELECT pk_group FROM org_crectlregion WHERE pk_ccregion=?",parameters);
                EsignValues.require(rows!=null && rows.size()==1,"客户分配信用控制域不存在或不唯一。");return EsignValues.text(rows.get(0),"pk_group");
            }
            private List<String> ids(List<Map<String,Object>> rows) {
                List<String> result=new ArrayList<String>();for(Map<String,Object> row:rows) result.add(EsignValues.text(row,"pk_ccregion"));return result;
            }
        };
    }
}
