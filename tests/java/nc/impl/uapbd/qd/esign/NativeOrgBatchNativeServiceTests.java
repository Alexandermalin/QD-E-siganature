package nc.impl.uapbd.qd.esign;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import nc.bs.bd.batchupdate.BatchUpdateOrgTabService;
import nc.bs.uapbd.qd.esign.core.NativeOrgBatchPage;
import nc.bs.uapbd.qd.esign.core.PartnerOrgBatchScope;
import nc.vo.bd.cust.finance.CustFinanceVO;
import nc.vo.bd.errorlog.ErrorLogVO;
import nc.vo.bd.errorlog.ErrorMsgVO;
import nc.vo.bd.errorlog.ErrLogResult;
import nc.vo.bd.errorlog.ErrLogReturnValue;
import nc.vo.pub.BusinessException;

/** Verifies trusted native service selection and exact page IDs with an offline invocation double. */
public final class NativeOrgBatchNativeServiceTests {
    private NativeOrgBatchNativeServiceTests() { }
    public static void run() throws Exception {
        String[] expected={
            "nc.impl.bd.cust.finance.CustFinanceBatchUpdateForNewTransServiceImpl",
            "nc.impl.bd.cust.sale.CustSaleBatchUpdateForNewTransServiceImpl",
            "nc.impl.bd.cust.creditctl.CustCreditctlBatchUpdateServiceImpl",
            "nc.impl.bd.supplier.finance.SupplierFiBatchUpdateForNewTransServiceImpl",
            "nc.impl.bd.supplier.stock.SupplierStockBatchUpdateForNewTransServiceImpl",
            "nc.impl.bd.material.fi.MaterialFiBatchUpdateForNewTransServiceImpl",
            "nc.impl.bd.material.pu.MaterialPuBatchUpdateForNewTransServiceImpl",
            "nc.impl.bd.material.sale.MaterialSaleBatchUpdateForNewTransServiceImpl",
            "nc.impl.bd.material.stock.MaterialStockBatchUpdateForNewTransServiceImpl",
            "nc.impl.bd.material.plan.MaterialPlanBatchUpdateForNewTransServiceImpl",
            "nc.impl.bd.material.prod.MaterialProdBatchUpdateForNewTransServiceImpl",
            "nc.impl.bd.material.cost.MaterialCostBatchUpdateForNewTransServiceImpl",
            "nc.impl.bd.material.pfc.MaterialPfcBatchUpdateForNewTransServiceImpl",
            "nc.impl.bd.material.pfcc.MaterialProfitCostBatchUpdateForNewTransServiceImpl"};
        NativeOrgBatchPage[] pages=NativeOrgBatchPage.values();
        require(pages.length==expected.length,"Native page dispatch coverage changed");
        for(int i=0;i<pages.length;i++) {
            NativeOrgBatchNativeService.Bound bound=NativeOrgBatchNativeService.open(pages[i]);
            require(expected[i].equals(bound.service.getClass().getSuperclass().getName()),"Wrong native page service selected");
            boolean permission=false,validation=false,before=false;
            for(Method method:bound.service.getClass().getDeclaredMethods()) if(!method.isBridge()) {
                permission|="filterPermissionVOs".equals(method.getName());validation|="validateVOs".equals(method.getName());
                before|="checkBeforeUpdate".equals(method.getName());
            }
            require(permission && validation && before && bound.type!=null,"Native filtering stages are not tracked");
            try { bound.execute(null,new String[]{"ORG"});throw new AssertionError("Missing locked scope reached native service"); }
            catch(BusinessException expectedFailure) { }
        }
        System.out.println("PASS NativeOrgBatchNativeServiceTests (14 fixed native services; no NC transaction)");
    }
    public static void verifyUnverified(PartnerOrgBatchScope.Prepared scope) throws Exception {
        FakeService fake=new FakeService();NativeOrgBatchNativeService.Bound bound=bound(fake);
        try { bound.execute(scope,new String[]{"ORG"});throw new AssertionError("Preparation-only scope reached native service"); }
        catch(BusinessException expected) { }
        require(fake.calls==0,"Unverified page scope invoked native business code");
    }
    public static void verifyExecution(PartnerOrgBatchScope.Prepared scope) throws Exception {
        FakeService fake=new FakeService();NativeOrgBatchNativeService.Bound bound=bound(fake);String[] permissions={"ORG"};
        NativeOrgBatchNativeService.Execution result=bound.execute(scope,permissions);
        require(fake.calls==1 && Arrays.equals(fake.ids,new String[]{"P1","P2"})
            && Arrays.equals(fake.targets,new String[]{"T1","T2"}) && Arrays.equals(fake.fields,new String[]{"name","taxcode"})
            && fake.needReturn && result.result!=null && "VALIDATION".equals(result.excluded.get("P2")),
            "Native invocation did not use the exact authorized page identities and validation evidence");
        require("ORG".equals(permissions[0]) && "n".equals(scope.getPayload().getChanges().copyValues().get("name")),
            "Native service mutation changed the signed input or caller permissions");
        try { bound.execute(scope,permissions);throw new AssertionError("A stateful native service was reused"); }
        catch(BusinessException expected) { }
        fake=new FakeService();fake.wrongTable=true;bound=bound(fake);
        try { bound.execute(scope,permissions);throw new AssertionError("Changed native physical metadata was accepted"); }
        catch(BusinessException expected) { }
        require(fake.calls==0,"Mismatched metadata reached native business code");
    }
    public static void verifyEmpty(PartnerOrgBatchScope.Prepared scope) throws Exception {
        FakeService fake=new FakeService();NativeOrgBatchNativeService.Execution result=bound(fake).execute(scope,new String[]{"ORG"});
        require(fake.calls==0 && result.result==null && result.excluded.isEmpty(),"An empty authorized page selection invoked native business code");
    }
    public static void verifyTransactionChange(PartnerOrgBatchScope.Prepared scope,Runnable switchTransaction) throws Exception {
        FakeService fake=new FakeService();fake.onCall=switchTransaction;
        try { bound(fake).execute(scope,new String[]{"ORG"});
            throw new AssertionError("A native call that changed the transaction returned a result");
        } catch(BusinessException expected) { }
        require(fake.calls==1,"Transaction-change fixture did not reach native invocation");
    }
    private static NativeOrgBatchNativeService.Bound bound(FakeService fake) {
        return new NativeOrgBatchNativeService.Bound(NativeOrgBatchPage.CUSTOMER_FINANCE,fake,CustFinanceVO.class);
    }
    private static void require(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
    private static final class FakeService extends BatchUpdateOrgTabService<CustFinanceVO> implements NativeOrgBatchNativeService.Trace {
        int calls;boolean needReturn,wrongTable;String[] ids,targets,fields;Runnable onCall;
        FakeService() { super(NativeOrgBatchPage.CUSTOMER_FINANCE.metadataId,null,NativeOrgBatchPage.CUSTOMER_FINANCE.rootMetadataId()); }
        @Override public Class<CustFinanceVO> getVOClass() { return CustFinanceVO.class; }
        @Override public String getTableName() { return wrongTable?"other":"bd_custfinance"; }
        @Override public String getPkFieldName() { return "pk_custfinance"; }
        @Override public ErrLogReturnValue batchUpdateByPksForNewTrans(String[] selected,Map<String,Object> values,String[] permissions,
                String[] targetOrgs,String[] selectedPks,boolean returned) {
            calls++;ids=selectedPks.clone();targets=targetOrgs.clone();fields=selected.clone();needReturn=returned;
            selected[0]="changed";values.put("name","changed");permissions[0]="changed";
            if(onCall!=null) onCall.run();
            return new ErrLogReturnValue(new ErrLogResult(new ErrorLogVO(),1,new ErrorMsgVO[0]),new CustFinanceVO[0],2);
        }
        public Map<String,String> excluded() {
            Map<String,String> result=new HashMap<String,String>();result.put("P2","VALIDATION");return Collections.unmodifiableMap(result);
        }
    }
}
