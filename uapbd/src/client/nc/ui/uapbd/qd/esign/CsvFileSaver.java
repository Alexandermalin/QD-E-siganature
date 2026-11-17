package nc.ui.uapbd.qd.esign;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** 只向用户明确选定的 CSV 目标写入，先写临时文件，再在支持时原子发布；覆盖既有文件前核对用户确认时的文件状态。 */
final class CsvFileSaver {
    /** 工具类不提供实例，使用静态方法完成本类职责。 */
    private CsvFileSaver() { }
    /** 把已取得的 CSV 字节写到目标目录临时文件；覆盖前核对既有目标的长度和修改时间，随后以原子或受控回退方式发布。 */
    static void save(File destination, byte[] bytes, boolean replaceExisting,
            long expectedLength, long expectedModified) throws IOException {
        Path target = destination.toPath().toAbsolutePath();
        if (replaceExisting && (!Files.exists(target) || Files.size(target) != expectedLength
                || Files.getLastModifiedTime(target).toMillis() != expectedModified)) {
            throw new IOException("目标文件已发生变化，请重新选择导出文件。");
        }
        if (!replaceExisting && Files.exists(target)) {
            throw new IOException("目标文件已存在，请重新选择文件或确认覆盖。");
        }
        Path temporary = Files.createTempFile(target.getParent(), ".qd-esign-", ".csv.part");
        try {
            Files.write(temporary, bytes);
            try {
                if (replaceExisting) { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                else { Files.move(temporary, target); }
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
