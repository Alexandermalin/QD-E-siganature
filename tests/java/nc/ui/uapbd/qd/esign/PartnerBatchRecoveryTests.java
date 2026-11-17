package nc.ui.uapbd.qd.esign;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.SwingUtilities;
import nc.vo.bd.cust.CustomerVO;
import nc.vo.bd.errorlog.ErrLogResult;
import nc.vo.bd.errorlog.ErrLogReturnValue;
import nc.vo.bd.errorlog.ErrorLogVO;
import nc.vo.bd.errorlog.ErrorMsgVO;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.ArchiveBatchStatus;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.PartnerBatchDraft;
import nc.vo.uapbd.qd.esign.PartnerOrgBatchPlan;

/** A lost business reply can only be described using a matching, internally consistent persisted receipt. */
public final class PartnerBatchRecoveryTests {
    private PartnerBatchRecoveryTests() { }
    public static void run() throws Exception {
        final OperationContext request=request("REQ","10140SUG","BD_SUPPLIER","A","B");
        final ArchiveBatchStatus partial=status("REQ","PARTIAL",unit("a","A","A-code","B","SUCCESS"),
            unit("b","B","B-code","B","FAILED"));
        final int[] reads={0};
        QdPartnerBatchRecovery.Summary summary=QdPartnerBatchRecovery.read(request,new QdPartnerBatchRecovery.Reader() {
            public ArchiveBatchStatus read(String id) { require("REQ".equals(id),"Wrong request queried");reads[0]++;return partial; }
        });
        require(reads[0]==1 && summary.terminal() && summary.succeeded==1 && summary.failed==1
            && summary.describe().contains("B-code") && summary.describe().contains("不要直接重试整批"),
            "A partial outcome lost its confirmed and failed details");
        reject(request,status("OTHER","PARTIAL",unit("a","A","A-code","B","SUCCESS"),unit("b","B","B-code","B","FAILED")));
        reject(request,status("REQ","SUCCESS",unit("a","A","A-code","B","SUCCESS"),unit("b","B","B-code","B","FAILED")));
        reject(request,status("REQ","PARTIAL",unit("a","A","A-code","B","SUCCESS"),unit("b","B","B-code","OTHER","FAILED")));
        reject(request,status("REQ","PARTIAL",unit("a","A","A-code","B","SUCCESS"),unit("b","OTHER","B-code","B","FAILED")));
        reject(request,status("REQ","PARTIAL",unit("a","A","A-code","B","SUCCESS"),unit("b","B","OTHER","B","FAILED")));
        reject(request,status("REQ","PARTIAL",unit("a","A","A-code","B","SUCCESS"),unit("a","B","B-code","B","FAILED")));
        reject(request,status("REQ","PARTIAL",unit("a","A","A-code","B","SUCCESS")));
        reject(request,status("REQ","RUNNING",unit("a","A","A-code","B","SUCCESS"),unit("b","B","B-code","B","PENDING")));
        reject(request,status("REQ","UNKNOWN",unit("a","A","A-code","B","SUCCESS"),unit("b","B","B-code","B","UNKNOWN")));
        reject(request,status("REQ","NEW_STATE",unit("a","A","A-code","B","PENDING"),unit("b","B","B-code","B","PENDING")));
        QdPartnerBatchRecovery.Summary unknown=QdPartnerBatchRecovery.verify(request,status("REQ","UNKNOWN",
            unit("a","A","A-code","B","UNKNOWN"),unit("b","B","B-code","B","PENDING")));
        require(!unknown.terminal() && unknown.unknown==1 && unknown.pending==1 && unknown.describe().contains("结果未知"),
            "An uncertain outcome was described as committed or rolled back");
        QdPartnerBatchRecovery.Summary failed=QdPartnerBatchRecovery.verify(request,status("REQ","FAILED",
            unit("a","A","A-code","B","PENDING"),unit("b","B","B-code","B","PENDING")));
        require(failed.terminal() && failed.failed==0 && failed.pending==2,"A pre-dispatch failure was invented as per-row failure");
        QdPartnerBatchChunks.Attempt refreshed=new QdPartnerBatchChunks.Attempt();refreshed.dispatched("REQ");
        refreshed.recovered(unknown);refreshed.recovered(summary);
        try { refreshed.recovered(unknown);throw new AssertionError("A terminal receipt was replaced by an uncertain state"); }
        catch(BusinessException expected) { }
        completeReadOnlyDetails();
        organizationDetails();
        preparedOrganizationDetails();
        recoveredChunkStopsRemainingWork();
        System.out.println("PASS PartnerBatchRecoveryTests (lost replies, terminal and uncertain receipts; no NC or Oracle)");
    }

