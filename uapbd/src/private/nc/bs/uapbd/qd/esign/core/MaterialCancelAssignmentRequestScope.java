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
import nc.bs.uapbd.qd.esign.persistence.QdEsignStore;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;

/** Candidate complete material-by-target request binding; native material cancellation does not append credit regions. */
public final class MaterialCancelAssignmentRequestScope {
    private MaterialCancelAssignmentRequestScope() { }
    public interface Reader {
        boolean organizationInGroup(String group,String org) throws BusinessException;
        String routeFingerprint(String target) throws BusinessException;
    }
    public static final class Expanded {
        private final Map<String,String> routes;private final List<BusinessObjectRef> units;
        private Expanded(Map<String,String> routes,List<BusinessObjectRef> units) {
            this.routes=Collections.unmodifiableMap(new TreeMap<String,String>(routes));this.units=Collections.unmodifiableList(units);
        }
        public String getRouteFingerprint(String target) { return routes.get(target); }
        public List<BusinessObjectRef> getUnits() { return units; }
    }
    /** Archives must be normalized from server-side master records before binding. */
    public static Expanded expand(String group,String operationOrg,String operation,List<BusinessObjectRef> archives,
            List<String> requested,Reader reader) throws BusinessException {
        EsignValues.require(validPk(group) && validPk(operationOrg) && "CANCEL_ASSIGN".equals(operation)
            && archives!=null && !archives.isEmpty() && archives.size()<=1000 && requested!=null && !requested.isEmpty()
            && requested.size()<=1000 && reader!=null,"物料分配缺少集团、档案或目标组织。");
        Map<String,BusinessObjectRef> normalized=new TreeMap<String,BusinessObjectRef>();
        for(BusinessObjectRef ref:archives) EsignValues.require(ref!=null && "BD_MATERIAL".equals(ref.getObjectType())
            && validPk(ref.getObjectPk()) && ref.getBillCode()!=null && !ref.getBillCode().trim().isEmpty()
            && ref.getParentClientObjectId()==null && ref.getDesiredOwnerOrg()==null && ref.getTargetOrg()==null
            && normalized.put(ref.getObjectPk(),ref)==null,"物料分配档案无效或重复。");
        Set<String> targets=new TreeSet<String>();
        for(String target:requested) EsignValues.require(validPk(target) && targets.add(target)
            && !group.equals(target) && reader.organizationInGroup(group,target),"物料取消分配目标必须是当前集团内启用的业务单元。");
        EsignValues.require((long)normalized.size()*targets.size()<=1000,"物料分配组合超过当前授权上限。");
        StringBuilder evidence=new StringBuilder(pack("material-cancel-assignment-request-v1",group,operationOrg,operation,
            Integer.toString(normalized.size()),Integer.toString(targets.size())));
        for(BusinessObjectRef ref:normalized.values()) evidence.append(pack(ref.getObjectPk(),ref.getBillCode()));
        Map<String,String> routes=new TreeMap<String,String>();
        for(String target:targets) {
            String route=reader.routeFingerprint(target);
            EsignValues.require(route!=null && route.matches("[0-9a-f]{64}"),"无法核实物料分配的页签服务或物理表。");
            routes.put(target,route);evidence.append(pack(target,route));
        }
        String fingerprint=SnapshotSupport.sha256(evidence.toString());List<String> orderedTargets=new ArrayList<String>(targets);
        if("CANCEL_ASSIGN".equals(operation) && orderedTargets.remove(operationOrg)) orderedTargets.add(operationOrg);
        List<BusinessObjectRef> units=new ArrayList<BusinessObjectRef>();Set<String> ids=new HashSet<String>();
        for(BusinessObjectRef ref:normalized.values()) for(String target:orderedTargets) {
            String id="mc:"+fingerprint+":"+SnapshotSupport.sha256(pack(ref.getObjectPk(),target)).substring(0,24);
            EsignValues.require(id.length()<=100 && ids.add(id),"物料分配授权目标标识冲突。");
            units.add(new BusinessObjectRef(id,"BD_MATERIAL",ref.getObjectPk(),ref.getBillCode(),null,null,null,target));
        }
        return new Expanded(routes,units);
    }
    /** Always verify the complete persisted selection, including before each independently committed unit. */
    public static Expanded verify(String group,String operationOrg,String operation,List<BusinessObjectRef> authorized,Reader reader)
            throws BusinessException {
        EsignValues.require(authorized!=null && !authorized.isEmpty() && authorized.size()<=1000,"物料分配授权清单无效。");
        Map<String,BusinessObjectRef> archives=new TreeMap<String,BusinessObjectRef>(),prior=new HashMap<String,BusinessObjectRef>();
        Set<String> targets=new TreeSet<String>();
        for(BusinessObjectRef ref:authorized) {
            EsignValues.require(ref!=null && "BD_MATERIAL".equals(ref.getObjectType()) && validPk(ref.getObjectPk())
                && validPk(ref.getTargetOrg()) && ref.getBillCode()!=null && !ref.getBillCode().trim().isEmpty()
                && ref.getParentClientObjectId()==null && ref.getDesiredOwnerOrg()==null
                && prior.put(ref.getClientObjectId(),ref)==null,"物料分配授权包含无效或重复目标。");
            String[] parts=ref.getClientObjectId().split(":",-1);
            EsignValues.require(parts.length==3 && "mc".equals(parts[0]) && parts[1].matches("[0-9a-f]{64}")
                && parts[2].matches("[0-9a-f]{24}"),"物料分配授权标识无效。");
            targets.add(ref.getTargetOrg());BusinessObjectRef existing=archives.get(ref.getObjectPk());
            if(existing==null) archives.put(ref.getObjectPk(),new BusinessObjectRef(ref.getClientObjectId(),"BD_MATERIAL",
                ref.getObjectPk(),ref.getBillCode(),null,null,ref.getPolicyOwnerOrg()));
            else EsignValues.require(existing.getBillCode().equals(ref.getBillCode()) && same(existing.getPolicyOwnerOrg(),ref.getPolicyOwnerOrg()),
                "物料分配同一档案的编码或所属组织不一致。");
        }
        Expanded current=expand(group,operationOrg,operation,new ArrayList<BusinessObjectRef>(archives.values()),new ArrayList<String>(targets),reader);
        for(BusinessObjectRef ref:current.units) {
            BusinessObjectRef old=prior.remove(ref.getClientObjectId());
            EsignValues.require(old!=null && ref.getObjectPk().equals(old.getObjectPk()) && ref.getBillCode().equals(old.getBillCode())
                && ref.getTargetOrg().equals(old.getTargetOrg()),"物料分配档案、目标或页签配置已变化，请重新发起操作。");
        }
        EsignValues.require(prior.isEmpty(),"物料分配授权目标清单不完整或已变化。");return current;
    }
    public interface RouteReader { String fingerprint(String target) throws BusinessException; }
    public static Reader database(final RouteReader routes) {
        return new Reader() {
            private final QdEsignStore store=new QdEsignStore();
            public boolean organizationInGroup(String group,String org) throws BusinessException { return store.isActiveBusinessUnit(group,org); }
            public String routeFingerprint(String target) throws BusinessException {
                EsignValues.require(routes!=null,"物料分配页签服务不可用。");return routes.fingerprint(target);
            }
        };
    }
    private static boolean same(String left,String right) { return left==null?right==null:left.equals(right); }
    private static boolean validPk(String value) { return value!=null && value.matches("[A-Za-z0-9_-]{1,20}"); }
    private static String pack(String... values) {
        StringBuilder result=new StringBuilder();for(String value:values)
            result.append(value==null?-1:value.length()).append(':').append(value==null?"":value);
        return result.toString();
    }
}

