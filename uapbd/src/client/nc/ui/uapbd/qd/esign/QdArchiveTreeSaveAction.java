package nc.ui.uapbd.qd.esign;

import java.awt.event.ActionEvent;
import nc.ui.uif2.UIState;
import nc.ui.uif2.actions.SaveAction;
import nc.ui.uif2.model.HierachicalDataAppModel;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;

/** Candidate customer/supplier classification save. */
public final class QdArchiveTreeSaveAction extends SaveAction {
    private static final long serialVersionUID=1L;private transient ActionEvent active;
    @Override public void doAction(ActionEvent event) throws Exception {
        if(active!=null) throw new BusinessException("分类保存正在处理。");
        if(getModel().getUiState()!=UIState.EDIT && getModel().getUiState()!=UIState.ADD) throw new BusinessException("分类保存状态不正确。");
        active=event;try { super.doAction(event); } finally { active=null; }
    }
    @Override protected void doEditSave(Object value) throws Exception {
        if(active==null || !(getModel() instanceof HierachicalDataAppModel)) throw new BusinessException("分类保存上下文无效。");
        HierachicalDataAppModel model=(HierachicalDataAppModel)getModel();
        SuperVO saved=new QdArchiveTreeSaveCoordinator(this,model,active,value).execute();
        model.directlyUpdate(saved);model.setUiState(UIState.NOT_EDIT);model.setOtherUiState(UIState.NOT_EDIT);
    }
    @Override protected void doAddSave(Object value) throws Exception {
        if(active==null || !(getModel() instanceof HierachicalDataAppModel)) throw new BusinessException("分类新增上下文无效。");
        HierachicalDataAppModel model=(HierachicalDataAppModel)getModel();
        SuperVO saved=new QdArchiveTreeSaveCoordinator(this,model,active,value).execute();
        model.directlyAdd(saved);model.setUiState(UIState.NOT_EDIT);model.setOtherUiState(UIState.NOT_EDIT);model.setSelectedData(saved);
    }
}
