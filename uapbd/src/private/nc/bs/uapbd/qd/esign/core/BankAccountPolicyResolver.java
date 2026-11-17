package nc.bs.uapbd.qd.esign.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import nc.bs.uapbd.qd.esign.persistence.QdEsignStore;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.PolicyDecision;

/** Resolves distinct formal action policies for all archives sharing a bank account. */
public final class BankAccountPolicyResolver {
    private BankAccountPolicyResolver() { }
    public interface Lookup {
        PolicyDecision load(String group,String owner,String scope) throws BusinessException;
    }
    public static final class Decision {
        private final String domain,archive,owner,scope;private final PolicyDecision policy;
        private Decision(BankAccountImpactScope.AffectedArchive archive,String scope,PolicyDecision policy) {
            this.domain=archive.getDomain();this.archive=archive.getArchive();this.owner=archive.getOwner();this.scope=scope;this.policy=policy;
        }
        public String getDomain() { return domain; }
        public String getArchive() { return archive; }
        public String getOwner() { return owner; }
        public String getScope() { return scope; }
        public PolicyDecision getPolicy() { return policy; }
    }
    public static final class Batch {
        private final List<Decision> decisions;private final boolean sign,reason,audit;
        private Batch(List<Decision> decisions,boolean sign,boolean reason,boolean audit) {
            this.decisions=Collections.unmodifiableList(decisions);this.sign=sign;this.reason=reason;this.audit=audit;
        }
        public List<Decision> getDecisions() { return decisions; }
        public boolean isSignatureRequired() { return sign; }
        public boolean isReasonRequired() { return reason; }
        public boolean isAuditEnabled() { return audit; }
    }
    public static Batch resolve(String group,String operation,BankAccountImpactScope.Selection selection,Lookup lookup)
            throws BusinessException {
        EsignValues.require(group!=null && !group.trim().isEmpty() && selection!=null && lookup!=null
            && !selection.getArchives().isEmpty(),"银行账户策略缺少有效的影响范围。");
        boolean sign=false,reason=false,audit=false;List<Decision> decisions=new ArrayList<Decision>();
        for(BankAccountImpactScope.AffectedArchive archive:selection.getArchives()) {
            String scope=BankAccountActionScopes.scopeFor(archive.getDomain(),operation);
            PolicyDecision policy=lookup.load(group,archive.getOwner(),scope);
            EsignValues.require(policy!=null && policy.isValidated(),"银行账户关联档案的正式动作尚未验证。");
            decisions.add(new Decision(archive,scope,policy));
            sign|=policy.isSignatureRequired();reason|=policy.isReasonRequired();audit|=policy.isAuditEnabled();
        }
        return new Batch(decisions,sign,reason,audit);
    }
    /** Query only the canonical formal scope; a missing owner policy or master remains disabled. */
    public static Lookup database() {
        return database(new QdEsignStore());
    }
    /** Reuse the caller's transaction-bound store when the owner guards are already locked. */
    public static Lookup database(final QdEsignStore store) {
        return new Lookup() {
            public PolicyDecision load(String group,String owner,String scope) throws BusinessException {
                EsignValues.require(store!=null,"银行账户策略存储不可用。");boolean groupOwner=group.equals(owner);
                EsignValues.require(groupOwner || store.isBusinessUnitInGroup(group,owner),"银行账户所属组织不属于当前集团的业务单元。");
                Map<String,Object> entry=groupOwner?store.loadGroupPolicyEntry(group,scope):store.loadOrgBasePolicyEntry(group,owner,scope);
                Map<String,Object> control=groupOwner?store.loadGroupControl(group):store.loadOrganizationControl(group,owner);
                return decode(entry,control);
            }
        };
    }
    static PolicyDecision decode(Map<String,Object> entry,Map<String,Object> control) throws BusinessException {
        EsignValues.require(entry!=null,"银行账户正式动作不在策略目录中。");
        Map<String,Object> master=control==null?Collections.<String,Object>emptyMap():control;
        return new PolicyDecision(EsignValues.yes(master,"master_enabled"),EsignValues.yes(entry,"sign_enabled"),
            EsignValues.yes(entry,"reason_required"),EsignValues.yes(entry,"audit_enabled"),EsignValues.yes(entry,"configurable"),
            EsignValues.number(entry,"policy_version"),EsignValues.number(master,"control_version"),true);
    }
    /** An authorization is invalidated if any linked archive's owner, scope, switches or versions change. */
    public static void verifyUnchanged(Batch prepared,Batch current) throws BusinessException {
        EsignValues.require(prepared!=null && current!=null && prepared.decisions.size()==current.decisions.size(),"银行账户策略范围已变化。");
        Map<String,Decision> expected=new LinkedHashMap<String,Decision>();
        for(Decision decision:prepared.decisions) EsignValues.require(expected.put(decision.domain+"\u0000"+decision.archive,decision)==null,
            "银行账户策略包含重复档案。");
        for(Decision now:current.decisions) {
            Decision before=expected.remove(now.domain+"\u0000"+now.archive);
            EsignValues.require(before!=null && before.owner.equals(now.owner) && before.scope.equals(now.scope)
                && same(before.policy,now.policy),"银行账户关联策略或版本已变化，请重新发起操作。");
        }
        EsignValues.require(expected.isEmpty(),"银行账户策略范围已变化。");
    }
    private static boolean same(PolicyDecision a,PolicyDecision b) {
        return a.isValidated()==b.isValidated() && a.isMasterEnabled()==b.isMasterEnabled()
            && a.getVersion()==b.getVersion() && a.getControlVersion()==b.getControlVersion()
            && a.getConfiguredSignature()==b.getConfiguredSignature() && a.getConfiguredReason()==b.getConfiguredReason()
            && a.getConfiguredAudit()==b.getConfiguredAudit() && a.isSignatureRequired()==b.isSignatureRequired()
            && a.isReasonRequired()==b.isReasonRequired() && a.isAuditEnabled()==b.isAuditEnabled();
    }
}
