package nc.impl.uapbd.qd.esign;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.bs.uapbd.qd.esign.core.MappingRules;
import nc.bs.uapbd.qd.esign.core.ObjectRelations;
import nc.bs.uapbd.qd.esign.persistence.QdEsignStore;
import nc.bs.uapbd.qd.esign.security.EsignPermission;
import nc.bs.uapbd.qd.esign.security.ServerIdentity;
import nc.itf.uapbd.qd.esign.IPolicyService;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.ButtonPolicyChange;
import nc.vo.uapbd.qd.esign.ButtonPolicyRow;
import nc.vo.uapbd.qd.esign.OrganizationPolicyView;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.ObjectPolicyDecision;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.PolicyDecision;
import nc.vo.uapbd.qd.esign.PartnerOrgBatchPlan;
import nc.vo.uapbd.qd.esign.SaleOrderPilotScope;

/** 集团基础档案及业务单元策略的 CMT 服务。权限与集团均取自当前服务端会话。 */
public final class PolicyServiceImpl implements IPolicyService {
    private static final String POLICY_NODE = "QD_ES_POLICY";
    private static final String GROUP_POLICY_NODE = "QD_ES_BD_POLICY";

    @Override public OrganizationPolicyView queryGroupPolicy() throws BusinessException {
        ServerIdentity identity = ServerIdentity.current();
        EsignPermission.require(identity, GROUP_POLICY_NODE, "QUERY", identity.groupId);
        return readGroup(new QdEsignStore(), identity.groupId,
            EsignPermission.authorizedOrgs(identity, GROUP_POLICY_NODE, "SAVE", true).contains(identity.groupId),
            EsignPermission.authorizedOrgs(identity, GROUP_POLICY_NODE, "MASTER", true).contains(identity.groupId));
    }

    @Override public List<ButtonPolicyRow> saveGroupPolicies(List<ButtonPolicyChange> changes) throws BusinessException {
        ServerIdentity identity = ServerIdentity.current();
        EsignPermission.require(identity, GROUP_POLICY_NODE, "SAVE", identity.groupId);
        return saveGroupChanges(new QdEsignStore(), identity.groupId, changes);
    }

    @Override public long setGroupMaster(boolean enabled, long expected) throws BusinessException {
        ServerIdentity identity = ServerIdentity.current();
        EsignPermission.require(identity, GROUP_POLICY_NODE, "MASTER", identity.groupId);
        return new QdEsignStore().setGroupMaster(identity.groupId, enabled, expected);
    }

    @Override public List<String> listPolicyOrganizations() throws BusinessException {
        ServerIdentity identity = ServerIdentity.current();
        List<String> allowed = EsignPermission.authorizedOrgs(identity, POLICY_NODE, "QUERY", true);
        return new QdEsignStore().listActiveBusinessUnits(identity.groupId, allowed);
    }

    @Override public OrganizationPolicyView queryOrganizationPolicy(String org) throws BusinessException {
        ServerIdentity identity = ServerIdentity.current();
        QdEsignStore store = new QdEsignStore();
        requireManagement(store, identity, "QUERY", org);
        boolean canSave = EsignPermission.authorizedOrgs(identity, POLICY_NODE, "SAVE", true).contains(org);
        boolean canSaveMaster = EsignPermission.authorizedOrgs(identity, POLICY_NODE, "MASTER", true).contains(org);
        return readOrganization(store, identity.groupId, org, canSave, canSaveMaster);
    }

    @Override public List<ButtonPolicyRow> saveOrganizationPolicies(String org, List<ButtonPolicyChange> changes)
            throws BusinessException {
        ServerIdentity identity = ServerIdentity.current();
        QdEsignStore store = new QdEsignStore();
        requireManagement(store, identity, "SAVE", org);
        return saveChanges(store, identity.groupId, org, changes);
    }

    @Override public long setOrganizationMaster(String org, boolean enabled, long expected) throws BusinessException {
        ServerIdentity identity = ServerIdentity.current();
        QdEsignStore store = new QdEsignStore();
        requireManagement(store, identity, "MASTER", org);
        return store.setOrganizationMaster(identity.groupId, org, enabled, expected);
    }

    @Override public PolicyDecision getPolicyForManagement(String org, String node, String button)
            throws BusinessException {
        ServerIdentity identity = ServerIdentity.current();
        QdEsignStore store = new QdEsignStore();
        requireManagement(store, identity, "QUERY", org);
        requireOrganizationMapping(store.loadNodeByAction(node, button));
        return resolve(store, identity.groupId, org, node, button);
    }

    @Override public PolicyDecision resolve(String org, String node, String button) throws BusinessException {
        ServerIdentity identity = ServerIdentity.current();
        EsignPermission.requireBusiness(identity, node, button, org);
        return resolveFor(identity, org, node, button);
    }

    @Override public List<ObjectPolicyDecision> resolveForObjects(OperationContext context) throws BusinessException {
        ServerIdentity identity = ServerIdentity.current();
        EsignValues.require(context != null && identity.groupId.equals(context.getGroupId()), "Group mismatch");
        EsignPermission.requireBusiness(identity, context.getNodeCode(), context.getButtonCode(), context.getOrgId());
        QdEsignStore store = new QdEsignStore();
        Map<String,Object> mapping = store.loadNodeByAction(context.getNodeCode(), context.getButtonCode());
        EsignValues.require(mapping != null && hasText(mapping, "group_scope_id"), "Not a base-data action");
        MappingRules.requireDomain(mapping, context.getOperationType(), context.getObjects());
        if(nc.bs.uapbd.qd.esign.core.BankAccountRoute.isAdapter(mapping))
            context=expandBankAccountRequest(identity,context,mapping);
        if(nc.bs.uapbd.qd.esign.core.PartnerMainSaveRoute.isAdapter(mapping))
            context=expandPartnerMainRequest(identity,context,mapping);
        if(nc.bs.uapbd.qd.esign.core.PartnerMainDeleteRoute.isMapping(mapping))
            context=expandPartnerMainDeleteRequest(identity,context,mapping);
        if(nc.bs.uapbd.qd.esign.core.PartnerBatchRoute.isAdapter(mapping))
            context=expandPartnerBatchRequest(identity,context,mapping);
        if(nc.bs.uapbd.qd.esign.core.CustomerAssignmentRoute.isAdapter(mapping))
            context=expandCustomerAssignmentRequest(identity,context,mapping);
        if(nc.bs.uapbd.qd.esign.core.SupplierAssignmentRoute.isAdapter(mapping))
            context=expandSupplierAssignmentRequest(identity,context,mapping);
        if(nc.bs.uapbd.qd.esign.core.MaterialCancelAssignmentRoute.isAdapter(mapping))
            context=expandMaterialCancelAssignmentRequest(identity,context,mapping);
        List<ObjectPolicyDecision> decisions = resolveOwnerPolicies(store, identity.groupId, context, mapping, false);
        requireTransferPermissions(identity, context, decisions);
        return decisions;
    }

    /** The plan freezes page identities only; each slice is separately authorized and rechecked before execution. */
    @Override public PartnerOrgBatchPlan planOrganizationBatch(OperationContext context) throws BusinessException {
        ServerIdentity identity=ServerIdentity.current();
        EsignValues.require(identity!=null && identity.groupId!=null && context!=null && identity.groupId.equals(context.getGroupId())
            && context.getPartnerBatchDraft()!=null
            && context.getPartnerBatchDraft().getTargetOrgs().length>0 && !context.getPartnerBatchDraft().hasPageSelection(),
            "组织批改分批计划缺少完整的档案或目标选择。");
        EsignPermission.requireBusiness(identity,context.getNodeCode(),context.getButtonCode(),context.getOrgId());
        QdEsignStore store=new QdEsignStore();
        Map<String,Object> mapping=store.loadNodeByAction(context.getNodeCode(),context.getButtonCode());
        MappingRules.requireDomain(mapping,context.getOperationType(),context.getObjects());
        OperationContext normalized=normalizePartnerBatchRequest(identity,context,mapping);
        return nc.bs.uapbd.qd.esign.core.PartnerOrgBatchScope.plan(identity,normalized,normalized.getPartnerBatchDraft());
    }

