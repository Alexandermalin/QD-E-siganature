package nc.bs.uapbd.qd.esign.core;

import java.util.HashMap;
import nc.bs.pub.filesystem.FSOption;
import nc.itf.bd.imageSystem.IImageSystemService;
import nc.vo.pub.BusinessException;
import nc.vo.pub.filesystem.NCFileNode;
import nc.vo.pub.filesystem.NCFileVO;

/** Tests the fixed material image folder identity and the same-node guard used by post-commit cleanup. */
public final class MaterialImageFolderTests {
    private MaterialImageFolderTests() { }

    /** 只返回固定目录身份的最小服务替身；不连接文件存储。 */
    private static final class FixedFolderService implements IImageSystemService {
        private final NCFileVO folder;
        FixedFolderService(NCFileVO folder) { this.folder = folder; }
        public NCFileVO queryFileVOByPkFilePath(String path) { return folder; }
        public boolean isFileExist(String path) { return false; }
        public NCFileVO[] queryFileVOsByPks(String[] pks) { return new NCFileVO[0]; }
        public void deleteNCFileVOByPath(String path) { }
        public void clearTempPicture(String path) { }
        public void insertImages(NCFileVO[] vos, String s) { }
        public void creatSmallPic(HashMap<String,String> map) { }
        public void deleteImages(NCFileVO[] vos) { }
        public HashMap<String,NCFileVO> synchronizedImages(String a, String b) { return new HashMap<String,NCFileVO>(); }
        public void editImages(HashMap<String,NCFileVO> map, String s) { }
        public String[] queryDeleteFileVOsBypk(java.util.List<String> pks) { return new String[0]; }
        public void setDefShowImage(String a, String[] b) { }
        public NCFileNode createNewFileNodeWithStream(String a, String b, String c, java.io.ObjectInputStream in, long len) { return null; }
        public void updateTruePathByNewPK(String a, String b) { }
        public void copyTo(String a, String b, FSOption o, boolean flag) { }
    }

    public static void run() throws Exception {
        String folderPath = MaterialImageFolder.folderPath("PK1");
        require(folderPath.equals(MaterialImageFolder.MATERIAL_IMAGE_ROOT + "PK1"),
            "Folder path must follow the native material image convention");

        MaterialImageFolder captured = MaterialImageFolder.capture(
            new FixedFolderService(file(folderPath, "NODE1", 1000L)), "PK1");
        require(captured != null && "NODE1".equals(captured.getNodePk())
            && captured.getModifiedAt() == 1000L && folderPath.equals(captured.getFullPath()),
            "Folder identity was not captured from the native service");

        // 目录不存在：无图片，无需清理。
        require(MaterialImageFolder.capture(new FixedFolderService(null), "PK1") == null,
            "Absent folder must not produce a task");

        // 路径或身份与原生约定不一致：拒绝登记，不得带病删除。
        try {
            MaterialImageFolder.capture(new FixedFolderService(file(folderPath + "/child", "NODE1", 1L)), "PK1");
            throw new AssertionError("Alien folder path accepted");
        } catch (BusinessException expected) { }
        try {
            MaterialImageFolder.capture(new FixedFolderService(file(folderPath, " ", 1L)), "PK1");
            throw new AssertionError("Folder without node identity accepted");
        } catch (BusinessException expected) { }

        // 同路径不同主键：目录被重建，清理必须保留待核对而不是删除新目录。
        require(!captured.isSameNode(file(folderPath, "NODE2", 2000L)),
            "A rebuilt folder node must not be treated as the recorded node");
        require(!captured.isSameNode(file(folderPath, "NODE1", 999L)),
            "A changed folder version must be held for review");
        require(captured.isSameNode(file(folderPath, "NODE1", 1000L)),
            "An unchanged folder identity and version must match");
        require(!captured.isSameNode(null), "Missing current node cannot confirm identity");

        System.out.println("PASS MaterialImageFolderTests (fixed folder identity, drift rejection, rebuild guard; no NC or Oracle)");
    }

    private static NCFileVO file(String fullPath, String pk, long modified) {
        NCFileVO vo = new NCFileVO();
        vo.setPath(fullPath);
        vo.setPk(pk);
        vo.setModifytime(modified);
        return vo;
    }

    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
