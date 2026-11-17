package nc.impl.so.qd.esign;
import java.io.Serializable;
import java.util.*;
import nc.bs.framework.common.NCLocator;
import nc.itf.so.m30.closemanage.ISaleOrderCloseManageMaintain;
import nc.itf.so.m30.self.ISaleOrderScriptMaintain;
import nc.itf.so.qd.esign.ISaleOrderOperationAdapter;
import nc.itf.uap.pf.IplatFormEntry;
import nc.vo.pub.BusinessException;
import nc.vo.pubapp.pflow.PFReturnObject;
import nc.vo.pubapp.pflow.PfUserObject;
import nc.vo.scmpub.res.BusinessCheck;
import nc.vo.so.m30.entity.SaleOrderBVO;
import nc.vo.so.m30.entity.SaleOrderUserObject;
import nc.vo.so.m30.entity.SaleOrderVO;
import nc.vo.so.m30.entity.SaleOrderHVO;
import nc.vo.so.qd.esign.SaleOrderCommand;
import nc.vo.so.qd.esign.SaleOrderPfActionCommand;
import nc.vo.so.qd.esign.SaleOrderPfActionResult;
import nc.vo.so.qd.esign.SaleOrderStateCommand;
import nc.vo.uapbd.qd.esign.*;

/** 委托原始销售订单脚本服务的适配器，校验授权上下文与命令匹配，不重新实现原生业务规则。 */
public class SaleOrderOperationAdapter implements ISaleOrderOperationAdapter {
    /** 在执行业务前核对命令类型、操作范围、授权对象和组织归属；不替代 NC 原生金额、状态等业务规则。 */
    public void validate(OperationContext c,Serializable payload) throws BusinessException {
        if (isPilotSave(c)) { pfAdapter().validate(c, payload); return; }
        // 其他按钮先经固定白名单核对；未通过联调的动作在执行前关闭，不得回落低层脚本写入。
        SaleOrderActionRoute route=SaleOrderActionRoute.forContext(c);
        if(route!=null){
            if(route.getKind().isState())validateState(route,c,payload);
            else validatePf(route,c,payload);
            route.requireEnabled();
            return;
        }
        require(payload instanceof SaleOrderCommand,"SaleOrderCommand required");SaleOrderCommand cmd=(SaleOrderCommand)payload;
        String op=c.getOperationType();require(Arrays.asList("INSERT","UPDATE","DELETE").contains(op),"Unsupported sale-order action");
        SaleOrderVO[] bills=cmd.getBills();require(bills!=null&&bills.length==c.getObjects().size(),"Selection size mismatch");
        if("INSERT".equals(op))require(bills.length==1,"Batch INSERT correlation has not been verified");
        Set<String> actual=new HashSet<String>();
        for(SaleOrderVO bill:bills){require(bill!=null&&bill.getParentVO()!=null,"Missing sale-order header");require(c.getGroupId().equals(bill.getParentVO().getPk_group())&&c.getOrgId().equals(bill.getParentVO().getPk_org()),"Sale-order organization mismatch");if(!"INSERT".equals(op))require(actual.add(bill.getPrimaryKey()),"Duplicate sale-order key");}
        for(BusinessObjectRef ref:c.getObjects()){require("SO_SALE_ORDER".equals(ref.getObjectType()),"Object type mismatch");if("INSERT".equals(op))require(ref.getObjectPk()==null&&bills[0].getPrimaryKey()==null,"INSERT must not target an existing object");else require(ref.getObjectPk()!=null&&actual.contains(ref.getObjectPk()),"Signed sale-order selection mismatch");}
        if(!"INSERT".equals(op))for(String pk:actual){SaleOrderHVO stored=(SaleOrderHVO)new nc.bs.dao.BaseDAO().retrieveByPK(SaleOrderHVO.class,pk,new String[]{"csaleorderid","pk_group","pk_org"});require(stored!=null&&c.getGroupId().equals(stored.getPk_group())&&c.getOrgId().equals(stored.getPk_org()),"Stored sale-order organization is outside the authorized request");}
        if("UPDATE".equals(op)){require(cmd.getOriginalBills()!=null&&cmd.getOriginalBills().length==bills.length,"Original sale-order versions are required");Set<String> old=new HashSet<String>();for(SaleOrderVO b:cmd.getOriginalBills())require(b!=null&&old.add(b.getPrimaryKey()),"Duplicate/missing original key");require(old.equals(actual),"Original selection mismatch");}
    }
    /** 重新校验已授权的类型化命令后，按固定动作分支委托 NC 原生业务服务；原生异常向调用方传播。 */
    public Serializable execute(OperationContext c,Serializable payload) throws BusinessException {
        if (isPilotSave(c)) return pfAdapter().execute(c,payload);
        // 先核对固定白名单并确认该动作已通过联调；未接入的动作在这里被拒绝，不执行任何原生写入。
        SaleOrderActionRoute route=SaleOrderActionRoute.forContext(c);
        if(route!=null){
            validate(c,payload);
            if(route.getKind().isState())return executeState(route,(SaleOrderStateCommand)payload);
            return executePf(route,(SaleOrderPfActionCommand)payload);
        }
        validate(c,payload);SaleOrderCommand cmd=(SaleOrderCommand)payload;ISaleOrderScriptMaintain service=NCLocator.getInstance().lookup(ISaleOrderScriptMaintain.class);
        if("INSERT".equals(c.getOperationType()))return service.saleOrderInsert(cmd.getBills(),cmd.getUserObject());
        if("UPDATE".equals(c.getOperationType()))return service.saleOrderUpdate(cmd.getBills(),cmd.getUserObject(),cmd.getOriginalBills());
        return service.saleOrderDelete(cmd.getBills(),cmd.getUserObject());
    }
    /** 将真实业务返回值还原为与请求客户端标识对应的对象引用，供网关检查完整性并回填持久化主键及编码。 */
    public List<BusinessObjectRef> describeResult(OperationContext c,Serializable result) throws BusinessException {
        if (isPilotSave(c)) return pfAdapter().describeResult(c,result);
        SaleOrderActionRoute route=SaleOrderActionRoute.forContext(c);
        if(route!=null&&route.getKind().isState())return describeState(route,c,result);
        if(route!=null&&result instanceof SaleOrderPfActionResult)return describePf(result);
        if("DELETE".equals(c.getOperationType()))return c.getObjects();require(result instanceof SaleOrderVO[],"Unexpected sale-order result");SaleOrderVO[] bills=(SaleOrderVO[])result;
        List<BusinessObjectRef> out=new ArrayList<BusinessObjectRef>();
        for(BusinessObjectRef ref:c.getObjects()){SaleOrderVO found=null;for(SaleOrderVO bill:bills)if(ref.getObjectPk()==null||ref.getObjectPk().equals(bill.getPrimaryKey())){require(found==null,"Ambiguous sale-order result");found=bill;}require(found!=null&&found.getPrimaryKey()!=null,"Saved sale-order key is missing");out.add(new BusinessObjectRef(ref.getClientObjectId(),ref.getObjectType(),found.getPrimaryKey(),found.getParentVO().getVbillcode(),ref.getParentClientObjectId()));}return out;
    }

