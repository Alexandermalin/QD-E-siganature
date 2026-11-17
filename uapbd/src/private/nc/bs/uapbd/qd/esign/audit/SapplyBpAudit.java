package nc.bs.uapbd.qd.esign.audit;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.transaction.Status;
import javax.transaction.TransactionSynchronizationRegistry;
import nc.bs.uapbd.qd.esign.core.NativeTransactionBoundaryProbe;
import nc.impl.uapbd.qd.esign.NativeBpPolicyResolver;
import nc.vo.ic.general.define.ICBillHeadVO;
import nc.vo.ic.general.define.ICBillVO;
import nc.vo.ic.m4455.entity.SapplyBillVO;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.InventoryBillScope;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.SapplyLineScope;

/** Transaction-bound bridge between the authorized gateway and outbound-request BP rules. */
public final class SapplyBpAudit {
    private static final ThreadLocal<Capture> CURRENT = new ThreadLocal<Capture>();
    private static final String NODE = "40080801";
    private SapplyBpAudit() { }

    interface Snapshotter { void before(AuditCaptureScope scope, OperationContext context) throws BusinessException; }
    interface PolicyLookup { boolean controlled(String group, String org, String button) throws BusinessException; }

    public static Capture open(OperationContext context, AuditCaptureScope audit,
            TransactionSynchronizationRegistry transaction) throws BusinessException {
        return open(context, audit, transaction, new Snapshotter() {
            public void before(AuditCaptureScope scope, OperationContext value) throws BusinessException {
                if (SapplyLineScope.matches(value.getNodeCode(),value.getButtonCode(),value.getOperationType()))
                    SapplyLineSnapshots.before(scope,value);
                else new InventoryPhysicalSnapshots().before(scope, value);
            }
        });
    }

    static Capture open(OperationContext context, AuditCaptureScope audit,
            TransactionSynchronizationRegistry transaction, Snapshotter snapshotter) throws BusinessException {
        if (CURRENT.get() != null || context == null || audit == null || audit != AuditCaptureScope.current()
                || audit.getContext() != context || transaction == null || snapshotter == null
                || transaction.getTransactionKey() == null || transaction.getTransactionStatus() != Status.STATUS_ACTIVE
                || transaction.getRollbackOnly() || !NODE.equals(context.getNodeCode())
                || !InventoryBillScope.matches(context.getNodeCode(), context.getButtonCode(), context.getOperationType())
                || context.getRequestId() == null || context.getRequestId().trim().isEmpty()
                || clean(context.getGroupId()) == null || !context.getGroupId().equals(clean(context.getGroupId()))
                || clean(context.getOrgId()) == null || !context.getOrgId().equals(clean(context.getOrgId()))
                || context.getObjects().isEmpty()) {
            throw new BusinessException("出库申请 BP 缺少有效的受控业务事务。");
        }
        BusinessObjectRef root;
        if (SapplyLineScope.matches(context.getNodeCode(),context.getButtonCode(),context.getOperationType()))
            root = SapplyLineScope.require(context).getRoot();
        else {
            if (context.getObjects().size()!=1) throw new BusinessException("出库申请 BP 仅支持一张单据。");
            root=context.getObjects().get(0);
        }
        if (root==null || !InventoryBillScope.SAPPLY.equals(root.getObjectType()) || clean(root.getClientObjectId())==null)
            throw new BusinessException("出库申请 BP 单据授权无效。");
        Capture capture = new Capture(context, audit, transaction, snapshotter);
        CURRENT.set(capture);
        return capture;
    }

    /** Called from a BP before-rule; an uncontrolled native path retains its normal behavior. */
    public static void before(String point, ICBillVO[] current, ICBillVO[] old) {
        try {
            Capture capture = CURRENT.get();
            if (capture == null) {
                requireUncontrolled(point, current, old, new PolicyLookup() {
                    public boolean controlled(String group, String org, String button) throws BusinessException {
                        return NativeBpPolicyResolver.isControlled(group, org, NODE, button);
                    }
                });
                return;
            }
            capture.before(point, current, old);
        } catch (BusinessException error) { throw reject("出库申请 BP 前置规则校验失败。",error); }
          catch (RuntimeException error) { throw reject("出库申请 BP 前置规则异常。",error); }
    }

    public static void after(String point, ICBillVO[] current, ICBillVO[] old) {
        Capture capture = CURRENT.get();
        if (capture == null) return;
        try { capture.after(point, current, old); }
        catch (BusinessException error) { throw reject("出库申请 BP 后置规则校验失败。",error); }
        catch (RuntimeException error) { throw reject("出库申请 BP 后置规则异常。",error); }
    }

