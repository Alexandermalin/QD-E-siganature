package nc.ui.uapbd.qd.esign;

import java.util.Map;
import nc.itf.bd.cust.baseinfo.ICustBaseInfoExtendService;
import nc.ui.bd.cust.baseinfo.service.CustBaseInfoModelService;
import nc.ui.bd.pub.extend.PrivateServiceContext;
import nc.ui.uif2.model.AbstractAppModel;
import nc.vo.bd.cust.CustomerVO;
import nc.vo.bd.supplier.SupplierVO;
import nc.vo.pub.BusinessException;

/** Keeps the native customer model's validation and tax-match dialog. */
public final class QdCustomerMainModelService extends CustBaseInfoModelService {
    private AbstractAppModel model;
    public void setModel(AbstractAppModel value) { model=value; }

    @Override public ICustBaseInfoExtendService getExtendService() { return controlled; }

    private final ICustBaseInfoExtendService controlled=new ICustBaseInfoExtendService() {
        public CustomerVO insertCustomerWithExtendVO(CustomerVO value,Map<String,Object> data,PrivateServiceContext extensions,boolean skip)
                throws BusinessException { return (CustomerVO)QdPartnerMainSaveCoordinator.save(model,value,data,skip); }
        public CustomerVO updateCustomerWithExtendVO(CustomerVO value,Map<String,Object> data,PrivateServiceContext extensions,boolean skip)
                throws BusinessException { return (CustomerVO)QdPartnerMainSaveCoordinator.save(model,value,data,skip); }
        public void deleteCustomerWithExtendVO(CustomerVO value,Map<String,Object> data,PrivateServiceContext extensions)
                throws BusinessException { nativeDelete(value,data,extensions); }
        public CustomerVO insertCustAndRelaToSupWithExtendVO(CustomerVO value,Map<String,Object> data,PrivateServiceContext extensions,
                boolean skip,SupplierVO supplier) throws BusinessException { throw linkPending(); }
        public CustomerVO updateCustAndRelaToSupWithExtendVO(CustomerVO value,Map<String,Object> data,PrivateServiceContext extensions,
                boolean skip,SupplierVO supplier) throws BusinessException { throw linkPending(); }
    };

    private void nativeDelete(CustomerVO value,Map<String,Object> data,PrivateServiceContext extensions) throws BusinessException {
        super.getExtendService().deleteCustomerWithExtendVO(value,data,extensions);
    }
    private static BusinessException linkPending() {
        return new BusinessException("关联现有供应商会变更其他档案主键及引用，当前保存入口尚未核实该范围。");
    }
}
