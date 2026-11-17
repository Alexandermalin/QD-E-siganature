package nc.bs.uapbd.qd.esign.persistence;

import java.sql.Types;
import java.lang.reflect.Method;
import nc.jdbc.framework.SQLParameter;
import nc.jdbc.framework.type.NullParamType;

/** 验证 NC 参数对象对可空文本和 CLOB 的绑定。 */
public final class NullParameterTests {
    private NullParameterTests() { }

    public static void run() throws Exception {
        SQLParameter positional = QdEsignStore.parameters("before", null, "after");
        check("before".equals(positional.get(0)) && "after".equals(positional.get(2)),
            "positional parameter order");
        checkNull(positional.get(1), Types.VARCHAR);

        QdEsignStore store = new QdEsignStore();
        Method bind = QdEsignStore.class.getDeclaredMethod("bind", SQLParameter.class, String.class, Object.class);
        bind.setAccessible(true);
        SQLParameter fields = new SQLParameter();
        for (String field : new String[] {"reason", "client_ip", "object_pk", "parent_object_id", "before_value", "after_value"}) {
            bind.invoke(store, fields, field, null);
        }
        for (int index = 0; index < 4; index++) { checkNull(fields.get(index), Types.VARCHAR); }
        checkNull(fields.get(4), Types.CLOB);
        checkNull(fields.get(5), Types.CLOB);
        System.out.println("PASS NullParameterTests (NC nullable text and CLOB binding)");
    }

    private static void checkNull(Object value, int expectedType) {
        check(value instanceof NullParamType && ((NullParamType) value).getType() == expectedType,
            "expected typed SQL NULL");
    }

    private static void check(boolean result, String message) {
        if (!result) { throw new AssertionError(message); }
    }

}
