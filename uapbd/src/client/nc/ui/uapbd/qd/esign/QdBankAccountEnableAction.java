package nc.ui.uapbd.qd.esign;

import java.awt.event.ActionEvent;
import nc.bs.bd.bankacc.validator.CustSupBankaccAutoEnableValidation;
import nc.ui.bd.bankacc.cust.actions.CustBankaccEnableAction;
import nc.ui.bd.bankacc.cust.model.CustBankaccAppModel;
import nc.vo.bd.bankaccount.cust.CustBankaccUnionVO;
import nc.vo.pub.BusinessException;

/** Reuses native confirmation and creator/modifier checks, then dispatches through the owner-policy gateway. */
public final class QdBankAccountEnableAction extends CustBankaccEnableAction {
    private static final long serialVersionUID=1L;private transient ActionEvent event;
    @Override public void doAction(ActionEvent value) throws Exception {
        if(event!=null) throw new BusinessException("银行账户启用正在处理。");
        event=value;try { super.doAction(value); } finally { event=null; }
    }
    @Override public Object doEnable(Object value) throws Exception {
        if(!(value instanceof CustBankaccUnionVO) || !(getModel() instanceof CustBankaccAppModel))
            throw new BusinessException("银行账户启用数据不正确。");
        new CustSupBankaccAutoEnableValidation().validate(value);
        CustBankaccUnionVO result=new QdBankAccountCoordinator((CustBankaccAppModel)getModel(),"ENABLE",event,
            (CustBankaccUnionVO)value).execute();
        showSuccessInfo();return result;
    }
}
