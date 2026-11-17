package nc.bs.uapbd.qd.esign.audit;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.transaction.Status;
import javax.transaction.TransactionSynchronizationRegistry;
import nc.bs.framework.aop.Around;
import nc.bs.framework.aop.Aspect;
import nc.bs.framework.aop.PatternType;
import nc.bs.framework.aop.Pointcut;
import nc.bs.framework.aop.ProceedingJoinpoint;
import nc.bs.framework.common.NCLocator;
import nc.bs.uapbd.qd.esign.core.NativeTransactionBoundaryProbe;
import nc.bs.uapbd.qd.esign.core.TransactionRollback;
import nc.vo.ic.m4455.entity.SapplyBillVO;
import nc.vo.ic.qd.esign.InventoryPfStateCommand;
import nc.vo.pub.AggregatedValueObject;
import nc.vo.pub.BusinessException;
import nc.vo.pub.workflownote.WorkflownoteVO;
import nc.vo.pubapp.pflow.PfUserObject;
import nc.vo.uapbd.qd.esign.OperationContext;

/** Candidate for the native batch and per-bill PF calls. Register after proving the site transaction boundary. */
@Aspect
public final class SapplyWorkflowBatchAspect {
    public static final String REQUEST_PARAMETER = "qdEsignSapplyBatchRequest";
    private static final String ORIGINAL_BILLS_PARAMETER = "nc.bs.scmpub.pf.ORIGIN_VO_PARAMETER";
    private static final ThreadLocal<Unit> ACTIVE_UNIT = new ThreadLocal<Unit>();
    private static final Pattern ID = Pattern.compile("[0-9a-fA-F]{32}");
    private static final Class<?>[] UNIT_TYPES = {String.class, String.class, WorkflownoteVO.class,
            AggregatedValueObject.class, Object.class, HashMap.class};
    private static final Class<?>[] BATCH_TYPES = {String.class, String.class, WorkflownoteVO.class,
            AggregatedValueObject[].class, Object[].class, HashMap.class};

    public interface UnitCallback {
        /** Authorize and capture before native execution, then write evidence in the same transaction. */
        Object execute(Unit unit, NativeCall nativeCall) throws Throwable;
    }

    public interface NativeCall {
        Object proceed() throws Throwable;
    }

    /** Travels in the native PF parameter map, which NC may print to its debug log. */
    public static final class RequestEnvelope implements Serializable {
        private static final long serialVersionUID = 1L;
        private static final String INVALID = "出库申请批量请求标识无效。";
        private final String requestId;
        private final String executionToken;
        private final transient SapplyBatchApprovalProof approvalProof;
        private final transient Set<String> submittedPks;
        private final transient java.util.List<String> submittedOrder;
        private final transient boolean standardInventoryCallback;
        private transient Set<String> visitedPks = new HashSet<String>();
        private transient boolean minted = true;
        private transient Object outerTransactionKey;
        private transient Set<String> rejectedBeforeNative = new HashSet<String>();

        public RequestEnvelope(String requestId, String executionToken) {
            this(requestId,executionToken,null,null);
        }

        public RequestEnvelope(String requestId, String executionToken, SapplyBatchApprovalProof approvalProof) {
            this(requestId,executionToken,approvalProof,null);
        }

        public RequestEnvelope(String requestId, String executionToken, SapplyBatchApprovalProof approvalProof,
                String[] submittedPks) {
            this(requestId,executionToken,approvalProof,submittedPks,false);
        }

        public RequestEnvelope(String requestId, String executionToken, SapplyBatchApprovalProof approvalProof,
                String[] submittedPks,boolean standardInventoryCallback) {
            this.requestId = requestId;
            this.executionToken = executionToken;
            this.approvalProof = approvalProof;
            this.standardInventoryCallback=standardInventoryCallback;
            Set<String> submitted = null;
            String[] ordered=submittedPks==null?null:submittedPks.clone();
            if (ordered != null) {
                submitted = new HashSet<String>();
                if (ordered.length == 0 || ordered.length > 1000) throw new IllegalArgumentException(INVALID);
                for (String pk : ordered)
                    if (pk == null || !pk.matches("[A-Za-z0-9_-]{1,20}") || !submitted.add(pk))
                        throw new IllegalArgumentException(INVALID);
            }
            this.submittedPks = submitted == null ? null : Collections.unmodifiableSet(submitted);
            this.submittedOrder=ordered==null?null:Collections.unmodifiableList(Arrays.asList(ordered));
            if (!isValid()) throw new IllegalArgumentException(INVALID);
        }

