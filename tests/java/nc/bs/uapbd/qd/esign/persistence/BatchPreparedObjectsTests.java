package nc.bs.uapbd.qd.esign.persistence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import nc.bs.dao.DAOException;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.jdbc.framework.SQLParameter;

/** The prepared request's locator is checked once while all object rows remain transaction-bound. */
public final class BatchPreparedObjectsTests {
    private static final String ID="0123456789abcdef0123456789abcdef",MONTH="202611";
    private BatchPreparedObjectsTests() { }
    public static void run() throws Exception {
        Store store=new Store();QdEsignStore.PreparedBatchObjects batch=store.beginPreparedBatchObjects(ID,MONTH);
        batch.insert(row("A"));batch.insert(row("B"));batch.finish();batch.close();
        require(store.queries==1 && store.rows.size()==2,"Object preparation repeatedly queried the request locator");
        try { batch.insert(row("C"));throw new AssertionError("Finished batch writer was reused"); } catch(DAOException expected) { }
        store=new Store();store.expected=10000;batch=store.beginPreparedBatchObjects(ID,MONTH);
        for(int i=0;i<10000;i++) batch.insert(row("B"+i));
        batch.finish();require(store.queries==1 && store.rows.size()==10000,"Large object preparation repeated locator queries");
        store=new Store();batch=store.beginPreparedBatchObjects(ID,MONTH);batch.insert(row("A"));
        try { batch.finish();throw new AssertionError("Incomplete object set was accepted"); } catch(DAOException expected) { }
        batch.close();
        store=new Store();batch=store.beginPreparedBatchObjects(ID,MONTH);
        Map<String,Object> wrong=row("A");wrong.put("pk_group","OTHER");reject(batch,wrong);
        wrong=row("A");wrong.put("archive_month","202612");reject(batch,wrong);
        wrong=row("A");wrong.put("object_type","BD_SUPPLIER");reject(batch,wrong);
        wrong=row("A");wrong.put("parent_object_id","FORGED");reject(batch,wrong);
        wrong=row("A");wrong.put("target_org","OTHER");reject(batch,wrong);
        final QdEsignStore.PreparedBatchObjects foreignBatch=batch;final boolean[] denied={false};
        Thread foreign=new Thread(new Runnable() { public void run() {
            try { foreignBatch.insert(row("A")); } catch(DAOException expected) { denied[0]=true; }
        }});
        foreign.start();foreign.join();require(denied[0] && store.rows.isEmpty(),"Another thread reused a preparation writer");
        batch.close();
        store=new Store();store.missing=true;
        try { store.beginPreparedBatchObjects(ID,MONTH);throw new AssertionError("Missing request or locator was accepted"); }
        catch(DAOException expected) { }
        store=new Store();store.node="OTHER";
        try { store.beginPreparedBatchObjects(ID,MONTH);throw new AssertionError("Unrelated node was accepted"); }
        catch(DAOException expected) { }
        System.out.println("PASS BatchPreparedObjectsTests (request locator and scoped object insertion; no NC or Oracle)");
    }
    private static void reject(QdEsignStore.PreparedBatchObjects batch,Map<String,Object> row) throws Exception {
        try { batch.insert(row);throw new AssertionError("A foreign prepared object was accepted"); } catch(DAOException expected) { }
    }
    private static Map<String,Object> row(String id) {
        return EsignValues.row("object_id",id,"request_id",ID,"archive_month",MONTH,"pk_group","G","pk_org","ORG",
            "object_type","BD_CUSTOMER","relation_type","ROOT","parent_object_id",null,"unit_state","PENDING","target_org","ORG");
    }
    private static void require(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
    private static final class Store extends QdEsignStore {
        int queries,expected=2;boolean missing;String node="10140CUG";final List<Map<String,Object>> rows=new ArrayList<Map<String,Object>>();
        @Override public Map<String,Object> one(String sql,SQLParameter parameters) {
            queries++;
            require(sql.contains("JOIN qd_es_locator") && sql.contains("l.request_closed='N'")
                && sql.contains("NOT EXISTS (SELECT 1 FROM qd_es_object") && sql.contains("r.execution_token IS NULL")
                && ID.equals(parameters.get(0)) && MONTH.equals(parameters.get(1)),"Prepared request query lost locator or transaction guards");
            return missing?null:EsignValues.row("pk_group","G","pk_org","ORG","node_code",node,"owner_object_count",expected);
        }
        @Override protected void insertPreparedBatchObjectRow(Map<String,Object> values) { rows.add(new LinkedHashMap<String,Object>(values)); }
    }
}
