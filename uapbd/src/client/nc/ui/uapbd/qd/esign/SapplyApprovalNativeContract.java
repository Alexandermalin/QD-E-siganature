package nc.ui.uapbd.qd.esign;

import nc.ui.ic.general.action.GeneralApproveAction;
import nc.ui.ic.general.action.GeneralUnApproveAction;
import nc.ui.ic.m4455.action.SapplyBillApproveAction;
import nc.ui.ic.m4455.action.SapplyBillUnApproveAction;
import nc.ui.pubapp.uif2app.actions.pflow.ApproveScriptAction;
import nc.ui.pubapp.uif2app.actions.pflow.UNApproveScriptAction;
import nc.ui.uif2.NCAction;
import nc.vo.pub.BusinessException;

/** Pins the native approval callbacks and flow implementation used by the candidate bridge. */
final class SapplyApprovalNativeContract {
    private SapplyApprovalNativeContract() { }
    static void verify(Class<?> nativeAction) throws BusinessException {
        try {
            InventoryPfPreparation.verifyNativeContract();
            InventoryPfPreparation.verifyClass(NCAction.class,"9B7186A9D0640EBA94F0DE8C2C9B9E1DD94F291E79333D4D9CF317419F708A0F");
            InventoryPfPreparation.verifyClass(nc.ui.pf.clientutils.PfUtilClientAssistor.class,
                    "3EE547982D086619974E82E0F322B0816D742CF5D96240ABC2D5C814D17F6190");
            if(nativeAction==SapplyBillApproveAction.class) {
                InventoryPfPreparation.verifyClass(SapplyBillApproveAction.class,"1A1760D71ED972ED70B4348815D9D046A9184339A8EBDFD90A2FAACA9EFD0534");
                InventoryPfPreparation.verifyClass(GeneralApproveAction.class,"AA65B1EF10063B608B8808F57BF8EC5B5B2C539D69A506F9939C81CC1390610B");
                InventoryPfPreparation.verifyClass(ApproveScriptAction.class,"9169C573E9A1A5126F5C766B47553E587E4A3C05BDD5D012E9A1C33BF906CA48");
            } else if(nativeAction==SapplyBillUnApproveAction.class) {
                InventoryPfPreparation.verifyClass(SapplyBillUnApproveAction.class,"6CD74DF102A011F013672ADB90496FCF702BF9FDE8716212D64E7AB3C4CBFB8C");
                InventoryPfPreparation.verifyClass(GeneralUnApproveAction.class,"A9CD5CDFB21E5D3D9F4A6953B32A267FA9DA1B673D7BF5A2A8658D2F387F7C5C");
                InventoryPfPreparation.verifyClass(UNApproveScriptAction.class,"38B432CF35027290B2ABBBB4E2E5A521A55C292E6B502CAD845BA3F610EABBEB");
            } else throw new IllegalStateException("Unknown outbound-request approval action");
        } catch(Exception failure){throw new BusinessException("当前 NC 出库申请审批客户端版本尚未核对。",failure);}
    }
}
