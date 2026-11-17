package nc.ui.uapbd.qd.esign;

import nc.ui.uif2.components.pagination.IPaginationQueryService;
import nc.ui.uif2.model.IAppModelService;
import nc.vo.pub.BusinessException;

/** Supplier page services expose pagination; customer page services do not. */
public final class QdSupplierOrgInfoModelService extends QdPartnerOrgInfoModelService implements IPaginationQueryService {
    public QdSupplierOrgInfoModelService(IAppModelService nativeService,String domain) { super(nativeService,domain); }
    public Object[] queryObjectByPks(String[] pks) throws BusinessException { return queryNativeObjectByPks(pks); }
}
