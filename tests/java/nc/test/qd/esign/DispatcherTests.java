package nc.test.qd.esign;
import java.io.Serializable;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import nc.impl.uapbd.qd.esign.OperationServiceImpl;
import nc.itf.uapbd.qd.esign.IOperationTransactionService;
import nc.vo.bd.cust.CustSupBusiException;
import nc.vo.ic.pub.exp.PackCheckBusException;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.*;
import nc.bs.uapbd.qd.esign.core.TransactionBoundaryProbe;
import nc.bs.uapbd.qd.esign.core.TransactionRollback;
import javax.transaction.TransactionSynchronizationRegistry;

/** 通过事务服务替身验证调度顺序和异常保留，不验证真实容器事务的提交、回滚或隔离行为。 */
public final class DispatcherTests {
    /** TEST_BOUNDARY：离线调度测试的事务边界探针，不检查真实 NC 事务。 */
    private static final TransactionBoundaryProbe TEST_BOUNDARY=new TransactionBoundaryProbe(){
        /** 提供可控的边界探针测试替身，正常场景放行，指定失败场景抛错，不查询真实 NC 事务。 */
        public void assertNoTransaction(){}};
    /** 执行当前离线测试组或测试操作，断言失败和未预期异常向外传播。 */
    public static void run() throws Exception {
        Fake f=new Fake();OperationResponse ok=new OperationServiceImpl(f,TEST_BOUNDARY).execute("request",null);
        require(f.events.equals(Arrays.asList("CLAIM","EXECUTE","VERIFY"))&&!ok.isReplayed(),"Claim and business must precede durable verification");
        f=new Fake();f.failVerification=true;
        try{new OperationServiceImpl(f,TEST_BOUNDARY).execute("request",null);throw new AssertionError("An unverified success was returned");}
        catch(BusinessException expected){require(f.events.equals(Arrays.asList("CLAIM","EXECUTE","VERIFY","FAIL"))
            && "BUSINESS_OUTCOME_UNKNOWN".equals(f.failureCode),"A missing durable result must remain unknown");}
        f=new Fake();final int[] boundaryChecks={0};
        try { new OperationServiceImpl(f,new TransactionBoundaryProbe() {
            public void assertNoTransaction() throws BusinessException {
                if(++boundaryChecks[0]>=3)throw new BusinessException("business transaction still active");
            }
        }).execute("request",null);throw new AssertionError("An active business transaction was accepted for verification"); }
        catch(BusinessException expected) { require(f.events.equals(Arrays.asList("CLAIM","EXECUTE"))
            && expected.getSuppressed().length==1,"Active transaction must block both verification and separate failure recording"); }
        f=new Fake();f.failure=new BusinessException("original business error");
        try{new OperationServiceImpl(f,TEST_BOUNDARY).execute("request",null);throw new AssertionError("Business failure swallowed");}catch(BusinessException error){require(error==f.failure&&f.events.equals(Arrays.asList("CLAIM","EXECUTE","FAIL")),"Independent failure callback and original exception");}
        require("BUSINESS_OUTCOME_UNKNOWN".equals(f.failureCode), "An unverified outcome must not be reported as a rollback");
        final Object transactionKey=new Object();final boolean[] marked={false};
        TransactionSynchronizationRegistry registry=(TransactionSynchronizationRegistry)java.lang.reflect.Proxy.newProxyInstance(
            TransactionSynchronizationRegistry.class.getClassLoader(),new Class<?>[]{TransactionSynchronizationRegistry.class},
            new java.lang.reflect.InvocationHandler(){public Object invoke(Object proxy,java.lang.reflect.Method method,Object[] args){
                if("getTransactionKey".equals(method.getName()))return transactionKey;
                if("setRollbackOnly".equals(method.getName())){marked[0]=true;return null;}
                if("getRollbackOnly".equals(method.getName()))return Boolean.valueOf(marked[0]);
                throw new UnsupportedOperationException(method.getName());}});
        f=new Fake();f.failure=TransactionRollback.abort(registry,transactionKey,new BusinessException("audit failed"));
        try{new OperationServiceImpl(f,TEST_BOUNDARY).execute("request",null);throw new AssertionError("Failure swallowed");}
        catch(BusinessException expected){require("BUSINESS_OUTCOME_UNKNOWN".equals(f.failureCode),"A rollback marker must not prove the completed outcome");}
        CustSupBusiException taxMatch=new CustSupBusiException();
        PartnerMainSaveCommand partnerSave=new PartnerMainSaveCommand(null,null,false);
        f=new Fake();f.failure=TransactionRollback.abort(registry,transactionKey,taxMatch);
        try{new OperationServiceImpl(f,TEST_BOUNDARY).execute("request",partnerSave);throw new AssertionError("Native tax match swallowed");}
        catch(BusinessException expected){require(expected==f.failure && "BUSINESS_OUTCOME_UNKNOWN".equals(f.failureCode),
            "A rollback marker must not expose a retryable tax-match error");}
        f=new Fake();f.failure=new BusinessException("uncertain",taxMatch);
        try{new OperationServiceImpl(f,TEST_BOUNDARY).execute("request",partnerSave);throw new AssertionError("Uncertain tax match swallowed");}
        catch(BusinessException expected){require(expected==f.failure && "BUSINESS_OUTCOME_UNKNOWN".equals(f.failureCode),
            "An unconfirmed rollback must not offer the native retry");}
        InventoryNativeCommand inventory=new InventoryNativeCommand(){private static final long serialVersionUID=1L;};
        PackCheckBusException resume=new PackCheckBusException("native inventory check");
        f=new Fake();f.failure=TransactionRollback.abort(registry,transactionKey,resume);
        try{new OperationServiceImpl(f,TEST_BOUNDARY).execute("request",inventory);throw new AssertionError("Native inventory check swallowed");}
        catch(BusinessException expected){require(expected==f.failure && "BUSINESS_OUTCOME_UNKNOWN".equals(f.failureCode),
            "A rollback marker must not expose a retryable inventory error");}
        f=new Fake();f.failure=new BusinessException("uncertain",resume);
        try{new OperationServiceImpl(f,TEST_BOUNDARY).execute("request",inventory);throw new AssertionError("Uncertain inventory failure swallowed");}
        catch(BusinessException expected){require(expected==f.failure && "BUSINESS_OUTCOME_UNKNOWN".equals(f.failureCode),
            "An unconfirmed inventory rollback must not expose a retryable error");}
        f=new Fake();f.failure=TransactionRollback.abort(registry,transactionKey,resume);f.failPersistence=true;
        Logger retryLog=Logger.getLogger(OperationServiceImpl.class.getName());Level retryLevel=retryLog.getLevel();retryLog.setLevel(Level.OFF);
        try{new OperationServiceImpl(f,TEST_BOUNDARY).execute("request",inventory);throw new AssertionError("Inventory failure swallowed");}
        catch(BusinessException expected){require(expected.getCause()==f.failure && f.failure.getSuppressed().length==1
            && expected.getMessage().contains("请求号"),
            "A failure to persist the result must not offer the native retry");}finally{retryLog.setLevel(retryLevel);}
        f=new Fake();f.runtimeFailure=new IllegalStateException("transport failure");
        try{new OperationServiceImpl(f,TEST_BOUNDARY).execute("request",null);throw new AssertionError("Failure swallowed");}
        catch(IllegalStateException expected){require("BUSINESS_OUTCOME_UNKNOWN".equals(f.failureCode),"Runtime failure without a rollback marker requires reconciliation");}
        f=new Fake();f.replayed=true;OperationResponse replay=new OperationServiceImpl(f,TEST_BOUNDARY).execute("request",null);require(replay.isReplayed()&&f.events.equals(Arrays.asList("CLAIM","EXECUTE","VERIFY")),"Committed request replays only after durable verification");
        f=new Fake();f.failure=new BusinessException("original");f.failPersistence=true;Logger log=Logger.getLogger(OperationServiceImpl.class.getName());Level level=log.getLevel();log.setLevel(Level.OFF);
        try{new OperationServiceImpl(f,TEST_BOUNDARY).execute("request",null);throw new AssertionError("Failure swallowed");}catch(BusinessException error){require(error==f.failure&&error.getSuppressed().length==1,"Failure persistence problem must remain attached");}finally{log.setLevel(level);}
        f=new Fake();try{new OperationServiceImpl(f,new TransactionBoundaryProbe(){
        /** 提供可控的边界探针测试替身，正常场景放行，指定失败场景抛错，不查询真实 NC 事务。 */
        public void assertNoTransaction() throws BusinessException{throw new BusinessException("ambient transaction");}}).execute("request",null);throw new AssertionError("Ambient transaction accepted");}catch(BusinessException expected){require(f.events.isEmpty(),"Boundary failure must prevent even the durable claim");}
        System.out.println("PASS DispatcherTests assertions=13 (ordering/outcome test doubles; NC CMT not invoked)");
    }
    /** 验证指定前置条件，不满足时立即抛出相应异常，阻止后续处理。 */
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    /** 事务边界测试替身，以事件列表记录认领、业务执行和失败记录顺序，不模拟真实 CMT 提交。 */
    private static final class Fake implements IOperationTransactionService {
        /** events：测试观察到的事务服务调用顺序。 */
        final List<String> events=new ArrayList<String>();
        /** failure：最近一次失败原因，保留原始异常。 */
        BusinessException failure;
        RuntimeException runtimeFailure;
        String failureCode;
        /**
         * replayed：是否为成功请求的结果重入，标记为真时不重新执行业务。
         * failPersistence：是否模拟失败结果无法落库。
         */
        boolean replayed,failPersistence,failVerification;
        /** 记录 CLAIM 事件，按测试重入标记返回空值或固定内部令牌，不执行行锁或数据库提交。 */
        public String claim_RequiresNew(String id){events.add("CLAIM");return replayed?null:"internal-token";}
        /** 记录 EXECUTE 事件并校验测试令牌，可抛出指定原异常或返回测试响应，不调用真实业务服务。 */
        public OperationResponse executeClaimed_RequiresNew(String id,String token,Serializable command) throws BusinessException{events.add("EXECUTE");if(!replayed&&token==null)throw new AssertionError("Missing token");if(failure!=null)throw failure;if(runtimeFailure!=null)throw runtimeFailure;return new OperationResponse(id,replayed,null,Collections.<BusinessObjectRef>emptyList());}
        public void verifyCommitted_RequiresNew(String id,String token,OperationResponse attempted) throws BusinessException {
            events.add("VERIFY");if(failVerification)throw new BusinessException("durable result missing");
        }
        /** 记录 FAIL 事件，并按开关模拟失败结果无法保存，不执行真实数据库写入。 */
        public void failClaimed_RequiresNew(String id,String token,String code) throws BusinessException{events.add("FAIL");failureCode=code;if(failPersistence)throw new BusinessException("database unavailable");}
    }
}
