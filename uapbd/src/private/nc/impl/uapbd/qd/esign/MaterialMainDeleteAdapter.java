package nc.impl.uapbd.qd.esign;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import nc.bs.dao.BaseDAO;
import nc.bs.framework.common.NCLocator;
import nc.bs.pub.filesystem.IFileSystemService;
import nc.bs.uapbd.qd.esign.audit.AuditCaptureScope;
import nc.bs.uapbd.qd.esign.audit.PhysicalSnapshotReader;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.bs.uapbd.qd.esign.core.MaterialImageFolder;
import nc.bs.uapbd.qd.esign.persistence.MaterialImageCleanupStore;
import nc.itf.bd.imageSystem.IImageSystemService;
import nc.itf.bd.material.baseinfo.IMaterialBaseInfoService;
import nc.itf.uapbd.qd.esign.IMaterialMainDeleteAdapter;
import nc.vo.bd.material.MaterialVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.filesystem.NCFileVO;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.MaterialCommand;
import nc.vo.uapbd.qd.esign.MaterialMainDeleteResult;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;
import nc.vo.util.BDPKLockUtil;

/**
 * 物料主档删除的受控适配器。
 * 原生 deleteMaterialWithExtendVO 在业务事务内删除图片目录，文件删除不受数据库回滚保护；
 * 本适配器只调用基础 deleteMaterial，事务内登记图片目录身份随业务与审计一起提交，
 * 后台任务当前只核对并转待核对，不自动删除文件；审计失败回滚时图片从未被删除。
 * 扩展对象走 deleteMaterialWithExtendVO 的持久化路径，现场扩展配置未核实前不接入。
 */
public final class MaterialMainDeleteAdapter implements IMaterialMainDeleteAdapter {
    /** 删除影响的固定物理表与各自主键列；组织页签表集合与已核实的标准平面页签一致。 */
    private static final String[][] CHILD_TABLES = {
        {"bd_materialconvert", "pk_materialconvert"},
        {"bd_materialtaxtype", "pk_materialtaxtype"},
        {"bd_marorg", "pk_marorg"},
        {"bd_materialfi", "pk_materialfi"},
        {"bd_materialpfc", "pk_materialpfc"},
        {"bd_materialpfcc", "pk_mateprofcost"},
        {"bd_materialpu", "pk_materialpu"},
        {"bd_materialsale", "pk_materialsale"},
        {"bd_materialstock", "pk_materialstock"},
        {"bd_materialplan", "pk_materialplan"},
        {"bd_materialprod", "pk_materialprod"},
        {"bd_materialcost", "pk_materialcost"},
    };
    /** 页签子行通过页签主键关联，而非统一假定物料主键是父行。 */
    private static final String[][] PAGE_CHILD_TABLES = {
        {"bd_materialsale", "bd_materialbindle", "pk_materialbindle", "pk_materialsale"},
        {"bd_materialstock", "bd_materialwarh", "pk_materialwarh", "pk_materialstock"},
        {"bd_materialplan", "bd_materialrepl", "pk_materialrepl", "pk_materialplan"},
        {"bd_materialcost", "bd_materialcostmod", "pk_materialcostmod", "pk_materialcost"},
        {"bd_materialpfcc", "bd_materialpfccsub", "pk_mateprofcost_b", "pk_mateprofcost"},
    };

    public void validate(OperationContext context,Serializable payload) throws BusinessException { draft(context,payload); }