    /** PF 流程动作命令校验：单张已存订单、主键与组织一致、交易类型匹配、原生用户对象与流程参数白名单。 */
    private void validatePf(SaleOrderActionRoute route,OperationContext c,Serializable payload) throws BusinessException {
        require(payload instanceof SaleOrderPfActionCommand,"SaleOrderPfActionCommand required");
        SaleOrderPfActionCommand cmd=(SaleOrderPfActionCommand)payload;
        SaleOrderVO[] bills=cmd.getBills();
        require(bills!=null&&bills.length==1,"销售订单 PF 受控动作只支持一张订单");
        SaleOrderVO bill=bills[0];
        require(bill!=null&&bill.getParentVO()!=null&&bill.getPrimaryKey()!=null,"缺少原生命中订单主键或表头");
        require(route.getObjectPk().equals(bill.getPrimaryKey()),"订单主键与签名授权对象不一致");
        // 客户端流程动作按原生语义提交差异 VO（仅主键与流程字段），组织与交易类型必须以持久化表头为准。
        SaleOrderHVO stored=loadHeader(bill.getPrimaryKey());
        require(stored!=null,"未找到已签名订单的持久化表头");
        require(c.getGroupId().equals(stored.getPk_group())&&c.getOrgId().equals(stored.getPk_org()),"订单组织与授权不一致");
        String type=stored.getVtrantypecode();
        String billType=cmd.getBillType();
        // 交易类型必须与单据一致；未带交易类型时由原生按固定功能型 30 处理。
        require(billType==null||(billType.matches("30(?:-[A-Za-z0-9_-]+)?")
                &&(billType.equals(type)||(type==null&&"30".equals(billType)))),"销售订单交易类型与授权不一致");
        PfUserObject pf=cmd.getUserObject();
        require(pf!=null&&pf.getUserObject() instanceof SaleOrderUserObject,"销售订单缺少原生流程用户对象");
        require(pf.getBusinessCheckMap()==null||pf.getBusinessCheckMap().isEmpty(),"销售订单 PF 动作不接受跳过检查参数");
        require(((SaleOrderUserObject)pf.getUserObject()).isIsclientsave(),"销售订单流程缺少原生客户端标记");
        // 原生流程参数只允许已核实的白名单键，禁止任意脚本或流程载荷。
        for(Map.Entry<String,Object> entry:cmd.getParameters().entrySet()){
            String key=entry.getKey();
            require("notechecked".equals(key)||"silently".equals(key)||"nosendmessage".equals(key)
                    ||"besideapprove".equals(key),"销售订单 PF 参数未在白名单内："+key);
            require(entry.getValue() instanceof String||entry.getValue() instanceof Boolean,
                    "销售订单 PF 参数类型不受支持："+key);
        }
        // 删除不需要流程说明；提交/收回允许原生流程准备产生的说明为空（无审批窗口时）。
        if(SaleOrderActionRoute.Kind.PF_DELETE!=route.getKind()){
            // Native pre-checks are still required; the note itself may be null when no approval dialog appears.
        }
    }

