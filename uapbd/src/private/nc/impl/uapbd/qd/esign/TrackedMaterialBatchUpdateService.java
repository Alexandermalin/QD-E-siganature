package nc.impl.uapbd.qd.esign;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import nc.bs.uapbd.qd.esign.core.NativeBatchFailureTracker;
import nc.impl.bd.material.baseinfo.MaterialBaseInfoBatchUpdateForNewTransServiceImpl;
import nc.vo.bd.errorlog.ErrLogReturnValue;
import nc.vo.bd.material.MaterialVO;
import nc.vo.pub.BusinessException;

/** Candidate native material batch service that preserves per-object filtering evidence. */
class TrackedMaterialBatchUpdateService extends MaterialBaseInfoBatchUpdateForNewTransServiceImpl {
    interface ClassificationCheck {
        void require(Object value) throws BusinessException;
    }

    private final NativeBatchFailureTracker tracker=new NativeBatchFailureTracker();

    final ErrLogReturnValue batchUpdateWithClassificationCheck(String[] fields,Map<String,Object> values,
            String[] orgs,String[] pks,ClassificationCheck classification) throws BusinessException {
        if(values.containsKey("pk_marbasclass")) classification.require(values.get("pk_marbasclass"));
        return batchUpdateByPksForNewTrans(fields,values,orgs,new String[0],pks,true);
    }

    @Override protected List<MaterialVO> filterPermissionVOs(List<MaterialVO> rows,String[] orgs) throws BusinessException {
        Set<String> before=NativeBatchFailureTracker.snapshot(rows);
        List<MaterialVO> result=super.filterPermissionVOs(rows,orgs);
        tracker.filtered("PERMISSION",before,result);return result;
    }
    @Override protected List<MaterialVO> validateVOs(List<MaterialVO> rows) throws BusinessException {
        Set<String> before=NativeBatchFailureTracker.snapshot(rows);
        List<MaterialVO> result=super.validateVOs(rows);
        tracker.filtered("VALIDATION",before,result);return result;
    }
    @Override protected MaterialVO[] checkBeforeUpdate(List<MaterialVO> rows) throws BusinessException {
        Set<String> before=NativeBatchFailureTracker.snapshot(rows);
        MaterialVO[] result=super.checkBeforeUpdate(rows);
        tracker.filtered("BEFORE_EVENT",before,result==null?Collections.<MaterialVO>emptyList():Arrays.asList(result));return result;
    }
    Map<String,String> excluded() { return tracker.excluded(); }
}

