package nc.bs.uapbd.qd.esign.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import nc.bs.uapbd.qd.esign.audit.SnapshotSupport;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;

/**
 * Binds each selected main archive to the structure and related rows that its native delete can affect.
 * Child references identify other archives whose owner policies also apply to the same delete unit.
 */
public final class PartnerMainDeleteScope {
    private PartnerMainDeleteScope() { }

    public interface Reader extends PartnerMainSaveImpact.Reader {
        List<String> dependencies(String group,String domain,String archive,boolean lock) throws BusinessException;
    }

    public static List<BusinessObjectRef> expand(String group,String operationOrg,String domain,
            List<BusinessObjectRef> selected,Reader reader,boolean lock) throws BusinessException {
        EsignValues.require(valid(group) && valid(operationOrg) && validDomain(domain) && selected!=null
            && !selected.isEmpty() && reader!=null,"客户或供应商删除缺少集团、组织或所选档案。");
        List<BusinessObjectRef> result=new ArrayList<BusinessObjectRef>();
        Set<String> roots=new HashSet<String>(),ids=new HashSet<String>();
        for(BusinessObjectRef draft:selected) {
            EsignValues.require(draft!=null && domain.equals(draft.getObjectType()) && valid(draft.getObjectPk())
                && draft.getParentClientObjectId()==null && draft.getDesiredOwnerOrg()==null
                && (draft.getTargetOrg()==null || operationOrg.equals(draft.getTargetOrg()))
                && roots.add(draft.getObjectPk()),"客户或供应商删除档案无效或重复。");
            Plan plan=plan(group,operationOrg,domain,draft.getObjectPk(),reader,lock);
            String rootId="partner-delete:"+plan.fingerprint;
            EsignValues.require(rootId.length()<=100 && ids.add(rootId),"客户或供应商删除授权标识重复。");
            result.add(new BusinessObjectRef(rootId,domain,plan.root.id,plan.root.code,null));
            for(PartnerMainSaveImpact.Archive affected:plan.affected.values()) {
                String childId="partner-related:"+SnapshotSupport.sha256(pack(rootId,affected.domain,affected.id));
                EsignValues.require(childId.length()<=100 && ids.add(childId),"客户或供应商删除影响档案重复。");
                result.add(new BusinessObjectRef(childId,affected.domain,affected.id,affected.code,rootId));
            }
        }
        return Collections.unmodifiableList(result);
    }

    /** Reconstructs every unit from current server data and compares the complete authorized set. */
    public static void verify(String group,String operationOrg,String domain,List<BusinessObjectRef> authorized,
            Reader reader,boolean lock) throws BusinessException {
        EsignValues.require(authorized!=null && !authorized.isEmpty(),"客户或供应商删除授权范围为空。");
        List<BusinessObjectRef> roots=new ArrayList<BusinessObjectRef>();
        Map<String,BusinessObjectRef> expected=new HashMap<String,BusinessObjectRef>();
        for(BusinessObjectRef ref:authorized) {
            EsignValues.require(ref!=null && expected.put(ref.getClientObjectId(),ref)==null
                && ref.getDesiredOwnerOrg()==null && (ref.getTargetOrg()==null || operationOrg.equals(ref.getTargetOrg())),
                "客户或供应商删除授权对象无效或重复。");
            if(ref.getParentClientObjectId()==null) roots.add(ref);
        }
        for(BusinessObjectRef current:expand(group,operationOrg,domain,roots,reader,lock)) {
            BusinessObjectRef old=expected.remove(current.getClientObjectId());
            EsignValues.require(old!=null && Objects.equals(old.getObjectType(),current.getObjectType())
                && Objects.equals(old.getObjectPk(),current.getObjectPk()) && Objects.equals(old.getBillCode(),current.getBillCode())
                && Objects.equals(old.getParentClientObjectId(),current.getParentClientObjectId()),
                "客户或供应商删除影响范围已变化，请重新发起操作。");
        }
        EsignValues.require(expected.isEmpty(),"客户或供应商删除授权范围不完整。");
    }

