package nc.ui.uapbd.qd.esign;

import java.awt.event.ActionEvent;
import nc.ui.bd.supplier.baseinfo.action.SupBaseInfoFreezeAction;
import nc.vo.pub.BusinessException;

public final class QdSupplierFreezeAction extends SupBaseInfoFreezeAction {
    private static final long serialVersionUID=1L;
    private PartnerOperationOrganization operationScope;private transient boolean running;
    public void setOperationScope(PartnerOperationOrganization value) { operationScope=value; }
    @Override public void doAction(ActionEvent event) throws Exception {
        if(running) throw new BusinessException("供应商冻结正在处理。");
        running=true;try { QdSupplierFreezeCoordinator.run(this,getModel(),operationScope,true,event); } finally { running=false; }
    }
}
