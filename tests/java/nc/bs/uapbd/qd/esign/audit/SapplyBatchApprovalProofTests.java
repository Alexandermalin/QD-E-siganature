package nc.bs.uapbd.qd.esign.audit;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import nc.vo.ic.m4455.entity.SapplyBillHeadVO;
import nc.vo.ic.m4455.entity.SapplyBillVO;
import nc.vo.ic.pub.pf.ICPFParameter;
import nc.vo.ic.qd.esign.SapplyApprovalIntent;
import nc.vo.ic.qd.esign.SapplyPfBatchCommand;
import nc.vo.pub.BusinessException;
import nc.vo.pub.IAttributeMeta;
import nc.vo.pub.ISuperVO;
import nc.vo.pub.IVOMeta;
import nc.vo.pub.pf.TransitionSelectableInfo;
import nc.vo.pub.pf.WfTaskInfo;
import nc.vo.pub.workflownote.WorkflownoteVO;
import nc.vo.pubapp.pattern.model.meta.entity.bill.IBillMeta;
import nc.vo.pubapp.pflow.PfUserObject;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;

/** Exercises the signed batch payload and NC's derived per-bill approval note. */
public final class SapplyBatchApprovalProofTests {
    private static final IBillMeta META=new IBillMeta(){
        public Map<String,Object> getBusinessAttribute(){return Collections.emptyMap();}
        public IAttributeMeta[] getChildForeignKeys(){return new IAttributeMeta[0];}
        public IVOMeta[] getChildren(){return new IVOMeta[0];}
        public String getComponentName(){return "TEST";}
        public IVOMeta getParent(){return null;}
        public Class<? extends ISuperVO> getVOClass(IVOMeta meta){throw new UnsupportedOperationException();}
        public IVOMeta getVOMeta(Class<? extends ISuperVO> type){throw new UnsupportedOperationException();}
    };

    public static void run() throws Exception {
        SapplyBillVO first=bill("A"),second=bill("B");
        WorkflownoteVO common=note("agree","Y",true);common.setCiphertext("signed");
        ICPFParameter user=new ICPFParameter();
        Map<String,Object> params=new HashMap<String,Object>();params.put("batch","batch");params.put("worknote",null);
        SapplyPfBatchCommand command=new SapplyPfBatchCommand("APPROVEU","4455",new SapplyBillVO[]{first,second},
                new PfUserObject[]{user,null},common,params);
        SapplyBatchApprovalProof proof=SapplyBatchApprovalProof.create(command);
        String[] ids=proof.signedIds();
        require(ids.length==2 && !ids[0].equals(ids[1]),"Each signed bill has a distinct approval identity");
        String[] skipped={"B"};
        SapplyPfBatchCommand partial=new SapplyPfBatchCommand("APPROVEU","4455",
                new SapplyBillVO[]{first,second},new PfUserObject[]{user,null},common,params,skipped);
        String[] partialIds=SapplyBatchApprovalProof.create(partial).signedIds();
        require(!ids[0].equals(partialIds[0]) && !ids[1].equals(partialIds[1]),
                "The signed approval binds the precheck-excluded bill");
        skipped[0]="A";partial.getPrecheckSkippedPks()[0]="A";
        require("B".equals(partial.getPrecheckSkippedPks()[0]),"Excluded bills are defensively copied");
        SapplyPfBatchCommand reordered=new SapplyPfBatchCommand("APPROVEU","4455",
                new SapplyBillVO[]{second,first},new PfUserObject[]{null,user},common,params,new String[]{"B"});
        String[] reorderedIds=SapplyBatchApprovalProof.create(reordered).signedIds();
        require(partialIds[0].equals(reorderedIds[1]) && partialIds[1].equals(reorderedIds[0]),
                "Reordering cannot transfer an exclusion to another bill");
        common.setChecknote("other opinion");
        String[] altered=SapplyBatchApprovalProof.create(command).signedIds();
        require(!ids[0].equals(altered[0]) && !ids[1].equals(altered[1]),"Common approval changes bind every bill");
        common.setChecknote("agree");
        BusinessObjectRef ref=new BusinessObjectRef(ids[0],"IC_SAPPLY","A","CODE",null);
        WorkflownoteVO derived=note("agree","Y",true);
        proof.verify("APPROVEU","Approve","4455",first,user,derived,params,ref);
        try{proof.verify("APPROVEOTHER","Approve","4455",first,user,derived,params,ref);
            throw new AssertionError("A different native approval actor was accepted");
        }catch(BusinessException expected) { }
        derived.setChecknote("changed");reject(proof,first,user,derived,params,ref);derived.setChecknote("agree");
        derived.setApproveresult("N");reject(proof,first,user,derived,params,ref);derived.setApproveresult("Y");
        ((TransitionSelectableInfo)derived.getTaskInfo().getTransitionSelectableInfos().get(0)).setChoiced(false);
        reject(proof,first,user,derived,params,ref);
        ((TransitionSelectableInfo)derived.getTaskInfo().getTransitionSelectableInfos().get(0)).setChoiced(true);
        derived.getRelaProperties().put("ElectSignature","true");derived.setCiphertext("changed");
        reject(proof,first,user,derived,params,ref);derived.setCiphertext("signed");
        proof.verify("APPROVEU","Approve","4455",first,user,derived,params,ref);
        user.setAccreditUserID("OTHER");reject(proof,first,user,derived,params,ref);user.setAccreditUserID(null);
        reject(proof,second,null,derived,params,ref);
        reject(proof,first,user,derived,params,new BusinessObjectRef("wrong","IC_SAPPLY","A","CODE",null));

        WfTaskInfo empty=new WfTaskInfo();WorkflownoteVO lazy=new WorkflownoteVO();lazy.setTaskInfo(empty);
        java.lang.reflect.Field field=WfTaskInfo.class.getDeclaredField("transitionSelectableInfos");field.setAccessible(true);
        require(field.get(empty)==null,"Fixture starts with an uninitialized native vector");
        require(SapplyApprovalIntent.workflowSelectionFingerprint(lazy).equals(
                SapplyApprovalIntent.workflowSelectionFingerprint(null)) && field.get(empty)==null,
                "Fingerprinting must not mutate lazy NC workflow fields");
        WorkflownoteVO untouched=new WorkflownoteVO();
        java.lang.reflect.Field taskField=WorkflownoteVO.class.getDeclaredField("taskInfo");taskField.setAccessible(true);
        require(taskField.get(untouched)==null,"Fixture starts without task information");
        SapplyApprovalIntent.workflowSelectionFingerprint(untouched);
        require(taskField.get(untouched)==null,"Fingerprinting must not create an NC task-info object");

        WorkflownoteVO returned=note("return","R",false);
        SapplyBatchApprovalProof returnProof=SapplyBatchApprovalProof.create(new SapplyPfBatchCommand("APPROVEU","4455",
                new SapplyBillVO[]{first,second},null,returned,params));
        BusinessObjectRef returnRef=new BusinessObjectRef(returnProof.signedIds()[0],"IC_SAPPLY","A","CODE",null);
        WorkflownoteVO returnedNative=note("return","R",false);
        nc.vo.wfengine.pub.WFTask returnTask=new nc.vo.wfengine.pub.WFTask();
        returnTask.setTaskType(nc.vo.wfengine.pub.WfTaskType.Backward.getIntValue());returnTask.setBackToFirstActivity(true);
        returnedNative.getTaskInfo().setTask(returnTask);
        returnProof.verify("APPROVEU","Approve","4455",first,null,returnedNative,params,returnRef);
        returnTask.setBackToFirstActivity(false);reject(returnProof,first,null,returnedNative,params,returnRef);
        unapproveWithoutConfirmation(first,second,params);
        System.out.println("PASS SapplyBatchApprovalProofTests (signed bills and derived workflow intent; no NC session)");
    }