    /** The client identifies the selected relation or the parent of a new account. */
    static OperationContext expandBankAccountRequest(ServerIdentity identity,OperationContext draft,Map<String,Object> mapping)
            throws BusinessException {
        nc.bs.uapbd.qd.esign.core.BankAccountRoute.requireMapping(mapping,draft.getOperationType());
        EsignValues.require(OperationContext.OWNER_POLICY_PROTOCOL.equals(draft.getPolicyProtocol()) && draft.getObjects().size()==1
            && draft.getObjects().get(0).getTargetOrg()==null,"银行账户操作只能选择一个账户或父档案。");
        BusinessObjectRef selected=draft.getObjects().get(0);
        boolean insert="INSERT".equals(draft.getOperationType());
        nc.bs.uapbd.qd.esign.core.BankAccountRequestScope.Expanded impact=insert?null:
            nc.bs.uapbd.qd.esign.core.BankAccountRequestScope.expand(identity.groupId,selected,
                nc.bs.uapbd.qd.esign.core.BankAccountRequestScope.database(),false);
        List<BusinessObjectRef> affected=insert?nc.bs.uapbd.qd.esign.core.BankAccountRequestScope.expandInsert(identity.groupId,selected,false)
            :impact.getObjects();
        OperationContext expanded=new OperationContext(draft.getRequestId(),draft.getBatchId(),draft.getGroupId(),draft.getOrgId(),
            draft.getNodeCode(),draft.getNodeName(),draft.getButtonCode(),draft.getButtonName(),draft.getOperationType(),
            draft.getReason(),affected,draft.getPolicyProtocol());
        MappingRules.requireDomain(mapping,expanded.getOperationType(),expanded.getObjects());
        requireBankAccountPermission(identity,expanded,impact);
        return expanded;
    }

    private static void requireBankAccountPermission(ServerIdentity identity,OperationContext context,
            nc.bs.uapbd.qd.esign.core.BankAccountRequestScope.Expanded impact) throws BusinessException {
        BusinessObjectRef root=nc.bs.uapbd.qd.esign.core.BankAccountRequestScope.root(context.getObjects());
        String operation=context.getOperationType(),node=context.getNodeCode();
        nc.bs.uapbd.qd.esign.core.BankAccountRequestScope.requireDefaultCurrency(operation,root,impact==null?null:impact.getSelection());
        String resource=nc.bs.uapbd.qd.esign.core.BankAccountRoute.permissionResource(node);
        String nativeOperation=operation.toLowerCase(java.util.Locale.ENGLISH);
        if("INSERT".equals(operation)) {
            resource="10140CBA".equals(node)?"customer":"supplier";
            nativeOperation="edit";
        } else if("UPDATE".equals(operation)) {
            nativeOperation="edit";
        } else if("ENABLE".equals(operation) || "DISABLE".equals(operation)) {
            // State buttons pass the union VO to the native data permission check.
            root=new BusinessObjectRef("bank-permission",root.getObjectType(),impact.getSelection().getAccount(),null,null);
        }
        EsignPermission.requireArchiveData(identity,resource,nativeOperation,java.util.Collections.singletonList(root));
    }

    /** Bind a candidate main-save authorization to its proposed parent and native affected structure. */
    static OperationContext expandPartnerMainRequest(ServerIdentity identity,OperationContext draft,Map<String,Object> mapping)
            throws BusinessException {
        nc.bs.uapbd.qd.esign.core.PartnerMainSaveRoute.requireMapping(mapping,draft.getOperationType());
        EsignValues.require(OperationContext.OWNER_POLICY_PROTOCOL.equals(draft.getPolicyProtocol()) && draft.getObjects().size()==1
            && draft.getObjects().get(0).getTargetOrg()==null && draft.getPartnerMainSaveDraft()!=null,
            "客户或供应商保存缺少所选主档或影响范围。");
        BusinessObjectRef selected=draft.getObjects().get(0);
        boolean insert="INSERT".equals(draft.getOperationType());
        EsignValues.require(!insert || !draft.getPartnerMainSaveDraft().hasCounterpart(),"新增时关联现有档案需要单独核实影响范围。");
        List<BusinessObjectRef> affected=insert?nc.bs.uapbd.qd.esign.core.PartnerMainRequestScope.expandInsert(identity.groupId,selected,
                draft.getPartnerMainSaveDraft().getParent(),nc.bs.uapbd.qd.esign.core.PartnerMainSaveImpact.database(),false)
            :nc.bs.uapbd.qd.esign.core.PartnerMainRequestScope.expand(identity.groupId,selected,draft.getPartnerMainSaveDraft().getParent(),
                draft.getPartnerMainSaveDraft().hasCounterpart(),nc.bs.uapbd.qd.esign.core.PartnerMainSaveImpact.database(),false).getObjects();
        requirePartnerMainPermission(identity,draft.getNodeCode(),affected);
        OperationContext expanded=new OperationContext(draft.getRequestId(),draft.getBatchId(),draft.getGroupId(),draft.getOrgId(),
            draft.getNodeCode(),draft.getNodeName(),draft.getButtonCode(),draft.getButtonName(),draft.getOperationType(),draft.getReason(),
            affected,draft.getPolicyProtocol(),null,null,draft.getPartnerMainSaveDraft());
        MappingRules.requireDomain(mapping,expanded.getOperationType(),expanded.getObjects());return expanded;
    }

    private static void requirePartnerMainPermission(ServerIdentity identity,String node,List<BusinessObjectRef> refs)
            throws BusinessException {
        BusinessObjectRef root=nc.bs.uapbd.qd.esign.core.PartnerMainRequestScope.root(refs);
        List<BusinessObjectRef> checked=new ArrayList<BusinessObjectRef>();
        if(root.getObjectPk()!=null) checked.add(root);
        else for(BusinessObjectRef ref:refs) if(ref.getObjectPk()!=null) checked.add(ref);
        if(!checked.isEmpty()) EsignPermission.requireArchiveData(identity,
            nc.bs.uapbd.qd.esign.core.PartnerMainSaveRoute.permissionResource(node),"edit",checked);
    }

    /** Expand selected master deletes to server-derived structure and paired-master policy objects. */
    static OperationContext expandPartnerMainDeleteRequest(ServerIdentity identity,OperationContext draft,Map<String,Object> mapping)
            throws BusinessException {
        nc.bs.uapbd.qd.esign.core.PartnerMainDeleteRoute.requireMapping(mapping,draft.getOperationType());
        EsignValues.require(OperationContext.OWNER_POLICY_PROTOCOL.equals(draft.getPolicyProtocol()) && !draft.getObjects().isEmpty(),
            "客户或供应商删除缺少所选档案。");
        for(BusinessObjectRef ref:draft.getObjects()) EsignValues.require(ref!=null && ref.getParentClientObjectId()==null
            && ref.getDesiredOwnerOrg()==null && ref.getTargetOrg()==null,"客户或供应商删除只能提交选中主档。");
        String domain=nc.bs.uapbd.qd.esign.core.PartnerMainDeleteRoute.domain(draft.getNodeCode());
        List<BusinessObjectRef> affected=nc.bs.uapbd.qd.esign.core.PartnerMainDeleteScope.expand(identity.groupId,draft.getOrgId(),domain,
            draft.getObjects(),PartnerMainDeleteData.reader(draft.getNodeCode()),false);
        EsignValues.require(affected.size()<=1000,"客户或供应商删除影响对象超过本次授权上限。");
        requirePartnerMainDeletePermission(identity,draft.getNodeCode(),affected);
        OperationContext expanded=new OperationContext(draft.getRequestId(),draft.getBatchId(),draft.getGroupId(),draft.getOrgId(),
            draft.getNodeCode(),draft.getNodeName(),draft.getButtonCode(),draft.getButtonName(),draft.getOperationType(),draft.getReason(),
            affected,draft.getPolicyProtocol());
        MappingRules.requireDomain(mapping,expanded.getOperationType(),expanded.getObjects());return expanded;
    }

    private static void requirePartnerMainDeletePermission(ServerIdentity identity,String node,List<BusinessObjectRef> refs)
            throws BusinessException {
        List<BusinessObjectRef> selected=new ArrayList<BusinessObjectRef>(),customers=new ArrayList<BusinessObjectRef>(),
            suppliers=new ArrayList<BusinessObjectRef>();
        for(BusinessObjectRef ref:refs) {
            if(ref.getParentClientObjectId()==null) selected.add(ref);
            else if("BD_CUSTOMER".equals(ref.getObjectType())) customers.add(ref);
            else if("BD_SUPPLIER".equals(ref.getObjectType())) suppliers.add(ref);
        }
        EsignValues.require(!selected.isEmpty(),"客户或供应商删除缺少选中主档。");
        EsignPermission.requireArchiveData(identity,nc.bs.uapbd.qd.esign.core.PartnerMainDeleteRoute.permissionResource(node),"delete",selected);
        if(!customers.isEmpty()) EsignPermission.requireArchiveData(identity,"customer","edit",customers);
        if(!suppliers.isEmpty()) EsignPermission.requireArchiveData(identity,"supplier","edit",suppliers);
    }

