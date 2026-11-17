package nc.ui.uapbd.qd.esign;

import java.awt.event.ActionEvent;
import nc.ui.pubapp.uif2app.actions.interceptor.CompositeActionInterceptor;
import nc.ui.uif2.NCAction;

/** Private lifecycle anchor for native actions whose interceptor setter appends rather than replaces. */
final class QdExplicitSignatureAction extends NCAction {
    private static final long serialVersionUID=1L;
    SignatureLifecycleInterceptor install(ElectronicSignatureActionInterceptor signing) {
        if(getInterceptor()!=null) throw new IllegalStateException("Explicit signature action is already installed");
        CompositeActionInterceptor chain=new CompositeActionInterceptor();chain.addInterceptor(signing);setInterceptor(chain);
        return SignatureLifecycleInstaller.install(this,signing);
    }
    @Override public void doAction(ActionEvent event) { throw new IllegalStateException("Signature lifecycle anchor is not a business action"); }
}
