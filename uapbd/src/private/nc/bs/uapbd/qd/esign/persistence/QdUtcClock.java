package nc.bs.uapbd.qd.esign.persistence;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** 持久化时间工具：时间值统一保存为 UTC，归档路由使用北京时间自然月，与 JVM 默认时区无关。 */
public final class QdUtcClock {
    /** 工具类不提供实例，使用静态方法完成本类职责。 */
    private QdUtcClock() { }
    /** 返回当前 UTC 持久化时间文本。 */
    public static String now() { return utc(new Date()); }
    /** 以固定 UTC 时区格式化时间，避免默认时区影响持久化值。 */
    public static String utc(Date value) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH);
        f.setTimeZone(TimeZone.getTimeZone("UTC")); return f.format(value);
    }
    /** 严格解析持久化 UTC 时间，不依赖默认时区；无效日期或格式使调用失败。 */
    public static Date parseUtc(String value) {
        if (value == null || !value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}")) {
            throw new IllegalArgumentException("Expected UTC yyyy-MM-dd HH:mm:ss");
        }
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH);
        f.setTimeZone(TimeZone.getTimeZone("UTC")); f.setLenient(false);
        try { return f.parse(value); } catch (ParseException e) { throw new IllegalArgumentException("Invalid UTC timestamp", e); }
    }
    /** 按北京时间自然月计算指定瞬时值的归档月份。 */
    public static String beijingMonth(Date value) {
        SimpleDateFormat f = new SimpleDateFormat("yyyyMM", Locale.ENGLISH);
        f.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai")); return f.format(value);
    }
    /** 计算当前月及前两个月在线保留策略的最早月份，历史月份才可进入归档候选。 */
    public static String firstOnlineMonth(Date now) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"), Locale.ENGLISH);
        c.setTime(now); c.add(Calendar.MONTH, -2); return beijingMonth(c.getTime());
    }
}
