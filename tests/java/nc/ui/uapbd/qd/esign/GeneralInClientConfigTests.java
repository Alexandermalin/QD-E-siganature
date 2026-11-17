package nc.ui.uapbd.qd.esign;

import java.util.Collections;
import nc.bs.uif2.validation.IValidationService;
import nc.ui.ic.general.action.GeneralSaveAction;
import nc.ui.ic.general.model.ICGenBizEditorModel;
import nc.ui.ic.general.model.ICGenBizModel;
import nc.ui.ic.general.view.ICBizView;
import nc.ui.ic.m4a.GeneralIn_Org;
import nc.ui.ic.m4a.handler.DefaultExceptionHanlerFor4A;
import nc.ui.ic.pub.env.ICUIContext;
import nc.ui.pubapp.uif2app.AppUiState;
import nc.ui.pubapp.uif2app.actions.pflow.AbstractScriptExcAction;
import nc.ui.uif2.AppEventListener;
import nc.ui.uif2.factory.UIF2BeanFactory;
import nc.vo.pub.BusinessException;
import nc.vo.scmpub.res.billtype.ICBillType;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uif2.LoginContext;

/** Checks native general-in action bindings and the controlled handoff with local doubles. */
public final class GeneralInClientConfigTests {
    public static void run() throws Exception {
        require(GeneralIn_Org.class.isAssignableFrom(QdGeneralInConfig.class),"General-in config must retain the native node");
        Probe config=new Probe();config.hookUIF2BeanFactory(new UIF2BeanFactory());
        GeneralSaveAction save=config.getSaveAction();
        require(save instanceof QdGeneralInSaveAction && save==config.getSaveAction()
            && save.getModel()==config.model && save.getEditorModel()==config.editor && save.getValidationService()==config.validation
            && save.getExceptionHandler()==config.exceptionHandler,
            "General-in save must retain native model and validation bindings");
        require(config.doGetBean("saveAction")==save && config.doGetBean("deleteAction")==config.getDeleteAction()
            && config.doGetBean("signAction")==config.getSignAction() && config.doGetBean("cancelSignAction")==config.getCancelSignAction(),
            "Native JavaBean lookup must return the controlled general-in actions");
        require(nc.ui.ic.m4a.action.GeneralInSignAction.class.isAssignableFrom(config.getSignAction().getClass()), "General-in sign must retain its specialized native action");
        require(config.getDeleteAction() instanceof QdGeneralInDeleteAction && config.getSignAction() instanceof QdGeneralInSignAction
            && config.getCancelSignAction() instanceof QdGeneralInCancelSignAction,"General-in state beans must preserve native action subclasses");
        require(config.nativeModelLookups == 2 && config.factoryModelLookups == 2,
                "Save/sign and delete/cancel-sign must retain their respective native model lookups");
        require(((AuthorizedRequestConsumer)save).isAvailable(context("Save","INSERT",null))
            && ((AuthorizedRequestConsumer)save).isAvailable(context("Save","UPDATE","A")),"General-in save route must be available");
        config.model.getContext().setNodeCode("40080608");config.model.setAppUiState(AppUiState.ADD);
        try {save.doAction(null);throw new AssertionError("Direct general-in save bypassed signing");}catch(BusinessException expected) { }
        try {save.procFlow(save.getFlowContext());throw new AssertionError("Direct general-in save PF bypassed signing");}catch(BusinessException expected) { }
        config.model.setAppUiState(AppUiState.NOT_EDIT);
        checkState(config.getDeleteAction(),"Delete","DELETE");
        checkState(config.getSignAction(),"Sign","SIGN");
        checkState(config.getCancelSignAction(),"CancelSign","CANCEL_SIGN");

        Probe failing=new Probe();failing.hookUIF2BeanFactory(new UIF2BeanFactory());
        int listeners=failing.model.getAppEventListener(AppEventListener.class).length;failing.failEditor=true;
        try {failing.getSaveAction();throw new AssertionError("General-in bean initialization failure ignored");}
        catch(IllegalStateException expected) { }
        require(failing.model.getAppEventListener(AppEventListener.class).length==listeners,"Failed general-in initialization leaked listeners");
        failing.failEditor=false;require(failing.getSaveAction() instanceof QdGeneralInSaveAction,"General-in initialization must be retryable");
        System.out.println("PASS GeneralInClientConfigTests (native general-in actions and handoff; no NC session)");
    }
    private static void checkState(AbstractScriptExcAction action,String button,String operation) throws Exception {
        require(((AuthorizedRequestConsumer)action).isAvailable(context(button,operation,"A")),"General-in state handoff unavailable");
        try {action.doAction(null);throw new AssertionError("Direct general-in state action bypassed signing");}catch(BusinessException expected) { }
        try {action.procFlow(action.getFlowContext());throw new AssertionError("Direct general-in state PF bypassed signing");}catch(BusinessException expected) { }
    }
    private static OperationContext context(String button,String operation,String pk) {
        return new OperationContext(null,null,"G","O","40080608","其他入库",button,button,operation,null,
            Collections.singletonList(new BusinessObjectRef("item","IC_GENERAL_IN",pk,null,null)));
    }
    private static void require(boolean valid,String message){if(!valid)throw new AssertionError(message);}
    private static final class Probe extends QdGeneralInConfig {
        final ICGenBizModel model=new ICGenBizModel();final ICBizView card=new ICBizView();
        final DefaultExceptionHanlerFor4A exceptionHandler=new DefaultExceptionHanlerFor4A();
        int nativeModelLookups, factoryModelLookups;
        @Override public ICGenBizModel getIcBizModel(){nativeModelLookups++;return model;}
        @Override public DefaultExceptionHanlerFor4A getMyExceptionHandler(){return exceptionHandler;}
        final ICGenBizEditorModel editor=new ICGenBizEditorModel(card,null){public ICBillType getICBillType(){return ICBillType.GeneralIn;}};
        final IValidationService validation=new IValidationService(){public void validate(Object value){}};
        boolean failEditor;
        Probe(){model.setIcUIContext(new ICUIContext(new LoginContext()));}
        protected Object findBeanInUIF2BeanFactory(String name) {
            if("icBizModel".equals(name)){factoryModelLookups++;return model;}
            if("icBizEditorModel".equals(name)){if(failEditor)throw new IllegalStateException("fixture failure");return editor;}
            if("validateService".equals(name))return validation;
            if("card".equals(name))return card;
            if("list".equals(name))return null;
            throw new AssertionError("Unexpected bean "+name);
        }
    }
}
