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
import nc.itf.uapbd.qd.esign.IMaterialAssignmentService;
import nc.itf.uapbd.qd.esign.IMaterialAssignmentTransactionService;
import nc.itf.uapbd.qd.esign.IMaterialAssignmentTransactionService.Claim;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.MaterialAssignmentResult;

/** NONE transaction dispatcher. No client-supplied business rows are accepted at execution. */
public final class MaterialAssignmentServiceImpl implements IMaterialAssignmentService {
    private static final Logger LOG = Logger.getLogger(MaterialAssignmentServiceImpl.class.getName());

    public MaterialAssignmentResult execute(final String id) throws BusinessException {
        final NativeTransactionBoundaryProbe boundary=new NativeTransactionBoundaryProbe();boundary.assertNoTransaction();
        final IMaterialAssignmentTransactionService service = NCLocator.getInstance().lookup(IMaterialAssignmentTransactionService.class);
        Claim claim = service.claim_RequiresNew(id);
        boundary.assertNoTransaction();
        if (claim == null) { return getResult(id); }
        final String token = claim.getToken();EsignValues.require(token!=null && !token.trim().isEmpty(),"物料分配认领令牌缺失。");
        AssignmentDispatch.run(id,claim.getObjectIds(),new AssignmentDispatch.UnitAction() {
            public void apply(String objectId) throws BusinessException { service.apply_RequiresNew(id,token,objectId); }
            public void verify(String objectId) throws BusinessException { service.verifyUnit_RequiresNew(id,token,objectId); }
            public void unknown(String objectId) throws BusinessException { service.recordUncommitted_RequiresNew(id,token,objectId,false); }
        },boundary,LOG);
        service.finish_RequiresNew(id, token);
        boundary.assertNoTransaction();
        return getResult(id);
    }

    public MaterialAssignmentResult getResult(String id) throws BusinessException {
        QdEsignStore store = new QdEsignStore();
        ServerIdentity identity = ServerIdentity.current();
        Map<String,Object> request = SignatureServiceImpl.owned(store, id, identity, false);
        MaterialAssignmentTransactionServiceImpl.requireRoute(request, identity);
        List<Map<String,Object>> objects = store.loadAssignmentResultObjects(request);
        MaterialAssignmentTransactionServiceImpl.requireObjectCount(request, objects);
        List<MaterialAssignmentResult.Unit> units = new ArrayList<MaterialAssignmentResult.Unit>();
        for (Map<String,Object> object : objects) {
            units.add(new MaterialAssignmentResult.Unit(EsignValues.text(object, "object_pk"), EsignValues.text(object, "bill_code"),
                EsignValues.text(object, "target_org"), EsignValues.text(object, "unit_state"), EsignValues.text(object, "unit_error_code")));
        }
        return new MaterialAssignmentResult(id, EsignValues.text(request, "business_state"), units);
    }
}