    /** Candidate: normalize selected primary archives and bind values from the server-verified native tab. */
    static OperationContext expandPartnerBatchRequest(ServerIdentity identity,OperationContext draft,Map<String,Object> mapping)
            throws BusinessException {
        return expandPartnerBatch(identity,draft,mapping).context;
    }
    static final class PartnerBatchExpansion {
        final OperationContext context;final nc.bs.uapbd.qd.esign.core.PartnerOrgBatchScope.Prepared organization;
        PartnerBatchExpansion(OperationContext context,nc.bs.uapbd.qd.esign.core.PartnerOrgBatchScope.Prepared organization) {
            this.context=context;this.organization=organization;
        }
    }
    static PartnerBatchExpansion expandPartnerBatch(ServerIdentity identity,OperationContext draft,Map<String,Object> mapping)
            throws BusinessException {
        OperationContext normalized=normalizePartnerBatchRequest(identity,draft,mapping);
        String domain=nc.bs.uapbd.qd.esign.core.PartnerBatchRoute.domain(normalized.getNodeCode());
        nc.vo.uapbd.qd.esign.PartnerBatchDraft payload=normalized.getPartnerBatchDraft();
        nc.bs.uapbd.qd.esign.core.PartnerOrgBatchScope.Prepared organization=null;List<BusinessObjectRef> bound;
        if(payload.getTargetOrgs().length>0) {
            EsignValues.require(payload.hasPageSelection(),"组织页签批改必须先规划并固定本批页签行。");
            organization=nc.bs.uapbd.qd.esign.core.PartnerOrgBatchScope.prepare(identity,normalized,payload);
            bound=organization.getObjects();
        } else {
            nc.bs.uapbd.qd.esign.core.NativeBatchChangeSet changes=
                nc.bs.uapbd.qd.esign.core.PartnerBatchPayload.resolve(domain,payload);
            bound=nc.bs.uapbd.qd.esign.core.NativeBatchRequestScope.bind(identity.groupId,normalized.getOrgId(),
                domain,normalized.getObjects(),changes);
        }
        OperationContext expanded=new OperationContext(normalized.getRequestId(),normalized.getBatchId(),normalized.getGroupId(),normalized.getOrgId(),
            normalized.getNodeCode(),normalized.getNodeName(),normalized.getButtonCode(),normalized.getButtonName(),normalized.getOperationType(),
            normalized.getReason(),bound,normalized.getPolicyProtocol(),null,null,null,normalized.getPartnerBatchDraft());
        MappingRules.requireDomain(mapping,expanded.getOperationType(),expanded.getObjects());return new PartnerBatchExpansion(expanded,organization);
    }

    private static OperationContext normalizePartnerBatchRequest(ServerIdentity identity,OperationContext draft,Map<String,Object> mapping)
            throws BusinessException {
        EsignValues.require(identity!=null && draft!=null && identity.groupId!=null && identity.groupId.equals(draft.getGroupId()),
            "快速批改的集团或身份不正确。");
        nc.bs.uapbd.qd.esign.core.PartnerBatchRoute.requireMapping(mapping,draft.getOperationType());
        EsignValues.require(draft.getNodeCode().equals(EsignValues.text(mapping,"node_code"))
            && draft.getButtonCode().equals(EsignValues.text(mapping,"button_code")),"快速批改映射与请求入口不一致。");
        String domain=nc.bs.uapbd.qd.esign.core.PartnerBatchRoute.domain(draft.getNodeCode());
        EsignValues.require(OperationContext.OWNER_POLICY_PROTOCOL.equals(draft.getPolicyProtocol()) && draft.getPartnerBatchDraft()!=null
            && !draft.getObjects().isEmpty() && draft.getObjects().size()<=nc.vo.uapbd.qd.esign.PartnerBatchDraft.MAX_SINGLE_TRANSACTION_OBJECTS,
            "快速批改缺少所选档案、字段值或超过单次规划上限。");
        Set<String> pks=new HashSet<String>(),clients=new HashSet<String>();
        for(BusinessObjectRef ref:draft.getObjects()) EsignValues.require(ref!=null && domain.equals(ref.getObjectType())
            && ref.getObjectPk()!=null && ref.getObjectPk().matches("[A-Za-z0-9_-]{1,20}") && pks.add(ref.getObjectPk())
            && ref.getClientObjectId()!=null && clients.add(ref.getClientObjectId()) && ref.getParentClientObjectId()==null
            && ref.getDesiredOwnerOrg()==null && ref.getTargetOrg()==null,"快速批改所选档案不正确或重复。");
        QdEsignStore store=new QdEsignStore();List<BusinessObjectRef> normalized=new ArrayList<BusinessObjectRef>();
        for(BusinessObjectRef ref:draft.getObjects()) {
            Map<String,Object> saved=store.loadBaseArchiveOwner(identity.groupId,domain,ref.getObjectPk(),false);
            EsignValues.require(saved!=null && EsignValues.text(saved,"code")!=null && EsignValues.text(saved,"pk_org")!=null,
                "无法确定快速批改档案或所属组织。");
            normalized.add(new BusinessObjectRef(ref.getClientObjectId(),domain,ref.getObjectPk(),EsignValues.text(saved,"code"),null));
        }
        EsignPermission.requireArchiveData(identity,nc.bs.uapbd.qd.esign.core.PartnerBatchRoute.permissionResource(draft.getNodeCode()),
            "batchupdate",normalized);
        OperationContext expanded=new OperationContext(draft.getRequestId(),draft.getBatchId(),draft.getGroupId(),draft.getOrgId(),
            draft.getNodeCode(),draft.getNodeName(),draft.getButtonCode(),draft.getButtonName(),draft.getOperationType(),draft.getReason(),normalized,
            draft.getPolicyProtocol(),null,null,null,draft.getPartnerBatchDraft());
        MappingRules.requireDomain(mapping,expanded.getOperationType(),expanded.getObjects());return expanded;
    }

    /** Candidate: normalize explicit customer-by-target selections, then add NC's derived credit regions. */
    static OperationContext expandCustomerAssignmentRequest(ServerIdentity identity,OperationContext draft,Map<String,Object> mapping)
            throws BusinessException {
        nc.bs.uapbd.qd.esign.core.CustomerAssignmentRoute.requireMapping(mapping,draft.getOperationType());
        EsignValues.require(OperationContext.OWNER_POLICY_PROTOCOL.equals(draft.getPolicyProtocol()) && !draft.getObjects().isEmpty(),
            "客户分配缺少档案或目标组织。");
        QdEsignStore store=new QdEsignStore();Map<String,BusinessObjectRef> archives=new java.util.TreeMap<String,BusinessObjectRef>();
        Set<String> targets=new java.util.TreeSet<String>(),pairs=new java.util.HashSet<String>();
        for(BusinessObjectRef ref:draft.getObjects()) {
            EsignValues.require(ref!=null && "BD_CUSTOMER".equals(ref.getObjectType()) && ref.getObjectPk()!=null
                && ref.getTargetOrg()!=null && ref.getParentClientObjectId()==null && ref.getDesiredOwnerOrg()==null
                && pairs.add(ref.getObjectPk()+"\u0000"+ref.getTargetOrg()),"客户分配档案或目标重复、无效。");
            targets.add(ref.getTargetOrg());
            if(!archives.containsKey(ref.getObjectPk())) {
                Map<String,Object> saved=store.loadBaseArchiveOwner(identity.groupId,"BD_CUSTOMER",ref.getObjectPk(),false);
                EsignValues.require(saved!=null && EsignValues.text(saved,"code")!=null && EsignValues.text(saved,"pk_org")!=null,
                    "无法核实客户档案或所属组织。");
                archives.put(ref.getObjectPk(),new BusinessObjectRef(ref.getClientObjectId(),"BD_CUSTOMER",ref.getObjectPk(),
                    EsignValues.text(saved,"code"),null));
            }
        }
        EsignValues.require((long)archives.size()*targets.size()==pairs.size(),"客户分配所选档案和目标组织的组合不完整。");
        nc.bs.uapbd.qd.esign.core.CustomerAssignmentRequestScope.Expanded scope=
            nc.bs.uapbd.qd.esign.core.CustomerAssignmentRequestScope.expand(identity.groupId,draft.getOrgId(),draft.getOperationType(),
                new ArrayList<BusinessObjectRef>(archives.values()),new ArrayList<String>(targets),
                nc.bs.uapbd.qd.esign.core.CustomerAssignmentTargets.database(),
                nc.bs.uapbd.qd.esign.core.CustomerAssignmentOrder.database(),CustomerAssignmentRoutePlan.reader());
        OperationContext expanded=new OperationContext(draft.getRequestId(),draft.getBatchId(),draft.getGroupId(),draft.getOrgId(),
            draft.getNodeCode(),draft.getNodeName(),draft.getButtonCode(),draft.getButtonName(),draft.getOperationType(),
            draft.getReason(),scope.getUnits(),draft.getPolicyProtocol());
        MappingRules.requireDomain(mapping,expanded.getOperationType(),expanded.getObjects());return expanded;
    }

