package nc.impl.uapbd.qd.esign;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import nc.bs.bd.batchupdate.BatchUpdateOrgTabService;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.bs.uapbd.qd.esign.core.NativeBatchChangeSet;
import nc.bs.uapbd.qd.esign.core.NativeBatchFailureTracker;
import nc.bs.uapbd.qd.esign.core.NativeOrgBatchPage;
import nc.bs.uapbd.qd.esign.core.NativeOrgBatchSelection;
import nc.bs.uapbd.qd.esign.core.PartnerOrgBatchScope;
import nc.vo.bd.errorlog.ErrLogReturnValue;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.uapbd.qd.esign.PartnerBatchDraft;

/** Local native-service candidates. The caller must remain in the verified lock transaction. */
final class NativeOrgBatchNativeService {
    private NativeOrgBatchNativeService() { }
    interface Trace { Map<String,String> excluded(); }
    static final class Execution {
        final ErrLogReturnValue result;final Map<String,String> excluded;
        Execution(ErrLogReturnValue result,Map<String,String> excluded) { this.result=result;this.excluded=excluded; }
    }
    static final class Bound {
        final NativeOrgBatchPage page;final BatchUpdateOrgTabService<?> service;final Class<? extends SuperVO> type;
        private boolean used;
        Bound(NativeOrgBatchPage page,BatchUpdateOrgTabService<?> service,Class<? extends SuperVO> type) {
            this.page=page;this.service=service;this.type=type;
        }
        void verifyDeclaration() throws BusinessException {
            String[] children=service.getChildrenAttrNames();
            EsignValues.require(page.metadataId.equals(service.getMDId()) && page.rootMetadataId().equals(service.getBaseMDId())
                && page.nativeType().equals(type) && Arrays.equals(page.childAttributes(),children==null?new String[0]:children) && service instanceof Trace,
                "原生组织页签服务或子属性与受控范围不一致。");
        }
        Execution execute(PartnerOrgBatchScope.Prepared scope,String[] permissionOrgs) throws BusinessException {
            EsignValues.require(scope!=null,"组织页签批改缺少核实后的授权范围。");
            scope.requireLockedVerification();verifyDeclaration();
            EsignValues.require(scope.getImpact()!=null && scope.getImpact().getPage()==page && scope.getPayload()!=null
                && scope.getPayload().getPage()==page && scope.getPayload().getChanges()!=null && !used,
                "组织页签批改服务与授权范围不一致或已经使用。");
            EsignValues.require(permissionOrgs!=null && permissionOrgs.length>0,"组织页签批改缺少原生组织权限。");
            Set<String> unique=new HashSet<String>();
            for(String org:permissionOrgs) EsignValues.require(org!=null && org.matches("[A-Za-z0-9_-]{1,20}") && unique.add(org),
                "原生组织权限无效或重复。");
            used=true;
            String[] ids=pageIds(scope.getImpact());
            if(ids.length==0) return new Execution(null,Collections.<String,String>emptyMap());
            EsignValues.require(type.equals(service.getVOClass()) && page.table.equalsIgnoreCase(service.getTableName())
                && page.key.equalsIgnoreCase(service.getPkFieldName()),"原生组织页签物理元数据已变化。");
            NativeBatchChangeSet changes=scope.getPayload().getChanges();
            List<String> targets=scope.getImpact().getTargets();
            ErrLogReturnValue result=service.batchUpdateByPksForNewTrans(changes.getSelectedFields(),changes.copyValues(),
                permissionOrgs.clone(),targets.toArray(new String[targets.size()]),ids,true);
            EsignValues.require(result!=null,"原生组织页签批改未返回可核对结果。");
            scope.requireLockedVerification();
            return new Execution(result,((Trace)service).excluded());
        }
    }
    static String[] pageIds(NativeOrgBatchSelection.Impact impact) throws BusinessException {
        EsignValues.require(impact!=null && impact.getRows().size()<=PartnerBatchDraft.MAX_SINGLE_TRANSACTION_PAGE_ROWS,
            "组织页签批改缺少已核定的物理行或超过单事务上限。");
        String[] result=new String[impact.getRows().size()];Set<String> ids=new HashSet<String>();
        for(int i=0;i<result.length;i++) {
            String id=impact.getRows().get(i).getId();
            EsignValues.require(id!=null && ids.add(id),"组织页签批改物理行缺少唯一主键。");result[i]=id;
        }
        return result;
    }
    static Bound open(NativeOrgBatchPage page) throws BusinessException {
        EsignValues.require(page!=null,"组织页签批改缺少原生服务。");
        Bound result;
        switch(page) {
            case CUSTOMER_FINANCE: result=new Bound(page,new CustomerFinance(),nc.vo.bd.cust.finance.CustFinanceVO.class);break;
            case CUSTOMER_SALE: result=new Bound(page,new CustomerSale(),nc.vo.bd.cust.saleinfo.CustsaleVO.class);break;
            case CUSTOMER_CREDIT: result=new Bound(page,new CustomerCredit(),nc.vo.bd.cust.creditctl.CustCreditCtlVO.class);break;
            case SUPPLIER_FINANCE: result=new Bound(page,new SupplierFinance(),nc.vo.bd.supplier.finance.SupFinanceVO.class);break;
            case SUPPLIER_STOCK: result=new Bound(page,new SupplierStock(),nc.vo.bd.supplier.stock.SupStockVO.class);break;
            case MATERIAL_FINANCE: result=new Bound(page,new MaterialFinance(),nc.vo.bd.material.fi.MaterialFiVO.class);break;
            case MATERIAL_PURCHASE: result=new Bound(page,new MaterialPurchase(),nc.vo.bd.material.pu.MaterialPuVO.class);break;
            case MATERIAL_SALE: result=new Bound(page,new MaterialSale(),nc.vo.bd.material.sale.MaterialSaleVO.class);break;
            case MATERIAL_STOCK: result=new Bound(page,new MaterialStock(),nc.vo.bd.material.stock.MaterialStockVO.class);break;
            case MATERIAL_PLAN: result=new Bound(page,new MaterialPlan(),nc.vo.bd.material.plan.MaterialPlanVO.class);break;
            case MATERIAL_PROD: result=new Bound(page,new MaterialProd(),nc.vo.bd.material.prod.MaterialProdVO.class);break;
            case MATERIAL_COST: result=new Bound(page,new MaterialCost(),nc.vo.bd.material.cost.MaterialCostVO.class);break;
            case MATERIAL_PFC: result=new Bound(page,new MaterialPfc(),nc.vo.bd.material.pfc.MaterialPfcVO.class);break;
            case MATERIAL_PFCC: result=new Bound(page,new MaterialPfcc(),nc.vo.bd.material.pfcc.MaterialPFCCVO.class);break;
            default: throw new BusinessException("组织页签批改原生服务尚未核实。");
        }
        result.verifyDeclaration();return result;
    }

