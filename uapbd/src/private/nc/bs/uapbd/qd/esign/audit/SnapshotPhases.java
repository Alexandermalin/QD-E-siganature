package nc.bs.uapbd.qd.esign.audit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;

/** Determine physical row phases when cancelling an assignment removes only part of an archive. */
public final class SnapshotPhases {
    private SnapshotPhases() { }
    public static final class Plan {
        private final List<SnapshotRow> before,deleted;
        private Plan(List<SnapshotRow> before,List<SnapshotRow> deleted) {
            this.before=Collections.unmodifiableList(before);this.deleted=Collections.unmodifiableList(deleted);
        }
        public List<SnapshotRow> getBefore() { return before; }
        public List<SnapshotRow> getDeleted() { return deleted; }
    }
    public static Plan forOperation(String operation,List<SnapshotRow> before,List<SnapshotRow> after) {
        if(before==null || after==null) throw new IllegalArgumentException("Snapshot lists are required");
        Set<String> remaining=new HashSet<String>();
        for(SnapshotRow row:after) if(row==null || !remaining.add(identity(row)))
            throw new IllegalArgumentException("Duplicate or missing post-operation snapshot identity");
        Set<String> previous=new HashSet<String>();List<SnapshotRow> kept=new ArrayList<SnapshotRow>(),deleted=new ArrayList<SnapshotRow>();
        for(SnapshotRow row:before) {
            if(row==null || !previous.add(identity(row))) throw new IllegalArgumentException("Duplicate or missing pre-operation snapshot identity");
            if(("DELETE".equals(operation) || "CANCEL_ASSIGN".equals(operation)) && !remaining.contains(identity(row))) deleted.add(row);
            else kept.add(row);
        }
        return new Plan(kept,deleted);
    }
    private static String identity(SnapshotRow row) { return row.getEntity()+"\u0000"+row.getRowId(); }
}
