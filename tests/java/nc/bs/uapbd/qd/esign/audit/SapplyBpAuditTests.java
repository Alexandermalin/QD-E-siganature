package nc.bs.uapbd.qd.esign.audit;

import java.util.Collections;
import java.util.Map;
import javax.transaction.Status;
import javax.transaction.Synchronization;
import javax.transaction.TransactionSynchronizationRegistry;
import nc.impl.ic.qd.esign.SapplyBpRules;
import nc.vo.ic.general.define.ICBillVO;
import nc.vo.ic.m4455.entity.SapplyBillHeadVO;
import nc.vo.ic.m4455.entity.SapplyBillVO;
import nc.vo.ic.m4455.entity.SapplyBillBodyVO;
import nc.vo.pub.lang.UFBoolean;
import nc.vo.pub.BusinessException;
import nc.vo.pub.IAttributeMeta;
import nc.vo.pub.ISuperVO;
import nc.vo.pub.IVOMeta;
import nc.vo.pubapp.pattern.model.meta.entity.bill.IBillMeta;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;

/** Exercises the actual BP rule classes and transaction-scoped audit handoff without NC or Oracle. */
public final class SapplyBpAuditTests {
    private static final IBillMeta META = new IBillMeta() {
        public Map<String,Object> getBusinessAttribute(){return Collections.emptyMap();}
        public IAttributeMeta[] getChildForeignKeys(){return new IAttributeMeta[0];}
        public IVOMeta[] getChildren(){return new IVOMeta[0];}
        public String getComponentName(){return "TEST";}
        public IVOMeta getParent(){throw new UnsupportedOperationException();}
        public Class<? extends ISuperVO> getVOClass(IVOMeta meta){throw new UnsupportedOperationException();}
        public IVOMeta getVOMeta(Class<? extends ISuperVO> type){throw new UnsupportedOperationException();}
    };

