package nc.ui.uapbd.qd.esign;

import java.awt.event.ActionEvent;
import nc.ui.uif2.UIState;
import nc.ui.uif2.actions.SaveAddAction;
import nc.ui.uif2.model.HierachicalDataAppModel;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;

/** Candidate save-and-add retains NC's validate, save, message and next-add order. */
public final class QdArchiveTreeSaveAddAction extends SaveAddAction {
    private static final long serialVersionUID=1L;private transient boolean running;
    @Override public void doAction(ActionEvent event) throws Exception {
        if(running) throw new BusinessException("基础档案保存正在处理。");
        if(!(getModel() instanceof HierachicalDataAppModel) || getModel().getUiState()!=UIState.ADD || getAddAction()==null)
            throw new BusinessException("基础档案保存新增状态不正确。");
        running=true;
        try {
            Object value=getEditor().getValue();validate(value);HierachicalDataAppModel model=(HierachicalDataAppModel)getModel();
            SuperVO saved=new QdArchiveTreeSaveCoordinator(this,model,event,value).execute();
            model.directlyAdd(saved);showSuccessInfo();getAddAction().doAction(event);
        } finally { running=false; }
    }
}
