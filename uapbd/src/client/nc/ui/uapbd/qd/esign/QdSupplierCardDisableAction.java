package nc.ui.uapbd.qd.esign;
import java.awt.event.ActionEvent;
import nc.bs.bd.util.IEnableMsgConstant;
import nc.ui.bd.supplier.baseinfo.action.SupBaseInfoDisableAction;
import nc.ui.uif2.ShowStatusBarMsgUtil;
import nc.ui.uif2.model.BillManageModel;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
public final class QdSupplierCardDisableAction extends SupBaseInfoDisableAction {
    private static final long serialVersionUID=1L;private PartnerOperationOrganization scope;private transient ActionEvent event;private transient boolean completed;
    public void setOperationScope(PartnerOperationOrganization value) { scope=value; }
    @Override public void doAction(ActionEvent value) throws Exception {
        if(event!=null) throw new BusinessException("供应商停用正在处理。");event=value;completed=false;
        try { checkPermission();validate();doDisable();if(completed) ShowStatusBarMsgUtil.showStatusBarMsg(IEnableMsgConstant.getDisableSuccessMsg(),getModel().getContext()); }
        finally { event=null;completed=false; }
    }
    @Override public Object doDisable(Object value) throws Exception {
        if(!(value instanceof SuperVO)) throw new BusinessException("供应商停用数据不正确。");
        Object saved=QdPartnerStateCoordinator.cardResult(new QdPartnerStateCoordinator(this,(BillManageModel)getModel(),scope,false,false,false,event,new SuperVO[]{(SuperVO)value}).execute());
        completed=true;return saved;
    }
}
