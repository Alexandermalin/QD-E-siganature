package nc.ui.uapbd.qd.esign;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import nc.vo.uapbd.qd.esign.log.LogKind;

/** 已加载日志页的标识提取工具，只交接事件 ID；导出时服务端重新校验原筛选、当前数据与组织权限。 */
final class LogUiSelections {
    /** 工具类不提供实例，使用静态方法完成本类职责。 */
    private LogUiSelections() { }
    /** 按固定日志类别提取已加载页的唯一事件标识，拒绝不完整、重复或超量选择。 */
    static String[] loadedIds(LogKind kind, List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) { throw new IllegalArgumentException("没有已加载记录可导出。"); }
        String key = kind == LogKind.AUDIT ? "audit_id" : "event_id";
        String[] result = new String[rows.size()];
        Set<String> seen = new HashSet<String>();
        for (int i = 0; i < rows.size(); i++) {
            Object value = rows.get(i).get(key);
            if (value == null || value.toString().length() == 0 || !seen.add(value.toString())) {
                throw new IllegalArgumentException("已加载页包含缺失或重复的记录编号，请重新查询。");
            }
            result[i] = value.toString();
        }
        return result;
    }
}
