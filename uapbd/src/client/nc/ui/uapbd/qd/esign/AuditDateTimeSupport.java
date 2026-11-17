package nc.ui.uapbd.qd.esign;

import java.awt.Component;
import nc.ui.pub.beans.UIRefPane;
import nc.ui.querytemplate.querytype.TimeSpinner;
import nc.ui.querytemplate.querytype.UFDateTimeTextField;

/** 适配 NC65 组合日期时间控件的日期与时间部分，避免 UFDate 返回含时间的文本。 */
final class AuditDateTimeSupport {
    private AuditDateTimeSupport() { }

    static void set(UFDateTimeTextField field, String value) {
        LogUiFormats.utcFromBeijing(value);
        date(field).setText(value.substring(0, 10));
        time(field).setText(value.substring(11, 19));
    }

    static String get(UFDateTimeTextField field) {
        return combine(date(field).getText(), time(field).getText());
    }

    static String combine(String day, String clock) {
        if (day == null) { throw new IllegalArgumentException("请输入有效的日期。"); }
        day = day.trim();
        // 此版本的 NC 日期参照可能返回包含零点的 19 位日期时间。
        if (day.length() == 19 && day.charAt(10) == ' ') {
            LogUiFormats.utcFromBeijing(day); day = day.substring(0, 10);
        }
        String result = day + " " + (clock == null ? "" : clock.trim());
        LogUiFormats.utcFromBeijing(result);
        return result;
    }

    private static UIRefPane date(UFDateTimeTextField field) {
        for (Component component : field.getComponents()) { if (component instanceof UIRefPane) { return (UIRefPane) component; } }
        throw new IllegalStateException("NC 日期控件结构不兼容。");
    }
    private static TimeSpinner time(UFDateTimeTextField field) {
        for (Component component : field.getComponents()) { if (component instanceof TimeSpinner) { return (TimeSpinner) component; } }
        throw new IllegalStateException("NC 时间控件结构不兼容。");
    }
}
