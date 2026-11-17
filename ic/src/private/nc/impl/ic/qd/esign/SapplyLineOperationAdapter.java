package nc.impl.ic.qd.esign;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import nc.bs.framework.common.NCLocator;
import nc.bs.uapbd.qd.esign.security.EsignPermission;
import nc.bs.uapbd.qd.esign.security.ServerIdentity;
import nc.itf.ic.m4455.self.ISapplyBillMaintain;
import nc.itf.ic.qd.esign.ISapplyLineOperationAdapter;
import nc.vo.ic.general.define.ICBillFlag;
import nc.vo.ic.m4455.entity.SapplyBillBodyVO;
import nc.vo.ic.m4455.entity.SapplyBillHeadVO;
import nc.vo.ic.m4455.entity.SapplyBillVO;
import nc.vo.ic.qd.esign.SapplyLineCommand;
import nc.vo.ic.qd.esign.SapplyLineResult;
import nc.vo.pub.BusinessException;
import nc.vo.pub.billtype.BilltypeVO;
import nc.vo.pubapp.pattern.model.transfer.bill.ClientBillToServer;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.InventoryBillScope;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.SapplyLineScope;

/** Rebuilds a row-operation bill from persisted server-side VOs before invoking the native service. */
public class SapplyLineOperationAdapter implements ISapplyLineOperationAdapter {
    public void validate(OperationContext context,Serializable value) throws BusinessException {
        require(value instanceof SapplyLineCommand,"出库申请行操作参数无效。");
        // A budget continuation requires a separately bound authorization; do not accept an unbound override.
        require(!((SapplyLineCommand)value).isResumeBudget(),"预算提示续做尚未接入独立授权，请重新操作。");
        load(context,true);
    }

    public Serializable execute(OperationContext context,Serializable value) throws BusinessException {
        require(value instanceof SapplyLineCommand && !((SapplyLineCommand)value).isResumeBudget(),"出库申请行操作参数无效。");
        Loaded before=load(context,true);
        String beforeType=text(before.head.getVtrantypecode());
        String beforeTypePk=InventoryTransactionTypes.identity(before.head);
        SapplyBillVO bill=createBill(before.head,before.bodies);
        require(bill!=null && bill.getHead()!=null && before.pk.equals(text(bill.getHead().getCgeneralhid())),
                "无法构造已授权的出库申请单。");
        SapplyBillVO[] sent=toNativeBill(bill);
        require(sent!=null && sent.length==1 && sent[0]!=null && sent[0].getHead()!=null
                && before.pk.equals(text(sent[0].getHead().getCgeneralhid())),"出库申请行操作转换结果无效。");
        SapplyBillVO[] result="LineClose".equals(context.getButtonCode())
                ?maintain().rowClose(sent,false):maintain().rowOpen(sent,false);
        require(result!=null && result.length==1 && result[0]!=null && result[0].getHead()!=null
                && before.pk.equals(text(result[0].getHead().getCgeneralhid())),"原生行操作未返回授权单据。");
        checkHead(context,result[0].getHead(),before.pk);
        Loaded after=load(context,false);
        require(beforeType.equals(text(after.head.getVtrantypecode()))
                && InventoryTransactionTypes.sameIdentity(beforeTypePk,after.head),"出库申请交易类型在行操作期间发生变化。");
        boolean target="LineClose".equals(context.getButtonCode());
        for(SapplyBillBodyVO body:after.bodies) require(body.getIsclosed()!=null && body.getIsclosed().booleanValue()==target,
                "出库申请行操作后的持久化状态不符合预期。");
        require(before.states.keySet().equals(after.states.keySet()),"出库申请表体在行操作期间发生变化。");
        for(Map.Entry<String,Boolean> state:before.states.entrySet()) {
            if(!before.selected.contains(state.getKey())) {
                Boolean prior=state.getValue(),current=after.states.get(state.getKey());
                require(prior==null?current==null:prior.equals(current),"未选中的出库申请行状态发生变化。");
            }
        }
        return new SapplyLineResult(after.pk,after.code,result);
    }

    public List<BusinessObjectRef> describeResult(OperationContext context,Serializable value) throws BusinessException {
        SapplyLineScope.Selection selected=SapplyLineScope.require(context);
        require(value instanceof SapplyLineResult,"出库申请行操作结果无效。");
        SapplyLineResult result=(SapplyLineResult)value;
        require(selected.getRoot().getObjectPk().equals(result.getBillPk()) && text(result.getBillCode())!=null,
                "出库申请行操作结果与授权单据不一致。");
        List<BusinessObjectRef> refs=new ArrayList<BusinessObjectRef>();
        for(BusinessObjectRef ref:context.getObjects()) refs.add(new BusinessObjectRef(ref.getClientObjectId(),
                ref.getObjectType(),ref.getObjectPk(),result.getBillCode(),ref.getParentClientObjectId()));
        return Collections.unmodifiableList(refs);
    }