        public String getRequestId() { return requestId; }
        public String getExecutionToken() { return executionToken; }

        @Override public String toString() { return "[受控出库申请请求]"; }

        private boolean isValid() {
            return requestId != null && ID.matcher(requestId).matches()
                    && executionToken != null && ID.matcher(executionToken).matches();
        }

        private synchronized void bindOuterTransaction(Object key) throws BusinessException {
            if (!minted || key == null || submittedPks == null || visitedPks == null) throw new BusinessException(INVALID);
            minted = false;
            outerTransactionKey = key;
        }

        private synchronized void requireBatchSelection(AggregatedValueObject[] bills) throws BusinessException {
            Set<String> actual = new HashSet<String>();
            for (AggregatedValueObject bill : bills) {
                if (!isSapplyBill(bill)) throw new BusinessException("原生批量入口包含其他类型单据。");
                String pk = ((SapplyBillVO) bill).getHead().getCgeneralhid();
                pk = pk == null ? null : pk.trim();
                if (pk == null || !actual.add(pk)) throw new BusinessException("原生批量单据主键无效或重复。");
            }
            if (submittedPks == null || !submittedPks.equals(actual))
                throw new BusinessException("原生批量单据与预校验后的提交范围不一致。");
        }

        private synchronized void requireSubmittedUnit(String pk) throws BusinessException {
            if (pk == null || submittedPks == null || !submittedPks.contains(pk)
                    || visitedPks == null || !visitedPks.add(pk))
                throw new BusinessException("原生逐单流程超出本批次提交范围或重复执行。");
        }

        private void requireOriginalBills(Object value,SapplyBillVO bill) throws BusinessException {
            if (!standardInventoryCallback || !(value instanceof Object[]) || submittedPks==null
                    || submittedOrder==null || bill==null || bill.getHead()==null)
                throw new BusinessException("出库申请原生批量原单参数无效。");
            Object[] originals=(Object[])value;
            if(originals.length!=submittedPks.size())throw new BusinessException("出库申请原生批量原单数量不一致。");
            String group=owner(bill.getHead().getPk_group()),org=owner(bill.getHead().getPk_org());
            if(group==null || org==null)throw new BusinessException("出库申请原生批量缺少单据组织。");
            Set<String> keys=new HashSet<String>();
            for(int index=0;index<originals.length;index++) {
                Object original=originals[index];
                if(!(original instanceof SapplyBillVO) || ((SapplyBillVO)original).getHead()==null)
                    throw new BusinessException("出库申请原生批量原单类型无效。");
                SapplyBillVO item=(SapplyBillVO)original;
                String pk=owner(item.getHead().getCgeneralhid());
                if(pk==null || !pk.equals(submittedOrder.get(index)) || !keys.add(pk)
                        || !group.equals(owner(item.getHead().getPk_group()))
                        || !org.equals(owner(item.getHead().getPk_org())))
                    throw new BusinessException("出库申请原生批量原单与授权范围不一致。");
            }
            if(!keys.equals(submittedPks))throw new BusinessException("出库申请原生批量原单范围不完整。");
        }

        private synchronized void requireUnitTransaction(Object key) throws BusinessException {
            if (outerTransactionKey == null || key == null || outerTransactionKey.equals(key)) {
                throw new BusinessException("出库申请逐单流程未进入独立的 NC 业务事务。");
            }
        }

        private synchronized void clearOuterTransaction() { outerTransactionKey = null; }

        private synchronized void recordRejectedBeforeNative(String pk) {
            if (pk != null && pk.matches("[A-Za-z0-9_-]{1,20}")) rejectedBeforeNative.add(pk);
        }

