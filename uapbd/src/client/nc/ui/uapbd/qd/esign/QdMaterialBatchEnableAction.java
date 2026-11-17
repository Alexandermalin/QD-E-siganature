package nc.ui.uapbd.qd.esign;
import java.awt.event.ActionEvent;
import nc.bs.bd.service.ValueObjWithErrLog;
import nc.ui.bd.material.baseinfo.action.MaterialBatchEnableAction;
import nc.ui.uif2.model.BillManageModel;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
public final class QdMaterialBatchEnableAction extends MaterialBatchEnableAction {
    private static final long serialVersionUID=1L;private PartnerOperationOrganization scope;private transient ActionEvent event;
    public void setOperationScope(PartnerOperationOrganization value) { scope=value; }
    @Override public void doAction(ActionEvent value) throws Exception {
        if(event!=null) throw new BusinessException("物料启用正在处理。");event=value;try { super.doAction(value); } finally { event=null; }
    }
    @Override public ValueObjWithErrLog doEnable(SuperVO[] values) throws Exception {
        return new QdPartnerStateCoordinator(this,(BillManageModel)getModel(),scope,false,true,true,true,event,values).execute().getNativeResult();
    }
}