    private Loaded load(OperationContext context,boolean requireChange) throws BusinessException {
        SapplyLineScope.Selection selected=SapplyLineScope.require(context);
        BusinessObjectRef root=selected.getRoot();String pk=root.getObjectPk();
        requirePk(pk);requireBillPermission(root);
        SapplyBillHeadVO head=loadHead(pk);checkHead(context,head,pk);
        String type=text(head.getVtrantypecode());require(type!=null,"出库申请单缺少交易类型。");
        InventoryTransactionTypes.require(context,InventoryBillScope.SAPPLY,type,head,false,new InventoryTransactionTypes.Lookup() {
            public BilltypeVO lock(String typePk) throws BusinessException { return loadTransactionType(typePk); }
        });
        require(head.getFbillflag()!=null && head.getFbillflag().intValue()==approvedFlag(),"出库申请单必须处于已审批状态。");
        List<SapplyBillBodyVO> bodies=new ArrayList<SapplyBillBodyVO>();boolean actionable=false;
        Map<String,SapplyBillBodyVO> persisted=loadBodies(pk);
        require(persisted!=null,"无法读取出库申请表体。");
        Map<String,Boolean> states=new HashMap<String,Boolean>();
        for(Map.Entry<String,SapplyBillBodyVO> item:persisted.entrySet()) {
            SapplyBillBodyVO row=item.getValue();
            require(row!=null && item.getKey()!=null && item.getKey().equals(text(row.getCgeneralbid()))
                    && pk.equals(text(row.getCgeneralhid())) && active(row.getAttributeValue("dr")),
                    "出库申请表体数据不完整或单据关系无效。");
            nc.vo.pub.lang.UFBoolean closed=row.getIsclosed();
            states.put(item.getKey(),closed==null?null:Boolean.valueOf(closed.booleanValue()));
        }
        java.util.Set<String> selectedKeys=new java.util.HashSet<String>();
        boolean target="LineClose".equals(context.getButtonCode());
        for(BusinessObjectRef ref:selected.getLines()) {
            requirePk(ref.getObjectPk());SapplyBillBodyVO body=persisted.get(ref.getObjectPk());
            require(body!=null && active(body.getAttributeValue("dr")) && ref.getObjectPk().equals(text(body.getCgeneralbid()))
                    && pk.equals(text(body.getCgeneralhid())) && body.getIsclosed()!=null,"所选出库申请行不存在或不属于授权单据。");
            actionable|=body.getIsclosed().booleanValue()!=target;bodies.add(body);
            selectedKeys.add(ref.getObjectPk());
        }
        if(requireChange)require(actionable,"所选出库申请行没有可执行的状态变化。");
        return new Loaded(pk,text(head.getVbillcode()),head,bodies.toArray(new SapplyBillBodyVO[bodies.size()]),states,selectedKeys);
    }

    private static void checkHead(OperationContext context,SapplyBillHeadVO head,String pk) throws BusinessException {
        require(head!=null && active(head.getAttributeValue("dr")) && pk.equals(text(head.getCgeneralhid()))
                && context.getGroupId().equals(text(head.getPk_group())) && context.getOrgId().equals(text(head.getPk_org()))
                && text(head.getVbillcode())!=null,"出库申请单主键、组织或单号与授权不一致。");
    }
    private static boolean active(Object dr){return dr==null || dr instanceof Number && ((Number)dr).intValue()==0;}
    private static String text(String value){if(value==null)return null;String s=value.trim();return s.isEmpty()||"~".equals(s)?null:s;}
    private static void requirePk(String pk) throws BusinessException {
        require(pk!=null && pk.matches("[A-Za-z0-9_-]{1,20}"),"出库申请单或行主键无效。");
    }
    private static void require(boolean value,String message) throws BusinessException {if(!value)throw new BusinessException(message);}
    protected void requireBillPermission(BusinessObjectRef bill) throws BusinessException {
        // The site's metadata resource/operation mapping must be verified before enabling this candidate.
        EsignPermission.requireArchiveData(ServerIdentity.current(),"ic_sapply","edit",Collections.singletonList(bill));
    }
    protected int approvedFlag() throws BusinessException {
        try{return ICBillFlag.getAuditedFlag();}catch(LinkageError e){throw new BusinessException("无法读取库存单据状态。",e);}
    }
    protected SapplyBillHeadVO loadHead(String pk) throws BusinessException {return SapplyBillDataAccess.readHead(pk);}
    protected Map<String,SapplyBillBodyVO> loadBodies(String billPk) throws BusinessException {
        Collection<?> rows=SapplyBillDataAccess.readBodies(billPk);
        require(rows!=null,"无法读取出库申请表体。");
        Map<String,SapplyBillBodyVO> result=new HashMap<String,SapplyBillBodyVO>();
        for(Object value:rows) {
            require(value instanceof SapplyBillBodyVO,"出库申请表体类型无效。");
            SapplyBillBodyVO row=(SapplyBillBodyVO)value;String pk=text(row.getCgeneralbid());
            Object dr=row.getAttributeValue("dr");
            if(dr instanceof Number && ((Number)dr).intValue()==1)continue;
            require(pk!=null && billPk.equals(text(row.getCgeneralhid())) && active(dr)
                    && result.put(pk,row)==null,"出库申请表体主键或单据关系无效。");
        }
        return result;
    }
    protected BilltypeVO loadTransactionType(String pk) throws BusinessException {return InventoryTransactionTypes.loadLocked(pk);}
    protected ISapplyBillMaintain maintain(){return NCLocator.getInstance().lookup(ISapplyBillMaintain.class);}
    protected SapplyBillVO createBill(SapplyBillHeadVO head,SapplyBillBodyVO[] bodies) {
        SapplyBillVO bill=new SapplyBillVO();bill.setParent(head);bill.setChildrenVO(bodies);return bill;
    }
    protected SapplyBillVO[] toNativeBill(SapplyBillVO bill) {
        return (SapplyBillVO[])new ClientBillToServer().construct(new SapplyBillVO[]{bill},new SapplyBillVO[]{bill});
    }
    private static final class Loaded {
        final String pk,code;final SapplyBillHeadVO head;final SapplyBillBodyVO[] bodies;
        final Map<String,Boolean> states;final java.util.Set<String> selected;
        Loaded(String pk,String code,SapplyBillHeadVO head,SapplyBillBodyVO[] bodies,Map<String,Boolean> states,java.util.Set<String> selected){
            this.pk=pk;this.code=code;this.head=head;this.bodies=bodies;this.states=states;this.selected=selected;
        }
    }
}