    public static void run() throws Exception {
        for (String button : new String[] {"SaveInsert","SaveUpdate","Delete","Commit","UnCommit","Approve","UnApprove"}) {
            OperationContext context = context(button);
            Tx tx = new Tx(); Snapshotter snapshots = new Snapshotter();
            AuditCaptureScope scope = new AuditCaptureScope(context,true);
            SapplyBpAudit.Capture capture = SapplyBpAudit.open(context,scope,tx,snapshots);
            try {
                ICBillVO[] before = bills("SaveInsert".equals(button) ? null : "A","G","O");
                ICBillVO[] after = bills("SaveInsert".equals(button) ? "NEW" : "A","G","O");
                before(button,before);
                after(button,after);
                capture.verify(result("SaveInsert".equals(button) ? "NEW" : "A"));
                require(snapshots.calls == 1 && !tx.rollbackOnly,"BP before snapshot and transaction must be retained");
            } finally { capture.close(); scope.close(); }
        }
        OperationContext insert = context("SaveInsert");
        AuditCaptureScope scope = new AuditCaptureScope(insert,true); Tx tx = new Tx(); Snapshotter snapshots = new Snapshotter();
        SapplyBpAudit.Capture capture = SapplyBpAudit.open(insert,scope,tx,snapshots);
        try {
            new SapplyBpRules.BeforeInsert().process(bills(null,"G","O"));
            new SapplyBpRules.BeforeApprove().process(bills("NEW","G","O"),bills("NEW","G","O"));
            new SapplyBpRules.AfterApprove().process(bills("NEW","G","O"),bills("NEW","G","O"));
            new SapplyBpRules.AfterInsert().process(bills("NEW","G","O"));
            capture.verify(result("NEW"));
            require(snapshots.calls == 1,"Nested native BP events must share the root snapshot");
        } finally { capture.close(); scope.close(); }

        OperationContext rowContext=new OperationContext("R","B","G","O","40080801","request","LineClose","close","UPDATE",null,
                java.util.Arrays.asList(new BusinessObjectRef("item","IC_SAPPLY","A","CODE",null),
                        new BusinessObjectRef("line","IC_SAPPLY_LINE","L1",null,"item")));
        scope=new AuditCaptureScope(rowContext,true);tx=new Tx();snapshots=new Snapshotter();
        capture=SapplyBpAudit.open(rowContext,scope,tx,snapshots);
        try {
            ICBillVO[] old=new ICBillVO[]{billWithRows(false,false)};
            ICBillVO[] current=new ICBillVO[]{billWithRows(true,false)};
            new SapplyBpRules.BeforeRowClose().process(current,old);
            new SapplyBpRules.AfterRowClose().process(current,old);
            capture.verify(rowContext.getObjects());require(snapshots.calls==1,"Row BP must capture its authorized bill once");
        } finally {capture.close();scope.close();}
        scope=new AuditCaptureScope(rowContext,true);tx=new Tx();snapshots=new Snapshotter();
        capture=SapplyBpAudit.open(rowContext,scope,tx,snapshots);
        try {
            try{new SapplyBpRules.BeforeRowClose().process(new ICBillVO[]{billWithRows(true,true)},
                    new ICBillVO[]{billWithRows(false,false)});throw new AssertionError("Unselected BP row change accepted");}
            catch(IllegalStateException expected) { }
            require(tx.rollbackOnly,"Unauthorized row effect must mark the native transaction for rollback");
        } finally {capture.close();scope.close();}

        OperationContext openContext=new OperationContext("R","B","G","O","40080801","request","LineOpen","open","UPDATE",null,
                java.util.Arrays.asList(new BusinessObjectRef("item","IC_SAPPLY","A","CODE",null),
                        new BusinessObjectRef("line","IC_SAPPLY_LINE","L1",null,"item")));
        scope=new AuditCaptureScope(openContext,true);tx=new Tx();snapshots=new Snapshotter();
        capture=SapplyBpAudit.open(openContext,scope,tx,snapshots);
        try {
            ICBillVO[] old=new ICBillVO[]{billWithRows(true,false)},current=new ICBillVO[]{billWithRows(false,false)};
            new SapplyBpRules.BeforeRowOpen().process(current,old);new SapplyBpRules.AfterRowOpen().process(current,old);
            capture.verify(openContext.getObjects());require(snapshots.calls==1,"Row-open BP must capture its authorized bill once");
        } finally {capture.close();scope.close();}
        for(boolean close:new boolean[]{true,false}) {
            OperationContext nullableContext=close?rowContext:openContext;
            scope=new AuditCaptureScope(nullableContext,true);tx=new Tx();snapshots=new Snapshotter();
            capture=SapplyBpAudit.open(nullableContext,scope,tx,snapshots);
            try {
                ICBillVO[] old=new ICBillVO[]{billWithRows(Boolean.valueOf(!close),null)};
                ICBillVO[] current=new ICBillVO[]{billWithRows(Boolean.valueOf(close),null)};
                if(close){new SapplyBpRules.BeforeRowClose().process(current,old);new SapplyBpRules.AfterRowClose().process(current,old);}
                else {new SapplyBpRules.BeforeRowOpen().process(current,old);new SapplyBpRules.AfterRowOpen().process(current,old);}
                capture.verify(nullableContext.getObjects());
                require(snapshots.calls==1 && !tx.rollbackOnly,"An unchanged unselected null state must pass the BP rules");
            } finally {capture.close();scope.close();}
        }
        scope=new AuditCaptureScope(rowContext,true);tx=new Tx();snapshots=new Snapshotter();
        capture=SapplyBpAudit.open(rowContext,scope,tx,snapshots);
        try {
            try {new SapplyBpRules.BeforeRowClose().process(new ICBillVO[]{billWithRows(true,false)},
                        new ICBillVO[]{billWithRows(false,null)});
                throw new AssertionError("An unselected null-to-false BP change was accepted");}
            catch(IllegalStateException expected) { }
            require(tx.rollbackOnly,"An unselected null-state change must mark the native transaction for rollback");
        } finally {capture.close();scope.close();}

        OperationContext update = context("SaveUpdate");
        scope = new AuditCaptureScope(update,false); tx = new Tx(); snapshots = new Snapshotter();
        capture = SapplyBpAudit.open(update,scope,tx,snapshots);
        try {
            before("SaveUpdate",bills("A","G","O")); after("SaveUpdate",bills("A","G","O"));
            capture.verify(result("A")); require(snapshots.calls == 0,"Disabled audit must not read snapshots");
        } finally { capture.close(); scope.close(); }

        scope = new AuditCaptureScope(update,true); tx = new Tx(); snapshots = new Snapshotter();
        capture = SapplyBpAudit.open(update,scope,tx,snapshots);
        try {
            before("SaveUpdate",bills("A","G","O"));
            try { capture.verify(result("A")); throw new AssertionError("Missing after rule accepted"); }
            catch (BusinessException expected) { }
        } finally { capture.close(); scope.close(); }

        for (String failure : new String[] {"wrongAction","wrongGroup","wrongPk","batch","duplicate","transaction","snapshot"}) {
            scope = new AuditCaptureScope(update,true); tx = new Tx(); snapshots = new Snapshotter();
            capture = SapplyBpAudit.open(update,scope,tx,snapshots);
            try {
                try {
                    if ("wrongAction".equals(failure)) new SapplyBpRules.BeforeDelete().process(bills("A","G","O"));
                    else if ("wrongGroup".equals(failure)) before("SaveUpdate",bills("A","OTHER","O"));
                    else if ("wrongPk".equals(failure)) before("SaveUpdate",bills("OTHER","G","O"));
                    else if ("batch".equals(failure)) before("SaveUpdate",new ICBillVO[] {bill("A","G","O"),bill("B","G","O")});
                    else if ("duplicate".equals(failure)) { before("SaveUpdate",bills("A","G","O")); before("SaveUpdate",bills("A","G","O")); }
                    else if ("transaction".equals(failure)) { tx.key="OTHER"; before("SaveUpdate",bills("A","G","O")); }
                    else { snapshots.fail=true; before("SaveUpdate",bills("A","G","O")); }
                    throw new AssertionError("Invalid BP event accepted: "+failure);
                } catch (IllegalStateException expected) { }
                require(tx.rollbackOnly,"Rule failure must mark the current transaction for rollback: "+failure);
            } finally { capture.close(); scope.close(); }
        }

        final String[] checked = new String[1];
        SapplyBpAudit.PolicyLookup policy = new SapplyBpAudit.PolicyLookup() {
            public boolean controlled(String group,String org,String button) {
                require("G".equals(group) && "O".equals(org),"Incorrect policy owner"); checked[0]=button; return false;
            }
        };
        SapplyBpAudit.requireUncontrolled("RowClose",bills("A","G","O"),policy);
        require("LineClose".equals(checked[0]),"BP row-close must resolve the formal button policy");
        try { SapplyBpAudit.requireUncontrolled("Commit",bills("A","G","O"),new SapplyBpAudit.PolicyLookup() {
                public boolean controlled(String group,String org,String button) { return true; }
            }); throw new AssertionError("Unscoped controlled BP accepted");
        } catch (BusinessException expected) { }
        final java.util.List<String> owners=new java.util.ArrayList<String>();
        SapplyBpAudit.PolicyLookup ownerPolicy=new SapplyBpAudit.PolicyLookup() {
            public boolean controlled(String group,String org,String button) { owners.add(org);return "OLD".equals(org); }
        };
        try { SapplyBpAudit.requireUncontrolled("Update",bills("A","G","NEW"),bills("A","G","OLD"),ownerPolicy);
            throw new AssertionError("An old controlled organization was bypassed");
        } catch (BusinessException expected) { }
        require(owners.equals(java.util.Arrays.asList("NEW","OLD")),"Old and new policy owners are checked");
        owners.clear();
        SapplyBpAudit.requireUncontrolled("Update",bills("A","G","NEW"),bills("A","G","NEW"),ownerPolicy);
        require(owners.equals(java.util.Collections.singletonList("NEW")),"Identical old and new owners are resolved once");
        try { SapplyBpAudit.requireUncontrolled("Update",bills("A","G","NEW"),bills("A","G",null),ownerPolicy);
            throw new AssertionError("An old bill with an unknown organization was accepted");
        } catch (BusinessException expected) { }
        System.out.println("PASS SapplyBpAuditTests (BP rules, transaction scope and policy guard; no NC/Oracle)");
    }

