package nc.ui.uapbd.qd.esign;

import java.awt.event.ActionEvent;
import nc.ui.ic.m4455.action.RowCloseAction;
import nc.ui.pubapp.uif2app.model.BillManageModel;
import nc.ui.uif2.NCAction;

/** Candidate row-close action retaining NC's original selection and presentation behavior. */
public final class QdSapplyRowCloseAction extends RowCloseAction {
    private static final long serialVersionUID=1L;
    private final SapplyLineCoordinator coordinator=new SapplyLineCoordinator(new SapplyLineCoordinator.Host(){
        public NCAction action(){return QdSapplyRowCloseAction.this;}
        public BillManageModel model(){return getEditorModel()==null?null:getEditorModel().getICBizModel();}
        public Class<?> nativeClass(){return RowCloseAction.class;}
        public void performNative(ActionEvent event){QdSapplyRowCloseAction.super.actionPerformed(event);}
        public void runNative(ActionEvent event) throws Exception {QdSapplyRowCloseAction.super.doAction(event);}
    },"LineClose","行关闭");
    @Override public void actionPerformed(ActionEvent event){coordinator.perform(event);}
    @Override public void doAction(ActionEvent event) throws Exception {coordinator.doAction(event);}
}
