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
import nc.itf.uapbd.qd.esign.IMaterialCancelAssignmentService;
import nc.itf.uapbd.qd.esign.IMaterialCancelAssignmentTransactionService;
import nc.itf.uapbd.qd.esign.IMaterialCancelAssignmentTransactionService.Claim;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.MaterialCancelAssignmentResult;

/** Candidate no-transaction dispatcher; it never accepts client-supplied business records. */
public final class MaterialCancelAssignmentServiceImpl implements IMaterialCancelAssignmentService {
    private static final Logger LOG=Logger.getLogger(MaterialCancelAssignmentServiceImpl.class.getName());
    public MaterialCancelAssignmentResult execute(final String id) throws BusinessException {
        final NativeTransactionBoundaryProbe boundary=new NativeTransactionBoundaryProbe();boundary.assertNoTransaction();
        final IMaterialCancelAssignmentTransactionService service=NCLocator.getInstance().lookup(IMaterialCancelAssignmentTransactionService.class);
        EsignValues.require(service!=null,"物料分配事务服务不可用。");Claim claim=service.claim_RequiresNew(id);boundary.assertNoTransaction();
        if(claim==null) return getResult(id);
        final String token=claim.getToken();EsignValues.require(token!=null && !token.trim().isEmpty(),"物料取消分配认领令牌缺失。");
        AssignmentDispatch.run(id,claim.getObjectIds(),new AssignmentDispatch.UnitAction() {
            public void apply(String objectId) throws BusinessException { service.apply_RequiresNew(id,token,objectId); }
            public void verify(String objectId) throws BusinessException { service.verifyUnit_RequiresNew(id,token,objectId); }
            public void unknown(String objectId) throws BusinessException { service.recordUncommitted_RequiresNew(id,token,objectId,false); }
        },boundary,LOG);
        service.finish_RequiresNew(id,claim.getToken());boundary.assertNoTransaction();return getResult(id);
    }
    public MaterialCancelAssignmentResult getResult(String id) throws BusinessException {
        QdEsignStore store=new QdEsignStore();ServerIdentity identity=ServerIdentity.current();
        Map<String,Object> request=SignatureServiceImpl.owned(store,id,identity,false);
        MaterialCancelAssignmentTransactionServiceImpl.requireRoute(request,identity);
        List<Map<String,Object>> objects=store.loadAssignmentResultObjects(request);
        MaterialCancelAssignmentTransactionServiceImpl.requireCount(request,objects);List<MaterialCancelAssignmentResult.Unit> units=new ArrayList<MaterialCancelAssignmentResult.Unit>();
        for(Map<String,Object> object:objects) units.add(new MaterialCancelAssignmentResult.Unit(EsignValues.text(object,"object_pk"),
            EsignValues.text(object,"bill_code"),EsignValues.text(object,"target_org"),EsignValues.text(object,"unit_state"),
            EsignValues.text(object,"unit_error_code")));
        return new MaterialCancelAssignmentResult(id,EsignValues.text(request,"business_state"),units);
    }
}

