package nc.bs.uapbd.qd.esign.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Executes the production eligibility predicate in an in-memory SQL engine; Oracle and NC are not invoked. */
public final class NativeOrgBatchArchiveTests {
    private NativeOrgBatchArchiveTests() { }
    public static void run() throws Exception {
        require(QdTableMetadata.columns("qd_es_batch_page").equals(NativeOrgBatchReceiptRecords.columns()),"Receipt columns diverged");
        require("qd_es_batch_page_h202608".equals(QdTableMetadata.historyTable("qd_es_batch_page","202608")),
            "Receipt history is not on the fixed whitelist");
        Class.forName("org.hsqldb.jdbcDriver");
        try(Connection connection=DriverManager.getConnection("jdbc:hsqldb:mem:qd_batch_archive_test","SA", "")) {
            try(Statement sql=connection.createStatement()) {
                sql.execute("CREATE TABLE qd_es_locator(request_id VARCHAR(32),archive_month VARCHAR(6),request_closed CHAR(1))");
                sql.execute("CREATE TABLE qd_es_object(object_id VARCHAR(32),request_id VARCHAR(32),archive_month VARCHAR(6))");
                sql.execute("CREATE TABLE qd_es_request(request_id VARCHAR(32),archive_month VARCHAR(6))");
                sql.execute("CREATE TABLE qd_es_batch_page(request_id VARCHAR(32),object_id VARCHAR(32),archive_month VARCHAR(6))");
                sql.execute("INSERT INTO qd_es_locator VALUES('RA','202608','Y'),('RB','202608','Y'),('RC','202608','Y'),('RD','202608','N')");
                sql.execute("INSERT INTO qd_es_locator VALUES('RA','202609','Y'),('RB','202609','Y'),('RD','202609','N')");
                sql.execute("INSERT INTO qd_es_object VALUES('A','RA','202608'),('B','RB','202608'),('C','RC','202608'),('D','RD','202608')");
                sql.execute("INSERT INTO qd_es_request VALUES('RA','202608'),('RB','202608'),('RC','202608'),('RD','202608')");
                sql.execute("INSERT INTO qd_es_batch_page VALUES('RA','A','202609'),('RB','@TARGETS','202609'),('RD','D','202609')");
            }
            require(query(connection,"qd_es_object","object_id","202608",true).equals(Arrays.asList("B","C")),
                "A later-month page receipt did not retain its object");
            require(query(connection,"qd_es_request","request_id","202608",true).equals(Arrays.asList("RC")),
                "A page or target receipt did not retain its request");
            require(query(connection,"qd_es_object","object_id","202608",false).equals(Arrays.asList("A","B","C")),
                "Legacy eligibility changed when receipt storage is absent");
            require(query(connection,"qd_es_batch_page","object_id","202609",true).size()==2,
                "Open requests were incorrectly eligible for receipt archive");
            int blockedObjects=query(connection,"qd_es_object","object_id","202608",false).size()
                -query(connection,"qd_es_object","object_id","202608",true).size();
            int blockedRequests=query(connection,"qd_es_request","request_id","202608",false).size()
                -query(connection,"qd_es_request","request_id","202608",true).size();
            require(blockedObjects==1 && blockedRequests==2 && "PARTIAL".equals(MonthlyArchiveStore.completionState(0,0,3)),
                "Receipt dependencies were reported as a completely archived month");
            try(Statement sql=connection.createStatement()) { sql.execute("DELETE FROM qd_es_batch_page WHERE request_id='RA'"); }
            require(query(connection,"qd_es_object","object_id","202608",true).equals(Arrays.asList("A","B","C"))
                && query(connection,"qd_es_request","request_id","202608",true).equals(Arrays.asList("RA","RC")),
                "Archived receipt did not release its object and request");
            try(Statement sql=connection.createStatement()) { sql.execute("DELETE FROM qd_es_batch_page WHERE request_id='RB'"); }
            require(query(connection,"qd_es_request","request_id","202608",true).equals(Arrays.asList("RA","RB","RC")),
                "Target-only receipt did not release its request");
            require("ARCHIVED".equals(MonthlyArchiveStore.completionState(0,0,0))
                && "PARTIAL".equals(MonthlyArchiveStore.completionState(0,1,0))
                && "RUNNING".equals(MonthlyArchiveStore.completionState(1,0,0)),"Archive completion state is inconsistent");
        }
        System.out.println("PASS NativeOrgBatchArchiveTests (cross-month receipt dependencies; in-memory SQL, no Oracle or NC)");
    }
    private static List<String> query(Connection connection,String table,String key,String month,boolean receipt) throws Exception {
        String sql="SELECT s."+key+" FROM "+table+" s WHERE "+MonthlyArchiveStore.eligible(table,receipt)+" ORDER BY s."+key;
        List<String> result=new ArrayList<String>();
        try(PreparedStatement statement=connection.prepareStatement(sql)) {
            statement.setString(1,month);
            try(ResultSet rows=statement.executeQuery()) { while(rows.next()) result.add(rows.getString(1)); }
        }
        return result;
    }
    private static void require(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
}
