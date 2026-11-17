package nc.bs.uapbd.qd.esign.persistence;

import java.util.Map;
import nc.bs.dao.DAOException;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.jdbc.framework.SQLParameter;

/** Offline boundary check for large requests; it does not inspect or modify Oracle. */
public final class BatchCountCapacityTests {
    private BatchCountCapacityTests() { }
    public static void run() throws Exception {
        FakeStore store=new FakeStore();
        store.requireBatchObjectCountCapacity(9999);
        require(store.calls==0,"The legacy supported range unexpectedly queried metadata");
        store.value=EsignValues.row("table_count",3,"online_count",1,"compatible_count",3);
        store.requireBatchObjectCountCapacity(10000);
        store.requireBatchObjectCountCapacity(199999);
        require(store.calls==2,"Large requests did not inspect online and history table capacity");
        reject(store,EsignValues.row("table_count",3,"online_count",1,"compatible_count",2));
        reject(store,EsignValues.row("table_count",1,"online_count",0,"compatible_count",1));
        reject(store,EsignValues.row("table_count",0,"online_count",0,"compatible_count",0));
        reject(store,EsignValues.row("table_count",1,"online_count",1,"compatible_count",null));
        store.value=null;
        try { store.requireBatchObjectCountCapacity(10000);throw new AssertionError("Missing metadata was accepted"); }
        catch(DAOException expected) { }
        System.out.println("PASS BatchCountCapacityTests (large-request schema boundary; no NC or Oracle)");
    }
    private static void reject(FakeStore store,Map<String,Object> row) throws Exception {
        store.value=row;
        try { store.requireBatchObjectCountCapacity(10000);throw new AssertionError("Incompatible request schema was accepted"); }
        catch(DAOException expected) { }
    }
    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
    private static final class FakeStore extends QdEsignStore {
        Map<String,Object> value;int calls;
        @Override public Map<String,Object> one(String sql,SQLParameter parameters) {
            calls++;
            require(sql.contains("FROM user_tables t LEFT JOIN user_tab_columns c")
                && sql.contains("^QD_ES_REQUEST_H[0-9]{6}$") && sql.contains("c.data_precision>=6")
                && sql.contains("c.data_scale=0") && sql.contains("c.nullable='Y'")
                && parameters.getParameters().isEmpty(),"Capacity query lost table, type, scale or history verification");
            return value;
        }
    }
}
