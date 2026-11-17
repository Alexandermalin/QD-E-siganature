package nc.impl.uapbd.qd.esign;

import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.bs.uapbd.qd.esign.core.MaterialImageFolder;
import nc.vo.pub.filesystem.NCFileVO;

/** Tests the post-commit cleanup decision, retry ceiling and backoff schedule; no NC or Oracle. */
public final class MaterialImageCleanupServiceTests {
    private MaterialImageCleanupServiceTests() { }

    public static void run() throws Exception {
        String path=MaterialImageFolder.folderPath("PK1");
        MaterialImageFolder recorded=MaterialImageFolder.recorded(path,"NODE1",1000L);

        // 物料行又出现：无论目录状态如何都不允许删除。
        require(MaterialImageCleanupServiceImpl.decide(true,node(path,"NODE1",1L),recorded)
            ==MaterialImageCleanupServiceImpl.Decision.MATERIAL_RESTORED,"Restored material must block cleanup");
        require(MaterialImageCleanupServiceImpl.decide(true,null,recorded)
            ==MaterialImageCleanupServiceImpl.Decision.MATERIAL_RESTORED,"Restored material must block even absent folder");

        // 目录已不存在：仍须核对物理文件，不能视为已清理。
        require(MaterialImageCleanupServiceImpl.decide(false,null,recorded)
            ==MaterialImageCleanupServiceImpl.Decision.FOLDER_MISSING,"Absent folder must remain unverified");

        // 目录仍是登记节点也不足以证明文件版本安全，不能自动递归删除。
        require(MaterialImageCleanupServiceImpl.decide(false,node(path,"NODE1",1000L),recorded)
            ==MaterialImageCleanupServiceImpl.Decision.REVIEW_UNVERIFIED,"A folder identity alone must never authorize deletion");
        require(MaterialImageCleanupServiceImpl.decide(false,node(path,"NODE1",999L),recorded)
            ==MaterialImageCleanupServiceImpl.Decision.IDENTITY_CHANGED,"Changed version must be held for review");
        require(MaterialImageCleanupServiceImpl.decide(false,node(path,"NODE2",999L),recorded)
            ==MaterialImageCleanupServiceImpl.Decision.IDENTITY_CHANGED,"Rebuilt folder must be held for review");
        require(MaterialImageCleanupServiceImpl.decide(false,node(path+"/child","NODE1",1L),recorded)
            ==MaterialImageCleanupServiceImpl.Decision.IDENTITY_CHANGED,"Drifted path must be held for review");

        // 重试上限：达到上限前继续退避，最后一次重试间隔为 9 小时，之后转待核对。
        require(MaterialImageCleanupServiceImpl.MAX_ATTEMPTS==10,"Retry ceiling changed; review hold policy");
        long previous=0;
        for(int attempts=1;attempts<MaterialImageCleanupServiceImpl.MAX_ATTEMPTS;attempts++) {
            String next=MaterialImageCleanupServiceImpl.nextRetryUtc(attempts);
            long delay=EsignValues.parseUtc(next)-System.currentTimeMillis();
            long expected=Math.min(60L*attempts,1440L)*60000L;
            require(delay>0 && delay<=expected+120000L && delay>=expected-120000L,
                "Backoff outside the expected window at attempt "+attempts);
            previous=delay;
        }
        require(previous>=539*60000L && previous<=541*60000L,"Final retry must back off nine hours before review");

        System.out.println("PASS MaterialImageCleanupServiceTests (cleanup decision guards and backoff ceiling; no NC or Oracle)");
    }

    private static NCFileVO node(String fullPath,String pk,long modified) {
        NCFileVO vo=new NCFileVO();
        vo.setPath(fullPath);
        vo.setPk(pk);
        vo.setModifytime(modified);
        return vo;
    }

    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
}
