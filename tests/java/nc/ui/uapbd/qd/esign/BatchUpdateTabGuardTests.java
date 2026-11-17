package nc.ui.uapbd.qd.esign;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import nc.bs.bd.batchupdate.IBatchUpdateService;
import nc.ui.bd.pub.batchupdate.config.BatchUpdateContext;
import nc.ui.bd.pub.batchupdate.config.IBatchUpdateTab;
import nc.vo.bd.errorlog.ErrLogResult;
import nc.vo.bd.errorlog.ErrLogReturnValue;
import nc.vo.pub.BusinessException;

/** Fast dialog calls are gated after it sets the mode; regular wizard calls retain the native service. */
public final class BatchUpdateTabGuardTests {
    private BatchUpdateTabGuardTests() { }
    public static void run() throws Exception {
        final int[] nativeCalls={0},guardCalls={0};
        final ErrLogReturnValue nativeResult=new ErrLogReturnValue(new ErrLogResult(null,0,null),null,1);
        IBatchUpdateService nativeService=new IBatchUpdateService() {
            public ErrLogReturnValue batchUpdateByPks(String[] a,Map<String,Object> b,String[] c,String[] d,String[] e,boolean f) {
                nativeCalls[0]++;return nativeResult;
            }
            public ErrLogReturnValue batchUpdateByCondition(String[] a,Map<String,Object> b,String[] c,String[] d,String e) {
                nativeCalls[0]++;return nativeResult;
            }
        };
        final IBatchUpdateTab original=tab(nativeService);BatchUpdateContext context=new BatchUpdateContext();
        QdBatchUpdateTabGuard.Dispatcher dispatcher=new QdBatchUpdateTabGuard.Dispatcher() {
            public ErrLogReturnValue execute(BatchUpdateContext ctx,IBatchUpdateTab tab,String attribute,String[] fields,
                    Map<String,Object> values,String[] permissions,String[] targets,String[] pks,boolean needReturn) {
                require(tab==original && "name".equals(attribute) && fields.length==1 && pks.length==2 && needReturn,
                    "Native fast-dialog arguments were not passed to the guarded dispatcher");
                guardCalls[0]++;return nativeResult;
            }
        };
        IBatchUpdateService guarded=new QdBatchUpdateTabGuard(context,original,dispatcher).getBatchUpdateService("name");
        String[] fields={"name"},pks={"A","B"};Map<String,Object> values=new HashMap<String,Object>();values.put("name","new");
        require(guarded.batchUpdateByPks(fields,values,null,null,pks,true)==nativeResult && nativeCalls[0]==1,
            "Regular wizard no longer delegates to NC");
        context.setFastBatchUpdate(true);
        require(guarded.batchUpdateByPks(fields,values,null,null,pks,true)==nativeResult && guardCalls[0]==1 && nativeCalls[0]==1,
            "Fast batch bypassed its dispatcher");
        try { guarded.batchUpdateByCondition(fields,values,null,null,"1=1");throw new AssertionError("Fast condition batch bypassed selected identities"); }
        catch(BusinessException expected) { }
        IBatchUpdateService missing=new QdBatchUpdateTabGuard(context,original,null).getBatchUpdateService("name");
        try { missing.batchUpdateByPks(fields,values,null,null,pks,true);throw new AssertionError("Missing fast-batch dispatcher fell back to NC"); }
        catch(BusinessException expected) { }
        require(nativeCalls[0]==1,"Rejected fast batch called the native write service");
        context.setFastBatchUpdate(false);
        require(missing.batchUpdateByCondition(fields,values,null,null,"condition")==nativeResult && nativeCalls[0]==2,
            "Regular condition wizard no longer delegates to NC");
        System.out.println("PASS BatchUpdateTabGuardTests (native fast-dialog service routing; no NC session)");
    }
    private static IBatchUpdateTab tab(final IBatchUpdateService service) {
        return (IBatchUpdateTab)Proxy.newProxyInstance(IBatchUpdateTab.class.getClassLoader(),new Class<?>[]{IBatchUpdateTab.class},
            new InvocationHandler() { public Object invoke(Object proxy,Method method,Object[] args) {
                if("getBatchUpdateService".equals(method.getName())) return service;
                if("getCode".equals(method.getName())) return "base";
                if(method.getReturnType()==Boolean.TYPE) return Boolean.FALSE;return null;
            }});
    }
    private static void require(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
}
