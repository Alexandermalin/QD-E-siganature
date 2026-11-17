package nc.ui.uapbd.qd.esign;
import java.awt.event.ActionEvent;
import nc.bs.bd.util.IEnableMsgConstant;
import nc.ui.bd.cust.baseinfo.action.CustBaseInfoDisableAction;
import nc.ui.uif2.ShowStatusBarMsgUtil;
import nc.ui.uif2.model.BillManageModel;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
public final class QdCustomerCardDisableAction extends CustBaseInfoDisableAction {
    private static final long serialVersionUID=1L;private PartnerOperationOrganization scope;private transient ActionEvent event;
    public void setOperationScope(PartnerOperationOrganization value) { scope=value; }
    @Override public void doAction(ActionEvent value) throws Exception {
        if(event!=null) throw new BusinessException("客户停用正在处理。");event=value;try { super.doAction(value); } finally { event=null; }
    }
    @Override public Object doDisable(Object value) throws Exception {
        if(!(value instanceof SuperVO)) throw new BusinessException("客户停用数据不正确。");
        Object saved=QdPartnerStateCoordinator.cardResult(new QdPartnerStateCoordinator(this,(BillManageModel)getModel(),scope,true,false,false,event,new SuperVO[]{(SuperVO)value}).execute());
        ShowStatusBarMsgUtil.showStatusBarMsg(IEnableMsgConstant.getDisableSuccessMsg(),getModel().getContext());return saved;
    }
}
