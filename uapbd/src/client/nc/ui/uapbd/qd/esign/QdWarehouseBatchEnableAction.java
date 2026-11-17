package nc.ui.uapbd.qd.esign;
import java.awt.event.ActionEvent;
import nc.bs.bd.service.ValueObjWithErrLog;
import nc.ui.bd.stordoc.actions.StordocBatchEnableAction;
import nc.ui.uif2.ShowStatusBarMsgUtil;
import nc.ui.uif2.model.BillManageModel;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.uapbd.qd.esign.ArchiveBatchResult;
public final class QdWarehouseBatchEnableAction extends StordocBatchEnableAction {
    private static final long serialVersionUID=1L;private PartnerOperationOrganization scope;private transient ActionEvent event;
    private transient boolean completed,partial,shown;private transient String request;
    public void setOperationScope(PartnerOperationOrganization value) { scope=value; }
    @Override public void doAction(ActionEvent value) throws Exception {
        if(event!=null) throw new BusinessException("仓库启用正在处理。");event=value;completed=false;partial=false;shown=false;request=null;
        try { super.doAction(value); } finally { event=null;completed=false;partial=false;shown=false;request=null; }
    }
    @Override public ValueObjWithErrLog doEnable(SuperVO[] values) throws Exception {
        QdPartnerStateCoordinator coordinator=new QdPartnerStateCoordinator(this,(BillManageModel)getModel(),scope,false,false,true,true,true,event,values);
        ArchiveBatchResult result=coordinator.execute();completed=true;request=coordinator.getRequestId();
        Object[] selected=getSelectedOperaDatas();partial=selected==null || selected.length!=values.length;
        for(ArchiveBatchResult.Unit unit:result.getUnits()) if("FAILED".equals(unit.getState())) partial=true;
        return result.getNativeResult();
    }
    @Override protected void showSuccessInfo() {
        if(!completed || shown) return;shown=true;
        if(partial) ShowStatusBarMsgUtil.showStatusBarMsg("部分仓库未启用，请查看明细。请求号："+request,getModel().getContext());
        else super.showSuccessInfo();
    }
}
