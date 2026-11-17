package nc.vo.uapbd.qd.esign.log;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.List;
import java.util.Map;

/** CSV 序列化工具：输出 UTF-8 BOM，按 RFC4180 处理引号和换行，并中和可能被电子表格识别为公式的单元格。 */
public final class CsvLogWriter {
    /** 工具类不提供实例，使用静态方法完成本类职责。 */
    private CsvLogWriter() { }
    /**
     * 校验列名、标题及行数限制，输出 UTF-8 BOM、标题和逐行转义内容；超行数抛出 IOException，完成后刷新但不关闭调用方输出流。
     */
    public static int write(OutputStream output, List<String> columns, List<String> titles,
            Iterable<? extends Map<String,?>> rows, int maxRows) throws IOException {
        if (output == null || columns == null || titles == null || rows == null
                || columns.isEmpty() || columns.size() != titles.size() || maxRows < 1) {
            throw new IllegalArgumentException("Invalid CSV arguments");
        }
        Writer writer = new OutputStreamWriter(output, "UTF-8");
        writer.write('\uFEFF');
        for (int i = 0; i < titles.size(); i++) { if (i > 0) { writer.write(','); } cell(writer, titles.get(i)); }
        writer.write("\r\n");
        int count = 0;
        for (Map<String,?> row : rows) {
            if (count >= maxRows) { throw new IOException("CSV row limit exceeded; export was not completed"); }
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) { writer.write(','); }
                Object value = row.get(columns.get(i)); cell(writer, value == null ? "" : String.valueOf(value));
            }
            writer.write("\r\n"); count++;
        }
        writer.flush(); return count;
    }
    /** 对单个 CSV 值加引号并转义内部引号；识别前导空白后的公式前缀并添加安全前缀。 */
    private static void cell(Writer writer, String input) throws IOException {
        String value = input == null ? "" : input;
        int first = 0;
        while (first < value.length() && Character.isWhitespace(value.charAt(first))) { first++; }
        boolean formula = first < value.length() && "=+-@".indexOf(value.charAt(first)) >= 0;
        writer.write('"'); if (formula) { writer.write('\''); }
        for (int i = 0; i < value.length(); i++) { char c = value.charAt(i); if (c == '"') { writer.write('"'); } writer.write(c); }
        writer.write('"');
    }
}
