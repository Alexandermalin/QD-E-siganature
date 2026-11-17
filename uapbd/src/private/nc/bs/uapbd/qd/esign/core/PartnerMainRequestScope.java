package nc.bs.uapbd.qd.esign.core;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import nc.bs.uapbd.qd.esign.audit.SnapshotSupport;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.PartnerMainSaveDraft;

/** Candidate signed request identity binds proposed parent, paired-master flag and native structure evidence. */
public final class PartnerMainRequestScope {
    private PartnerMainRequestScope() { }
    public static final class Expanded {
        private final PartnerMainSaveImpact.Selection impact;private final List<BusinessObjectRef> objects;
        private Expanded(PartnerMainSaveImpact.Selection impact,List<BusinessObjectRef> objects) {
            this.impact=impact;this.objects=Collections.unmodifiableList(objects);
        }
        public PartnerMainSaveImpact.Selection getImpact() { return impact; }
        public List<BusinessObjectRef> getObjects() { return objects; }
    }
    public static Expanded expand(String group,BusinessObjectRef selected,String desiredParent,boolean counterpart,
            PartnerMainSaveImpact.Reader reader,boolean lock) throws BusinessException {
        desiredParent=PartnerMainSaveDraft.normalizeParent(desiredParent);
        EsignValues.require(selected!=null && selected.getParentClientObjectId()==null && validPk(selected.getObjectPk())
            && (desiredParent==null || validPk(desiredParent) && !"-".equals(desiredParent)),"客户或供应商保存对象或上级无效。");
        PartnerMainSaveImpact.Selection impact=PartnerMainSaveImpact.select(group,selected.getObjectType(),selected.getObjectPk(),
            desiredParent,counterpart,reader,lock);
        String parent=impact.getDesiredParent(),rootId="main:"+(impact.hasCounterpart()?"1":"0")+":"+(parent==null?"-":parent)+":"+impact.fingerprint();
        EsignValues.require(rootId.length()<=100,"客户或供应商授权标识超出数据库限制。");
        PartnerMainSaveImpact.Archive selectedArchive=null;
        for(PartnerMainSaveImpact.Archive archive:impact.getArchives()) if(selected.getObjectType().equals(archive.domain)
                && selected.getObjectPk().equals(archive.id)) selectedArchive=archive;
        EsignValues.require(selectedArchive!=null,"客户或供应商保存影响范围缺少所选主档。");
        boolean transfer=selected.getDesiredOwnerOrg()!=null && !selected.getDesiredOwnerOrg().equals(selectedArchive.owner);
        List<BusinessObjectRef> result=new ArrayList<BusinessObjectRef>();
        result.add(new BusinessObjectRef(rootId,selectedArchive.domain,selectedArchive.id,selectedArchive.code,null,selected.getDesiredOwnerOrg()));
        for(PartnerMainSaveImpact.Archive archive:impact.getArchives()) {
            if(selectedArchive.domain.equals(archive.domain) && selectedArchive.id.equals(archive.id)) continue;
            boolean otherMaster=!selectedArchive.domain.equals(archive.domain) && selectedArchive.id.equals(archive.id);
            String desired=transfer && otherMaster?selected.getDesiredOwnerOrg():null;
            String id="main-child-"+UUID.nameUUIDFromBytes((rootId+"\u0000"+archive.domain+"\u0000"+archive.id).getBytes(Charset.forName("UTF-8"))).toString();
            result.add(new BusinessObjectRef(id,archive.domain,archive.id,archive.code,rootId,desired));
        }
        return new Expanded(impact,result);
    }
    public static List<BusinessObjectRef> expandInsert(String group,BusinessObjectRef selected,String proposedParent,
            PartnerMainSaveImpact.Reader reader,boolean lock) throws BusinessException {
        String parent=PartnerMainSaveDraft.normalizeParent(proposedParent);
        EsignValues.require(selected!=null && selected.getObjectPk()==null && selected.getParentClientObjectId()==null
            && group.equals(selected.getDesiredOwnerOrg()) && (parent==null || validPk(parent) && !"-".equals(parent)),
            "集团主档新增缺少所属组织或上级档案不正确。");
        String domain=selected.getObjectType();
        PartnerMainSaveImpact.Archive upper=null;
        String evidence=group+"\u0000"+domain;
        if(parent!=null) {
            upper=reader.archive(group,domain,parent,lock);
            EsignValues.require(upper!=null && group.equals(upper.group) && group.equals(upper.owner) && parent.equals(upper.id)
                && domain.equals(upper.domain) && upper.code!=null,"集团主档的新上级不属于当前集团。");
            PartnerMainSaveImpact.Structure structure=reader.structure(domain,parent,lock);
            evidence+="\u0000"+upper.code+"\u0000"+upper.parent
                +"\u0000"+(structure==null?"":structure.parent+"\u0000"+structure.innercode+"\u0000"+structure.deleted);
        }
        String id="main-new:"+(parent==null?"-":parent)+":"+SnapshotSupport.sha256(evidence);
        List<BusinessObjectRef> result=new ArrayList<BusinessObjectRef>();
        result.add(new BusinessObjectRef(id,domain,null,selected.getBillCode(),null,group));
        if(upper!=null) result.add(new BusinessObjectRef("main-parent-"+parent,domain,parent,upper.code,id));
        return result;
    }
    public static String verifyInsert(String group,List<BusinessObjectRef> authorized,PartnerMainSaveImpact.Reader reader,boolean lock)
            throws BusinessException {
        BusinessObjectRef root=root(authorized);
        String[] pieces=root.getClientObjectId().split(":",-1);
        EsignValues.require(pieces.length==3 && "main-new".equals(pieces[0])
            && ("-".equals(pieces[1]) || validPk(pieces[1])) && pieces[2].matches("[0-9a-f]{64}"),"新增主档的授权标识无效。");
        String parent="-".equals(pieces[1])?null:pieces[1];
        verifyObjects(authorized,expandInsert(group,root,parent,reader,lock));
        return parent;
    }
    /** Reconstruct the draft from the persisted root and compare the complete server-generated object set. */
    public static Expanded verify(String group,List<BusinessObjectRef> authorized,PartnerMainSaveImpact.Reader reader,boolean lock)
            throws BusinessException {
        BusinessObjectRef root=root(authorized);
        String[] pieces=root.getClientObjectId().split(":",-1);
        EsignValues.require(pieces.length==4 && "main".equals(pieces[0]) && ("0".equals(pieces[1]) || "1".equals(pieces[1]))
            && ("-".equals(pieces[2]) || validPk(pieces[2])) && pieces[3].matches("[0-9a-f]{64}"),"客户或供应商保存授权标识无效。");
        Expanded current=expand(group,root,"-".equals(pieces[2])?null:pieces[2],"1".equals(pieces[1]),reader,lock);
        verifyObjects(authorized,current.objects);
        return current;
    }
    private static void verifyObjects(List<BusinessObjectRef> authorized,List<BusinessObjectRef> current) throws BusinessException {
        Map<String,BusinessObjectRef> expected=new HashMap<String,BusinessObjectRef>();
        for(BusinessObjectRef ref:authorized) EsignValues.require(ref!=null && expected.put(ref.getClientObjectId(),ref)==null,
            "客户或供应商保存授权对象重复。");
        for(BusinessObjectRef ref:current) {
            BusinessObjectRef prior=expected.remove(ref.getClientObjectId());
            EsignValues.require(prior!=null && Objects.equals(prior.getObjectType(),ref.getObjectType())
                && Objects.equals(prior.getObjectPk(),ref.getObjectPk()) && Objects.equals(prior.getBillCode(),ref.getBillCode())
                && Objects.equals(prior.getParentClientObjectId(),ref.getParentClientObjectId())
                && Objects.equals(prior.getDesiredOwnerOrg(),ref.getDesiredOwnerOrg()),"客户或供应商保存影响范围或归属变化，请重新发起操作。");
        }
        EsignValues.require(expected.isEmpty(),"客户或供应商保存影响范围已变化，请重新发起操作。");
    }
    public static BusinessObjectRef root(List<BusinessObjectRef> authorized) throws BusinessException {
        EsignValues.require(authorized!=null && !authorized.isEmpty(),"客户或供应商保存授权范围为空。");
        BusinessObjectRef root=null;
        for(BusinessObjectRef ref:authorized) if(ref!=null && ref.getParentClientObjectId()==null) {
            EsignValues.require(root==null,"客户或供应商保存授权包含多个主对象。");root=ref;
        }
        EsignValues.require(root!=null,"客户或供应商保存授权缺少主对象。");
        return root;
    }
    private static boolean validPk(String value) { return value!=null && value.matches("[A-Za-z0-9_-]{1,20}"); }
}
