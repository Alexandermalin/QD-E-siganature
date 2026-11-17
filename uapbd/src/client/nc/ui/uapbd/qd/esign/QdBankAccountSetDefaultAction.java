package nc.ui.uapbd.qd.esign;

import nc.ui.bd.bankacc.cust.actions.SetDefaultAction;
import nc.vo.bd.bankaccount.cust.CustBankaccUnionVO;
import nc.vo.pub.BusinessException;

/** Uses the native currency picker and confirmation, then saves through the bank Edit policy. */
public final class QdBankAccountSetDefaultAction extends SetDefaultAction {
    private static final long serialVersionUID=1L;

    @Override protected boolean isActionEnable() {
        return getModel()!=null && getModel().getSelectedData()!=null && getModel().canMaintainCustAcc() && super.isActionEnable();
    }

    @Override public CustBankaccUnionVO[] doIsDefault(Object value,String currency) throws Exception {
        if(!(value instanceof CustBankaccUnionVO) || currency==null || !currency.matches("[A-Za-z0-9_-]{1,20}"))
            throw new BusinessException("请选择银行账户及币种。");
        return new QdBankAccountCoordinator(getModel(),"UPDATE",null,(CustBankaccUnionVO)value,currency).executeDefault();
    }
}
