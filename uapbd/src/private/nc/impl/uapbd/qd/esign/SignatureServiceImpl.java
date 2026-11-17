package nc.impl.uapbd.qd.esign;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.transaction.TransactionSynchronizationRegistry;
import nc.bs.framework.common.NCLocator;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.bs.uapbd.qd.esign.core.RequestRules;
import nc.bs.uapbd.qd.esign.core.MappingRules;
import nc.bs.uapbd.qd.esign.core.ReasonRules;
import nc.bs.uapbd.qd.esign.core.ArchiveTreeScope;
import nc.bs.uapbd.qd.esign.core.ArchiveTreeSaveScope;
import nc.bs.uapbd.qd.esign.core.MaterialProdImpactScope;
import nc.bs.uapbd.qd.esign.core.NativeTransactionBoundaryProbe;
import nc.bs.uapbd.qd.esign.core.PartnerOrgBatchScope;
import nc.bs.uapbd.qd.esign.core.TransactionRollback;
import nc.bs.uapbd.qd.esign.persistence.NativeOrgBatchTargetStore;
import nc.bs.uapbd.qd.esign.persistence.QdEsignStore;
import nc.bs.uapbd.qd.esign.security.EsignPermission;
import nc.bs.uapbd.qd.esign.security.ServerIdentity;
import nc.itf.baseapp.qd.esign.ISignatureIdentityConfirmationService;
import nc.itf.uapbd.qd.esign.ISignatureService;
import nc.itf.uapbd.qd.esign.IMaterialPageDeleteService;
import nc.vo.baseapp.qd.esign.auth.IdentityConfirmationRequest;
import nc.vo.baseapp.qd.esign.auth.IdentityConfirmationResult;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.*;

