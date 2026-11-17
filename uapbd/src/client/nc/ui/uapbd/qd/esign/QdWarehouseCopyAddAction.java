package nc.ui.uapbd.qd.esign;

import java.awt.event.ActionEvent;
import nc.ui.bd.pub.actions.BDCopyAddAction;
import nc.ui.bd.stordoc.view.StordocEditor;
import nc.ui.uif2.AppEvent;
import nc.ui.uif2.UIState;
import nc.vo.bd.stordoc.AgentstoreVO;
import nc.vo.bd.stordoc.StordocVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.pub.lang.UFBoolean;

/** Opens a warehouse copy as a local draft. The server copies its address during save. */
public final class QdWarehouseCopyAddAction extends BDCopyAddAction {
    private static final long serialVersionUID=1L;
    private final QdWarehouseModelService service;

    public QdWarehouseCopyAddAction(QdWarehouseModelService service) { this.service=service; }

    @Override public void doAction(ActionEvent event) throws Exception {
        Object selected=getModel().getSelectedData();
        if(!(selected instanceof StordocVO) || !(getEditor() instanceof StordocEditor))
            throw new BusinessException("无法确定待复制的仓库。");
        StordocVO source=(StordocVO)selected;
        StordocEditor editor=(StordocEditor)getEditor();
        service.clearCopySource();
        editor.setAddWithData(true);
        try {
            super.doAction(event);
            editor.afterSetValue();
            service.setCopySource(source.getPk_stordoc(),source.getStoraddr());
        } finally { editor.setAddWithData(false); }
    }

    @Override protected void setDefaultValue(SuperVO row) {
        StordocVO warehouse=(StordocVO)row;
        String org=warehouse.getPk_org();
        super.setDefaultValue(warehouse);
        warehouse.setPk_org(org);
        warehouse.setIsdirectstore(UFBoolean.FALSE);
        AgentstoreVO[] original=warehouse.getAgentstores();
        if(original==null) return;
        AgentstoreVO[] children=new AgentstoreVO[original.length];
        for(int index=0;index<original.length;index++) {
            AgentstoreVO child=(AgentstoreVO)original[index].clone();
            child.setPk_agentstore(null);
            child.setPk_stordoc(null);
            children[index]=child;
        }
        warehouse.setAgentstores(children);
    }

    @Override public void handleEvent(AppEvent event) {
        if("UiState_Changed".equals(event.getType()) && getModel().getUiState()!=UIState.ADD) service.clearCopySource();
        super.handleEvent(event);
    }
}
