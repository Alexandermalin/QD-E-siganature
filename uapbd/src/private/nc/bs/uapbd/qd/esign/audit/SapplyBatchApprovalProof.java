package nc.bs.uapbd.qd.esign.audit;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import nc.vo.ic.general.util.ICLocationUtil;
import nc.vo.ic.location.ICLocationVO;
import nc.vo.ic.m4455.entity.SapplyBillVO;
import nc.vo.ic.qd.esign.InventoryPfStateCommand;
import nc.vo.ic.qd.esign.SapplyApprovalIntent;
import nc.vo.ic.qd.esign.SapplyNativeAction;
import nc.vo.ic.qd.esign.SapplyPfBatchCommand;
import nc.vo.pub.BusinessException;
import nc.vo.pub.workflownote.WorkflownoteVO;
import nc.vo.pubapp.pattern.model.transfer.bill.ServerBillCombinClient;
import nc.vo.pubapp.pflow.PfUserObject;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.wfengine.pub.WFTask;
import nc.vo.wfengine.pub.WfTaskType;

/** Immutable, server-local evidence of the approval payload before NC prepares each workflow task. */
public final class SapplyBatchApprovalProof {
    private final String nativeAction, button, billType, ischeck, decision, comment, ciphertext, selection, emptySelection;
    private final String emptyNativeNote;
    private final boolean hasNote;
    private final Map<String,String> signedIds, payloadIds;
    private final Map<String,SapplyBillVO> signedBills;
    private final String[] orderedSignedIds;

    private SapplyBatchApprovalProof(String nativeAction,String button, String billType, WorkflownoteVO note,
            Map<String,String> signed, Map<String,String> payload, Map<String,SapplyBillVO> bills,
            String[] ordered) throws BusinessException {
        this.nativeAction=nativeAction;this.button=button;this.billType=billType;hasNote=note!=null;
        ischeck=note==null?null:note.getIscheck();decision=note==null?null:note.getApproveresult();
        comment=note==null?null:note.getChecknote();ciphertext=note==null?null:note.getCiphertext();
        selection=SapplyApprovalIntent.workflowSelectionFingerprint(note);
        emptySelection=SapplyApprovalIntent.workflowSelectionFingerprint(null);
        emptyNativeNote=hasNote?null:SapplyApprovalIntent.batchSharedFingerprint(new WorkflownoteVO(),
                Collections.<String,Object>emptyMap());
        signedIds=Collections.unmodifiableMap(new HashMap<String,String>(signed));
        payloadIds=Collections.unmodifiableMap(new HashMap<String,String>(payload));
        signedBills=Collections.unmodifiableMap(new HashMap<String,SapplyBillVO>(bills));
        orderedSignedIds=ordered.clone();
    }

    public static SapplyBatchApprovalProof create(SapplyPfBatchCommand batch) throws BusinessException {
        if(batch==null)throw new BusinessException("出库申请批量审批参数缺失。");
        String action=batch.getAction();String button=SapplyNativeAction.approvalButton(action);
        SapplyBillVO[] bills=batch.getBills();PfUserObject[] users=batch.getUserObjects();
        WorkflownoteVO note=batch.getWorkflowNote();
        if(button==null || bills==null || bills.length<2 || users!=null && users.length!=bills.length
                || note!=null && note.getClass()!=WorkflownoteVO.class || "Approve".equals(button) && note==null)
            throw new BusinessException("出库申请批量审批确认或单据范围不完整。");
        Map<String,String> signed=new HashMap<String,String>(),payload=new HashMap<String,String>();
        Map<String,SapplyBillVO> originals=new HashMap<String,SapplyBillVO>();
        String[] ordered=new String[bills.length];
        Map<String,Object> parameters=batch.getParameters();
        String shared=SapplyApprovalIntent.batchSharedFingerprint(note,parameters,batch.getPrecheckSkippedPks());
        for(int index=0;index<bills.length;index++){
            SapplyBillVO bill=bills[index];String pk=bill==null || bill.getHead()==null?null:clean(bill.getHead().getCgeneralhid());
            if(pk==null || !pk.matches("[A-Za-z0-9_-]{1,20}") || signed.containsKey(pk))
                throw new BusinessException("出库申请批量审批包含无效或重复的单据。");
            PfUserObject user=users==null?null:users[index];
            ordered[index]=SapplyApprovalIntent.batchClientObjectId(button,batch.getBillType(),bill,user,shared);
            signed.put(pk,ordered[index]);
            payload.put(pk,identity(button,batch.getBillType(),bill,user,null,parameters));
            originals.put(pk,bill);
        }
        return new SapplyBatchApprovalProof(action,button,batch.getBillType(),note,signed,payload,originals,ordered);
    }

    /** Ordered like the original bills; claim compares each id to the persisted signed object. */
    public String[] signedIds() { return orderedSignedIds.clone(); }

    public void verify(String actualAction,String actualButton, String actualType, SapplyBillVO bill, PfUserObject user,
            WorkflownoteVO nativeNote, Map<String,Object> parameters, BusinessObjectRef authorized)
            throws BusinessException {
        verify(actualAction,actualButton,actualType,bill,user,nativeNote,parameters,authorized,null);
    }

