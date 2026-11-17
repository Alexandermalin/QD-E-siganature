package nc.bs.uapbd.qd.esign.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import nc.bs.uapbd.qd.esign.audit.SnapshotSupport;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;

/** Candidate binding for the complete customer-by-target assignment request, including NC-derived regions. */
public final class CustomerAssignmentRequestScope {
    private CustomerAssignmentRequestScope() { }
    public interface RouteReader {
        /** Fingerprint the server-inspected native page services and physical mappings for this target. */
        String fingerprint(String target) throws BusinessException;
    }

    public static final class Expanded {
        private final CustomerAssignmentTargets.Selection targets;
        private final CustomerAssignmentOrder.Selection hierarchy;
        private final Map<String,String> routeFingerprints;
        private final List<BusinessObjectRef> units;
        private Expanded(CustomerAssignmentTargets.Selection targets,CustomerAssignmentOrder.Selection hierarchy,
                Map<String,String> routes,List<BusinessObjectRef> units) {
            this.targets=targets;this.hierarchy=hierarchy;this.routeFingerprints=Collections.unmodifiableMap(new TreeMap<String,String>(routes));
            this.units=Collections.unmodifiableList(units);
        }
        public CustomerAssignmentTargets.Selection getTargets() { return targets; }
        public CustomerAssignmentOrder.Selection getHierarchy() { return hierarchy; }
        public String getRouteFingerprint(String target) { return routeFingerprints.get(target); }
        public List<BusinessObjectRef> getUnits() { return units; }
    }

    /** Call with server-normalized archives, not client-provided owner organizations or bill codes. */
    public static Expanded expand(String group,String operationOrg,String operation,List<BusinessObjectRef> archives,
            List<String> selected,CustomerAssignmentTargets.Reader reader,CustomerAssignmentOrder.Reader hierarchyReader,
            RouteReader routeReader) throws BusinessException {
        EsignValues.require(validPk(group) && validPk(operationOrg) && archives!=null && !archives.isEmpty()
            && archives.size()<=1000 && routeReader!=null,"客户分配缺少集团、操作组织或档案。");
        Map<String,BusinessObjectRef> normalized=new TreeMap<String,BusinessObjectRef>();
        for(BusinessObjectRef ref:archives) {
            EsignValues.require(ref!=null && "BD_CUSTOMER".equals(ref.getObjectType()) && validPk(ref.getObjectPk())
                && ref.getBillCode()!=null && !ref.getBillCode().trim().isEmpty() && ref.getParentClientObjectId()==null
                && ref.getDesiredOwnerOrg()==null && ref.getTargetOrg()==null
                && normalized.put(ref.getObjectPk(),ref)==null,"客户分配档案重复或不完整。");
        }
        CustomerAssignmentTargets.Selection targetSet=CustomerAssignmentTargets.select(group,operation,selected,reader);
        CustomerAssignmentOrder.Selection hierarchy=CustomerAssignmentOrder.select(group,operation,
            new ArrayList<String>(normalized.keySet()),hierarchyReader);
        EsignValues.require((long)normalized.size()*targetSet.getTargets().size()<=1000,
            "客户分配档案与目标组织的组合超过当前授权上限。");
        StringBuilder evidence=new StringBuilder(pack("customer-assignment-request-v1",group,operationOrg,operation,
            targetSet.getFingerprint(),hierarchy.getFingerprint(),Integer.toString(normalized.size()),Integer.toString(targetSet.getTargets().size())));
        for(BusinessObjectRef ref:normalized.values()) evidence.append(pack(ref.getObjectPk(),ref.getBillCode()));
        Map<String,String> routes=new TreeMap<String,String>();
        for(String target:targetSet.getTargets()) {
            String route=routeReader.fingerprint(target);
            EsignValues.require(route!=null && route.matches("[0-9a-f]{64}"),"无法核实客户分配的页签服务或物理表。");
            routes.put(target,route);evidence.append(pack(target,route));
        }
        String fingerprint=SnapshotSupport.sha256(evidence.toString());
        Set<String> selectedTargets=new HashSet<String>(targetSet.getSelected()),ids=new HashSet<String>();
        List<BusinessObjectRef> units=new ArrayList<BusinessObjectRef>();
        List<String> executionTargets=new ArrayList<String>(targetSet.getTargets());
        if("CANCEL_ASSIGN".equals(operation) && executionTargets.remove(operationOrg)) executionTargets.add(operationOrg);
        for(String customer:hierarchy.getOrder()) for(String target:executionTargets) {
            BusinessObjectRef ref=normalized.get(customer);
            String identity=SnapshotSupport.sha256(pack(ref.getObjectPk(),target)).substring(0,24);
            String client="ca:"+(selectedTargets.contains(target)?"S":"D")+":"+fingerprint+":"+identity;
            EsignValues.require(client.length()<=100 && ids.add(client),"客户分配授权目标标识冲突。");
            units.add(new BusinessObjectRef(client,"BD_CUSTOMER",ref.getObjectPk(),ref.getBillCode(),null,null,null,target));
        }
        return new Expanded(targetSet,hierarchy,routes,units);
    }

