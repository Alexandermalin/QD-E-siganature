package nc.ui.uapbd.qd.esign;

import nc.ui.uif2.model.BillManageModel;
import nc.vo.bd.config.BDModeSelectedVO;
import nc.vo.bd.pub.NODE_TYPE;
import nc.vo.pub.BusinessException;
import nc.vo.util.BDModeManager;

/** Retains the native material card's manage-mode check before replacing its service call. */
final class QdMaterialStateClientRules {
    private QdMaterialStateClientRules() { }
    static void requireManageMode(BillManageModel model) throws BusinessException {
        if(model==null || model.getContext()==null || model.getContext().getNodeType()==null)
            throw new BusinessException("无法确定物料操作节点类型。");
        if(!NODE_TYPE.ORG_NODE.equals(model.getContext().getNodeType())) return;
        BDModeSelectedVO mode=BDModeManager.getInstance().getBDModeSelectedVOByMDClassID("c7dc0ccd-8872-4eee-8882-160e8f49dfad");
        if(mode==null || mode.getManagemode()==null) throw new BusinessException("无法确定物料的原生管理模式。");
        int value=mode.getManagemode().intValue();
        if(value==1 || value==2 || value==5) throw new BusinessException("当前物料管理模式不允许在业务单元启用或停用。");
    }
}