/** 独立 CMT 签名服务。普通密码错误或锁定以结果返回，便于签名结果及日志正常提交；业务执行通过另一条事务调用完成。 */
public final class SignatureServiceImpl implements ISignatureService {
    /** TTL：签名请求有效期，单位毫秒，当前为十分钟。 */
    private static final long TTL=10*60*1000L;
    private static final int MAX_PASSWORD_FAILURES=5;
    /** 检查事由、身份、策略与对象域，创建请求及对象关联；不受控时返回 BYPASS，受控请求记录各自的签名和业务初始状态。 */
    public SignaturePreparation prepare(OperationContext draft) throws BusinessException {
        // 以服务端会话取得真实用户和集团；草稿中的集团只能用于一致性核对。
        EsignValues.require(draft!=null,"Operation context is required");ServerIdentity i=ServerIdentity.current();
        ReasonRules.validate(draft.getReason());
        boolean partnerBatch=nc.bs.uapbd.qd.esign.core.PartnerBatchRoute.matches(draft.getNodeCode(),draft.getButtonCode(),draft.getOperationType());
        requireExecutablePreparation(draft,partnerBatch);
        int maxObjects=partnerBatch?PartnerBatchDraft.MAX_SINGLE_TRANSACTION_OBJECTS:1000;
        EsignValues.require(draft.getObjects().size()<=maxObjects,"授权对象超过本次操作上限："+maxObjects);
        EsignValues.require(draft.getGroupId()==null||i.groupId.equals(draft.getGroupId()),"Group mismatch");
        QdEsignStore store=new QdEsignStore();Map<String,Object> node=store.loadNodeByAction(draft.getNodeCode(),draft.getButtonCode());
        boolean ownerMode=node!=null&&EsignValues.text(node,"group_scope_id")!=null;
        requireOwnerMapping(draft,ownerMode);
        boolean assignment = MaterialAssignmentResult.isRoute(draft.getNodeCode(),draft.getButtonCode(),draft.getOperationType());
        boolean customerAssignment=CustomerAssignmentResult.isRoute(draft.getNodeCode(),draft.getButtonCode(),draft.getOperationType());
        boolean supplierAssignment=SupplierAssignmentResult.isRoute(draft.getNodeCode(),draft.getButtonCode(),draft.getOperationType());
        boolean materialCancelAssignment=MaterialCancelAssignmentResult.isRoute(draft.getNodeCode(),draft.getButtonCode(),draft.getOperationType());
        boolean pageDelete = MaterialPageDeleteResult.isRoute(draft.getNodeCode(),draft.getButtonCode(),draft.getOperationType());
        boolean customerFreeze=ArchiveBatchResult.isCustomerFreeze(draft.getNodeCode(),draft.getButtonCode(),draft.getOperationType());
        boolean supplierFreeze=ArchiveBatchResult.isSupplierFreeze(draft.getNodeCode(),draft.getButtonCode(),draft.getOperationType());
        boolean partnerState=ArchiveBatchResult.isPartnerState(draft.getNodeCode(),draft.getButtonCode(),draft.getOperationType());
        boolean materialState=ArchiveBatchResult.isMaterialState(draft.getNodeCode(),draft.getButtonCode(),draft.getOperationType());
        boolean warehouseState=ArchiveBatchResult.isWarehouseState(draft.getNodeCode(),draft.getButtonCode(),draft.getOperationType());
        boolean bankAccount=nc.bs.uapbd.qd.esign.core.BankAccountRoute.matches(draft.getNodeCode(),draft.getButtonCode(),draft.getOperationType());
        boolean partnerMain=nc.bs.uapbd.qd.esign.core.PartnerMainSaveRoute.matches(draft.getNodeCode(),draft.getButtonCode(),draft.getOperationType());
        boolean partnerDelete=nc.bs.uapbd.qd.esign.core.PartnerMainDeleteRoute.matches(draft.getNodeCode(),draft.getButtonCode(),draft.getOperationType());
        boolean treeSave=node!=null && "nc.itf.uapbd.qd.esign.IArchiveTreeSaveAdapter".equals(EsignValues.text(node,"adapter_class"))
            && ("UPDATE".equals(draft.getOperationType()) || "INSERT".equals(draft.getOperationType()));
        boolean treeDelete=node!=null && "nc.itf.uapbd.qd.esign.IArchiveTreeDeleteAdapter".equals(EsignValues.text(node,"adapter_class"))
            && "DELETE".equals(draft.getOperationType());
        boolean productionSave=("10140MAG".equals(draft.getNodeCode()) || "10140MAO".equals(draft.getNodeCode()))
            && "PrSave".equals(draft.getButtonCode()) && "UPDATE".equals(draft.getOperationType());
        EsignValues.require(draft.getMaterialProdImpact()==null || productionSave,"生产影响范围不能用于其他动作。");
        EsignValues.require(draft.getArchiveTreeSaveDraft()==null || treeSave,"树形保存影响范围不能用于其他动作。");
        EsignValues.require(draft.getPartnerMainSaveDraft()==null || partnerMain,"客户或供应商保存影响范围不能用于其他动作。");
        EsignValues.require(draft.getPartnerBatchDraft()==null || partnerBatch,"快速批改字段和值不能用于其他动作。");
        if (assignment) {
            EsignValues.require(ownerMode && MaterialAssignmentResult.SCOPE.equals(node.get("group_scope_id"))
                && EsignValues.yes(node,"validated"),"物料分配入口尚未启用。");
        }
        if(customerAssignment) {
            EsignValues.require(ownerMode,"客户分配入口尚未启用。");
            nc.bs.uapbd.qd.esign.core.CustomerAssignmentRoute.requireMapping(node,draft.getOperationType());
        }
        if(supplierAssignment) {
            EsignValues.require(ownerMode,"供应商分配入口尚未启用。");
            nc.bs.uapbd.qd.esign.core.SupplierAssignmentRoute.requireMapping(node,draft.getOperationType());
        }
        if(materialCancelAssignment) {
            EsignValues.require(ownerMode,"物料取消分配入口尚未启用。");
            nc.bs.uapbd.qd.esign.core.MaterialCancelAssignmentRoute.requireMapping(node,draft.getOperationType());
        }
        if (pageDelete) {
            EsignValues.require(ownerMode && IMaterialPageDeleteService.class.getName().equals(EsignValues.text(node,"adapter_class"))
                && EsignValues.yes(node,"validated"), "档案页签删除入口尚未启用。");
        }
        if(customerFreeze) EsignValues.require(ownerMode && "nc.itf.uapbd.qd.esign.ICustomerFreezeAdapter".equals(EsignValues.text(node,"adapter_class"))
            && EsignValues.yes(node,"validated"),"客户冻结入口尚未启用。");
        if(supplierFreeze) EsignValues.require(ownerMode && "nc.itf.uapbd.qd.esign.ISupplierFreezeAdapter".equals(EsignValues.text(node,"adapter_class"))
            && EsignValues.yes(node,"validated"),"供应商冻结入口尚未启用。");
        if(partnerState) EsignValues.require(ownerMode && "nc.itf.uapbd.qd.esign.IPartnerStateAdapter".equals(EsignValues.text(node,"adapter_class"))
            && EsignValues.yes(node,"validated"),"客户或供应商启停入口尚未启用。");
        if(materialState) EsignValues.require(ownerMode && "nc.itf.uapbd.qd.esign.IMaterialStateAdapter".equals(EsignValues.text(node,"adapter_class"))
            && EsignValues.yes(node,"validated"),"物料启停入口尚未启用。");
        if(warehouseState) EsignValues.require(ownerMode && "nc.itf.uapbd.qd.esign.IWarehouseStateAdapter".equals(EsignValues.text(node,"adapter_class"))
            && EsignValues.yes(node,"validated"),"仓库启停入口尚未启用。");
        if(bankAccount) {
            EsignValues.require(ownerMode,"银行账户入口尚未启用。");
            nc.bs.uapbd.qd.esign.core.BankAccountRoute.requireMapping(node,draft.getOperationType());
        }
        if(partnerMain) {
            EsignValues.require(ownerMode,"客户或供应商保存入口尚未启用。");
            nc.bs.uapbd.qd.esign.core.PartnerMainSaveRoute.requireMapping(node,draft.getOperationType());
        }
        if(partnerDelete) {
            EsignValues.require(ownerMode,"客户或供应商删除入口尚未启用。");
            nc.bs.uapbd.qd.esign.core.PartnerMainDeleteRoute.requireMapping(node,draft.getOperationType());
        }
        if(partnerBatch) {
            EsignValues.require(ownerMode,"客户或供应商快速批改入口尚未启用。");
            nc.bs.uapbd.qd.esign.core.PartnerBatchRoute.requireMapping(node,draft.getOperationType());
        }
        List<ObjectPolicyDecision> ownerPolicies=null;PolicyDecision p;PartnerOrgBatchScope.Prepared organizationBatch=null;
        if(ownerMode){
            EsignValues.require(OperationContext.OWNER_POLICY_PROTOCOL.equals(draft.getPolicyProtocol()),"基础档案策略已升级，请升级客户端后重试。");
            EsignValues.require(i.groupId.equals(draft.getGroupId()),"Group mismatch");
            EsignPermission.requireBusiness(i,draft.getNodeCode(),draft.getButtonCode(),draft.getOrgId());
            MappingRules.requireDomain(node,draft.getOperationType(),draft.getObjects());
            if(bankAccount) {
                draft=PolicyServiceImpl.expandBankAccountRequest(i,draft,node);
            }
            if(partnerMain) draft=PolicyServiceImpl.expandPartnerMainRequest(i,draft,node);
            if(partnerDelete) draft=PolicyServiceImpl.expandPartnerMainDeleteRequest(i,draft,node);
            if(partnerBatch) {
                PolicyServiceImpl.PartnerBatchExpansion expansion=PolicyServiceImpl.expandPartnerBatch(i,draft,node);
                draft=expansion.context;organizationBatch=expansion.organization;
            }
            if(customerAssignment) draft=PolicyServiceImpl.expandCustomerAssignmentRequest(i,draft,node);
            if(supplierAssignment) draft=PolicyServiceImpl.expandSupplierAssignmentRequest(i,draft,node);
            if(materialCancelAssignment) draft=PolicyServiceImpl.expandMaterialCancelAssignmentRequest(i,draft,node);
            if(MaterialPageDeleteResult.isMaterialMain(draft.getNodeCode(),draft.getButtonCode(),draft.getOperationType())) {
                List<BusinessObjectRef> normalized=new ArrayList<BusinessObjectRef>();
                java.util.Set<String> keys=new java.util.HashSet<String>(),ids=new java.util.HashSet<String>();
                for(BusinessObjectRef ref:draft.getObjects()) {
                    EsignValues.require(ref!=null && "BD_MATERIAL".equals(ref.getObjectType())
                        && ref.getObjectPk()!=null && ref.getClientObjectId()!=null && keys.add(ref.getObjectPk())
                        && ids.add(ref.getClientObjectId()) && ref.getParentClientObjectId()==null
                        && ref.getDesiredOwnerOrg()==null && ref.getTargetOrg()==null,"物料删除授权档案无效或重复。");
                    Map<String,Object> saved=store.loadBaseArchiveOwner(i.groupId,"BD_MATERIAL",ref.getObjectPk(),false);
                    EsignValues.require(saved!=null && EsignValues.text(saved,"code")!=null,"无法确定物料档案或编码。");
                    normalized.add(new BusinessObjectRef(ref.getClientObjectId(),"BD_MATERIAL",ref.getObjectPk(),
                        EsignValues.text(saved,"code"),null));
                }
                EsignValues.require(!normalized.isEmpty(),"请选择待删除的物料。");
                draft=new OperationContext(draft.getRequestId(),draft.getBatchId(),draft.getGroupId(),draft.getOrgId(),draft.getNodeCode(),
                    draft.getNodeName(),draft.getButtonCode(),draft.getButtonName(),draft.getOperationType(),draft.getReason(),normalized,draft.getPolicyProtocol());
                EsignPermission.requireArchiveData(i,"material","delete",normalized);
            }
            if(partnerState || materialState || warehouseState) {
                boolean customer="10140CUG".equals(draft.getNodeCode()) || "10140CUO".equals(draft.getNodeCode());
                String type=warehouseState?"BD_STORDOC":materialState?"BD_MATERIAL":customer?"BD_CUSTOMER":"BD_SUPPLIER";
                List<BusinessObjectRef> normalized=new ArrayList<BusinessObjectRef>();
                for(BusinessObjectRef ref:draft.getObjects()) {
                    EsignValues.require(type.equals(ref.getObjectType()) && ref.getObjectPk()!=null
                        && ref.getParentClientObjectId()==null && ref.getDesiredOwnerOrg()==null && ref.getTargetOrg()==null,
                        "档案启停授权对象不正确。");
                    Map<String,Object> saved=store.loadBaseArchiveOwner(i.groupId,type,ref.getObjectPk(),false);
                    EsignValues.require(saved!=null && EsignValues.text(saved,"code")!=null,"无法确定档案或档案编码。");
                    normalized.add(new BusinessObjectRef(ref.getClientObjectId(),type,ref.getObjectPk(),EsignValues.text(saved,"code"),null));
                }
                draft=new OperationContext(draft.getRequestId(),draft.getBatchId(),draft.getGroupId(),draft.getOrgId(),draft.getNodeCode(),
                    draft.getNodeName(),draft.getButtonCode(),draft.getButtonName(),draft.getOperationType(),draft.getReason(),normalized,draft.getPolicyProtocol());
                EsignPermission.requireArchiveData(i,warehouseState?"stordoc":materialState?"material":customer?"customer":"supplier",draft.getOperationType().toLowerCase(java.util.Locale.ENGLISH),normalized);
                if(materialState) MaterialStateAdapter.requireManageMode(draft.getNodeCode());
            }
            if(customerFreeze) {
                List<BusinessObjectRef> normalized=new ArrayList<BusinessObjectRef>();
                for(BusinessObjectRef ref:draft.getObjects()) {
                    EsignValues.require("BD_CUSTOMER".equals(ref.getObjectType()) && ref.getObjectPk()!=null
                        && ref.getParentClientObjectId()==null && ref.getDesiredOwnerOrg()==null,"客户冻结授权对象不正确。");
                    Map<String,Object> customer=store.loadBaseArchiveOwner(i.groupId,"BD_CUSTOMER",ref.getObjectPk(),false);
                    EsignValues.require(customer!=null && EsignValues.text(customer,"code")!=null,"无法确定客户或客户编码。");
                    normalized.add(new BusinessObjectRef(ref.getClientObjectId(),"BD_CUSTOMER",ref.getObjectPk(),
                        EsignValues.text(customer,"code"),null));
                }
                draft=new OperationContext(draft.getRequestId(),draft.getBatchId(),draft.getGroupId(),draft.getOrgId(),
                    draft.getNodeCode(),draft.getNodeName(),draft.getButtonCode(),draft.getButtonName(),draft.getOperationType(),
                    draft.getReason(),normalized,draft.getPolicyProtocol());
            }
            if(supplierFreeze) {
                List<BusinessObjectRef> normalized=new ArrayList<BusinessObjectRef>();
                String selected=null,owner=null,code=null;
                for(BusinessObjectRef ref:draft.getObjects()) {
                    EsignValues.require("BD_SUPPLIER".equals(ref.getObjectType()) && ref.getObjectPk()!=null
                        && ref.getTargetOrg()!=null && ref.getParentClientObjectId()==null && ref.getDesiredOwnerOrg()==null,
                        "供应商冻结授权对象不正确。");
                    if(selected==null) {
                        selected=ref.getObjectPk();
                        Map<String,Object> supplier=store.loadBaseArchiveOwner(i.groupId,"BD_SUPPLIER",selected,false);
                        EsignValues.require(supplier!=null && EsignValues.text(supplier,"code")!=null
                            && EsignValues.text(supplier,"pk_org")!=null,"无法确定供应商、编码或所属组织。");
                        code=EsignValues.text(supplier,"code");owner=EsignValues.text(supplier,"pk_org");
                    }
                    EsignValues.require(selected.equals(ref.getObjectPk()),"供应商冻结只能选择一个供应商。");
                    normalized.add(new BusinessObjectRef(ref.getClientObjectId(),"BD_SUPPLIER",selected,code,null,null,null,ref.getTargetOrg()));
                }
                if("10140SUO".equals(draft.getNodeCode())) EsignValues.require(normalized.size()==1
                    && owner.equals(normalized.get(0).getTargetOrg()),"业务单元供应商冻结目标与原生操作不一致。");
                draft=new OperationContext(draft.getRequestId(),draft.getBatchId(),draft.getGroupId(),draft.getOrgId(),
                    draft.getNodeCode(),draft.getNodeName(),draft.getButtonCode(),draft.getButtonName(),draft.getOperationType(),
                    draft.getReason(),normalized,draft.getPolicyProtocol());
            }
            if(productionSave) {
                EsignValues.require("nc.itf.uapbd.qd.esign.IMaterialProdSaveAdapter".equals(EsignValues.text(node,"adapter_class"))
                    && draft.getObjects().size()==1 && draft.getMaterialProdImpact()!=null,"生产信息保存入口尚未完成级联接入。");
                BusinessObjectRef root=draft.getObjects().get(0);MaterialProdImpact impact=draft.getMaterialProdImpact();
                Map<String,Object> selected=store.loadMaterialOwner(i.groupId,"BD_MATERIAL_PROD",root.getObjectPk(),false);
                EsignValues.require(selected!=null && impact.getTargetOrg()!=null
                    && impact.getTargetOrg().equals(EsignValues.text(selected,"target_org"))
                    && EsignValues.text(selected,"code")!=null && root.getDesiredOwnerOrg()==null
                    && root.getParentClientObjectId()==null,"生产页签目标组织已变化，请重新保存。");
                root=new BusinessObjectRef(root.getClientObjectId(),root.getObjectType(),root.getObjectPk(),
                    EsignValues.text(selected,"code"),null);
                EsignPermission.requirePageOrganization(i,draft.getNodeCode(),draft.getButtonCode(),"prod_info",impact.getTargetOrg());
                List<BusinessObjectRef> affected=MaterialProdImpactScope.expand(i.groupId,root,impact);
                draft=new OperationContext(draft.getRequestId(),draft.getBatchId(),draft.getGroupId(),draft.getOrgId(),
                    draft.getNodeCode(),draft.getNodeName(),draft.getButtonCode(),draft.getButtonName(),draft.getOperationType(),
                    draft.getReason(),affected,draft.getPolicyProtocol(),impact);
                MappingRules.requireDomain(node,draft.getOperationType(),draft.getObjects());
            }
            if(treeSave) {
                requireTreeSaveDraft(draft);
                BusinessObjectRef root=draft.getObjects().get(0);ArchiveTreeSaveDraft impact=draft.getArchiveTreeSaveDraft();
                if("UPDATE".equals(draft.getOperationType())) {
                    List<BusinessObjectRef> affected=ArchiveTreeSaveScope.select(i.groupId,root,impact.getCode(),impact.getParent(),impact.getWarehouse()).getObjects();
                    draft=new OperationContext(draft.getRequestId(),draft.getBatchId(),draft.getGroupId(),draft.getOrgId(),draft.getNodeCode(),
                        draft.getNodeName(),draft.getButtonCode(),draft.getButtonName(),draft.getOperationType(),draft.getReason(),affected,
                        draft.getPolicyProtocol(),null,impact);
                    MappingRules.requireDomain(node,draft.getOperationType(),draft.getObjects());
                    EsignPermission.requireArchiveData(i,ArchiveTreeScope.permissionResource(root.getObjectType()),"edit",affected);
                }
            }
            if(treeDelete) {
                requireTreeDeleteDraft(draft);
                BusinessObjectRef root=draft.getObjects().get(0);
                List<BusinessObjectRef> branch=ArchiveTreeScope.expand(i.groupId,java.util.Collections.singletonList(root),false);
                EsignValues.require(branch.size()==1 && root.getObjectPk().equals(branch.get(0).getObjectPk()),
                    "所选档案存在下级，请先处理下级后再删除。");
                draft=new OperationContext(draft.getRequestId(),draft.getBatchId(),draft.getGroupId(),draft.getOrgId(),draft.getNodeCode(),
                    draft.getNodeName(),draft.getButtonCode(),draft.getButtonName(),draft.getOperationType(),draft.getReason(),branch,draft.getPolicyProtocol());
                EsignPermission.requireArchiveData(i,ArchiveTreeScope.permissionResource(root.getObjectType()),"delete",draft.getObjects());
            }
            if ("nc.itf.uapbd.qd.esign.IArchiveTreeDisableAdapter".equals(EsignValues.text(node,"adapter_class"))) {
                EsignValues.require("DISABLE".equals(draft.getOperationType()) && draft.getObjects().size()==1,
                    "分类停用须选择一个根分类。");
                BusinessObjectRef root=draft.getObjects().get(0);
                EsignValues.require(ArchiveTreeScope.supports(root.getObjectType()) && root.getObjectPk()!=null
                    && root.getParentClientObjectId()==null && root.getDesiredOwnerOrg()==null && root.getTargetOrg()==null,
                    "分类停用根对象不正确。");
                List<BusinessObjectRef> branch=ArchiveTreeScope.expand(i.groupId,draft.getObjects(),false);
                draft=new OperationContext(draft.getRequestId(),draft.getBatchId(),draft.getGroupId(),draft.getOrgId(),
                    draft.getNodeCode(),draft.getNodeName(),draft.getButtonCode(),draft.getButtonName(),
                    draft.getOperationType(),draft.getReason(),branch,draft.getPolicyProtocol());
                MappingRules.requireDomain(node,draft.getOperationType(),draft.getObjects());
                EsignPermission.requireArchiveData(i,ArchiveTreeScope.permissionResource(root.getObjectType()),
                    "disable",draft.getObjects());
            }
            if ("nc.itf.uapbd.qd.esign.IArchiveTreeEnableAdapter".equals(EsignValues.text(node,"adapter_class"))) {
                EsignValues.require("ENABLE".equals(draft.getOperationType()) && draft.getObjects().size()==1,
                    "分类启用须选择一个分类。");
                BusinessObjectRef root=draft.getObjects().get(0);
                EsignValues.require(ArchiveTreeScope.supports(root.getObjectType()) && root.getObjectPk()!=null
                    && root.getParentClientObjectId()==null && root.getDesiredOwnerOrg()==null && root.getTargetOrg()==null,
                    "分类启用对象不正确。");
                EsignPermission.requireArchiveData(i,ArchiveTreeScope.permissionResource(root.getObjectType()),"enable",draft.getObjects());
            }
            ownerPolicies=PolicyServiceImpl.resolveOwnerPolicies(store,i.groupId,draft,node,false);
            PolicyServiceImpl.requireTransferPermissions(i,draft,ownerPolicies);
            p=PolicyServiceImpl.aggregateOwnerPolicies(ownerPolicies);
        }else p=PolicyServiceImpl.resolveFor(i,draft.getOrgId(),draft.getNodeCode(),draft.getButtonCode());
        boolean sapplyBatch = !ownerMode && p.isControlled() && SapplyBatchScope.supports(draft);
        if (sapplyBatch) {
            SapplyBatchNativeContract.requireRegistration();
            SapplyWorkflowBatchUnit.requireMapping(store, draft.getButtonCode(), draft.getOperationType(), draft.getObjects());
            store.requireBatchObjectCountCapacity(draft.getObjects().size());
        } else nc.bs.uapbd.qd.esign.core.InventoryTransactionRoute.requireSupportedPreparation(draft,ownerMode,p);
        // 签名与审计均不生效时返回 BYPASS，不为原业务路径制造受控请求。
        if(!ownerMode&&!p.isControlled())return new SignaturePreparation(draft,p,"BYPASS");
        EsignValues.require(draft.getOrgId()!=null,"Business unit is required");
        EsignPermission.requireBusiness(i,draft.getNodeCode(),draft.getButtonCode(),draft.getOrgId());
        store.validateReason(draft.getReason());
        // 只有已验证且动作及对象类型匹配的节点映射才允许创建授权，不能由客户端指定适配服务。
        EsignValues.require(p.isValidated()&&node!=null&&EsignValues.yes(node,"validated"),"NC adapter is not verified");
        MappingRules.requireDomain(node,draft.getOperationType(),draft.getObjects());
        List<BusinessObjectRef> refs = assignment ? assignmentRefs(store, i.groupId, draft.getObjects()) : draft.getObjects();
        String protocol = ownerMode ? OperationContext.OWNER_POLICY_PROTOCOL
                : sapplyBatch ? SapplyBatchScope.POLICY_MODE : null;
        String id=EsignValues.id();OperationContext c=new OperationContext(id,id,i.groupId,draft.getOrgId(),draft.getNodeCode(),EsignValues.text(node,"node_name"),draft.getButtonCode(),EsignValues.text(node,"button_name"),draft.getOperationType(),draft.getReason(),refs,protocol,draft.getMaterialProdImpact(),draft.getArchiveTreeSaveDraft(),draft.getPartnerMainSaveDraft(),draft.getPartnerBatchDraft());
        long startedAt=System.currentTimeMillis();String now=EsignValues.utc(startedAt),month=EsignValues.monthForUtc(now);
        TransactionSynchronizationRegistry organizationTx=organizationBatch==null?null:NativeTransactionBoundaryProbe.requireActiveTransaction();
        Object organizationKey=organizationTx==null?null:organizationTx.getTransactionKey();
        try {
        // 同时保存签名状态、业务状态与两个策略版本，后续总控或节点配置变化会使旧请求失效。
        store.insert("qd_es_request",EsignValues.row("request_id",id,"batch_id",id,"user_id",i.userId,"user_code",i.userCode,"user_name",i.userName,
            "pk_group",i.groupId,"pk_org",c.getOrgId(),"node_code",c.getNodeCode(),"node_name",c.getNodeName(),
            "button_code",c.getButtonCode(),"button_name",c.getButtonName(),"operation_type",c.getOperationType(),
            "reason",c.getReason(),"signature_state",p.isSignatureRequired()?"PENDING":"NOT_REQUIRED",
            "business_state",(p.isSignatureRequired()||p.isReasonRequired())?"PENDING":"READY","policy_version",Long.valueOf(p.getVersion()),"control_version",Long.valueOf(p.getControlVersion()),
            "created_at",now,"expires_at",EsignValues.utc(startedAt+TTL),"client_ip",i.clientIp,"terminal_name",i.terminalName,"archive_month",month,
            "policy_mode",protocol == null ? "LEGACY" : protocol,
            "owner_object_count",ownerMode || sapplyBatch ? Integer.valueOf(c.getObjects().size()):null));
        NativeOrgBatchTargetStore targetStore=organizationBatch==null?null:new NativeOrgBatchTargetStore();
        NativeOrgBatchTargetStore.Prepared targetTicket=targetStore==null?null:targetStore.begin(id,organizationBatch.getImpact().getPage());
        // 先分配全部对象的数据库 ID，再写入父对象关联，避免依赖客户端对象排列顺序。
        Map<String,String> objectIds=new java.util.LinkedHashMap<String,String>();
        List<String> generatedObjectIds=new ArrayList<String>();
        for(BusinessObjectRef object:c.getObjects())generatedObjectIds.add(EsignValues.id());
        // The native warehouse delete stops at the first failed row. Sort random identities before
        // assigning them so the persisted ORDER BY object_id recovers the signed selection order.
        if(MaterialPageDeleteResult.isWarehouse(c.getNodeCode(),c.getButtonCode(),c.getOperationType()))
            java.util.Collections.sort(generatedObjectIds);
        EsignValues.require(new java.util.HashSet<String>(generatedObjectIds).size()==generatedObjectIds.size(),"Duplicate generated object identity");
        int generatedIndex=0;for(BusinessObjectRef object:c.getObjects())objectIds.put(object.getClientObjectId(),generatedObjectIds.get(generatedIndex++));
        QdEsignStore.PreparedBatchObjects batchObjects=partnerBatch?store.beginPreparedBatchObjects(id,month):null;
        try { int index=0;for(BusinessObjectRef object:c.getObjects()){
            EsignValues.require(object.getParentClientObjectId()==null||objectIds.containsKey(object.getParentClientObjectId()),"Unknown parent object");
            Map<String,Object> values=EsignValues.row("object_id",objectIds.get(object.getClientObjectId()),
            "request_id",id,"client_object_id",object.getClientObjectId(),"object_type",object.getObjectType(),"object_pk",object.getObjectPk(),
            "bill_code",object.getBillCode(),"parent_object_id",objectIds.get(object.getParentClientObjectId()),"relation_type",object.getParentClientObjectId()==null?"ROOT":"CHILD",
            "pk_group",i.groupId,"pk_org",c.getOrgId(),"created_at",now,"archive_month",month);
            if(ownerMode){ObjectPolicyDecision item=ownerPolicies.get(index);PolicyDecision op=item.getPolicy();
                values.put("policy_owner_org",item.getOwnerOrg());values.put("policy_owner_type",item.getOwnerType());values.put("policy_scope_id",item.getScopeId());
                values.put("target_org",item.getTargetOrg());values.put("policy_owner_root",item.getOwnerRoot());values.put("desired_owner_org",object.getDesiredOwnerOrg());
                values.put("owner_policy_version",Long.valueOf(op.getVersion()));values.put("owner_control_version",Long.valueOf(op.getControlVersion()));
                values.put("owner_sign_required",op.isSignatureRequired()?"Y":"N");values.put("owner_reason_required",op.isReasonRequired()?"Y":"N");values.put("owner_audit_enabled",op.isAuditEnabled()?"Y":"N");}
            if (assignment || customerAssignment || supplierAssignment || materialCancelAssignment || pageDelete || customerFreeze || supplierFreeze || partnerState || materialState || warehouseState || partnerBatch || sapplyBatch) { values.put("unit_state","PENDING"); }
            if(batchObjects==null) store.insert("qd_es_object",values);else batchObjects.insert(values);index++;}
            if(batchObjects!=null) batchObjects.finish();
        } finally { if(batchObjects!=null) batchObjects.close(); }
        if(targetStore!=null) {
            targetStore.write(targetTicket,organizationBatch);
            targetStore.requireCompleted(targetTicket);
        }
        if(partnerBatch) finishBatchPreparation(store,id,startedAt,System.currentTimeMillis());
        if(organizationTx!=null)NativeTransactionBoundaryProbe.requireSingleDataSource(organizationTx,organizationKey);
        return new SignaturePreparation(c,p,(p.isSignatureRequired()||p.isReasonRequired())?"PENDING":"READY",ownerMode);
        } catch(BusinessException failure) {
            if(organizationTx==null || TransactionRollback.isMarked(failure))throw failure;
            throw TransactionRollback.abort(organizationTx,organizationKey,failure);
        } catch(RuntimeException failure) {
            if(organizationTx==null)throw failure;
            throw TransactionRollback.abort(organizationTx,organizationKey,failure);
        }
    }
    /** Do not ask a user to sign until the organization-page business and receipt path is connected. */
    static void requireExecutablePreparation(OperationContext draft,boolean partnerBatch) throws BusinessException {
        PartnerBatchDraft payload=draft==null?null:draft.getPartnerBatchDraft();
        EsignValues.require(!partnerBatch || payload==null || payload.getTargetOrgs().length==0,
            "组织页签批改的原生执行与回执链尚未开放，尚未创建签名请求。");
    }
    /** The ten-minute signing window begins after the full batch has been persisted in this transaction. */
    static void finishBatchPreparation(QdEsignStore store,String requestId,long startedAt,long readyAt) throws BusinessException {
        EsignValues.require(store!=null && requestId!=null && startedAt>=0 && readyAt>=startedAt && readyAt<=Long.MAX_VALUE-TTL,
            "快速批改准备期间服务器时间异常，请重新发起。");
        store.finishBatchPreparation(requestId,EsignValues.utc(readyAt+TTL));
    }
    static void requireOwnerMapping(OperationContext context,boolean ownerMode) throws BusinessException {
        EsignValues.require(!OperationContext.OWNER_POLICY_PROTOCOL.equals(context.getPolicyProtocol()) || ownerMode,
            "基础档案动作尚未登记所属组织策略，无法执行受控操作。");
    }
    static void requireTreeSaveDraft(OperationContext draft) throws BusinessException {
        EsignValues.require(draft!=null && "Save".equals(draft.getButtonCode())
            && draft.getObjects().size()==1 && draft.getObjects().get(0)!=null && draft.getArchiveTreeSaveDraft()!=null,
            "树形档案保存缺少待核实的影响范围。");
        BusinessObjectRef root=draft.getObjects().get(0);
        EsignValues.require(ArchiveTreeScope.supports(root.getObjectType())
            && ArchiveTreeSaveAdapter.tree(root.getObjectType()).matchesNode(draft.getNodeCode())
            && root.getParentClientObjectId()==null && root.getTargetOrg()==null
            && ("UPDATE".equals(draft.getOperationType()) && root.getObjectPk()!=null
                || "INSERT".equals(draft.getOperationType()) && root.getObjectPk()==null && root.getDesiredOwnerOrg()!=null),
            "树形档案保存的选中对象不正确。");
    }
    static void requireTreeDeleteDraft(OperationContext draft) throws BusinessException {
        EsignValues.require(draft!=null && "Delete".equals(draft.getButtonCode()) && "DELETE".equals(draft.getOperationType())
            && draft.getObjects().size()==1 && draft.getObjects().get(0)!=null,"树形档案删除只能选择一个对象。");
        BusinessObjectRef root=draft.getObjects().get(0);
        EsignValues.require(ArchiveTreeScope.supports(root.getObjectType())
            && ArchiveTreeSaveAdapter.tree(root.getObjectType()).matchesNode(draft.getNodeCode())
            && root.getObjectPk()!=null && root.getParentClientObjectId()==null
            && root.getDesiredOwnerOrg()==null && root.getTargetOrg()==null,"树形档案删除对象无效。");
    }
    private static List<BusinessObjectRef> assignmentRefs(QdEsignStore store, String group, List<BusinessObjectRef> objects)
            throws BusinessException {
        List<BusinessObjectRef> refs = new ArrayList<BusinessObjectRef>();
        Map<String,String> codes = new HashMap<String,String>();
        for (BusinessObjectRef object : objects) {
            String pk = object.getObjectPk();
            if (!codes.containsKey(pk)) {
                Map<String,Object> material = store.loadMaterialOwner(group, "BD_MATERIAL", pk, false);
                EsignValues.require(material != null, "待分配物料已变化，请重新选择。");
                codes.put(pk, EsignValues.text(material, "code"));
            }
            refs.add(new BusinessObjectRef(object.getClientObjectId(), "BD_MATERIAL", pk, codes.get(pk),
                null, null, null, object.getTargetOrg()));
        }
        return refs;
    }
    /** 锁定本人的请求，复核有效期、策略版本和必填事由后调用原生认证桥接；落库记录认证结果，finally 清零传入密码。 */
    public SignatureResult confirm(String id,char[] password,String reason) throws BusinessException {
        try {
            QdEsignStore store=new QdEsignStore();ServerIdentity i=ServerIdentity.current();Map<String,Object> r=owned(store,id,i,true);
            boolean ownerMode=OperationContext.OWNER_POLICY_PROTOCOL.equals(EsignValues.text(r,"policy_mode"));
            boolean reasonOnly=RequestRules.mayConfirmReason(ownerMode || SapplyBatchScope.POLICY_MODE.equals(r.get("policy_mode")),
                EsignValues.text(r,"signature_state"),EsignValues.text(r,"business_state"));
            if(!reasonOnly&&!RequestRules.mayAuthenticate(EsignValues.text(r,"signature_state"),EsignValues.text(r,"business_state")))return result(store,r);
            // 过期请求落库记录过期结果，不再进入原生认证。
            if(System.currentTimeMillis()>EsignValues.parseUtc(EsignValues.text(r,"expires_at"))){
                if(reasonOnly)store.updateRequest(id,EsignValues.row("business_state","EXPIRED","finished_at",EsignValues.utc()));
                else finish(store,r,"EXPIRED","EXPIRED",store.cancellationReason(reason),false);
                return new SignatureResult(id,reasonOnly?"NOT_REQUIRED":"EXPIRED",false,"EXPIRED");}
            PolicyDecision p;
            if(ownerMode){Map<String,Object> node=store.loadNodeByAction(EsignValues.text(r,"node_code"),EsignValues.text(r,"button_code"));
                List<Map<String,Object>> objects=store.loadObjects(id);
                p=PolicyServiceImpl.validateOwnerRequest(store,i,r,node,objects,false);
                if(ArchiveBatchResult.isPartnerBatchUpdate(EsignValues.text(r,"node_code"),EsignValues.text(r,"button_code"),
                        EsignValues.text(r,"operation_type")) && ArchiveBatchResultServiceImpl.hasOrganizationMarker(objects)) {
                    ArchiveBatchStatus status=ArchiveBatchResultServiceImpl.authorizedStatus(i,r,objects);
                    EsignValues.require(status.getOrganization()!=null && status.getOrganization().hasPreparedSelection()
                        && !status.getOrganization().isResultAvailable(),
                        "组织批改准备范围或权限不可核实，请重新发起。");
                }
            }else{p=PolicyServiceImpl.resolveFor(i,EsignValues.text(r,"pk_org"),EsignValues.text(r,"node_code"),EsignValues.text(r,"button_code"));
                EsignValues.require(p.getVersion()==EsignValues.number(r,"policy_version")&&p.getControlVersion()==EsignValues.number(r,"control_version"),"Policy changed; start a new operation");}
            EsignValues.require(p.isValidated()&&(reasonOnly?(!p.isSignatureRequired()&&p.isReasonRequired()):p.isSignatureRequired()),"Policy changed; start a new operation");
            EsignValues.require(!p.isReasonRequired()||(reason!=null&&reason.trim().length()>0),"Operation reason is required");
            store.validateReason(reason);
            if(reasonOnly){
                EsignValues.require(password==null||password.length==0,"此操作只需填写事由，无需提供密码。");
                store.updateRequest(id,EsignValues.row("reason",reason,"business_state","READY"));
                return new SignatureResult(id,"NOT_REQUIRED",false,"READY");
            }
            EsignValues.require(password!=null&&password.length>0,"请输入当前登录人员的密码。");
            return confirmPassword(store,r,i,NCLocator.getInstance().lookup(ISignatureIdentityConfirmationService.class),password,reason);
        // 所有成功及异常出口都清零调用方传入的密码数组。
        } finally {if(password!=null)Arrays.fill(password,'\0');}
    }
    /** 调用方已锁定请求行；计数及锁定决定均来自已提交的签名日志。 */
    static SignatureResult confirmPassword(QdEsignStore store,Map<String,Object> r,ServerIdentity identity,
            ISignatureIdentityConfirmationService authentication,char[] password,String reason) throws BusinessException {
        String id=EsignValues.text(r,"request_id");
        int previous=store.countSignaturePasswordFailures(id);
        EsignValues.require(previous>=0&&previous<MAX_PASSWORD_FAILURES,"签名失败次数异常，请重新发起操作。");
        boolean lockOnFailure=previous==MAX_PASSWORD_FAILURES-1;
        IdentityConfirmationRequest auth=new IdentityConfirmationRequest();auth.setRequestId(id);auth.setPassword(password);
        IdentityConfirmationResult verified;
        try{verified=authentication.confirm(auth,lockOnFailure);}finally{auth.clearPassword();}
        EsignValues.require(verified!=null&&id.equals(verified.getRequestId())
            &&identity.userId.equals(verified.getUserId())&&identity.groupId.equals(verified.getGroupId()),"Authentication identity mismatch");
        String status=verified.getStatus();
        EsignValues.require(IdentityConfirmationResult.SUCCESS.equals(status)
            ||IdentityConfirmationResult.PASSWORD_INCORRECT.equals(status)
            ||IdentityConfirmationResult.ACCOUNT_LOCKED.equals(status)
            ||IdentityConfirmationResult.ACCOUNT_UNAVAILABLE.equals(status),"Unexpected authentication result");
        EsignValues.require(verified.isLocked()==IdentityConfirmationResult.ACCOUNT_LOCKED.equals(status),"Inconsistent account lock result");
        EsignValues.require(!lockOnFailure||!IdentityConfirmationResult.PASSWORD_INCORRECT.equals(status),
            "账号锁定结果无法确认，请联系管理员。");
        store.updateRequest(id,EsignValues.row("user_name",verified.getUserName(),"reason",reason));
        r.put("user_name",verified.getUserName());r.put("reason",reason);
        if(IdentityConfirmationResult.PASSWORD_INCORRECT.equals(status)){
            recordAttempt(store,r,status,reason,false,EsignValues.utc());
            return new SignatureResult(id,status,false,"PENDING",Integer.valueOf(MAX_PASSWORD_FAILURES-previous-1));
        }
        String business=verified.isSuccess()?"READY":"FAILED";
        finish(store,r,status,business,reason,verified.isLocked());
        return new SignatureResult(id,status,verified.isLocked(),business);
    }
    /** 尝试结束仍可取消的请求；服务端依据持久化状态判断，已提交或正在执行的业务不会由此推测为失败。 */
    public void cancel(String id,String reason) throws BusinessException {
        QdEsignStore store=new QdEsignStore();Map<String,Object> r=owned(store,id,ServerIdentity.current(),true);
        if(RequestRules.mayAuthenticate(EsignValues.text(r,"signature_state"),EsignValues.text(r,"business_state")))finish(store,r,"CANCELLED","CANCELLED",store.cancellationReason(reason),false);
        else if("READY".equals(EsignValues.text(r,"business_state"))||RequestRules.mayConfirmReason(
            OperationContext.OWNER_POLICY_PROTOCOL.equals(EsignValues.text(r,"policy_mode"))
                || SapplyBatchScope.POLICY_MODE.equals(r.get("policy_mode")),
            EsignValues.text(r,"signature_state"),EsignValues.text(r,"business_state")))
            store.updateRequest(id,EsignValues.row("business_state","CANCELLED","finished_at",EsignValues.utc()));
    }
    /** 上报允许的客户端失败码，仅在请求仍为 READY 时登记失败及结束时间。 */
    public void reportBusinessFailure(String id,String code) throws BusinessException {
        EsignValues.require("BUSINESS_EXCEPTION".equals(code)||"CLIENT_ADAPTER_FAILED".equals(code)||"DISPATCH_NOT_OBSERVED".equals(code),"Unsupported failure code");
        QdEsignStore store=new QdEsignStore();Map<String,Object> r=owned(store,id,ServerIdentity.current(),true);
        if("READY".equals(EsignValues.text(r,"business_state")))store.updateRequest(id,EsignValues.row("business_state","FAILED","error_code",code,"finished_at",EsignValues.utc()));
    }
    /** 查询当前调用者拥有的请求结果，分别返回签名状态与业务状态。 */
    public SignatureResult getResult(String id) throws BusinessException {
        QdEsignStore store=new QdEsignStore();return result(store,owned(store,id,ServerIdentity.current(),false));
    }
    /** 读取并核对请求所属用户和集团，可选择加行锁；请求不存在或不属于当前身份时拒绝访问。 */
    public static Map<String,Object> owned(QdEsignStore store,String id,ServerIdentity i,boolean lock) throws BusinessException {
        Map<String,Object> r=lock?store.loadRequest(id,true):store.loadRequestResult(id);EsignValues.require(r!=null,"Unknown request");
        EsignValues.require(i.userId.equals(EsignValues.text(r,"user_id"))&&i.groupId.equals(EsignValues.text(r,"pk_group")),"Request belongs to another identity");return r;
    }
    /** 业务网关只锁在线请求；历史仅允许返回已成功结果，不允许重新认领或执行。 */
    public static Map<String,Object> ownedForExecution(QdEsignStore store,String id,ServerIdentity i) throws BusinessException {
        Map<String,Object> r=store.loadRequest(id,true);
        if(r==null){r=store.loadArchivedRequest(id);EsignValues.require(r!=null,"Unknown request");}
        EsignValues.require(i.userId.equals(EsignValues.text(r,"user_id"))&&i.groupId.equals(EsignValues.text(r,"pk_group")),"Request belongs to another identity");
        EsignValues.require(!QdEsignStore.isArchivedResult(r)||"SUCCESS".equals(r.get("business_state")),"Historical request cannot be claimed or executed");
        return r;
    }
    /** 将持久化请求的签名状态、业务状态及锁定标记转换为对外结果。 */
    private SignatureResult result(QdEsignStore store,Map<String,Object> r) throws BusinessException {
        String id=EsignValues.text(r,"request_id"),status=EsignValues.text(r,"signature_state"),business=EsignValues.text(r,"business_state");
        Integer remaining=null;
        if(RequestRules.mayAuthenticate(status,business)){
            int failures=store.countSignaturePasswordFailures(id);
            EsignValues.require(failures>=0&&failures<MAX_PASSWORD_FAILURES,"签名失败次数异常，请重新发起操作。");
            remaining=Integer.valueOf(MAX_PASSWORD_FAILURES-failures);
        }
        return new SignatureResult(id,status,"ACCOUNT_LOCKED".equals(status),business,remaining);
    }
    /** 为每个关联对象保存同一次尝试的签名结果，随后更新签名及业务状态，终态补充结束时间。 */
    private static void finish(QdEsignStore store,Map<String,Object> r,String status,String business,String reason,boolean locked) throws BusinessException {
        String id=EsignValues.text(r,"request_id"),now=EsignValues.utc();
        recordAttempt(store,r,status,reason,locked,now);
        Map<String,Object> changes=EsignValues.row("signature_state",status,"business_state",business,"reason",reason);
        if(RequestRules.terminal(business))changes.put("finished_at",now);store.updateRequest(id,changes);
    }
    /** 一次认证对多个对象写入相同的尝试编号。 */
    static void recordAttempt(QdEsignStore store,Map<String,Object> r,String status,String reason,boolean locked,String now) throws BusinessException {
        String id=EsignValues.text(r,"request_id"),attempt=EsignValues.id();
        if(OperationContext.OWNER_POLICY_PROTOCOL.equals(EsignValues.text(r,"policy_mode"))
                && nc.bs.uapbd.qd.esign.core.PartnerBatchRoute.matches(EsignValues.text(r,"node_code"),
                    EsignValues.text(r,"button_code"),EsignValues.text(r,"operation_type"))) {
            int written=store.recordPartnerBatchAttempt(id,attempt,status,locked,reason,now);
            if(IdentityConfirmationResult.PASSWORD_INCORRECT.equals(status)||IdentityConfirmationResult.ACCOUNT_LOCKED.equals(status))
                EsignValues.require(written>0,"签名失败记录没有关联对象，已终止本次操作。");
            return;
        }
        List<Map<String,Object>> objects=store.loadObjects(id);
        java.util.Set<String> representatives=OperationContext.OWNER_POLICY_PROTOCOL.equals(EsignValues.text(r,"policy_mode"))
            && nc.bs.uapbd.qd.esign.core.BankAccountRoute.matches(EsignValues.text(r,"node_code"),
            EsignValues.text(r,"button_code"),EsignValues.text(r,"operation_type"))
            ?nc.bs.uapbd.qd.esign.core.BankAccountAuditObjects.representatives(objects):null;
        int written=0;
        for(Map<String,Object> o:objects){if(OperationContext.OWNER_POLICY_PROTOCOL.equals(EsignValues.text(r,"policy_mode"))&&!EsignValues.yes(o,"owner_sign_required"))continue;
            if(representatives!=null && !representatives.contains(EsignValues.text(o,"client_object_id")))continue;
            store.insert("qd_es_signlog",EsignValues.row("event_id",EsignValues.id(),"request_id",id,"batch_id",r.get("batch_id"),
            "attempt_id",attempt,"object_id",o.get("object_id"),"result_code",status,"lock_result",locked?"Y":"N","reason",reason,
            "event_time",now,"archive_month",EsignValues.monthForUtc(now)));written++;}
        if(IdentityConfirmationResult.PASSWORD_INCORRECT.equals(status)||IdentityConfirmationResult.ACCOUNT_LOCKED.equals(status))
            EsignValues.require(written>0,"签名失败记录没有关联对象，已终止本次操作。");
    }
}
