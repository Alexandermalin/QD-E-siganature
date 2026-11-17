package nc.ui.uapbd.qd.esign;

import java.util.Collections;
import nc.ui.ic.general.model.ICGenBizEditorModel;
import nc.ui.ic.general.model.ICGenBizModel;
import nc.ui.ic.general.view.ICBizView;
import nc.ui.ic.pub.env.ICUIContext;
import nc.ui.pubapp.uif2app.actions.pflow.AbstractScriptExcAction;
import nc.ui.uif2.factory.UIF2BeanFactory;
import nc.vo.pub.BusinessException;
import nc.vo.scmpub.res.billtype.ICBillType;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uif2.LoginContext;

/** Verifies the custom purchase node retains native state-action bindings and separate authorization. */
public final class OutsourceInStateConfigTests {
    public static void run() throws Exception {
        Probe config=new Probe();config.hookUIF2BeanFactory(new UIF2BeanFactory());
        require(config.getDeleteAction() instanceof QdOutsourceInDeleteAction
                && config.getSignAction() instanceof QdOutsourceInSignAction
                && config.getCancelSignAction() instanceof QdOutsourceInCancelSignAction,
                "Custom node must install its own native state-action subclasses");
        require(config.doGetBean("deleteAction")==config.getDeleteAction()
                && config.doGetBean("signAction")==config.getSignAction()
                && config.doGetBean("cancelSignAction")==config.getCancelSignAction(),
                "Native bean lookup must return the custom state actions");
        config.model.getContext().setNodeCode("40080602400");
        check(config.getDeleteAction(),"Delete","DELETE");
        check(config.getSignAction(),"Sign","SIGN");
        check(config.getCancelSignAction(),"CancelSign","CANCEL_SIGN");
        System.out.println("PASS OutsourceInStateConfigTests (distinct custom state bindings; no NC session)");
    }
    private static void check(AbstractScriptExcAction action,String button,String operation) throws Exception {
        OperationContext context=new OperationContext(null,null,"G","O","40080602400","委外加工入库",button,button,
                operation,null,Collections.singletonList(new BusinessObjectRef("item","IC_OUTSOURCE_IN","A","CODE",null)));
        require(((AuthorizedRequestConsumer)action).isAvailable(context),"Custom state handoff must be available");
        try {action.doAction(null);throw new AssertionError("Direct custom state action bypassed signing");}
        catch(BusinessException expected) { }
        try {action.procFlow(action.getFlowContext());throw new AssertionError("Direct custom PF action bypassed signing");}
        catch(BusinessException expected) { }
    }
    private static void require(boolean valid,String message){if(!valid)throw new AssertionError(message);}
    private static final class Probe extends QdOutsourceInConfig {
        final ICGenBizModel model=new ICGenBizModel();
        final ICBizView card=new ICBizView();
        final ICGenBizEditorModel editor=new ICGenBizEditorModel(card,null) {
            public ICBillType getICBillType(){return ICBillType.PurchaseIn;}
        };
        Probe(){model.setIcUIContext(new ICUIContext(new LoginContext()));}
        protected Object findBeanInUIF2BeanFactory(String name) {
            if("icBizModel".equals(name))return model;
            if("icBizEditorModel".equals(name))return editor;
            if("card".equals(name))return card;
            if("list".equals(name))return null;
            throw new AssertionError("Unexpected bean "+name);
        }
    }
}