    /** 按已核实的原生 PF 入口执行固定动作，并以持久化结果核对业务事实，避免把返回原 VO 当作成功。 */
    private Serializable executePf(SaleOrderActionRoute route,SaleOrderPfActionCommand cmd) throws BusinessException {
        SaleOrderVO bill=cmd.getBills()[0];
        String key=bill.getPrimaryKey();
        String action;
        if(SaleOrderActionRoute.Kind.PF_DELETE==route.getKind())action="DELETE";
        else if(SaleOrderActionRoute.Kind.PF_SAVE==route.getKind())action="SAVE";
        else if(SaleOrderActionRoute.Kind.PF_UNSAVE==route.getKind())action="UNSAVE";
        else if(SaleOrderActionRoute.Kind.PF_APPROVE==route.getKind())action="APPROVE";
        else action="UNAPPROVE";
        String billType=cmd.getBillType()==null?"30":cmd.getBillType();
        Map<String,Object> parameters=cmd.getParameters();
        HashMap<String,Object> rawParameters=parameters.isEmpty()?new HashMap<String,Object>():new HashMap<String,Object>(parameters);
        Object raw=platformEntry().processAction(action,billType,cmd.getWorkflownote(),bill,cmd.getUserObject(),rawParameters);
        Serializable nativeResult=raw instanceof Serializable?(Serializable)raw:null;
        SaleOrderHVO persisted=loadHeader(key);
        if(SaleOrderActionRoute.Kind.PF_DELETE==route.getKind()){
            require(persisted==null||isDeleted(persisted),"原生删除后订单仍然有效");
            String code=persisted==null?bill.getParentVO().getVbillcode():persisted.getVbillcode();
            return new SaleOrderPfActionResult(nativeResult,key,code,true);
        }
        require(persisted!=null,"原生流程动作后未找到订单");
        require(route.getKind()!=SaleOrderActionRoute.Kind.PF_SAVE||persisted.getFstatusflag()!=null,
                "提交后订单状态不可核对");
        SaleOrderVO returned=firstReturnedOrder(raw);
        if(returned!=null)require(key.equals(returned.getPrimaryKey()),"原生 PF 返回订单与授权不一致");
        return new SaleOrderPfActionResult(nativeResult,key,persisted.getVbillcode(),false);
    }

    /** 从原生 PF 返回对象中取唯一单据，用于核对返回身份；非单据结果返回 null。 */
    private static SaleOrderVO firstReturnedOrder(Object raw) throws BusinessException {
        Object value=raw;
        if(value instanceof PFReturnObject[]){
            PFReturnObject[] wrapped=(PFReturnObject[])value;
            require(wrapped.length==1&&wrapped[0]!=null,"原生 PF 返回包装不唯一");
            value=wrapped[0].getBills();
        }else if(value instanceof PFReturnObject){
            value=((PFReturnObject)value).getBills();
        }
        if(value instanceof SaleOrderVO[])return ((SaleOrderVO[])value)[0];
        return null;
    }

