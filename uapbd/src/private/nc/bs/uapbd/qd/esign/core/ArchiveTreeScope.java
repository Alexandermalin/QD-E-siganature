package nc.bs.uapbd.qd.esign.core;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import nc.bs.bd.baseservice.persistence.MDTreePersistenceUtil;
import nc.bs.bd.baseservice.persistence.TreeRootInnerCodeLockUtil;
import nc.bs.dao.BaseDAO;
import nc.vo.bd.cust.custclass.CustClassVO;
import nc.vo.bd.material.marbasclass.MarBasClassVO;
import nc.vo.bd.rack.RackVO;
import nc.vo.bd.supplier.supplierclass.SupplierclassVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.util.BDPKLockUtil;

/** Scope the same descendants NC changes in a tree cascade and recheck them under NC's branch lock. */
public final class ArchiveTreeScope {
    private static final int MAX_OBJECTS = 1000;
    private ArchiveTreeScope() { }

    private enum Tree {
        CUSTOMER("BD_CUSTCLASS", "b941438d-ce1f-4fd7-b972-f4f342fbd04f", CustClassVO.class),
        SUPPLIER("BD_SUPPLIERCLASS", "dbacb77c-8a6c-4e50-aab2-d4500383b7b7", SupplierclassVO.class),
        MATERIAL("BD_MARBASCLASS", "c099f7d7-52a9-4b98-bee7-2e3a6c3ea263", MarBasClassVO.class),
        RACK("BD_RACK", "9158fa3b-f765-4132-b14d-1851a18ffddc", RackVO.class);

        final String domain, metadataId;
        final Class<? extends SuperVO> voClass;
        Tree(String domain, String metadataId, Class<? extends SuperVO> voClass) {
            this.domain = domain; this.metadataId = metadataId; this.voClass = voClass;
        }
        static Tree of(String domain) {
            for (Tree tree : values()) if (tree.domain.equals(domain)) return tree;
            return null;
        }
    }

    public static boolean supports(String domain) { return Tree.of(domain) != null; }

    /** Use the same branch lock as the native tree service before checking ancestors. */
    public static void lockBranch(String group,String domain,String rootPk) throws BusinessException {
        Tree tree=Tree.of(domain);
        EsignValues.require(tree!=null && present(group) && present(rootPk),"无法确定分类锁定范围。");
        SuperVO root=(SuperVO)new BaseDAO().retrieveByPK(tree.voClass,rootPk);
        EsignValues.require(root!=null && group.equals(root.getAttributeValue("pk_group")),"分类不属于当前集团。");
        Object originalScope=root.getAttributeValue(tree==Tree.RACK?"pk_stordoc":"innercode");
        EsignValues.require(originalScope instanceof String && present((String)originalScope),"无法确定档案加锁范围。");
        lock(tree,root);
        SuperVO current=(SuperVO)new BaseDAO().retrieveByPK(tree.voClass,rootPk);
        EsignValues.require(current!=null && group.equals(current.getAttributeValue("pk_group"))
            && originalScope.equals(current.getAttributeValue(tree==Tree.RACK?"pk_stordoc":"innercode")),"档案加锁范围已变化，请重新操作。");
    }

    /** Resource codes are the same ones configured on the native tree actions. */
    public static String permissionResource(String domain) {
        if (Tree.CUSTOMER.domain.equals(domain)) return "custclass";
        if (Tree.SUPPLIER.domain.equals(domain)) return "supplierclass";
        if (Tree.MATERIAL.domain.equals(domain)) return "materialbasclass";
        if (Tree.RACK.domain.equals(domain)) return "rack";
        return null;
    }

    /** The input is the selected roots; no client-provided descendant list is trusted. */
    public static List<BusinessObjectRef> expand(String group, List<BusinessObjectRef> roots, boolean lock)
            throws BusinessException {
        return expand(group, roots, lock, new NativeReader());
    }

