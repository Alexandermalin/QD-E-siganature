package nc.bs.uapbd.qd.esign.core;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;

/** Exact typed values are signed, and native value-comparison rules determine which rows are no-ops. */
public final class NativeBatchChangeSetTests {
    private NativeBatchChangeSetTests() { }
    public static void run() throws Exception {
        Set<String> allowed=new HashSet<String>(Arrays.asList("name","flag","custom"));
        Map<String,Object> values=new HashMap<String,Object>();values.put("name","new");values.put("flag",Integer.valueOf(1));
        NativeBatchChangeSet a=NativeBatchChangeSet.create(new String[]{"flag","name"},values,allowed);
        NativeBatchChangeSet b=NativeBatchChangeSet.create(new String[]{"name","flag"},values,allowed);
        require(!a.getFingerprint().equals(b.getFingerprint()),"Changed native field order did not invalidate the signed batch values");
        String[] selectedCopy=a.getSelectedFields();selectedCopy[0]="changed";
        require("flag".equals(a.getSelectedFields()[0]),"Native selected-field array was not copied");
        values.put("name","other");NativeBatchChangeSet c=NativeBatchChangeSet.create(new String[]{"name","flag"},values,allowed);
        require(!a.getFingerprint().equals(c.getFingerprint()),"Changed field value did not invalidate the fingerprint");
        values.put("name","new");values.put("flag",Long.valueOf(1));
        require(!a.getFingerprint().equals(NativeBatchChangeSet.create(new String[]{"name","flag"},values,allowed).getFingerprint()),
            "Changed field type did not invalidate the fingerprint");
        values.put("flag",Integer.valueOf(1));
        List<Row> rows=Arrays.asList(new Row("A","new",1),new Row("B","old",1),new Row("C","new",0));
        require(a.unchanged(rows).equals(new HashSet<String>(Arrays.asList("A"))),"Native value comparison misclassified no-op rows");
        java.util.Date mutable=new java.util.Date(1000L);Map<String,Object> dateValue=new HashMap<String,Object>();dateValue.put("custom",mutable);
        NativeBatchChangeSet protectedValue=NativeBatchChangeSet.create(new String[]{"custom"},dateValue,allowed);
        mutable.setTime(2000L);java.util.Date copied=(java.util.Date)protectedValue.copyValues().get("custom");
        require(copied.getTime()==1000L,"Mutating the client date changed the signed value");copied.setTime(3000L);
        require(((java.util.Date)protectedValue.copyValues().get("custom")).getTime()==1000L,"Native service copy changed the signed value");
        dateValue.put("custom",new java.util.Date(1001L));String millisHash=NativeBatchChangeSet.create(new String[]{"custom"},dateValue,allowed).getFingerprint();
        dateValue.put("custom",new java.util.Date(1002L));require(!millisHash.equals(NativeBatchChangeSet.create(new String[]{"custom"},dateValue,allowed).getFingerprint()),
            "Different sub-second dates shared a signature fingerprint");
        dateValue.put("custom",new nc.vo.pub.lang.UFDateTime(1001L));millisHash=NativeBatchChangeSet.create(new String[]{"custom"},dateValue,allowed).getFingerprint();
        dateValue.put("custom",new nc.vo.pub.lang.UFDateTime(1002L));require(millisHash.equals(NativeBatchChangeSet.create(new String[]{"custom"},dateValue,allowed).getFingerprint()),
            "Equal NC date-times, which internally truncate to seconds, did not match");
        dateValue.put("custom",new nc.vo.pub.lang.UFDateTime(2002L));require(!millisHash.equals(NativeBatchChangeSet.create(new String[]{"custom"},dateValue,allowed).getFingerprint()),
            "Different NC date-times shared a signature fingerprint");
        java.sql.Timestamp first=java.sql.Timestamp.valueOf("2026-11-11 00:00:00.123456100");
        java.sql.Timestamp second=java.sql.Timestamp.valueOf("2026-11-11 00:00:00.123456200");
        dateValue.put("custom",first);String firstHash=NativeBatchChangeSet.create(new String[]{"custom"},dateValue,allowed).getFingerprint();
        dateValue.put("custom",second);require(!firstHash.equals(NativeBatchChangeSet.create(new String[]{"custom"},dateValue,allowed).getFingerprint()),
            "Different sub-millisecond timestamp values shared a signature fingerprint");
        dateValue.put("custom",new java.math.BigDecimal("1.0"));String numericHash=NativeBatchChangeSet.create(new String[]{"custom"},dateValue,allowed).getFingerprint();
        dateValue.put("custom",new java.math.BigDecimal("1.00"));require(!numericHash.equals(NativeBatchChangeSet.create(new String[]{"custom"},dateValue,allowed).getFingerprint()),
            "Different decimal representations shared a signature fingerprint");
        dateValue.put("custom",Float.valueOf(Float.NaN));reject(new String[]{"custom"},dateValue,allowed);
        reject(new String[]{"name"},values,allowed);reject(new String[]{"name","name"},values,allowed);
        Map<String,Object> unknown=new HashMap<String,Object>();unknown.put("custom",new byte[]{1});reject(new String[]{"custom"},unknown,allowed);
        unknown.clear();unknown.put("password","secret");Set<String> broad=new HashSet<String>(Arrays.asList("password"));reject(new String[]{"password"},unknown,broad);
        unknown.clear();unknown.put("other","x");reject(new String[]{"other"},unknown,allowed);
        System.out.println("PASS NativeBatchChangeSetTests (typed value binding and native no-op comparison; no NC or Oracle)");
    }
    private static void reject(String[] fields,Map<String,Object> values,Set<String> allowed) throws Exception {
        try { NativeBatchChangeSet.create(fields,values,allowed);throw new AssertionError("Unsafe batch field or value accepted"); }
        catch(BusinessException expected) { }
    }
    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
    private static final class Row extends SuperVO {
        private static final long serialVersionUID=1L;private final String id;private final Map<String,Object> data=new HashMap<String,Object>();
        Row(String id,String name,int flag) { this.id=id;data.put("name",name);data.put("flag",Integer.valueOf(flag)); }
        public String getPrimaryKey() { return id; }
        public String getPKFieldName() { return "pk"; }
        public String getParentPKFieldName() { return null; }
        public String getTableName() { return "bd_customer"; }
        public Object getAttributeValue(String name) { return data.get(name); }
    }
}
