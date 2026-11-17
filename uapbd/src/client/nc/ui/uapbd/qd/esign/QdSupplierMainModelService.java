package nc.ui.uapbd.qd.esign;

import java.util.LinkedHashMap;
import java.util.Map;
import nc.ui.bd.pub.extend.ExtendContext;
import nc.ui.bd.pub.extend.ExtendInfo;
import nc.ui.bd.supplier.baseinfo.model.SupplierAppModelService;
import nc.ui.uif2.model.AbstractAppModel;
import nc.vo.bd.cust.CustomerVO;
import nc.vo.bd.supplier.SupplierVO;
import nc.vo.pub.BusinessException;

/** Routes the native supplier save and the user's tax-match choice through one controlled RPC. */
public final class QdSupplierMainModelService extends SupplierAppModelService {
    private AbstractAppModel model;
    public void setModel(AbstractAppModel value) { model=value; }

    @Override public Object insert(Object value) throws Exception { return save(value,false); }
    @Override public Object update(Object value) throws Exception { return save(value,false); }
    @Override public Object insertWithoutRelaCheck(Object value) throws Exception { return save(value,true); }
    @Override public Object updateWithoutRelaCheck(Object value) throws Exception { return save(value,true); }
    @Override public SupplierVO insertSupAndRelaToCust(SupplierVO value,CustomerVO customer) throws BusinessException { throw linkPending(); }
    @Override public Object updateSupAndRelaToCust(SupplierVO value,CustomerVO customer) throws BusinessException { throw linkPending(); }

    private SupplierVO save(Object raw,boolean skip) throws BusinessException {
        if(!(raw instanceof SupplierVO)) throw new BusinessException("供应商主档保存数据不正确。");
        Map<String,Object> data=new LinkedHashMap<String,Object>();
        ExtendContext context=getExtendContext();
        if(context!=null) for(ExtendInfo info:context.getExtendInfoList())
            data.put(info.getIdentifier(),info.getExtendeditor().getValue());
        return (SupplierVO)QdPartnerMainSaveCoordinator.save(model,(SupplierVO)raw,data,skip);
    }

    private static BusinessException linkPending() {
        return new BusinessException("关联现有客户会变更其他档案主键及引用，当前保存入口尚未核实该范围。");
    }
}
