package nc.impl.uapbd.qd.esign;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import nc.bs.bd.assignservice.AssignOrgFilterUtil;
import nc.bs.bd.assignservice.AssignToOrgServiceImpl;
import nc.bs.bd.assignservice.IAssignToOrgService;
import nc.bs.uapbd.qd.esign.audit.SnapshotSupport;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.bs.uapbd.qd.esign.core.MaterialCancelAssignmentRequestScope;
import nc.impl.bd.material.assign.MaterialAssignServiceImpl;
import nc.md.model.IBean;
import nc.vo.bd.assign.AssigntabVO;
import nc.vo.bd.errorlog.ErrorLogUtil;
import nc.vo.pub.BusinessException;

/** Candidate material route preflight; create a fresh instance for each check of NC's cached configuration. */
final class MaterialCancelAssignmentRoutePlan extends MaterialAssignServiceImpl {
    private static final Map<String,String> STANDARD=new HashMap<String,String>();
    static {
        STANDARD.put("nc.impl.bd.material.fi.MaterialFiAssignToOrgService","bd_materialfi");
        STANDARD.put("nc.impl.bd.material.pfc.MaterialPfcAssignToOrgService","bd_materialpfc");
        STANDARD.put("nc.impl.bd.material.pu.MaterialPuAssignToOrgService","bd_materialpu");
        STANDARD.put("nc.impl.bd.material.stock.MaterialStockAssignToOrgService","bd_materialstock");
        STANDARD.put("nc.impl.bd.material.sale.MaterialSaleAssignToOrgService","bd_materialsale");
        STANDARD.put("nc.impl.bd.material.plan.MaterialPlanAssignToOrgService","bd_materialplan");
        STANDARD.put("nc.impl.bd.material.prod.MaterialProdAssignToOrgService","bd_materialprod");
    }
    static final class Plan {
        private final String fingerprint;private final Set<String> tables;
        private final Map<IAssignToOrgService,List<String>> routes;
        private Plan(String fingerprint,Set<String> tables,Map<IAssignToOrgService,List<String>> routes) {
            this.fingerprint=fingerprint;this.tables=Collections.unmodifiableSet(tables);this.routes=routes;
        }
        String fingerprint() { return fingerprint; }
        Set<String> tables() { return tables; }
        Map<IAssignToOrgService,List<String>> routes() { return routes; }
    }
    static MaterialCancelAssignmentRequestScope.RouteReader reader() {
        return new MaterialCancelAssignmentRequestScope.RouteReader() {
            public String fingerprint(String target) throws BusinessException {
                return new MaterialCancelAssignmentRoutePlan().inspect(target).fingerprint();
            }
        };
    }
    void applyPages(String material,Map<IAssignToOrgService,List<String>> routes,ErrorLogUtil errors)
            throws BusinessException {
        cancelAssignByPks(new String[]{material},routes,errors);
    }
    String baseBeanId() throws BusinessException { return getBaseBean().getID(); }
    Plan inspect(String target) throws BusinessException {
        EsignValues.require(target!=null && target.matches("[A-Za-z0-9_-]{1,20}"),"物料分配目标无效。");
        Map<IAssignToOrgService,List<String>> nativeRoutes=groupTargetPks(new String[]{target},false);
        EsignValues.require(nativeRoutes!=null,"无法获取原生物料分配页签。");
        Map<IAssignToOrgService,List<String>> routes=new HashMap<IAssignToOrgService,List<String>>();
        Set<String> tables=new HashSet<String>();tables.add("bd_marorg");List<String> evidence=new ArrayList<String>();
        for(Map.Entry<IAssignToOrgService,List<String>> entry:nativeRoutes.entrySet()) {
            IAssignToOrgService service=entry.getKey();List<String> selected=entry.getValue();
            EsignValues.require(service instanceof AssignToOrgServiceImpl && selected!=null && selected.size()==1
                && target.equals(selected.get(0)),"物料分配页签目标范围无法核实。");
            AssignToOrgServiceImpl tab=(AssignToOrgServiceImpl)service;IBean bean=tab.getOrgBean();
            EsignValues.require(bean!=null && bean.getTable()!=null,"物料分配页签缺少物理元数据。");
            List<String> enabled=AssignOrgFilterUtil.filterEnableOrgIDs(
                AssignOrgFilterUtil.getAssignedOrgType(bean,tab.getOrgFieldName()),selected);
            if(enabled==null || enabled.isEmpty()) continue;
            EsignValues.require(enabled.size()==1 && target.equals(enabled.get(0)),"物料分配页签组织范围已变化。");
            String table=tab.getAssignTableName();table=table==null?null:table.toLowerCase(Locale.ENGLISH);
            String expected=STANDARD.get(service.getClass().getName());
            EsignValues.require(expected!=null && expected.equals(table) && tables.add(table)
                && "pk_material".equalsIgnoreCase(tab.getRelationPkFieldName()) && "pk_org".equalsIgnoreCase(tab.getOrgFieldName())
                && expectedKey(table).equalsIgnoreCase(bean.getTable().getPrimaryKeyName()),
                "物料分配页签包含待核实的服务、表或关联字段。");
            CustomerAssignmentRoutePlan.requireFlat(bean,"物料");AssigntabVO configuration=tab.getAssigntabVO();
            EsignValues.require(configuration!=null && configuration.getPk_assign_tab()!=null
                && service.getClass().getName().equals(configuration.getOrgservice()),"物料分配页签配置缺失或服务不一致。");
            evidence.add(CustomerAssignmentRoutePlan.configurationEvidence(configuration,bean,table,service.getClass().getName(),"物料"));
            routes.put(service,new ArrayList<String>(selected));
        }
        Collections.sort(evidence);StringBuilder value=new StringBuilder(CustomerAssignmentRoutePlan.pack("material-cancel-assignment-routes-v1",target));
        for(String item:evidence) value.append(CustomerAssignmentRoutePlan.pack(item));
        return new Plan(SnapshotSupport.sha256(value.toString()),tables,routes);
    }
    private static String expectedKey(String table) {
        if("bd_materialfi".equals(table)) return "pk_materialfi";
        if("bd_materialpfc".equals(table)) return "pk_materialpfc";
        if("bd_materialpu".equals(table)) return "pk_materialpu";
        if("bd_materialstock".equals(table)) return "pk_materialstock";
        if("bd_materialsale".equals(table)) return "pk_materialsale";
        if("bd_materialplan".equals(table)) return "pk_materialplan";
        if("bd_materialprod".equals(table)) return "pk_materialprod";
        return "";
    }
}

