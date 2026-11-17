package nc.ui.uapbd.qd.esign;

import java.awt.event.ActionEvent;
import nc.bs.bd.service.ValueObjWithErrLog;
import nc.ui.bd.cust.baseinfo.action.CustBaseInfoUnFreezeAction;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;

public final class QdCustomerCardUnfreezeAction extends CustBaseInfoUnFreezeAction {
    private static final long serialVersionUID=1L;
    private PartnerOperationOrganization operationScope;
    private transient ActionEvent event;
    public void setOperationScope(PartnerOperationOrganization value) { operationScope=value; }
    @Override public void doAction(ActionEvent value) throws Exception {
        if(event!=null) throw new BusinessException("客户解冻正在处理。");
        event=value;try { super.doAction(value); } finally { event=null; }
    }
    @Override protected ValueObjWithErrLog doOperate(SuperVO value) throws BusinessException {
        return new QdCustomerFreezeCoordinator(this,getModel(),operationScope,false,false,event,new SuperVO[]{value}).execute().getNativeResult();
    }
}