    private static final class CustomerFinance extends nc.impl.bd.cust.finance.CustFinanceBatchUpdateForNewTransServiceImpl implements Trace {
        private final NativeBatchFailureTracker tracker=new NativeBatchFailureTracker();
        @Override protected List<nc.vo.bd.cust.finance.CustFinanceVO> filterPermissionVOs(List<nc.vo.bd.cust.finance.CustFinanceVO> rows,String[] orgs) throws BusinessException {
            Set<String> before=NativeBatchFailureTracker.snapshot(rows);
            List<nc.vo.bd.cust.finance.CustFinanceVO> result=super.filterPermissionVOs(rows,orgs);
            tracker.filtered("PERMISSION",before,result);return result;
        }
        @Override protected List<nc.vo.bd.cust.finance.CustFinanceVO> validateVOs(List<nc.vo.bd.cust.finance.CustFinanceVO> rows) throws BusinessException {
            Set<String> before=NativeBatchFailureTracker.snapshot(rows);
            List<nc.vo.bd.cust.finance.CustFinanceVO> result=super.validateVOs(rows);
            tracker.filtered("VALIDATION",before,result);return result;
        }
        @Override protected nc.vo.bd.cust.finance.CustFinanceVO[] checkBeforeUpdate(List<nc.vo.bd.cust.finance.CustFinanceVO> rows) throws BusinessException {
            Set<String> before=NativeBatchFailureTracker.snapshot(rows);
            nc.vo.bd.cust.finance.CustFinanceVO[] result=super.checkBeforeUpdate(rows);
            tracker.filtered("BEFORE_EVENT",before,result==null?Collections.<nc.vo.bd.cust.finance.CustFinanceVO>emptyList():Arrays.asList(result));return result;
        }
        public Map<String,String> excluded() { return tracker.excluded(); }
    }

