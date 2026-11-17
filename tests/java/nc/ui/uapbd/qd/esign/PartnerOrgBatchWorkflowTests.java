package nc.ui.uapbd.qd.esign;

import java.util.List;
import nc.vo.bd.errorlog.ErrLogResult;
import nc.vo.bd.errorlog.ErrLogReturnValue;
import nc.vo.bd.errorlog.ErrorMsgVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.PartnerOrgBatchPlan;

/** Complete read-only planning, consent, independent dispatch and aggregation sequence with fake boundaries. */
public final class PartnerOrgBatchWorkflowTests {
    private PartnerOrgBatchWorkflowTests() { }
    public static void run(final OperationContext context,final PartnerOrgBatchPlan plan) throws Exception {
        final int[] planned={0},approved={0},dispatched={0};final boolean[] accept={false},changedIdentity={false};
        QdPartnerOrgBatchPlan.Reader reader=new QdPartnerOrgBatchPlan.Reader() {
            public PartnerOrgBatchPlan read(OperationContext actual) { planned[0]++;return plan; }
        };
        QdPartnerOrgBatchWorkflow.Hooks hooks=new QdPartnerOrgBatchWorkflow.Hooks() {
            public void requireCurrent() throws BusinessException {
                if(changedIdentity[0] && dispatched[0]==3)throw new BusinessException("identity changed");
            }
            public boolean approve(int roots,long pages,int slices) {
                approved[0]++;require(roots==3 && pages==3 && slices==3,"Consent omitted complete plan counts");return accept[0];
            }
            public void execute(QdPartnerOrgBatchSlices.Slice slice,int number,int total,QdPartnerOrgBatchProgress.Attempt attempt)
                    throws BusinessException {
                dispatched[0]++;OperationContext request=PartnerOrgBatchProgressTests.request(slice,number);attempt.dispatched(request);
                attempt.confirmed(PartnerOrgBatchProgressTests.reply(request,false),PartnerOrgBatchProgressTests.status(request,false));
            }
            public ErrLogResult write(List<String> messages) { throw new AssertionError("Unchanged pages wrote an error log"); }
            public String[] messageIds(String id) { throw new AssertionError("Unexpected native log lookup"); }
            public List<ErrorMsgVO> messages(String[] ids) { throw new AssertionError("Unexpected native log lookup"); }
        };
        try { QdPartnerOrgBatchWorkflow.executeForTest(context,reader,hooks,SuperVO.class);
            throw new AssertionError("A cancelled plan was dispatched"); }
        catch(BusinessException expected) { require(planned[0]==1 && approved[0]==1 && dispatched[0]==0,
            "Cancellation did not stop every business request"); }
        accept[0]=true;ErrLogReturnValue result=QdPartnerOrgBatchWorkflow.executeForTest(context,reader,hooks,SuperVO.class);
        require(planned[0]==2 && approved[0]==2 && dispatched[0]==3 && result.getTotalNum()==3
            && ((SuperVO[])result.getReturnValue()).length==0,"The verified slices did not reach one combined native result");
        dispatched[0]=0;changedIdentity[0]=true;
        try { QdPartnerOrgBatchWorkflow.executeForTest(context,reader,hooks,SuperVO.class);
            throw new AssertionError("An identity change after committed batches returned a success"); }
        catch(BusinessException expected) { require(dispatched[0]==3
            && expected.getMessage().contains("00000000000000000000000000000001")
            && expected.getMessage().contains("00000000000000000000000000000003"),
            "A post-commit identity change lost committed request numbers"); }
        changedIdentity[0]=false;
        try { QdPartnerOrgBatchWorkflow.executeForTest(context,new QdPartnerOrgBatchPlan.Reader() {
                public PartnerOrgBatchPlan read(OperationContext actual) throws BusinessException { throw new BusinessException("planning failure"); }
            },hooks,SuperVO.class);throw new AssertionError("A failed plan was dispatched"); }
        catch(BusinessException expected) { require(dispatched[0]==3 && approved[0]==3,"Planning failure reached consent or dispatch"); }
        require(QdPartnerOrgBatchWorkflow.pageType("10140CUG","finance_info")==nc.vo.bd.cust.finance.CustFinanceVO.class
            && QdPartnerOrgBatchWorkflow.pageType("10140SUO","stock_info")==nc.vo.bd.supplier.stock.SupStockVO.class
            && QdPartnerOrgBatchWorkflow.pageType("10140MAO","pfcc_info")==nc.vo.bd.material.pfcc.MaterialPFCCVO.class,
            "A fixed organization page resolved to the wrong NC native result type");
        for(nc.bs.uapbd.qd.esign.core.NativeOrgBatchPage page:nc.bs.uapbd.qd.esign.core.NativeOrgBatchPage.values()) {
            String group="BD_CUSTOMER".equals(page.domain)?"10140CUG":"BD_SUPPLIER".equals(page.domain)?"10140SUG":"10140MAG";
            String org="BD_CUSTOMER".equals(page.domain)?"10140CUO":"BD_SUPPLIER".equals(page.domain)?"10140SUO":"10140MAO";
            require(QdPartnerOrgBatchWorkflow.pageType(group,page.tabCode)==page.nativeType()
                && QdPartnerOrgBatchWorkflow.pageType(org,page.tabCode)==page.nativeType(),
                "Client and server disagree on a fixed organization page VO type: "+page.tabCode);
        }
        try { QdPartnerOrgBatchWorkflow.pageType("10140CUG","pfcc_info");throw new AssertionError("A foreign page type was accepted"); }
        catch(BusinessException expected) { }
        System.out.println("PASS PartnerOrgBatchWorkflowTests (planning, consent, dispatch and aggregation; no NC session)");
    }
    private static void require(boolean value,String message) { if(!value)throw new AssertionError(message); }
}