        /** Returns only bills rejected before entering their native single-bill PF action. */
        public synchronized Set<String> getRejectedBeforeNative() {
            return rejectedBeforeNative == null ? Collections.<String>emptySet()
                    : Collections.unmodifiableSet(new HashSet<String>(rejectedBeforeNative));
        }

        private void readObject(ObjectInputStream input) throws IOException, ClassNotFoundException {
            input.defaultReadObject();
            if (!isValid()) throw new InvalidObjectException(INVALID);
        }

        private void readObjectNoData() throws InvalidObjectException {
            throw new InvalidObjectException(INVALID);
        }
    }

    interface RuntimeAccess {
        TransactionSynchronizationRegistry transaction() throws BusinessException;
        UnitCallback callback();
    }

    private final RuntimeAccess runtime;

    public SapplyWorkflowBatchAspect() {
        this(new RuntimeAccess() {
            public TransactionSynchronizationRegistry transaction() throws BusinessException {
                return NativeTransactionBoundaryProbe.requireActiveTransaction();
            }
            public UnitCallback callback() {
                return NCLocator.getInstance().lookup(UnitCallback.class);
            }
        });
    }

    SapplyWorkflowBatchAspect(RuntimeAccess runtime) {
        this.runtime = runtime;
    }

    @Pointcut
    public boolean isSingleBillWorkflow(Method method) {
        return matchesMethod(method, "processSingleBillFlow_RequiresNew", UNIT_TYPES);
    }

    @Pointcut
    public boolean isBatchEntry(Method method) {
        return matchesMethod(method, "processBatch", BATCH_TYPES);
    }

    @Around(pointcut = "isBatchEntry", patternType = PatternType.method)
    public Object aroundBatchEntry(ProceedingJoinpoint joinpoint) throws Throwable {
        Object[] arguments = joinpoint.getArgs();
        Map<?, ?> parameters = markedParameters(arguments);
        if (parameters == null) return joinpoint.proceed();

        TransactionSynchronizationRegistry transaction = runtime.transaction();
        Object key = transaction.getTransactionKey();
        try {
            requireActive(transaction, key);
            if (!(joinpoint.getStaticPart() instanceof Method) || !isBatchEntry((Method) joinpoint.getStaticPart())
                    || !hasNativeArguments(arguments, parameters) || !(arguments[3] instanceof AggregatedValueObject[])
                    || (arguments[4] != null && !(arguments[4] instanceof Object[]))) {
                throw new BusinessException("出库申请批量调用不符合原生 PF 入口契约。");
            }
            RequestEnvelope envelope = requireEnvelope(parameters);
            envelope.bindOuterTransaction(key);
            try {
                AggregatedValueObject[] bills = (AggregatedValueObject[]) arguments[3];
                // A signed multi-bill request can contain one bill after native precheck excludes the rest.
                if (bills.length < 1) throw new BusinessException("出库申请批量入口缺少单据。");
                envelope.requireBatchSelection(bills);
                Object result = joinpoint.proceed();
                requireActive(transaction, key);
                return result;
            } finally {
                envelope.clearOuterTransaction();
            }
        } catch (Throwable failure) {
            throw rollback(transaction, key, failure);
        }
    }