    static void prelock(OperationContext context,Serializable payload) throws BusinessException {
        MaterialVO requested=draft(context,payload);
        MaterialMainDeleteExtensionGuard.requireSafe(context.getNodeCode());
        MaterialMainDeleteRouteGuard.requireKnownRoutes();
        MaterialVO current=(MaterialVO)new BaseDAO().retrieveByPK(MaterialVO.class,requested.getPrimaryKey());
        BusinessObjectRef ref=context.getObjects().get(0);
        EsignValues.require(current!=null && context.getGroupId().equals(current.getPk_group())
            && ref.getPolicyOwnerOrg().equals(current.getPk_org()) && current.getPk_source()!=null,
            "物料或版本来源已变化，请重新发起删除。");
        // 原生删除先锁版本来源、再锁主档。用服务端来源保持相同顺序，避免反向锁顺序。
        BDPKLockUtil.lockString(new String[]{current.getPk_source()});
        BDPKLockUtil.lockSuperVO(new nc.vo.pub.SuperVO[]{current});
        MaterialVO locked=(MaterialVO)new BaseDAO().retrieveByPK(MaterialVO.class,requested.getPrimaryKey());
        EsignValues.require(locked!=null && current.getPk_source().equals(locked.getPk_source())
            && context.getGroupId().equals(locked.getPk_group()) && ref.getPolicyOwnerOrg().equals(locked.getPk_org()),
            "物料版本来源或所属组织在加锁期间变化，请重新发起删除。");
    }

    public Serializable execute(OperationContext context,Serializable payload) throws BusinessException {
        draft(context,payload);
        BusinessObjectRef ref=context.getObjects().get(0);
        AuditCaptureScope scope=AuditCaptureScope.current();
        EsignValues.require(scope!=null,"物料删除缺少受控事务上下文。");
        PhysicalSnapshotReader reader=new PhysicalSnapshotReader();
        String pk=ref.getObjectPk();

        // 锁定主档并采集删除前权威快照：主档、计量、税类、分配关系、组织页签与版本行。
        List<SnapshotRow> before=reader.readLocked("bd_material","pk_material",null,"pk_material",pk,
            "pk_group",context.getGroupId(),true);
        EsignValues.require(before.size()==1 && ref.getPolicyOwnerOrg().equals(before.get(0).getValues().get("pk_org")),
            "物料不存在或所属组织已变化。");
        EsignValues.require(ref.getBillCode()!=null && ref.getBillCode().equals(before.get(0).getValues().get("code")),
            "物料编码与授权时不一致。");
        String source=before.get(0).getValues().get("pk_source");
        EsignValues.require(source!=null,"物料缺少版本来源，删除范围无法核对。");
        // 原生删除会读取 VO 的版本来源和最新标记；不得使用客户端提交的字段。
        MaterialVO material=(MaterialVO)new BaseDAO().retrieveByPK(MaterialVO.class,pk);
        EsignValues.require(material!=null && pk.equals(material.getPrimaryKey())
            && context.getGroupId().equals(material.getPk_group())
            && ref.getPolicyOwnerOrg().equals(material.getPk_org()) && ref.getBillCode().equals(material.getCode())
            && source.equals(material.getPk_source()) && material.getLatest()!=null
            && material.getLatest().toString().equalsIgnoreCase(before.get(0).getValues().get("latest")),
            "物料版本或服务端主档已变化，请重新发起删除。");
        List<SnapshotRow> relatedVersions=reader.readLocked("bd_material","pk_material",null,"pk_source",source,false);
        for(SnapshotRow version:relatedVersions) {
            EsignValues.require(context.getGroupId().equals(version.getValues().get("pk_group"))
                && ref.getPolicyOwnerOrg().equals(version.getValues().get("pk_org")),
                "同源物料版本跨集团或所属组织，须先完整授权影响范围。");
            if(!pk.equals(version.getRowId())) before.add(version);
        }
        List<SnapshotRow> versions=reader.readLocked("bd_material_v","pk_source",null,"pk_source",source,false);
        for(SnapshotRow version:versions) EsignValues.require(context.getGroupId().equals(version.getValues().get("pk_group"))
            && ref.getPolicyOwnerOrg().equals(version.getValues().get("pk_org")),"物料版本行所属组织不一致。");
        before.addAll(versions);
        List<SnapshotRow> related=relatedRows(reader,pk,true);
        before.addAll(related);
        if(scope.isEnabledFor(ref.getClientObjectId())) scope.before(ref.getClientObjectId(),before);

        // 图片目录身份在业务事务内读取并随业务、审计同事务登记；目录不存在则无需清理。
        MaterialImageFolder folder=null;boolean probeUnknown=false;
        try {
            IImageSystemService images=NCLocator.getInstance().lookup(IImageSystemService.class);
            folder=MaterialImageFolder.capture(images,pk);
            if(folder==null && NCLocator.getInstance().lookup(IFileSystemService.class).isExist(MaterialImageFolder.folderPath(pk)))
                probeUnknown=true;
        } catch(BusinessException failure) { probeUnknown=true; }
        catch(RuntimeException failure) { probeUnknown=true; }

        // 先登记清理任务再执行删除：删除或审计随后失败时，任务随事务回滚，图片保持未删除。
        if(folder!=null) new MaterialImageCleanupStore().enqueue(context.getRequestId(),context.getGroupId(),"BD_MATERIAL",pk,folder);
        else if(probeUnknown) new MaterialImageCleanupStore().enqueueUnknown(context.getRequestId(),context.getGroupId(),pk);

        NCLocator.getInstance().lookup(IMaterialBaseInfoService.class).deleteMaterial(material);
        requireAllRemoved(reader,pk,source,related);
        if(scope.isEnabledFor(ref.getClientObjectId())) {
            List<SnapshotRow> after=reader.read("bd_material","pk_material",null,"pk_source",source,false);
            after.addAll(reader.read("bd_material_v","pk_source",null,"pk_source",source,false));
            if(!after.isEmpty()) scope.after(ref.getClientObjectId(),after);
        }
        return new MaterialMainDeleteResult(material,folder!=null || probeUnknown);
    }

