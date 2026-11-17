package nc.bs.uapbd.qd.esign.core;

import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.Locale;

/** 按实际 Oracle 数据库字符集防止 JDBC 将无法表示的字符静默替换为问号；不读取或缓存任何凭证。 */
public final class DatabaseTextRules {
    private DatabaseTextRules() { }

    /** 仅转换明确核实的 Oracle 字符集名；未知字符集必须拒绝部署而非猜测编码。 */
    public static Charset oracleCharset(String name) {
        if (name == null) { throw new IllegalArgumentException("Database charset is missing"); }
        String nativeName = name.trim().toUpperCase(Locale.ENGLISH);
        String javaName;
        if ("ZHS16GBK".equals(nativeName)) { javaName = "GBK"; }
        else if ("AL32UTF8".equals(nativeName)) { javaName = "UTF-8"; }
        else if ("UTF8".equals(nativeName)) { javaName = "CESU-8"; }
        else if ("US7ASCII".equals(nativeName)) { javaName = "US-ASCII"; }
        else if ("WE8MSWIN1252".equals(nativeName)) { javaName = "windows-1252"; }
        else if ("WE8ISO8859P1".equals(nativeName)) { javaName = "ISO-8859-1"; }
        else { throw new IllegalArgumentException("Unsupported database charset: " + nativeName); }
        return Charset.forName(javaName);
    }

    /** 编码器报告无法映射或非法代理对；调用 getBytes 前必须通过本检查，禁止默认替换。 */
    public static boolean lossless(String value, Charset charset) {
        return value == null || charset.newEncoder().canEncode(value);
    }

    /** 取消仍需留痕：将不能表示的代码单元显式写成 Unicode 转义并标记，再执行原有长度截断。 */
    public static String cancellation(String reason, Charset charset) {
        if (reason == null || lossless(reason, charset)) { return ReasonRules.cancellation(reason); }
        CharsetEncoder encoder = charset.newEncoder();
        StringBuilder encoded = new StringBuilder();
        for (int offset = 0; offset < reason.length();) {
            int end = offset + Character.charCount(reason.codePointAt(offset));
            if (encoder.canEncode(CharBuffer.wrap(reason, offset, end))) {
                encoded.append(reason, offset, end);
            } else {
                for (int at = offset; at < end; at++) {
                    encoded.append('\\').append('u');
                    String hex = Integer.toHexString(reason.charAt(at)).toUpperCase(Locale.ENGLISH);
                    for (int zero = hex.length(); zero < 4; zero++) { encoded.append('0'); }
                    encoded.append(hex);
                }
            }
            offset = end;
        }
        encoded.append(" [escaped for database]");
        return ReasonRules.cancellation(encoded.toString());
    }
}