    @Around(pointcut = "isSingleBillWorkflow", patternType = PatternType.method)
    public Object aroundSingleBill(ProceedingJoinpoint joinpoint) throws Throwable {
        Object[] arguments = joinpoint.getArgs();
        Map<?, ?> parameters = markedParameters(arguments);
        if (parameters == null) return joinpoint.proceed();

        TransactionSynchronizationRegistry transaction = runtime.transaction();
        Object transactionKey = transaction.getTransactionKey();
        try {
            requireActive(transaction, transactionKey);
            if (!(joinpoint.getStaticPart() instanceof Method)
                    || !isSingleBillWorkflow((Method) joinpoint.getStaticPart())
                    || !hasNativeArguments(arguments, parameters) || !isSapplyBill(arguments[3])) {
                throw new BusinessException("出库申请批量调用不符合原生逐单流程契约。");
            }
            RequestEnvelope envelope = requireEnvelope(parameters);
            envelope.requireUnitTransaction(transactionKey);
            // Remember the input identity before the callback can alter the VO while rejecting it.
            String initialPk = owner(((SapplyBillVO)arguments[3]).getHead().getCgeneralhid());
            if (ACTIVE_UNIT.get()!=null) throw new BusinessException("出库申请批量逐单流程不能嵌套。");
            envelope.requireSubmittedUnit(initialPk);
            GuardedCall nativeCall=null;
            try {
                Unit unit = new Unit(envelope, arguments, parameters, transaction, transactionKey);
                nativeCall = new GuardedCall(joinpoint, parameters, transaction, transactionKey,unit);
                ACTIVE_UNIT.set(unit);
                UnitCallback callback = runtime.callback();
                if (callback == null) throw new BusinessException("出库申请批量事务服务不可用。");
                callback.execute(unit, nativeCall);
                return nativeCall.result();
            } catch (Throwable failure) {
                if (nativeCall==null || !nativeCall.entered) {
                    envelope.recordRejectedBeforeNative(initialPk);
                }
                throw failure;
            } finally {
                ACTIVE_UNIT.remove();
                if(nativeCall!=null)nativeCall.closed = true;
            }
        } catch (Throwable failure) {
            throw rollback(transaction, transactionKey, failure);
        }
    }

    /** A local adapter may trust a derived approval note only within the matching native unit. */
    public static void requireApprovalIntent(OperationContext context,InventoryPfStateCommand command)
            throws BusinessException {
        Unit unit=ACTIVE_UNIT.get();
        if(unit==null || context==null || command==null || unit.approvalProof==null
                || !unit.requestId.equals(context.getRequestId()) || context.getObjects().size()!=1
                || !unit.billType.equals(command.getBillType())
                || !context.getButtonCode().equals(nc.vo.ic.qd.esign.SapplyNativeAction.approvalButton(unit.action))
                || unit.bill!=command.getBill() || unit.workflowNote!=command.getWorkflowNote()
                || unit.userObject!=command.getUserObject() || !unit.parameters.equals(command.getParameters()))
            throw new BusinessException("出库申请逐单审批缺少有效的批量授权上下文。");
        requireActive(unit.transaction,unit.transactionKey);
        unit.requireNativeParameters(unit.liveNativeParameters);
        unit.approvalProof.verify(unit.action,context.getButtonCode(),unit.billType,unit.bill,
                command.getUserObject(),unit.workflowNote,unit.parameters,context.getObjects().get(0),unit.nativeOriginal);
        unit.requireNativeParameters(unit.liveNativeParameters);
        unit.verifiedContext=context;
        unit.verifiedCommand=command;
    }

    private static boolean matchesMethod(Method method, String name, Class<?>[] types) {
        return method != null && name.equals(method.getName()) && method.getReturnType() == Object.class
                && Arrays.equals(types, method.getParameterTypes());
    }

    private static Map<?, ?> markedParameters(Object[] arguments) {
        if (arguments == null || arguments.length < 6 || !(arguments[5] instanceof Map)) return null;
        Map<?, ?> parameters = (Map<?, ?>) arguments[5];
        return parameters.containsKey(REQUEST_PARAMETER) ? parameters : null;
    }

    private static boolean hasNativeArguments(Object[] arguments, Map<?, ?> parameters) {
        return arguments.length == 6 && hasText(arguments[0]) && hasText(arguments[1]) && parameters instanceof HashMap
                && (arguments[2] == null || arguments[2] instanceof WorkflownoteVO);
    }

    private static boolean isSapplyBill(Object bill) {
        return bill instanceof SapplyBillVO && ((SapplyBillVO) bill).getHead() != null;
    }

    private static String owner(String value) {
        if(value==null)return null;String result=value.trim();
        return result.isEmpty() || "~".equals(result)?null:result;
    }

    private static RequestEnvelope requireEnvelope(Map<?, ?> parameters) throws BusinessException {
        Object envelope = parameters.get(REQUEST_PARAMETER);
        if (!(envelope instanceof RequestEnvelope) || !((RequestEnvelope) envelope).isValid()) {
            throw new BusinessException(RequestEnvelope.INVALID);
        }
        return (RequestEnvelope) envelope;
    }

