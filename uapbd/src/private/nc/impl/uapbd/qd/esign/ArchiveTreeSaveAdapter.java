package nc.impl.uapbd.qd.esign;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import nc.bs.bd.baseservice.persistence.MDTreePersistenceUtil;
import nc.bs.bd.baseservice.persistence.TreeRootInnerCodeLockUtil;
import nc.bs.dao.BaseDAO;
import nc.bs.framework.common.NCLocator;
import nc.bs.uapbd.qd.esign.audit.AuditCaptureScope;
import nc.bs.uapbd.qd.esign.audit.PhysicalSnapshotReader;
import nc.bs.uapbd.qd.esign.core.ArchiveTreeSaveImpact;
import nc.bs.uapbd.qd.esign.core.ArchiveTreeSaveScope;
import nc.bs.uapbd.qd.esign.core.ArchiveTreeScope;
import nc.bs.uapbd.qd.esign.core.BaseArchiveOwnerType;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.bs.uapbd.qd.esign.security.EsignPermission;
import nc.bs.uapbd.qd.esign.security.ServerIdentity;
import nc.impl.bd.rack.RackBizlockDataProvider;
import nc.itf.bd.cust.custclass.ICustClassService;
import nc.itf.bd.material.marbasclass.IMaterialBasClassExtendService;
import nc.itf.bd.rack.IRackService;
import nc.itf.bd.supplier.supplierclass.ISupplierclassService;
import nc.itf.uapbd.qd.esign.IArchiveTreeSaveAdapter;
import nc.vo.bd.cust.custclass.CustClassVO;
import nc.vo.bd.material.marbasclass.MarBasClassSysParaUtil;
import nc.vo.bd.material.marbasclass.MarBasClassVO;
import nc.vo.bd.rack.RackCodeRuleUtil;
import nc.vo.bd.rack.RackVO;
import nc.vo.bd.supplier.supplierclass.SupplierclassVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.CircularlyAccessibleValueObject;
import nc.vo.pub.SuperVO;
import nc.vo.uapbd.qd.esign.ArchiveTreeSaveCommand;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;
import nc.vo.uif2.CodeRuleUtil;
import nc.vo.util.BDPKLockUtil;
import nc.vo.util.bizlock.BizlockDataUtil;

/** Candidate tree save with native locks and per-owner snapshots. */
public final class ArchiveTreeSaveAdapter implements IArchiveTreeSaveAdapter {
    public void validate(OperationContext context,Serializable command) throws BusinessException { value(context,command); }