    private static final class CustomerSale extends nc.impl.bd.cust.sale.CustSaleBatchUpdateForNewTransServiceImpl implements Trace {
        private final NativeBatchFailureTracker tracker=new NativeBatchFailureTracker();
        @Override protected List<nc.vo.bd.cust.saleinfo.CustsaleVO> filterPermissionVOs(List<nc.vo.bd.cust.saleinfo.CustsaleVO> rows,String[] orgs) throws BusinessException {
            Set<String> before=NativeBatchFailureTracker.snapshot(rows);
            List<nc.vo.bd.cust.saleinfo.CustsaleVO> result=super.filterPermissionVOs(rows,orgs);
            tracker.filtered("PERMISSION",before,result);return result;
        }
        @Override protected List<nc.vo.bd.cust.saleinfo.CustsaleVO> validateVOs(List<nc.vo.bd.cust.saleinfo.CustsaleVO> rows) throws BusinessException {
            Set<String> before=NativeBatchFailureTracker.snapshot(rows);
            List<nc.vo.bd.cust.saleinfo.CustsaleVO> result=super.validateVOs(rows);
            tracker.filtered("VALIDATION",before,result);return result;
        }
        @Override protected nc.vo.bd.cust.saleinfo.CustsaleVO[] checkBeforeUpdate(List<nc.vo.bd.cust.saleinfo.CustsaleVO> rows) throws BusinessException {
            Set<String> before=NativeBatchFailureTracker.snapshot(rows);
            nc.vo.bd.cust.saleinfo.CustsaleVO[] result=super.checkBeforeUpdate(rows);
            tracker.filtered("BEFORE_EVENT",before,result==null?Collections.<nc.vo.bd.cust.saleinfo.CustsaleVO>emptyList():Arrays.asList(result));return result;
        }
        public Map<String,String> excluded() { return tracker.excluded(); }
    }

    private static final class CustomerCredit extends nc.impl.bd.cust.creditctl.CustCreditctlBatchUpdateServiceImpl implements Trace {
        private final NativeBatchFailureTracker tracker=new NativeBatchFailureTracker();
        @Override protected List<nc.vo.bd.cust.creditctl.CustCreditCtlVO> filterPermissionVOs(List<nc.vo.bd.cust.creditctl.CustCreditCtlVO> rows,String[] orgs) throws BusinessException {
            Set<String> before=NativeBatchFailureTracker.snapshot(rows);
            List<nc.vo.bd.cust.creditctl.CustCreditCtlVO> result=super.filterPermissionVOs(rows,orgs);
            tracker.filtered("PERMISSION",before,result);return result;
        }
        @Override protected List<nc.vo.bd.cust.creditctl.CustCreditCtlVO> validateVOs(List<nc.vo.bd.cust.creditctl.CustCreditCtlVO> rows) throws BusinessException {
            Set<String> before=NativeBatchFailureTracker.snapshot(rows);
            List<nc.vo.bd.cust.creditctl.CustCreditCtlVO> result=super.validateVOs(rows);
            tracker.filtered("VALIDATION",before,result);return result;
        }
        @Override protected nc.vo.bd.cust.creditctl.CustCreditCtlVO[] checkBeforeUpdate(List<nc.vo.bd.cust.creditctl.CustCreditCtlVO> rows) throws BusinessException {
            Set<String> before=NativeBatchFailureTracker.snapshot(rows);
            nc.vo.bd.cust.creditctl.CustCreditCtlVO[] result=super.checkBeforeUpdate(rows);
            tracker.filtered("BEFORE_EVENT",before,result==null?Collections.<nc.vo.bd.cust.creditctl.CustCreditCtlVO>emptyList():Arrays.asList(result));return result;
        }
        public Map<String,String> excluded() { return tracker.excluded(); }
    }

