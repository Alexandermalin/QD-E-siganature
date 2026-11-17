package nc.vo.uapbd.qd.esign.log;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 一次日志查询的只读分页结果，记录当前页数据与符合筛选条件的总数；集合通过复制隔离外部结构修改。 */
public final class LogPage implements Serializable {
    /** serialVersionUID：Java 序列化版本号，保持跨 RPC 或 Swing 组件序列化的兼容标识。 */
    private static final long serialVersionUID = 1L;
    /** rows：当前结果行集合。 */
    private final List<Map<String,Object>> rows;
    /** total：符合当前筛选条件的总记录数。 */
    private final long total;
    /** 复制当前页记录及各行映射并保存总数，避免调用方更改容器影响分页结果。 */
    public LogPage(List<Map<String,Object>> rows,long total) {
        List<Map<String,Object>> copied = new ArrayList<Map<String,Object>>();
        for(Map<String,Object> row:rows) { copied.add(Collections.unmodifiableMap(new LinkedHashMap<String,Object>(row))); }
        this.rows=Collections.unmodifiableList(copied); this.total=total;
    }
    /** 返回当前结果行集合。 */
    public List<Map<String,Object>> getRows() { return rows; }
    /** 返回符合当前筛选条件的总记录数。 */
    public long getTotal() { return total; }
}
