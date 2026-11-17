package nc.vo.uapbd.qd.esign;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 跨 RPC 传递的显式业务上下文，保存请求、组织、节点、动作及所选对象。身份以服务端会话为准，禁止加入密码或其他认证秘密。 */
public final class OperationContext implements Serializable {
    public static final String OWNER_POLICY_PROTOCOL = "OWNER_V1";
    /** serialVersionUID：Java 序列化版本号，保持跨 RPC 或 Swing 组件序列化的兼容标识。 */
    private static final long serialVersionUID = 1L;
    /**
     * requestId：贯通准备、签名、执行及日志关联的请求标识。
     * batchId：同批业务对象共用的批次标识。
     * groupId：NC 集团主键，服务端从当前身份核定。
     * orgId：本次业务操作所属业务单元主键。
     * nodeCode：NC 功能节点编码。
     * nodeName：操作时保存的功能节点名称。
     * buttonCode：节点内的真实操作按钮编码。
     * buttonName：操作时保存的按钮名称。
     * operationType：受映射动作域约束的业务操作类型。
     * reason：操作事由文本。
     */
    private final String requestId, batchId, groupId, orgId, nodeCode, nodeName,
        buttonCode, buttonName, operationType, reason;
    private final String policyProtocol;
    /** Only preparation may use these draft fields to expand a production cascade. */
    private final MaterialProdImpact materialProdImpact;
    /** Only preparation uses this draft to identify the native tree's affected branch. */
    private final ArchiveTreeSaveDraft archiveTreeSaveDraft;
    /** Only preparation uses this draft to identify customer/supplier save side effects. */
    private final PartnerMainSaveDraft partnerMainSaveDraft;
    /** Only preparation uses this draft to bind the native quick-batch fields and values. */
    private final PartnerBatchDraft partnerBatchDraft;
    /** objects：本次授权或结果关联的业务对象集合。 */
    private final List<BusinessObjectRef> objects;
    /** 构造操作上下文，校验必需编码、非空对象集、客户端标识唯一性、父对象存在及关系无环；对象列表复制后只读保存。 */
    public OperationContext(String requestId, String batchId, String groupId, String orgId,
            String nodeCode, String nodeName, String buttonCode, String buttonName,
            String operationType, String reason, List<BusinessObjectRef> objects) {
        this(requestId, batchId, groupId, orgId, nodeCode, nodeName, buttonCode, buttonName,
            operationType, reason, objects, null);
    }
    public OperationContext(String requestId, String batchId, String groupId, String orgId,
            String nodeCode, String nodeName, String buttonCode, String buttonName,
            String operationType, String reason, List<BusinessObjectRef> objects, String policyProtocol) {
        this(requestId,batchId,groupId,orgId,nodeCode,nodeName,buttonCode,buttonName,operationType,reason,objects,policyProtocol,null);
    }
    public OperationContext(String requestId, String batchId, String groupId, String orgId,
            String nodeCode, String nodeName, String buttonCode, String buttonName,
            String operationType, String reason, List<BusinessObjectRef> objects, String policyProtocol,
            MaterialProdImpact materialProdImpact) {
        this(requestId,batchId,groupId,orgId,nodeCode,nodeName,buttonCode,buttonName,operationType,reason,objects,
            policyProtocol,materialProdImpact,null);
    }
    public OperationContext(String requestId, String batchId, String groupId, String orgId,
            String nodeCode, String nodeName, String buttonCode, String buttonName,
            String operationType, String reason, List<BusinessObjectRef> objects, String policyProtocol,
            MaterialProdImpact materialProdImpact,ArchiveTreeSaveDraft archiveTreeSaveDraft) {
        this(requestId,batchId,groupId,orgId,nodeCode,nodeName,buttonCode,buttonName,operationType,reason,objects,
            policyProtocol,materialProdImpact,archiveTreeSaveDraft,null);
    }
    public OperationContext(String requestId, String batchId, String groupId, String orgId,
            String nodeCode, String nodeName, String buttonCode, String buttonName,
            String operationType, String reason, List<BusinessObjectRef> objects, String policyProtocol,
            MaterialProdImpact materialProdImpact,ArchiveTreeSaveDraft archiveTreeSaveDraft,PartnerMainSaveDraft partnerMainSaveDraft) {
        this(requestId,batchId,groupId,orgId,nodeCode,nodeName,buttonCode,buttonName,operationType,reason,objects,
            policyProtocol,materialProdImpact,archiveTreeSaveDraft,partnerMainSaveDraft,null);
    }
    public OperationContext(String requestId, String batchId, String groupId, String orgId,
            String nodeCode, String nodeName, String buttonCode, String buttonName,
            String operationType, String reason, List<BusinessObjectRef> objects, String policyProtocol,
            MaterialProdImpact materialProdImpact,ArchiveTreeSaveDraft archiveTreeSaveDraft,PartnerMainSaveDraft partnerMainSaveDraft,
            PartnerBatchDraft partnerBatchDraft) {
        require(nodeCode, "nodeCode"); require(buttonCode, "buttonCode"); require(operationType, "operationType");
        if (objects == null || objects.isEmpty()) { throw new IllegalArgumentException("No business objects"); }
        // 先检查客户端对象标识唯一，保证未保存的新对象也能在签名、快照及结果回填中稳定关联。
        Set<String> ids = new HashSet<String>();
        for (BusinessObjectRef object : objects) {
            if (object == null || !ids.add(object.getClientObjectId())) { throw new IllegalArgumentException("Duplicate/null object"); }
        }
        // 验证父对象存在和已存对象主键唯一，不能用不同客户端标识重复选择同一业务对象。
        java.util.Map<String,String> parents=new java.util.HashMap<String,String>();
        Set<String> existingKeys=new HashSet<String>();
        for(BusinessObjectRef object:objects){
            String parent=object.getParentClientObjectId();
            if(parent!=null&&!ids.contains(parent))throw new IllegalArgumentException("Unknown parent object");
            parents.put(object.getClientObjectId(),parent);
            boolean supplierTarget=(ArchiveBatchResult.isSupplierFreeze(nodeCode,buttonCode,operationType)
                || SupplierAssignmentResult.isRoute(nodeCode,buttonCode,operationType))
                && "BD_SUPPLIER".equals(object.getObjectType());
            boolean customerTarget=CustomerAssignmentResult.isRoute(nodeCode,buttonCode,operationType)
                && "BD_CUSTOMER".equals(object.getObjectType());
            boolean materialCancelTarget=MaterialCancelAssignmentResult.isRoute(nodeCode,buttonCode,operationType)
                && "BD_MATERIAL".equals(object.getObjectType());
            if(("ASSIGN".equals(operationType)&&"BD_MATERIAL".equals(object.getObjectType())) || materialCancelTarget || supplierTarget || customerTarget){
                if(!OWNER_POLICY_PROTOCOL.equals(policyProtocol)||object.getObjectPk()==null
                        ||object.getTargetOrg()==null||object.getTargetOrg().trim().isEmpty()
                        ||!object.getTargetOrg().equals(object.getTargetOrg().trim())||object.getTargetOrg().length()>20
                        ||object.getDesiredOwnerOrg()!=null||parent!=null)
                    throw new IllegalArgumentException("Archive operation requires an identified object and target organization");
                if(!existingKeys.add(object.getObjectType()+"\u0000"+object.getObjectPk()+"\u0000"+object.getTargetOrg()))
                    throw new IllegalArgumentException("Duplicate archive and target organization");
            }else if(object.getObjectPk()!=null&&!existingKeys.add(object.getObjectType()+"\u0000"+object.getObjectPk()))
                throw new IllegalArgumentException("Duplicate business object key");
        }
        // 逐个追溯父链，检测任意层级的环；不能只检查对象是否直接指向自己。
        for(String id:ids){Set<String> chain=new HashSet<String>();String next=id;while(next!=null){if(!chain.add(next))throw new IllegalArgumentException("Cyclic business-object relation");next=parents.get(next);}}
        this.requestId=requestId; this.batchId=batchId; this.groupId=groupId; this.orgId=orgId;
        this.nodeCode=nodeCode; this.nodeName=nodeName; this.buttonCode=buttonCode; this.buttonName=buttonName;
        this.operationType=operationType; this.reason=reason;
        this.policyProtocol=policyProtocol;
        this.materialProdImpact=materialProdImpact;
        this.archiveTreeSaveDraft=archiveTreeSaveDraft;
        this.partnerMainSaveDraft=partnerMainSaveDraft;
        this.partnerBatchDraft=partnerBatchDraft;
        // 复制列表后只读暴露，防止请求准备后被外部增删授权对象。
        this.objects=Collections.unmodifiableList(new ArrayList<BusinessObjectRef>(objects));
    }
    /** 检查构造上下文所需的非空编码，缺失时抛出 IllegalArgumentException。 */
    private static void require(String value,String name) {
        if(value==null || value.trim().length()==0) throw new IllegalArgumentException(name+" is required");
    }
    /** 复制业务上下文并替换服务端确认的请求、批次及集团标识，原对象保持不变。 */
    public OperationContext withRequest(String id,String batch,String verifiedGroup) {
        return new OperationContext(id,batch,verifiedGroup,orgId,nodeCode,nodeName,buttonCode,buttonName,operationType,reason,objects,policyProtocol,materialProdImpact,archiveTreeSaveDraft,partnerMainSaveDraft,partnerBatchDraft);
    }
    /** 返回贯通准备、签名、执行及日志关联的请求标识。 */
    public String getRequestId(){return requestId;}
    /** 返回同批业务对象共用的批次标识。 */
    public String getBatchId(){return batchId;}
    /** 返回NC 集团主键，服务端从当前身份核定。 */
    public String getGroupId(){return groupId;}
    /** 返回本次业务操作所属业务单元主键。 */
    public String getOrgId(){return orgId;}
    /** 返回NC 功能节点编码。 */
    public String getNodeCode(){return nodeCode;}
    /** 返回操作时保存的功能节点名称。 */
    public String getNodeName(){return nodeName;}
    /** 返回节点内的真实操作按钮编码。 */
    public String getButtonCode(){return buttonCode;}
    /** 返回操作时保存的按钮名称。 */
    public String getButtonName(){return buttonName;}
    /** 返回受映射动作域约束的业务操作类型。 */
    public String getOperationType(){return operationType;}
    /** 返回操作事由文本。 */
    public String getReason(){return reason;}
    /** 返回本次授权或结果关联的业务对象集合。 */
    public List<BusinessObjectRef> getObjects(){return objects;}
    /** 标记客户端支持逐档案归属校验和关闭策略时的服务端执行保护。 */
    public String getPolicyProtocol(){return policyProtocol;}
    public MaterialProdImpact getMaterialProdImpact(){return materialProdImpact;}
    public ArchiveTreeSaveDraft getArchiveTreeSaveDraft(){return archiveTreeSaveDraft;}
    public PartnerMainSaveDraft getPartnerMainSaveDraft(){return partnerMainSaveDraft;}
    public PartnerBatchDraft getPartnerBatchDraft(){return partnerBatchDraft;}
}
