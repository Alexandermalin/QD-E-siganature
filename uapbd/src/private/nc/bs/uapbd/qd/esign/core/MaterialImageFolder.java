package nc.bs.uapbd.qd.esign.core;

import nc.itf.bd.imageSystem.IImageSystemService;
import nc.vo.pub.BusinessException;
import nc.vo.pub.filesystem.NCFileVO;

/**
 * 物料图片目录在业务事务内的固定身份。
 * 原生 ImageSystemProxyService 以 "uapbd/IMG/&lt;物料元数据类&gt;/&lt;物料主键&gt;" 为目录，
 * GUID 与 BDModeManager 使用的物料元数据类一致；该常量来自本机反编译证据，字段变更须重新核对。
 */
public final class MaterialImageFolder {
    /** 原生物料图片目录前缀，来源见类注释；不得拼接父级以外的任何路径。 */
    public static final String MATERIAL_IMAGE_ROOT = "uapbd/IMG/c7dc0ccd-8872-4eee-8882-160e8f49dfad/";

    /** 目录完整路径，与提交后清理使用的删除范围逐字一致。 */
    private final String fullPath;
    /** 目录节点主键，用于防止同路径目录被重建后误删。 */
    private final String nodePk;
    /** 目录节点修改时间毫秒，作为版本证据保存。 */
    private final long modifiedAt;

    private MaterialImageFolder(String fullPath, String nodePk, long modifiedAt) {
        this.fullPath = fullPath;
        this.nodePk = nodePk;
        this.modifiedAt = modifiedAt;
    }

    /** 从任务行恢复登记时的目录身份，供提交后清理核对；参数完整性由调用方保证。 */
    public static MaterialImageFolder recorded(String fullPath, String nodePk, long modifiedAt) {
        if (fullPath == null || nodePk == null) { throw new IllegalArgumentException("Recorded folder identity is required"); }
        return new MaterialImageFolder(fullPath, nodePk, modifiedAt);
    }

    /** 物料主键对应的原生图片目录路径。 */
    public static String folderPath(String materialPk) {
        if(materialPk==null || !materialPk.matches("[A-Za-z0-9_-]{1,20}"))
            throw new IllegalArgumentException("Invalid material image identity");
        return MATERIAL_IMAGE_ROOT + materialPk;
    }

    /**
     * 在业务事务内读取目录当前身份；目录不存在返回 null（无图片无需清理）。
     * 返回身份必须与预期路径逐字一致，原生目录约定变化时拒绝受控删除。
     */
    public static MaterialImageFolder capture(IImageSystemService service, String materialPk) throws BusinessException {
        String expected = folderPath(materialPk);
        NCFileVO current = service.queryFileVOByPkFilePath(expected);
        if (current == null) { return null; }
        String path = current.getFullPath();
        String pk = current.getPk();
        if (path == null || !path.equals(expected) || pk == null || pk.trim().isEmpty()) {
            throw new BusinessException("物料图片目录路径或身份与原生约定不一致，不能登记清理。");
        }
        return new MaterialImageFolder(path, pk, current.getModifytime());
    }

    public String getFullPath() { return fullPath; }
    public String getNodePk() { return nodePk; }
    public long getModifiedAt() { return modifiedAt; }

    /** 判断清理时读到的目录仍是业务事务登记的同一节点，防止删除重建后的新目录。 */
    public boolean isSameNode(NCFileVO current) {
        return current != null && current.getFullPath() != null
            && current.getFullPath().equals(fullPath) && current.getPk() != null
            && current.getPk().equals(nodePk) && current.getModifytime()==modifiedAt;
    }
}
