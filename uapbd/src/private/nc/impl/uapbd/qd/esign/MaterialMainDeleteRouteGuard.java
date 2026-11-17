package nc.impl.uapbd.qd.esign;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import nc.bs.bd.assignservice.AssignToOrgServiceImpl;
import nc.bs.bd.assignservice.IAssignToOrgService;
import nc.bs.dao.BaseDAO;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.impl.bd.material.assign.MaterialAssignServiceImpl;
import nc.md.common.AssociationKind;
import nc.md.model.IAssociation;
import nc.md.model.IBean;
import nc.md.util.MDUtil;
import nc.vo.bd.assign.AssigntabVO;
import nc.vo.pub.BusinessException;

/** Verifies the same dynamically configured organization services used by native master deletion. */
final class MaterialMainDeleteRouteGuard extends MaterialAssignServiceImpl {
    private static final Map<String,String> TABLES=new HashMap<String,String>();
    static {
        TABLES.put("nc.impl.bd.material.fi.MaterialFiAssignToOrgService","bd_materialfi");
        TABLES.put("nc.impl.bd.material.pfc.MaterialPfcAssignToOrgService","bd_materialpfc");
        TABLES.put("nc.impl.bd.material.pfcc.MaterialPFCCAssignToOrgService","bd_materialpfcc");
        TABLES.put("nc.impl.bd.material.pu.MaterialPuAssignToOrgService","bd_materialpu");
        TABLES.put("nc.impl.bd.material.stock.MaterialStockAssignToOrgService","bd_materialstock");
        TABLES.put("nc.impl.bd.material.sale.MaterialSaleAssignToOrgService","bd_materialsale");
        TABLES.put("nc.impl.bd.material.plan.MaterialPlanAssignToOrgService","bd_materialplan");
        TABLES.put("nc.impl.bd.material.prod.MaterialProdAssignToOrgService","bd_materialprod");
        TABLES.put("nc.impl.bd.material.cost.MaterialCostAssignToOrgService","bd_materialcost");
    }

    static void requireKnownRoutes() throws BusinessException {
        // Native groupTargetPks(null,false) reads dynamic assignment metadata. Freeze its registration
        // until the surrounding business transaction finishes, then reject any unmodelled service.
        new BaseDAO().executeUpdate("LOCK TABLE bd_assign_tab IN SHARE MODE NOWAIT");
        MaterialMainDeleteRouteGuard guard=new MaterialMainDeleteRouteGuard();
        IBean root=guard.getBaseBean();
        EsignValues.require(root!=null && root.getTable()!=null && "bd_material".equals(lower(root.getTable().getName()))
            && "pk_material".equalsIgnoreCase(root.getTable().getPrimaryKeyName()),"无法核验物料主档物理元数据。");
        requireKnownChildren(root,"bd_material");
        Map<IAssignToOrgService,List<String>> routes=guard.groupTargetPks(null,false);
        EsignValues.require(routes!=null,"无法核验物料删除的原生组织页签范围。");
        for(IAssignToOrgService service:routes.keySet()) {
            EsignValues.require(service instanceof AssignToOrgServiceImpl,"物料删除包含未核验的组织页签服务。");
            AssignToOrgServiceImpl tab=(AssignToOrgServiceImpl)service;
            String expected=TABLES.get(service.getClass().getName());IBean bean=tab.getOrgBean();
            EsignValues.require(expected!=null && bean!=null && bean.getTable()!=null
                && expected.equals(lower(tab.getAssignTableName())) && expected.equals(lower(bean.getTable().getName()))
                && keyFor(expected).equalsIgnoreCase(bean.getTable().getPrimaryKeyName())
                && "pk_material".equalsIgnoreCase(tab.getRelationPkFieldName())
                && "pk_org".equalsIgnoreCase(tab.getOrgFieldName()),"物料删除包含未核验的组织页签服务或物理关联。");
            AssigntabVO configuration=tab.getAssigntabVO();
            EsignValues.require(configuration!=null && configuration.getPk_assign_tab()!=null
                && service.getClass().getName().equals(configuration.getOrgservice()),"物料删除页签配置不一致。");
            requireKnownChildren(bean,expected);
        }
    }

