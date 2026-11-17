package nc.ui.uapbd.qd.esign;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** 日志界面的显式时区转换工具，固定在 UTC 和 Asia/Shanghai 间转换，不依赖客户端 Windows 或 JVM 默认时区。 */
final class LogUiFormats {
    /** 工具类不提供实例，使用静态方法完成本类职责。 */
    private LogUiFormats() { }
    /** 把 epoch 毫秒格式化为北京时间文本。 */
    static String beijing(long epoch) { return format("Asia/Shanghai").format(new Date(epoch)); }
    /** 严格解析北京时间输入后转换为 UTC 筛选值。 */
    static String utcFromBeijing(String text) {
        return format("UTC").format(parse(text, "Asia/Shanghai"));
    }
    /** 将 UTC 日志时间转换为北京时间展示文本。 */
    static String beijingFromUtc(String text) {
        return format("Asia/Shanghai").format(parse(text, "UTC"));
    }
    /** 按明确时区及严格格式解析日期时间，拒绝自动纠正非法日期。 */
    private static Date parse(String text, String zone) {
        if (text == null || text.length() != 19) {
            throw new IllegalArgumentException("时间格式应为 yyyy-MM-dd HH:mm:ss。");
        }
        ParsePosition position = new ParsePosition(0);
        Date parsed = format(zone).parse(text, position);
        if (parsed == null || position.getIndex() != text.length()) {
            throw new IllegalArgumentException("时间无效，请使用 yyyy-MM-dd HH:mm:ss。");
        }
        return parsed;
    }
    /** 构造指定时区的独立日期格式器，关闭宽松解析，避免共享非线程安全格式器。 */
    private static SimpleDateFormat format(String zone) {
        SimpleDateFormat result = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT);
        result.setLenient(false);
        result.setTimeZone(TimeZone.getTimeZone(zone));
        return result;
    }
}
