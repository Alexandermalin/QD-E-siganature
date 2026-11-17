package nc.ui.uapbd.qd.esign;

import java.awt.event.ActionEvent;
import java.util.Objects;
import nc.ui.bd.pub.actions.CodeTreeSaveAction;
import nc.ui.uif2.UIState;
import nc.ui.uif2.model.HierachicalDataAppModel;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;

/** Preserve the native refresh after a code change in a code-based tree. */
public final class QdArchiveTreeCodeSaveAction extends CodeTreeSaveAction {
    private static final long serialVersionUID=1L;private transient boolean running;
    @Override public void doAction(ActionEvent event) throws Exception {
        if(running) throw new BusinessException("树形档案保存正在处理。");
        if(!(getModel() instanceof HierachicalDataAppModel) || getModel().getUiState()!=UIState.EDIT && getModel().getUiState()!=UIState.ADD)
            throw new BusinessException("树形档案保存状态不正确。");
        running=true;
        try {
            Object input=getEditor().getValue();validate(input);
            HierachicalDataAppModel model=(HierachicalDataAppModel)getModel();Object old=model.getSelectedData();boolean add=model.getUiState()==UIState.ADD;
            if(!add && (!(old instanceof SuperVO) || input==null || old.getClass()!=input.getClass()))
                throw new BusinessException("无法确定待修改的树形档案。");
            SuperVO saved=new QdArchiveTreeSaveCoordinator(this,model,event,input).execute();
            if(add) { model.directlyAdd(saved);model.setSelectedData(saved); }
            else if(!Objects.equals(QdArchiveTreeSaveCoordinator.field((SuperVO)old,"code"),
                    QdArchiveTreeSaveCoordinator.field(saved,"code"))
                    || !Objects.equals(QdArchiveTreeSaveCoordinator.field((SuperVO)old,"parent"),
                        QdArchiveTreeSaveCoordinator.field(saved,"parent"))) {
                if(getRefreshAction()==null) throw new BusinessException("档案已保存，但无法刷新树，请重新查询。");
                getRefreshAction().doAction(event);model.setSelectedData(saved);
            } else model.directlyUpdate(saved);
            model.setUiState(UIState.NOT_EDIT);showSuccessInfo();
        } finally { running=false; }
    }
}