    private static void preparedOrganizationDetails() throws Exception {
        String hash="0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        String first="orgbulk:"+hash+":A",second="orgbulk:"+hash+":B";
        List<BusinessObjectRef> refs=Arrays.asList(new BusinessObjectRef(first,"BD_CUSTOMER","A","A-code",null),
            new BusinessObjectRef(second,"BD_CUSTOMER","B","B-code",null));
        PartnerOrgBatchPlan.Page one=new PartnerOrgBatchPlan.Page("P1","A","T");
        PartnerOrgBatchPlan.Page two=new PartnerOrgBatchPlan.Page("P2","A","T");
        PartnerBatchDraft draft=new PartnerBatchDraft("TAB","finance_info","field",new String[]{"field"},
            java.util.Collections.<String,Object>singletonMap("field","x"),new String[]{"T","EMPTY"},
            new PartnerOrgBatchPlan.Page[]{one,two});
        OperationContext request=new OperationContext("ORGREQ","ORGREQ","G","B","10140CUG","客户","BatchUpdate","批改","UPDATE",null,
            refs,OperationContext.OWNER_POLICY_PROTOCOL,null,null,null,draft);
        List<ArchiveBatchStatus.Unit> units=Arrays.asList(unit(first,"A","A-code","B","PARTIAL"),
            unit(second,"B","B-code","B","UNCHANGED"));
        List<ArchiveBatchStatus.PageHeader> headers=Arrays.asList(new ArchiveBatchStatus.PageHeader(first,2),
            new ArchiveBatchStatus.PageHeader(second,0));
        List<ArchiveBatchStatus.Page> pages=Arrays.asList(new ArchiveBatchStatus.Page(first,"P1","T","SUCCESS",null),
            new ArchiveBatchStatus.Page(first,"P2","T","FAILED","VALIDATION"));
        List<PartnerOrgBatchPlan.Page> prepared=Arrays.asList(two,one);
        ArchiveBatchStatus.Organization complete=new ArchiveBatchStatus.Organization("finance_info",Arrays.asList("EMPTY","T"),true,
            headers,pages,prepared);
        final QdPartnerBatchRecovery.Summary summary=QdPartnerBatchRecovery.verify(request,new ArchiveBatchStatus("ORGREQ","PARTIAL",units,complete));
        require(summary.partial==1,"Matching persisted page identities were not recovered");
        SwingUtilities.invokeAndWait(new Runnable() { public void run() {
            QdPartnerBatchRecoveryPanel panel=new QdPartnerBatchRecoveryPanel(summary);
            require(panel.preparedTable()!=null && panel.preparedTable().getRowCount()==2
                && "P2".equals(panel.preparedTable().getValueAt(0,3)) && !panel.preparedTable().isCellEditable(0,0),
                "Prepared page identities were not displayed read-only");
        }});
        reject(request,new ArchiveBatchStatus("ORGREQ","PARTIAL",units,new ArchiveBatchStatus.Organization("finance_info",
            Arrays.asList("EMPTY","T"),true,headers,pages)));
        reject(request,new ArchiveBatchStatus("ORGREQ","PARTIAL",units,new ArchiveBatchStatus.Organization("finance_info",
            Arrays.asList("EMPTY","T"),true,headers,pages,Arrays.asList(one))));
        reject(request,new ArchiveBatchStatus("ORGREQ","PARTIAL",units,new ArchiveBatchStatus.Organization("finance_info",
            Arrays.asList("EMPTY","T"),true,headers,pages,Arrays.asList(one,new PartnerOrgBatchPlan.Page("P2","B","T")))));
        reject(request,new ArchiveBatchStatus("ORGREQ","PARTIAL",units,new ArchiveBatchStatus.Organization("finance_info",
            Arrays.asList("EMPTY","T"),true,headers,Arrays.asList(pages.get(0),
                new ArchiveBatchStatus.Page(first,"P3","T","FAILED","VALIDATION")),prepared)));
        List<ArchiveBatchStatus.Unit> pending=Arrays.asList(unit(first,"A","A-code","B","PENDING"),
            unit(second,"B","B-code","B","PENDING"));
        ArchiveBatchStatus.Organization waiting=new ArchiveBatchStatus.Organization("finance_info",Arrays.asList("EMPTY","T"),false,
            java.util.Collections.<ArchiveBatchStatus.PageHeader>emptyList(),java.util.Collections.<ArchiveBatchStatus.Page>emptyList(),prepared);
        require(QdPartnerBatchRecovery.verify(request,new ArchiveBatchStatus("ORGREQ","PENDING",pending,waiting)).pending==2,
            "Prepared selection was unavailable before the business result");
    }

