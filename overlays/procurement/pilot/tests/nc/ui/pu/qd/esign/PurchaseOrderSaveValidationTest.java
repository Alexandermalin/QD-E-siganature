package nc.ui.pu.qd.esign;

import java.awt.event.ActionEvent;
import javax.swing.Action;
import nc.ui.pubapp.uif2app.model.BillManageModel;
import nc.ui.uif2.NCAction;
import nc.ui.uif2.actions.ActionInterceptor;
import nc.vo.uif2.LoginContext;

/** A native veto at the button boundary must run before the save tries to establish a signing scope. */
public final class PurchaseOrderSaveValidationTest {
    public static void main(String[] args) {
        LoginContext login = new LoginContext();
        login.setNodeCode("40040400");
        BillManageModel model = new BillManageModel();
        model.setContext(login);
        QdPurchaseOrderSaveAction save = new QdPurchaseOrderSaveAction();
        save.setModel(model);

        final boolean[] nativeValidationCalled = {false};
        save.setInterceptor(new ActionInterceptor() {
            @Override public boolean beforeDoAction(Action action, ActionEvent event) {
                if (((NCAction) action).getInterceptor() != this) {
                    throw new AssertionError("the save button installed an early signing interceptor");
                }
                nativeValidationCalled[0] = true;
                return false;
            }

            @Override public void afterDoActionSuccessed(Action action, ActionEvent event) {
                throw new AssertionError("native veto reached successful save");
            }

            @Override public boolean afterDoActionFailed(Action action, ActionEvent event, Throwable failure) {
                throw new AssertionError("native veto should terminate before PF dispatch", failure);
            }
        });

        // There is no bill/editor to establish a signature scope if the native button vetoes the action.
        save.actionPerformed(new ActionEvent(save, ActionEvent.ACTION_PERFORMED, "Save"));
        if (!nativeValidationCalled[0]) throw new AssertionError("signing ran before the native save interceptor");
    }
}
