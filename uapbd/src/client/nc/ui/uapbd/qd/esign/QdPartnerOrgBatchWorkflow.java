package nc.ui.uapbd.qd.esign;

import java.util.List;
import nc.vo.bd.errorlog.ErrLogReturnValue;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.uapbd.qd.esign.OperationContext;

/** Candidate orchestration; the public fast-batch entry remains guarded. */
final class QdPartnerOrgBatchWorkflow {
    private QdPartnerOrgBatchWorkflow() { }
    interface Hooks extends QdPartnerOrgBatchProgress.Operation,QdPartnerOrgBatchCombine.LogWriter,QdPartnerBatchChunks.NativeLogReader {
        boolean approve(int roots,long pages,int slices) throws BusinessException;
        void requireCurrent() throws BusinessException;
    }
    static ErrLogReturnValue execute(OperationContext context,QdPartnerOrgBatchPlan.Reader reader,Hooks hooks) throws BusinessException {
        if(context==null || context.getPartnerBatchDraft()==null)throw new BusinessException("组织批改缺少页签选择。");
        return executeForTest(context,reader,hooks,pageType(context.getNodeCode(),context.getPartnerBatchDraft().getTabCode()));
    }
    /** Package-local seam permits offline result VOs without NC metadata. */
    static ErrLogReturnValue executeForTest(OperationContext context,QdPartnerOrgBatchPlan.Reader reader,Hooks hooks,
            Class<? extends SuperVO> type) throws BusinessException {
        if(context==null || reader==null || hooks==null || type==null)throw new BusinessException("组织批改缺少计划、授权或原生页签类型。");
        hooks.requireCurrent();
        QdPartnerOrgBatchSlices.Prepared prepared=QdPartnerOrgBatchSlices.prepare(context,reader);long pages=0;
        for(QdPartnerOrgBatchSlices.Slice slice:prepared.slices()) {
            pages+=slice.draft().getSelectedPages().length;
            if(pages>Integer.MAX_VALUE)throw new BusinessException("组织批改页签数量超出原生汇总界面上限，请缩小选择。");
        }
        if(Thread.currentThread().isInterrupted())throw new BusinessException("组织批改已中断，尚未执行任何批次。");
        if(!hooks.approve(context.getObjects().size(),pages,prepared.slices().size()))
            throw new BusinessException("组织批改已取消，尚未执行任何批次。");
        List<QdPartnerOrgBatchProgress.Completed> completed=QdPartnerOrgBatchProgress.execute(prepared,hooks);
        try { hooks.requireCurrent(); }
        catch(BusinessException error) { throw QdPartnerOrgBatchCombine.mergeFailure(completed,error); }
        catch(RuntimeException error) { throw QdPartnerOrgBatchCombine.mergeFailure(completed,error); }
        ErrLogReturnValue result=QdPartnerOrgBatchCombine.combine(completed,type,hooks,hooks);
        try { hooks.requireCurrent(); }
        catch(BusinessException error) { throw QdPartnerOrgBatchCombine.mergeFailure(completed,error); }
        catch(RuntimeException error) { throw QdPartnerOrgBatchCombine.mergeFailure(completed,error); }
        return result;
    }
    /** Fixed native VO classes corresponding to the 14 server-side page routes. */
    static Class<? extends SuperVO> pageType(String node,String tab) throws BusinessException {
        boolean customer="10140CUG".equals(node) || "10140CUO".equals(node);
        boolean supplier="10140SUG".equals(node) || "10140SUO".equals(node);
        boolean material="10140MAG".equals(node) || "10140MAO".equals(node);
        if(customer) {
            if("finance_info".equals(tab))return nc.vo.bd.cust.finance.CustFinanceVO.class;
            if("sale_info".equals(tab))return nc.vo.bd.cust.saleinfo.CustsaleVO.class;
            if("credit_info".equals(tab))return nc.vo.bd.cust.creditctl.CustCreditCtlVO.class;
        } else if(supplier) {
            if("finance_info".equals(tab))return nc.vo.bd.supplier.finance.SupFinanceVO.class;
            if("stock_info".equals(tab))return nc.vo.bd.supplier.stock.SupStockVO.class;
        } else if(material) {
            if("fi_info".equals(tab))return nc.vo.bd.material.fi.MaterialFiVO.class;
            if("pu_info".equals(tab))return nc.vo.bd.material.pu.MaterialPuVO.class;
            if("sale_info".equals(tab))return nc.vo.bd.material.sale.MaterialSaleVO.class;
            if("stock_info".equals(tab))return nc.vo.bd.material.stock.MaterialStockVO.class;
            if("plan_info".equals(tab))return nc.vo.bd.material.plan.MaterialPlanVO.class;
            if("prod_info".equals(tab))return nc.vo.bd.material.prod.MaterialProdVO.class;
            if("cost_info".equals(tab))return nc.vo.bd.material.cost.MaterialCostVO.class;
            if("pfc_info".equals(tab))return nc.vo.bd.material.pfc.MaterialPfcVO.class;
            if("pfcc_info".equals(tab))return nc.vo.bd.material.pfcc.MaterialPFCCVO.class;
        }
        throw new BusinessException("组织批改页签尚未核实。");
    }
}