    private static List<SnapshotRow> relatedRows(PhysicalSnapshotReader reader,String pk,boolean lock)
            throws BusinessException {
        List<SnapshotRow> rows=new ArrayList<SnapshotRow>();
        for(String[] table:CHILD_TABLES) {
            List<SnapshotRow> parents=lock?reader.readLocked(table[0],table[1],"pk_material","pk_material",pk,false)
                :reader.read(table[0],table[1],"pk_material","pk_material",pk,false);
            rows.addAll(parents);
            for(String[] child:PAGE_CHILD_TABLES) if(child[0].equals(table[0])) {
                for(SnapshotRow parent:parents) rows.addAll(lock
                    ?reader.readLocked(child[1],child[2],child[3],child[3],parent.getRowId(),false)
                    :reader.read(child[1],child[2],child[3],child[3],parent.getRowId(),false));
            }
        }
        return rows;
    }

    /** 删除后核对全部关联行确已消失；仍存在其他版本主档时允许保留同源的版本行。 */
    private static void requireAllRemoved(PhysicalSnapshotReader reader,String pk,String source,List<SnapshotRow> priorRelated) throws BusinessException {
        EsignValues.require(reader.read("bd_material","pk_material",null,"pk_material",pk,false).isEmpty(),
            "原生物料删除未删除主档行，业务必须回滚。");
        EsignValues.require(relatedRows(reader,pk,false).isEmpty(),"原生物料删除遗留组织页签或子行，业务必须回滚。");
        // The parent may have disappeared or been reparented; recheck old identities and old parent keys.
        final PhysicalSnapshotReader authoritative=reader;
        requireHistoricalRowsGone(priorRelated,new RelatedProbe() {
            public boolean exists(String table,String key,String match,String value) throws BusinessException {
                return !authoritative.read(table,key,null,match,value,false).isEmpty();
            }
        });
        List<SnapshotRow> others=reader.read("bd_material","pk_material","pk_source","pk_source",source,false);
        boolean otherVersion=false;
        for(SnapshotRow row:others) if(!pk.equals(row.getRowId())) otherVersion=true;
        if(!otherVersion) EsignValues.require(reader.read("bd_material_v","pk_source",null,"pk_source",source,false).isEmpty(),
            "原生物料删除未清除最终版本行，业务必须回滚。");
    }

