package nc.ui.uapbd.qd.esign;
import java.awt.event.ActionEvent;
import nc.bs.bd.util.IEnableMsgConstant;
import nc.ui.bd.cust.baseinfo.action.CustBaseInfoEnableAction;
import nc.ui.uif2.ShowStatusBarMsgUtil;
import nc.ui.uif2.model.BillManageModel;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
public final class QdCustomerCardEnableAction extends CustBaseInfoEnableAction {
    private static final long serialVersionUID=1L;private PartnerOperationOrganization scope;private transient ActionEvent event;
    public void setOperationScope(PartnerOperationOrganization value) { scope=value; }
    @Override public void doAction(ActionEvent value) throws Exception {
        if(event!=null) throw new BusinessException("客户启用正在处理。");event=value;try { super.doAction(value); } finally { event=null; }
    }
    @Override public Object doEnable(Object value) throws Exception {
        if(!(value instanceof SuperVO)) throw new BusinessException("客户启用数据不正确。");
        Object saved=QdPartnerStateCoordinator.cardResult(new QdPartnerStateCoordinator(this,(BillManageModel)getModel(),scope,true,true,false,event,new SuperVO[]{(SuperVO)value}).execute());
        ShowStatusBarMsgUtil.showStatusBarMsg(IEnableMsgConstant.getEnableSuccessMsg(),getModel().getContext());return saved;
    }
}
