package nc.bs.uapbd.qd.esign.audit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import nc.vo.ic.m4455.entity.SapplyBillBodyVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.lang.UFBoolean;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;

/** Verifies whole-bill and selected-row evidence against explicit physical row doubles. */
public final class SapplyLineSnapshotsTests {
    public static void run() throws Exception {
        for(boolean close:new boolean[]{true,false}) {
            OperationContext context=context(close,"A"); Rows rows=new Rows(close); AuditCaptureScope scope=new AuditCaptureScope(context,true);
            try {
                InventoryPhysicalSnapshots snapshots=new InventoryPhysicalSnapshots(rows);
                SapplyLineSnapshots.before(scope,context,snapshots);
                SapplyLineSnapshots.after(scope,context,context.getObjects(),snapshots);
                require(scope.getBefore("bill").size()==5 && scope.getAfter("bill").size()==5
                        && scope.getBefore("line").size()==2 && scope.getAfter("line").size()==2,
                        "Whole bill and selected line evidence must be recorded separately");
                require(rows.beforeReads==3 && rows.afterReads==3,"Bill and extension evidence must be read in both phases");
            } finally { scope.close(); }
            SapplyLineEffects.requireBodies(new SapplyBillBodyVO[]{body("A",!close),body("B",false)},
                    new SapplyBillBodyVO[]{body("A",close),body("B",false)},Collections.singleton("A"),!close);
            SapplyLineEffects.requireBodies(new SapplyBillBodyVO[]{body("A",!close),body("B",null)},
                    new SapplyBillBodyVO[]{body("A",close),body("B",null)},Collections.singleton("A"),!close);
        }
        OperationContext context=context(true,"A");
        Rows rows=new Rows(true);rows.changeOther=true;reject(context,rows,"Unauthorized row change accepted");
        rows=new Rows(true);rows.missingSelected=true;reject(context,rows,"Missing selected row accepted");
        rows=new Rows(true);rows.badState=true;reject(context,rows,"Unknown physical close state accepted");
        rows=new Rows(true);rows.noChange=true;reject(context,rows,"No-op result accepted");
        rows=new Rows(true);rows.nullSelectedBefore=true;reject(context,rows,"Selected null state before execution accepted");
        rows=new Rows(true);rows.nullSelectedAfter=true;reject(context,rows,"Selected null state after execution accepted");
        rows=new Rows(true);rows.nullOtherBefore=true;reject(context,rows,"Unselected null-to-false change accepted");
        rows=new Rows(true);rows.nullOtherAfter=true;reject(context,rows,"Unselected false-to-null change accepted");
        rows=new Rows(true);rows.nullOtherBefore=true;rows.nullOtherAfter=true;
        AuditCaptureScope nullableScope=new AuditCaptureScope(context,true);
        try {
            InventoryPhysicalSnapshots snapshots=new InventoryPhysicalSnapshots(rows);
            SapplyLineSnapshots.before(nullableScope,context,snapshots);
            SapplyLineSnapshots.after(nullableScope,context,context.getObjects(),snapshots);
        } finally {nullableScope.close();}
        try {
            SapplyLineEffects.requireBodies(new SapplyBillBodyVO[]{body("A",true),body("B",false)},
                    new SapplyBillBodyVO[]{body("A",false),body("B",null)},Collections.singleton("A"),true);
            throw new AssertionError("Unselected VO null-to-false change accepted");
        } catch(BusinessException expected) { }
        try {
            SapplyLineEffects.requireBodies(new SapplyBillBodyVO[]{body("A",true),body("B",null)},
                    new SapplyBillBodyVO[]{body("A",false),body("B",false)},Collections.singleton("A"),true);
            throw new AssertionError("Unselected VO false-to-null change accepted");
        } catch(BusinessException expected) { }
        try {
            SapplyLineEffects.requireBodies(new SapplyBillBodyVO[]{body("A",true)},
                    new SapplyBillBodyVO[]{body("A",null)},Collections.singleton("A"),true);
            throw new AssertionError("Selected null-state VO row accepted");
        } catch(BusinessException expected) { }
        try {
            SapplyLineEffects.requireBodies(new SapplyBillBodyVO[]{body("A",true),body("B",null),body("B",null)},
                    new SapplyBillBodyVO[]{body("A",false),body("B",null)},Collections.singleton("A"),true);
            throw new AssertionError("Duplicate null-state VO row accepted");
        } catch(BusinessException expected) { }
        Rows duplicate=new Rows(true);
        List<SnapshotRow> duplicateRows=Arrays.asList(duplicate.bodyRow("A","BILL",false),
                duplicate.bodyRow("B","BILL",null),duplicate.bodyRow("B","BILL",null));
        try {
            SapplyLineEffects.requireSnapshotChanges(duplicateRows,duplicateRows,Collections.singleton("A"),true);
            throw new AssertionError("Duplicate null-state physical row accepted");
        } catch(BusinessException expected) { }
        try {
            SapplyLineEffects.requireBodies(new SapplyBillBodyVO[]{body("A",true),body("B",true)},
                    new SapplyBillBodyVO[]{body("A",false),body("B",false)},Collections.singleton("A"),true);
            throw new AssertionError("Unselected VO row was modified");
        } catch(BusinessException expected) { }
        try {
            SapplyLineEffects.requireBodies(new SapplyBillBodyVO[]{body("A",true)},new SapplyBillBodyVO[]{body("A",false)},Collections.singleton("OTHER"),true);
            throw new AssertionError("Missing authorized VO row accepted");
        } catch(BusinessException expected) { }
        System.out.println("PASS SapplyLineSnapshotsTests (bill/line evidence and row effects; no Oracle)");
    }
    private static void reject(OperationContext context,Rows rows,String message) throws Exception {
        AuditCaptureScope scope=new AuditCaptureScope(context,true);
        try {
            InventoryPhysicalSnapshots snapshots=new InventoryPhysicalSnapshots(rows);
            try {
                SapplyLineSnapshots.before(scope,context,snapshots);
                SapplyLineSnapshots.after(scope,context,context.getObjects(),snapshots);
                throw new AssertionError(message);
            } catch(BusinessException expected) { }
        } finally { scope.close(); }
    }
    private static OperationContext context(boolean close,String line) {
        return new OperationContext("R","B","G","O","40080801","request",close?"LineClose":"LineOpen","line","UPDATE",null,
                Arrays.asList(new BusinessObjectRef("bill","IC_SAPPLY","BILL","CODE",null),
                        new BusinessObjectRef("line","IC_SAPPLY_LINE",line,null,"bill")));
    }
    private static SnapshotRow row(String entity,String id,String parent,Map<String,String> values) {
        return new SnapshotRow(entity,id,parent,values,Collections.<String,String>emptyMap(),Collections.<String,String>emptyMap(),true);
    }
    private static SapplyBillBodyVO body(final String id,final Boolean closed) {
        return new SapplyBillBodyVO(){public String getCgeneralbid(){return id;}
            public UFBoolean getIsclosed(){return closed==null?null:closed.booleanValue()?UFBoolean.TRUE:UFBoolean.FALSE;}};
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static final class Rows implements InventoryPhysicalSnapshots.Rows {
        final boolean close;int beforeReads,afterReads;boolean changeOther,missingSelected,badState,noChange,nullOtherBefore,nullOtherAfter,nullSelectedBefore,nullSelectedAfter;
        Rows(boolean close){this.close=close;}
        public List<SnapshotRow> read(String table,String key,String parent,String pk,boolean lock) {
            if(lock)beforeReads++;else afterReads++;
            List<SnapshotRow> result=new ArrayList<SnapshotRow>();
            if("ic_sapply_h".equals(table)) {
                Map<String,String> fields=new HashMap<String,String>();fields.put("pk_group","G");fields.put("pk_org","O");fields.put("vbillcode","CODE");
                result.add(row(table,pk,null,fields));
            } else if("ic_sapply_b".equals(table)) {
                if(!missingSelected)result.add(bodyRow("A",pk,lock&&nullSelectedBefore || !lock&&nullSelectedAfter
                        ?null:badState?"?":Boolean.valueOf((lock||noChange)?!close:close)));
                result.add(bodyRow("B",pk,lock&&nullOtherBefore || !lock&&nullOtherAfter?null:Boolean.valueOf(!lock && changeOther)));
            } else throw new AssertionError("Unexpected outbound request table "+table);
            return result;
        }
        private SnapshotRow bodyRow(String id,String parent,Object closed) {
            Map<String,String> values=new HashMap<String,String>();values.put("pk_batchcode",null);
            values.put("isclosed",closed instanceof Boolean?((Boolean)closed?"Y":"N"):(String)closed);
            return row("ic_sapply_b",id,parent,values);
        }
        public List<SnapshotRow> readBatchReferences(List<String> keys,String group,boolean lock,boolean sapply) {
            throw new AssertionError("No batch lookup expected");
        }
        public List<SnapshotRow> readSapplyExtensions(List<String> keys,boolean lock) {
            if(lock)beforeReads++;else afterReads++;
            List<SnapshotRow> result=new ArrayList<SnapshotRow>();
            for(String key:keys){Map<String,String> fields=new HashMap<String,String>();fields.put("cgeneralbid",key);
                fields.put("noutboundnum","2");result.add(row("ic_sapply_e",key,key,fields));}
            return result;
        }
    }
}
