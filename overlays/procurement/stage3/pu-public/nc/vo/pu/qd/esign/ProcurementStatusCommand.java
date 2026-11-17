package nc.vo.pu.qd.esign;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import nc.vo.pub.AggregatedValueObject;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.pubapp.pattern.model.entity.bill.AbstractBill;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;

public final class ProcurementStatusCommand implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String serviceName;
    private final String methodName;
    private final Object[] arguments;

    public ProcurementStatusCommand(String serviceName, String methodName, Object[] arguments) {
        this.serviceName = serviceName;
        this.methodName = methodName;
        this.arguments = arguments == null ? null : arguments.clone();
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getMethodName() {
        return methodName;
    }

    public Object[] getArguments() {
        return arguments == null ? null : arguments.clone();
    }

    public static String serviceFor(String node, String button) throws BusinessException {
        return ProcurementStatusRoute.forAction(node, button).service().getName();
    }

    public static String methodFor(String node, String button) throws BusinessException {
        return ProcurementStatusRoute.forAction(node, button).method();
    }

    public OperationContext draft(String node, String button, String loginGroup, AggregatedValueObject[] originalSelection)
            throws BusinessException {
        List<OperationContext> scopes = drafts(node, button, loginGroup, originalSelection);
        if (scopes.size() != 1) {
            throw new BusinessException("一次采购状态签名须使用同一业务单元的单据。");
        }
        return scopes.get(0);
    }

    public List<OperationContext> drafts(String node, String button, String loginGroup,
            AggregatedValueObject[] originalSelection) throws BusinessException {
        ProcurementStatusRoute route = ProcurementStatusRoute.forAction(node, button);
        Map<String, AbstractBill> originals = new LinkedHashMap<String, AbstractBill>();
        if (originalSelection == null || !ProcurementStatusRoute.validKey(loginGroup)) {
            throw new BusinessException("采购状态操作缺少原选单或当前集团。");
        }
        for (AggregatedValueObject original : originalSelection) {
            SuperVO header = route.header(original);
            String pk = header.getPrimaryKey();
            if (originals.put(pk, (AbstractBill) original) != null) {
                throw new BusinessException("采购状态操作包含重复的原选单。");
            }
        }
        Map<String, List<BusinessObjectRef>> byOrg = new LinkedHashMap<String, List<BusinessObjectRef>>();
        for (AbstractBill input : route.bills(this).values()) {
            String pk = route.header(input).getPrimaryKey();
            AbstractBill original = originals.get(pk);
            SuperVO header = route.header(original);
            String currentOrg = ProcurementStatusRoute.text(header, "pk_org");
            if (!loginGroup.equals(header.getAttributeValue("pk_group")) || !ProcurementStatusRoute.validKey(currentOrg)) {
                throw new BusinessException("采购状态操作须使用当前集团内有业务单元的单据。");
            }
            Map<String, SuperVO> selected = route.isRow() ? route.rows(input) : null;
            if (selected != null && !route.rows(original).keySet().containsAll(selected.keySet())) {
                throw new BusinessException("采购状态操作行超出原选单范围。");
            }
            List<BusinessObjectRef> refs = byOrg.get(currentOrg);
            if (refs == null) {
                refs = new ArrayList<BusinessObjectRef>();
                byOrg.put(currentOrg, refs);
            }
            refs.add(new BusinessObjectRef(route.clientId(pk, selected), route.objectType(), pk,
                    ProcurementStatusRoute.text(header, "vbillcode"), null));
        }
        List<OperationContext> scopes = new ArrayList<OperationContext>(byOrg.size());
        for (Map.Entry<String, List<BusinessObjectRef>> org : byOrg.entrySet()) {
            scopes.add(new OperationContext(null, null, loginGroup, org.getKey(), node, route.nodeName(), button,
                    route.buttonName(), route.operationType(), null, org.getValue()));
        }
        return scopes;
    }
}