    /** Acquire the same tree/warehouse and business locks before owner-policy row locks. */
    static void prelock(OperationContext context,Serializable command) throws BusinessException {
        SuperVO value=value(context,command);BusinessObjectRef root=root(context);Tree tree=tree(root.getObjectType());
        ServerIdentity identity=ServerIdentity.current();
        EsignValues.require(context.getGroupId().equals(identity.groupId),"树形档案集团已变化。");
        if("INSERT".equals(context.getOperationType())) {
            ArchiveTreeMutationLock.acquire(tree.domain,tree==Tree.MATERIAL);
            prelockInsert(identity,context,tree,value);return;
        }
        EsignPermission.requireArchiveData(identity,ArchiveTreeScope.permissionResource(tree.domain),"edit",context.getObjects());
        ArchiveTreeMutationLock.acquire(tree.domain,tree==Tree.MATERIAL);
        SuperVO persisted=(SuperVO)new BaseDAO().retrieveByPK(tree.voClass,root.getObjectPk());
        EsignValues.require(persisted!=null && context.getGroupId().equals(text(persisted,"pk_group"))
            && root.getPolicyOwnerOrg().equals(text(persisted,"pk_org")) && text(persisted,"innercode")!=null
            && text(persisted,"innercode").equals(text(value,"innercode")),"树形档案或内部编码已变化，请重新查询。");
        if(tree==Tree.RACK) EsignValues.require(Objects.equals(text(persisted,"pk_stordoc"),text(value,"pk_stordoc")),
            "货位所属仓库已变化。");
        MDTreePersistenceUtil<SuperVO> nativeTree=new MDTreePersistenceUtil<SuperVO>(tree.metadata,null);
        deriveParentWhenCodeChanges(tree,persisted,value,nativeTree);
        checkParent(identity,context,tree,persisted,value);
        if(tree==Tree.RACK) {
            String warehouse=text(value,"pk_stordoc");EsignValues.require(warehouse!=null,"货位缺少仓库。");
            BDPKLockUtil.lockString(new String[]{tree.metadata+"#"+warehouse});
            BizlockDataUtil.lockDataByBizlock(new RackBizlockDataProvider(),new Object[]{value});
        } else {
            new TreeRootInnerCodeLockUtil<SuperVO>(nativeTree).lockBranchRootWhenParentChanged(value);
            BizlockDataUtil.lockDataByBizlock(new CircularlyAccessibleValueObject[]{value});
        }
        ArchiveTreeSaveScope.verifyAuthorized(context.getObjects(),selection(context,value));
    }
    private static void prelockInsert(ServerIdentity identity,OperationContext context,Tree tree,SuperVO value) throws BusinessException {
        MDTreePersistenceUtil<SuperVO> nativeTree=new MDTreePersistenceUtil<SuperVO>(tree.metadata,null);
        deriveParentWhenCodeChanges(tree,null,value,nativeTree);
        checkParent(identity,context,tree,null,value);
        if(tree==Tree.RACK) {
            String warehouse=text(value,"pk_stordoc");EsignValues.require(warehouse!=null,"货位缺少仓库。");
            BDPKLockUtil.lockString(new String[]{tree.metadata+"#"+warehouse});
            BizlockDataUtil.lockDataByBizlock(new RackBizlockDataProvider(),new Object[]{value});
        } else {
            String parent=text(value,ArchiveTreeSaveImpact.parentField(tree.domain));
            // The native single-row insert takes the parent branch lock only for a non-null parent.
            if(parent!=null) new TreeRootInnerCodeLockUtil<SuperVO>(nativeTree).lockBranchRoot(value);
            BizlockDataUtil.lockDataByBizlock(new CircularlyAccessibleValueObject[]{value});
        }
    }
    public Serializable execute(OperationContext context,Serializable command) throws BusinessException {
        SuperVO value=value(context,command);BusinessObjectRef root=root(context);Tree tree=tree(root.getObjectType());
        if("INSERT".equals(context.getOperationType())) return insert(context,tree,root,value);
        ArchiveTreeSaveScope.Decision selected=selection(context,value);
        ArchiveTreeSaveScope.verifyAuthorized(context.getObjects(),selected);
        AuditCaptureScope scope=AuditCaptureScope.current();
        EsignValues.require(scope!=null,"树形档案保存缺少受控事务上下文。");
        PhysicalSnapshotReader reader=new PhysicalSnapshotReader();
        List<SnapshotRow> extension=Collections.emptyList();
        for(BusinessObjectRef ref:context.getObjects()) {
            List<SnapshotRow> rows=read(reader,context,tree,ref,true,false);
            if(ref==root && tree==Tree.MATERIAL) extension=rows.subList(1,rows.size());
            if(scope.isEnabledFor(ref.getClientObjectId())) scope.before(ref.getClientObjectId(),rows);
        }
        SuperVO saved=update(context,tree,value,extension);
        EsignValues.require(saved!=null && saved.getClass()==tree.voClass && root.getObjectPk().equals(pk(saved)),
            "原生树形档案保存结果与授权不一致。");
        if(selected.changesBranch()) ArchiveTreeSaveScope.verifyBranch(context.getGroupId(),context.getObjects());
        for(BusinessObjectRef ref:context.getObjects()) {
            List<SnapshotRow> rows=read(reader,context,tree,ref,false,true);
            if(scope.isEnabledFor(ref.getClientObjectId())) scope.after(ref.getClientObjectId(),rows);
        }
        return saved;
    }
    public List<BusinessObjectRef> describeResult(OperationContext context,Serializable result) throws BusinessException {
        BusinessObjectRef selected=root(context);Tree tree=tree(selected.getObjectType());
        if("INSERT".equals(context.getOperationType())) {
            EsignValues.require(result!=null && result.getClass()==tree.voClass && pk((SuperVO)result)!=null
                && context.getGroupId().equals(text((SuperVO)result,"pk_group"))
                && selected.getDesiredOwnerOrg().equals(text((SuperVO)result,"pk_org")),"新增树形档案结果不完整。");
            return Collections.singletonList(new BusinessObjectRef(selected.getClientObjectId(),selected.getObjectType(),pk((SuperVO)result),
                text((SuperVO)result,"code"),null,selected.getDesiredOwnerOrg(),selected.getPolicyOwnerOrg(),selected.getTargetOrg()));
        }
        EsignValues.require(result!=null && result.getClass()==tree.voClass && selected.getObjectPk().equals(pk((SuperVO)result)),
            "树形档案保存返回了其他档案。");
        List<BusinessObjectRef> refs=new ArrayList<BusinessObjectRef>();BaseDAO dao=new BaseDAO();
        for(BusinessObjectRef ref:context.getObjects()) {
            SuperVO saved=(SuperVO)dao.retrieveByPK(tree.voClass,ref.getObjectPk());
            EsignValues.require(saved!=null && context.getGroupId().equals(text(saved,"pk_group")) && text(saved,"code")!=null,
                "无法确认树形档案保存结果。");
            refs.add(new BusinessObjectRef(ref.getClientObjectId(),ref.getObjectType(),ref.getObjectPk(),text(saved,"code"),
                ref.getParentClientObjectId(),ref.getDesiredOwnerOrg(),ref.getPolicyOwnerOrg(),ref.getTargetOrg()));
        }
        return refs;
    }

