package nc.impl.ic.qd.esign;

import nc.bs.uapbd.qd.esign.audit.SapplyBpAudit;
import nc.impl.pubapp.pattern.rule.ICompareRule;
import nc.impl.pubapp.pattern.rule.IRule;
import nc.vo.ic.general.define.ICBillVO;

/** Candidate NC m4455 BP extension rules; registration and ordering require site verification. */
public final class SapplyBpRules {
    private SapplyBpRules() { }

    public static final class BeforeInsert implements IRule<ICBillVO> {
        public void process(ICBillVO[] bills) { SapplyBpAudit.before("Insert",bills,null); }
    }
    public static final class AfterInsert implements IRule<ICBillVO> {
        public void process(ICBillVO[] bills) { SapplyBpAudit.after("Insert",bills,null); }
    }
    public static final class BeforeUpdate implements ICompareRule<ICBillVO> {
        public void process(ICBillVO[] bills,ICBillVO[] old) { SapplyBpAudit.before("Update",bills,old); }
    }
    public static final class AfterUpdate implements ICompareRule<ICBillVO> {
        public void process(ICBillVO[] bills,ICBillVO[] old) { SapplyBpAudit.after("Update",bills,old); }
    }
    public static final class BeforeDelete implements IRule<ICBillVO> {
        public void process(ICBillVO[] bills) { SapplyBpAudit.before("Delete",bills,null); }
    }
    public static final class AfterDelete implements IRule<ICBillVO> {
        public void process(ICBillVO[] bills) { SapplyBpAudit.after("Delete",bills,null); }
    }
    public static final class BeforeCommit implements IRule<ICBillVO> {
        public void process(ICBillVO[] bills) { SapplyBpAudit.before("Commit",bills,null); }
    }
    public static final class AfterCommit implements IRule<ICBillVO> {
        public void process(ICBillVO[] bills) { SapplyBpAudit.after("Commit",bills,null); }
    }
    public static final class BeforeUnCommit implements IRule<ICBillVO> {
        public void process(ICBillVO[] bills) { SapplyBpAudit.before("UnCommit",bills,null); }
    }
    public static final class AfterUnCommit implements IRule<ICBillVO> {
        public void process(ICBillVO[] bills) { SapplyBpAudit.after("UnCommit",bills,null); }
    }
    public static final class BeforeApprove implements ICompareRule<ICBillVO> {
        public void process(ICBillVO[] bills,ICBillVO[] old) { SapplyBpAudit.before("Approve",bills,old); }
    }
    public static final class AfterApprove implements ICompareRule<ICBillVO> {
        public void process(ICBillVO[] bills,ICBillVO[] old) { SapplyBpAudit.after("Approve",bills,old); }
    }
    public static final class BeforeUnApprove implements ICompareRule<ICBillVO> {
        public void process(ICBillVO[] bills,ICBillVO[] old) { SapplyBpAudit.before("UnApprove",bills,old); }
    }
    public static final class AfterUnApprove implements ICompareRule<ICBillVO> {
        public void process(ICBillVO[] bills,ICBillVO[] old) { SapplyBpAudit.after("UnApprove",bills,old); }
    }
    public static final class BeforeRowClose implements ICompareRule<ICBillVO> {
        public void process(ICBillVO[] bills,ICBillVO[] old) { SapplyBpAudit.before("RowClose",bills,old); }
    }
    public static final class AfterRowClose implements ICompareRule<ICBillVO> {
        public void process(ICBillVO[] bills,ICBillVO[] old) { SapplyBpAudit.after("RowClose",bills,old); }
    }
    public static final class BeforeRowOpen implements ICompareRule<ICBillVO> {
        public void process(ICBillVO[] bills,ICBillVO[] old) { SapplyBpAudit.before("RowOpen",bills,old); }
    }
    public static final class AfterRowOpen implements ICompareRule<ICBillVO> {
        public void process(ICBillVO[] bills,ICBillVO[] old) { SapplyBpAudit.after("RowOpen",bills,old); }
    }
}