    private static IllegalStateException reject(String message, Throwable failure) {
        Capture capture = CURRENT.get();
        if (capture != null) capture.poisoned = true;
        try {
            TransactionSynchronizationRegistry tx = capture == null
                    ? NativeTransactionBoundaryProbe.requireActiveTransaction() : capture.transaction;
            if (tx.getTransactionKey() == null || tx.getTransactionStatus() != Status.STATUS_ACTIVE)
                throw new IllegalStateException("No active BP transaction to mark for rollback");
            if (!tx.getRollbackOnly()) tx.setRollbackOnly();
            if (!tx.getRollbackOnly()) throw new IllegalStateException("BP rollback marker was not retained");
        } catch (Exception markerFailure) {
            if (markerFailure != failure) failure.addSuppressed(markerFailure);
        }
        return new IllegalStateException(message,failure);
    }

    static void requireUncontrolled(String point, ICBillVO[] bills, PolicyLookup lookup) throws BusinessException {
        requireUncontrolled(point,bills,null,lookup);
    }

    static void requireUncontrolled(String point, ICBillVO[] bills, ICBillVO[] old, PolicyLookup lookup)
            throws BusinessException {
        String button = buttonFor(point);
        if (button == null || bills == null || bills.length == 0 || lookup == null)
            throw new BusinessException("无法确定出库申请 BP 的策略范围。");
        Map<String,Set<String>> checked=new HashMap<String,Set<String>>();
        requireUncontrolledOwners(bills,button,lookup,checked);
        if(old!=null)requireUncontrolledOwners(old,button,lookup,checked);
    }

    private static void requireUncontrolledOwners(ICBillVO[] bills,String button,PolicyLookup lookup,
            Map<String,Set<String>> checked) throws BusinessException {
        for (ICBillVO bill : bills) {
            ICBillHeadVO head = !(bill instanceof SapplyBillVO) ? null : bill.getHead();
            String group = head == null ? null : clean(head.getPk_group());
            String org = head == null ? null : clean(head.getPk_org());
            if (group == null || org == null) throw new BusinessException("出库申请 BP 缺少集团或组织。");
            Set<String> organizations=checked.get(group);
            if(organizations==null){organizations=new HashSet<String>();checked.put(group,organizations);}
            if (organizations.add(org) && lookup.controlled(group, org, button))
                throw new BusinessException("出库申请操作已受控，请通过电子签名入口执行。");
        }
    }

    private static String buttonFor(String point) {
        if ("Insert".equals(point) || "Update".equals(point)) return "Save";
        if ("Delete".equals(point) || "Commit".equals(point) || "UnCommit".equals(point)
                || "Approve".equals(point) || "UnApprove".equals(point)) return point;
        if ("RowClose".equals(point)) return "LineClose";
        if ("RowOpen".equals(point)) return "LineOpen";
        return null;
    }

    private static String rootFor(OperationContext context) throws BusinessException {
        String button = context.getButtonCode();
        if ("Save".equals(button)) return "INSERT".equals(context.getOperationType()) ? "Insert" : "Update";
        if ("LineClose".equals(button)) return "RowClose";
        if ("LineOpen".equals(button)) return "RowOpen";
        if (buttonFor(button) != null) return button;
        throw new BusinessException("未支持的出库申请 BP 动作。");
    }

    private static String clean(String value) {
        if (value == null) return null;
        String result = value.trim();
        return result.isEmpty() || "~".equals(result) ? null : result;
    }

    public static final class Capture implements AutoCloseable {
        private final OperationContext context;
        private final AuditCaptureScope audit;
        private final TransactionSynchronizationRegistry transaction;
        private final Object transactionKey;
        private final Snapshotter snapshotter;
        private final String root;
        private final BusinessObjectRef rootObject;
        private final SapplyLineScope.Selection lineSelection;
        private final Deque<String> stack = new ArrayDeque<String>();
        private String billPk;
        private boolean started, finished, verified, closed, poisoned;

        private Capture(OperationContext context, AuditCaptureScope audit,
                TransactionSynchronizationRegistry transaction, Snapshotter snapshotter) throws BusinessException {
            this.context = context; this.audit = audit; this.transaction = transaction;
            this.transactionKey = transaction.getTransactionKey(); this.snapshotter = snapshotter;
            this.root = rootFor(context);
            this.lineSelection=SapplyLineScope.matches(context.getNodeCode(),context.getButtonCode(),context.getOperationType())
                    ?SapplyLineScope.require(context):null;
            this.rootObject=lineSelection==null?context.getObjects().get(0):lineSelection.getRoot();
            this.billPk = clean(rootObject.getObjectPk());
            if ("Insert".equals(root) != (billPk == null)) throw new BusinessException("出库申请授权主键与动作不一致。");
        }

