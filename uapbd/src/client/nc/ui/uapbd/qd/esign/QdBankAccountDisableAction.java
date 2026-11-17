package nc.ui.uapbd.qd.esign;

import java.awt.event.ActionEvent;
import nc.ui.bd.bankacc.cust.actions.CustBankaccDisableAction;
import nc.ui.bd.bankacc.cust.model.CustBankaccAppModel;
import nc.vo.bd.bankaccount.cust.CustBankaccUnionVO;
import nc.vo.pub.BusinessException;

/** Keeps native confirmation while suppressing the native success message after a cancellation. */
public final class QdBankAccountDisableAction extends CustBankaccDisableAction {
    private static final long serialVersionUID=1L;private transient ActionEvent event;private transient boolean completed;
    @Override public void doAction(ActionEvent value) throws Exception {
        if(event!=null) throw new BusinessException("银行账户停用正在处理。");
        completed=false;event=value;try { super.doAction(value); } finally { event=null;completed=false; }
    }
    @Override public Object doDisable(Object value) throws Exception {
        if(!(value instanceof CustBankaccUnionVO) || !(getModel() instanceof CustBankaccAppModel))
            throw new BusinessException("银行账户停用数据不正确。");
        CustBankaccUnionVO result=new QdBankAccountCoordinator((CustBankaccAppModel)getModel(),"DISABLE",event,
            (CustBankaccUnionVO)value).execute();completed=true;return result;
    }
    @Override protected void showSuccessInfo() { if(completed) super.showSuccessInfo(); }
}
