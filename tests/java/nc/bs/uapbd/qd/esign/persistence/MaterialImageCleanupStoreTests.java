package nc.bs.uapbd.qd.esign.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import nc.bs.dao.DAOException;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.bs.uapbd.qd.esign.core.MaterialImageFolder;
import nc.jdbc.framework.SQLParameter;
import nc.vo.pub.BusinessException;

/** Verifies the actual SQL argument lists and the task-claim compare-and-set without a database. */
public final class MaterialImageCleanupStoreTests {
    private MaterialImageCleanupStoreTests() { }

    private static final class RecordingDao implements MaterialImageCleanupStore.Database {
        String sql;
        int parameters;
        int updateResult=1;
        boolean mismatch;
        boolean newFileRevision;
        boolean hasManifest=true;
        final List<String> statements=new ArrayList<String>();
        final List<Integer> counts=new ArrayList<Integer>();
        public List<Map<String,Object>> query(String statement,SQLParameter args) throws DAOException {
            if(statement.contains("FROM sm_pub_filesystem")) {
                List<Map<String,Object>> rows=new ArrayList<Map<String,Object>>();
                rows.add(EsignValues.row("pk",mismatch?"OTHER":"NODE1","filepath",MaterialImageFolder.folderPath("PK1"),
                    "isfolder","Y","isdoc","N","modifytime","123"));
                rows.add(EsignValues.row("pk","FILE1","filepath",MaterialImageFolder.folderPath("PK1")+"/file.png",
                    "isfolder","N","isdoc","Y","pk_doc",newFileRevision?"STORAGE2":"STORAGE1","modifytime","456"));
                return rows;
            }
            if(statement.contains("FROM qd_es_img_cleanup_item") && hasManifest) {
                List<Map<String,Object>> rows=new ArrayList<Map<String,Object>>();
                rows.add(EsignValues.row("pk","NODE1","filepath",MaterialImageFolder.folderPath("PK1"),
                    "isfolder","Y","isdoc","N","modifytime","123"));
                rows.add(EsignValues.row("pk","FILE1","filepath",MaterialImageFolder.folderPath("PK1")+"/file.png",
                    "isfolder","N","isdoc","Y","pk_doc","STORAGE1","modifytime","456"));
                return rows;
            }
            return new ArrayList<Map<String,Object>>();
        }
        public int update(String statement,SQLParameter args) throws DAOException {
            sql=statement;parameters=args.getCountParams();statements.add(sql);counts.add(Integer.valueOf(parameters));return updateResult;
        }
    }

    public static void run() throws Exception {
        RecordingDao dao=new RecordingDao();
        MaterialImageCleanupStore store=new MaterialImageCleanupStore(dao);
        store.enqueue("R0000000000000000000000000000001","G1","BD_MATERIAL","PK1",
            MaterialImageFolder.recorded(MaterialImageFolder.folderPath("PK1"),"NODE1",123L));
        require(dao.statements.get(0).contains("created_at") && dao.counts.get(0).intValue()==10
            && placeholders(dao.statements.get(0))==10,
            "Cleanup task INSERT must bind all ten values");
        require(dao.statements.size()==3 && dao.statements.get(1).contains("qd_es_img_cleanup_item")
            && dao.counts.get(1).intValue()==9 && dao.counts.get(2).intValue()==9,
            "Directory and file identities must be recorded in the business transaction");
        require("A\\_B\\%C\\\\D".equals(MaterialImageCleanupStore.escapeLike("A_B%C\\D")),"LIKE escaping must be exact");
        dao.mismatch=true;
        try { store.enqueue("R0000000000000000000000000000002","G1","BD_MATERIAL","PK1",
                MaterialImageFolder.recorded(MaterialImageFolder.folderPath("PK1"),"NODE1",123L));
            throw new AssertionError("Changed directory identity accepted"); }
        catch(BusinessException required) { }
        dao.mismatch=false;
        MaterialImageFolder recorded=MaterialImageFolder.recorded(MaterialImageFolder.folderPath("PK1"),"NODE1",123L);
        require(store.compareManifest("TASK1",recorded)==MaterialImageCleanupStore.ManifestStatus.MATCHED,
            "All unchanged file nodes should match the recorded manifest");
        dao.newFileRevision=true;
        require(store.compareManifest("TASK1",recorded)==MaterialImageCleanupStore.ManifestStatus.CHANGED,
            "A replaced child file must be identified even when the directory version is unchanged");
        dao.hasManifest=false;
        require(store.compareManifest("TASK1",recorded)==MaterialImageCleanupStore.ManifestStatus.MISSING,
            "Legacy tasks without evidence must report a missing manifest");
        store.enqueueUnknown("R0000000000000000000000000000003","G1","PK1");
        require(dao.sql.contains("finished_at") && dao.parameters==10 && placeholders(dao.sql)==10,
            "An uncertain file probe must retain a review task with complete bindings");
        require(store.claim("G1","TASK1","TOKEN1","2026-12-01 00:00:00"),"First claim must succeed");
        require(dao.sql.contains("status='PENDING'") && dao.sql.contains("status='RUNNING'")
            && placeholders(dao.sql)==dao.parameters,"Claim must atomically transition only pending rows");
        dao.updateResult=0;
        require(!store.claim("G1","TASK1","TOKEN2","2026-12-01 00:00:00"),"Competing claim must lose");
        dao.updateResult=1;
        require(store.finish("TASK1","TOKEN1","NEEDS_REVIEW","unverified"),"Active lease must finish");
        require(dao.sql.contains("status='RUNNING'") && dao.sql.contains("lease_token=?")
            && placeholders(dao.sql)==dao.parameters,"Finishing must require the active lease");
        store.finish("TASK1","TOKEN1","CLEANED",null);
        require(placeholders(dao.sql)==dao.parameters,"Null cleanup result messages must be bound explicitly");
        require(store.retry("TASK1","TOKEN1",1,"retry","2026-12-01 00:00:00"),"Active lease must retry");
        require(dao.sql.contains("status='PENDING'") && dao.sql.contains("lease_token=?")
            && placeholders(dao.sql)==dao.parameters,"Retry must be scoped to the active lease");
        dao.updateResult=0;
        require(!store.finish("TASK1","STALE","NEEDS_REVIEW","expired"),"A lost lease must not stop the batch or overwrite another state");
        require(!store.retry("TASK1","STALE",1,"expired","2026-12-01 00:00:00"),"A lost lease must not requeue another worker's task");
        System.out.println("PASS MaterialImageCleanupStoreTests (SQL bindings and atomic task claims; no Oracle)");
    }

    private static int placeholders(String sql) {
        int count=0;for(int i=0;i<sql.length();i++) if(sql.charAt(i)=='?') count++;return count;
    }
    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
}
