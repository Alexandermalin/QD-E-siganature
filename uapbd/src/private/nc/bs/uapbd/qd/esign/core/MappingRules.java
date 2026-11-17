package nc.bs.uapbd.qd.esign.core;
import java.util.*;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.SalesOtherNodeScope;
import nc.vo.uapbd.qd.esign.SapplyLineScope;

/** 验证节点映射允许的动作集合和业务对象类型；仅绑定按钮及适配器不足以授权任意动作。 */
public final class MappingRules {
    /** 工具类不提供实例，使用静态方法完成本类职责。 */
    private MappingRules(){}
    /** 校验映射中的操作集合和对象类型，阻止把同一按钮的授权用于其他动作或对象域。 */
    public static void requireDomain(Map<String,Object> mapping,String operation,List<BusinessObjectRef> objects) throws BusinessException {
        EsignValues.require(mapping!=null&&EsignValues.yes(mapping,"validated"),"Node adapter has not been verified");
        String allowed=EsignValues.text(mapping,"operation_types"),type=EsignValues.text(mapping,"object_type");
        EsignValues.require(allowed!=null&&type!=null&&operation!=null,"Node action/object domain is not registered");
        Set<String> actions=new HashSet<String>();for(String action:allowed.split(","))actions.add(action.trim());
        EsignValues.require(actions.contains(operation),"Operation is outside the registered button action domain");
        if ("nc.itf.ic.qd.esign.ISapplyLineOperationAdapter".equals(EsignValues.text(mapping,"adapter_class"))) {
            EsignValues.require("IC_SAPPLY".equals(type) && SapplyLineScope.matches(EsignValues.text(mapping,"node_code"),
                    EsignValues.text(mapping,"button_code"),operation),"出库申请行操作映射不匹配。");
            SapplyLineScope.require(objects);
            return;
        }
        if(BankAccountRoute.isAdapter(mapping)) {
            BankAccountRoute.requireMapping(mapping,operation);
            BusinessObjectRef root=null;
            for(BusinessObjectRef object:objects) if(object!=null && object.getParentClientObjectId()==null) {
                EsignValues.require(root==null && type.equals(object.getObjectType()),"银行账户选中对象不匹配。");root=object;
            }
            EsignValues.require(root!=null,"银行账户缺少选中关系。");
            for(BusinessObjectRef object:objects) EsignValues.require(object!=null && object.getObjectPk()!=null
                && ("BD_CUSTOMER_BANK".equals(object.getObjectType()) || "BD_SUPPLIER_BANK".equals(object.getObjectType()))
                && object.getDesiredOwnerOrg()==null && (object==root || root.getClientObjectId().equals(object.getParentClientObjectId())),
                "银行账户影响范围包含非法对象或关系。");
            return;
        }
        if(PartnerMainSaveRoute.isAdapter(mapping)) {
            PartnerMainSaveRoute.requireMapping(mapping,operation);
            BusinessObjectRef root=null;
            for(BusinessObjectRef object:objects) if(object!=null && object.getParentClientObjectId()==null) {
                EsignValues.require(root==null && type.equals(object.getObjectType()),"客户或供应商保存选中对象不匹配。");root=object;
            }
            EsignValues.require(root!=null,"客户或供应商保存缺少选中档案。");
            boolean insert="INSERT".equals(operation);
            for(BusinessObjectRef object:objects) EsignValues.require(object!=null
                && (insert && object==root?object.getObjectPk()==null:object.getObjectPk()!=null)
                && ("BD_CUSTOMER".equals(object.getObjectType()) || "BD_SUPPLIER".equals(object.getObjectType()))
                && (!insert || type.equals(object.getObjectType()))
                && (object==root || root.getClientObjectId().equals(object.getParentClientObjectId())),
                "客户或供应商保存影响范围包含非法对象或关系。");
            return;
        }
        if(PartnerMainDeleteRoute.isMapping(mapping)) {
            PartnerMainDeleteRoute.requireMapping(mapping,operation);
            Map<String,BusinessObjectRef> roots=new HashMap<String,BusinessObjectRef>();Set<String> ids=new HashSet<String>();
            EsignValues.require(objects!=null && !objects.isEmpty(),"客户或供应商删除缺少授权对象。");
            for(BusinessObjectRef object:objects) {
                EsignValues.require(object!=null && object.getObjectPk()!=null && object.getDesiredOwnerOrg()==null
                    && ("BD_CUSTOMER".equals(object.getObjectType()) || "BD_SUPPLIER".equals(object.getObjectType()))
                    && ids.add(object.getClientObjectId()),"客户或供应商删除授权对象无效或重复。");
                if(object.getParentClientObjectId()==null) {
                    EsignValues.require(type.equals(object.getObjectType()) && roots.put(object.getClientObjectId(),object)==null,
                        "客户或供应商删除选中档案不匹配。");
                }
            }
            EsignValues.require(!roots.isEmpty(),"客户或供应商删除缺少选中档案。");
            for(BusinessObjectRef object:objects) if(object.getParentClientObjectId()!=null)
                EsignValues.require(roots.containsKey(object.getParentClientObjectId()),"客户或供应商删除关联档案缺少选中主档。");
            return;
        }
        // 销售管理其他三节点：根对象必须与映射对象类型一致，行对象只允许已核实的行类型且必须挂在根对象下。
        String salesAdapter = EsignValues.text(mapping, "adapter_class");
        if (SalesOtherNodeScope.isAdapter(salesAdapter)) {
            String lineType = SalesOtherNodeScope.lineObjectTypeForAdapter(salesAdapter);
            BusinessObjectRef salesRoot = null;
            for (BusinessObjectRef object : objects) if (object != null && object.getParentClientObjectId() == null) {
                EsignValues.require(salesRoot == null && type.equals(object.getObjectType()),
                        "销售管理受控动作选中单据不匹配。");
                salesRoot = object;
            }
            EsignValues.require(salesRoot != null, "销售管理受控动作缺少选中单据。");
            for (BusinessObjectRef object : objects) {
                EsignValues.require(object != null && object.getObjectPk() != null
                        && (type.equals(object.getObjectType())
                            || (object.getParentClientObjectId() != null && lineType != null
                                && lineType.equals(object.getObjectType())))
                        && (object == salesRoot || salesRoot.getClientObjectId().equals(object.getParentClientObjectId())),
                        "销售管理受控动作影响范围包含非法对象或关系。");
            }
            return;
        }
        for(BusinessObjectRef object:objects)EsignValues.require(type.equals(object.getObjectType()),"Object type is outside the registered button domain");
    }
}