    static OperationContext expandSupplierAssignmentRequest(ServerIdentity identity,OperationContext draft,Map<String,Object> mapping)
            throws BusinessException {
        nc.bs.uapbd.qd.esign.core.SupplierAssignmentRoute.requireMapping(mapping,draft.getOperationType());
        EsignValues.require(OperationContext.OWNER_POLICY_PROTOCOL.equals(draft.getPolicyProtocol()) && !draft.getObjects().isEmpty(),
            "供应商分配缺少档案或目标组织。");
        QdEsignStore store=new QdEsignStore();Map<String,BusinessObjectRef> archives=new java.util.TreeMap<String,BusinessObjectRef>();
        Set<String> targets=new java.util.TreeSet<String>(),pairs=new java.util.HashSet<String>();
        for(BusinessObjectRef ref:draft.getObjects()) {
            EsignValues.require(ref!=null && "BD_SUPPLIER".equals(ref.getObjectType()) && ref.getObjectPk()!=null
                && ref.getTargetOrg()!=null && ref.getParentClientObjectId()==null && ref.getDesiredOwnerOrg()==null
                && pairs.add(ref.getObjectPk()+"\u0000"+ref.getTargetOrg()),"供应商分配档案或目标重复、无效。");
            targets.add(ref.getTargetOrg());
            if(!archives.containsKey(ref.getObjectPk())) {
                Map<String,Object> saved=store.loadBaseArchiveOwner(identity.groupId,"BD_SUPPLIER",ref.getObjectPk(),false);
                EsignValues.require(saved!=null && EsignValues.text(saved,"code")!=null && EsignValues.text(saved,"pk_org")!=null,
                    "无法核实供应商档案或所属组织。");
                archives.put(ref.getObjectPk(),new BusinessObjectRef(ref.getClientObjectId(),"BD_SUPPLIER",ref.getObjectPk(),
                    EsignValues.text(saved,"code"),null));
            }
        }
        EsignValues.require((long)archives.size()*targets.size()==pairs.size(),"供应商分配所选档案和目标组织的组合不完整。");
        nc.bs.uapbd.qd.esign.core.SupplierAssignmentRequestScope.Expanded scope=
            nc.bs.uapbd.qd.esign.core.SupplierAssignmentRequestScope.expand(identity.groupId,draft.getOrgId(),draft.getOperationType(),
                new ArrayList<BusinessObjectRef>(archives.values()),new ArrayList<String>(targets),
                nc.bs.uapbd.qd.esign.core.SupplierAssignmentRequestScope.database(SupplierAssignmentRoutePlan.reader()));
        OperationContext expanded=new OperationContext(draft.getRequestId(),draft.getBatchId(),draft.getGroupId(),draft.getOrgId(),
            draft.getNodeCode(),draft.getNodeName(),draft.getButtonCode(),draft.getButtonName(),draft.getOperationType(),
            draft.getReason(),scope.getUnits(),draft.getPolicyProtocol());
        MappingRules.requireDomain(mapping,expanded.getOperationType(),expanded.getObjects());return expanded;
    }

    static OperationContext expandMaterialCancelAssignmentRequest(ServerIdentity identity,OperationContext draft,Map<String,Object> mapping)
            throws BusinessException {
        nc.bs.uapbd.qd.esign.core.MaterialCancelAssignmentRoute.requireMapping(mapping,draft.getOperationType());
        EsignValues.require(OperationContext.OWNER_POLICY_PROTOCOL.equals(draft.getPolicyProtocol()) && !draft.getObjects().isEmpty(),
            "物料分配缺少档案或目标组织。");
        QdEsignStore store=new QdEsignStore();Map<String,BusinessObjectRef> archives=new java.util.TreeMap<String,BusinessObjectRef>();
        Set<String> targets=new java.util.TreeSet<String>(),pairs=new java.util.HashSet<String>();
        for(BusinessObjectRef ref:draft.getObjects()) {
            EsignValues.require(ref!=null && "BD_MATERIAL".equals(ref.getObjectType()) && ref.getObjectPk()!=null
                && ref.getTargetOrg()!=null && ref.getParentClientObjectId()==null && ref.getDesiredOwnerOrg()==null
                && pairs.add(ref.getObjectPk()+"\u0000"+ref.getTargetOrg()),"物料分配档案或目标重复、无效。");
            targets.add(ref.getTargetOrg());
            if(!archives.containsKey(ref.getObjectPk())) {
                Map<String,Object> saved=store.loadBaseArchiveOwner(identity.groupId,"BD_MATERIAL",ref.getObjectPk(),false);
                EsignValues.require(saved!=null && EsignValues.text(saved,"code")!=null && EsignValues.text(saved,"pk_org")!=null,
                    "无法核实物料档案或所属组织。");
                archives.put(ref.getObjectPk(),new BusinessObjectRef(ref.getClientObjectId(),"BD_MATERIAL",ref.getObjectPk(),
                    EsignValues.text(saved,"code"),null));
            }
        }
        EsignValues.require((long)archives.size()*targets.size()==pairs.size(),"物料分配所选档案和目标组织的组合不完整。");
        nc.bs.uapbd.qd.esign.core.MaterialCancelAssignmentRequestScope.Expanded scope=
            nc.bs.uapbd.qd.esign.core.MaterialCancelAssignmentRequestScope.expand(identity.groupId,draft.getOrgId(),draft.getOperationType(),
                new ArrayList<BusinessObjectRef>(archives.values()),new ArrayList<String>(targets),
                nc.bs.uapbd.qd.esign.core.MaterialCancelAssignmentRequestScope.database(MaterialCancelAssignmentRoutePlan.reader()));
        OperationContext expanded=new OperationContext(draft.getRequestId(),draft.getBatchId(),draft.getGroupId(),draft.getOrgId(),
            draft.getNodeCode(),draft.getNodeName(),draft.getButtonCode(),draft.getButtonName(),draft.getOperationType(),
            draft.getReason(),scope.getUnits(),draft.getPolicyProtocol());
        MappingRules.requireDomain(mapping,expanded.getOperationType(),expanded.getObjects());return expanded;
    }

    /** 签名准备、确认及业务执行均由已登记的节点动作确定策略范围。 */
    public static PolicyDecision resolveFor(ServerIdentity identity, String org, String node, String button)
            throws BusinessException {
        return resolveLegacy(new QdEsignStore(), identity.groupId, org, node, button);
    }

    static PolicyDecision resolveLegacy(QdEsignStore store, String group, String org, String node, String button)
            throws BusinessException {
        Map<String,Object> mapping = store.loadNodeByAction(node, button);
        EsignValues.require(mapping == null || !hasText(mapping, "group_scope_id"),
            "基础档案策略需要档案对象，请升级电子签名客户端。");
        return resolve(store, group, org, node, button);
    }

    @Override public void save(String org, String node, String button, boolean sign, boolean reason,
                               boolean audit, long expected) throws BusinessException {
        ServerIdentity identity = ServerIdentity.current();
        QdEsignStore store = new QdEsignStore();
        requireManagement(store, identity, "SAVE", org);
        Map<String,Object> mapping = store.loadNodeByAction(node, button);
        requireOrganizationMapping(mapping);
        requireConfigurable(mapping);
        saveButton(store, identity.groupId, org, mapping,
            new ButtonPolicyChange(EsignValues.text(mapping, "scope_id"), sign, reason, audit, expected));
    }

    @Deprecated
    @Override public void setMasterEnabled(boolean enabled) throws BusinessException {
        throw new BusinessException("请升级电子签名客户端并选择业务单元，再保存组织总控。");
    }

    private static void requireManagement(QdEsignStore store, ServerIdentity identity, String action, String org)
            throws BusinessException {
        EsignValues.require(org != null && org.trim().length() > 0, "请选择业务单元。");
        EsignPermission.require(identity, POLICY_NODE, action, org);
        EsignValues.require(store.isActiveBusinessUnit(identity.groupId, org), "请选择当前集团有效的业务单元。");
    }

    static PolicyDecision resolve(QdEsignStore store, String group, String org, String node, String button)
            throws BusinessException {
        Map<String,Object> row = store.loadPolicyWithControl(group, org, node, button);
        if (row == null) { return new PolicyDecision(false, false, false, false, false, 0L, 0L); }
        boolean active = EsignValues.yes(row, "enabled");
        return new PolicyDecision(EsignValues.yes(row, "master_enabled"),
            active && EsignValues.yes(row, "sign_enabled"), active && EsignValues.yes(row, "reason_required"),
            active && EsignValues.yes(row, "audit_enabled"), active && EsignValues.yes(row, "validated"),
            EsignValues.number(row, "policy_version"), EsignValues.number(row, "control_version"));
    }