    static void requireKnownChildren(IBean bean,String parentTable) throws BusinessException {
        requireKnownChildren(bean,parentTable,new HashSet<String>());
    }
    private static void requireKnownChildren(IBean bean,String parentTable,Set<String> visited) throws BusinessException {
        EsignValues.require(visited.add(parentTable),"物料删除元数据包含循环级联。");
        List<?> extras=bean.getExtendTableList();
        EsignValues.require(extras==null || extras.isEmpty(),"物料删除页签含未核验的扩展物理表。");
        List<IAssociation> associations=bean.getAssociations();
        if(associations==null) { visited.remove(parentTable);return; }
        for(IAssociation association:associations) {
            EsignValues.require(association!=null,"物料删除页签关联配置无效。");
            boolean writes=association.getType()==AssociationKind.Composite;
            if(association.getType()==AssociationKind.Relation) {
                EsignValues.require(association.getStartAttribute()!=null,"物料删除页签关联字段缺失。");
                writes=!MDUtil.isRefType(association.getStartAttribute().getDataType())
                    && !association.getStartAttribute().isCalculation();
            }
            if(writes) {
                IBean child=association.getEndBean();
                EsignValues.require(child!=null && child.getTable()!=null
                    && knownChild(parentTable,lower(child.getTable().getName()),child.getTable().getPrimaryKeyName()),
                    "物料删除页签包含未核验的级联表。");
                List<?> childExtras=child.getExtendTableList();
                EsignValues.require(childExtras==null || childExtras.isEmpty(),"物料删除子表含未核验的扩展物理表。");
                requireKnownChildren(child,lower(child.getTable().getName()),visited);
            }
        }
        visited.remove(parentTable);
    }

    static boolean knownChild(String parent,String child,String key) {
        return "bd_material".equals(parent) && "bd_materialconvert".equals(child) && "pk_materialconvert".equalsIgnoreCase(key)
            || "bd_material".equals(parent) && "bd_materialtaxtype".equals(child) && "pk_materialtaxtype".equalsIgnoreCase(key)
            || "bd_material".equals(parent) && "bd_materialfi".equals(child) && "pk_materialfi".equalsIgnoreCase(key)
            || "bd_material".equals(parent) && "bd_materialpfc".equals(child) && "pk_materialpfc".equalsIgnoreCase(key)
            || "bd_material".equals(parent) && "bd_materialpfcc".equals(child) && "pk_mateprofcost".equalsIgnoreCase(key)
            || "bd_material".equals(parent) && "bd_materialpu".equals(child) && "pk_materialpu".equalsIgnoreCase(key)
            || "bd_material".equals(parent) && "bd_materialsale".equals(child) && "pk_materialsale".equalsIgnoreCase(key)
            || "bd_material".equals(parent) && "bd_materialstock".equals(child) && "pk_materialstock".equalsIgnoreCase(key)
            || "bd_material".equals(parent) && "bd_materialplan".equals(child) && "pk_materialplan".equalsIgnoreCase(key)
            || "bd_material".equals(parent) && "bd_materialprod".equals(child) && "pk_materialprod".equalsIgnoreCase(key)
            || "bd_material".equals(parent) && "bd_materialcost".equals(child) && "pk_materialcost".equalsIgnoreCase(key)
            || "bd_materialsale".equals(parent) && "bd_materialbindle".equals(child) && "pk_materialbindle".equalsIgnoreCase(key)
            || "bd_materialstock".equals(parent) && "bd_materialwarh".equals(child) && "pk_materialwarh".equalsIgnoreCase(key)
            || "bd_materialplan".equals(parent) && "bd_materialrepl".equals(child) && "pk_materialrepl".equalsIgnoreCase(key)
            || "bd_materialcost".equals(parent) && "bd_materialcostmod".equals(child) && "pk_materialcostmod".equalsIgnoreCase(key)
            || "bd_materialpfcc".equals(parent) && "bd_materialpfccsub".equals(child) && "pk_mateprofcost_b".equalsIgnoreCase(key);
    }
    private static String lower(String value) { return value==null?null:value.toLowerCase(Locale.ENGLISH); }
    private static String keyFor(String table) {
        if("bd_materialfi".equals(table))return "pk_materialfi";
        if("bd_materialpfc".equals(table))return "pk_materialpfc";
        if("bd_materialpfcc".equals(table))return "pk_mateprofcost";
        if("bd_materialpu".equals(table))return "pk_materialpu";
        if("bd_materialstock".equals(table))return "pk_materialstock";
        if("bd_materialsale".equals(table))return "pk_materialsale";
        if("bd_materialplan".equals(table))return "pk_materialplan";
        if("bd_materialprod".equals(table))return "pk_materialprod";
        if("bd_materialcost".equals(table))return "pk_materialcost";
        return "";
    }
}