    private static void before(String button, ICBillVO[] bills) {
        if ("SaveInsert".equals(button)) new SapplyBpRules.BeforeInsert().process(bills);
        else if ("SaveUpdate".equals(button)) new SapplyBpRules.BeforeUpdate().process(bills,bills);
        else if ("Delete".equals(button)) new SapplyBpRules.BeforeDelete().process(bills);
        else if ("Commit".equals(button)) new SapplyBpRules.BeforeCommit().process(bills);
        else if ("UnCommit".equals(button)) new SapplyBpRules.BeforeUnCommit().process(bills);
        else if ("Approve".equals(button)) new SapplyBpRules.BeforeApprove().process(bills,bills);
        else if ("UnApprove".equals(button)) new SapplyBpRules.BeforeUnApprove().process(bills,bills);
        else throw new IllegalArgumentException(button);
    }
    private static void after(String button, ICBillVO[] bills) {
        if ("SaveInsert".equals(button)) new SapplyBpRules.AfterInsert().process(bills);
        else if ("SaveUpdate".equals(button)) new SapplyBpRules.AfterUpdate().process(bills,bills);
        else if ("Delete".equals(button)) new SapplyBpRules.AfterDelete().process(bills);
        else if ("Commit".equals(button)) new SapplyBpRules.AfterCommit().process(bills);
        else if ("UnCommit".equals(button)) new SapplyBpRules.AfterUnCommit().process(bills);
        else if ("Approve".equals(button)) new SapplyBpRules.AfterApprove().process(bills,bills);
        else if ("UnApprove".equals(button)) new SapplyBpRules.AfterUnApprove().process(bills,bills);
        else throw new IllegalArgumentException(button);
    }
    private static OperationContext context(String button) {
        String action=button.startsWith("Save")?"Save":button;
        String op="SaveInsert".equals(button)?"INSERT":"Delete".equals(button)?"DELETE":"UPDATE";
        return new OperationContext("R","B","G","O","40080801","request",action,action,op,null,
                Collections.singletonList(new BusinessObjectRef("item","IC_SAPPLY","SaveInsert".equals(button)?null:"A",null,null)));
    }
    private static java.util.List<BusinessObjectRef> result(String pk) {
        return Collections.singletonList(new BusinessObjectRef("item","IC_SAPPLY",pk,"CODE",null));
    }
    private static ICBillVO[] bills(String pk,String group,String org) { return new ICBillVO[] {bill(pk,group,org)}; }
    private static ICBillVO bill(final String pk,final String group,final String org) {
        final SapplyBillHeadVO head=new SapplyBillHeadVO() {
            public String getCgeneralhid(){return pk;} public String getPk_group(){return group;} public String getPk_org(){return org;}
        };
        return new SapplyBillVO() { public IBillMeta getMetaData(){return META;} public SapplyBillHeadVO getHead(){return head;} };
    }
    private static SapplyBillVO billWithRows(final Boolean oneClosed,final Boolean twoClosed) {
        final SapplyBillHeadVO head=new SapplyBillHeadVO(){public String getCgeneralhid(){return "A";}
            public String getPk_group(){return "G";}public String getPk_org(){return "O";}};
        return new SapplyBillVO(){public IBillMeta getMetaData(){return META;}public SapplyBillHeadVO getHead(){return head;}
            public SapplyBillBodyVO[] getBodys(){return new SapplyBillBodyVO[]{row("L1",oneClosed),row("L2",twoClosed)};}};
    }
    private static SapplyBillBodyVO row(final String pk,final Boolean closed) {
        return new SapplyBillBodyVO(){public String getCgeneralbid(){return pk;}
            public UFBoolean getIsclosed(){return closed==null?null:closed.booleanValue()?UFBoolean.TRUE:UFBoolean.FALSE;}};
    }
    private static void require(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
    private static final class Snapshotter implements SapplyBpAudit.Snapshotter {
        int calls; boolean fail;
        public void before(AuditCaptureScope scope,OperationContext context) throws BusinessException {
            calls++; if(fail)throw new BusinessException("injected snapshot failure");
        }
    }
    private static final class Tx implements TransactionSynchronizationRegistry {
        Object key="TX"; boolean rollbackOnly;
        public Object getTransactionKey(){return key;} public int getTransactionStatus(){return Status.STATUS_ACTIVE;}
        public boolean getRollbackOnly(){return rollbackOnly;} public void setRollbackOnly(){rollbackOnly=true;}
        public void putResource(Object key,Object value){throw new UnsupportedOperationException();}
        public Object getResource(Object key){throw new UnsupportedOperationException();}
        public void registerInterposedSynchronization(Synchronization sync){throw new UnsupportedOperationException();}
    }
}
