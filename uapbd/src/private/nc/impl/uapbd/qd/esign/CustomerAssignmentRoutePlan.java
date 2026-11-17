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
import nc.bs.uapbd.qd.esign.core.CustomerAssignmentRequestScope;
import nc.impl.bd.cust.assign.CustAssignServiceImpl;
import nc.md.common.AssociationKind;
import nc.md.model.IAssociation;
import nc.md.model.IBean;
import nc.md.util.MDUtil;
import nc.vo.bd.assign.AssigntabVO;
import nc.vo.bd.errorlog.ErrorLogUtil;
import nc.vo.pub.BusinessException;
import nc.vo.pub.lang.UFDateTime;

/** Candidate route preflight; a new instance is required to avoid NC's cached assignment-tab list. */
final class CustomerAssignmentRoutePlan extends CustAssignServiceImpl {
    private static final Map<String,String> STANDARD=new HashMap<String,String>();
    static {
        STANDARD.put("nc.impl.bd.cust.finance.CustFinanceAssignToOrgService","bd_custfinance");
        STANDARD.put("nc.impl.bd.cust.sale.CustSaleAssignToOrgService","bd_custsale");
        STANDARD.put("nc.impl.bd.cust.creditctl.CustCreditctlAssignToOrgService","bd_custcreditctl");
        STANDARD.put("nc.impl.bd.cust.creditctl.CustCreditctlAssignToOrgsService","bd_custcreditctl");
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

    static CustomerAssignmentRequestScope.RouteReader reader() {
        return new CustomerAssignmentRequestScope.RouteReader() {
            public String fingerprint(String target) throws BusinessException {
                return new CustomerAssignmentRoutePlan().inspect(target).fingerprint();
            }
        };
    }

    void applyPages(boolean cancel,String customer,Map<IAssignToOrgService,List<String>> routes,ErrorLogUtil errors)
            throws BusinessException {
        if(cancel) cancelAssignByPks(new String[]{customer},routes,errors);
        else assignByPks(new String[]{customer},routes,errors);
    }
    String baseBeanId() throws BusinessException { return getBaseBean().getID(); }
    IBean baseBean() throws BusinessException { return getBaseBean(); }

    /** Native main deletion calls every configured assignment service with all eligible organizations. */
    List<String> allRouteEvidence() throws BusinessException {
        List<IAssignToOrgService> services=getAssignServices(false);
        EsignValues.require(services!=null,"无法获取客户删除的原生组织页签。");
        Set<String> tables=new HashSet<String>();
        List<String> evidence=new ArrayList<String>();
        for(IAssignToOrgService service:services) evidence.add(requireRoute(service,tables,true));
        Collections.sort(evidence);
        return evidence;
    }

    /** Main deletion snapshots each configured page's full server-side cascade metadata. */
    List<IBean> allRouteBeans() throws BusinessException {
        List<IAssignToOrgService> services=getAssignServices(false);
        EsignValues.require(services!=null,"无法获取客户删除的原生组织页签。");
        Set<String> tables=new HashSet<String>();List<IBean> beans=new ArrayList<IBean>();
        for(IAssignToOrgService service:services) {
            requireRoute(service,tables,true);
            beans.add(((AssignToOrgServiceImpl)service).getOrgBean());
        }
        return beans;
    }

    Plan inspect(String target) throws BusinessException {
        EsignValues.require(target!=null && target.matches("[A-Za-z0-9_-]{1,20}"),"客户分配目标无效。");
        Map<IAssignToOrgService,List<String>> nativeRoutes=groupTargetPks(new String[]{target},false);
        EsignValues.require(nativeRoutes!=null,"无法获取原生客户分配页签。");
        Map<IAssignToOrgService,List<String>> routes=new HashMap<IAssignToOrgService,List<String>>();
        Set<String> tables=new HashSet<String>();tables.add("bd_custorg");List<String> evidence=new ArrayList<String>();
        for(Map.Entry<IAssignToOrgService,List<String>> entry:nativeRoutes.entrySet()) {
            IAssignToOrgService service=entry.getKey();List<String> selected=entry.getValue();
            EsignValues.require(service instanceof AssignToOrgServiceImpl && selected!=null && selected.size()==1
                && target.equals(selected.get(0)),"客户分配页签目标范围无法核实。");
            AssignToOrgServiceImpl tab=(AssignToOrgServiceImpl)service;IBean bean=tab.getOrgBean();
            EsignValues.require(bean!=null && bean.getTable()!=null,"客户分配页签缺少物理元数据。");
            List<String> enabled=AssignOrgFilterUtil.filterEnableOrgIDs(
                AssignOrgFilterUtil.getAssignedOrgType(bean,tab.getOrgFieldName()),selected);
            if(enabled==null || enabled.isEmpty()) continue;
            EsignValues.require(enabled.size()==1 && target.equals(enabled.get(0)),"客户分配页签组织范围已变化。");
            evidence.add(requireRoute(service,tables,false));
            routes.put(service,new ArrayList<String>(selected));
        }
        Collections.sort(evidence);StringBuilder value=new StringBuilder(pack("customer-assignment-routes-v1",target));
        for(String item:evidence) value.append(pack(item));
        return new Plan(SnapshotSupport.sha256(value.toString()),tables,routes);
    }
    private static String requireRoute(IAssignToOrgService service,Set<String> tables,boolean allowOtherOrgTypes) throws BusinessException {
        EsignValues.require(service instanceof AssignToOrgServiceImpl,"客户分配页签服务尚未核实。");
        AssignToOrgServiceImpl tab=(AssignToOrgServiceImpl)service;
        String table=tab.getAssignTableName();table=table==null?null:table.toLowerCase(Locale.ENGLISH);
        String expected=STANDARD.get(service.getClass().getName());IBean bean=tab.getOrgBean();
        EsignValues.require(expected!=null && expected.equals(table) && (tables.add(table) || allowOtherOrgTypes)
            && bean!=null && bean.getTable()!=null
            && expected.equalsIgnoreCase(bean.getTable().getName())
            && "pk_customer".equalsIgnoreCase(tab.getRelationPkFieldName()) && "pk_org".equalsIgnoreCase(tab.getOrgFieldName())
            && expectedKey(table).equalsIgnoreCase(bean.getTable().getPrimaryKeyName()),
            "客户分配页签包含待核实的服务、表或关联字段。");
        if(!allowOtherOrgTypes) requireFlat(bean,"客户");
        AssigntabVO configuration=tab.getAssigntabVO();
        EsignValues.require(configuration!=null && configuration.getPk_assign_tab()!=null
            && service.getClass().getName().equals(configuration.getOrgservice()),"客户分配页签配置缺失或服务不一致。");
        return configurationEvidence(configuration,bean,table,service.getClass().getName(),"客户");
    }
    private static String expectedKey(String table) {
        if("bd_custfinance".equals(table)) return "pk_custfinance";
        if("bd_custsale".equals(table)) return "pk_custsale";
        if("bd_custcreditctl".equals(table)) return "pk_custcreditctl";
        return "";
    }
    static void requireFlat(IBean bean,String name) throws BusinessException {
        List<IAssociation> associations=bean.getAssociations();if(associations==null) return;
        for(IAssociation association:associations) {
            EsignValues.require(association!=null,name+"分配元数据关联无效。");
            if(association.getType()==AssociationKind.Composite) throw new BusinessException(name+"分配页签含待核实的级联子表。");
            if(association.getType()==AssociationKind.Relation) {
                EsignValues.require(association.getStartAttribute()!=null,name+"分配元数据关联字段缺失。");
                EsignValues.require(MDUtil.isRefType(association.getStartAttribute().getDataType())
                    || association.getStartAttribute().isCalculation(),name+"分配页签含待核实的级联子表。");
            }
        }
    }
    static String configurationEvidence(AssigntabVO config,IBean bean,String table,String service,String name) throws BusinessException {
        String[] fields=config.getAttributeNames();EsignValues.require(fields!=null && fields.length>0,name+"分配页签字段配置为空。");
        java.util.Arrays.sort(fields);StringBuilder value=new StringBuilder(pack(service,bean.getID(),table,config.getTableName()));
        for(String field:fields) {
            Object raw=config.getAttributeValue(field);
            EsignValues.require(raw==null || raw instanceof String || raw instanceof Number || raw instanceof UFDateTime,
                name+"分配页签配置存在未核实的数据类型。");
            value.append(pack(field,raw==null?null:raw.getClass().getName(),raw==null?null:raw.toString()));
        }
        return value.toString();
    }
    static String pack(String... values) {
        StringBuilder result=new StringBuilder();for(String value:values)
            result.append(value==null?-1:value.length()).append(':').append(value==null?"":value);
        return result.toString();
    }
}