    /** 真实删除以主键查不到或逻辑删除（dr=1）为准，不以成功回调代替。 */
    private static boolean isDeleted(SaleOrderHVO header) {
        Integer dr=header.getDr();
        return dr!=null&&dr.intValue()==1;
    }

    /** PF 动作结果引用：删除沿用授权主键，提交/收回以持久化主键与真实单号回填。 */
    private List<BusinessObjectRef> describePf(Serializable result) throws BusinessException {
        require(result instanceof SaleOrderPfActionResult,"原生 PF 结果类型不正确");
        SaleOrderPfActionResult outcome=(SaleOrderPfActionResult)result;
        BusinessObjectRef ref=null;
        return Collections.singletonList(new BusinessObjectRef("result", "SO_SALE_ORDER",
                outcome.getPrimaryKey(), outcome.getBillCode(), null));
    }

    /** 直接服务动作的结果引用：以原生命中返回的实际主键为准确认业务对象，编码缺失时保留原引用编码。 */
    private List<BusinessObjectRef> describeState(SaleOrderActionRoute route,OperationContext c,Serializable result) throws BusinessException {
        require(result instanceof SaleOrderVO[],"原生命中服务返回类型不正确");
        SaleOrderVO[] bills=(SaleOrderVO[])result;
        List<BusinessObjectRef> out=new ArrayList<BusinessObjectRef>();
        for(BusinessObjectRef ref:c.getObjects()){
            SaleOrderVO found=null;
            for(SaleOrderVO bill:bills)if(ref.getObjectPk()!=null&&ref.getObjectPk().equals(bill.getPrimaryKey())){require(found==null,"原生命中返回了重复订单");found=bill;}
            require(found!=null,"原生命中结果未返回已授权订单");
            String code=found.getParentVO()==null?null:found.getParentVO().getVbillcode();
            if(code==null)code=ref.getBillCode();
            out.add(new BusinessObjectRef(ref.getClientObjectId(),ref.getObjectType(),found.getPrimaryKey(),code,ref.getParentClientObjectId()));
        }
        return out;
    }

    /** 冻结/解冻/整单关闭的类型化命令校验：单张已存订单、组织与主键一致、参数按动作白名单。 */
    private void validateState(SaleOrderActionRoute route,OperationContext c,Serializable payload) throws BusinessException {
        require(payload instanceof SaleOrderStateCommand,"SaleOrderStateCommand required");
        SaleOrderStateCommand cmd=(SaleOrderStateCommand)payload;
        SaleOrderVO[] bills=cmd.getBills();
        require(bills!=null&&bills.length==1,"销售订单直接服务动作只支持一张订单");
        SaleOrderVO bill=bills[0];
        require(bill!=null&&bill.getParentVO()!=null&&bill.getPrimaryKey()!=null,"缺少原生命中订单主键或表头");
        require(route.getObjectPk().equals(bill.getPrimaryKey()),"订单主键与签名授权对象不一致");
        SaleOrderHVO header=bill.getParentVO();
        require(c.getGroupId().equals(header.getPk_group())&&c.getOrgId().equals(header.getPk_org()),"订单组织与授权不一致");
        String reason=cmd.getCloseReason();
        Map<String,Boolean> checks=cmd.getBusinessCheckMap();
        if(SaleOrderActionRoute.Kind.STATE_CLOSE==route.getKind()){
            // 关闭原因与签名事由分开传递：原因必须由原生输入取得，且已逐行写入 vclosereason。
            require(reason!=null&&reason.trim().length()>0,"整单关闭缺少关闭原因");
            require(reason.equals(reason.trim()),"关闭原因首尾不能包含空白");
            SaleOrderBVO[] lines=bill.getChildrenVO();
            require(lines!=null&&lines.length>0,"整单关闭缺少整单行，请重新查询后重试");
            for(SaleOrderBVO line:lines){require(line!=null&&reason.equals(line.getVclosereason()),"行关闭原因与关闭原因不一致");}
            require(checks.isEmpty(),"整单关闭不接受跳过检查参数");
            return;
        }
        require(reason==null||reason.trim().length()==0,"非关闭动作不接受关闭原因");
        if(SaleOrderActionRoute.Kind.STATE_FREEZE==route.getKind()){
            require(checks.isEmpty(),"冻结不接受业务检查参数");
        }else{
            // 解冻只接受用户已确认的 ATP 继续标记（值为 false 表示跳过该检查），不得默认传入。
            String atp=BusinessCheck.ATPCheck.getCheckCode();
            require(checks.isEmpty()||(checks.size()==1&&checks.containsKey(atp)&&Boolean.FALSE.equals(checks.get(atp))),
                    "解冻只接受已确认的 ATP 继续标记");
        }
    }

