package nc.vo.uapbd.qd.esign.audit;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A table row's snapshot values, returned as field previews or one requested value segment. */
public final class SnapshotContent implements Serializable {
    private static final long serialVersionUID = 1L;
    public static final int FIELD_PREVIEW = 512;
    public static final int VALUE_PAGE_SIZE = 16384;
    private final boolean physicalColumns;
    private final List<Field> fields;

    SnapshotContent(boolean physicalColumns, List<Field> fields) {
        this.physicalColumns = physicalColumns;
        this.fields = Collections.unmodifiableList(new ArrayList<Field>(fields));
    }

    public boolean hasPhysicalColumns() { return physicalColumns; }
    public List<Field> getFields() { return fields; }

    public static final class Field implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String code, type, label, value;
        private final long offset, length;

        Field(String code, String type, String label, String value, long offset, long length) {
            this.code = code;
            this.type = type;
            this.label = label;
            this.value = value;
            this.offset = offset;
            this.length = length;
        }

        public String getCode() { return code; }
        public String getType() { return type; }
        public String getLabel() { return label; }
        public String getValue() { return value; }
        public long getOffset() { return offset; }
        public long getLength() { return length; }
        public boolean hasMore() { return value != null && length - offset > value.length(); }
    }
}
