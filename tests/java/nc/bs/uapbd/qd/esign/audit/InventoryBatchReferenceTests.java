package nc.bs.uapbd.qd.esign.audit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import nc.bs.dao.DAOException;
import nc.bs.uapbd.qd.esign.persistence.QdEsignStore;
import nc.jdbc.framework.SQLParameter;
import nc.jdbc.framework.processor.ResultSetProcessor;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.audit.FieldChange;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;

/** Validates batch-reference evidence using an explicit row-reader double, without NC/Oracle. */
public final class InventoryBatchReferenceTests {
    public static void run() throws Exception {
        for(String type:new String[]{"IC_PURCHASE_IN","IC_SALE_OUT","IC_TRANS_IN","IC_INITIAL_IN","IC_GENERAL_IN","IC_PRODUCT_IN","IC_SUBCONT_IN","IC_GENERAL_OUT","IC_MATERIAL_OUT","IC_TRANS_OUT","IC_OUTSOURCE_IN","IC_PRODUCTION_ORDER","IC_SAPPLY"}) {
            Rows rows=new Rows(type,"B2","B1","B2","~",null);
            OperationContext delete=context(type,"DELETE");
            AuditCaptureScope scope=new AuditCaptureScope(delete,true);
            try {
                new InventoryPhysicalSnapshots(rows).before(scope,delete);
                require(rows.referenceCalls==1 && rows.requested.equals(Arrays.asList("B1","B2")) && rows.locked,
                        "Referenced batch keys must be deduplicated, sorted and locked");
                List<SnapshotRow> references=scope.getBeforeReferences("item");
                require(references.size()==2 && "B1".equals(references.get(0).getRowId())
                    && "B2".equals(references.get(1).getRowId()),"Reference order must be stable");
                for(SnapshotRow reference:references) require(InventoryPhysicalSnapshots.BATCH_REFERENCE_ENTITY.equals(reference.getEntity())
                    && "G".equals(reference.getValues().get("pk_group")),"Reference entity and group must be explicit");
                AuditRecorder.requireReferences(references);
                require(scope.getBefore("item").size()==6,"Bill rows must remain separate from shared references");
                for(FieldChange change:SnapshotSupport.diff(scope.getBefore("item"),Collections.<SnapshotRow>emptyList()))
                    require(!InventoryPhysicalSnapshots.BATCH_REFERENCE_ENTITY.equals(change.getRow().getEntity()),
                            "A shared batch reference must not be presented as a deleted bill row");
                require(SnapshotPhases.forOperation("DELETE",scope.getBefore("item"),Collections.<SnapshotRow>emptyList())
                    .getDeleted().size()==6,"Only the bill header and body are deleted evidence");
                FakeAuditStore audit=new FakeAuditStore();
                Map<String,Object> request=new HashMap<String,Object>();request.put("request_id","request");
                request.put("pk_group","G");request.put("pk_org","O");request.put("operation_type","DELETE");
                Map<String,Object> object=new HashMap<String,Object>();object.put("object_id","object");
                new AuditRecorder(audit).record(request,object,scope.getBefore("item"),Collections.<SnapshotRow>emptyList(),references);
                require(audit.references==2 && audit.deletedBillRows==6 && audit.referenceChanges==0,
                    "Shared references must be stored only as BEFORE snapshots, never DELETE changes");
                audit=new FakeAuditStore();audit.failReferences=true;
                try { new AuditRecorder(audit).record(request,object,scope.getBefore("item"),Collections.<SnapshotRow>emptyList(),references);
                    throw new AssertionError("Reference evidence write failure was hidden"); }
                catch(DAOException expected) { }
                try { references.clear();throw new AssertionError("Reference evidence is mutable"); }
                catch(UnsupportedOperationException expected) { }
                try { AuditRecorder.requireReferences(Arrays.asList(references.get(0),references.get(0)));
                    throw new AssertionError("Duplicate reference evidence accepted"); }
                catch(BusinessException expected) { }
                try { AuditRecorder.requireReferences(scope.getBefore("item"));throw new AssertionError("Bill row accepted as reference evidence"); }
                catch(BusinessException expected) { }
            } finally { scope.close(); }

            rows=new Rows(type,"B1");rows.missing=true;reject(rows,delete,"Missing batch reference accepted");
            rows=new Rows(type,"B1");rows.nullResult=true;reject(rows,delete,"Missing batch query result accepted");
            rows=new Rows(type,"B1");rows.wrongGroup=true;reject(rows,delete,"Cross-group batch reference accepted");
            rows=new Rows(type,"B1");rows.wrongEntity=true;reject(rows,delete,"Wrong batch entity accepted");
            rows=new Rows(type,"B1");rows.wrongId=true;reject(rows,delete,"Inconsistent batch identity accepted");
            rows=new Rows(type,"B1");rows.duplicate=true;reject(rows,delete,"Duplicate batch result accepted");
            rows=new Rows(type,"B1");rows.omitColumn=true;reject(rows,delete,"Missing body batch column accepted");
            rows=new Rows(type," B1 ");reject(rows,delete,"Malformed batch key accepted");

            rows=new Rows(type,"B1");OperationContext update=context(type,"UPDATE");scope=new AuditCaptureScope(update,true);
            try { new InventoryPhysicalSnapshots(rows).before(scope,update);
                require(rows.referenceCalls==0 && scope.getBeforeReferences("item").isEmpty(),"Reference capture is limited to deletion");
            } finally { scope.close(); }
            rows=new Rows(type,"B1");scope=new AuditCaptureScope(delete,false);
            try { new InventoryPhysicalSnapshots(rows).before(scope,delete);
                require(rows.reads==0 && rows.referenceCalls==0,"Disabled audit must not read shared batch records");
            } finally { scope.close(); }
        }
        Rows paddedReference=new Rows("IC_SAPPLY","B1");paddedReference.padReference=true;
        OperationContext paddedDelete=context("IC_SAPPLY","DELETE");AuditCaptureScope paddedScope=new AuditCaptureScope(paddedDelete,true);
        try {
            new InventoryPhysicalSnapshots(paddedReference).before(paddedScope,paddedDelete);
            SnapshotRow reference=paddedScope.getBeforeReferences("item").get(0);
            require("B1".equals(reference.getRowId()) && "B1                  ".equals(reference.getValues().get("pk_batchcode")),
                "Outbound batch CHAR key must link by its canonical identity and retain the physical value");
        } finally {paddedScope.close();}
        PhysicalSnapshotReader reader=new PhysicalSnapshotReader();
        require(reader.readInventoryBatchReferences(Collections.<String>emptyList(),"G",true).isEmpty(),"Empty key set must not query");
        try { reader.readInventoryBatchReferences(Arrays.asList("B","B"),"G",true);throw new AssertionError("Duplicate keys accepted"); }
        catch(IllegalArgumentException expected) { }
        try { reader.readInventoryBatchReferences(Arrays.asList("B")," ",true);throw new AssertionError("Missing group accepted"); }
        catch(IllegalArgumentException expected) { }
        verifyBatchedQueries();
        System.out.println("PASS InventoryBatchReferenceTests (shared reference evidence and group scope; no Oracle)");
    }