    private static Throwable rollback(TransactionSynchronizationRegistry transaction, Object key, Throwable failure) {
        BusinessException rollback = TransactionRollback.abort(transaction, key, failure);
        return TransactionRollback.isMarked(rollback) ? failure : rollback;
    }

    private static boolean hasText(Object value) {
        return value instanceof String && !((String) value).trim().isEmpty();
    }

    private static void requireActive(TransactionSynchronizationRegistry transaction, Object key) throws BusinessException {
        if (key == null || !key.equals(transaction.getTransactionKey())
                || transaction.getTransactionStatus() != Status.STATUS_ACTIVE || transaction.getRollbackOnly()) {
            throw new BusinessException("无法确认出库申请原生逐单流程的活动事务。");
        }
    }

    public static final class Unit {
        private final RequestEnvelope envelope;
        private final String requestId;
        private final String executionToken;
        private final String action;
        private final String billType;
        private final WorkflownoteVO workflowNote;
        private final SapplyBillVO bill;
        private final Object userObject;
        private final Map<String, Object> parameters;
        private final Map<String, Object> nativeParameters;
        private final Map<?,?> liveNativeParameters;
        private final SapplyBatchApprovalProof approvalProof;
        private final SapplyBillVO nativeOriginal;
        private final int originalIndex;
        private final String originalFingerprint;
        private final TransactionSynchronizationRegistry transaction;
        private final Object transactionKey;
        private OperationContext verifiedContext;
        private InventoryPfStateCommand verifiedCommand;

        @SuppressWarnings("unchecked")
        private Unit(RequestEnvelope envelope, Object[] arguments, Map<?, ?> parameters,
                TransactionSynchronizationRegistry transaction, Object transactionKey) throws BusinessException {
            this.envelope=envelope;
            requestId = envelope.getRequestId();
            executionToken = envelope.getExecutionToken();
            approvalProof = envelope.approvalProof;
            action = (String) arguments[0];
            billType = (String) arguments[1];
            workflowNote = (WorkflownoteVO) arguments[2];
            bill = (SapplyBillVO) arguments[3];
            userObject = arguments[4];
            liveNativeParameters=parameters;
            Map<String, Object> copy = new HashMap<String, Object>((Map<String, Object>) parameters);
            copy.remove(REQUEST_PARAMETER);
            nativeParameters=Collections.unmodifiableMap(new HashMap<String,Object>(copy));
            if(envelope.standardInventoryCallback && !copy.containsKey(ORIGINAL_BILLS_PARAMETER))
                throw new BusinessException("库存标准批量回调没有提供原单参数。");
            SapplyBillVO original=null;int index=-1;
            if(copy.containsKey(ORIGINAL_BILLS_PARAMETER)) {
                envelope.requireOriginalBills(copy.get(ORIGINAL_BILLS_PARAMETER),bill);
                index=envelope.submittedOrder.indexOf(owner(bill.getHead().getCgeneralhid()));
                if(index<0)throw new BusinessException("出库申请原生批量原单位置无效。");
                original=(SapplyBillVO)((Object[])copy.get(ORIGINAL_BILLS_PARAMETER))[index];
                copy.remove(ORIGINAL_BILLS_PARAMETER);
            }
            this.parameters = Collections.unmodifiableMap(copy);
            nativeOriginal=original;
            originalIndex=index;
            if(approvalProof!=null && userObject!=null && !(userObject instanceof PfUserObject))
                throw new BusinessException("出库申请批量审批流程参数类型不正确。");
            originalFingerprint=approvalProof==null || original==null?null:
                    approvalProof.originalFingerprint(original,(PfUserObject)userObject,this.parameters);
            this.transaction = transaction;
            this.transactionKey = transactionKey;
        }

        public String getRequestId() { return requestId; }
        public String getExecutionToken() { return executionToken; }
        public String getAction() { return action; }
        public String getBillType() { return billType; }
        public WorkflownoteVO getWorkflowNote() { return workflowNote; }
        public SapplyBillVO getBill() { return bill; }
        public Object getUserObject() { return userObject; }
        public Map<String, Object> getParameters() { return parameters; }
        public SapplyBatchApprovalProof getApprovalProof() { return approvalProof; }
        public TransactionSynchronizationRegistry getTransaction() { return transaction; }
        public Object getTransactionKey() { return transactionKey; }

