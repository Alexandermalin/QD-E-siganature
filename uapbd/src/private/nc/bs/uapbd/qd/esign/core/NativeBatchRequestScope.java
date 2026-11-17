package nc.bs.uapbd.qd.esign.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import nc.bs.uapbd.qd.esign.audit.SnapshotSupport;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.PartnerBatchDraft;

/** Binds the exact server-normalized selection and typed batch changes to persisted request object identities. */
public final class NativeBatchRequestScope {
    private NativeBatchRequestScope() { }

    public static List<BusinessObjectRef> bind(String group,String org,String domain,List<BusinessObjectRef> selected,
            NativeBatchChangeSet changes) throws BusinessException {
        return bind(selected,"bulk",fingerprint(group,org,domain,selected,changes));
    }

    /** An organization-tab request commits to the selected pages, targets and physical metadata-linked evidence. */
    public static List<BusinessObjectRef> bindOrganization(String group,String org,String domain,List<BusinessObjectRef> selected,
            NativeBatchChangeSet changes,NativeOrgBatchSelection.Impact impact,NativeOrgBatchSnapshots.Capture evidence)
            throws BusinessException {
        return bind(selected,"orgbulk",organizationFingerprint(group,org,domain,selected,changes,impact,evidence));
    }

    private static List<BusinessObjectRef> bind(List<BusinessObjectRef> selected,String prefix,String fingerprint) throws BusinessException {
        List<BusinessObjectRef> result=new ArrayList<BusinessObjectRef>();
        for(BusinessObjectRef ref:selected) {
            String client=prefix+":"+fingerprint+":"+ref.getObjectPk();
            EsignValues.require(client.length()<=100,"批改授权对象标识过长。");
            result.add(new BusinessObjectRef(client,ref.getObjectType(),ref.getObjectPk(),ref.getBillCode(),null,null,
                ref.getPolicyOwnerOrg(),ref.getTargetOrg()));
        }
        return Collections.unmodifiableList(result);
    }

    /** Recalculate from the persisted complete object set, server metadata and the actual submitted change values. */
    public static void verify(String group,String org,String domain,List<BusinessObjectRef> authorized,NativeBatchChangeSet changes)
            throws BusinessException {
        verify(authorized,"bulk",fingerprint(group,org,domain,authorized,changes));
    }
    public static void verifyOrganization(String group,String org,String domain,List<BusinessObjectRef> authorized,
            NativeBatchChangeSet changes,NativeOrgBatchSelection.Impact impact,NativeOrgBatchSnapshots.Capture evidence)
            throws BusinessException {
        verify(authorized,"orgbulk",organizationFingerprint(group,org,domain,authorized,changes,impact,evidence));
    }
    private static void verify(List<BusinessObjectRef> authorized,String prefix,String fingerprint) throws BusinessException {
        for(BusinessObjectRef ref:authorized) EsignValues.require((prefix+":"+fingerprint+":"+ref.getObjectPk()).equals(ref.getClientObjectId()),
            "批改字段、值或授权档案范围已变化，请重新发起操作。");
    }

    private static String organizationFingerprint(String group,String org,String domain,List<BusinessObjectRef> selected,
            NativeBatchChangeSet changes,NativeOrgBatchSelection.Impact impact,NativeOrgBatchSnapshots.Capture evidence)
            throws BusinessException {
        EsignValues.require(impact!=null && impact.matches(group,domain,selected) && evidence!=null && evidence.matches(impact),
            "组织页签批改实际行或关联证据与授权主档不一致。");
        return SnapshotSupport.sha256(pack("batch-org-v2",fingerprint(group,org,domain,selected,changes),
            impact.getFingerprint(),evidence.getFingerprint()));
    }

    private static String fingerprint(String group,String org,String domain,List<BusinessObjectRef> selected,NativeBatchChangeSet changes)
            throws BusinessException {
        EsignValues.require(validPk(group) && validPk(org) && ("BD_CUSTOMER".equals(domain) || "BD_SUPPLIER".equals(domain)
            || "BD_MATERIAL".equals(domain)) && selected!=null && !selected.isEmpty()
            && selected.size()<=PartnerBatchDraft.MAX_SINGLE_TRANSACTION_OBJECTS && changes!=null,
            "批改缺少有效的集团、操作组织、档案范围或字段值。");
        Set<String> pks=new TreeSet<String>(),clients=new HashSet<String>();
        for(BusinessObjectRef ref:selected) EsignValues.require(ref!=null && domain.equals(ref.getObjectType())
            && validPk(ref.getObjectPk()) && pks.add(ref.getObjectPk()) && clients.add(ref.getClientObjectId())
            && ref.getParentClientObjectId()==null && ref.getDesiredOwnerOrg()==null
            && (ref.getTargetOrg()==null || org.equals(ref.getTargetOrg())),"批改授权档案、所属类型或操作组织无效。");
        StringBuilder value=new StringBuilder(pack("batch-v1",group,org,domain,changes.getFingerprint()));
        for(String pk:pks) value.append(pack(pk));
        return SnapshotSupport.sha256(value.toString());
    }
    private static String pack(String... values) {
        StringBuilder result=new StringBuilder();for(String value:values) result.append(value==null?-1:value.length()).append(':').append(value==null?"":value);
        return result.toString();
    }
    private static boolean validPk(String value) { return value!=null && value.matches("[A-Za-z0-9_-]{1,20}"); }
}
