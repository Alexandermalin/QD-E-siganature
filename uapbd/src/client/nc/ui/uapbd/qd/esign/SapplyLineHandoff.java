package nc.ui.uapbd.qd.esign;

import java.util.Set;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.SapplyLineScope;

/** One-time client handoff bound to the signed bill and exact selected row identities. */
final class SapplyLineHandoff {
    private OperationContext authorized;
    private String dispatched;

    boolean supports(OperationContext context) {
        if(context==null || blank(context.getGroupId()) || blank(context.getOrgId()))return false;
        try {SapplyLineScope.require(context);return true;}
        catch(BusinessException invalid){return false;}
    }
    boolean bind(OperationContext context) {
        if(authorized!=null || !supports(context) || blank(context.getRequestId()))return false;
        authorized=context;return true;
    }
    OperationContext context(){return authorized;}
    String dispatch(String group,String org,String billPk,Set<String> rowPks) throws BusinessException {
        if(authorized==null || dispatched!=null || !authorized.getGroupId().equals(group)
                || !authorized.getOrgId().equals(org))throw new BusinessException("出库申请行操作授权不可用。");
        SapplyLineScope.Selection scope=SapplyLineScope.require(authorized);
        if(!scope.getRoot().getObjectPk().equals(billPk) || rowPks==null || !scope.lineKeys().equals(rowPks))
            throw new BusinessException("出库申请单或所选行已变化，请重新签名。");
        dispatched=authorized.getRequestId();return dispatched;
    }
    boolean wasDispatched(String requestId){return requestId!=null && requestId.equals(dispatched);}
    void release(String requestId){if(authorized!=null && authorized.getRequestId().equals(requestId))reset();}
    void reset(){authorized=null;dispatched=null;}
    private static boolean blank(String value){return value==null||value.trim().isEmpty();}
}