        private void requireArguments(Object[] current) throws BusinessException {
            if(current==null || current.length!=6 || !action.equals(current[0]) || !billType.equals(current[1])
                    || workflowNote!=current[2] || bill!=current[3] || userObject!=current[4]
                    || liveNativeParameters!=current[5])
                throw new BusinessException("出库申请原生逐单参数在执行前发生变化。");
        }

        private void requireNativeParameters(Map<?,?> current) throws BusinessException {
            if(current.get(REQUEST_PARAMETER)!=envelope)
                throw new BusinessException("出库申请逐单授权上下文已变化。");
            Map<Object,Object> observed=new HashMap<Object,Object>(current);observed.remove(REQUEST_PARAMETER);
            if(!nativeParameters.equals(observed))throw new BusinessException("出库申请原生批量参数在执行前发生变化。");
            if(observed.containsKey(ORIGINAL_BILLS_PARAMETER))
                envelope.requireOriginalBills(observed.get(ORIGINAL_BILLS_PARAMETER),bill);
            if(nativeOriginal!=null) {
                Object[] originals=(Object[])observed.get(ORIGINAL_BILLS_PARAMETER);
                if(originals[originalIndex]!=nativeOriginal || originalFingerprint!=null &&
                        !originalFingerprint.equals(approvalProof.originalFingerprint(nativeOriginal,
                                (PfUserObject)userObject,parameters)))
                    throw new BusinessException("出库申请原生批量原单在审批前发生变化。");
            }
        }
    }

    private static final class GuardedCall implements NativeCall {
        private final Thread caller = Thread.currentThread();
        private final ProceedingJoinpoint joinpoint;
        private final Map<?, ?> parameters;
        private final TransactionSynchronizationRegistry transaction;
        private final Object transactionKey;
        private final Unit unit;
        private boolean closed;
        private boolean called;
        private boolean entered;
        private Throwable failure;
        private Object nativeResult;

        GuardedCall(ProceedingJoinpoint joinpoint, Map<?, ?> parameters,
                TransactionSynchronizationRegistry transaction, Object transactionKey,Unit unit) {
            this.joinpoint = joinpoint;
            this.parameters = parameters;
            this.transaction = transaction;
            this.transactionKey = transactionKey;
            this.unit=unit;
        }

        @SuppressWarnings("unchecked")
        public Object proceed() throws Throwable {
            if (Thread.currentThread() != caller || closed) {
                throw new BusinessException("出库申请逐单原生流程必须在当前事务内调用。");
            }
            if (called) {
                if (failure == null) failure = new BusinessException("出库申请逐单原生流程不能重复调用。");
                throw failure;
            }
            called = true;
            try {
                requireActive(transaction, transactionKey);
                unit.requireArguments(joinpoint.getArgs());
                unit.requireNativeParameters(parameters);
                if(unit.approvalProof!=null) {
                    if(unit.verifiedContext==null || unit.verifiedCommand==null)
                        throw new BusinessException("出库申请逐单审批尚未核对签名内容。");
                    requireApprovalIntent(unit.verifiedContext,unit.verifiedCommand);
                }
                Object envelope=parameters.remove(REQUEST_PARAMETER);
                if (!(envelope instanceof RequestEnvelope))
                    throw new BusinessException("出库申请逐单授权上下文已变化。");
                try { entered=true; nativeResult = joinpoint.proceed(); }
                finally { ((Map<String,Object>)parameters).put(REQUEST_PARAMETER,envelope); }
                requireActive(transaction, transactionKey);
                return nativeResult;
            } catch (Throwable error) {
                failure = error;
                throw error;
            }
        }

        Object result() throws Throwable {
            if (failure != null) throw failure;
            if (!called) throw new BusinessException("出库申请逐单原生流程未执行。");
            requireActive(transaction, transactionKey);
            return nativeResult;
        }
    }
}