    private static void verifyBatchedQueries() throws Exception {
        FakeQuery query=new FakeQuery();PhysicalSnapshotReader reader=new PhysicalSnapshotReader(query);
        List<String> keys=new ArrayList<String>();for(int i=901;i>=1;i--) keys.add(String.format("B%04d",i));
        reader.readInventoryBatchReferences(keys,"G",true);
        require(query.sql.size()==2 && query.parameters.get(0).size()==901 && query.parameters.get(1).size()==2,
            "Oracle IN queries must split at 900 keys and bind the group");
        require("B0001".equals(query.parameters.get(0).get(0)) && "B0900".equals(query.parameters.get(0).get(899))
            && "G".equals(query.parameters.get(0).get(900)) && "B0901".equals(query.parameters.get(1).get(0)),
            "Reference keys and group must be bound in sorted order");
        for(String sql:query.sql) require(sql.contains("pk_group=?") && sql.contains("ORDER BY pk_batchcode FOR UPDATE NOWAIT")
            && !sql.contains("NVL(dr,0)=0"),"Reference queries must retain group scope, locks and historical rows");
        query=new FakeQuery();query.fields.clear();query.fields.put("cgeneralbid","body");query.fields.put("dr","deleted");
        query.fields.put("noutboundnum","quantity");reader=new PhysicalSnapshotReader(query);
        reader.readSapplyExtensions(keys,true);
        require(query.sql.size()==2 && query.parameters.get(0).size()==900 && query.parameters.get(1).size()==1,
                "Outbound extension queries must split at 900 body keys");
        require("B0001               ".equals(query.parameters.get(0).get(0)) && "B0901               ".equals(query.parameters.get(1).get(0)),
                "Outbound extension identities must be bound in sorted order");
        for(String sql:query.sql)require(sql.contains("FROM ic_sapply_e WHERE cgeneralbid")
                && sql.endsWith("ORDER BY cgeneralbid FOR UPDATE NOWAIT") && !sql.contains("NVL(dr,0)=0"),
                "Outbound extensions must include logical-delete state under the authorized body keys");
        query=new FakeQuery();reader=new PhysicalSnapshotReader(query);
        reader.readSapplyBatchReferences(Collections.singletonList("B1"),"G",true);
        require(query.parameters.get(0).equals(Arrays.<Object>asList("B1                  ","G"))
                && query.sql.get(0).contains("pk_group=?"),"Outbound batch CHAR identity must be padded and scoped");
        query=new FakeQuery();query.fields.clear();query.fields.put("cgeneralhid","bill");query.fields.put("pk_org","organization");
        query.fields.put("pk_group","group");query.fields.put("dr","deleted");reader=new PhysicalSnapshotReader(query);
        reader.readInventory("ic_sapply_h","cgeneralhid",null,"BILL",true);
        require(query.parameters.size()==1 && query.parameters.get(0).equals(Collections.<Object>singletonList("BILL                "))
                && query.sql.get(0).contains("FROM ic_sapply_h") && query.sql.get(0).endsWith("FOR UPDATE NOWAIT"),
                "Outbound header CHAR identity must be padded and the bill row locked");
        query=new FakeQuery();query.badCharType=true;reader=new PhysicalSnapshotReader(query);
        try{reader.readInventory("ic_sapply_h","cgeneralhid",null,"BILL",true);throw new AssertionError("Unreviewed bill CHAR type accepted");}
        catch(nc.bs.dao.DAOException expected) { }
        try{reader.readSapplyExtensions(Arrays.asList("B","B"),true);throw new AssertionError("Duplicate extension keys accepted");}
        catch(IllegalArgumentException expected) { }
        query=new FakeQuery();reader=new PhysicalSnapshotReader(query);
        reader.readInventoryBatchReferences(Collections.singletonList("B' OR 1=1 --"),"G'X",false);
        require(query.sql.size()==1 && !query.sql.get(0).contains("B' OR") && !query.sql.get(0).contains("G'X")
            && "B' OR 1=1 --".equals(query.parameters.get(0).get(0)) && "G'X".equals(query.parameters.get(0).get(1)),
            "Keys and group must be parameters, not SQL text");
        query=new FakeQuery();query.fields.clear();query.fields.put("cgeneralhid","bill");
        query.fields.put("pk_group","group");query.fields.put("dr","deleted");reader=new PhysicalSnapshotReader(query);
        reader.readLocked("ic_purchasein_h","cgeneralhid",null,"cgeneralhid","BILL","pk_group","G",true);
        require(query.sql.size()==1 && query.sql.get(0).contains("WHERE cgeneralhid=? AND pk_group=? AND NVL(dr,0)=0")
            && query.sql.get(0).endsWith("ORDER BY cgeneralhid FOR UPDATE NOWAIT")
            && query.parameters.get(0).equals(Arrays.<Object>asList("BILL","G")),
            "Existing single-key scoped snapshot query must retain its filters and bind order");
    }

