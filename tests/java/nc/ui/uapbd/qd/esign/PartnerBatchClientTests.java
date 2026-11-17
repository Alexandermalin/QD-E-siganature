package nc.ui.uapbd.qd.esign;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import nc.ui.uif2.factory.UIF2BeanFactory;
import nc.vo.bd.cust.CustomerVO;
import nc.vo.bd.material.MaterialVO;
import nc.vo.bd.errorlog.ErrLogResult;
import nc.vo.bd.errorlog.ErrLogReturnValue;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.uapbd.qd.esign.ArchiveBatchResult;
import nc.vo.uapbd.qd.esign.ArchiveBatchUpdateResult;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.PartnerBatchDraft;
import org.springframework.beans.factory.xml.XmlBeanFactory;
import org.springframework.core.io.ClassPathResource;

/** A partially rejected native batch must not be displayed as an unqualified success. */
public final class PartnerBatchClientTests {
    private PartnerBatchClientTests() { }
    public static void run() throws Exception {
        BusinessObjectRef a=new BusinessObjectRef("a","BD_CUSTOMER","A","A-code",null),b=new BusinessObjectRef("b","BD_CUSTOMER","B","B-code",null);
        OperationContext request=new OperationContext("R","R","G","ORG","10140CUG","客户","BatchUpdate","批改","UPDATE",null,
            Arrays.asList(a,b),OperationContext.OWNER_POLICY_PROTOCOL);
        CustomerVO saved=new CustomerVO();saved.setPk_customer("A");
        ErrLogReturnValue nativeResult=new ErrLogReturnValue(new ErrLogResult(null,0,null),new SuperVO[]{saved},2);
        ArchiveBatchUpdateResult partial=new ArchiveBatchUpdateResult(nativeResult,Arrays.asList(unit("a","A","SUCCESS"),unit("b","B","FAILED")));
        ErrLogReturnValue shown=QdPartnerBatchDispatcher.validateResult(request,partial);
        require(shown.getErrLogResult().getErrorMessagegNum()==1 && shown.getErrLogResult().getErrorMsgs().length==1
            && shown.getErrLogResult().getErrorMsgs()[0].getErrormsg().contains("B-code"),"Filtered failure was displayed as native success");
        ArchiveBatchUpdateResult unchanged=new ArchiveBatchUpdateResult(nativeResult,Arrays.asList(unit("a","A","SUCCESS"),unit("b","B","UNCHANGED")));
        require(QdPartnerBatchDispatcher.validateResult(request,unchanged).getErrLogResult().getErrorMessagegNum()==0,
            "Unchanged row introduced a false error");
        reject(request,new ArchiveBatchUpdateResult(nativeResult,Arrays.asList(unit("a","A","SUCCESS"),unit("b","C","FAILED"))));
        reject(request,new ArchiveBatchUpdateResult(nativeResult,Arrays.asList(unit("a","A","SUCCESS"),unit("b","B","UNKNOWN"))));
        reject(request,new ArchiveBatchUpdateResult(new ErrLogReturnValue(new ErrLogResult(null,0,null),null,2),unchanged.getUnits()));
        reject(request,new ArchiveBatchUpdateResult(new ErrLogReturnValue(new ErrLogResult(null,1,null),new SuperVO[]{saved},2),unchanged.getUnits()));
        Map<String,Object> values=new HashMap<String,Object>();StringBuilder longValue=new StringBuilder();
        for(int i=0;i<1500;i++) longValue.append('X');values.put("name",longValue.toString());values.put("related",null);
        String summary=BatchSignatureSummary.format(new PartnerBatchDraft("TAB","baseinfo","name",new String[]{"name"},values));
        require(summary.contains(longValue.toString()) && summary.contains("related：（空）"),"Signed batch values were omitted or truncated");
        summary=BatchSignatureSummary.format(new PartnerBatchDraft("TAB","finance_info","name",new String[]{"name"},values,
            new String[]{"ORG1","ORG2"}));
        require(summary.contains("目标组织主键：ORG1、ORG2") && summary.contains(longValue.toString()),
            "Organization targets were omitted from the signed batch summary");
        summary=BatchSignatureSummary.format(new PartnerBatchDraft("TAB","finance_info","name",new String[]{"name"},values,
            new String[]{"ORG1"},new nc.vo.uapbd.qd.esign.PartnerOrgBatchPlan.Page[]{
                new nc.vo.uapbd.qd.esign.PartnerOrgBatchPlan.Page("P1","A","ORG1"),
                new nc.vo.uapbd.qd.esign.PartnerOrgBatchPlan.Page("P2","A","ORG1")}));
        require(summary.contains("本批固定页签行数：2") && summary.contains("计划后新增的行不包含在本批"),
            "The signed batch summary omitted its fixed page-slice semantics");
        config("QdCustomerBatchWizardCandidate");config("QdSupplierBatchWizardCandidate");config("QdMaterialBatchWizardCandidate");
        BusinessObjectRef materialRef=new BusinessObjectRef("m","BD_MATERIAL","M","M-code",null);
        OperationContext materialRequest=new OperationContext("R","R","G","ORG","10140MAO","物料","BatchUpdate","批改","UPDATE",null,
            Arrays.asList(materialRef),OperationContext.OWNER_POLICY_PROTOCOL);
        MaterialVO material=new MaterialVO();material.setPk_material("M");
        ArchiveBatchUpdateResult materialResult=new ArchiveBatchUpdateResult(
            new ErrLogReturnValue(new ErrLogResult(null,0,null),new SuperVO[]{material},1),Arrays.asList(unit("m","M","SUCCESS")));
        require(QdPartnerBatchDispatcher.validateResult(materialRequest,materialResult).getTotalNum()==1,
            "Material batch success was not correlated with its native material object");
        CustomerVO wrongType=new CustomerVO();wrongType.setPk_customer("M");
        reject(materialRequest,new ArchiveBatchUpdateResult(
            new ErrLogReturnValue(new ErrLogResult(null,0,null),new SuperVO[]{wrongType},1),Arrays.asList(unit("m","M","SUCCESS"))));
        QdPartnerBatchDispatcher.BatchAnchor anchor=new QdPartnerBatchDispatcher.BatchAnchor("G","10140CUG","ORG","USER");
        PartnerBatchDraft orgDraft=new PartnerBatchDraft("TAB","finance_info","name",new String[]{"name"},
            java.util.Collections.<String,Object>singletonMap("name","v"),new String[]{"T"});
        OperationContext planned=QdPartnerBatchDispatcher.batchContext(anchor,orgDraft,new String[]{"A","B"},Integer.MAX_VALUE);
        require(planned.getObjects().size()==2 && planned.getPartnerBatchDraft()==orgDraft && "BD_CUSTOMER".equals(planned.getObjects().get(0).getObjectType())
            && "ORG".equals(planned.getOrgId()),"The organization planning request changed selected roots or operation identity");
        try { QdPartnerBatchDispatcher.batchContext(anchor,orgDraft,new String[]{"A","A"},Integer.MAX_VALUE);
            throw new AssertionError("Duplicate archive selections reached organization planning"); }
        catch(BusinessException expected) { }
        try { QdPartnerBatchDispatcher.batchContext(anchor,orgDraft,new String[]{"A","B"},1);
            throw new AssertionError("A signing slice exceeded the requested object limit"); }
        catch(BusinessException expected) { }
        interruptedWorker();
        System.out.println("PASS PartnerBatchClientTests (native batch receipts and filtered errors; no NC session)");
    }
    private static void config(String file) {
        XmlBeanFactory beans=new XmlBeanFactory(new ClassPathResource("nc/ui/uapbd/qd/esign/"+file+".xml",UIF2BeanFactory.class.getClassLoader()));
        require(QdPartnerBatchWizardAction.class.getName().equals(beans.getBeanDefinition("batchUpdateWizardAction").getBeanClassName()),
            "Candidate quick-batch wizard was not wired to the native bean name");
    }
    private static void interruptedWorker() throws Exception {
        final AtomicReference<Throwable> failure=new AtomicReference<Throwable>();
        Thread worker=new Thread(new Runnable() { public void run() {
            try {
                Thread.currentThread().interrupt();
                String result=QdPartnerBatchDispatcher.onEdt(new Callable<String>() { public String call() {
                    require(SwingUtilities.isEventDispatchThread(),"Signing callback ran outside the Swing event thread");return "done";
                }});
                require("done".equals(result) && Thread.currentThread().isInterrupted(),"Interrupted worker abandoned or lost the queued EDT callback");
            } catch(Throwable error) { failure.set(error); }
        }});
        worker.start();worker.join(5000L);
        require(!worker.isAlive(),"Interrupted worker did not finish its EDT handoff");
        if(failure.get()!=null) throw new AssertionError("EDT handoff failed",failure.get());
    }
    private static ArchiveBatchResult.Unit unit(String id,String pk,String state) { return new ArchiveBatchResult.Unit(id,pk,state); }
    private static void reject(OperationContext request,ArchiveBatchUpdateResult result) throws Exception {
        try { QdPartnerBatchDispatcher.validateResult(request,result);throw new AssertionError("Invalid fast-batch result accepted"); }
        catch(BusinessException expected) { }
    }
    private static void require(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
}
