package nc.bs.uapbd.qd.esign.persistence;

import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Types;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import nc.jdbc.framework.processor.BaseProcessor;
import nc.vo.uapbd.qd.esign.audit.SnapshotContent;
import nc.vo.uapbd.qd.esign.audit.SnapshotPayload;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;

/** NC 结果集不提供 getClob，审计预览通过字符流保留长度和截断信息。 */
public final class AuditPreviewTests {
    private AuditPreviewTests() { }

    public static void run() throws Exception {
        check(AuditDetailsRepository.detailProjection("qd_es_change").contains("DBMS_LOB.GETLENGTH(before_value)"),
            "change length projection");
        check(AuditDetailsRepository.detailProjection("qd_es_snapshot").contains("DBMS_LOB.GETLENGTH(payload)"),
            "snapshot length projection");

        char[] characters = new char[100001];
        Arrays.fill(characters, '中');
        String longText = new String(characters);
        ResultSet rs = resultSet(new String[][] {{null, "测试内容"}, {longText, ""}});
        Class<?> type = Class.forName("nc.bs.uapbd.qd.esign.persistence.AuditDetailsRepository$PreviewProcessor");
        java.lang.reflect.Constructor<?> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        BaseProcessor processor = (BaseProcessor) constructor.newInstance();
        @SuppressWarnings("unchecked")
        List<Map<String,Object>> rows = (List<Map<String,Object>>) processor.processResultSet(rs);
        check(rows.size() == 2, "two rows");
        check(rows.get(0).get("before_value") == null
            && Long.valueOf(0).equals(rows.get(0).get("before_value_chars"))
            && Boolean.FALSE.equals(rows.get(0).get("before_value_truncated")), "null CLOB");
        check("测试内容".equals(rows.get(0).get("after_value"))
            && Long.valueOf(4).equals(rows.get(0).get("after_value_chars"))
            && Boolean.FALSE.equals(rows.get(0).get("after_value_truncated")), "short Unicode CLOB");
        check(((String)rows.get(1).get("before_value")).length() == 100000
            && Long.valueOf(100001).equals(rows.get(1).get("before_value_chars"))
            && Boolean.TRUE.equals(rows.get(1).get("before_value_truncated")), "long CLOB truncation");
        check("".equals(rows.get(1).get("after_value"))
            && Long.valueOf(0).equals(rows.get(1).get("after_value_chars")), "empty CLOB");
        check(!rows.get(0).containsKey("before_value_qd_length"), "internal length projection hidden");
        testFullSnapshot(longText);
        System.out.println("PASS AuditPreviewTests (CLOB previews and complete long snapshot fields)");
    }

    private static void testFullSnapshot(String text) throws Exception {
        Map<String,String> values = new LinkedHashMap<String,String>();
        values.put("code", "001234");
        values.put("empty_value", "");
        values.put("null_value", null);
        values.put("vdef1", text + "\n\\\"end");
        Map<String,String> types = new LinkedHashMap<String,String>();
        types.put("code", "VARCHAR2"); types.put("vdef1", "NCLOB");
        Map<String,String> labels = Collections.singletonMap("code", "物料编码");
        String payload = SnapshotPayload.encode(new SnapshotRow("bd_material", "pk", null, values, types, labels, true));
        SnapshotContent preview = SnapshotPayload.read(new StringReader(payload), null, 0, SnapshotContent.FIELD_PREVIEW);
        List<SnapshotContent.Field> fields = preview.getFields();
        check(preview.hasPhysicalColumns() && fields.size() == 4, "physical field set and coverage format retained");
        check("VARCHAR2".equals(fields.get(0).getType()) && "物料编码".equals(fields.get(0).getLabel())
            && fields.get(3).getLabel() == null, "recorded Oracle type and optional label retained");
        check("001234".equals(fields.get(0).getValue()) && "".equals(fields.get(1).getValue())
            && fields.get(2).getValue() == null, "leading zeros and empty/null values retained");
        check(fields.get(3).hasMore() && fields.get(3).getLength() == values.get("vdef1").length(), "long field is explicitly previewed");
        StringBuilder full = new StringBuilder();
        while (full.length() < values.get("vdef1").length()) {
            SnapshotContent.Field chunk = SnapshotPayload.read(new StringReader(payload), "vdef1", full.length(),
                SnapshotContent.VALUE_PAGE_SIZE).getFields().get(0);
            check(chunk.getOffset() == full.length(), "chunks are aligned to decoded field offsets");
            check("NCLOB".equals(chunk.getType()), "field chunks retain recorded metadata");
            full.append(chunk.getValue());
        }
        check(values.get("vdef1").contentEquals(full), "all long field characters available without truncation");
    }

    private static ResultSet resultSet(final String[][] values) {
        final String[] names = {"before_value", "after_value", "before_value_qd_length", "after_value_qd_length"};
        final ResultSetMetaData metadata = (ResultSetMetaData) Proxy.newProxyInstance(
            ResultSetMetaData.class.getClassLoader(), new Class<?>[] {ResultSetMetaData.class},
            new InvocationHandler() {
                @Override public Object invoke(Object proxy, Method method, Object[] args) {
                    if ("getColumnCount".equals(method.getName())) { return Integer.valueOf(names.length); }
                    int index = ((Integer)args[0]).intValue() - 1;
                    if ("getColumnLabel".equals(method.getName())) { return names[index]; }
                    if ("getColumnType".equals(method.getName())) {
                        return Integer.valueOf(index < 2 ? Types.CLOB : Types.NUMERIC);
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
            });
        return (ResultSet) Proxy.newProxyInstance(ResultSet.class.getClassLoader(), new Class<?>[] {ResultSet.class},
            new InvocationHandler() {
                private int row = -1;
                private boolean wasNull;
                @Override public Object invoke(Object proxy, Method method, Object[] args) {
                    String name = method.getName();
                    if ("getMetaData".equals(name)) { return metadata; }
                    if ("next".equals(name)) { return Boolean.valueOf(++row < values.length); }
                    if ("getLong".equals(name)) {
                        int index = names[2].equals(args[0]) ? 0 : 1;
                        String value = values[row][index];
                        wasNull = value == null;
                        return Long.valueOf(value == null ? 0 : value.length());
                    }
                    if ("wasNull".equals(name)) { return Boolean.valueOf(wasNull); }
                    if ("getCharacterStream".equals(name)) {
                        String value = values[row][((Integer)args[0]).intValue() - 1];
                        return value == null ? null : (Reader)new StringReader(value);
                    }
                    throw new UnsupportedOperationException(name);
                }
            });
    }

    private static void check(boolean result, String message) {
        if (!result) { throw new AssertionError(message); }
    }
}