    private static final class SupplierFinance extends nc.impl.bd.supplier.finance.SupplierFiBatchUpdateForNewTransServiceImpl implements Trace {
        private final NativeBatchFailureTracker tracker=new NativeBatchFailureTracker();
        @Override protected List<nc.vo.bd.supplier.finance.SupFinanceVO> filterPermissionVOs(List<nc.vo.bd.supplier.finance.SupFinanceVO> rows,String[] orgs) throws BusinessException {
            Set<String> before=NativeBatchFailureTracker.snapshot(rows);
            List<nc.vo.bd.supplier.finance.SupFinanceVO> result=super.filterPermissionVOs(rows,orgs);
            tracker.filtered("PERMISSION",before,result);return result;
        }
        @Override protected List<nc.vo.bd.supplier.finance.SupFinanceVO> validateVOs(List<nc.vo.bd.supplier.finance.SupFinanceVO> rows) throws BusinessException {
            Set<String> before=NativeBatchFailureTracker.snapshot(rows);
            List<nc.vo.bd.supplier.finance.SupFinanceVO> result=super.validateVOs(rows);
            tracker.filtered("VALIDATION",before,result);return result;
        }
        @Override protected nc.vo.bd.supplier.finance.SupFinanceVO[] checkBeforeUpdate(List<nc.vo.bd.supplier.finance.SupFinanceVO> rows) throws BusinessException {
            Set<String> before=NativeBatchFailureTracker.snapshot(rows);
            nc.vo.bd.supplier.finance.SupFinanceVO[] result=super.checkBeforeUpdate(rows);
            tracker.filtered("BEFORE_EVENT",before,result==null?Collections.<nc.vo.bd.supplier.finance.SupFinanceVO>emptyList():Arrays.asList(result));return result;
        }
        public Map<String,String> excluded() { return tracker.excluded(); }
    }

    private static final class SupplierStock extends nc.impl.bd.supplier.stock.SupplierStockBatchUpdateForNewTransServiceImpl implements Trace {
        private final NativeBatchFailureTracker tracker=new NativeBatchFailureTracker();
        @Override protected List<nc.vo.bd.supplier.stock.SupStockVO> filterPermissionVOs(List<nc.vo.bd.supplier.stock.SupStockVO> rows,String[] orgs) throws BusinessException {
            Set<String> before=NativeBatchFailureTracker.snapshot(rows);
            List<nc.vo.bd.supplier.stock.SupStockVO> result=super.filterPermissionVOs(rows,orgs);
            tracker.filtered("PERMISSION",before,result);return result;
        }
        @Override protected List<nc.vo.bd.supplier.stock.SupStockVO> validateVOs(List<nc.vo.bd.supplier.stock.SupStockVO> rows) throws BusinessException {
            Set<String> before=NativeBatchFailureTracker.snapshot(rows);
            List<nc.vo.bd.supplier.stock.SupStockVO> result=super.validateVOs(rows);
            tracker.filtered("VALIDATION",before,result);return result;
        }
        @Override protected nc.vo.bd.supplier.stock.SupStockVO[] checkBeforeUpdate(List<nc.vo.bd.supplier.stock.SupStockVO> rows) throws BusinessException {
            Set<String> before=NativeBatchFailureTracker.snapshot(rows);
            nc.vo.bd.supplier.stock.SupStockVO[] result=super.checkBeforeUpdate(rows);
            tracker.filtered("BEFORE_EVENT",before,result==null?Collections.<nc.vo.bd.supplier.stock.SupStockVO>emptyList():Arrays.asList(result));return result;
        }
        public Map<String,String> excluded() { return tracker.excluded(); }
    }