    private static void unapproveWithoutConfirmation(SapplyBillVO first,SapplyBillVO second,Map<String,Object> params)
            throws Exception {
        SapplyBatchApprovalProof proof=SapplyBatchApprovalProof.create(new SapplyPfBatchCommand("UNAPPROVEU","4455",
                new SapplyBillVO[]{first,second},null,null,params));
        BusinessObjectRef ref=new BusinessObjectRef(proof.signedIds()[0],"IC_SAPPLY","A","CODE",null);
        proof.verify("UNAPPROVEU","UnApprove","4455",first,null,null,params,ref);
        proof.verify("UNAPPROVEU","UnApprove","4455",first,null,new WorkflownoteVO(),params,ref);
        WorkflownoteVO changed=new WorkflownoteVO();changed.setChecknote("not confirmed");
        rejectUnapprove(proof,first,changed,params,ref);
        changed=new WorkflownoteVO();changed.setApproveresult("Y");rejectUnapprove(proof,first,changed,params,ref);
        changed=new WorkflownoteVO();changed.setAgencyuser("OTHER");rejectUnapprove(proof,first,changed,params,ref);
        changed=new WorkflownoteVO();changed.setAnyoneCanApprove(true);rejectUnapprove(proof,first,changed,params,ref);
        changed=new WorkflownoteVO();changed.setCiphertext("changed");rejectUnapprove(proof,first,changed,params,ref);
        changed=note("unconfirmed","Y",true);rejectUnapprove(proof,first,changed,params,ref);
    }

    private static void rejectUnapprove(SapplyBatchApprovalProof proof,SapplyBillVO bill,WorkflownoteVO note,
            Map<String,Object> params,BusinessObjectRef ref) throws Exception {
        try {proof.verify("UNAPPROVEU","UnApprove","4455",bill,null,note,params,ref);
            throw new AssertionError("An unconfirmed unapproval note was accepted");}
        catch(BusinessException expected) { }
    }

    private static void reject(SapplyBatchApprovalProof proof,SapplyBillVO bill,PfUserObject user,
            WorkflownoteVO note,Map<String,Object> params,BusinessObjectRef ref) throws Exception {
        try{proof.verify("APPROVEU","Approve","4455",bill,user,note,params,ref);throw new AssertionError("Changed approval accepted");}
        catch(BusinessException expected) { }
    }
    private static WorkflownoteVO note(String comment,String decision,boolean chosen) {
        WorkflownoteVO note=new WorkflownoteVO();note.setChecknote(comment);note.setApproveresult(decision);note.setIscheck("Y");
        WfTaskInfo info=new WfTaskInfo();TransitionSelectableInfo transition=new TransitionSelectableInfo();
        transition.setTransitionDefId("ROUTE");transition.setChoiced(chosen);info.addTransitionSelectableInfo(transition);
        note.setTaskInfo(info);return note;
    }
    private static SapplyBillVO bill(final String pk) {
        final SapplyBillHeadVO head=new SapplyBillHeadVO(){
            public String getCgeneralhid(){return pk;}
            public java.util.Set<String> usedAttributeNames(){return Collections.singleton("pk");}
            public Object getAttributeValue(String name){return "pk".equals(name)?pk:null;}
            public IVOMeta getMetaData(){return null;}
        };
        return new SapplyBillVO(){public IBillMeta getMetaData(){return META;}public SapplyBillHeadVO getHead(){return head;}};
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
