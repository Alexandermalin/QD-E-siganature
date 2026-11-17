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
import nc.bs.uapbd.qd.esign.core.SupplierAssignmentRequestScope;
import nc.impl.bd.supplier.assign.SupplierAssignServiceImpl;
import nc.md.model.IBean;
import nc.vo.bd.assign.AssigntabVO;
import nc.vo.bd.errorlog.ErrorLogUtil;
import nc.vo.pub.BusinessException;

/** Candidate supplier route preflight; create a fresh instance for each check of NC's cached configuration. */
final class SupplierAssignmentRoutePlan extends SupplierAssignServiceImpl {
    private static final Map<String,String> STANDARD=new HashMap<String,String>();
    static {
        STANDARD.put("nc.impl.bd.supplier.finance.SupplierFinanceAssignToOrgService","bd_supfinance");
        STANDARD.put("nc.impl.bd.supplier.stock.SupplierStockAssignToOrgService","bd_supstock");
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
    static SupplierAssignmentRequestScope.RouteReader reader() {
        return new SupplierAssignmentRequestScope.RouteReader() {
            public String fingerprint(String target) throws BusinessException {
                return new SupplierAssignmentRoutePlan().inspect(target).fingerprint();
            }
        };
    }
    void applyPages(boolean cancel,String supplier,Map<IAssignToOrgService,List<String>> routes,ErrorLogUtil errors)
            throws BusinessException {
        if(cancel) cancelAssignByPks(new String[]{supplier},routes,errors);
        else assignByPks(new String[]{supplier},routes,errors);
    }
    String baseBeanId() throws BusinessException { return getBaseBean().getID(); }
    IBean baseBean() throws BusinessException { return getBaseBean(); }

    List<String> allRouteEvidence() throws BusinessException {
        List<IAssignToOrgService> services=getAssignServices(false);
        EsignValues.require(services!=null,"无法获取供应商删除的原生组织页签。");
        Set<String> tables=new HashSet<String>();
        List<String> evidence=new ArrayList<String>();
        for(IAssignToOrgService service:services) evidence.add(requireRoute(service,tables,true));
        Collections.sort(evidence);
        return evidence;
    }
    List<IBean> allRouteBeans() throws BusinessException {
        List<IAssignToOrgService> services=getAssignServices(false);
        EsignValues.require(services!=null,"无法获取供应商删除的原生组织页签。");
        Set<String> tables=new HashSet<String>();List<IBean> beans=new ArrayList<IBean>();
        for(IAssignToOrgService service:services) {
            requireRoute(service,tables,true);
            beans.add(((AssignToOrgServiceImpl)service).getOrgBean());
        }
        return beans;
    }
    Plan inspect(String target) throws BusinessException {
        EsignValues.require(target!=null && target.matches("[A-Za-z0-9_-]{1,20}"),"供应商分配目标无效。");
        Map<IAssignToOrgService,List<String>> nativeRoutes=groupTargetPks(new String[]{target},false);
        EsignValues.require(nativeRoutes!=null,"无法获取原生供应商分配页签。");
        Map<IAssignToOrgService,List<String>> routes=new HashMap<IAssignToOrgService,List<String>>();
        Set<String> tables=new HashSet<String>();tables.add("bd_suporg");List<String> evidence=new ArrayList<String>();
        for(Map.Entry<IAssignToOrgService,List<String>> entry:nativeRoutes.entrySet()) {
            IAssignToOrgService service=entry.getKey();List<String> selected=entry.getValue();
            EsignValues.require(service instanceof AssignToOrgServiceImpl && selected!=null && selected.size()==1
                && target.equals(selected.get(0)),"供应商分配页签目标范围无法核实。");
            AssignToOrgServiceImpl tab=(AssignToOrgServiceImpl)service;IBean bean=tab.getOrgBean();
            EsignValues.require(bean!=null && bean.getTable()!=null,"供应商分配页签缺少物理元数据。");
            List<String> enabled=AssignOrgFilterUtil.filterEnableOrgIDs(
                AssignOrgFilterUtil.getAssignedOrgType(bean,tab.getOrgFieldName()),selected);
            if(enabled==null || enabled.isEmpty()) continue;
            EsignValues.require(enabled.size()==1 && target.equals(enabled.get(0)),"供应商分配页签组织范围已变化。");
            evidence.add(requireRoute(service,tables,false));
            routes.put(service,new ArrayList<String>(selected));
        }
        Collections.sort(evidence);StringBuilder value=new StringBuilder(CustomerAssignmentRoutePlan.pack("supplier-assignment-routes-v1",target));
        for(String item:evidence) value.append(CustomerAssignmentRoutePlan.pack(item));
        return new Plan(SnapshotSupport.sha256(value.toString()),tables,routes);
    }
    private static String requireRoute(IAssignToOrgService service,Set<String> tables,boolean allowOtherOrgTypes) throws BusinessException {
        EsignValues.require(service instanceof AssignToOrgServiceImpl,"供应商分配页签服务尚未核实。");
        AssignToOrgServiceImpl tab=(AssignToOrgServiceImpl)service;
        String table=tab.getAssignTableName();table=table==null?null:table.toLowerCase(Locale.ENGLISH);
        String expected=STANDARD.get(service.getClass().getName());IBean bean=tab.getOrgBean();
        EsignValues.require(expected!=null && expected.equals(table) && (tables.add(table) || allowOtherOrgTypes)
            && bean!=null && bean.getTable()!=null
            && expected.equalsIgnoreCase(bean.getTable().getName())
            && "pk_supplier".equalsIgnoreCase(tab.getRelationPkFieldName()) && "pk_org".equalsIgnoreCase(tab.getOrgFieldName())
            && expectedKey(table).equalsIgnoreCase(bean.getTable().getPrimaryKeyName()),
            "供应商分配页签包含待核实的服务、表或关联字段。");
        if(!allowOtherOrgTypes) CustomerAssignmentRoutePlan.requireFlat(bean,"供应商");
        AssigntabVO configuration=tab.getAssigntabVO();
        EsignValues.require(configuration!=null && configuration.getPk_assign_tab()!=null
            && service.getClass().getName().equals(configuration.getOrgservice()),"供应商分配页签配置缺失或服务不一致。");
        return CustomerAssignmentRoutePlan.configurationEvidence(configuration,bean,table,service.getClass().getName(),"供应商");
    }
    private static String expectedKey(String table) {
        if("bd_supfinance".equals(table)) return "pk_supfinance";
        if("bd_supstock".equals(table)) return "pk_supstock";
        return "";
    }
}