    private static SuperVO insert(OperationContext context,Tree tree,BusinessObjectRef root,SuperVO value) throws BusinessException {
        AuditCaptureScope scope=AuditCaptureScope.current();EsignValues.require(scope!=null,"树形档案新增缺少受控事务上下文。");
        NCLocator locator=NCLocator.getInstance();Object raw;
        if(tree==Tree.CUSTOMER) raw=locator.lookup(ICustClassService.class).insertCustClass((CustClassVO)value);
        else if(tree==Tree.SUPPLIER) raw=locator.lookup(ISupplierclassService.class).insertSupplierclassVO((SupplierclassVO)value);
        else if(tree==Tree.RACK) raw=locator.lookup(IRackService.class).insertRack((RackVO)value);
        else {
            MarBasClassVO materialClass=(MarBasClassVO)value;
            raw=locator.lookup(IMaterialBasClassExtendService.class).insertMarBasClAndExtendVO(materialClass,
                MaterialClassExtension.saveContext(context.getNodeCode(),materialClass,Collections.<SnapshotRow>emptyList()));
        }
        EsignValues.require(raw!=null && raw.getClass()==tree.voClass && pk((SuperVO)raw)!=null
            && context.getGroupId().equals(text((SuperVO)raw,"pk_group"))
            && root.getDesiredOwnerOrg().equals(text((SuperVO)raw,"pk_org")),"原生新增树形档案的所属组织不一致。");
        SuperVO saved=(SuperVO)raw;
        BusinessObjectRef actual=new BusinessObjectRef(root.getClientObjectId(),root.getObjectType(),pk(saved),text(saved,"code"),null,
            root.getDesiredOwnerOrg(),root.getPolicyOwnerOrg(),root.getTargetOrg());
        List<SnapshotRow> after=read(new PhysicalSnapshotReader(),context,tree,actual,false,true);
        if(scope.isEnabledFor(root.getClientObjectId())) scope.after(root.getClientObjectId(),after);
        return saved;
    }

