package nc.impl.uapbd.qd.esign;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import nc.bs.framework.common.NCLocator;
import nc.bs.uapbd.qd.esign.audit.SapplyWorkflowBatchAspect;
import nc.bs.uapbd.qd.esign.audit.SapplyBatchApprovalProof;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.bs.uapbd.qd.esign.core.TransactionBoundaryProbe;
import nc.itf.uap.pf.IplatFormEntry;
import nc.itf.uapbd.qd.esign.ISapplyBatchTransactionService;
import nc.vo.ic.general.define.ICBillHeadVO;
import nc.vo.ic.m4455.entity.SapplyBillVO;
import nc.vo.ic.qd.esign.SapplyPfBatchCommand;
import nc.vo.ic.qd.esign.SapplyNativeAction;
import nc.vo.ic.qd.esign.SapplyPfBatchResult;
import nc.vo.pub.BusinessException;
import nc.vo.pub.workflownote.WorkflownoteVO;
import nc.vo.pubapp.pflow.PfUserObject;
import nc.vo.uap.pf.PfProcessBatchRetObject;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationResponse;
import nc.vo.uapbd.qd.esign.SapplyBatchOutcome;

/** Invokes native PF batch processing once, then reads committed unit receipts. */
final class SapplyBatchDispatch {
    private static final Logger LOG = Logger.getLogger(SapplyBatchDispatch.class.getName());

    private SapplyBatchDispatch() { }

    static OperationResponse execute(String id, SapplyPfBatchCommand command, TransactionBoundaryProbe boundary)
            throws BusinessException {
        Submission submission = Submission.check(command);
        boolean standardInventoryCallback=SapplyBatchNativeContract.require(command.getBillType());
        NCLocator locator = NCLocator.getInstance();
        return execute(id, submission, boundary, locator.lookup(ISapplyBatchTransactionService.class),
                locator.lookup(IplatFormEntry.class),standardInventoryCallback);
    }

    static OperationResponse execute(String id, Submission submission, TransactionBoundaryProbe boundary,
            ISapplyBatchTransactionService transactions, IplatFormEntry platform) throws BusinessException {
        return execute(id,submission,boundary,transactions,platform,false);
    }

    static OperationResponse execute(String id, Submission submission, TransactionBoundaryProbe boundary,
            ISapplyBatchTransactionService transactions, IplatFormEntry platform,boolean standardInventoryCallback)
            throws BusinessException {
        boundary.assertNoTransaction();
        SapplyPfBatchCommand command = submission.command;
        String token = transactions.claim_RequiresNew(id, command.getAction(), submission.pks.clone(),
                submission.approvalProof==null?null:submission.approvalProof.signedIds());
        boundary.assertNoTransaction();
        HashMap<String,Object> parameters = command.getParameters();
        SapplyWorkflowBatchAspect.RequestEnvelope envelope = new SapplyWorkflowBatchAspect.RequestEnvelope(id, token,
                submission.approvalProof, submission.submittedPks,standardInventoryCallback);
        parameters.put(SapplyWorkflowBatchAspect.REQUEST_PARAMETER, envelope);
        Exception nativeFailure = null;
        PfProcessBatchRetObject nativeResult = null;
        try {
            Object returned = platform.processBatch(command.getAction(), command.getBillType(),
                    command.getWorkflowNote(), submission.submittedBills, submission.submittedUsers, parameters);
            EsignValues.require(returned instanceof PfProcessBatchRetObject, "NC 出库申请批量未返回原生结果。");
            nativeResult = (PfProcessBatchRetObject) returned;
        } catch (BusinessException failure) {
            nativeFailure = failure;
        } catch (RuntimeException failure) {
            nativeFailure = failure;
        }
        String[] rejected = rejectedBeforeNative(envelope, submission.submittedPks);

        SapplyBatchOutcome outcome;
        try {
            boundary.assertNoTransaction();
            transactions.finish_RequiresNew(id, token, rejected, submission.skippedPks.clone());
            boundary.assertNoTransaction();
            outcome = transactions.verifyFinished_RequiresNew(id);
            boundary.assertNoTransaction();
            EsignValues.require(outcome != null && id.equals(outcome.getRequestId()),
                    "无法确认出库申请批量提交结果，请按请求号核对。");
        } catch (BusinessException failure) {
            if (nativeFailure != null && nativeFailure != failure) failure.addSuppressed(nativeFailure);
            throw failure;
        } catch (RuntimeException failure) {
            if (nativeFailure != null && nativeFailure != failure) failure.addSuppressed(nativeFailure);
            throw failure;
        }
        if (nativeFailure != null)
            LOG.log(Level.WARNING, "QD_ESIGN_SAPPLY_NATIVE_BATCH_INTERRUPTED request=" + id, nativeFailure);
        List<BusinessObjectRef> committed = new ArrayList<BusinessObjectRef>();
        for (SapplyBatchOutcome.Unit unit : outcome.getUnits()) {
            if ("SUCCESS".equals(unit.getState())) committed.add(unit.getBill());
        }
        return new OperationResponse(id, false, new SapplyPfBatchResult(outcome, nativeResult, nativeFailure != null),
                committed);
    }