    /** At execution the current locked branch must equal the objects saved with the authorization. */
    public static void verify(String group, List<BusinessObjectRef> authorized, boolean lock) throws BusinessException {
        verify(group, authorized, lock, new NativeReader());
    }

    static void verify(String group, List<BusinessObjectRef> authorized, boolean lock, Reader reader) throws BusinessException {
        List<BusinessObjectRef> roots = new ArrayList<BusinessObjectRef>();
        for (BusinessObjectRef object : authorized) if (object.getParentClientObjectId() == null) roots.add(object);
        List<BusinessObjectRef> current = expand(group, roots, lock, reader);
        EsignValues.require(identities(authorized).equals(identities(current)),
            "分类下级档案范围已变化，请重新发起操作。");
    }

    interface Reader { List<Row> descendants(String domain, String rootPk, String group, boolean lock) throws BusinessException; }
    static final class Row {
        final String pk, group, owner, code;
        Row(String pk, String group, String owner, String code) {
            this.pk = pk; this.group = group; this.owner = owner; this.code = code;
        }
    }

    static List<BusinessObjectRef> expand(String group, List<BusinessObjectRef> roots, boolean lock, Reader reader)
            throws BusinessException {
        EsignValues.require(present(group) && roots != null && !roots.isEmpty() && reader != null,
            "分类级联缺少集团或所选分类。");
        List<BusinessObjectRef> sorted = new ArrayList<BusinessObjectRef>(roots);
        final Set<String> selected = new HashSet<String>(), ids = new HashSet<String>();
        for (BusinessObjectRef root : sorted) {
            EsignValues.require(root != null && supports(root.getObjectType()) && present(root.getObjectPk())
                && root.getParentClientObjectId() == null && selected.add(key(root)) && ids.add(root.getClientObjectId()),
                "分类级联选择包含无效或重复的档案。");
        }
        Collections.sort(sorted, new Comparator<BusinessObjectRef>() {
            public int compare(BusinessObjectRef left, BusinessObjectRef right) { return key(left).compareTo(key(right)); }
        });
        Map<String,BusinessObjectRef> output = new LinkedHashMap<String,BusinessObjectRef>();
        for (BusinessObjectRef root : sorted) output.put(key(root), root);
        for (BusinessObjectRef root : sorted) {
            List<Row> rows = reader.descendants(root.getObjectType(), root.getObjectPk(), group, lock);
            EsignValues.require(rows != null && !rows.isEmpty(), "无法读取分类及其下级范围。");
            for (Row row : rows) {
                EsignValues.require(row != null && present(row.pk), "分类级联结果缺少档案标识。");
            }
            Collections.sort(rows, new Comparator<Row>() {
                public int compare(Row left, Row right) { return left.pk.compareTo(right.pk); }
            });
            boolean foundRoot = false;
            Set<String> seen = new HashSet<String>();
            for (Row row : rows) {
                EsignValues.require(row != null && present(row.pk) && group.equals(row.group) && present(row.owner)
                    && seen.add(row.pk), "分类下级档案缺少标识或不属于当前集团。");
                String objectKey = root.getObjectType() + "\u0000" + row.pk;
                if (root.getObjectPk().equals(row.pk)) {
                    foundRoot = true;
                    // The client chooses a root, but its code is always the persisted server value.
                    output.put(objectKey, new BusinessObjectRef(root.getClientObjectId(), root.getObjectType(),
                        root.getObjectPk(), row.code, null, root.getDesiredOwnerOrg(), root.getPolicyOwnerOrg(), root.getTargetOrg()));
                }
                if (!output.containsKey(objectKey)) {
                    String clientId = "tree-" + UUID.nameUUIDFromBytes(objectKey.getBytes(Charset.forName("UTF-8"))).toString();
                    EsignValues.require(ids.add(clientId), "分类级联对象标识冲突。");
                    output.put(objectKey, new BusinessObjectRef(clientId, root.getObjectType(), row.pk, row.code,
                        root.getClientObjectId()));
                }
                EsignValues.require(output.size() <= MAX_OBJECTS, "分类级联影响超过当前支持的 1000 条档案，请联系管理员处理。");
            }
            EsignValues.require(foundRoot, "分类级联结果不包含所选档案。");
        }
        return Collections.unmodifiableList(new ArrayList<BusinessObjectRef>(output.values()));
    }

