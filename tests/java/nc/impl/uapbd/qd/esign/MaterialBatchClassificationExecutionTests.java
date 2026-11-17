package nc.impl.uapbd.qd.esign;

import java.util.HashMap;
import java.util.Map;
import nc.vo.bd.errorlog.ErrLogReturnValue;
import nc.vo.pub.BusinessException;

/** Verifies the handoff immediately before NC's material batch call. */
public final class MaterialBatchClassificationExecutionTests {
    private MaterialBatchClassificationExecutionTests() { }

    public static void run() throws BusinessException {
        final String category="CLASS-A";
        final Map<String,Object> values=new HashMap<String,Object>();
        values.put("materialmnecode","001");
        values.put("pk_marbasclass",category);
        final NativeWriteSpy service=new NativeWriteSpy();
        final BusinessException denied=new BusinessException("Target classification denied");

        reject(service,values,denied);
        reject(service,values,new BusinessException("NC reference permissions unavailable"));
        require(service.calls==0,"A denied or unverified classification reached NC");

        service.batchUpdateWithClassificationCheck(new String[]{"materialmnecode"},values,
            new String[]{"ORG"},new String[]{"MAT"},new TrackedMaterialBatchUpdateService.ClassificationCheck() {
                public void require(Object value) {
                    MaterialBatchClassificationExecutionTests.require(category.equals(value),
                        "Linked classification must be checked using the submitted value");
                    MaterialBatchClassificationExecutionTests.require(service.calls==0,
                        "Reference checks must run before the native write");
                }
            });
        require(service.calls==1 && service.values==values,"The verified payload must be sent to NC exactly once");
        System.out.println("PASS MaterialBatchClassificationExecutionTests (checked signed payload handoff; no NC transaction)");
    }

    private static void reject(NativeWriteSpy service,Map<String,Object> values,final BusinessException failure)
            throws BusinessException {
        try {
            service.batchUpdateWithClassificationCheck(new String[]{"pk_marbasclass"},values,
                new String[]{"ORG"},new String[]{"MAT"},new TrackedMaterialBatchUpdateService.ClassificationCheck() {
                    public void require(Object value) throws BusinessException { throw failure; }
                });
            throw new AssertionError("Classification check failure did not abort execution");
        } catch(BusinessException expected) {
            if(expected!=failure) throw expected;
        }
    }

    private static final class NativeWriteSpy extends TrackedMaterialBatchUpdateService {
        int calls;
        Map<String,Object> values;

        @Override public ErrLogReturnValue batchUpdateByPksForNewTrans(String[] fields,Map<String,Object> values,
                String[] orgs,String[] targets,String[] pks,boolean needReturn) {
            calls++;
            this.values=values;
            require(targets.length==0 && pks.length==1 && "MAT".equals(pks[0]) && needReturn,
                "Native main-tab batch arguments changed");
            return null;
        }
    }

    private static void require(boolean condition,String message) {
        if(!condition) throw new AssertionError(message);
    }
}