    /** 基础档案的策略组织从服务端主档和已验证分配关系获取。 */
    static List<ObjectPolicyDecision> resolveOwnerPolicies(QdEsignStore store, String group,
            OperationContext context, Map<String,Object> mapping, boolean lock) throws BusinessException {
        EsignValues.require(context != null && group != null && group.equals(context.getGroupId())
            && context.getOrgId() != null && mapping != null && hasText(mapping, "group_scope_id"),
            "基础档案策略缺少集团、操作组织或正式动作。");
        EsignValues.require(group.equals(context.getOrgId()) || store.isBusinessUnitInGroup(group, context.getOrgId()),
            "实际操作组织不属于当前集团的业务单元。");
        String mappingScope = EsignValues.text(mapping, "group_scope_id");
        boolean bankAccount=nc.bs.uapbd.qd.esign.core.BankAccountRoute.isAdapter(mapping);
        boolean bankInsert=bankAccount && "INSERT".equals(context.getOperationType());
        boolean partnerMain=nc.bs.uapbd.qd.esign.core.PartnerMainSaveRoute.isAdapter(mapping);
        boolean partnerDelete=nc.bs.uapbd.qd.esign.core.PartnerMainDeleteRoute.isMapping(mapping);
        boolean customerAssignment=nc.bs.uapbd.qd.esign.core.CustomerAssignmentRoute.isAdapter(mapping);
        boolean supplierAssignment=nc.bs.uapbd.qd.esign.core.SupplierAssignmentRoute.isAdapter(mapping);
        boolean materialCancelAssignment=nc.bs.uapbd.qd.esign.core.MaterialCancelAssignmentRoute.isAdapter(mapping);
        if(bankAccount) nc.bs.uapbd.qd.esign.core.BankAccountRoute.requireMapping(mapping,context.getOperationType());
        if(partnerMain) nc.bs.uapbd.qd.esign.core.PartnerMainSaveRoute.requireMapping(mapping,context.getOperationType());
        if(partnerDelete) nc.bs.uapbd.qd.esign.core.PartnerMainDeleteRoute.requireMapping(mapping,context.getOperationType());
        if(customerAssignment) nc.bs.uapbd.qd.esign.core.CustomerAssignmentRoute.requireMapping(mapping,context.getOperationType());
        if(supplierAssignment) nc.bs.uapbd.qd.esign.core.SupplierAssignmentRoute.requireMapping(mapping,context.getOperationType());
        if(materialCancelAssignment) nc.bs.uapbd.qd.esign.core.MaterialCancelAssignmentRoute.requireMapping(mapping,context.getOperationType());
        nc.bs.uapbd.qd.esign.core.BankAccountPolicyResolver.Lookup relatedPolicies=bankAccount || partnerMain || partnerDelete
            ?nc.bs.uapbd.qd.esign.core.BankAccountPolicyResolver.database(store):null;
        List<ObjectPolicyDecision> initial = lock ? resolveOwnerPolicies(store, group, context, mapping, false) : null;
        Map<String,ObjectPolicyDecision> initialById = new java.util.HashMap<String,ObjectPolicyDecision>();
        if (initial != null) {
            Set<String> owners = new java.util.TreeSet<String>();
            for (ObjectPolicyDecision decision : initial) { owners.add(decision.getOwnerOrg()); initialById.put(decision.getClientObjectId(), decision); }
            for (String owner : owners) { store.lockBaseOwner(group, owner); }
        }
        List<BusinessObjectRef> ordered = new ArrayList<BusinessObjectRef>(context.getObjects());
        if (lock) java.util.Collections.sort(ordered, new java.util.Comparator<BusinessObjectRef>() {
            public int compare(BusinessObjectRef a, BusinessObjectRef b) {
                String ak=a.getObjectType()+"\u0000"+(a.getObjectPk()==null?a.getClientObjectId():a.getObjectPk())
                    +"\u0000"+(a.getTargetOrg()==null?"":a.getTargetOrg());
                String bk=b.getObjectType()+"\u0000"+(b.getObjectPk()==null?b.getClientObjectId():b.getObjectPk())
                    +"\u0000"+(b.getTargetOrg()==null?"":b.getTargetOrg());
                return ak.compareTo(bk);
            }
        });
        Map<String,ObjectPolicyDecision> results = new java.util.HashMap<String,ObjectPolicyDecision>();
        for (BusinessObjectRef object : ordered) {
            String owner, root = null, target = context.getOrgId();
            if (customerAssignment) {
                EsignValues.require("BD_CUSTOMER".equals(object.getObjectType()) && object.getTargetOrg()!=null,
                    "客户分配缺少目标组织。");target=object.getTargetOrg();
            } else if(supplierAssignment) {
                EsignValues.require("BD_SUPPLIER".equals(object.getObjectType()) && object.getTargetOrg()!=null,
                    "供应商分配缺少目标组织。");target=object.getTargetOrg();
            } else if(materialCancelAssignment) {
                EsignValues.require("BD_MATERIAL".equals(object.getObjectType()) && object.getTargetOrg()!=null,
                    "物料取消分配缺少目标组织。");target=object.getTargetOrg();
            } else if ("ASSIGN".equals(context.getOperationType())) {
                EsignValues.require("BD_MATERIAL".equals(object.getObjectType()) && object.getTargetOrg() != null
                    && !object.getTargetOrg().trim().isEmpty(), "物料分配缺少目标组织。");
                target = object.getTargetOrg();
            }
            if (nc.vo.uapbd.qd.esign.ArchiveBatchResult.isSupplierFreeze(context.getNodeCode(),
                    context.getButtonCode(),context.getOperationType())) {
                EsignValues.require("BD_SUPPLIER".equals(object.getObjectType()) && object.getTargetOrg()!=null,
                    "供应商冻结缺少目标组织。");
                target=object.getTargetOrg();
            }
            if ("INSERT".equals(context.getOperationType()) && object.getObjectPk() == null) {
                owner = object.getDesiredOwnerOrg();
                nc.bs.uapbd.qd.esign.core.BaseArchiveOwnerType type =
                    nc.bs.uapbd.qd.esign.core.BaseArchiveOwnerType.forDomain(object.getObjectType());
                EsignValues.require(type != null && type.isRoot(object.getObjectType()) && owner != null
                    && owner.equals(context.getOrgId()), "新增档案的所属组织与操作组织不一致。");
            } else {
                EsignValues.require(object.getObjectPk() != null, "无法确定基础档案主键。");
                String archiveDomain=bankInsert?nc.bs.uapbd.qd.esign.core.BankAccountRoute.archiveDomain(object.getObjectType()):object.getObjectType();
                Map<String,Object> stored = store.loadBaseArchiveOwner(group, archiveDomain, object.getObjectPk(), lock);
                EsignValues.require(stored != null && group.equals(EsignValues.text(stored, "pk_group")),
                    "无法核实档案所属集团或主档。");
                owner = EsignValues.text(stored, "pk_org"); root = archiveRoot(stored);
                if (EsignValues.text(stored, "target_org") != null) { target = EsignValues.text(stored, "target_org"); }
                EsignValues.require(owner != null && root != null, "无法确定档案所属组织。");
                // Only the selected relation is a native operation target. Other relations were
                // expanded and checked by the server because the physical account is shared.
                if (!group.equals(context.getOrgId()) && !owner.equals(context.getOrgId())
                        && (!(bankAccount || partnerMain || partnerDelete) || object.getParentClientObjectId()==null)) {
                    EsignValues.require(store.isBaseArchiveAssignedTo(group, archiveDomain, root, context.getOrgId(), lock),
                        "当前业务单元没有该档案的有效分配关系。");
                }
            }
            EsignValues.require(group.equals(owner) || store.isBusinessUnitInGroup(group, owner),
                "档案所属组织不属于当前集团。");
            EsignValues.require(group.equals(target) || store.isOrganizationInGroup(group, target),
                "档案目标组织不属于当前集团。");
            if (initial != null) {
                ObjectPolicyDecision earlier = initialById.get(object.getClientObjectId());
                EsignValues.require(earlier != null && earlier.getClientObjectId().equals(object.getClientObjectId())
                    && earlier.getOwnerOrg().equals(owner), "档案所属组织已变化，请重新发起操作。");
            }
            String desired = object.getDesiredOwnerOrg();
            EsignValues.require(desired == null || group.equals(desired) || store.isBusinessUnitInGroup(group, desired),
                "目标所属组织不属于当前集团。");
            String scope=bankAccount?nc.bs.uapbd.qd.esign.core.BankAccountActionScopes.scopeFor(object.getObjectType(),context.getOperationType())
                :partnerMain?nc.bs.uapbd.qd.esign.core.PartnerMainSaveRoute.scopeFor(object.getObjectType())
                :partnerDelete?nc.bs.uapbd.qd.esign.core.PartnerMainDeleteRoute.scopeFor(object.getObjectType()):mappingScope;
            PolicyDecision decision;
            if(bankAccount || partnerMain || partnerDelete) {
                decision=relatedPolicies.load(group,owner,scope);
                EsignValues.require(decision!=null && decision.isValidated(),"关联档案的正式动作尚未通过接入验证。");
            } else {
                Map<String,Object> row = store.loadOwnerBasePolicyWithControl(group, owner, context.getNodeCode(), context.getButtonCode());
                EsignValues.require(row != null && EsignValues.yes(row, "validated"), "基础档案动作尚未通过接入验证。");
                boolean configured = EsignValues.yes(row, "enabled");
                decision = new PolicyDecision(EsignValues.yes(row, "master_enabled"),
                    configured && EsignValues.yes(row, "sign_enabled"), configured && EsignValues.yes(row, "reason_required"),
                    configured && EsignValues.yes(row, "audit_enabled"), true,
                    EsignValues.number(row, "policy_version"), EsignValues.number(row, "control_version"), true);
            }
            results.put(object.getClientObjectId(), new ObjectPolicyDecision(object.getClientObjectId(), owner,
                group.equals(owner) ? "G" : "O", scope, root, target, decision));
        }
        List<ObjectPolicyDecision> result = new ArrayList<ObjectPolicyDecision>();
        for (BusinessObjectRef object : context.getObjects()) { result.add(results.get(object.getClientObjectId())); }
        return result;
    }