    private static final class MaterialFinance extends nc.impl.bd.material.fi.MaterialFiBatchUpdateForNewTransServiceImpl implements Trace {
        private final NativeBatchFailureTracker tracker=new NativeBatchFailureTracker();
        @Override protected List<nc.vo.bd.material.fi.MaterialFiVO> filterPermissionVOs(List<nc.vo.bd.material.fi.MaterialFiVO> rows,String[] orgs) throws BusinessException {
            Set<String> before=NativeBatchFailureTracker.snapshot(rows);
            List<nc.vo.bd.material.fi.MaterialFiVO> result=super.filterPermissionVOs(rows,orgs);
            tracker.filtered("PERMISSION",before,result);return result;
        }
        @Override protected List<nc.vo.bd.material.fi.MaterialFiVO> validateVOs(List<nc.vo.bd.material.fi.MaterialFiVO> rows) throws BusinessException {
            Set<String> before=NativeBatchFailureTracker.snapshot(rows);
            List<nc.vo.bd.material.fi.MaterialFiVO> result=super.validateVOs(rows);
            tracker.filtered("VALIDATION",before,result);return result;
        }
        @Override protected nc.vo.bd.material.fi.MaterialFiVO[] checkBeforeUpdate(List<nc.vo.bd.material.fi.MaterialFiVO> rows) throws BusinessException {
            Set<String> before=NativeBatchFailureTracker.snapshot(rows);
            nc.vo.bd.material.fi.MaterialFiVO[] result=super.checkBeforeUpdate(rows);
            tracker.filtered("BEFORE_EVENT",before,result==null?Collections.<nc.vo.bd.material.fi.MaterialFiVO>emptyList():Arrays.asList(result));return result;
        }
        public Map<String,String> excluded() { return tracker.excluded(); }
    }

    private static final class MaterialPurchase extends nc.impl.bd.material.pu.MaterialPuBatchUpdateForNewTransServiceImpl implements Trace {
        private final NativeBatchFailureTracker tracker=new NativeBatchFailureTracker();
        @Override protected List<nc.vo.bd.material.pu.MaterialPuVO> filterPermissionVOs(List<nc.vo.bd.material.pu.MaterialPuVO> rows,String[] orgs) throws BusinessException {
            Set<String> before=NativeBatchFailureTracker.snapshot(rows);
            List<nc.vo.bd.material.pu.MaterialPuVO> result=super.filterPermissionVOs(rows,orgs);
            tracker.filtered("PERMISSION",before,result);return result;
        }
        @Override protected List<nc.vo.bd.material.pu.MaterialPuVO> validateVOs(List<nc.vo.bd.material.pu.MaterialPuVO> rows) throws BusinessException {
            Set<String> before=NativeBatchFailureTracker.snapshot(rows);
            List<nc.vo.bd.material.pu.MaterialPuVO> result=super.validateVOs(rows);
            tracker.filtered("VALIDATION",before,result);return result;
        }
        @Override protected nc.vo.bd.material.pu.MaterialPuVO[] checkBeforeUpdate(List<nc.vo.bd.material.pu.MaterialPuVO> rows) throws BusinessException {
            Set<String> before=NativeBatchFailureTracker.snapshot(rows);
            nc.vo.bd.material.pu.MaterialPuVO[] result=super.checkBeforeUpdate(rows);
            tracker.filtered("BEFORE_EVENT",before,result==null?Collections.<nc.vo.bd.material.pu.MaterialPuVO>emptyList():Arrays.asList(result));return result;
        }
        public Map<String,String> excluded() { return tracker.excluded(); }
    }