    private static ArchiveTreeSaveScope.Decision selection(OperationContext context,SuperVO value) throws BusinessException {
        BusinessObjectRef root=root(context);
        return ArchiveTreeSaveScope.select(context.getGroupId(),root,text(value,"code"),
            text(value,ArchiveTreeSaveImpact.parentField(root.getObjectType())),
            "BD_RACK".equals(root.getObjectType())?text(value,"pk_stordoc"):null);
    }
    private static void deriveParentWhenCodeChanges(Tree tree,SuperVO persisted,SuperVO value,MDTreePersistenceUtil<SuperVO> nativeTree)
            throws BusinessException {
        if(tree!=Tree.MATERIAL && tree!=Tree.RACK) return;
        String oldCode=persisted==null?null:text(persisted,"code"),code=text(value,"code");
        EsignValues.require((persisted==null || oldCode!=null) && code!=null && !code.trim().isEmpty(),"树形档案编码不能为空。");
        code=code.trim();value.setAttributeValue("code",code);
        if(oldCode!=null && oldCode.equals(code)) return;
        String rule=tree==Tree.MATERIAL?MarBasClassSysParaUtil.getCodeRuleSysPara(text(value,"pk_group"),text(value,"pk_org"))
            :RackCodeRuleUtil.getCodeRule(text(value,"pk_org"));
        String parent=nativeTree.retrieveParentPk(value,new CodeRuleUtil(rule));
        value.setAttributeValue(ArchiveTreeSaveImpact.parentField(tree.domain),parent);
    }
    private static void checkParent(ServerIdentity identity,OperationContext context,Tree tree,SuperVO old,SuperVO value)
            throws BusinessException {
        String field=ArchiveTreeSaveImpact.parentField(tree.domain),previous=old==null?null:text(old,field),next=text(value,field);
        if(Objects.equals(previous,next) || next==null || next.isEmpty() || "~".equals(next)) return;
        SuperVO parent=(SuperVO)new BaseDAO().retrieveByPK(tree.voClass,next);
        EsignValues.require(parent!=null && context.getGroupId().equals(text(parent,"pk_group")),"新上级不属于当前集团。");
        if(tree==Tree.RACK) EsignValues.require(Objects.equals(text(value,"pk_stordoc"),text(parent,"pk_stordoc")),"货位新上级属于其他仓库。");
        BusinessObjectRef ref=new BusinessObjectRef("new-parent",tree.domain,next,text(parent,"code"),null);
        EsignPermission.requireArchiveData(identity,ArchiveTreeScope.permissionResource(tree.domain),"edit",Collections.singletonList(ref));
    }
    private static List<SnapshotRow> read(PhysicalSnapshotReader reader,OperationContext context,Tree tree,BusinessObjectRef ref,boolean lock,boolean after)
            throws BusinessException {
        BaseArchiveOwnerType type=BaseArchiveOwnerType.forDomain(tree.domain);
        List<SnapshotRow> rows=lock?reader.readLocked(type.rootTable,type.rootKey,null,type.rootKey,ref.getObjectPk(),"pk_group",context.getGroupId(),true)
            :reader.read(type.rootTable,type.rootKey,null,type.rootKey,ref.getObjectPk(),"pk_group",context.getGroupId(),true);
        String owner=after && ref.getDesiredOwnerOrg()!=null?ref.getDesiredOwnerOrg():ref.getPolicyOwnerOrg();
        EsignValues.require(rows.size()==1 && owner!=null && owner.equals(rows.get(0).getValues().get("pk_org")),
            "树形档案不存在或所属组织已变化，业务必须回滚。");
        SnapshotRow row=rows.get(0);String parent=row.getValues().get(ArchiveTreeSaveImpact.parentField(tree.domain));
        List<SnapshotRow> result=new ArrayList<SnapshotRow>();
        result.add(new SnapshotRow(row.getEntity(),row.getRowId(),parent,row.getValues(),row.getTypes(),row.getLabels(),true));
        if(tree==Tree.MATERIAL) result.addAll(MaterialClassExtension.rows(reader,row.getRowId(),context.getGroupId(),owner,lock));
        return result;
    }
    private static SuperVO update(OperationContext context,Tree tree,SuperVO value,List<SnapshotRow> extension) throws BusinessException {
        NCLocator locator=NCLocator.getInstance();Object result;
        if(tree==Tree.CUSTOMER) result=locator.lookup(ICustClassService.class).updateCustClass((CustClassVO)value);
        else if(tree==Tree.SUPPLIER) result=locator.lookup(ISupplierclassService.class).updateSupplierclassVO((SupplierclassVO)value);
        else if(tree==Tree.MATERIAL) {
            MarBasClassVO materialClass=(MarBasClassVO)value;
            result=locator.lookup(IMaterialBasClassExtendService.class).updateMarBasClAndExtendVO(materialClass,
                MaterialClassExtension.saveContext(context.getNodeCode(),materialClass,extension));
        }
        else result=locator.lookup(IRackService.class).updateRack((RackVO)value);
        EsignValues.require(result instanceof SuperVO,"原生树形档案保存未返回有效结果。");return (SuperVO)result;
    }
    private static SuperVO value(OperationContext context,Serializable command) throws BusinessException {
        EsignValues.require(context!=null && ("UPDATE".equals(context.getOperationType()) || "INSERT".equals(context.getOperationType())) && "Save".equals(context.getButtonCode())
            && OperationContext.OWNER_POLICY_PROTOCOL.equals(context.getPolicyProtocol()) && context.getGroupId()!=null
            && context.getOrgId()!=null && command instanceof ArchiveTreeSaveCommand,"树形档案保存载荷或动作不正确。");
        BusinessObjectRef root=root(context);Tree tree=tree(root.getObjectType());SuperVO value=((ArchiveTreeSaveCommand)command).getValue();
        String desired=root.getDesiredOwnerOrg()==null?root.getPolicyOwnerOrg():root.getDesiredOwnerOrg();
        boolean update="UPDATE".equals(context.getOperationType());
        EsignValues.require(tree.matchesNode(context.getNodeCode()) && value!=null && value.getClass()==tree.voClass
            && (update?root.getObjectPk()!=null && root.getObjectPk().equals(pk(value))
                :context.getObjects().size()==1 && root.getObjectPk()==null && pk(value)==null
                    && (text(value,"innercode")==null || text(value,"innercode").trim().isEmpty())
                    && root.getDesiredOwnerOrg()!=null && root.getDesiredOwnerOrg().equals(context.getOrgId()))
            && context.getGroupId().equals(text(value,"pk_group"))
            && root.getPolicyOwnerOrg()!=null && desired!=null && desired.equals(text(value,"pk_org")),
            "树形档案保存的节点、主档或所属组织不一致。");
        return value;
    }
    private static BusinessObjectRef root(OperationContext context) throws BusinessException {
        EsignValues.require(context!=null && !context.getObjects().isEmpty(),"树形档案保存缺少授权范围。");
        BusinessObjectRef root=null;
        for(BusinessObjectRef ref:context.getObjects()) if(ref.getParentClientObjectId()==null) {
            EsignValues.require(root==null,"树形档案保存只能选择一个根对象。");root=ref;
        }
        EsignValues.require(root!=null && ("INSERT".equals(context.getOperationType()) || root.getObjectPk()!=null),"树形档案保存根对象无效。");
        for(BusinessObjectRef ref:context.getObjects()) EsignValues.require(root.getObjectType().equals(ref.getObjectType())
            && (ref.getObjectPk()!=null || "INSERT".equals(context.getOperationType()) && ref==root) && ref.getPolicyOwnerOrg()!=null
            && context.getOrgId().equals(ref.getTargetOrg())
            && (ref==root || root.getClientObjectId().equals(ref.getParentClientObjectId()) && ref.getDesiredOwnerOrg()==null),
            "树形档案保存包含无效的级联对象。");
        return root;
    }
    static String text(SuperVO row,String name) {
        if(row instanceof CustClassVO) {
            CustClassVO v=(CustClassVO)row;
            if("pk_group".equals(name)) return v.getPk_group();if("pk_org".equals(name)) return v.getPk_org();
            if("code".equals(name)) return v.getCode();if("parent_id".equals(name)) return v.getParent_id();
            if("innercode".equals(name)) return v.getInnercode();
        } else if(row instanceof SupplierclassVO) {
            SupplierclassVO v=(SupplierclassVO)row;
            if("pk_group".equals(name)) return v.getPk_group();if("pk_org".equals(name)) return v.getPk_org();
            if("code".equals(name)) return v.getCode();if("parent_id".equals(name)) return v.getParent_id();
            if("innercode".equals(name)) return v.getInnercode();
        } else if(row instanceof MarBasClassVO) {
            MarBasClassVO v=(MarBasClassVO)row;
            if("pk_group".equals(name)) return v.getPk_group();if("pk_org".equals(name)) return v.getPk_org();
            if("code".equals(name)) return v.getCode();if("pk_parent".equals(name)) return v.getPk_parent();
            if("innercode".equals(name)) return v.getInnercode();
        } else if(row instanceof RackVO) {
            RackVO v=(RackVO)row;
            if("pk_group".equals(name)) return v.getPk_group();if("pk_org".equals(name)) return v.getPk_org();
            if("code".equals(name)) return v.getCode();if("pk_parent".equals(name)) return v.getPk_parent();
            if("innercode".equals(name)) return v.getInnercode();if("pk_stordoc".equals(name)) return v.getPk_stordoc();
        }
        throw new IllegalArgumentException("Unsupported tree archive field: "+name);
    }
    static String pk(SuperVO row) {
        if(row instanceof CustClassVO) return ((CustClassVO)row).getPk_custclass();
        if(row instanceof SupplierclassVO) return ((SupplierclassVO)row).getPk_supplierclass();
        if(row instanceof MarBasClassVO) return ((MarBasClassVO)row).getPk_marbasclass();
        if(row instanceof RackVO) return ((RackVO)row).getPk_rack();
        return null;
    }
    static Tree tree(String domain) throws BusinessException {
        for(Tree tree:Tree.values()) if(tree.domain.equals(domain)) return tree;throw new BusinessException("未知的树形档案类型。");
    }
    enum Tree {
        CUSTOMER("BD_CUSTCLASS","b941438d-ce1f-4fd7-b972-f4f342fbd04f",CustClassVO.class,"10140CCLG","10140CCLO"),
        SUPPLIER("BD_SUPPLIERCLASS","dbacb77c-8a6c-4e50-aab2-d4500383b7b7",SupplierclassVO.class,"10140SCLG","10140SCLO"),
        MATERIAL("BD_MARBASCLASS","c099f7d7-52a9-4b98-bee7-2e3a6c3ea263",MarBasClassVO.class,"10140MCLG","10140MCLO"),
        RACK("BD_RACK","9158fa3b-f765-4132-b14d-1851a18ffddc",RackVO.class,"10140RACK","10140RACK");
        final String domain,metadata,groupNode,orgNode;final Class<? extends SuperVO> voClass;
        Tree(String domain,String metadata,Class<? extends SuperVO> voClass,String groupNode,String orgNode) {
            this.domain=domain;this.metadata=metadata;this.voClass=voClass;this.groupNode=groupNode;this.orgNode=orgNode;
        }
        boolean matchesNode(String node) { return groupNode.equals(node) || orgNode.equals(node); }
    }
}