    static void requireTransferPermissions(ServerIdentity identity, OperationContext context,
            List<ObjectPolicyDecision> decisions) throws BusinessException {
        if (nc.vo.uapbd.qd.esign.MaterialAssignmentResult.isRoute(
                context.getNodeCode(), context.getButtonCode(), context.getOperationType())) {
            java.util.Set<String> targets = EsignPermission.authorizedAssignmentTargets(identity, context.getNodeCode(), context.getOrgId());
            for (ObjectPolicyDecision decision : decisions) {
                EsignValues.require(targets.contains(decision.getTargetOrg()), "没有目标业务单元的物料分配权限。");
            }
        }
        if (nc.vo.uapbd.qd.esign.CustomerAssignmentResult.isRoute(context.getNodeCode(),context.getButtonCode(),context.getOperationType())
                || nc.vo.uapbd.qd.esign.SupplierAssignmentResult.isRoute(context.getNodeCode(),context.getButtonCode(),context.getOperationType())
                || nc.vo.uapbd.qd.esign.MaterialCancelAssignmentResult.isRoute(context.getNodeCode(),context.getButtonCode(),context.getOperationType())) {
            java.util.Set<String> targets=EsignPermission.authorizedButtonTargets(identity,context.getNodeCode(),
                context.getButtonCode(),context.getOrgId());
            for(ObjectPolicyDecision decision:decisions)
                EsignValues.require(targets.contains(decision.getTargetOrg()),"没有档案分配目标组织的原生权限。");
            java.util.List<String> owners=EsignPermission.authorizedOrgs(identity,context.getNodeCode(),context.getButtonCode(),false);
            for(ObjectPolicyDecision decision:decisions)
                EsignValues.require(owners.contains(decision.getOwnerOrg()),"没有档案所属组织的原生分配权限。");
        }
        if (nc.vo.uapbd.qd.esign.ArchiveBatchResult.isSupplierFreeze(
                context.getNodeCode(),context.getButtonCode(),context.getOperationType())) {
            java.util.Set<String> targets=EsignPermission.authorizedButtonTargets(identity,context.getNodeCode(),
                context.getButtonCode(),context.getOrgId());
            for (ObjectPolicyDecision decision:decisions) {
                EsignValues.require(targets.contains(decision.getTargetOrg()),"没有目标组织的供应商冻结权限。");
            }
        }
        for (int i = 0; i < decisions.size(); i++) {
            String desired = context.getObjects().get(i).getDesiredOwnerOrg();
            if (desired != null && !desired.equals(decisions.get(i).getOwnerOrg())) {
                EsignPermission.requireBusiness(identity, context.getNodeCode(), context.getButtonCode(), desired);
            }
        }
    }

    static PolicyDecision aggregateOwnerPolicies(List<ObjectPolicyDecision> decisions) throws BusinessException {
        EsignValues.require(decisions != null && !decisions.isEmpty(), "基础档案策略结果为空。");
        boolean sign = false, reason = false, audit = false;
        for (ObjectPolicyDecision item : decisions) {
            PolicyDecision policy = item.getPolicy();
            EsignValues.require(policy.isValidated(), "基础档案动作尚未通过接入验证。");
            sign |= policy.isSignatureRequired(); reason |= policy.isReasonRequired(); audit |= policy.isAuditEnabled();
        }
        return new PolicyDecision(true, sign, reason, audit, true, 0L, 0L, true);
    }

    /** 对每个授权对象重新核实所属组织、正式动作、开关和版本；执行阶段同时取得所属组织锁。 */
    static PolicyDecision validateOwnerRequest(QdEsignStore store, ServerIdentity identity, Map<String,Object> request,
            Map<String,Object> mapping, List<Map<String,Object>> objects, boolean lock) throws BusinessException {
        List<BusinessObjectRef> refs = ObjectRelations.restore(objects);
        String org = EsignValues.text(request, "pk_org"), node = EsignValues.text(request, "node_code");
        String button = EsignValues.text(request, "button_code");
        EsignPermission.requireBusiness(identity, node, button, org);
        OperationContext context = new OperationContext(EsignValues.text(request, "request_id"),
            EsignValues.text(request, "batch_id"), identity.groupId, org, node, EsignValues.text(request, "node_name"),
            button, EsignValues.text(request, "button_name"), EsignValues.text(request, "operation_type"),
            EsignValues.text(request, "reason"), refs, OperationContext.OWNER_POLICY_PROTOCOL);
        MappingRules.requireDomain(mapping, context.getOperationType(), refs);
        if(nc.bs.uapbd.qd.esign.core.CustomerAssignmentRoute.isAdapter(mapping)) {
            verifyCustomerAssignmentScope(store,identity,request,mapping,refs);
        }
        if(nc.bs.uapbd.qd.esign.core.SupplierAssignmentRoute.isAdapter(mapping)) {
            verifySupplierAssignmentScope(store,identity,request,mapping,refs);
        }
        if(nc.bs.uapbd.qd.esign.core.MaterialCancelAssignmentRoute.isAdapter(mapping)) {
            verifyMaterialCancelAssignmentScope(store,identity,request,mapping,refs);
        }
        if(nc.bs.uapbd.qd.esign.core.BankAccountRoute.isAdapter(mapping)) {
            nc.bs.uapbd.qd.esign.core.BankAccountRequestScope.Expanded impact=null;
            if("INSERT".equals(context.getOperationType())) {
                nc.bs.uapbd.qd.esign.core.BankAccountRequestScope.verifyInsert(identity.groupId,refs,false);
            } else {
                impact=nc.bs.uapbd.qd.esign.core.BankAccountRequestScope.verify(identity.groupId,refs,
                    nc.bs.uapbd.qd.esign.core.BankAccountRequestScope.database(),false);
            }
            requireBankAccountPermission(identity,context,impact);
        }
        if(nc.bs.uapbd.qd.esign.core.PartnerMainSaveRoute.isAdapter(mapping)) {
            if("INSERT".equals(context.getOperationType()))
                nc.bs.uapbd.qd.esign.core.PartnerMainRequestScope.verifyInsert(identity.groupId,refs,
                    nc.bs.uapbd.qd.esign.core.PartnerMainSaveImpact.database(),false);
            else nc.bs.uapbd.qd.esign.core.PartnerMainRequestScope.verify(identity.groupId,refs,
                    nc.bs.uapbd.qd.esign.core.PartnerMainSaveImpact.database(),false);
            requirePartnerMainPermission(identity,context.getNodeCode(),refs);
        }
        if(nc.bs.uapbd.qd.esign.core.PartnerMainDeleteRoute.isMapping(mapping)) {
            nc.bs.uapbd.qd.esign.core.PartnerMainDeleteScope.verify(identity.groupId,org,
                nc.bs.uapbd.qd.esign.core.PartnerMainDeleteRoute.domain(node),refs,PartnerMainDeleteData.reader(node),false);
            requirePartnerMainDeletePermission(identity,node,refs);
        }
        List<ObjectPolicyDecision> current = resolveOwnerPolicies(store, identity.groupId, context, mapping, lock);
        requireTransferPermissions(identity, context, current);
        verifyOwnerSnapshot(current, objects);
        return aggregateOwnerPolicies(current);
    }

    static nc.bs.uapbd.qd.esign.core.CustomerAssignmentRequestScope.Expanded verifyCustomerAssignmentScope(
            QdEsignStore store,ServerIdentity identity,Map<String,Object> request,Map<String,Object> mapping,List<BusinessObjectRef> refs)
            throws BusinessException {
        String org=EsignValues.text(request,"pk_org"),operation=EsignValues.text(request,"operation_type");
        nc.bs.uapbd.qd.esign.core.CustomerAssignmentRoute.requireMapping(mapping,operation);
        EsignValues.require(refs.size()==EsignValues.number(request,"owner_object_count"),"客户分配必须复核完整授权清单。");
        nc.bs.uapbd.qd.esign.core.CustomerAssignmentRequestScope.Expanded scope=
            nc.bs.uapbd.qd.esign.core.CustomerAssignmentRequestScope.verify(identity.groupId,org,operation,refs,
                nc.bs.uapbd.qd.esign.core.CustomerAssignmentTargets.database(),
                nc.bs.uapbd.qd.esign.core.CustomerAssignmentOrder.database(),CustomerAssignmentRoutePlan.reader());
        Set<String> checked=new java.util.HashSet<String>();
        for(BusinessObjectRef ref:refs) if(checked.add(ref.getObjectPk())) {
            Map<String,Object> root=store.loadBaseArchiveOwner(identity.groupId,"BD_CUSTOMER",ref.getObjectPk(),false);
            EsignValues.require(root!=null && ref.getBillCode().equals(EsignValues.text(root,"code")),"客户档案编码已变化，请重新发起操作。");
        }
        return scope;
    }

