package nc.ui.uapbd.qd.esign;

import java.awt.event.ActionEvent;
import nc.ui.ic.m4455.action.RowOpenAction;
import nc.ui.pubapp.uif2app.model.BillManageModel;
import nc.ui.uif2.NCAction;

/** Candidate row-open action retaining NC's original selection and presentation behavior. */
public final class QdSapplyRowOpenAction extends RowOpenAction {
    private static final long serialVersionUID=1L;
    private final SapplyLineCoordinator coordinator=new SapplyLineCoordinator(new SapplyLineCoordinator.Host(){
        public NCAction action(){return QdSapplyRowOpenAction.this;}
        public BillManageModel model(){return getEditorModel()==null?null:getEditorModel().getICBizModel();}
        public Class<?> nativeClass(){return RowOpenAction.class;}
        public void performNative(ActionEvent event){QdSapplyRowOpenAction.super.actionPerformed(event);}
        public void runNative(ActionEvent event) throws Exception {QdSapplyRowOpenAction.super.doAction(event);}
    },"LineOpen","行打开");
    @Override public void actionPerformed(ActionEvent event){coordinator.perform(event);}
    @Override public void doAction(ActionEvent event) throws Exception {coordinator.doAction(event);}
}
