package nc.vo.uapbd.qd.esign;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import nc.vo.pub.BusinessException;

/** The authorized set of one outbound request and its selected persisted body rows. */
public final class SapplyLineScope {
    public static final String LINE_TYPE = "IC_SAPPLY_LINE";
    private SapplyLineScope() { }

    public static boolean matches(String node,String button,String operation) {
        return "40080801".equals(node) && ("LineClose".equals(button) || "LineOpen".equals(button))
                && "UPDATE".equals(operation);
    }

    public static Selection require(OperationContext context) throws BusinessException {
        if (context == null || !matches(context.getNodeCode(),context.getButtonCode(),context.getOperationType()))
            throw new BusinessException("出库申请行操作范围无效。");
        return require(context.getObjects());
    }

    public static Selection require(List<BusinessObjectRef> objects) throws BusinessException {
        if (objects == null || objects.size() < 2 || objects.size() > 1000)
            throw new BusinessException("请选择一张出库申请单及不超过 999 行。");
        BusinessObjectRef root = null;
        List<BusinessObjectRef> lines = new ArrayList<BusinessObjectRef>();
        Set<String> clientIds = new HashSet<String>(), linePks = new HashSet<String>();
        for (BusinessObjectRef ref : objects) {
            if (ref == null || !valid(ref.getClientObjectId()) || ref.getClientObjectId().length()>100
                    || !valid(ref.getObjectPk()) || !ref.getObjectPk().matches("[A-Za-z0-9_-]{1,20}")
                    || !clientIds.add(ref.getClientObjectId()) || ref.getDesiredOwnerOrg() != null
                    || ref.getPolicyOwnerOrg() != null || ref.getTargetOrg() != null)
                throw new BusinessException("出库申请行授权对象无效或重复。");
            if (ref.getParentClientObjectId() == null) {
                if (root != null || !InventoryBillScope.SAPPLY.equals(ref.getObjectType()))
                    throw new BusinessException("行操作必须且只能包含一张出库申请单。");
                root = ref;
            } else {
                if (!LINE_TYPE.equals(ref.getObjectType()) || !linePks.add(ref.getObjectPk()))
                    throw new BusinessException("出库申请行类型或主键无效。");
                lines.add(ref);
            }
        }
        if (root == null || lines.isEmpty()) throw new BusinessException("出库申请单或所选行缺失。");
        for (BusinessObjectRef line : lines) if (!root.getClientObjectId().equals(line.getParentClientObjectId()))
            throw new BusinessException("出库申请行不属于已授权的单据。");
        return new Selection(root,lines);
    }

    private static boolean valid(String value) {
        return value != null && !value.trim().isEmpty() && value.equals(value.trim()) && !"~".equals(value);
    }

    public static final class Selection {
        private final BusinessObjectRef root;
        private final List<BusinessObjectRef> lines;
        private Selection(BusinessObjectRef root,List<BusinessObjectRef> lines) {
            this.root=root; this.lines=Collections.unmodifiableList(new ArrayList<BusinessObjectRef>(lines));
        }
        public BusinessObjectRef getRoot() { return root; }
        public List<BusinessObjectRef> getLines() { return lines; }
        public Set<String> lineKeys() {
            Set<String> keys=new HashSet<String>(); for(BusinessObjectRef line:lines)keys.add(line.getObjectPk());
            return Collections.unmodifiableSet(keys);
        }
    }
}
