package nc.impl.uapbd.qd.esign;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import nc.bs.dao.BaseDAO;
import nc.bs.framework.common.NCLocator;
import nc.bs.uapbd.qd.esign.audit.AuditCaptureScope;
import nc.bs.uapbd.qd.esign.audit.PhysicalSnapshotReader;
import nc.itf.bd.material.baseinfo.IMaterialBaseInfoExtendService;
import nc.itf.bd.material.baseinfo.IMaterialBaseInfoService;
import nc.itf.uapbd.qd.esign.IMaterialOperationAdapter;
import nc.ui.bd.pub.extend.PrivateServiceContext;
import nc.ui.bd.pub.extend.PrivateServiceInfo;
import nc.vo.bd.material.MaterialVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.MaterialCommand;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;

/** 仅适配物料主页面的原生服务，不包含子页面、版本、分发和 RequiresNew 删除路径；不复制 NC 原生业务校验。 */
public final class MaterialOperationAdapter implements IMaterialOperationAdapter {
    public void validate(OperationContext context, Serializable payload) throws BusinessException {
        require(payload instanceof MaterialCommand, "MaterialCommand required");
        MaterialCommand command = (MaterialCommand) payload;
        MaterialVO material = command.getMaterial();
        require(material != null && context.getObjects().size() == 1, "One material object is required");
        String operation = context.getOperationType();
        // Native deletion also removes version/organization data and invokes an independent
        // file-storage service.  It cannot use this single-row save adapter until those effects
        // are captured and file cleanup is safely coordinated with the business transaction.
        require(!"DELETE".equals(operation), "物料主档删除尚未完成关联数据和图片清理的事务核验，不能使用此适配器。");
        require(Arrays.asList("INSERT", "UPDATE").contains(operation), "Unsupported material action");
        require(sameOwner(context, material), "Material owner organization mismatch; shared/global node needs a verified dedicated adapter");
        BusinessObjectRef object = context.getObjects().get(0);
        require("BD_MATERIAL".equals(object.getObjectType()), "Material object type mismatch");

        boolean ordinarySave = MaterialCommand.supportsSave(context);
        require(ordinarySave == command.hasExtensions(), "Material Save requires the matching native command");
        if (ordinarySave) {
            require(MaterialCommand.matchesDraft(context, material), "Signed material does not match command");
            require(!"INSERT".equals(operation) || material.getPk_material_pf() == null,
                    "Material workflow inserts require a dedicated adapter");
            String signedCode = object.getBillCode();
            String draftCode = material.getCode();
            require(signedCode == null || signedCode.trim().isEmpty()
                    || (draftCode != null && signedCode.trim().equals(draftCode.trim())),
                    "Material code changed after signature preparation");
            validateExtensions(command);
            require(noRows(material.getMaterialfi()) && noRows(material.getMaterialpu())
                    && noRows(material.getMaterialsale()) && noRows(material.getMaterialstock())
                    && noRows(material.getMaterialplan()) && noRows(material.getMaterialprod())
                    && noRows(material.getMaterialcost()),
                    "Organization subpages must use their own material services");
        }

        if ("INSERT".equals(operation)) {
            require(object.getObjectPk() == null && material.getPrimaryKey() == null,
                    "INSERT cannot target an existing material");
            return;
        }
        require(object.getObjectPk() != null && object.getObjectPk().equals(material.getPrimaryKey()),
                "Signed material does not match command");
        MaterialVO stored = (MaterialVO) new BaseDAO().retrieveByPK(MaterialVO.class, object.getObjectPk(),
                new String[]{"pk_material", "pk_org", "pk_group"});
        require(stored != null && storedOwner(context, stored), "Stored material organization is outside the authorized request");
    }

    private static boolean noRows(Object[] rows) {
        return rows == null || rows.length == 0;
    }

    private static void validateExtensions(MaterialCommand command) throws BusinessException {
        Map<String, Object> values = command.getExtensionValues();
        PrivateServiceContext context = command.getExtensionContext();
        require(context != null && context.getClass() == PrivateServiceContext.class,
                "Material extension context is invalid");
        List<PrivateServiceInfo> services = context.getExtendInfoList();
        // 两个原生配置共用空 baseExtendContext。原生持久化会用客户端传入的服务名定位服务。
        require(values != null && values.getClass() == HashMap.class && values.isEmpty()
                && services != null && services.getClass() == ArrayList.class && services.isEmpty(),
                "Custom material extension services are outside the verified pilot configuration");
    }