    private static void reject(Rows rows,OperationContext context,String message) throws Exception {
        AuditCaptureScope scope=new AuditCaptureScope(context,true);
        try { try { new InventoryPhysicalSnapshots(rows).before(scope,context);throw new AssertionError(message); }
            catch(BusinessException expected) { }
        } finally { scope.close(); }
    }
    private static void require(boolean value,String message) { if(!value)throw new AssertionError(message); }
    private static OperationContext context(String type,String operation) {
        return new OperationContext("request","batch","G","O","node","inventory","Delete","delete",operation,null,
            Collections.singletonList(new BusinessObjectRef("item",type,"BILL","CODE",null)));
    }
    private static SnapshotRow row(String entity,String id,String parent,Map<String,String> values) {
        return new SnapshotRow(entity,id,parent,values,Collections.<String,String>emptyMap(),Collections.<String,String>emptyMap(),true);
    }
    private static final class Rows implements InventoryPhysicalSnapshots.Rows {
        final String prefix;final List<String> batches;int reads,referenceCalls;boolean locked;
        boolean missing,nullResult,wrongGroup,wrongEntity,wrongId,duplicate,omitColumn,padReference;List<String> requested;
        Rows(String type,String... batches) { this.prefix="IC_SAPPLY".equals(type)?"ic_sapply":"IC_PURCHASE_IN".equals(type)||"IC_OUTSOURCE_IN".equals(type)?"ic_purchasein":"IC_SALE_OUT".equals(type)?"ic_saleout":"IC_INITIAL_IN".equals(type)?"ic_openbal":"IC_GENERAL_IN".equals(type)?"ic_generalin":"IC_PRODUCT_IN".equals(type)||"IC_PRODUCTION_ORDER".equals(type)?"ic_finprodin":"IC_SUBCONT_IN".equals(type)?"ic_subcontin":"IC_GENERAL_OUT".equals(type)?"ic_generalout":"IC_MATERIAL_OUT".equals(type)?"ic_material":"IC_TRANS_OUT".equals(type)?"ic_transout":"ic_transin";this.batches=Arrays.asList(batches); }
        public List<SnapshotRow> read(String table,String key,String parent,String pk,boolean lock) {
            reads++;List<SnapshotRow> rows=new ArrayList<SnapshotRow>();
            if(table.equals(prefix+"_h")) {
                Map<String,String> values=new HashMap<String,String>();values.put("pk_group","G");values.put("pk_org","O");values.put("vbillcode","CODE");
                rows.add(row(table,pk,null,values));
            } else if(table.equals(prefix+"_b")) {
                for(int i=0;i<batches.size();i++) {Map<String,String> values=new HashMap<String,String>();
                    if(!omitColumn)values.put("pk_batchcode",batches.get(i));rows.add(row(table,"body"+i,pk,values));}
            } else if(!table.equals(prefix+"_l") || "ic_sapply".equals(prefix)) throw new AssertionError("Unexpected table "+table);
            return rows;
        }
        public List<SnapshotRow> readBatchReferences(List<String> keys,String group,boolean lock,boolean sapply) {
            referenceCalls++;requested=new ArrayList<String>(keys);locked=lock;require("G".equals(group),"Wrong requested group");
            List<SnapshotRow> rows=new ArrayList<SnapshotRow>();
            if(nullResult)return null;
            if(missing)return rows;
            for(int i=keys.size()-1;i>=0;i--) {
                String key=keys.get(i);Map<String,String> values=new HashMap<String,String>();
                values.put("pk_batchcode",wrongId?"OTHER":padReference?"B1                  ":key);values.put("pk_group",wrongGroup?"OTHER":"G");
                rows.add(row(wrongEntity?"other":"scm_batchcode",key,null,values));
            }
            if(duplicate && !rows.isEmpty()) rows.add(rows.get(0));
            return rows;
        }
        public List<SnapshotRow> readSapplyExtensions(List<String> keys,boolean lock) {return Collections.emptyList();}
    }
    private static final class FakeAuditStore extends QdEsignStore {
        int references,deletedBillRows,referenceChanges;boolean failReferences;
        public void insert(String table,Map<String,Object> values) throws DAOException {
            boolean reference=InventoryPhysicalSnapshots.BATCH_REFERENCE_ENTITY.equals(values.get("entity_name"));
            if(reference && failReferences) throw new DAOException("injected reference write failure");
            if("qd_es_snapshot".equals(table) && reference) {
                require("BEFORE".equals(values.get("snapshot_phase")),"Reference snapshot phase must be BEFORE");references++;
            }
            if("qd_es_snapshot".equals(table) && !reference && "DELETE".equals(values.get("snapshot_phase")))deletedBillRows++;
            if("qd_es_change".equals(table) && reference)referenceChanges++;
        }
    }
    private static final class FakeQuery implements PhysicalSnapshotReader.QueryExecutor {
        final List<String> sql=new ArrayList<String>();final List<List<Object>> parameters=new ArrayList<List<Object>>();boolean badCharType;
        final Map<String,String> fields=new LinkedHashMap<String,String>();
        FakeQuery() { fields.put("pk_batchcode","batch");fields.put("pk_group","group"); }
        public Object execute(String statement,SQLParameter args,ResultSetProcessor processor) {
            if(statement.contains("user_tab_columns") && statement.contains("data_type"))return Boolean.valueOf(!badCharType);
            if(statement.contains("user_tab_columns")) return new LinkedHashMap<String,String>(fields);
            sql.add(statement);parameters.add(new ArrayList<Object>(args.getParameters()));return Collections.<SnapshotRow>emptyList();
        }
    }
}