    private static final class MaterialSale extends nc.impl.bd.material.sale.MaterialSaleBatchUpdateForNewTransServiceImpl implements Trace {
        private final NativeBatchFailureTracker tracker=new NativeBatchFailureTracker();
        @Override protected List<nc.vo.bd.material.sale.MaterialSaleVO> filterPermissionVOs(List<nc.vo.bd.material.sale.MaterialSaleVO> rows,String[] orgs) throws BusinessException {
            Set<String> before=NativeBatchFailureTracker.snapshot(rows);
            List<nc.vo.bd.material.sale.MaterialSaleVO> result=super.filterPermissionVOs(rows,orgs);
            tracker.filtered("PERMISSION",before,result);return result;
        }
        @Override protected List<nc.vo.bd.material.sale.MaterialSaleVO> validateVOs(List<nc.vo.bd.material.sale.MaterialSaleVO> rows) throws BusinessException {
            Set<String> before=NativeBatchFailureTracker.snapshot(rows);
            List<nc.vo.bd.material.sale.MaterialSaleVO> result=super.validateVOs(rows);
            tracker.filtered("VALIDATION",before,result);return result;
        }
        @Override protected nc.vo.bd.material.sale.MaterialSaleVO[] checkBeforeUpdate(List<nc.vo.bd.material.sale.MaterialSaleVO> rows) throws BusinessException {
            Set<String> before=NativeBatchFailureTracker.snapshot(rows);
            nc.vo.bd.material.sale.MaterialSaleVO[] result=super.checkBeforeUpdate(rows);
            tracker.filtered("BEFORE_EVENT",before,result==null?Collections.<nc.vo.bd.material.sale.MaterialSaleVO>emptyList():Arrays.asList(result));return result;
        }
        public Map<String,String> excluded() { return tracker.excluded(); }
    }

    private static final class MaterialStock extends nc.impl.bd.material.stock.MaterialStockBatchUpdateForNewTransServiceImpl implements Trace {
        private final NativeBatchFailureTracker tracker=new NativeBatchFailureTracker();
        @Override protected List<nc.vo.bd.material.stock.MaterialStockVO> filterPermissionVOs(List<nc.vo.bd.material.stock.MaterialStockVO> rows,String[] orgs) throws BusinessException {
            Set<String> before=NativeBatchFailureTracker.snapshot(rows);
            List<nc.vo.bd.material.stock.MaterialStockVO> result=super.filterPermissionVOs(rows,orgs);
            tracker.filtered("PERMISSION",before,result);return result;
        }
        @Override protected List<nc.vo.bd.material.stock.MaterialStockVO> validateVOs(List<nc.vo.bd.material.stock.MaterialStockVO> rows) throws BusinessException {
            Set<String> before=NativeBatchFailureTracker.snapshot(rows);
            List<nc.vo.bd.material.stock.MaterialStockVO> result=super.validateVOs(rows);
            tracker.filtered("VALIDATION",before,result);return result;
        }
        @Override protected nc.vo.bd.material.stock.MaterialStockVO[] checkBeforeUpdate(List<nc.vo.bd.material.stock.MaterialStockVO> rows) throws BusinessException {
            Set<String> before=NativeBatchFailureTracker.snapshot(rows);
            nc.vo.bd.material.stock.MaterialStockVO[] result=super.checkBeforeUpdate(rows);
            tracker.filtered("BEFORE_EVENT",before,result==null?Collections.<nc.vo.bd.material.stock.MaterialStockVO>emptyList():Arrays.asList(result));return result;
        }
        public Map<String,String> excluded() { return tracker.excluded(); }
    }

    private static final class MaterialPlan extends nc.impl.bd.material.plan.MaterialPlanBatchUpdateForNewTransServiceImpl implements Trace {
        private final NativeBatchFailureTracker tracker=new NativeBatchFailureTracker();
        @Override protected List<nc.vo.bd.material.plan.MaterialPlanVO> filterPermissionVOs(List<nc.vo.bd.material.plan.MaterialPlanVO> rows,String[] orgs) throws BusinessException {
            Set<String> before=NativeBatchFailureTracker.snapshot(rows);
            List<nc.vo.bd.material.plan.MaterialPlanVO> result=super.filterPermissionVOs(rows,orgs);
            tracker.filtered("PERMISSION",before,result);return result;
        }
        @Override protected List<nc.vo.bd.material.plan.MaterialPlanVO> validateVOs(List<nc.vo.bd.material.plan.MaterialPlanVO> rows) throws BusinessException {
            Set<String> before=NativeBatchFailureTracker.snapshot(rows);
            List<nc.vo.bd.material.plan.MaterialPlanVO> result=super.validateVOs(rows);
            tracker.filtered("VALIDATION",before,result);return result;
        }
        @Override protected nc.vo.bd.material.plan.MaterialPlanVO[] checkBeforeUpdate(List<nc.vo.bd.material.plan.MaterialPlanVO> rows) throws BusinessException {
            Set<String> before=NativeBatchFailureTracker.snapshot(rows);
            nc.vo.bd.material.plan.MaterialPlanVO[] result=super.checkBeforeUpdate(rows);
            tracker.filtered("BEFORE_EVENT",before,result==null?Collections.<nc.vo.bd.material.plan.MaterialPlanVO>emptyList():Arrays.asList(result));return result;
        }
        public Map<String,String> excluded() { return tracker.excluded(); }
    }

