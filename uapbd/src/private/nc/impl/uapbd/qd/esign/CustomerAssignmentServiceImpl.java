package nc.impl.uapbd.qd.esign;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import nc.bs.framework.common.NCLocator;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.bs.uapbd.qd.esign.core.NativeTransactionBoundaryProbe;
import nc.bs.uapbd.qd.esign.persistence.QdEsignStore;
import nc.bs.uapbd.qd.esign.security.ServerIdentity;
import nc.itf.uapbd.qd.esign.ICustomerAssignmentService;
import nc.itf.uapbd.qd.esign.ICustomerAssignmentTransactionService;
import nc.itf.uapbd.qd.esign.ICustomerAssignmentTransactionService.Claim;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.CustomerAssignmentResult;

/** Candidate no-transaction dispatcher; it never accepts client-supplied business records. */
public final class CustomerAssignmentServiceImpl implements ICustomerAssignmentService {
    private static final Logger LOG=Logger.getLogger(CustomerAssignmentServiceImpl.class.getName());
    public CustomerAssignmentResult execute(final String id) throws BusinessException {
        final NativeTransactionBoundaryProbe boundary=new NativeTransactionBoundaryProbe();boundary.assertNoTransaction();
        final ICustomerAssignmentTransactionService service=NCLocator.getInstance().lookup(ICustomerAssignmentTransactionService.class);
        EsignValues.require(service!=null,"客户分配事务服务不可用。");Claim claim=service.claim_RequiresNew(id);boundary.assertNoTransaction();
        if(claim==null) return getResult(id);
        final String token=claim.getToken();EsignValues.require(token!=null && !token.trim().isEmpty(),"客户分配认领令牌缺失。");
        AssignmentDispatch.run(id,claim.getObjectIds(),new AssignmentDispatch.UnitAction() {
            public void apply(String objectId) throws BusinessException { service.apply_RequiresNew(id,token,objectId); }
            public void verify(String objectId) throws BusinessException { service.verifyUnit_RequiresNew(id,token,objectId); }
            public void unknown(String objectId) throws BusinessException { service.recordUncommitted_RequiresNew(id,token,objectId,false); }
        },boundary,LOG);
        service.finish_RequiresNew(id,claim.getToken());boundary.assertNoTransaction();return getResult(id);
    }
    public CustomerAssignmentResult getResult(String id) throws BusinessException {
        QdEsignStore store=new QdEsignStore();ServerIdentity identity=ServerIdentity.current();
        Map<String,Object> request=SignatureServiceImpl.owned(store,id,identity,false);
        CustomerAssignmentTransactionServiceImpl.requireRoute(request,identity);
        List<Map<String,Object>> objects=store.loadAssignmentResultObjects(request);
        CustomerAssignmentTransactionServiceImpl.requireCount(request,objects);List<CustomerAssignmentResult.Unit> units=new ArrayList<CustomerAssignmentResult.Unit>();
        for(Map<String,Object> object:objects) units.add(new CustomerAssignmentResult.Unit(EsignValues.text(object,"object_pk"),
            EsignValues.text(object,"bill_code"),EsignValues.text(object,"target_org"),EsignValues.text(object,"unit_state"),
            EsignValues.text(object,"unit_error_code")));
        return new CustomerAssignmentResult(id,EsignValues.text(request,"business_state"),units);
    }
}
