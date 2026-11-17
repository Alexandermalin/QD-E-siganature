package nc.test.qd.esign;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import nc.impl.ic.qd.esign.SapplyLineOperationAdapter;
import nc.itf.ic.m4455.self.ISapplyBillMaintain;
import nc.vo.ic.m4455.entity.SapplyBillBodyVO;
import nc.vo.ic.m4455.entity.SapplyBillHeadVO;
import nc.vo.ic.m4455.entity.SapplyBillVO;
import nc.vo.ic.qd.esign.SapplyLineCommand;
import nc.vo.ic.qd.esign.SapplyLineResult;
import nc.vo.pub.BusinessException;
import nc.vo.pub.IAttributeMeta;
import nc.vo.pub.ISuperVO;
import nc.vo.pub.IVOMeta;
import nc.vo.pub.lang.UFBoolean;
import nc.vo.pubapp.pattern.model.meta.entity.bill.IBillMeta;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;

/** Validates persisted row reconstruction and native service delegation with explicit doubles. */
public final class SapplyLineOperationAdapterTests {
    private static final IBillMeta META=new IBillMeta(){
        public Map<String,Object> getBusinessAttribute(){return Collections.emptyMap();}
        public IAttributeMeta[] getChildForeignKeys(){return new IAttributeMeta[0];}
        public IVOMeta[] getChildren(){return new IVOMeta[0];}
        public String getComponentName(){return "TEST";}
        public IVOMeta getParent(){throw new UnsupportedOperationException();}
        public Class<? extends ISuperVO> getVOClass(IVOMeta meta){throw new UnsupportedOperationException();}
        public IVOMeta getVOMeta(Class<? extends ISuperVO> type){throw new UnsupportedOperationException();}
    };
    public static void run() throws Exception {
        for(boolean close:new boolean[]{true,false}) {
            Fake adapter=new Fake();adapter.closed.put("L1",Boolean.valueOf(!close));adapter.closed.put("L2",Boolean.valueOf(close));
            OperationContext context=context(close);
            adapter.validate(context,new SapplyLineCommand(false));
            SapplyLineResult result=(SapplyLineResult)adapter.execute(context,new SapplyLineCommand(false));
            require(adapter.calls==1 && (close?"rowClose":"rowOpen").equals(adapter.action)
                    && "BILL".equals(result.getBillPk()) && "CODE".equals(result.getBillCode())
                    && adapter.describeResult(context,result).size()==3,"Native row operation or returned scope is wrong");
            require(adapter.builtRows==2 && adapter.closed.get("L1").booleanValue()==close
                    && adapter.closed.get("L2").booleanValue()==close,"Only server-loaded selected rows must be sent");
            Fake nullable=new Fake();nullable.closed.put("L1",Boolean.valueOf(!close));
            nullable.closed.put("L2",Boolean.valueOf(close));nullable.closed.put("L3",null);
            nullable.validate(context,new SapplyLineCommand(false));
            nullable.execute(context,new SapplyLineCommand(false));
            require(nullable.calls==1 && nullable.closed.containsKey("L3") && nullable.closed.get("L3")==null,
                    "An unchanged, unselected null state must not block a native row operation");
        }
        Fake bad=new Fake();bad.closed.put("L1",false);bad.closed.put("L2",false);
        reject(bad,context(true),new SapplyLineCommand(true));
        bad.badParent=true;reject(bad,context(true),new SapplyLineCommand(false));bad.badParent=false;
        bad.deny=true;reject(bad,context(true),new SapplyLineCommand(false));bad.deny=false;
        bad.wrongOrg=true;reject(bad,context(true),new SapplyLineCommand(false));bad.wrongOrg=false;
        bad.closed.put("L1",true);bad.closed.put("L2",true);reject(bad,context(true),new SapplyLineCommand(false));
        require(bad.calls==0,"Invalid scope, permission or state must not invoke the native service");
        bad.closed.put("L1",false);bad.closed.put("L2",false);bad.noResult=true;
        try {bad.execute(context(true),new SapplyLineCommand(false));throw new AssertionError("Missing native result accepted");}
        catch(BusinessException expected) { }
        require(bad.calls==1,"Missing result must be detected after the native call and trigger rollback");
        Fake unexpected=new Fake();unexpected.closed.put("L1",false);unexpected.closed.put("L2",false);
        unexpected.closed.put("L3",false);unexpected.changeUnselected=true;
        try{unexpected.execute(context(true),new SapplyLineCommand(false));throw new AssertionError("Unselected row change accepted");}
        catch(BusinessException expected) { }
        require(unexpected.calls==1,"An unselected row change must fail after native processing");
        Fake selectedNull=new Fake();selectedNull.closed.put("L1",null);selectedNull.closed.put("L2",false);
        reject(selectedNull,context(true),new SapplyLineCommand(false));
        require(selectedNull.calls==0,"A selected row with an unknown state cannot enter the native action");
        Fake nullChanged=new Fake();nullChanged.closed.put("L1",false);nullChanged.closed.put("L2",false);
        nullChanged.closed.put("L3",null);nullChanged.changeUnselected=true;
        try {nullChanged.execute(context(true),new SapplyLineCommand(false));
            throw new AssertionError("An unselected null state changed to closed");}
        catch(BusinessException expected) { }
        require(nullChanged.calls==1,"A null-to-boolean change on an unselected row must fail after native processing");
        Fake changedToNull=new Fake();changedToNull.closed.put("L1",false);changedToNull.closed.put("L2",false);
        changedToNull.closed.put("L3",false);changedToNull.clearUnselected=true;
        try {changedToNull.execute(context(true),new SapplyLineCommand(false));
            throw new AssertionError("An unselected state was changed to null");}
        catch(BusinessException expected) { }
        require(changedToNull.calls==1,"A boolean-to-null change on an unselected row must fail after native processing");
        System.out.println("PASS SapplyLineOperationAdapterTests (server-owned selected rows and native delegation; no NC/Oracle)");
    }
    private static void reject(Fake adapter,OperationContext context,SapplyLineCommand command) throws Exception {
        try{adapter.validate(context,command);throw new AssertionError("Invalid row command accepted");}
        catch(BusinessException expected) { }
    }
    private static OperationContext context(boolean close) {
        String button=close?"LineClose":"LineOpen";
        return new OperationContext("R","B","G","O","40080801","request",button,button,"UPDATE",null,
                Arrays.asList(new BusinessObjectRef("root","IC_SAPPLY","BILL","CODE",null),
                    new BusinessObjectRef("line1","IC_SAPPLY_LINE","L1",null,"root"),
                    new BusinessObjectRef("line2","IC_SAPPLY_LINE","L2",null,"root")));
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static final class Fake extends SapplyLineOperationAdapter {
        final Map<String,Boolean> closed=new HashMap<String,Boolean>();
        int calls,builtRows;String action;boolean badParent,deny,wrongOrg,noResult,changeUnselected,clearUnselected;
        protected void requireBillPermission(BusinessObjectRef root) throws BusinessException {if(deny)throw new BusinessException("denied");}
        protected int approvedFlag(){return 4;}
        protected SapplyBillHeadVO loadHead(final String pk) {
            return new SapplyBillHeadVO(){public String getCgeneralhid(){return pk;}public String getPk_group(){return "G";}
                public String getPk_org(){return wrongOrg?"OTHER":"O";}public String getVbillcode(){return "CODE";}
                public String getVtrantypecode(){return "4455";}public String getCtrantypeid(){return null;}
                public Integer getFbillflag(){return Integer.valueOf(4);}public Object getAttributeValue(String name){return null;}};
        }
        protected Map<String,SapplyBillBodyVO> loadBodies(String billPk) {
            Map<String,SapplyBillBodyVO> result=new HashMap<String,SapplyBillBodyVO>();
            for(String pk:closed.keySet())result.put(pk,loadBody(pk));return result;
        }
        private SapplyBillBodyVO loadBody(final String pk) {
            if(!closed.containsKey(pk))return null;
            return new SapplyBillBodyVO(){public String getCgeneralbid(){return pk;}
                public String getCgeneralhid(){return badParent?"OTHER":"BILL";}
                public UFBoolean getIsclosed(){Boolean value=closed.get(pk);return value==null?null:value.booleanValue()?UFBoolean.TRUE:UFBoolean.FALSE;}
                public Object getAttributeValue(String name){return null;}};
        }
        protected SapplyBillVO createBill(final SapplyBillHeadVO head,final SapplyBillBodyVO[] bodies) {
            builtRows=bodies.length;
            return new SapplyBillVO(){public IBillMeta getMetaData(){return META;}
                public SapplyBillHeadVO getHead(){return head;}public SapplyBillBodyVO[] getBodys(){return bodies;}};
        }
        protected SapplyBillVO[] toNativeBill(SapplyBillVO bill){return new SapplyBillVO[]{bill};}
        protected ISapplyBillMaintain maintain(){
            return (ISapplyBillMaintain)Proxy.newProxyInstance(ISapplyBillMaintain.class.getClassLoader(),
                    new Class<?>[]{ISapplyBillMaintain.class},new InvocationHandler(){
                public Object invoke(Object proxy,Method method,Object[] args){
                    action=method.getName();calls++;
                    require(("rowClose".equals(action)||"rowOpen".equals(action)) && Boolean.FALSE.equals(args[1]),
                            "Unexpected native service or unbound budget continuation");
                    SapplyBillVO[] bills=(SapplyBillVO[])args[0];
                    for(SapplyBillBodyVO row:bills[0].getBodys())closed.put(row.getCgeneralbid(),Boolean.valueOf("rowClose".equals(action)));
                    if(changeUnselected)closed.put("L3",Boolean.valueOf("rowClose".equals(action)));
                    if(clearUnselected)closed.put("L3",null);
                    return noResult?null:bills;
                }});
        }
    }
}