    public Serializable execute(OperationContext context, Serializable payload) throws BusinessException {
        validate(context, payload);
        MaterialCommand command = (MaterialCommand) payload;
        MaterialVO material = command.getMaterial();
        if (command.hasExtensions()) {
            return save(context, material);
        }
        IMaterialBaseInfoService service = NCLocator.getInstance().lookup(IMaterialBaseInfoService.class);
        if ("INSERT".equals(context.getOperationType())) return new MaterialVO[]{service.insertMaterial(material)};
        if ("UPDATE".equals(context.getOperationType())) return service.updateMaterial(material);
        throw new BusinessException("Unsupported material action");
    }

    private static MaterialVO[] save(OperationContext context, MaterialVO material) throws BusinessException {
        boolean insert = "INSERT".equals(context.getOperationType());
        AuditCaptureScope scope = AuditCaptureScope.current();
        boolean audit = scope != null && scope.isEnabled();
        PhysicalSnapshotReader reader = new PhysicalSnapshotReader();
        String key = material.getPrimaryKey();
        String source = null;
        List<SnapshotRow> previous = new ArrayList<SnapshotRow>();
        if (!insert) {
            // 锁和物料保存都在网关 CMT 内。原生业务校验继续由原生保存服务处理。
            List<SnapshotRow> root = reader.readLocked("bd_material", "pk_material", null, "pk_material", key, true);
            require(root.size() == 1 && owner(context, root.get(0), false), "Stored material is outside the authorized request");
            Map<String, String> values = root.get(0).getValues();
            source = values.get("pk_source");
            require(source != null && Objects.equals(source, material.getPk_source())
                    && Objects.equals(values.get("pk_material_pf"), material.getPk_material_pf()),
                    "Material version or workflow source differs from the authorized row");
            // 原生改编码会同步同一原始版本的其他主档。单对象请求只可变更一条主档。
            if (!Objects.equals(values.get("code"), material.getCode())) {
                require(reader.readLocked("bd_material", "pk_material", null, "pk_source", source, true).size() == 1,
                        "Changing the code of multiple material versions requires a separate authorized operation");
            }
            previous.addAll(root);
            previous.addAll(children(reader, "bd_materialconvert", "pk_materialconvert", key, true));
            previous.addAll(children(reader, "bd_materialtaxtype", "pk_materialtaxtype", key, true));
            if (audit) {
                previous.addAll(version(reader, source, true));
                scope.before(scope.objectId("BD_MATERIAL", key), previous);
            }
        }
        validateChildren(material.getMaterialconvert(), "bd_materialconvert", key, previous);
        validateChildren(material.getMaterialtaxtype(), "bd_materialtaxtype", key, previous);

        IMaterialBaseInfoExtendService service = NCLocator.getInstance().lookup(IMaterialBaseInfoExtendService.class);
        Map<String, Object> extensions = new HashMap<String, Object>();
        PrivateServiceContext extensionContext = new PrivateServiceContext();
        MaterialVO[] result = insert
                ? new MaterialVO[]{service.insertMaterialWithExtendVO(material, extensions, extensionContext)}
                : service.updateMaterialWithExtendVO(material, extensions, extensionContext);
        require(result != null && result.length == 1 && result[0] != null,
                "Material save returned an unexpected version set");
        MaterialVO saved = result[0];
        require(saved.getPrimaryKey() != null && !saved.getPrimaryKey().trim().isEmpty()
                && saved.getCode() != null && !saved.getCode().trim().isEmpty() && sameOwner(context, saved)
                && (insert || key.equals(saved.getPrimaryKey())), "Native material save returned a different material");
        if (audit) {
            key = saved.getPrimaryKey();
            List<SnapshotRow> rows = reader.readByPk("bd_material", "pk_material", key);
            require(rows.size() == 1 && owner(context, rows.get(0), true)
                    && saved.getCode().equals(rows.get(0).getValues().get("code")),
                    "Persisted material does not match native save result");
            source = rows.get(0).getValues().get("pk_source");
            require(source != null, "Persisted material is missing its version source");
            rows.addAll(version(reader, source, false));
            rows.addAll(children(reader, "bd_materialconvert", "pk_materialconvert", key, false));
            rows.addAll(children(reader, "bd_materialtaxtype", "pk_materialtaxtype", key, false));
            if (insert && "10140MAO".equals(context.getNodeCode())) {
                rows.addAll(reader.read("bd_marorg", "pk_marorg", "pk_material", "pk_material", key,
                        "pk_org", context.getOrgId(), true));
            }
            scope.after(scope.objectId("BD_MATERIAL", key), rows);
        }
        return result;
    }

