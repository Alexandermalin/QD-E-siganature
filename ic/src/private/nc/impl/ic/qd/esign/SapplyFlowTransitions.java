package nc.impl.ic.qd.esign;

import nc.vo.pub.BusinessException;

/** Validates the candidate outbound-request workflow transitions using NC's runtime enum values. */
final class SapplyFlowTransitions {
    private SapplyFlowTransitions() { }

    static void before(String button, Integer status, int free, int auditing, int audited) throws BusinessException {
        validateFlags(free, auditing, audited);
        if (status == null || !("Commit".equals(button) && status.intValue() == free
                || "UnCommit".equals(button) && status.intValue() == auditing)) {
            throw new BusinessException("出库申请单提交或收回前的状态不符合原生流程，请重新查询。");
        }
    }

    static void after(String button, Integer status, int free, int auditing, int audited) throws BusinessException {
        validateFlags(free, auditing, audited);
        if (status == null || !("Commit".equals(button) && (status.intValue() == auditing || status.intValue() == audited)
                || "UnCommit".equals(button) && status.intValue() == free)) {
            throw new BusinessException("出库申请单提交或收回后的状态无法确认。");
        }
    }

    static void beforeApproval(String button,Integer status,int free,int auditing,int audited,int noPass) throws BusinessException {
        validateApprovalFlags(free,auditing,audited,noPass);
        if(status==null || !("Approve".equals(button) && (status.intValue()==free || status.intValue()==auditing)
                || "UnApprove".equals(button) && status.intValue()==audited))
            throw new BusinessException("出库申请审批前状态不符合原生流程，请重新查询。");
    }

    static void afterApproval(String button,Integer status,int free,int auditing,int audited,int noPass) throws BusinessException {
        validateApprovalFlags(free,auditing,audited,noPass);
        if(status==null || !("Approve".equals(button) && (status.intValue()==free || status.intValue()==auditing
                    || status.intValue()==audited || status.intValue()==noPass)
                || "UnApprove".equals(button) && (status.intValue()==free || status.intValue()==auditing)))
            throw new BusinessException("出库申请审批后的状态无法确认。");
    }

    private static void validateApprovalFlags(int free,int auditing,int audited,int noPass) throws BusinessException {
        validateFlags(free,auditing,audited);
        if(noPass==free || noPass==auditing || noPass==audited)throw new BusinessException("NC 审批状态定义不一致。");
    }

    private static void validateFlags(int free, int auditing, int audited) throws BusinessException {
        if (free == auditing || free == audited || auditing == audited)
            throw new BusinessException("NC 库存单据流程状态定义不一致。");
    }
}