    interface RelatedProbe {
        boolean exists(String table,String key,String match,String value) throws BusinessException;
    }

    /** Detect moved rows and children inserted under a parent that no longer exists. */
    static void requireHistoricalRowsGone(List<SnapshotRow> priorRelated,RelatedProbe probe) throws BusinessException {
        EsignValues.require(priorRelated!=null && probe!=null,"物料删除缺少关联行复核范围。");
        for(SnapshotRow prior:priorRelated) {
            EsignValues.require(prior!=null && prior.getRowId()!=null,"物料删除关联行缺少主键。");
            boolean known=false;
            for(String[] table:CHILD_TABLES) if(table[0].equals(prior.getEntity())) {
                known=true;
                EsignValues.require(!probe.exists(table[0],table[1],table[1],prior.getRowId()),
                    "原生物料删除遗留或转移了"+table[0]+"关联行，业务必须回滚。");
            }
            for(String[] child:PAGE_CHILD_TABLES) {
                if(child[0].equals(prior.getEntity()))
                    EsignValues.require(!probe.exists(child[1],child[2],child[3],prior.getRowId()),
                        "原生物料删除遗留"+child[1]+"子行，业务必须回滚。");
                if(child[1].equals(prior.getEntity())) {
                    known=true;
                    EsignValues.require(!probe.exists(child[1],child[2],child[2],prior.getRowId()),
                        "原生物料删除遗留或转移了"+child[1]+"子行，业务必须回滚。");
                }
            }
            EsignValues.require(known,"物料删除包含未核实的关联表。");
        }
    }

    public List<BusinessObjectRef> describeResult(OperationContext context,Serializable result) throws BusinessException {
        EsignValues.require(result instanceof MaterialMainDeleteResult
            && ((MaterialMainDeleteResult)result).getMaterial()!=null
            && context.getObjects().get(0).getObjectPk()!=null
            && context.getObjects().get(0).getObjectPk()
                .equals(((MaterialMainDeleteResult)result).getMaterial().getPrimaryKey()),
            "物料删除结果与授权对象不一致。");
        return Collections.singletonList(context.getObjects().get(0));
    }

    private static MaterialVO draft(OperationContext context,Serializable payload) throws BusinessException {
        EsignValues.require(context!=null && isRoute(context.getNodeCode(),context.getButtonCode(),context.getOperationType())
            && OperationContext.OWNER_POLICY_PROTOCOL.equals(context.getPolicyProtocol())
            && context.getGroupId()!=null && context.getOrgId()!=null && context.getObjects().size()==1
            && payload instanceof MaterialCommand,"物料删除载荷或入口不正确。");
        MaterialCommand command=(MaterialCommand)payload;
        EsignValues.require(!command.hasExtensions(),"物料删除不支持扩展对象载荷，现场扩展配置须先核实。");
        MaterialVO material=command.getMaterial();
        BusinessObjectRef ref=context.getObjects().get(0);
        EsignValues.require(material!=null && material.getClass()==MaterialVO.class && "BD_MATERIAL".equals(ref.getObjectType())
            && ref.getObjectPk()!=null && ref.getObjectPk().equals(material.getPrimaryKey())
            && context.getGroupId().equals(material.getPk_group()) && ref.getPolicyOwnerOrg()!=null
            && ref.getPolicyOwnerOrg().equals(material.getPk_org()) && ref.getDesiredOwnerOrg()==null
            && ref.getParentClientObjectId()==null,"物料删除数据与授权档案或所属组织不一致。");
        return material;
    }

    /** 受控删除入口识别：集团与业务单元物料节点共用同一删除动作。 */
    static boolean isRoute(String node,String button,String operation) {
        return ("10140MAG".equals(node) || "10140MAO".equals(node)) && "Delete".equals(button)
            && "DELETE".equals(operation);
    }
}
