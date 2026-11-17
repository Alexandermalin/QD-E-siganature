package nc.ui.uapbd.qd.esign;
import java.awt.event.ActionEvent;
import nc.bs.bd.util.IEnableMsgConstant;
import nc.ui.bd.supplier.baseinfo.action.SupBaseInfoEnableAction;
import nc.ui.uif2.ShowStatusBarMsgUtil;
import nc.ui.uif2.model.BillManageModel;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
public final class QdSupplierCardEnableAction extends SupBaseInfoEnableAction {
    private static final long serialVersionUID=1L;private PartnerOperationOrganization scope;private transient ActionEvent event;private transient boolean completed;
    public void setOperationScope(PartnerOperationOrganization value) { scope=value; }
    @Override public void doAction(ActionEvent value) throws Exception {
        if(event!=null) throw new BusinessException("供应商启用正在处理。");event=value;completed=false;
        try { checkPermission();validate();doEnable();if(completed) ShowStatusBarMsgUtil.showStatusBarMsg(IEnableMsgConstant.getEnableSuccessMsg(),getModel().getContext()); }
        finally { event=null;completed=false; }
    }
    @Override public Object doEnable(Object value) throws Exception {
        if(!(value instanceof SuperVO)) throw new BusinessException("供应商启用数据不正确。");
        Object saved=QdPartnerStateCoordinator.cardResult(new QdPartnerStateCoordinator(this,(BillManageModel)getModel(),scope,false,true,false,event,new SuperVO[]{(SuperVO)value}).execute());
        completed=true;return saved;
    }
}