    /**
     * Verify against the entire persisted request, including before each independently committed unit.
     * The selected markers reconstruct the original user selection; derived targets are recomputed on the server.
     */
    public static Expanded verify(String group,String operationOrg,String operation,List<BusinessObjectRef> authorized,
            CustomerAssignmentTargets.Reader reader,CustomerAssignmentOrder.Reader hierarchyReader,RouteReader routeReader) throws BusinessException {
        EsignValues.require(authorized!=null && !authorized.isEmpty() && authorized.size()<=1000,
            "客户分配授权清单为空或超过上限。");
        Set<String> selected=new TreeSet<String>();Map<String,BusinessObjectRef> archives=new TreeMap<String,BusinessObjectRef>();
        Map<String,BusinessObjectRef> prior=new HashMap<String,BusinessObjectRef>();
        for(BusinessObjectRef ref:authorized) {
            EsignValues.require(ref!=null && "BD_CUSTOMER".equals(ref.getObjectType()) && validPk(ref.getObjectPk())
                && validPk(ref.getTargetOrg()) && ref.getBillCode()!=null && !ref.getBillCode().trim().isEmpty()
                && ref.getParentClientObjectId()==null && ref.getDesiredOwnerOrg()==null
                && prior.put(ref.getClientObjectId(),ref)==null,"客户分配授权包含无效或重复的目标。");
            String[] parts=ref.getClientObjectId().split(":",-1);
            EsignValues.require(parts.length==4 && "ca".equals(parts[0]) && ("S".equals(parts[1]) || "D".equals(parts[1]))
                && parts[2].matches("[0-9a-f]{64}") && parts[3].matches("[0-9a-f]{24}"),"客户分配授权标识无效。");
            if("S".equals(parts[1])) selected.add(ref.getTargetOrg());
            BusinessObjectRef existing=archives.get(ref.getObjectPk());
            if(existing==null) {
                archives.put(ref.getObjectPk(),new BusinessObjectRef(ref.getClientObjectId(),ref.getObjectType(),ref.getObjectPk(),
                    ref.getBillCode(),null,null,ref.getPolicyOwnerOrg()));
            } else {
                EsignValues.require(existing.getBillCode().equals(ref.getBillCode())
                    && same(existing.getPolicyOwnerOrg(),ref.getPolicyOwnerOrg()),"客户分配同一档案的编码或所属组织不一致。");
            }
        }
        Expanded current=expand(group,operationOrg,operation,new ArrayList<BusinessObjectRef>(archives.values()),
            new ArrayList<String>(selected),reader,hierarchyReader,routeReader);
        for(BusinessObjectRef ref:current.units) {
            BusinessObjectRef old=prior.remove(ref.getClientObjectId());
            EsignValues.require(old!=null && ref.getObjectPk().equals(old.getObjectPk())
                && ref.getBillCode().equals(old.getBillCode()) && ref.getTargetOrg().equals(old.getTargetOrg()),
                "客户分配目标、档案或信用控制域已变化，请重新发起操作。");
        }
        EsignValues.require(prior.isEmpty(),"客户分配授权目标清单不完整或已变化。");
        return current;
    }

    private static boolean same(String left,String right) { return left==null?right==null:left.equals(right); }
    private static boolean validPk(String value) { return value!=null && value.matches("[A-Za-z0-9_-]{1,20}"); }
    private static String pack(String... values) {
        StringBuilder result=new StringBuilder();
        for(String value:values) result.append(value==null?-1:value.length()).append(':').append(value==null?"":value);
        return result.toString();
    }
}