    /** 按已核实的原生签名调用直接服务，并核对返回单据身份，避免把返回原 VO 当成业务成功。 */
    private Serializable executeState(SaleOrderActionRoute route,SaleOrderStateCommand cmd) throws BusinessException {
        SaleOrderVO[] bills=cmd.getBills();
        SaleOrderVO[] returned;
        if(SaleOrderActionRoute.Kind.STATE_FREEZE==route.getKind()){
            returned=closeManage().freezeSaleOrder(bills);
        }else if(SaleOrderActionRoute.Kind.STATE_UNFREEZE==route.getKind()){
            try{
                returned=closeManage().unFreezeSaleOrder(bills,cmd.getBusinessCheckMap());
            }catch(BusinessException failure){
                // 原生 ATP 检查以可恢复异常提示"ATP 不足"；受控网关不能把可恢复异常带回客户端，
                // 用固定前缀标记，客户端据此询问用户"继续/取消"后带 ATPCheck=false 重新授权执行。
                if(failure instanceof nc.itf.pubapp.pub.exception.IResumeException
                        && nc.vo.scmpub.res.BusinessCheck.ATPCheck.getCheckCode()
                            .equals(((nc.itf.pubapp.pub.exception.IResumeException)failure).getBusiExceptionType())){
                    throw new BusinessException("QD_ESIGN_ATP_CONFIRM:"+failure.getMessage());
                }
                throw failure;
            }
        }else{
            returned=closeManage().closeSaleOrder(bills);
        }
        require(returned!=null&&returned.length==bills.length,"原生命中服务未返回完整结果");
        Set<String> requested=new HashSet<String>();
        for(SaleOrderVO bill:bills)requested.add(bill.getPrimaryKey());
        Set<String> actual=new HashSet<String>();
        for(SaleOrderVO bill:returned){
            require(bill!=null&&bill.getPrimaryKey()!=null,"原生命中结果缺少订单主键");
            require(requested.contains(bill.getPrimaryKey()),"原生命中结果与授权订单不一致");
            require(actual.add(bill.getPrimaryKey()),"原生命中结果重复");
            if(SaleOrderActionRoute.Kind.STATE_CLOSE==route.getKind())require(bill.getChildrenVO()!=null,"整单关闭结果缺少整单行");
        }
        require(actual.equals(requested),"原生命中结果未覆盖全部授权订单");
        return returned;
    }

    /** 验证指定前置条件，不满足时立即抛出相应异常，阻止后续处理。 */
    private static void require(boolean condition,String message) throws BusinessException{if(!condition)throw new BusinessException(message);}
    protected SaleOrderPfSaveAdapter pfAdapter() { return new SaleOrderPfSaveAdapter(); }
    /** 直接服务定位；离线测试可覆盖以断言原生调用次数与参数。 */
    protected ISaleOrderCloseManageMaintain closeManage() { return NCLocator.getInstance().lookup(ISaleOrderCloseManageMaintain.class); }
    /** 原生 PF 入口定位；离线测试可覆盖以断言进程动作、交易类型与流程参数。 */
    protected IplatFormEntry platformEntry() { return NCLocator.getInstance().lookup(IplatFormEntry.class); }
    /** 读取持久化表头以核对业务事实；离线测试可覆盖。 */
    protected SaleOrderHVO loadHeader(String key) throws BusinessException {
        return (SaleOrderHVO)new nc.bs.dao.BaseDAO().retrieveByPK(SaleOrderHVO.class,key,
                new String[]{"csaleorderid","pk_group","pk_org","vbillcode","dr","vtrantypecode","fstatusflag"});
    }
    private static boolean isPilotSave(OperationContext c) {
        return c != null && "40060301".equals(c.getNodeCode()) && "Save".equals(c.getButtonCode());
    }
}
