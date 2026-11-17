package nc.ui.uapbd.qd.esign;
import java.awt.event.ActionEvent;
import nc.ui.bd.stordoc.actions.StordocEnableAction;
import nc.ui.uif2.model.BillManageModel;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
public final class QdWarehouseCardEnableAction extends StordocEnableAction {
    private static final long serialVersionUID=1L;private PartnerOperationOrganization scope;private transient ActionEvent event;private transient boolean completed;
    public void setOperationScope(PartnerOperationOrganization value) { scope=value; }
    @Override public void doAction(ActionEvent value) throws Exception {
        if(event!=null) throw new BusinessException("仓库启用正在处理。");event=value;completed=false;try { super.doAction(value); } finally { event=null;completed=false; }
    }
    @Override public Object doEnable(Object value) throws Exception {
        if(!(value instanceof SuperVO)) throw new BusinessException("仓库启用数据不正确。");
        Object saved=QdPartnerStateCoordinator.cardResult(new QdPartnerStateCoordinator(this,(BillManageModel)getModel(),scope,false,false,true,true,false,event,new SuperVO[]{(SuperVO)value}).execute());
        completed=true;return saved;
    }
    @Override protected void showSuccessInfo() { if(completed) super.showSuccessInfo(); }
}