    private static final class MaterialProd extends nc.impl.bd.material.prod.MaterialProdBatchUpdateForNewTransServiceImpl implements Trace {
        private final NativeBatchFailureTracker tracker=new NativeBatchFailureTracker();
        @Override protected List<nc.vo.bd.material.prod.MaterialProdVO> filterPermissionVOs(List<nc.vo.bd.material.prod.MaterialProdVO> rows,String[] orgs) throws BusinessException {
            Set<String> before=NativeBatchFailureTracker.snapshot(rows);
            List<nc.vo.bd.material.prod.MaterialProdVO> result=super.filterPermissionVOs(rows,orgs);
            tracker.filtered("PERMISSION",before,result);return result;
        }
        @Override protected List<nc.vo.bd.material.prod.MaterialProdVO> validateVOs(List<nc.vo.bd.material.prod.MaterialProdVO> rows) throws BusinessException {
            Set<String> before=NativeBatchFailureTracker.snapshot(rows);
            List<nc.vo.bd.material.prod.MaterialProdVO> result=super.validateVOs(rows);
            tracker.filtered("VALIDATION",before,result);return result;
        }
        @Override protected nc.vo.bd.material.prod.MaterialProdVO[] checkBeforeUpdate(List<nc.vo.bd.material.prod.MaterialProdVO> rows) throws BusinessException {
            Set<String> before=NativeBatchFailureTracker.snapshot(rows);
            nc.vo.bd.material.prod.MaterialProdVO[] result=super.checkBeforeUpdate(rows);
            tracker.filtered("BEFORE_EVENT",before,result==null?Collections.<nc.vo.bd.material.prod.MaterialProdVO>emptyList():Arrays.asList(result));return result;
        }
        public Map<String,String> excluded() { return tracker.excluded(); }
    }

    private static final class MaterialCost extends nc.impl.bd.material.cost.MaterialCostBatchUpdateForNewTransServiceImpl implements Trace {
        private final NativeBatchFailureTracker tracker=new NativeBatchFailureTracker();
        @Override protected List<nc.vo.bd.material.cost.MaterialCostVO> filterPermissionVOs(List<nc.vo.bd.material.cost.MaterialCostVO> rows,String[] orgs) throws BusinessException {
            Set<String> before=NativeBatchFailureTracker.snapshot(rows);
            List<nc.vo.bd.material.cost.MaterialCostVO> result=super.filterPermissionVOs(rows,orgs);
            tracker.filtered("PERMISSION",before,result);return result;
        }
        @Override protected List<nc.vo.bd.material.cost.MaterialCostVO> validateVOs(List<nc.vo.bd.material.cost.MaterialCostVO> rows) throws BusinessException {
            Set<String> before=NativeBatchFailureTracker.snapshot(rows);
            List<nc.vo.bd.material.cost.MaterialCostVO> result=super.validateVOs(rows);
            tracker.filtered("VALIDATION",before,result);return result;
        }
        @Override protected nc.vo.bd.material.cost.MaterialCostVO[] checkBeforeUpdate(List<nc.vo.bd.material.cost.MaterialCostVO> rows) throws BusinessException {
            Set<String> before=NativeBatchFailureTracker.snapshot(rows);
            nc.vo.bd.material.cost.MaterialCostVO[] result=super.checkBeforeUpdate(rows);
            tracker.filtered("BEFORE_EVENT",before,result==null?Collections.<nc.vo.bd.material.cost.MaterialCostVO>emptyList():Arrays.asList(result));return result;
        }
        public Map<String,String> excluded() { return tracker.excluded(); }
    }

