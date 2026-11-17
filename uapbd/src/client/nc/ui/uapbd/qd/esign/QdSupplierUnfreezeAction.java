package nc.ui.uapbd.qd.esign;

import java.awt.event.ActionEvent;
import nc.ui.bd.supplier.baseinfo.action.SupBaseInfoUnFreezeAction;
import nc.vo.pub.BusinessException;

public final class QdSupplierUnfreezeAction extends SupBaseInfoUnFreezeAction {
    private static final long serialVersionUID=1L;
    private PartnerOperationOrganization operationScope;private transient boolean running;
    public void setOperationScope(PartnerOperationOrganization value) { operationScope=value; }
    @Override public void doAction(ActionEvent event) throws Exception {
        if(running) throw new BusinessException("供应商解冻正在处理。");
        running=true;try { QdSupplierFreezeCoordinator.run(this,getModel(),operationScope,false,event); } finally { running=false; }
    }
}