    private static Plan plan(String group,String operationOrg,String domain,String pk,Reader reader,boolean lock)
            throws BusinessException {
        PartnerMainSaveImpact.Archive root=archive(reader,group,domain,pk,lock);
        Map<String,PartnerMainSaveImpact.Archive> affected=new TreeMap<String,PartnerMainSaveImpact.Archive>();
        String otherDomain="BD_CUSTOMER".equals(domain)?"BD_SUPPLIER":"BD_CUSTOMER";
        PartnerMainSaveImpact.Archive other=reader.archive(group,otherDomain,pk,lock);
        if(other!=null) {
            requireArchive(other,group,otherDomain,pk);
            affected.put(otherDomain+"\u0000"+pk,other);
        }
        PartnerMainSaveImpact.Structure selected=reader.structure(domain,pk,lock);
        List<PartnerMainSaveImpact.Structure> children=reader.children(domain,pk,lock);
        String parent=root.parent==null || root.parent.trim().isEmpty()?null:root.parent;
        List<PartnerMainSaveImpact.Structure> siblings=parent==null
            ?Collections.<PartnerMainSaveImpact.Structure>emptyList():reader.children(domain,parent,lock);
        checkRows(children,pk);
        checkRows(siblings,parent);
        PartnerMainSaveImpact.Structure parentRow=parent==null?null:reader.structure(domain,parent,lock);
        if(selected!=null) EsignValues.require(pk.equals(selected.id),"待删档案的结构主键不一致。");
        if(parentRow!=null) EsignValues.require(parent.equals(parentRow.id),"待删档案的上级结构主键不一致。");
        boolean removesSelected=selected!=null && children.isEmpty();
        boolean parentHasOtherChild=false;
        for(PartnerMainSaveImpact.Structure row:siblings)
            if(!removesSelected || !pk.equals(row.id)) parentHasOtherChild=true;
        if("BD_CUSTOMER".equals(domain) && parentRow!=null && !parentHasOtherChild && !"~".equals(parent)) {
            PartnerMainSaveImpact.Archive parentArchive=archive(reader,group,domain,parent,lock);
            affected.put(domain+"\u0000"+parent,parentArchive);
        }
        List<String> dependencies=reader.dependencies(group,domain,pk,lock);
        EsignValues.require(dependencies!=null,"无法核实客户或供应商删除的关联数据。");
        Set<String> unique=new TreeSet<String>();
        for(String fact:dependencies) EsignValues.require(fact!=null && unique.add(fact),"删除关联数据缺失或重复。");
        StringBuilder evidence=new StringBuilder(pack("partner-delete-v1",group,operationOrg,domain,pk,
            root.owner,root.code,parent,archiveEvidence(other),structureEvidence(selected),structureEvidence(parentRow)));
        appendRows(evidence,children);
        appendRows(evidence,siblings);
        for(PartnerMainSaveImpact.Archive archive:affected.values()) evidence.append(pack(archiveEvidence(archive)));
        for(String fact:unique) evidence.append(pack(fact));
        return new Plan(root,affected,SnapshotSupport.sha256(evidence.toString()));
    }

    private static PartnerMainSaveImpact.Archive archive(Reader reader,String group,String domain,String pk,boolean lock)
            throws BusinessException {
        PartnerMainSaveImpact.Archive row=reader.archive(group,domain,pk,lock);
        requireArchive(row,group,domain,pk);
        return row;
    }

    private static void requireArchive(PartnerMainSaveImpact.Archive row,String group,String domain,String pk)
            throws BusinessException {
        EsignValues.require(row!=null && domain.equals(row.domain) && pk.equals(row.id) && group.equals(row.group)
            && valid(row.owner) && row.code!=null && !row.code.trim().isEmpty(),"受影响档案不存在或所属集团、组织不正确。");
    }

    private static void checkRows(List<PartnerMainSaveImpact.Structure> rows,String parent) throws BusinessException {
        EsignValues.require(rows!=null,"客户或供应商结构范围不可用。");
        Set<String> ids=new HashSet<String>();
        for(PartnerMainSaveImpact.Structure row:rows) EsignValues.require(row!=null && valid(row.id)
            && Objects.equals(parent,row.parent) && ids.add(row.id),"客户或供应商结构范围无效或重复。");
    }

    private static void appendRows(StringBuilder result,List<PartnerMainSaveImpact.Structure> rows) {
        Set<String> ordered=new TreeSet<String>();
        for(PartnerMainSaveImpact.Structure row:rows) ordered.add(structureEvidence(row));
        result.append(pack(Integer.toString(ordered.size())));
        for(String row:ordered) result.append(pack(row));
    }

    private static String structureEvidence(PartnerMainSaveImpact.Structure row) {
        return row==null?null:pack(row.id,row.parent,row.innercode,row.deleted);
    }

    private static String archiveEvidence(PartnerMainSaveImpact.Archive row) {
        return row==null?null:pack(row.domain,row.id,row.group,row.owner,row.code,row.parent);
    }

    private static boolean validDomain(String domain) {
        return "BD_CUSTOMER".equals(domain) || "BD_SUPPLIER".equals(domain);
    }

    private static boolean valid(String value) {
        return value!=null && value.matches("[A-Za-z0-9_-]{1,20}");
    }

    private static String pack(String... values) {
        StringBuilder result=new StringBuilder();
        for(String value:values) result.append(value==null?-1:value.length()).append(':').append(value==null?"":value);
        return result.toString();
    }

    private static final class Plan {
        final PartnerMainSaveImpact.Archive root;
        final Map<String,PartnerMainSaveImpact.Archive> affected;
        final String fingerprint;
        Plan(PartnerMainSaveImpact.Archive root,Map<String,PartnerMainSaveImpact.Archive> affected,String fingerprint) {
            this.root=root;
            this.affected=affected;
            this.fingerprint=fingerprint;
        }
    }
}