        private void before(String point, ICBillVO[] current, ICBillVO[] old) throws BusinessException {
            checkTransaction();
            if (buttonFor(point) == null || finished || stack.contains(point))
                throw new BusinessException("出库申请 BP 重复或动作不受支持。");
            if (stack.isEmpty()) {
                if (started || !root.equals(point)) throw new BusinessException("出库申请实际 BP 与授权动作不一致。");
                started = true;
                checkBill(point, current, old, true);
                if (lineSelection!=null) SapplyLineEffects.requireVoChanges((SapplyBillVO)current[0],(SapplyBillVO)old[0],
                        lineSelection.lineKeys(),"RowClose".equals(point));
                if (audit.isEnabled()) snapshotter.before(audit, context);
            } else checkBill(point, current, old, false);
            stack.push(point);
        }

        private void after(String point, ICBillVO[] current, ICBillVO[] old) throws BusinessException {
            checkTransaction();
            if (stack.isEmpty() || !stack.peek().equals(point)) throw new BusinessException("出库申请 BP 前后规则顺序不匹配。");
            checkBill(point, current, old, false);
            stack.pop();
            if (stack.isEmpty()) finished = true;
        }

        private void checkBill(String point, ICBillVO[] current, ICBillVO[] old, boolean first) throws BusinessException {
            if (("Update".equals(point) || "Approve".equals(point) || "UnApprove".equals(point)
                    || "RowClose".equals(point) || "RowOpen".equals(point)) && old == null)
                throw new BusinessException("出库申请比较型 BP 缺少原单据。");
            checkOne(current, first);
            if (old != null) checkOne(old, false);
        }

        private void checkOne(ICBillVO[] bills, boolean first) throws BusinessException {
            if (bills == null || bills.length != 1 || !(bills[0] instanceof SapplyBillVO) || bills[0].getHead() == null)
                throw new BusinessException("出库申请受控 BP 只支持一张完整单据。");
            ICBillHeadVO head = bills[0].getHead();
            String pk = clean(head.getCgeneralhid());
            if (!context.getGroupId().equals(clean(head.getPk_group())) || !context.getOrgId().equals(clean(head.getPk_org())))
                throw new BusinessException("出库申请 BP 单据集团或组织与授权不一致。");
            if (pk != null && billPk == null) billPk = pk;
            if (pk == null && !("Insert".equals(root) && first) || pk != null && !pk.equals(billPk))
                throw new BusinessException("出库申请 BP 单据主键与授权不一致。");
        }

        public void verify(List<BusinessObjectRef> results) throws BusinessException {
            checkTransaction();
            BusinessObjectRef returnedRoot;
            if(lineSelection==null) returnedRoot=results==null || results.size()!=1?null:results.get(0);
            else {
                SapplyLineScope.Selection returned=SapplyLineScope.require(results);
                returnedRoot=returned.getRoot();
                if (returned.getLines().size()!=lineSelection.getLines().size())
                    throw new BusinessException("出库申请实际行数与授权不一致。");
                java.util.Map<String,String> rows=new java.util.HashMap<String,String>();
                for (BusinessObjectRef row:returned.getLines()) rows.put(row.getClientObjectId(),row.getObjectPk());
                for (BusinessObjectRef row:lineSelection.getLines())
                    if (!row.getObjectPk().equals(rows.get(row.getClientObjectId())))
                        throw new BusinessException("出库申请实际行与授权不一致。");
            }
            if (verified || !started || !finished || !stack.isEmpty() || returnedRoot == null
                    || billPk == null || !billPk.equals(clean(returnedRoot.getObjectPk()))
                    || !rootObject.getClientObjectId().equals(returnedRoot.getClientObjectId()))
                throw new BusinessException("出库申请 BP 规则未完整执行，或实际主键与授权不一致。");
            verified = true;
        }

        private void checkTransaction() throws BusinessException {
            if (closed || poisoned || CURRENT.get() != this || audit != AuditCaptureScope.current()
                    || !transactionKey.equals(transaction.getTransactionKey())
                    || transaction.getTransactionStatus() != Status.STATUS_ACTIVE || transaction.getRollbackOnly())
                throw new BusinessException("出库申请 BP 的线程或业务事务已变化。");
        }

        public void close() {
            if (closed) return;
            if (CURRENT.get() != this) throw new IllegalStateException("Outbound-request BP scope closed out of order");
            CURRENT.remove(); closed = true;
        }
    }
}