    private static List<SnapshotRow> children(PhysicalSnapshotReader reader, String table,
            String keyColumn, String parent, boolean lock) throws BusinessException {
        return lock ? reader.readLocked(table, keyColumn, "pk_material", "pk_material", parent, true)
                : reader.read(table, keyColumn, "pk_material", "pk_material", parent, true);
    }

    private static List<SnapshotRow> version(PhysicalSnapshotReader reader, String source, boolean lock)
            throws BusinessException {
        return lock ? reader.readLocked("bd_material_v", "pk_source", null, "pk_source", source, true)
                : reader.readByPk("bd_material_v", "pk_source", source);
    }

    private static void validateChildren(SuperVO[] children, String entity, String parent, List<SnapshotRow> previous)
            throws BusinessException {
        if (children == null) return;
        Set<String> existing = new HashSet<String>();
        for (SnapshotRow row : previous) {
            if (entity.equals(row.getEntity())) existing.add(row.getRowId());
        }
        for (SuperVO child : children) {
            require(child != null, "Material save contains an invalid detail row");
            Object childParent = child.getAttributeValue("pk_material");
            require(childParent == null || childParent.equals(parent), "Material detail belongs to another material");
            String childKey = child.getPrimaryKey();
            require(childKey == null || existing.remove(childKey),
                    "Material detail key is outside the authorized rows or appears twice");
        }
    }

    private static boolean owner(OperationContext context, SnapshotRow row, boolean after) {
        String expected = expectedOwner(context, after);
        return context.getGroupId().equals(row.getValues().get("pk_group"))
                && expected != null && expected.equals(row.getValues().get("pk_org"));
    }

    public List<BusinessObjectRef> describeResult(OperationContext context, Serializable result) throws BusinessException {
        if ("DELETE".equals(context.getOperationType())) return context.getObjects();
        require(result instanceof MaterialVO[] && ((MaterialVO[]) result).length == 1,
                "Multiple material versions require a dedicated adapter");
        MaterialVO saved = ((MaterialVO[]) result)[0];
        require(saved != null && saved.getPrimaryKey() != null, "Saved material key is missing");
        BusinessObjectRef original = context.getObjects().get(0);
        require("INSERT".equals(context.getOperationType()) || original.getObjectPk().equals(saved.getPrimaryKey()),
                "Material version change requires a dedicated adapter");
        return Collections.singletonList(new BusinessObjectRef(original.getClientObjectId(), original.getObjectType(),
                saved.getPrimaryKey(), saved.getCode(), original.getParentClientObjectId()));
    }

    private static boolean sameOwner(OperationContext context, MaterialVO material) {
        String expected = expectedOwner(context, true);
        return context.getGroupId().equals(material.getPk_group()) && expected != null && expected.equals(material.getPk_org());
    }

    private static boolean storedOwner(OperationContext context, MaterialVO material) {
        String expected = expectedOwner(context, false);
        return context.getGroupId().equals(material.getPk_group()) && expected != null && expected.equals(material.getPk_org());
    }

    private static String expectedOwner(OperationContext context, boolean after) {
        if (!OperationContext.OWNER_POLICY_PROTOCOL.equals(context.getPolicyProtocol())) { return context.getOrgId(); }
        BusinessObjectRef object = context.getObjects().get(0);
        return after ? object.getDesiredOwnerOrg() : object.getPolicyOwnerOrg();
    }

    private static void require(boolean condition, String message) throws BusinessException {
        if (!condition) throw new BusinessException(message);
    }
}