    static nc.bs.uapbd.qd.esign.core.SupplierAssignmentRequestScope.Expanded verifySupplierAssignmentScope(
            QdEsignStore store,ServerIdentity identity,Map<String,Object> request,Map<String,Object> mapping,List<BusinessObjectRef> refs)
            throws BusinessException {
        String org=EsignValues.text(request,"pk_org"),operation=EsignValues.text(request,"operation_type");
        nc.bs.uapbd.qd.esign.core.SupplierAssignmentRoute.requireMapping(mapping,operation);
        EsignValues.require(refs.size()==EsignValues.number(request,"owner_object_count"),"供应商分配必须复核完整授权清单。");
        nc.bs.uapbd.qd.esign.core.SupplierAssignmentRequestScope.Expanded scope=
            nc.bs.uapbd.qd.esign.core.SupplierAssignmentRequestScope.verify(identity.groupId,org,operation,refs,
                nc.bs.uapbd.qd.esign.core.SupplierAssignmentRequestScope.database(SupplierAssignmentRoutePlan.reader()));
        Set<String> checked=new java.util.HashSet<String>();
        for(BusinessObjectRef ref:refs) if(checked.add(ref.getObjectPk())) {
            Map<String,Object> root=store.loadBaseArchiveOwner(identity.groupId,"BD_SUPPLIER",ref.getObjectPk(),false);
            EsignValues.require(root!=null && ref.getBillCode().equals(EsignValues.text(root,"code")),"供应商档案编码已变化，请重新发起操作。");
        }
        return scope;
    }

    static nc.bs.uapbd.qd.esign.core.MaterialCancelAssignmentRequestScope.Expanded verifyMaterialCancelAssignmentScope(
            QdEsignStore store,ServerIdentity identity,Map<String,Object> request,Map<String,Object> mapping,List<BusinessObjectRef> refs)
            throws BusinessException {
        String org=EsignValues.text(request,"pk_org"),operation=EsignValues.text(request,"operation_type");
        nc.bs.uapbd.qd.esign.core.MaterialCancelAssignmentRoute.requireMapping(mapping,operation);
        EsignValues.require(refs.size()==EsignValues.number(request,"owner_object_count"),"物料分配必须复核完整授权清单。");
        nc.bs.uapbd.qd.esign.core.MaterialCancelAssignmentRequestScope.Expanded scope=
            nc.bs.uapbd.qd.esign.core.MaterialCancelAssignmentRequestScope.verify(identity.groupId,org,operation,refs,
                nc.bs.uapbd.qd.esign.core.MaterialCancelAssignmentRequestScope.database(MaterialCancelAssignmentRoutePlan.reader()));
        Set<String> checked=new java.util.HashSet<String>();
        for(BusinessObjectRef ref:refs) if(checked.add(ref.getObjectPk())) {
            Map<String,Object> root=store.loadBaseArchiveOwner(identity.groupId,"BD_MATERIAL",ref.getObjectPk(),false);
            EsignValues.require(root!=null && ref.getBillCode().equals(EsignValues.text(root,"code")),"物料档案编码已变化，请重新发起操作。");
        }
        return scope;
    }

    /** Call after verifying the entire signed scope; recheck only the pending unit's current permissions and policy. */
    static PolicyDecision validatePartnerAssignmentUnit(QdEsignStore store,ServerIdentity identity,Map<String,Object> request,
            Map<String,Object> mapping,Map<String,Object> unit,boolean lock) throws BusinessException {
        List<BusinessObjectRef> refs=nc.bs.uapbd.qd.esign.core.ObjectRelations.restore(java.util.Collections.singletonList(unit));
        String org=EsignValues.text(request,"pk_org"),node=EsignValues.text(request,"node_code"),button=EsignValues.text(request,"button_code");
        EsignPermission.requireBusiness(identity,node,button,org);
        OperationContext context=new OperationContext(EsignValues.text(request,"request_id"),EsignValues.text(request,"batch_id"),identity.groupId,
            org,node,EsignValues.text(request,"node_name"),button,EsignValues.text(request,"button_name"),
            EsignValues.text(request,"operation_type"),EsignValues.text(request,"reason"),refs,OperationContext.OWNER_POLICY_PROTOCOL);
        MappingRules.requireDomain(mapping,context.getOperationType(),refs);
        List<ObjectPolicyDecision> current=resolveOwnerPolicies(store,identity.groupId,context,mapping,lock);
        requireTransferPermissions(identity,context,current);verifyOwnerSnapshot(current,java.util.Collections.singletonList(unit));
        return aggregateOwnerPolicies(current);
    }

    static void verifyOwnerSnapshot(List<ObjectPolicyDecision> current, List<Map<String,Object>> objects)
            throws BusinessException {
        EsignValues.require(current.size() == objects.size(), "基础档案对象范围已变化。");
        for (int i = 0; i < current.size(); i++) {
            ObjectPolicyDecision decision = current.get(i); Map<String,Object> saved = objects.get(i);
            PolicyDecision p = decision.getPolicy();
            EsignValues.require(decision.getClientObjectId().equals(EsignValues.text(saved, "client_object_id"))
                && decision.getOwnerOrg().equals(EsignValues.text(saved, "policy_owner_org"))
                && decision.getOwnerType().equals(EsignValues.text(saved, "policy_owner_type"))
                && decision.getScopeId().equals(EsignValues.text(saved, "policy_scope_id"))
                && java.util.Objects.equals(decision.getOwnerRoot(), EsignValues.text(saved, "policy_owner_root"))
                && java.util.Objects.equals(decision.getTargetOrg(), EsignValues.text(saved, "target_org"))
                && p.getVersion() == EsignValues.number(saved, "owner_policy_version")
                && p.getControlVersion() == EsignValues.number(saved, "owner_control_version")
                && p.isSignatureRequired() == EsignValues.yes(saved, "owner_sign_required")
                && p.isReasonRequired() == EsignValues.yes(saved, "owner_reason_required")
                && p.isAuditEnabled() == EsignValues.yes(saved, "owner_audit_enabled"),
                "档案所属组织或策略已变化，请重新发起操作。");
        }
    }

    static void validateSavedOwners(QdEsignStore store, String group, String operation,
            List<Map<String,Object>> objects, List<BusinessObjectRef> saved) throws BusinessException {
        if ("DELETE".equals(operation)) { return; }
        Map<String,Map<String,Object>> expected = new java.util.HashMap<String,Map<String,Object>>();
        for (Map<String,Object> row : objects) { expected.put(EsignValues.text(row, "client_object_id"), row); }
        for (BusinessObjectRef object : saved) {
            Map<String,Object> row = expected.get(object.getClientObjectId());
            EsignValues.require(row != null && object.getObjectPk() != null, "无法核实保存后的基础档案。");
            Map<String,Object> actual = store.loadBaseArchiveOwner(group, object.getObjectType(), object.getObjectPk(), false);
            String desired = EsignValues.text(row, "desired_owner_org");
            String owner = desired == null ? EsignValues.text(row, "policy_owner_org") : desired;
            EsignValues.require(actual != null && group.equals(EsignValues.text(actual, "pk_group"))
                && owner != null && owner.equals(EsignValues.text(actual, "pk_org")),
                "保存后的档案所属组织与授权不一致，业务必须回滚。");
            String oldRoot = EsignValues.text(row, "policy_owner_root");
            if (!"INSERT".equals(operation)) {
                EsignValues.require(oldRoot != null && oldRoot.equals(archiveRoot(actual)),
                    "保存后的档案主档与授权不一致。");
            }
            if (EsignValues.text(actual, "target_org") != null) {
                EsignValues.require(EsignValues.text(actual, "target_org").equals(EsignValues.text(row, "target_org")),
                    "保存后的页签目标组织与授权不一致。");
            }
        }
    }

    private static String archiveRoot(Map<String,Object> row) {
        String root = EsignValues.text(row, "pk_archive_root");
        return root == null ? EsignValues.text(row, "pk_material") : root;
    }

    static OrganizationPolicyView readGroup(QdEsignStore store, String group, boolean canSave, boolean canSaveMaster)
            throws BusinessException {
        Map<String,Object> control = store.loadGroupControl(group);
        List<ButtonPolicyRow> rows = new ArrayList<ButtonPolicyRow>();
        for (Map<String,Object> entry : store.listGroupPolicies(group)) {
            rows.add(toGroupRow(entry, EsignValues.yes(entry, "sign_enabled"), EsignValues.yes(entry, "reason_required"),
                EsignValues.yes(entry, "audit_enabled"), EsignValues.number(entry, "policy_version")));
        }
        return new OrganizationPolicyView(group, control != null && EsignValues.yes(control, "master_enabled"),
            control == null ? 0L : EsignValues.number(control, "control_version"), canSave, canSaveMaster, rows);
    }

