package nc.impl.ic.qd.esign;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import nc.vo.ic.pub.pf.ICPFParameter;
import nc.vo.ic.qd.esign.InventoryPfStateCommand;
import nc.vo.ic.qd.esign.SapplyApprovalIntent;
import nc.vo.pub.BusinessException;
import nc.vo.pub.workflownote.WorkflownoteVO;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;

/** Checks approval-note identity and rejection of unverified PF bypass parameters. */
public final class SapplyPfCommandGuardTests {
    public static void run() throws Exception {
        WorkflownoteVO note=note("A","G","O","U");note.setSenddate(new nc.vo.pub.lang.UFDateTime(12345000L));
        InventoryPfStateCommand valid=command(note,null,Collections.<String,Object>emptyMap());
        OperationContext approval=context("Approve",valid);
        SapplyPfCommandGuard.validate(approval,valid,"U");
        try{SapplyPfCommandGuard.validate(approval,valid,"U",true);
            throw new AssertionError("Batch approval accepted outside a native unit");
        }catch(BusinessException expected) { }
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();ObjectOutputStream out=new ObjectOutputStream(bytes);
        out.writeObject(valid);out.close();
        InventoryPfStateCommand roundtrip=(InventoryPfStateCommand)new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())).readObject();
        SapplyPfCommandGuard.validate(approval,roundtrip,"U");
        nc.vo.pub.lang.UFDateTime altered=new nc.vo.pub.lang.UFDateTime(12345000L);
        java.lang.reflect.Field utc=nc.vo.pub.lang.UFDateTime.class.getDeclaredField("utcTime");utc.setAccessible(true);utc.setLong(altered,12345001L);
        note.setSenddate(altered);reject(approval,valid);
        note.setSenddate(new nc.vo.pub.lang.UFDateTime(12345000L));
        note.setChecknote("changed after signing");reject(approval,valid);note.setChecknote(null);
        note.setChecknote("\uD800");OperationContext unusual=context("Approve",valid);
        note.setChecknote("\uD801");reject(unusual,valid);note.setChecknote(null);
        rejectBound("Approve",command(null,null,Collections.<String,Object>emptyMap()));
        rejectBound("Approve",command(note("OTHER","G","O","U"),null,Collections.<String,Object>emptyMap()));
        rejectBound("Approve",command(note("A","OTHER","O","U"),null,Collections.<String,Object>emptyMap()));
        rejectBound("Approve",command(note("A","G","OTHER","U"),null,Collections.<String,Object>emptyMap()));
        rejectBound("Approve",command(note("A","G","O","OTHER"),null,Collections.<String,Object>emptyMap()));
        Map<String,Object> params=new HashMap<String,Object>();params.put("silently",Boolean.TRUE);
        rejectBound("Approve",command(note,null,params));
        for(Object beside:new Object[]{Boolean.TRUE,Boolean.FALSE,"true","false"}) {
            params.clear();params.put("besideapprove",beside);
            rejectBound("Approve",command(note,null,params));
            rejectBound("UnApprove",command(note,null,params));
            InventoryPfStateCommand commit=command(null,null,params);
            OperationContext commitContext=new OperationContext("R","B","G","O","40080801","request","Commit","submit","UPDATE",null,
                    Collections.singletonList(new BusinessObjectRef("root","IC_SAPPLY","A","CODE",null)));
            SapplyPfCommandGuard.validate(commitContext,commit,"U");
        }
        params.clear();params.put("besideapprove",null);
        rejectBound("Approve",command(note,null,params));
        rejectBound("UnApprove",command(note,null,params));
        params.clear();params.put("notechecked","notechecked");
        InventoryPfStateCommand checked=command(note,null,params);
        SapplyPfCommandGuard.validate(context("Approve",checked),checked,"U");
        reject(approval,checked);
        ICPFParameter user=new ICPFParameter();user.setbBudgetCheckkFlag(true);
        rejectBound("Approve",command(note,user,Collections.<String,Object>emptyMap()));
        user=new ICPFParameter();user.setAccreditUserID("OTHER");
        rejectBound("Approve",command(note,user,Collections.<String,Object>emptyMap()));
        user=new ICPFParameter();user.setUserObject("unverified payload");
        rejectBound("Approve",command(note,user,Collections.<String,Object>emptyMap()));
        user=new ICPFParameter();user.setBillaction(nc.vo.ic.pub.define.BillOperator.Delete);
        rejectBound("Approve",command(note,user,Collections.<String,Object>emptyMap()));
        user=new ICPFParameter();user.setBillaction(nc.vo.ic.pub.define.BillOperator.Audit);
        InventoryPfStateCommand auditUser=command(note,user,Collections.<String,Object>emptyMap());
        SapplyPfCommandGuard.validate(context("Approve",auditUser),auditUser,"U");
        user=new ICPFParameter();user.setBAtpCheckFlag(false);
        rejectBound("Approve",command(note,user,Collections.<String,Object>emptyMap()));
        InventoryPfStateCommand undo=command(null,null,Collections.<String,Object>emptyMap());
        SapplyPfCommandGuard.validate(context("UnApprove",undo),undo,"U");
        workflowTaskIntent();
        System.out.println("PASS SapplyPfCommandGuardTests (approval identity and PF parameter guard; no NC session)");
    }

    private static void workflowTaskIntent() throws Exception {
        WorkflownoteVO note=note("A","G","O","U");
        if(SapplyApprovalIntent.isWorkflowAdjustment(null) || SapplyApprovalIntent.isWorkflowAdjustment(note)
                || SapplyApprovalIntent.workflowTaskInfo(note)!=null)
            throw new AssertionError("An empty approval note must not gain a lazy workflow task");
        nc.vo.wfengine.pub.WFTask task=new nc.vo.wfengine.pub.WFTask();
        task.setTaskPK("TASK1");task.setBillID("A");task.setWorknoteVO(note);
        nc.vo.pub.pf.WfTaskInfo info=new nc.vo.pub.pf.WfTaskInfo(task);
        nc.vo.pub.pf.TransitionSelectableInfo transition=new nc.vo.pub.pf.TransitionSelectableInfo();
        transition.setTransitionDefId("TRANSITION1");transition.setChoiced(true);
        nc.vo.pub.pf.AssignableInfo approvers=new nc.vo.pub.pf.AssignableInfo();
        approvers.setActivityDefId("NEXT");approvers.getAssignedOperatorPKs().add("USER1");
        info.addTransitionSelectableInfo(transition);info.addAssignableInfo(approvers);note.setTaskInfo(info);
        InventoryPfStateCommand command=command(note,null,Collections.<String,Object>emptyMap());
        OperationContext signed=context("Approve",command);
        SapplyPfCommandGuard.validate(signed,command,"U");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();ObjectOutputStream out=new ObjectOutputStream(bytes);
        out.writeObject(command);out.close();
        InventoryPfStateCommand roundtrip=(InventoryPfStateCommand)new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray())).readObject();
        SapplyPfCommandGuard.validate(signed,roundtrip,"U");
        for(boolean appoint:new boolean[]{false,true}) {
            task.setAddApprover(!appoint);task.setAppoint(appoint);
            rejectBound("Approve",command);rejectBound("UnApprove",command);
        }
        task.setAddApprover(false);task.setAppoint(false);
        task.setTaskPK("OTHER");reject(signed,command);task.setTaskPK("TASK1");
        task.setBillID("OTHER");rejectBound("Approve",command);task.setBillID("A");
        transition.setChoiced(false);reject(signed,command);transition.setChoiced(true);
        approvers.getAssignedOperatorPKs().set(0,"OTHER");reject(signed,command);
        approvers.getAssignedOperatorPKs().set(0,"USER1");
        task.setWorknoteVO(new WorkflownoteVO());reject(signed,command);
    }
    private static void reject(OperationContext context,InventoryPfStateCommand command) throws Exception {
        try{SapplyPfCommandGuard.validate(context,command,"U");throw new AssertionError("Invalid PF command accepted");}
        catch(BusinessException expected) { }
    }
    private static void rejectBound(String button,InventoryPfStateCommand command) throws Exception {reject(context(button,command),command);}
    private static InventoryPfStateCommand command(WorkflownoteVO note,ICPFParameter user,Map<String,Object> values) {
        return new InventoryPfStateCommand("4455",null,user,note,values);
    }
    private static WorkflownoteVO note(String bill,String group,String org,String user) {
        WorkflownoteVO value=new WorkflownoteVO();value.setBillid(bill);value.setPk_group(group);value.setPk_org(org);value.setCheckman(user);return value;
    }
    private static OperationContext context(String button,InventoryPfStateCommand command) throws BusinessException {
        return new OperationContext("R","B","G","O","40080801","request",button,button,"UPDATE",null,
                Collections.singletonList(new BusinessObjectRef(SapplyApprovalIntent.clientObjectId(button,command),"IC_SAPPLY","A","CODE",null)));
    }
}
