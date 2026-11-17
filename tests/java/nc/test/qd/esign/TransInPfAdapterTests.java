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
import nc.vo.ic.m4e.entity.TransInHeadVO;
import nc.vo.ic.m4e.entity.TransInVO;
import nc.vo.ic.pub.define.BillOperator;
import nc.vo.ic.pub.pf.ICPFParameter;
import nc.vo.ic.qd.esign.InventoryPfSaveCommand;
import nc.vo.ic.qd.esign.InventoryPfSaveResult;
import nc.vo.ic.qd.esign.InventoryPfStateCommand;
import nc.vo.ic.qd.esign.InventoryPfStateResult;
import nc.vo.pub.BusinessException;
import nc.vo.pub.billtype.BilltypeVO;
import nc.vo.pub.IAttributeMeta;
import nc.vo.pub.ISuperVO;
import nc.vo.pub.IVOMeta;
import nc.vo.pubapp.pattern.model.meta.entity.bill.IBillMeta;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;

/** Exercises transfer-in PF delegation and authorization without a database or NC transaction. */
public final class TransInPfAdapterTests {
    private static final IBillMeta META=new IBillMeta() {
        public Map<String,Object> getBusinessAttribute(){return Collections.emptyMap();}
        public IAttributeMeta[] getChildForeignKeys(){return new IAttributeMeta[0];}
        public IVOMeta[] getChildren(){return new IVOMeta[0];}
        public String getComponentName(){return "TRANS_IN_PF_TEST";}
        public IVOMeta getParent(){throw new UnsupportedOperationException();}
        public Class<? extends ISuperVO> getVOClass(IVOMeta meta){throw new UnsupportedOperationException();}
        public IVOMeta getVOMeta(Class<? extends ISuperVO> type){throw new UnsupportedOperationException();}
    };
    public static void run() throws Exception {
        TransInVO fresh=bill(null,"G","O",null,"4E",2,0);
        TransInVO saved=bill("NEW","G","O","CODE","4E",2,0);
        SaveFake save=new SaveFake();save.result=new ICBillVO[]{saved};save.after=saved.getHead();
        OperationContext insert=context("40080618","IC_TRANS_IN","Save","INSERT",null);
        InventoryPfSaveResult result=(InventoryPfSaveResult)save.execute(insert,saveCommand("4E",fresh,BillOperator.New));
        require(save.calls==1 && "WRITE".equals(save.action) && "4E".equals(save.type)
            && "NEW".equals(result.getPrimaryKey()) && "CODE".equals(result.getBillCode()),"Transfer-in insert must return persisted identity");
        require("NEW".equals(save.describeResult(insert,result).get(0).getObjectPk()),"New key must be backfilled to authorized object");
        save=new SaveFake();save.result=new ICBillVO[]{saved};save.after=saved.getHead();
        save.execute(insert,saveCommand("4E",bill(null,"G","O",null,null,2,0),BillOperator.New));
        require(save.calls==1,"A new draft may receive its base transaction type during native save");

        TransInVO existing=bill("A","G","O","CODE","4E",2,0);
        OperationContext update=context("40080618","IC_TRANS_IN","Save","UPDATE","A");
        save=new SaveFake();save.before=existing.getHead();save.after=existing.getHead();save.result=new ICBillVO[]{existing};
        save.execute(update,saveCommand("4E",existing,BillOperator.Edit));
        require(save.calls==1 && "4E".equals(save.type),"Matching transfer base type must reach native PF");

        TransInVO subtype=bill("A","G","O","CODE","4E-01",2,0,"TYPE-PK");
        save=new SaveFake();save.before=subtype.getHead();save.after=subtype.getHead();save.result=new ICBillVO[]{subtype};
        save.metadata=transactionType("40080618");
        save.execute(update,saveCommand("4E-01",subtype,BillOperator.Edit));
        require(save.calls==1 && save.typeChecks>=3,"Authorized subtype must be checked before and after native save");
        SaveFake crossNode=new SaveFake();crossNode.before=subtype.getHead();crossNode.metadata=transactionType("OTHER");
        rejectSave(crossNode,update,saveCommand("4E-01",subtype,BillOperator.Edit));
        require(crossNode.calls==0,"A subtype for another node must not reach native PF");
        SaveFake mismatchedTypeKey=new SaveFake();
        mismatchedTypeKey.before=bill("A","G","O","CODE","4E-01",2,0,"OTHER-TYPE").getHead();
        mismatchedTypeKey.metadata=transactionType("40080618");
        mismatchedTypeKey.alternateMetadata=transactionType("40080618","OTHER-TYPE","4E-01");
        rejectSave(mismatchedTypeKey,update,saveCommand("4E-01",subtype,BillOperator.Edit));
        require(mismatchedTypeKey.calls==0,"A different stored type identity must not reach native PF even if its code matches");
        mismatchedTypeKey=new SaveFake();mismatchedTypeKey.before=subtype.getHead();
        mismatchedTypeKey.after=bill("A","G","O","CODE","4E-01",2,0,"OTHER-TYPE").getHead();
        mismatchedTypeKey.result=new ICBillVO[]{subtype};mismatchedTypeKey.metadata=transactionType("40080618");
        mismatchedTypeKey.alternateMetadata=transactionType("40080618","OTHER-TYPE","4E-01");
        try {mismatchedTypeKey.execute(update,saveCommand("4E-01",subtype,BillOperator.Edit));
            throw new AssertionError("Changed persisted type identity accepted");}
        catch(BusinessException expected) { }
        require(mismatchedTypeKey.calls==1,"The saved type identity must be checked after native PF");
        final String[] mutableTypeKey=new String[]{"TYPE-PK"};
        TransInVO mutable=mutableBill(mutableTypeKey);
        SaveFake mutatedSave=new SaveFake();mutatedSave.before=subtype.getHead();
        mutatedSave.after=bill("A","G","O","CODE","4E-01",2,0,"OTHER-TYPE").getHead();
        mutatedSave.result=new ICBillVO[]{mutable};mutatedSave.metadata=transactionType("40080618");
        mutatedSave.alternateMetadata=transactionType("40080618","OTHER-TYPE","4E-01");
        mutatedSave.duringCall=new Runnable(){public void run(){mutableTypeKey[0]="OTHER-TYPE";}};
        try {mutatedSave.execute(update,saveCommand("4E-01",mutable,BillOperator.Edit));
            throw new AssertionError("A native mutation of the submitted type identity was accepted");}
        catch(BusinessException expected) { }
        require(mutatedSave.calls==1,"Submitted type identity must be frozen before native save");

        SaveFake bad=new SaveFake();bad.before=existing.getHead();
        rejectSave(bad,context("40080602400","IC_TRANS_IN","Save","UPDATE","A"),saveCommand("4E",existing,BillOperator.Edit));
        rejectSave(bad,context("40080618","IC_SALE_OUT","Save","UPDATE","A"),saveCommand("4E",existing,BillOperator.Edit));
        rejectSave(bad,update,saveCommand("4E-01",bill("A","G","O","CODE","4E-01",2,0),BillOperator.Edit));
        rejectSave(bad,update,saveCommand("4E",bill("A","G","OTHER","CODE","4E",2,0),BillOperator.Edit));
        rejectSave(bad,update,saveCommand("4E",new PurchaseInVO(){public IBillMeta getMetaData(){return META;}},BillOperator.Edit));
        require(bad.calls==0,"Invalid transfer save must not reach native PF");
        bad=new SaveFake();bad.before=bill("A","G","O","CODE","4E-02",2,0).getHead();
        rejectSave(bad,update,saveCommand("4E",existing,BillOperator.Edit));
        require(bad.calls==0,"A different persisted transaction type must not reach native PF");
        bad=new SaveFake();bad.before=bill("A","G","O","CODE",null,2,0).getHead();
        rejectSave(bad,update,saveCommand("4E",existing,BillOperator.Edit));
        require(bad.calls==0,"An unknown persisted transaction type must not reach native PF");
        bad=new SaveFake();bad.before=existing.getHead();bad.after=bill("A","G","O","CODE","4E-02",2,0).getHead();
        bad.result=new ICBillVO[]{existing};
        try {bad.execute(update,saveCommand("4E",existing,BillOperator.Edit));throw new AssertionError("Changed persisted transaction type accepted");}
        catch(BusinessException expected) { }
        require(bad.calls==1,"Post-save transaction type must be checked after native PF");
        bad=new SaveFake();bad.before=existing.getHead();bad.after=saved.getHead();bad.result=new ICBillVO[]{saved};
        try {bad.execute(update,saveCommand("4E",existing,BillOperator.Edit));throw new AssertionError("Different returned key accepted");}
        catch(BusinessException expected) { }

        for(String button:new String[]{"Delete","Sign","CancelSign"}) {
            String op="Delete".equals(button)?"DELETE":"Sign".equals(button)?"SIGN":"CANCEL_SIGN";
            OperationContext state=context("40080618","IC_TRANS_IN",button,op,"A");
            TransInVO input=bill("A","G","O","CODE","4E",2,0);
            StateFake adapter=new StateFake();adapter.before=input.getHead();
            adapter.after="Delete".equals(button)?null:bill("A","G","O","CODE","4E","Sign".equals(button)?3:2,0).getHead();
            adapter.result="Delete".equals(button)?null:new ICBillVO[]{input};
            InventoryPfStateResult outcome=(InventoryPfStateResult)adapter.execute(state,new InventoryPfStateCommand("4E",input,null,null,null));
            String expectedAction="CancelSign".equals(button)?"CANCELSIGN":button.toUpperCase();
            require(adapter.calls==1 && expectedAction.equals(adapter.action) && "A".equals(outcome.getPrimaryKey())
                && outcome.isDeleted()=="Delete".equals(button),"Transfer state action must use original PF and identity");
            require("item".equals(adapter.describeResult(state,outcome).get(0).getClientObjectId()),"Transfer state result lost authorization identity");
            StateFake wrong=new StateFake();wrong.before=input.getHead();
            try {wrong.validate(state,new InventoryPfStateCommand("45",input,null,null,null));throw new AssertionError("Wrong transfer bill type accepted");}
            catch(BusinessException expected) { }
            try {wrong.validate(state,new InventoryPfStateCommand("4E-01",bill("A","G","O","CODE","4E-01",2,0),null,null,null));throw new AssertionError("Unverified custom state type accepted");}
            catch(BusinessException expected) { }
            require(wrong.calls==0,"Invalid transfer state must not reach native PF");
            wrong=new StateFake();wrong.before=bill("A","G","O","CODE","4E-02",2,0).getHead();
            try {wrong.validate(state,new InventoryPfStateCommand("4E",input,null,null,null));throw new AssertionError("Different persisted state type accepted");}
            catch(BusinessException expected) { }
            require(wrong.calls==0,"A different persisted state type must not reach native PF");
            wrong=new StateFake();wrong.before=bill("A","G","O","CODE",null,2,0).getHead();
            try {wrong.validate(state,new InventoryPfStateCommand("4E",input,null,null,null));throw new AssertionError("Unknown persisted state type accepted");}
            catch(BusinessException expected) { }
            require(wrong.calls==0,"An unknown persisted state type must not reach native PF");
            wrong=new StateFake();wrong.before=input.getHead();
            wrong.after=bill("A","G","O","CODE","4E-02","Sign".equals(button)?3:2,"Delete".equals(button)?1:0).getHead();
            try {wrong.execute(state,new InventoryPfStateCommand("4E",input,null,null,null));throw new AssertionError("Changed persisted state type accepted");}
            catch(BusinessException expected) { }
            require(wrong.calls==1,"Post-action transaction type must be checked after native PF");
        }
        OperationContext sign=context("40080618","IC_TRANS_IN","Sign","SIGN","A");
        StateFake subtypeState=new StateFake();subtypeState.before=subtype.getHead();
        subtypeState.after=bill("A","G","O","CODE","4E-01",3,0,"TYPE-PK").getHead();
        subtypeState.result=new ICBillVO[]{subtype};subtypeState.metadata=transactionType("40080618");
        subtypeState.execute(sign,new InventoryPfStateCommand("4E-01",subtype,null,null,null));
        require(subtypeState.calls==1 && subtypeState.typeChecks>=3,"Authorized subtype must be checked before and after native state action");
        StateFake changedIdentity=new StateFake();
        changedIdentity.before=bill("A","G","O","CODE","4E-01",2,0,"OTHER-TYPE").getHead();
        changedIdentity.metadata=transactionType("40080618");
        changedIdentity.alternateMetadata=transactionType("40080618","OTHER-TYPE","4E-01");
        try {changedIdentity.validate(sign,new InventoryPfStateCommand("4E-01",subtype,null,null,null));
            throw new AssertionError("Different stored state type identity accepted");}
        catch(BusinessException expected) { }
        require(changedIdentity.calls==0,"Different stored type identity must not reach state PF");
        changedIdentity=new StateFake();changedIdentity.before=subtype.getHead();changedIdentity.after=subtypeState.after;
        changedIdentity.result=new ICBillVO[]{bill("A","G","O","CODE","4E-01",2,0,"OTHER-TYPE")};
        changedIdentity.metadata=transactionType("40080618");
        changedIdentity.alternateMetadata=transactionType("40080618","OTHER-TYPE","4E-01");
        try {changedIdentity.execute(sign,new InventoryPfStateCommand("4E-01",subtype,null,null,null));
            throw new AssertionError("Different returned state type identity accepted");}
        catch(BusinessException expected) { }
        require(changedIdentity.calls==1,"Returned state type identity must be checked after PF");
        final String[] stateTypeKey=new String[]{"TYPE-PK"};TransInVO mutableState=mutableBill(stateTypeKey);
        changedIdentity=new StateFake();changedIdentity.before=subtype.getHead();
        changedIdentity.after=bill("A","G","O","CODE","4E-01",3,0,"OTHER-TYPE").getHead();
        changedIdentity.result=new ICBillVO[]{mutableState};changedIdentity.metadata=transactionType("40080618");
        changedIdentity.alternateMetadata=transactionType("40080618","OTHER-TYPE","4E-01");
        changedIdentity.duringCall=new Runnable(){public void run(){stateTypeKey[0]="OTHER-TYPE";}};
        try {changedIdentity.execute(sign,new InventoryPfStateCommand("4E-01",mutableState,null,null,null));
            throw new AssertionError("A native mutation of the submitted state identity was accepted");}
        catch(BusinessException expected) { }
        require(changedIdentity.calls==1,"Submitted type identity must be frozen before the native state action");
        System.out.println("PASS TransInPfAdapterTests (typed PF delegation and scope; no NC or Oracle)");
    }
    private static void rejectSave(SaveFake adapter,OperationContext context,InventoryPfSaveCommand command) throws Exception {
        try {adapter.validate(context,command);throw new AssertionError("Invalid transfer save accepted");}catch(BusinessException expected) { }
    }
    private static OperationContext context(String node,String type,String button,String op,String pk) {
        return new OperationContext("request","batch","G","O",node,"transfer",button,button,op,null,
            Collections.singletonList(new BusinessObjectRef("item",type,pk,null,null)));
    }
    private static InventoryPfSaveCommand saveCommand(String type,ICBillVO bill,BillOperator operator) {
        ICPFParameter user=new ICPFParameter();user.setBillaction(operator);
        return new InventoryPfSaveCommand(type,bill,user,null,Collections.<String,Object>emptyMap());
    }
    private static TransInVO bill(final String pk,final String group,final String org,final String code,
            final String type,final int flag,final int dr) {
        return bill(pk,group,org,code,type,flag,dr,null);
    }
    private static TransInVO bill(final String pk,final String group,final String org,final String code,
            final String type,final int flag,final int dr,final String typePk) {
        final TransInHeadVO head=new TransInHeadVO() {
            public String getCgeneralhid(){return pk;} public String getPk_group(){return group;}
            public String getPk_org(){return org;} public String getVbillcode(){return code;}
            public String getVtrantypecode(){return type;} public Integer getFbillflag(){return Integer.valueOf(flag);}
            public String getCtrantypeid(){return typePk;}
            public Object getAttributeValue(String name){return "dr".equals(name)?Integer.valueOf(dr):null;}
        };
        return new TransInVO(){public IBillMeta getMetaData(){return META;}public TransInHeadVO getHead(){return head;}};
    }
    private static BilltypeVO transactionType(String node) {
        return transactionType(node,"TYPE-PK","4E-01");
    }
    private static BilltypeVO transactionType(String node,String pk,String code) {
        BilltypeVO row=new BilltypeVO();row.setPrimaryKey(pk);row.setPk_billtypecode(code);
        row.setPk_group("G");row.setNodecode(node);row.setParentbilltype("4E");row.setComponent("ic_transin");
        row.setDr(Integer.valueOf(0));return row;
    }
    private static TransInVO mutableBill(final String[] typeKey) {
        final TransInHeadVO head=new TransInHeadVO() {
            public String getCgeneralhid(){return "A";} public String getPk_group(){return "G";}
            public String getPk_org(){return "O";} public String getVbillcode(){return "CODE";}
            public String getVtrantypecode(){return "4E-01";} public String getCtrantypeid(){return typeKey[0];}
            public Integer getFbillflag(){return Integer.valueOf(2);}
            public Object getAttributeValue(String name){return "dr".equals(name)?Integer.valueOf(0):null;}
        };
        return new TransInVO(){public IBillMeta getMetaData(){return META;}public TransInHeadVO getHead(){return head;}};
    }
    private static IplatFormEntry entry(final Capture owner) {
        return (IplatFormEntry)Proxy.newProxyInstance(IplatFormEntry.class.getClassLoader(),new Class<?>[]{IplatFormEntry.class},
            new InvocationHandler(){public Object invoke(Object proxy,Method method,Object[] args){
                if(!"processAction".equals(method.getName()))throw new AssertionError(method.getName());
                owner.called((String)args[0],(String)args[1]);return owner.result();}});
    }
    private interface Capture {void called(String action,String type);Object result();}
    private static final class SaveFake extends InventoryPfSaveAdapter implements Capture {
        ICBillHeadVO before,after;Object result;String action,type;int calls,typeChecks;BilltypeVO metadata,alternateMetadata;Runnable duringCall;
        protected ICBillHeadVO loadTransInHeader(String pk){return calls==0?before:after;}
        protected ICBillHeadVO loadHeader(boolean purchase,String pk){throw new AssertionError("Wrong header service");}
        protected BilltypeVO loadTransactionType(String pk){typeChecks++;return alternateMetadata!=null && pk.equals(alternateMetadata.getPrimaryKey())?alternateMetadata:metadata;}
        protected IplatFormEntry platformEntry(){return entry(this);}
        public void called(String value,String billType){calls++;action=value;type=billType;if(duringCall!=null)duringCall.run();}public Object result(){return result;}
    }
    private static final class StateFake extends InventoryPfStateAdapter implements Capture {
        ICBillHeadVO before,after;Object result;String action;int calls,typeChecks;BilltypeVO metadata,alternateMetadata;Runnable duringCall;
        protected ICBillHeadVO loadTransInHeader(String pk){return calls==0?before:after;}
        protected ICBillHeadVO loadHeader(boolean purchase,String pk){throw new AssertionError("Wrong header service");}
        protected BilltypeVO loadTransactionType(String pk){typeChecks++;return alternateMetadata!=null && pk.equals(alternateMetadata.getPrimaryKey())?alternateMetadata:metadata;}
        protected int expectedStatus(boolean sign){return sign?3:2;}
        protected IplatFormEntry platformEntry(){return entry(this);}
        public void called(String value,String billType){calls++;action=value;if(duringCall!=null)duringCall.run();}public Object result(){return result;}
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
