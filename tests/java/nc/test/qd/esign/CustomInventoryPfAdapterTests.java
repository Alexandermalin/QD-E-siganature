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
import nc.vo.ic.m45.entity.PurchaseInHeadVO;
import nc.vo.ic.m45.entity.PurchaseInVO;
import nc.vo.ic.m46.entity.FinProdInHeadVO;
import nc.vo.ic.m46.entity.FinProdInVO;
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
import nc.vo.pub.billtype.BilltypeVO;
import nc.vo.pubapp.pattern.model.meta.entity.bill.IBillMeta;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;

/** Exercises the two custom node/type scopes and native PF delegation without NC or Oracle. */
public final class CustomInventoryPfAdapterTests {
    private static final IBillMeta META = new IBillMeta() {
        public Map<String,Object> getBusinessAttribute(){return Collections.emptyMap();}
        public IAttributeMeta[] getChildForeignKeys(){return new IAttributeMeta[0];}
        public IVOMeta[] getChildren(){return new IVOMeta[0];}
        public String getComponentName(){return "CUSTOM_INVENTORY_TEST";}
        public IVOMeta getParent(){throw new UnsupportedOperationException();}
        public Class<? extends ISuperVO> getVOClass(IVOMeta meta){throw new UnsupportedOperationException();}
        public IVOMeta getVOMeta(Class<? extends ISuperVO> type){throw new UnsupportedOperationException();}
    };
    public static void run() throws Exception {
        for (boolean purchase : new boolean[] {true, false}) {
            String node=purchase?"40080602400":"40080604400";
            String type=purchase?"IC_OUTSOURCE_IN":"IC_PRODUCTION_ORDER";
            String code=purchase?"45-Cxx-08":"46-Cxx-2";
            ICBillVO fresh=bill(purchase,null,null,code,"TYPE",2,0);
            ICBillVO saved=bill(purchase,"NEW","CODE",code,"TYPE",2,0);
            SaveFake insert=new SaveFake(purchase,metadata(purchase,node,code));
            insert.after=saved.getHead();insert.raw=new ICBillVO[]{saved};
            OperationContext insertContext=context(node,type,"Save","INSERT",null);
            InventoryPfSaveResult result=(InventoryPfSaveResult)insert.execute(insertContext,save(code,fresh,BillOperator.New));
            require(insert.calls==1 && "WRITE".equals(insert.action) && code.equals(insert.billType)
                    && "NEW".equals(result.getPrimaryKey()) && "CODE".equals(result.getBillCode()),
                    "Custom insert must retain the exact subtype and backfill persisted identity");
            ICBillVO existing=bill(purchase,"A","CODE",code,"TYPE",2,0);
            OperationContext update=context(node,type,"Save","UPDATE","A");
            SaveFake changed=new SaveFake(purchase,metadata(purchase,node,code));
            changed.before=existing.getHead();changed.after=existing.getHead();changed.raw=new ICBillVO[]{existing};
            changed.execute(update,save(code,existing,BillOperator.Edit));
            require(changed.calls==1,"Custom update must invoke native PF once");
            for(String button:new String[]{"Delete","Sign","CancelSign"}) {
                String op="Delete".equals(button)?"DELETE":"Sign".equals(button)?"SIGN":"CANCEL_SIGN";
                StateFake state=new StateFake(purchase,metadata(purchase,node,code));state.before=existing.getHead();
                state.after="Delete".equals(button)?null:bill(purchase,"A","CODE",code,"TYPE","Sign".equals(button)?3:2,0).getHead();
                state.raw="Delete".equals(button)?null:new ICBillVO[]{existing};
                InventoryPfStateResult stateResult=(InventoryPfStateResult)state.execute(context(node,type,button,op,"A"),
                        new InventoryPfStateCommand(code,existing,null,null,null));
                require(state.calls==1 && "A".equals(stateResult.getPrimaryKey())
                        && stateResult.isDeleted()=="Delete".equals(button),"Custom state action must retain the signed bill");
            }
            SaveFake denied=new SaveFake(purchase,metadata(purchase,node,code));denied.before=existing.getHead();
            reject(denied,update,save(purchase?"45":"46",existing,BillOperator.Edit));
            reject(denied,update,save(purchase?"45-OTHER":"46-OTHER",existing,BillOperator.Edit));
            reject(denied,update,save(code,bill(purchase,"A","CODE",code,null,2,0),BillOperator.Edit));
            reject(denied,context(purchase?"40080602":"40080604",type,"Save","UPDATE","A"),save(code,existing,BillOperator.Edit));
            denied.metadata=metadata(purchase,purchase?"40080602":"40080604",code);
            reject(denied,update,save(code,existing,BillOperator.Edit));
            denied.metadata=metadata(purchase,node,code);denied.metadata.setPk_group("OTHER");
            reject(denied,update,save(code,existing,BillOperator.Edit));
            require(denied.calls==0,"Unmatched custom subtype, metadata, node or type key must not reach PF");
        }
        System.out.println("PASS CustomInventoryPfAdapterTests (distinct custom nodes and exact subtypes; no NC or Oracle)");
    }
    private static void reject(SaveFake adapter,OperationContext context,InventoryPfSaveCommand command) throws Exception {
        try {adapter.validate(context,command);throw new AssertionError("Invalid custom inventory scope accepted");}
        catch(BusinessException expected) { }
    }
    private static InventoryPfSaveCommand save(String type,ICBillVO bill,BillOperator operation) {
        ICPFParameter user=new ICPFParameter();user.setBillaction(operation);
        return new InventoryPfSaveCommand(type,bill,user,null,Collections.<String,Object>emptyMap());
    }
    private static OperationContext context(String node,String type,String button,String operation,String pk) {
        return new OperationContext("R","B","G","O",node,"custom",button,button,operation,null,
                Collections.singletonList(new BusinessObjectRef("item",type,pk,pk==null?null:"CODE",null)));
    }
    private static BilltypeVO metadata(boolean purchase,String node,String code) {
        BilltypeVO row=new BilltypeVO();row.setPrimaryKey("TYPE");row.setPk_billtypecode(code);row.setPk_group("G");
        row.setNodecode(node);row.setParentbilltype(purchase?"45":"46");
        row.setComponent(purchase?"ic_purchasein":"ic_finprodin");row.setDr(Integer.valueOf(0));return row;
    }
    private static ICBillVO bill(boolean purchase,final String pk,final String number,final String type,
            final String typePk,final int flag,final int dr) {
        if(purchase) {
            final PurchaseInHeadVO head=new PurchaseInHeadVO() {
                public String getCgeneralhid(){return pk;} public String getPk_group(){return "G";}
                public String getPk_org(){return "O";} public String getVbillcode(){return number;}
                public String getVtrantypecode(){return type;} public String getCtrantypeid(){return typePk;}
                public Integer getFbillflag(){return Integer.valueOf(flag);}
                public Object getAttributeValue(String key){return "dr".equals(key)?Integer.valueOf(dr):null;}
            };
            return new PurchaseInVO(){public IBillMeta getMetaData(){return META;}public PurchaseInHeadVO getHead(){return head;}};
        }
        final FinProdInHeadVO head=new FinProdInHeadVO() {
            public String getCgeneralhid(){return pk;} public String getPk_group(){return "G";}
            public String getPk_org(){return "O";} public String getVbillcode(){return number;}
            public String getVtrantypecode(){return type;} public String getCtrantypeid(){return typePk;}
            public Integer getFbillflag(){return Integer.valueOf(flag);}
            public Object getAttributeValue(String key){return "dr".equals(key)?Integer.valueOf(dr):null;}
        };
        return new FinProdInVO(){public IBillMeta getMetaData(){return META;}public FinProdInHeadVO getHead(){return head;}};
    }
    private interface Calls {Object invoke(String action,String billType);}
    private static IplatFormEntry platform(final Calls calls) {
        return (IplatFormEntry)Proxy.newProxyInstance(IplatFormEntry.class.getClassLoader(),new Class<?>[]{IplatFormEntry.class},
                new InvocationHandler(){public Object invoke(Object proxy,Method method,Object[] args) {
                    if(!"processAction".equals(method.getName()))throw new AssertionError(method.getName());
                    return calls.invoke((String)args[0],(String)args[1]);
                }});
    }
    private static final class SaveFake extends InventoryPfSaveAdapter implements Calls {
        final boolean purchase;BilltypeVO metadata;ICBillHeadVO before,after;Object raw;int calls;String action,billType;
        SaveFake(boolean purchase,BilltypeVO metadata){this.purchase=purchase;this.metadata=metadata;}
        protected ICBillHeadVO loadHeader(boolean p,String pk){require(purchase && p,"Wrong purchase header route");return calls==0?before:after;}
        protected ICBillHeadVO loadProductInHeader(String pk){require(!purchase,"Wrong production header route");return calls==0?before:after;}
        protected BilltypeVO loadTransactionType(String pk){return metadata;}
        protected IplatFormEntry platformEntry(){return platform(this);}
        public Object invoke(String action,String type){calls++;this.action=action;billType=type;return raw;}
    }
    private static final class StateFake extends InventoryPfStateAdapter implements Calls {
        final boolean purchase;final BilltypeVO metadata;ICBillHeadVO before,after;Object raw;int calls;
        StateFake(boolean purchase,BilltypeVO metadata){this.purchase=purchase;this.metadata=metadata;}
        protected ICBillHeadVO loadHeader(boolean p,String pk){require(purchase && p,"Wrong purchase header route");return calls==0?before:after;}
        protected ICBillHeadVO loadProductInHeader(String pk){require(!purchase,"Wrong production header route");return calls==0?before:after;}
        protected BilltypeVO loadTransactionType(String pk){return metadata;}
        protected int expectedStatus(boolean sign){return sign?3:2;}
        protected IplatFormEntry platformEntry(){return platform(this);}
        public Object invoke(String action,String type){calls++;return raw;}
    }
    private static void require(boolean valid,String message){if(!valid)throw new AssertionError(message);}
}
