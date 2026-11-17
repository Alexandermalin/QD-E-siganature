package nc.ui.uapbd.qd.esign;

import nc.ui.bd.bankacc.cust.actions.CustBankaccDisableAction;
import nc.ui.bd.bankacc.cust.actions.CustBankaccEnableAction;
import nc.ui.bd.bankacc.cust.actions.SetDefaultAction;
import nc.ui.bd.bankacc.cust.model.CustBankaccAppModel;
import nc.ui.bd.bankacc.cust.supplierbankacc;

/** Candidate for NC installations that load the supplier popup's generated Java bean definition. */
public final class QdSupplierBankAccountBeanDefinition extends supplierbankacc {
    private QdBankAccountEnableAction enable;
    private QdBankAccountDisableAction disable;
    private QdBankAccountSetDefaultAction setDefault;
    private final QdBankAccountModelService accountService=new QdBankAccountModelService();
    @Override public QdBankAccountModelService getModelservice() { return accountService; }
    @Override public CustBankaccAppModel getAppmodel() {
        CustBankaccAppModel model=super.getAppmodel();
        accountService.setModel(model);
        return model;
    }
    @Override public SetDefaultAction getSetDefaultAction() {
        if(setDefault==null) {
            setDefault=new QdBankAccountSetDefaultAction();
            setDefault.setModel(getAppmodel());
            setDefault.setEditor(getEditor());
            setDefault.setExceptionHandler(getExceptionHandler());
            setBeanFacotryIfBeanFacatoryAware(setDefault);
            invokeInitializingBean(setDefault);
        }
        return setDefault;
    }
    @Override public CustBankaccEnableAction getEnableaction() {
        if(enable==null) {
            enable=new QdBankAccountEnableAction();enable.setModel(getAppmodel());enable.setMdOperateCode("enable");enable.setResourceCode("supbankacc");
            setBeanFacotryIfBeanFacatoryAware(enable);invokeInitializingBean(enable);
        }
        return enable;
    }
    @Override public CustBankaccDisableAction getDisableaction() {
        if(disable==null) {
            disable=new QdBankAccountDisableAction();disable.setModel(getAppmodel());disable.setMdOperateCode("disable");disable.setResourceCode("supbankacc");
            setBeanFacotryIfBeanFacatoryAware(disable);invokeInitializingBean(disable);
        }
        return disable;
    }
}
