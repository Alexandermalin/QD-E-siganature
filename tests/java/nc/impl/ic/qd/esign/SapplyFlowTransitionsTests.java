package nc.impl.ic.qd.esign;

import nc.vo.pub.BusinessException;

/** Checks workflow status validation without requiring the NC metadata service. */
public final class SapplyFlowTransitionsTests {
    public static void run() throws Exception {
        SapplyFlowTransitions.before("Commit", 2, 2, 5, 4);
        SapplyFlowTransitions.after("Commit", 5, 2, 5, 4);
        SapplyFlowTransitions.after("Commit", 4, 2, 5, 4);
        SapplyFlowTransitions.before("UnCommit", 5, 2, 5, 4);
        SapplyFlowTransitions.after("UnCommit", 2, 2, 5, 4);
        SapplyFlowTransitions.beforeApproval("Approve",2,2,5,4,6);
        SapplyFlowTransitions.beforeApproval("Approve",5,2,5,4,6);
        SapplyFlowTransitions.afterApproval("Approve",5,2,5,4,6);
        SapplyFlowTransitions.afterApproval("Approve",4,2,5,4,6);
        SapplyFlowTransitions.afterApproval("Approve",6,2,5,4,6);
        SapplyFlowTransitions.beforeApproval("UnApprove",4,2,5,4,6);
        SapplyFlowTransitions.afterApproval("UnApprove",2,2,5,4,6);
        try {SapplyFlowTransitions.beforeApproval("UnApprove",5,2,5,4,6);throw new AssertionError("Invalid unapproval accepted");}
        catch(BusinessException expected) { }
        reject(true,"Commit",null,2,5,4);
        reject(true,"Commit",5,2,5,4);
        reject(true,"UnCommit",4,2,5,4);
        reject(false,"Commit",2,2,5,4);
        reject(false,"UnCommit",5,2,5,4);
        reject(false,"OTHER",4,2,5,4);
        reject(true,"Commit",2,2,2,4);
        System.out.println("PASS SapplyFlowTransitionsTests (workflow status guard; no NC transaction)");
    }
    private static void reject(boolean before,String button,Integer status,int free,int auditing,int audited) throws Exception {
        try {
            if (before) SapplyFlowTransitions.before(button,status,free,auditing,audited);
            else SapplyFlowTransitions.after(button,status,free,auditing,audited);
            throw new AssertionError("Unexpected workflow transition accepted");
        } catch (BusinessException expected) { }
    }
}
