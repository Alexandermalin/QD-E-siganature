package nc.ui.uapbd.qd.esign;

import java.io.IOException;
import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.Map;
import nc.vo.uapbd.qd.esign.audit.SnapshotContent;
import nc.vo.uapbd.qd.esign.audit.SnapshotFieldLabels;
import nc.vo.uapbd.qd.esign.audit.SnapshotPayload;

/** 审计界面字段名称及既有平面快照格式的只读解析。 */
final class AuditUiData {
    private AuditUiData() { }

    static String fieldName(String entity, String code, String recorded) {
        if (recorded != null && recorded.length() > 0 && !recorded.equals(code)) { return recorded; }
        String known = SnapshotFieldLabels.known(entity, code);
        if (known != null) { return known; }
        return code == null ? "" : code;
    }

    static boolean truncated(Map<String, Object> row, String field) {
        Object value = row.get(field + "_truncated");
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value)) || "Y".equals(value);
    }

    static String displayValue(Object value) {
        if (value == null) { return "（空值）"; }
        String text = String.valueOf(value);
        return text.length() == 0 ? "（空字符串）" : text;
    }

    static Map<String, String> snapshot(Map<String, Object> row) {
        Map<String, String> values = new LinkedHashMap<String, String>();
        for (SnapshotContent.Field field : content(row, 100000).getFields()) {
            if (field.hasMore()) { throw new IllegalArgumentException("快照超过预览长度。"); }
            values.put(field.getCode(), field.getValue());
        }
        return values;
    }

    /** 完整的短载荷直接解码，长载荷由授权服务按字段读取。 */
    static SnapshotContent content(Map<String, Object> row, int limit) {
        if (truncated(row, "payload")) { throw new IllegalArgumentException("快照超过预览长度，无法完整按字段展示。"); }
        Object payload = row.get("payload");
        if (!(payload instanceof String)) { throw new IllegalArgumentException("快照内容为空或格式无效。"); }
        try {
            return SnapshotPayload.read(new StringReader((String) payload), null, 0, limit);
        } catch (IOException failure) {
            throw new IllegalArgumentException("快照格式不完整或无效，无法按字段展示。", failure);
        }
    }
}
