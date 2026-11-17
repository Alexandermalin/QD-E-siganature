package nc.vo.ic.qd.esign;

import nc.vo.pub.BusinessException;

/** Native approval actions append the current NC user primary key to the PF action name. */
public final class SapplyNativeAction {
    private SapplyNativeAction() { }

    public static String approval(String button,String userId) throws BusinessException {
        String base="Approve".equals(button)?"APPROVE":"UnApprove".equals(button)?"UNAPPROVE":null;
        if(base==null || userId==null || !userId.matches("[A-Za-z0-9_-]{1,20}"))
            throw new BusinessException("出库申请审批缺少有效的当前用户。");
        return base+userId;
    }

    public static String approvalButton(String action) {
        if(action==null)return null;
        String base=action.startsWith("UNAPPROVE")?"UNAPPROVE":action.startsWith("APPROVE")?"APPROVE":null;
        if(base==null || !action.substring(base.length()).matches("[A-Za-z0-9_-]{1,20}"))return null;
        return "APPROVE".equals(base)?"Approve":"UnApprove";
    }

    public static boolean matches(String button,String action,String userId) {
        try{return approval(button,userId).equals(action);}catch(BusinessException invalid){return false;}
    }
}