    private static final class MaterialPfc extends nc.impl.bd.material.pfc.MaterialPfcBatchUpdateForNewTransServiceImpl implements Trace {
        private final NativeBatchFailureTracker tracker=new NativeBatchFailureTracker();
        @Override protected List<nc.vo.bd.material.pfc.MaterialPfcVO> filterPermissionVOs(List<nc.vo.bd.material.pfc.MaterialPfcVO> rows,String[] orgs) throws BusinessException {
            Set<String> before=NativeBatchFailureTracker.snapshot(rows);
            List<nc.vo.bd.material.pfc.MaterialPfcVO> result=super.filterPermissionVOs(rows,orgs);
            tracker.filtered("PERMISSION",before,result);return result;
        }
        @Override protected List<nc.vo.bd.material.pfc.MaterialPfcVO> validateVOs(List<nc.vo.bd.material.pfc.MaterialPfcVO> rows) throws BusinessException {
            Set<String> before=NativeBatchFailureTracker.snapshot(rows);
            List<nc.vo.bd.material.pfc.MaterialPfcVO> result=super.validateVOs(rows);
            tracker.filtered("VALIDATION",before,result);return result;
        }
        @Override protected nc.vo.bd.material.pfc.MaterialPfcVO[] checkBeforeUpdate(List<nc.vo.bd.material.pfc.MaterialPfcVO> rows) throws BusinessException {
            Set<String> before=NativeBatchFailureTracker.snapshot(rows);
            nc.vo.bd.material.pfc.MaterialPfcVO[] result=super.checkBeforeUpdate(rows);
            tracker.filtered("BEFORE_EVENT",before,result==null?Collections.<nc.vo.bd.material.pfc.MaterialPfcVO>emptyList():Arrays.asList(result));return result;
        }
        public Map<String,String> excluded() { return tracker.excluded(); }
    }

    private static final class MaterialPfcc extends nc.impl.bd.material.pfcc.MaterialProfitCostBatchUpdateForNewTransServiceImpl implements Trace {
        private final NativeBatchFailureTracker tracker=new NativeBatchFailureTracker();
        @Override protected List<nc.vo.bd.material.pfcc.MaterialPFCCVO> filterPermissionVOs(List<nc.vo.bd.material.pfcc.MaterialPFCCVO> rows,String[] orgs) throws BusinessException {
            Set<String> before=NativeBatchFailureTracker.snapshot(rows);
            List<nc.vo.bd.material.pfcc.MaterialPFCCVO> result=super.filterPermissionVOs(rows,orgs);
            tracker.filtered("PERMISSION",before,result);return result;
        }
        @Override protected List<nc.vo.bd.material.pfcc.MaterialPFCCVO> validateVOs(List<nc.vo.bd.material.pfcc.MaterialPFCCVO> rows) throws BusinessException {
            Set<String> before=NativeBatchFailureTracker.snapshot(rows);
            List<nc.vo.bd.material.pfcc.MaterialPFCCVO> result=super.validateVOs(rows);
            tracker.filtered("VALIDATION",before,result);return result;
        }
        @Override protected nc.vo.bd.material.pfcc.MaterialPFCCVO[] checkBeforeUpdate(List<nc.vo.bd.material.pfcc.MaterialPFCCVO> rows) throws BusinessException {
            Set<String> before=NativeBatchFailureTracker.snapshot(rows);
            nc.vo.bd.material.pfcc.MaterialPFCCVO[] result=super.checkBeforeUpdate(rows);
            tracker.filtered("BEFORE_EVENT",before,result==null?Collections.<nc.vo.bd.material.pfcc.MaterialPFCCVO>emptyList():Arrays.asList(result));return result;
        }
        public Map<String,String> excluded() { return tracker.excluded(); }
    }
}

