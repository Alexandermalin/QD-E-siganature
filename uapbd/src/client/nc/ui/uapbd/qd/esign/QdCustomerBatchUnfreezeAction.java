package nc.ui.uapbd.qd.esign;

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import nc.bs.bd.service.ErrLogElement;
import nc.bs.bd.service.ValueObjWithErrLog;
import nc.ui.bd.cust.baseinfo.action.CustBatchUnFreezeAction;
import nc.ui.uif2.ShowStatusBarMsgUtil;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.ArchiveBatchResult;

public final class QdCustomerBatchUnfreezeAction extends CustBatchUnFreezeAction {
    private static final long serialVersionUID=1L;
    private PartnerOperationOrganization operationScope;
    private transient ActionEvent event;
    private transient QdCustomerFreezeCoordinator coordinator;
    private transient boolean partial;
    public void setOperationScope(PartnerOperationOrganization value) { operationScope=value; }
    @Override public void doAction(ActionEvent value) throws Exception {
        if(event!=null) throw new BusinessException("客户解冻正在处理。");
        event=value;partial=false;try { super.doAction(value); } finally { event=null;coordinator=null;partial=false; }
    }
    @Override protected Object doOperate(ValueObjWithErrLog input) throws BusinessException {
        coordinator=new QdCustomerFreezeCoordinator(this,getModel(),operationScope,false,true,event,input.getVos());
        ArchiveBatchResult result=coordinator.execute();
        ArrayList<ErrLogElement> errors=new ArrayList<ErrLogElement>();
        if(input.getErrLogList()!=null) errors.addAll(input.getErrLogList());
        if(result.getNativeResult().getErrLogList()!=null) errors.addAll(result.getNativeResult().getErrLogList());
        partial=!errors.isEmpty();
        if(!errors.isEmpty()) showErrorLog(errors);
        return result.getNativeResult().getVos();
    }
    @Override protected void showSuccessInfo() {
        if(coordinator==null || (!partial && "SUCCESS".equals(coordinator.getState()))) super.showSuccessInfo();
        else ShowStatusBarMsgUtil.showStatusBarMsg("客户解冻未全部成功，请查看失败明细。请求号："+coordinator.getRequestId(),getModel().getContext());
    }
}