    private static void organizationDetails() throws Exception {
        String hash="0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        String first="orgbulk:"+hash+":A",second="orgbulk:"+hash+":B";
        List<BusinessObjectRef> refs=Arrays.asList(new BusinessObjectRef(first,"BD_CUSTOMER","A","A-code",null),
            new BusinessObjectRef(second,"BD_CUSTOMER","B","B-code",null));
        PartnerBatchDraft draft=new PartnerBatchDraft("TAB","finance_info","field",new String[]{"field"},
            java.util.Collections.<String,Object>singletonMap("field","x"),new String[]{"T","EMPTY"});
        OperationContext request=new OperationContext("ORGREQ","ORGREQ","G","B","10140CUG","客户","BatchUpdate","批改","UPDATE",null,
            refs,OperationContext.OWNER_POLICY_PROTOCOL,null,null,null,draft);
        List<ArchiveBatchStatus.Unit> units=Arrays.asList(unit(first,"A","A-code","B","PARTIAL"),
            unit(second,"B","B-code","B","UNCHANGED"));
        List<ArchiveBatchStatus.PageHeader> headers=Arrays.asList(new ArchiveBatchStatus.PageHeader(first,2),
            new ArchiveBatchStatus.PageHeader(second,0));
        List<ArchiveBatchStatus.Page> pages=Arrays.asList(new ArchiveBatchStatus.Page(first,"P1","T","SUCCESS",null),
            new ArchiveBatchStatus.Page(first,"P2","T","FAILED","VALIDATION"));
        ArchiveBatchStatus.Organization details=new ArchiveBatchStatus.Organization("finance_info",Arrays.asList("EMPTY","T"),true,headers,pages);
        final QdPartnerBatchRecovery.Summary summary=QdPartnerBatchRecovery.verify(request,new ArchiveBatchStatus("ORGREQ","PARTIAL",units,details));
        require(summary.partial==1 && summary.succeeded==0 && summary.failed==0 && summary.describe().contains("部分成功 1"),
            "A mixed outcome inside one archive was miscounted");
        java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
        java.io.ObjectOutputStream output=new java.io.ObjectOutputStream(bytes);
        output.writeObject(new ArchiveBatchStatus("ORGREQ","PARTIAL",units,details));output.close();
        java.io.ObjectInputStream input=new java.io.ObjectInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray()));
        ArchiveBatchStatus restored=(ArchiveBatchStatus)input.readObject();input.close();
        require(QdPartnerBatchRecovery.verify(request,restored).partial==1,"Serialized page results lost their scope or partial state");
        SwingUtilities.invokeAndWait(new Runnable() { public void run() {
            QdPartnerBatchRecoveryPanel panel=new QdPartnerBatchRecoveryPanel(summary);
            require("部分成功".equals(panel.table().getValueAt(0,2)) && panel.pageTable().getRowCount()==2
                && "原生校验未通过".equals(panel.pageTable().getValueAt(1,4)) && panel.targetTable().getRowCount()==2,
                "Organization recovery hid page failures or a target without pages");
        }});
        reject(request,new ArchiveBatchStatus("ORGREQ","PARTIAL",units));
        reject(request,new ArchiveBatchStatus("ORGREQ","PARTIAL",units,new ArchiveBatchStatus.Organization("finance_info",
            Arrays.asList("T"),true,headers,pages)));
        reject(request,new ArchiveBatchStatus("ORGREQ","PARTIAL",units,new ArchiveBatchStatus.Organization("finance_info",
            Arrays.asList("EMPTY","T"),true,headers,pages.subList(0,1))));
        reject(request,new ArchiveBatchStatus("ORGREQ","PARTIAL",units,new ArchiveBatchStatus.Organization("finance_info",
            Arrays.asList("EMPTY","T"),false,java.util.Collections.<ArchiveBatchStatus.PageHeader>emptyList(),
            java.util.Collections.<ArchiveBatchStatus.Page>emptyList())));
        reject(request,new ArchiveBatchStatus("ORGREQ","PARTIAL",units,new ArchiveBatchStatus.Organization("finance_info",
            Arrays.asList("EMPTY","T"),true,headers,Arrays.asList(pages.get(0),
                new ArchiveBatchStatus.Page(first,"P2","OTHER","FAILED","VALIDATION")))));
        List<ArchiveBatchStatus.Unit> unchanged=Arrays.asList(unit(first,"A","A-code","B","UNCHANGED"),
            unit(second,"B","B-code","B","UNCHANGED"));
        ArchiveBatchStatus.Organization noPages=new ArchiveBatchStatus.Organization("finance_info",Arrays.asList("EMPTY","T"),true,
            Arrays.asList(new ArchiveBatchStatus.PageHeader(first,0),new ArchiveBatchStatus.PageHeader(second,0)),
            java.util.Collections.<ArchiveBatchStatus.Page>emptyList());
        require(QdPartnerBatchRecovery.verify(request,new ArchiveBatchStatus("ORGREQ","SUCCESS",unchanged,noPages)).unchanged==2,
            "A confirmed batch with zero matching pages was confused with a missing result");
    }

    private static void completeReadOnlyDetails() throws Exception {
        List<BusinessObjectRef> refs=new ArrayList<BusinessObjectRef>();List<ArchiveBatchStatus.Unit> units=new ArrayList<ArchiveBatchStatus.Unit>();
        for(int i=0;i<25;i++) {
            String pk="P"+i,client="I"+i,code=pk+"-code";
            refs.add(new BusinessObjectRef(client,"BD_CUSTOMER",pk,code,null));
            units.add(new ArchiveBatchStatus.Unit(client,pk,code,"OWNER","B",i==0?"SUCCESS":"FAILED",i==0?null:"NATIVE_REJECTED"));
        }
        OperationContext request=new OperationContext("DETAIL","DETAIL","G","B","10140CUG","客户","BatchUpdate","批改","UPDATE",null,
            refs,OperationContext.OWNER_POLICY_PROTOCOL);
        final QdPartnerBatchRecovery.Summary summary=QdPartnerBatchRecovery.verify(request,new ArchiveBatchStatus("DETAIL","PARTIAL",units));
        SwingUtilities.invokeAndWait(new Runnable() { public void run() {
            QdPartnerBatchRecoveryPanel panel=new QdPartnerBatchRecoveryPanel(summary);
            require(summary.examples.size()==10 && panel.table().getRowCount()==25
                && "P24-code".equals(panel.table().getValueAt(24,0)) && "失败".equals(panel.table().getValueAt(24,2))
                && "未通过原生校验".equals(panel.table().getValueAt(24,5))
                && !panel.table().isCellEditable(24,0),"Full recovery details were truncated or editable");
        }});
    }

    private static void recoveredChunkStopsRemainingWork() throws Exception {
        final int[] dispatches={0};
        try {
            QdPartnerBatchChunks.execute(new String[]{"A","B","C","D","E"},2,new QdPartnerBatchChunks.Operation() {
                public void execute(String[] selected,int number,int total,QdPartnerBatchChunks.Attempt attempt) throws BusinessException {
                    dispatches[0]++;attempt.dispatched("REQ"+number);
                    if(number==1) { attempt.confirmed(success(selected));return; }
                    OperationContext request=request("REQ2","10140CUG","BD_CUSTOMER","C","D");
                    ArchiveBatchStatus status=status("REQ2","PARTIAL",unit("a","C","C-code","B","SUCCESS"),
                        unit("b","D","D-code","B","FAILED"));
                    attempt.recovered(QdPartnerBatchRecovery.verify(request,status));
                    throw new BusinessException("lost reply");
                }
            });
            throw new AssertionError("Recovered reply loss unexpectedly continued to another batch");
        } catch(BusinessException expected) {
            String message=expected.getMessage();
            require(dispatches[0]==2 && message.contains("已确认结果 2 批、4 个档案") && message.contains("REQ1")
                && message.contains("REQ2") && message.contains("部分完成") && message.contains("D-code")
                && message.contains("后续 1 个档案未发起"),"Recovered partial result or remaining work was misreported");
        }
    }
    private static OperationContext request(String id,String node,String type,String first,String second) {
        List<BusinessObjectRef> refs=new ArrayList<BusinessObjectRef>();
        refs.add(new BusinessObjectRef("a",type,first,first+"-code",null));
        refs.add(new BusinessObjectRef("b",type,second,second+"-code",null));
        return new OperationContext(id,id,"G","B",node,"档案","BatchUpdate","快速批改","UPDATE",null,refs,
            OperationContext.OWNER_POLICY_PROTOCOL);
    }
    private static ArchiveBatchStatus status(String id,String state,ArchiveBatchStatus.Unit... units) {
        return new ArchiveBatchStatus(id,state,Arrays.asList(units));
    }
    private static ArchiveBatchStatus.Unit unit(String client,String pk,String code,String target,String state) {
        return new ArchiveBatchStatus.Unit(client,pk,code,"OWNER",target,state,null);
    }
    private static ErrLogReturnValue success(String[] selected) {
        CustomerVO[] rows=new CustomerVO[selected.length];
        for(int i=0;i<selected.length;i++) { rows[i]=new CustomerVO();rows[i].setPk_customer(selected[i]); }
        return new ErrLogReturnValue(new ErrLogResult(new ErrorLogVO(),0,new ErrorMsgVO[0]),rows,selected.length);
    }
    private static void reject(OperationContext request,ArchiveBatchStatus status) throws Exception {
        try { QdPartnerBatchRecovery.verify(request,status);throw new AssertionError("Invalid recovered status was accepted"); }
        catch(BusinessException expected) { }
    }
    private static void require(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
}
