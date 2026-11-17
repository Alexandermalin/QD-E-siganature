package nc.test.qd.esign;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Map;
import nc.impl.ic.qd.esign.InventoryPfSaveAdapter;
import nc.impl.ic.qd.esign.InventoryPfStateAdapter;
import nc.itf.uap.pf.IplatFormEntry;
import nc.vo.ic.general.define.ICBillHeadVO;
import nc.vo.ic.general.define.ICBillVO;
import nc.vo.ic.m45.entity.PurchaseInVO;
import nc.vo.ic.m40.entity.InitializtionInHeadVO;
import nc.vo.ic.m40.entity.InitializtionInVO;
import nc.vo.ic.pub.define.BillOperator;
import nc.vo.ic.pub.pf.ICPFParameter;
import nc.vo.ic.qd.esign.InventoryPfSaveCommand;
import nc.vo.ic.qd.esign.InventoryPfSaveResult;
import nc.vo.ic.qd.esign.InventoryPfStateCommand;
import nc.vo.ic.qd.esign.InventoryPfStateResult;
import nc.vo.pub.BusinessException;
import nc.vo.pub.IAttributeMeta;
import nc.vo.pub.ISuperVO;
import nc.vo.pub.IVOMeta;
import nc.vo.pubapp.pattern.model.meta.entity.bill.IBillMeta;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;

