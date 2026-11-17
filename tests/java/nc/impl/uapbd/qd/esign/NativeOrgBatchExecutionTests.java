package nc.impl.uapbd.qd.esign;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import nc.bs.uapbd.qd.esign.audit.AuditCaptureScope;
import nc.bs.uapbd.qd.esign.core.NativeOrgBatchReceipt;
import nc.bs.uapbd.qd.esign.core.NativeOrgBatchSnapshotTests;
import nc.bs.uapbd.qd.esign.core.NativeOrgBatchSnapshots;
import nc.bs.uapbd.qd.esign.core.PartnerOrgBatchScope;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.bd.errorlog.ErrLogResult;
import nc.vo.bd.errorlog.ErrLogReturnValue;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;

/** Offline ordering and failure checks around the real scope, reconciliation and receipt logic. */
public final class NativeOrgBatchExecutionTests {
    private static final String TOKEN="abcdefabcdefabcdefabcdefabcdefab";
    private NativeOrgBatchExecutionTests() { }
    public static void run(PartnerOrgBatchScope.Prepared selected,OperationContext selectedContext,
            PartnerOrgBatchScope.Prepared empty,OperationContext emptyContext) throws Exception {
        NonemptyFlow nonempty=new NonemptyFlow(selected);AuditCaptureScope audit=audit(selectedContext);
        try {
            NativeOrgBatchExecution.Outcome result=NativeOrgBatchExecution.run(selectedContext,selected,TOKEN,new String[]{"ORG"},audit,nonempty);
            require("PLBNBRLWC".equals(nonempty.order.toString()) && result.evidence.getPages().size()==2
                && "PARTIAL".equals(result.units.get(0).getState()) && "UNCHANGED".equals(result.units.get(1).getState()),
                "Nonempty organization pages did not retain partial and empty archive outcomes");
            BusinessObjectRef first=selectedContext.getObjects().get(0),second=selectedContext.getObjects().get(1);
            require(audit.hasBefore(first.getClientObjectId()) && audit.hasAfter(first.getClientObjectId())
                && !audit.hasBefore(second.getClientObjectId()) && !audit.hasAfter(second.getClientObjectId()),
                "Nonempty page audit was not attached only to the changed archive");
        } finally { audit.close(); }

        Flow flow=new Flow(empty);audit=audit(emptyContext);
        try {
            NativeOrgBatchExecution.Outcome result=NativeOrgBatchExecution.run(emptyContext,empty,TOKEN,new String[]{"ORG"},audit,flow);
            require("PLBNBRLWC".equals(flow.order.toString()) && flow.writes==1 && flow.completions==1,
                "An empty authorized selection skipped or reordered transaction preflight and receipt completion");
            require(result.nativeResult==null && "SUCCESS".equals(result.receipt.getState()) && result.units.size()==2
                && "UNCHANGED".equals(result.units.get(0).getState()) && flow.evidence.getPages().isEmpty(),
                "An empty page selection fabricated a native result or lost its archive outcomes");
            for(BusinessObjectRef ref:emptyContext.getObjects())
                require(!audit.hasBefore(ref.getClientObjectId()) && !audit.hasAfter(ref.getClientObjectId()),
                    "An unchanged archive was falsely captured as a changed audit object");
        } finally { audit.close(); }

        flow=new Flow(empty);flow.failPreflight=true;audit=audit(emptyContext);
        try { NativeOrgBatchExecution.run(emptyContext,empty,TOKEN,new String[]{"ORG"},audit,flow);
            throw new AssertionError("Failed receipt preflight reached native execution"); }
        catch(BusinessException expected) { require("P".equals(flow.order.toString()) && flow.writes==0,
            "Preflight failure reached the native service or wrote a receipt"); }
        finally { audit.close(); }

        flow=new Flow(empty);flow.failBoundary=true;audit=audit(emptyContext);
        try { NativeOrgBatchExecution.run(emptyContext,empty,TOKEN,new String[]{"ORG"},audit,flow);
            throw new AssertionError("A rejected transaction boundary reached native execution"); }
        catch(BusinessException expected) { require("PLB".equals(flow.order.toString()) && flow.writes==0,
            "A rejected pre-native transaction boundary reached the native service or wrote a receipt"); }
        finally { audit.close(); }

        flow=new Flow(empty);flow.failBoundaryAt=2;audit=audit(emptyContext);
        try { NativeOrgBatchExecution.run(emptyContext,empty,TOKEN,new String[]{"ORG"},audit,flow);
            throw new AssertionError("A rejected post-native transaction boundary returned a receipt"); }
        catch(BusinessException expected) { require("PLBNB".equals(flow.order.toString()) && flow.writes==0,
            "A rejected post-native transaction boundary wrote or completed a receipt"); }
        finally { audit.close(); }

        flow=new Flow(selected);audit=audit(selectedContext);
        try { NativeOrgBatchExecution.run(selectedContext,selected,TOKEN,new String[]{"ORG"},audit,flow);
            throw new AssertionError("Missing server page VOs reached native execution"); }
        catch(BusinessException expected) { require("PL".equals(flow.order.toString()) && flow.writes==0,
            "Incomplete pre-update page data reached the native service"); }
        finally { audit.close(); }

        flow=new Flow(empty);flow.failRecapture=true;audit=audit(emptyContext);
        try { NativeOrgBatchExecution.run(emptyContext,empty,TOKEN,new String[]{"ORG"},audit,flow);
            throw new AssertionError("A failed post-update snapshot returned a receipt"); }
        catch(BusinessException expected) { require("PLBNBR".equals(flow.order.toString()) && flow.writes==0,
            "A missing after snapshot wrote or completed a receipt"); }
        finally { audit.close(); }

        OperationContext wrongNode=new OperationContext(emptyContext.getRequestId(),emptyContext.getBatchId(),"G","ORG",
            "10140CUO","客户","BatchUpdate","批改","UPDATE",null,emptyContext.getObjects(),OperationContext.OWNER_POLICY_PROTOCOL);
        flow=new Flow(empty);audit=audit(wrongNode);
        try { NativeOrgBatchExecution.run(wrongNode,empty,TOKEN,new String[]{"ORG"},audit,flow);
            throw new AssertionError("A mismatched node reached the transaction preflight"); }
        catch(BusinessException expected) { require(flow.order.length()==0,"A foreign context reached the receipt store"); }
        finally { audit.close(); }
        System.out.println("PASS NativeOrgBatchExecutionTests (nonempty partial and empty pages, ordering and fail-closed paths; no NC or Oracle)");
    }
    private static AuditCaptureScope audit(OperationContext context) {
        HashSet<String> ids=new HashSet<String>();for(BusinessObjectRef ref:context.getObjects())ids.add(ref.getClientObjectId());
        return new AuditCaptureScope(context,ids);
    }
    private static void require(boolean value,String message) { if(!value)throw new AssertionError(message); }
    private static final class NonemptyFlow implements NativeOrgBatchExecution.Flow {
        final StringBuilder order=new StringBuilder();final List<SuperVO> before,after;final NativeOrgBatchSnapshots.Capture capture;
        int loads;
        NonemptyFlow(PartnerOrgBatchScope.Prepared scope) throws Exception {
            before=NativeOrgBatchSnapshotTests.executionVOs(scope,false);after=NativeOrgBatchSnapshotTests.executionVOs(scope,true);
            capture=NativeOrgBatchSnapshotTests.recaptureExecutionChange(scope);
        }
        public void preflight(PartnerOrgBatchScope.Prepared scope,String request,String token) { order.append('P'); }
        public List<SuperVO> load(PartnerOrgBatchScope.Prepared scope) { order.append('L');return loads++==0?before:after; }
        public void requireBoundary() { order.append('B'); }
        public NativeOrgBatchNativeService.Execution invoke(PartnerOrgBatchScope.Prepared scope,String[] permissions) {
            order.append('N');Map<String,String> excluded=new HashMap<String,String>();excluded.put("P2","VALIDATION");
            return new NativeOrgBatchNativeService.Execution(new ErrLogReturnValue(new ErrLogResult(null,1,null),
                new SuperVO[]{after.get(0)},2),excluded);
        }
        public NativeOrgBatchSnapshots.Capture recapture(PartnerOrgBatchScope.Prepared scope) { order.append('R');return capture; }
        public void write(NativeOrgBatchReceipt.Evidence value) { order.append('W'); }
        public void complete() { order.append('C'); }
    }
    private static final class Flow implements NativeOrgBatchExecution.Flow {
        final StringBuilder order=new StringBuilder();final NativeOrgBatchSnapshots.Capture after;
        boolean failPreflight,failBoundary,failRecapture;int writes,completions,boundaryChecks,failBoundaryAt;NativeOrgBatchReceipt.Evidence evidence;
        Flow(PartnerOrgBatchScope.Prepared scope) throws BusinessException { after=NativeOrgBatchSnapshotTests.recaptureUnchanged(scope); }
        public void preflight(PartnerOrgBatchScope.Prepared scope,String request,String token) throws BusinessException {
            order.append('P');if(failPreflight)throw new BusinessException("preflight rejected");
        }
        public List<SuperVO> load(PartnerOrgBatchScope.Prepared scope) { order.append('L');return new ArrayList<SuperVO>(); }
        public void requireBoundary() throws BusinessException {
            order.append('B');boundaryChecks++;if(failBoundary || boundaryChecks==failBoundaryAt)
                throw new BusinessException("transaction boundary rejected");
        }
        public NativeOrgBatchNativeService.Execution invoke(PartnerOrgBatchScope.Prepared scope,String[] permissions) {
            order.append('N');return new NativeOrgBatchNativeService.Execution(null,Collections.<String,String>emptyMap());
        }
        public NativeOrgBatchSnapshots.Capture recapture(PartnerOrgBatchScope.Prepared scope) throws BusinessException {
            order.append('R');if(failRecapture)throw new BusinessException("after snapshot rejected");return after;
        }
        public void write(NativeOrgBatchReceipt.Evidence value) { order.append('W');writes++;evidence=value; }
        public void complete() { order.append('C');completions++; }
    }
}
