package nc.bs.uapbd.qd.esign.persistence;

/** 原生日志全主键循环对账的纯分页规则；每轮扫描到尾后重置起点，下一轮再覆盖之前已越过的迟提交记录。 */
public final class NativeReconciliationRules {
    /** START_KEY：完整扫描周期的起始主键哨兵值。 */
    public static final String START_KEY="!";
    /** 工具类不提供实例，使用静态方法完成本类职责。 */
    private NativeReconciliationRules() { }
    /** 本批数量小于批量上限时认为已扫描到尾；恰好满批仍需下一次空批确认。 */
    public static boolean completesCycle(int selectedCount,int batchSize) {
        if(batchSize<1||batchSize>900||selectedCount<0||selectedCount>batchSize) {
            throw new IllegalArgumentException("Invalid native reconciliation page size");
        }
        return selectedCount<batchSize;
    }
    /** 已到尾时返回周期起点，否则返回本批最后主键，供下批继续扫描。 */
    public static String nextPrimaryKey(String lastSelectedKey,int selectedCount,int batchSize) {
        if(completesCycle(selectedCount,batchSize)) { return START_KEY; }
        if(lastSelectedKey==null||lastSelectedKey.length()==0) { throw new IllegalArgumentException("Full page needs its last primary key"); }
        return lastSelectedKey;
    }
}