    static OrganizationPolicyView readOrganization(QdEsignStore store, String group, String org,
                                                   boolean canSave, boolean canSaveMaster) throws BusinessException {
        Map<String,Object> control = store.loadOrganizationControl(group, org);
        List<ButtonPolicyRow> rows = new ArrayList<ButtonPolicyRow>();
        for (Map<String,Object> record : store.listOrganizationPolicies(group, org)) {
            if (EsignValues.yes(record, "base_scope")) {
                rows.add(toGroupRow(record, EsignValues.yes(record, "sign_enabled"),
                    EsignValues.yes(record, "reason_required"), EsignValues.yes(record, "audit_enabled"),
                    EsignValues.number(record, "policy_version")));
                continue;
            }
            // 未配置或旧停用策略以关闭显示。既有行仍使用实际版本保存。
            boolean active = EsignValues.yes(record, "enabled");
            rows.add(toRow(record, active && EsignValues.yes(record, "sign_enabled"),
                active && EsignValues.yes(record, "reason_required"), active && EsignValues.yes(record, "audit_enabled"),
                EsignValues.number(record, "policy_version")));
        }
        return new OrganizationPolicyView(org, control != null && EsignValues.yes(control, "master_enabled"),
            control == null ? 0L : EsignValues.number(control, "control_version"), canSave, canSaveMaster, rows);
    }

    /** 先校验整批。写入时仍以行版本比对，冲突向外抛出交由 CMT 回滚。 */
    static List<ButtonPolicyRow> saveChanges(QdEsignStore store, String group, String org,
                                             List<ButtonPolicyChange> changes) throws BusinessException {
        validateChanges(changes);
        List<Map<String,Object>> mappings = new ArrayList<Map<String,Object>>();
        for (ButtonPolicyChange change : changes) {
            Map<String,Object> mapping = store.loadNode(change.getScopeId());
            Map<String,Object> current;
            if (mapping != null && hasText(mapping, "group_scope_id")) {
                EsignValues.require(change.getScopeId().equals(EsignValues.text(mapping, "group_scope_id")),
                    "基础档案策略必须使用正式动作标识。");
                current = store.loadOrgBasePolicyEntry(group, org, change.getScopeId());
                EsignValues.require(current != null && EsignValues.yes(current, "configurable"),
                    "该基础档案按钮尚未通过接入验证，无法保存策略。");
                mapping = current;
            } else {
                requireConfigurable(mapping);
                current = store.loadPolicy(group, org, EsignValues.text(mapping, "node_code"),
                    EsignValues.text(mapping, "button_code"));
            }
            requirePilotPolicy(group, org, change);
            requireVersion(change, current);
            mappings.add(mapping);
        }
        List<ButtonPolicyRow> saved = new ArrayList<ButtonPolicyRow>();
        for (int index = 0; index < mappings.size(); index++) {
            ButtonPolicyChange change = changes.get(index);
            Map<String,Object> mapping = mappings.get(index);
            if (EsignValues.yes(mapping, "base_scope")) {
                long version = store.saveOrgBasePolicy(group, org, change.getScopeId(), change.isSignatureEnabled(),
                    change.isReasonRequired(), change.isAuditEnabled(), change.getExpectedVersion());
                saved.add(toGroupRow(mapping, change.isSignatureEnabled(), change.isReasonRequired(), change.isAuditEnabled(), version));
            } else {
                long version = saveButton(store, group, org, mapping, change);
                saved.add(toRow(mapping, change.isSignatureEnabled(), change.isReasonRequired(), change.isAuditEnabled(), version));
            }
        }
        return saved;
    }

    static List<ButtonPolicyRow> saveGroupChanges(QdEsignStore store, String group, List<ButtonPolicyChange> changes)
            throws BusinessException {
        validateChanges(changes);
        List<Map<String,Object>> entries = new ArrayList<Map<String,Object>>();
        for (ButtonPolicyChange change : changes) {
            Map<String,Object> entry = store.loadGroupPolicyEntry(group, change.getScopeId());
            EsignValues.require(entry != null && EsignValues.yes(entry, "configurable"),
                "该按钮不属于基础档案集团策略或尚未通过接入验证。");
            requireVersion(change, entry);
            entries.add(entry);
        }
        List<ButtonPolicyRow> saved = new ArrayList<ButtonPolicyRow>();
        for (int index = 0; index < entries.size(); index++) {
            ButtonPolicyChange change = changes.get(index);
            long version = store.saveGroupPolicy(group, change.getScopeId(), change.isSignatureEnabled(),
                change.isReasonRequired(), change.isAuditEnabled(), change.getExpectedVersion());
            saved.add(toGroupRow(entries.get(index), change.isSignatureEnabled(), change.isReasonRequired(),
                change.isAuditEnabled(), version));
        }
        return saved;
    }

    private static void validateChanges(List<ButtonPolicyChange> changes) throws BusinessException {
        EsignValues.require(changes != null && !changes.isEmpty(), "请选择要保存的策略修改。");
        Set<String> scopes = new HashSet<String>();
        for (ButtonPolicyChange change : changes) {
            EsignValues.require(change != null && change.getScopeId() != null && change.getScopeId().trim().length() > 0
                && scopes.add(change.getScopeId()), "按钮策略缺少清单标识或重复提交。");
        }
    }

    private static void requireVersion(ButtonPolicyChange change, Map<String,Object> row) throws BusinessException {
        long version = row == null ? 0L : EsignValues.number(row, "policy_version");
        EsignValues.require(change.getExpectedVersion() == version && version >= 0L && version < Long.MAX_VALUE,
            "按钮策略已被修改或版本无效，请重新查询。");
    }

    private static long saveButton(QdEsignStore store, String group, String org, Map<String,Object> mapping,
                                   ButtonPolicyChange change) throws BusinessException {
        requirePilotPolicy(group, org, change);
        return store.savePolicy(EsignValues.row("policy_id", EsignValues.id(), "pk_group", group, "pk_org", org,
            "node_code", EsignValues.text(mapping, "node_code"), "button_code", EsignValues.text(mapping, "button_code"),
            "sign_enabled", change.isSignatureEnabled() ? "Y" : "N", "reason_required", change.isReasonRequired() ? "Y" : "N",
            "audit_enabled", change.isAuditEnabled() ? "Y" : "N", "enabled", "Y", "validated", "Y",
            "expected_version", Long.valueOf(change.getExpectedVersion())));
    }

    static void requirePilotPolicy(String group, String org, ButtonPolicyChange change) throws BusinessException {
        if (SaleOrderPilotScope.SCOPE_ID.equals(change.getScopeId())
                && (change.isSignatureEnabled() || change.isReasonRequired() || change.isAuditEnabled())) {
            EsignValues.require(SaleOrderPilotScope.supports(group, org), "销售订单签名试点仅支持 990 销售组织。");
        }
    }

    private static ButtonPolicyRow toRow(Map<String,Object> mapping, boolean sign, boolean reason, boolean audit, long version) {
        return toRow(mapping, configurable(mapping), sign, reason, audit, version);
    }

    private static ButtonPolicyRow toGroupRow(Map<String,Object> entry, boolean sign, boolean reason, boolean audit, long version) {
        return toRow(entry, EsignValues.yes(entry, "configurable"), sign, reason, audit, version);
    }

    private static ButtonPolicyRow toRow(Map<String,Object> mapping, boolean configurable, boolean sign, boolean reason,
                                         boolean audit, long version) {
        return new ButtonPolicyRow(EsignValues.text(mapping, "scope_id"), EsignValues.text(mapping, "node_code"),
            EsignValues.text(mapping, "node_name"), EsignValues.text(mapping, "button_code"), EsignValues.text(mapping, "button_name"),
            EsignValues.text(mapping, "mapping_state"), configurable, sign, reason, audit, version);
    }

    private static void requireOrganizationMapping(Map<String,Object> mapping) throws BusinessException {
        EsignValues.require(mapping == null || !hasText(mapping, "group_scope_id"),
            "基础档案请使用新版所属组织策略页面维护。");
    }

    private static void requireConfigurable(Map<String,Object> mapping) throws BusinessException {
        EsignValues.require(configurable(mapping), "该按钮尚未通过接入验证，无法保存策略。");
    }

    private static boolean configurable(Map<String,Object> mapping) {
        return mapping != null && EsignValues.yes(mapping, "validated")
            && hasText(mapping, "node_code") && hasText(mapping, "button_code")
            && hasText(mapping, "adapter_class") && hasText(mapping, "operation_types") && hasText(mapping, "object_type");
    }

    private static boolean hasText(Map<String,Object> row, String field) {
        String value = EsignValues.text(row, field);
        return value != null && value.trim().length() > 0;
    }
}