    /** nativeOriginal is supplied only by the verified, server-local standard inventory callback. */
    void verify(String actualAction,String actualButton,String actualType,SapplyBillVO bill,PfUserObject user,
            WorkflownoteVO nativeNote,Map<String,Object> parameters,BusinessObjectRef authorized,
            SapplyBillVO nativeOriginal) throws BusinessException {
        String pk=bill==null || bill.getHead()==null?null:clean(bill.getHead().getCgeneralhid());
        if(!equal(nativeAction,actualAction) || !equal(button,actualButton) || !equal(billType,actualType) || pk==null || authorized==null
                || !pk.equals(authorized.getObjectPk()) || !equal(signedIds.get(pk),authorized.getClientObjectId())
                || !matchesPayload(pk,actualType,bill,user,parameters,nativeOriginal))
            throw new BusinessException("出库申请批量审批内容与签名授权不一致。");
        if(!hasNote) {
            // NC creates an empty per-bill note for a non-workflow action such as ordinary UNAPPROVE.
            // A different derived note was not part of the signed confirmation and needs site verification.
            if(nativeNote!=null && (nativeNote.getClass()!=WorkflownoteVO.class
                    || !equal(emptyNativeNote,SapplyApprovalIntent.batchSharedFingerprint(nativeNote,
                            Collections.<String,Object>emptyMap()))))
                throw new BusinessException("出库申请逐单取消审批包含未经确认的工作流内容。");
            return;
        }
        if(nativeNote==null || nativeNote.getClass()!=WorkflownoteVO.class
                || !equal(ischeck,nativeNote.getIscheck()) || !equal(decision,nativeNote.getApproveresult())
                || !equal(comment,nativeNote.getChecknote()))
            throw new BusinessException("出库申请逐单审批意见或结果与批量确认不一致。");
        Map<?,?> properties=nativeNote.getRelaProperties();
        Object electronic=properties==null?null:properties.get("ElectSignature");
        if(electronic!=null && "true".equalsIgnoreCase(electronic.toString())
                && !equal(ciphertext,nativeNote.getCiphertext()))
            throw new BusinessException("出库申请逐单审批签名信息与批量确认不一致。");
        if("R".equals(decision) && !nativeNote.isAnyoneCanApprove()){
            nc.vo.pub.pf.WfTaskInfo taskInfo=SapplyApprovalIntent.workflowTaskInfo(nativeNote);
            WFTask task=taskInfo==null?null:taskInfo.getTask();
            if(task==null || task.getTaskType()!=WfTaskType.Backward.getIntValue() || !task.isBackToFirstActivity())
                throw new BusinessException("出库申请逐单退回路径与原生批量确认不一致。");
        }
        String actualSelection=SapplyApprovalIntent.workflowSelectionFingerprint(nativeNote);
        if(!"R".equals(decision) && !emptySelection.equals(actualSelection) && !selection.equals(actualSelection))
            throw new BusinessException("出库申请逐单审批流转选择与批量确认不一致。");
    }

    String originalFingerprint(SapplyBillVO original,PfUserObject user,Map<String,Object> parameters)
            throws BusinessException {
        return identity(button,billType,original,user,null,parameters);
    }

    private boolean matchesPayload(String pk,String type,SapplyBillVO actual,PfUserObject user,
            Map<String,Object> parameters,SapplyBillVO original) throws BusinessException {
        String signed=payloadIds.get(pk),actualId=identity(button,type,actual,user,null,parameters);
        if(equal(signed,actualId))return true;
        SapplyBillVO input=signedBills.get(pk);
        if(original==null || input==null || !equal(signed,identity(button,type,input,user,null,parameters)))return false;
        try {
            // Native NC reads the original client VO and clones only the database-side bill.
            // Keep the signed input's runtime array types intact and recheck it after the merge.
            SapplyBillVO[] source={input};
            Map<Integer,Map<Integer,ICLocationVO[]>> locations=ICLocationUtil.getLocationVO(source);
            SapplyBillVO[] expected={(SapplyBillVO)original.clone()};
            new ServerBillCombinClient<SapplyBillVO>().combine(expected,source);
            expected[0]=(SapplyBillVO)expected[0].clone();
            ICLocationUtil.setLocationVO(expected,locations);
            return equal(actualId,identity(button,type,expected[0],user,null,parameters))
                    && equal(signed,identity(button,type,input,user,null,parameters));
        } catch(RuntimeException error) {
            throw new BusinessException("无法核对出库申请原生批量单据转换。",error);
        } catch(LinkageError error) {
            throw new BusinessException("当前 NC 版本无法核对出库申请原生批量单据转换。",error);
        }
    }

    private static String identity(String button,String type,SapplyBillVO bill,PfUserObject user,
            WorkflownoteVO note,Map<String,Object> parameters) throws BusinessException {
        return SapplyApprovalIntent.clientObjectId(button,new InventoryPfStateCommand(type,bill,user,note,parameters));
    }
    private static boolean equal(Object first,Object second){return first==null?second==null:first.equals(second);}
    private static String clean(String value){if(value==null)return null;String s=value.trim();return s.isEmpty()?null:s;}
}