    static String[] rejectedBeforeNative(SapplyWorkflowBatchAspect.RequestEnvelope envelope, String[] submitted)
            throws BusinessException {
        Set<String> recorded = envelope.getRejectedBeforeNative();
        Set<String> authorized = new HashSet<String>(java.util.Arrays.asList(submitted));
        EsignValues.require(authorized.containsAll(recorded), "出库申请批量拒绝结果超出提交范围。");
        List<String> rejected = new ArrayList<String>();
        for (String pk : submitted) if (recorded.contains(pk)) rejected.add(pk);
        return rejected.toArray(new String[rejected.size()]);
    }

    static final class Submission {
        final SapplyPfBatchCommand command;
        final String[] pks, submittedPks, skippedPks;
        final SapplyBillVO[] submittedBills;
        final PfUserObject[] submittedUsers;
        final SapplyBatchApprovalProof approvalProof;

        private Submission(SapplyPfBatchCommand command, String[] pks, String[] submittedPks, String[] skippedPks,
                SapplyBillVO[] submittedBills, PfUserObject[] submittedUsers, SapplyBatchApprovalProof approvalProof) {
            this.command = command;
            this.pks = pks;
            this.submittedPks = submittedPks;
            this.skippedPks = skippedPks;
            this.submittedBills = submittedBills;
            this.submittedUsers = submittedUsers;
            this.approvalProof = approvalProof;
        }

        static Submission check(SapplyPfBatchCommand command) throws BusinessException {
            EsignValues.require(command!=null,"出库申请批量参数缺失。");
            String action = command.getAction(), type = command.getBillType();
            SapplyBillVO[] bills = command.getBills();
            PfUserObject[] users = command.getUserObjects();
            boolean approval=SapplyNativeAction.approvalButton(action)!=null;
            EsignValues.require(("SAVE".equals(action) || "UNSAVE".equals(action) || "DELETE".equals(action) || approval)
                    && type != null && type.matches("4455(?:-[A-Za-z0-9_-]{1,95})?")
                    && bills != null && bills.length >= 2 && bills.length <= 1000
                    && (users == null || users.length == bills.length),
                    "出库申请原生批量操作、交易类型或单据范围无效。");
            String[] pks = new String[bills.length];
            Set<String> seen = new HashSet<String>();
            for (int index = 0; index < bills.length; index++) {
                ICBillHeadVO head = bills[index] == null ? null : bills[index].getHead();
                String pk = head == null ? null : SapplyWorkflowBatchUnit.key(head.getCgeneralhid());
                EsignValues.require(pk != null && pk.matches("[A-Za-z0-9_-]{1,20}") && seen.add(pk),
                        "出库申请批量包含缺少主键或重复的单据。");
                pks[index] = pk;
            }
            String[] skipped = command.getPrecheckSkippedPks();
            Set<String> skippedSet = new HashSet<String>();
            for (String pk : skipped)
                EsignValues.require(pk != null && seen.contains(pk) && skippedSet.add(pk),
                        "出库申请预校验排除范围无效或重复。");
            EsignValues.require(skippedSet.size()<bills.length,"出库申请批量没有可提交的单据。");
            List<SapplyBillVO> actual = new ArrayList<SapplyBillVO>();
            List<PfUserObject> actualUsers = new ArrayList<PfUserObject>();
            List<String> actualPks = new ArrayList<String>();
            for (int index=0;index<bills.length;index++) if(!skippedSet.contains(pks[index])) {
                actual.add(bills[index]);actualPks.add(pks[index]);
                if(users!=null)actualUsers.add(users[index]);
            }
            for (Map.Entry<String,Object> entry : command.getParameters().entrySet()) {
                String name = entry.getKey();
                Object value = entry.getValue();
                EsignValues.require(("batch".equals(name) && "batch".equals(value))
                        || ("worknote".equals(name) && value == null),
                        "出库申请批量参数包含未经核对的流程覆盖值。");
            }
            WorkflownoteVO note = command.getWorkflowNote();
            EsignValues.require(!approval || note==null || note.getClass()==WorkflownoteVO.class,
                    "出库申请批量审批确认类型尚未核对。");
            EsignValues.require(approval || note == null || note.getClass() == WorkflownoteVO.class && note.getTaskInfo() == null
                    && note.getBillid() == null && note.getCheckman() == null && note.getApproveresult() == null
                    && note.getChecknote() == null && !note.isAnyoneCanApprove()
                    && (note.getRelaProperties() == null || note.getRelaProperties().isEmpty()),
                    "出库申请批量流程确认包含需要逐单绑定的工作项或审批内容。");
            // Ordinary batch notes only select the normal PF route; approval retains its signed confirmation.
            WorkflownoteVO nativeNote = approval ? note : note == null ? null : new WorkflownoteVO();
            SapplyPfBatchCommand prepared=new SapplyPfBatchCommand(action,type,bills,users,nativeNote,
                    command.getParameters(),skipped);
            return new Submission(prepared,pks,actualPks.toArray(new String[actualPks.size()]),skipped,
                    actual.toArray(new SapplyBillVO[actual.size()]),
                    users==null?null:actualUsers.toArray(new PfUserObject[actualUsers.size()]),
                    approval?SapplyBatchApprovalProof.create(prepared):null);
        }
    }
}
