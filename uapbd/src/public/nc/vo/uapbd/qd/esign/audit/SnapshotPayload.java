package nc.vo.uapbd.qd.esign.audit;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Stable snapshot JSON. Version 2 records physical fields with database types and labels. */
public final class SnapshotPayload {
    private SnapshotPayload() { }

    public static String encode(SnapshotRow row) {
        boolean physical = row.hasPhysicalColumns();
        StringBuilder output = new StringBuilder(physical ? "{\"version\":2,\"fields\":{" : "{");
        boolean first = true;
        for (Map.Entry<String, String> field : row.getValues().entrySet()) {
            if (!first) { output.append(','); }
            first = false;
            quote(output, field.getKey(), physical);
            output.append(':');
            if (physical) {
                output.append("{\"type\":");
                nullable(output, row.getTypes().get(field.getKey()), true);
                output.append(",\"label\":");
                nullable(output, row.getLabels().get(field.getKey()), true);
                output.append(",\"value\":");
            }
            nullable(output, field.getValue(), physical);
            if (physical) { output.append('}'); }
        }
        return output.append(physical ? "}}" : "}").toString();
    }

    private static void nullable(StringBuilder output, String value, boolean ascii) {
        if (value == null) { output.append("null"); }
        else { quote(output, value, ascii); }
    }

    private static void quote(StringBuilder output, String value, boolean ascii) {
        output.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\') { output.append('\\').append(c); }
            else if (c < 32 || (ascii && c > 126)) {
                output.append("\\u");
                for (int shift = 12; shift >= 0; shift -= 4) { output.append(Character.forDigit((c >> shift) & 15, 16)); }
            } else { output.append(c); }
        }
        output.append('"');
    }

    /** Decode full field lengths from a stream but materialize only the requested portions of their values. */
    public static SnapshotContent read(Reader input, String field, long offset, int limit) throws IOException {
        if (input == null || offset < 0 || limit < 1 || (field == null && offset != 0)) {
            throw new IllegalArgumentException("Invalid snapshot read range");
        }
        return new Parser(new BufferedReader(input), field, offset, limit).read();
    }

    private static final class Parser {
        private final Reader input;
        private final String selected;
        private final long offset;
        private final int limit;
        private final List<SnapshotContent.Field> fields = new ArrayList<SnapshotContent.Field>();
        private final Set<String> seen = new HashSet<String>();
        private int next = -2;

        Parser(Reader input, String selected, long offset, int limit) {
            this.input = input;
            this.selected = selected;
            this.offset = offset;
            this.limit = limit;
        }

        SnapshotContent read() throws IOException {
            expect('{');
            boolean physical = false;
            if (!take('}')) {
                String first = key();
                if ("version".equals(first) && peek() == '2') {
                    physical = true;
                    expect('2'); expect(',');
                    if (!"fields".equals(key())) { throw invalid(); }
                    expect('{');
                    if (!take('}')) { object(key(), true); }
                    expect('}');
                } else {
                    object(first, false);
                }
            }
            skipWhitespace();
            if (peek() != -1) { throw invalid(); }
            if (selected != null && fields.isEmpty()) { throw new IOException("Snapshot field does not exist"); }
            return new SnapshotContent(physical, fields);
        }

        private void object(String code, boolean physical) throws IOException {
            while (true) {
                if (seen.size() >= 4096 || !seen.add(code)) { throw invalid(); }
                if (physical && take('{')) {
                    if (!"type".equals(key())) { throw invalid(); }
                    String type = metadata();
                    expect(',');
                    if (!"label".equals(key())) { throw invalid(); }
                    String label = metadata();
                    expect(',');
                    if (!"value".equals(key())) { throw invalid(); }
                    field(code, type, label);
                    expect('}');
                } else {
                    field(code, null, null);
                }
                if (take('}')) { return; }
                expect(',');
                code = key();
            }
        }

        private void field(String code, String type, String label) throws IOException {
            boolean requested = selected == null || selected.equals(code);
            if (peek() == 'n') {
                literal("null");
                if (requested) { add(code, type, label, null, 0); }
            } else {
                StringBuilder value = requested ? new StringBuilder(Math.min(limit, 4096)) : null;
                long length = string(value, offset, limit);
                if (requested) { add(code, type, label, value.toString(), length); }
            }
        }

        private String metadata() throws IOException {
            if (peek() == 'n') { literal("null"); return null; }
            StringBuilder value = new StringBuilder();
            if (string(value, 0, 200) > 200) { throw invalid(); }
            return value.toString();
        }

        private void add(String code, String type, String label, String value, long length) throws IOException {
            if (offset > length) { throw new IOException("Snapshot value offset exceeds the field length"); }
            fields.add(new SnapshotContent.Field(code, type, label, value, offset, length));
        }

        private String key() throws IOException {
            StringBuilder code = new StringBuilder();
            long length = string(code, 0, 200);
            if (length > 200) { throw invalid(); }
            expect(':');
            skipWhitespace();
            return code.toString();
        }

        private long string(StringBuilder value, long start, int size) throws IOException {
            expect('"');
            long length = 0;
            int c;
            while ((c = readChar()) != '"') {
                if (c < 32) { throw invalid(); }
                if (c == '\\') { c = escape(); }
                if (value != null && length >= start && length - start < size) { value.append((char)c); }
                length++;
            }
            return length;
        }

        private int escape() throws IOException {
            int c = readChar();
            switch (c) {
                case '"': case '\\': case '/': return c;
                case 'b': return '\b';
                case 'f': return '\f';
                case 'n': return '\n';
                case 'r': return '\r';
                case 't': return '\t';
                case 'u':
                    int value = 0;
                    for (int i = 0; i < 4; i++) {
                        int digit = Character.digit(readChar(), 16);
                        if (digit < 0) { throw invalid(); }
                        value = value * 16 + digit;
                    }
                    return value;
                default: throw invalid();
            }
        }

        private void literal(String value) throws IOException {
            for (int i = 0; i < value.length(); i++) { if (readChar() != value.charAt(i)) { throw invalid(); } }
        }

        private int peek() throws IOException {
            if (next == -2) { next = input.read(); }
            return next;
        }

        private int readChar() throws IOException { int c = peek(); next = -2; return c; }

        private void skipWhitespace() throws IOException {
            int c;
            while ((c = peek()) == ' ' || c == '\t' || c == '\r' || c == '\n') { readChar(); }
        }

        private boolean take(int c) throws IOException {
            skipWhitespace();
            if (peek() != c) { return false; }
            readChar();
            return true;
        }

        private void expect(int c) throws IOException { if (!take(c)) { throw invalid(); } }
        private IOException invalid() { return new IOException("Invalid or incomplete audit snapshot JSON"); }
    }
}