/** Exercises opening-balance PF delegation and authorization without a database or NC transaction. */
public final class InitialInPfAdapterTests {
    private static final IBillMeta META=new IBillMeta() {
        public Map<String,Object> getBusinessAttribute(){return Collections.emptyMap();}
        public IAttributeMeta[] getChildForeignKeys(){return new IAttributeMeta[0];}
        public IVOMeta[] getChildren(){return new IVOMeta[0];}
        public String getComponentName(){return "INITIAL_IN_PF_TEST";}
        public IVOMeta getParent(){throw new UnsupportedOperationException();}
        public Class<? extends ISuperVO> getVOClass(IVOMeta meta){throw new UnsupportedOperationException();}
        public IVOMeta getVOMeta(Class<? extends ISuperVO> type){throw new UnsupportedOperationException();}
    };
    public static void run() throws Exception {
        InitializtionInVO fresh=bill(null,"G","O",null,"40",2,0);
        InitializtionInVO saved=bill("NEW","G","O","CODE","40",2,0);
        SaveFake save=new SaveFake();save.result=new ICBillVO[]{saved};save.after=saved.getHead();
        OperationContext insert=context("40080402","IC_INITIAL_IN","Save","INSERT",null);
        InventoryPfSaveResult result=(InventoryPfSaveResult)save.execute(insert,saveCommand("40",fresh,BillOperator.New));
        require(save.calls==1 && "WRITE".equals(save.action) && "40".equals(save.type)
            && "NEW".equals(result.getPrimaryKey()) && "CODE".equals(result.getBillCode()),"Opening-balance insert must return persisted identity");
        require("NEW".equals(save.describeResult(insert,result).get(0).getObjectPk()),"New key must be backfilled to authorized object");
        save=new SaveFake();save.result=new ICBillVO[]{saved};save.after=saved.getHead();
        save.execute(insert,saveCommand("40",bill(null,"G","O",null,null,2,0),BillOperator.New));
        require(save.calls==1,"A new draft may receive its base transaction type during native save");

        InitializtionInVO existing=bill("A","G","O","CODE","40",2,0);
        OperationContext update=context("40080402","IC_INITIAL_IN","Save","UPDATE","A");
        save=new SaveFake();save.before=existing.getHead();save.after=existing.getHead();save.result=new ICBillVO[]{existing};
        save.execute(update,saveCommand("40",existing,BillOperator.Edit));
        require(save.calls==1 && "40".equals(save.type),"Matching opening balance base type must reach native PF");

        SaveFake bad=new SaveFake();bad.before=existing.getHead();
        rejectSave(bad,context("40080602400","IC_INITIAL_IN","Save","UPDATE","A"),saveCommand("40",existing,BillOperator.Edit));
        rejectSave(bad,context("40080402","IC_SALE_OUT","Save","UPDATE","A"),saveCommand("40",existing,BillOperator.Edit));
        rejectSave(bad,update,saveCommand("40-01",bill("A","G","O","CODE","40-01",2,0),BillOperator.Edit));
        rejectSave(bad,update,saveCommand("40",bill("A","G","OTHER","CODE","40",2,0),BillOperator.Edit));
        rejectSave(bad,update,saveCommand("40",new PurchaseInVO(){public IBillMeta getMetaData(){return META;}},BillOperator.Edit));
        require(bad.calls==0,"Invalid opening balance save must not reach native PF");
        bad=new SaveFake();bad.before=bill("A","G","O","CODE","40-02",2,0).getHead();
        rejectSave(bad,update,saveCommand("40",existing,BillOperator.Edit));
        require(bad.calls==0,"A different persisted transaction type must not reach native PF");
        bad=new SaveFake();bad.before=bill("A","G","O","CODE",null,2,0).getHead();
        rejectSave(bad,update,saveCommand("40",existing,BillOperator.Edit));
        require(bad.calls==0,"An unknown persisted transaction type must not reach native PF");
        bad=new SaveFake();bad.before=existing.getHead();bad.after=bill("A","G","O","CODE","40-02",2,0).getHead();
        bad.result=new ICBillVO[]{existing};
        try {bad.execute(update,saveCommand("40",existing,BillOperator.Edit));throw new AssertionError("Changed persisted transaction type accepted");}
        catch(BusinessException expected) { }
        require(bad.calls==1,"Post-save transaction type must be checked after native PF");
        bad=new SaveFake();bad.before=existing.getHead();bad.after=saved.getHead();bad.result=new ICBillVO[]{saved};
        try {bad.execute(update,saveCommand("40",existing,BillOperator.Edit));throw new AssertionError("Different returned key accepted");}
        catch(BusinessException expected) { }

        for(String button:new String[]{"Delete","Sign","CancelSign"}) {
            String op="Delete".equals(button)?"DELETE":"Sign".equals(button)?"SIGN":"CANCEL_SIGN";
            OperationContext state=context("40080402","IC_INITIAL_IN",button,op,"A");
            InitializtionInVO input=bill("A","G","O","CODE","40",2,0);
            StateFake adapter=new StateFake();adapter.before=input.getHead();
            adapter.after="Delete".equals(button)?null:bill("A","G","O","CODE","40","Sign".equals(button)?3:2,0).getHead();
            adapter.result="Delete".equals(button)?null:new ICBillVO[]{input};
            InventoryPfStateResult outcome=(InventoryPfStateResult)adapter.execute(state,new InventoryPfStateCommand("40",input,null,null,null));
            String expectedAction="CancelSign".equals(button)?"CANCELSIGN":button.toUpperCase();
            require(adapter.calls==1 && expectedAction.equals(adapter.action) && "A".equals(outcome.getPrimaryKey())
                && outcome.isDeleted()=="Delete".equals(button),"Opening balance state action must use original PF and identity");
            require("item".equals(adapter.describeResult(state,outcome).get(0).getClientObjectId()),"Opening balance state result lost authorization identity");
            StateFake wrong=new StateFake();wrong.before=input.getHead();
            try {wrong.validate(state,new InventoryPfStateCommand("45",input,null,null,null));throw new AssertionError("Wrong opening balance bill type accepted");}
            catch(BusinessException expected) { }
            try {wrong.validate(state,new InventoryPfStateCommand("40-01",bill("A","G","O","CODE","40-01",2,0),null,null,null));throw new AssertionError("Unverified custom state type accepted");}
            catch(BusinessException expected) { }
            require(wrong.calls==0,"Invalid opening balance state must not reach native PF");
            wrong=new StateFake();wrong.before=bill("A","G","O","CODE","40-02",2,0).getHead();
            try {wrong.validate(state,new InventoryPfStateCommand("40",input,null,null,null));throw new AssertionError("Different persisted state type accepted");}
            catch(BusinessException expected) { }
            require(wrong.calls==0,"A different persisted state type must not reach native PF");
            wrong=new StateFake();wrong.before=bill("A","G","O","CODE",null,2,0).getHead();
            try {wrong.validate(state,new InventoryPfStateCommand("40",input,null,null,null));throw new AssertionError("Unknown persisted state type accepted");}
            catch(BusinessException expected) { }
            require(wrong.calls==0,"An unknown persisted state type must not reach native PF");
            wrong=new StateFake();wrong.before=input.getHead();
            wrong.after=bill("A","G","O","CODE","40-02","Sign".equals(button)?3:2,"Delete".equals(button)?1:0).getHead();
            try {wrong.execute(state,new InventoryPfStateCommand("40",input,null,null,null));throw new AssertionError("Changed persisted state type accepted");}
            catch(BusinessException expected) { }
            require(wrong.calls==1,"Post-action transaction type must be checked after native PF");
        }
        System.out.println("PASS InitialInPfAdapterTests (typed PF delegation and scope; no NC or Oracle)");
    }
    private static void rejectSave(SaveFake adapter,OperationContext context,InventoryPfSaveCommand command) throws Exception {
        try {adapter.validate(context,command);throw new AssertionError("Invalid opening balance save accepted");}catch(BusinessException expected) { }
    }
    private static OperationContext context(String node,String type,String button,String op,String pk) {
        return new OperationContext("request","batch","G","O",node,"opening balance",button,button,op,null,
            Collections.singletonList(new BusinessObjectRef("item",type,pk,null,null)));
    }
    private static InventoryPfSaveCommand saveCommand(String type,ICBillVO bill,BillOperator operator) {
        ICPFParameter user=new ICPFParameter();user.setBillaction(operator);
        return new InventoryPfSaveCommand(type,bill,user,null,Collections.<String,Object>emptyMap());
    }
    private static InitializtionInVO bill(final String pk,final String group,final String org,final String code,
            final String type,final int flag,final int dr) {
        final InitializtionInHeadVO head=new InitializtionInHeadVO() {
            public String getCgeneralhid(){return pk;} public String getPk_group(){return group;}
            public String getPk_org(){return org;} public String getVbillcode(){return code;}
            public String getVtrantypecode(){return type;} public Integer getFbillflag(){return Integer.valueOf(flag);}
            public Object getAttributeValue(String name){return "dr".equals(name)?Integer.valueOf(dr):null;}
        };
        return new InitializtionInVO(){public IBillMeta getMetaData(){return META;}public InitializtionInHeadVO getHead(){return head;}};
    }
    private static IplatFormEntry entry(final Capture owner) {
        return (IplatFormEntry)Proxy.newProxyInstance(IplatFormEntry.class.getClassLoader(),new Class<?>[]{IplatFormEntry.class},
            new InvocationHandler(){public Object invoke(Object proxy,Method method,Object[] args){
                if(!"processAction".equals(method.getName()))throw new AssertionError(method.getName());
                owner.called((String)args[0],(String)args[1]);return owner.result();}});
    }
    private interface Capture {void called(String action,String type);Object result();}
    private static final class SaveFake extends InventoryPfSaveAdapter implements Capture {
        ICBillHeadVO before,after;Object result;String action,type;int calls;
        protected ICBillHeadVO loadInitialInHeader(String pk){return calls==0?before:after;}
        protected ICBillHeadVO loadHeader(boolean purchase,String pk){throw new AssertionError("Wrong header service");}
        protected IplatFormEntry platformEntry(){return entry(this);}
        public void called(String value,String billType){calls++;action=value;type=billType;}public Object result(){return result;}
    }
    private static final class StateFake extends InventoryPfStateAdapter implements Capture {
        ICBillHeadVO before,after;Object result;String action;int calls;
        protected ICBillHeadVO loadInitialInHeader(String pk){return calls==0?before:after;}
        protected ICBillHeadVO loadHeader(boolean purchase,String pk){throw new AssertionError("Wrong header service");}
        protected int expectedStatus(boolean sign){return sign?3:2;}
        protected IplatFormEntry platformEntry(){return entry(this);}
        public void called(String value,String billType){calls++;action=value;}public Object result(){return result;}
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