    private static Set<String> identities(List<BusinessObjectRef> objects) throws BusinessException {
        Set<String> values = new HashSet<String>(), keys = new HashSet<String>();
        for (BusinessObjectRef ref : objects) {
            EsignValues.require(ref != null && present(ref.getObjectPk()) && supports(ref.getObjectType())
                && keys.add(key(ref)) && values.add(key(ref) + "\u0000" + ref.getClientObjectId() + "\u0000"
                    + (ref.getParentClientObjectId() == null ? "" : ref.getParentClientObjectId())),
                "分类授权对象无效或重复。");
        }
        return values;
    }

    private static String key(BusinessObjectRef ref) { return ref.getObjectType() + "\u0000" + ref.getObjectPk(); }
    private static boolean present(String value) { return value != null && !value.trim().isEmpty(); }

    private static final class NativeReader implements Reader {
        public List<Row> descendants(String domain, String rootPk, String group, boolean lock) throws BusinessException {
            Tree tree = Tree.of(domain);
            EsignValues.require(tree != null, "未知的分类档案类型。");
            SuperVO root = (SuperVO)new BaseDAO().retrieveByPK(tree.voClass, rootPk);
            EsignValues.require(root != null && group.equals(text(root, "pk_group")) && present(text(root, "pk_org")),
                "所选分类不属于当前集团或所属组织不明确。");
            MDTreePersistenceUtil<SuperVO> nativeTree = new MDTreePersistenceUtil<SuperVO>(tree.metadataId, null);
            String warehouse=tree==Tree.RACK?text(root,"pk_stordoc"):null;
            if(tree==Tree.RACK) EsignValues.require(present(warehouse),"货位缺少所属仓库。");
            if (lock) {
                String originalScope=tree==Tree.RACK?warehouse:text(root,"innercode");
                EsignValues.require(present(originalScope),"无法确定档案加锁范围。");
                ArchiveTreeScope.lock(tree,root);
                SuperVO current=(SuperVO)new BaseDAO().retrieveByPK(tree.voClass,rootPk);
                EsignValues.require(current!=null && group.equals(text(current,"pk_group"))
                    && originalScope.equals(text(current,tree==Tree.RACK?"pk_stordoc":"innercode")),"档案范围在加锁时已变化。");
            }
            SuperVO[] branch = nativeTree.retrieveSubVOsWithSelf(rootPk);
            List<Row> rows = new ArrayList<Row>();
            if (branch != null) for (SuperVO value : branch) {
                EsignValues.require(value != null && tree.voClass.isInstance(value), "原生分类范围返回了其他档案类型。");
                if(tree==Tree.RACK) EsignValues.require(warehouse.equals(text(value,"pk_stordoc")),"货位级联范围跨越了其他仓库，无法安全执行。");
                rows.add(new Row(value.getPrimaryKey(), text(value, "pk_group"), text(value, "pk_org"), text(value, "code")));
            }
            return rows;
        }
        private static String text(SuperVO value, String field) {
            Object actual = value.getAttributeValue(field);
            return actual instanceof String ? (String)actual : null;
        }
    }
    private static void lock(Tree tree,SuperVO root) throws BusinessException {
        if(tree==Tree.RACK) {
            Object warehouse=root.getAttributeValue("pk_stordoc");
            EsignValues.require(warehouse instanceof String && present((String)warehouse),"货位缺少所属仓库。");
            BDPKLockUtil.lockString(new String[]{tree.metadataId+"#"+(String)warehouse});
        } else new TreeRootInnerCodeLockUtil<SuperVO>(new MDTreePersistenceUtil<SuperVO>(tree.metadataId,null)).lockBranchRoot(root);
    }
}
