package nc.bs.uapbd.qd.esign.audit;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.transaction.Status;
import javax.transaction.Synchronization;
import javax.transaction.TransactionSynchronizationRegistry;
import nc.bs.framework.aop.ProceedingJoinpoint;
import nc.bs.framework.aop.rt.Aspects;
import nc.bs.uapbd.qd.esign.audit.SapplyWorkflowBatchAspect.NativeCall;
import nc.bs.uapbd.qd.esign.audit.SapplyWorkflowBatchAspect.RequestEnvelope;
import nc.bs.uapbd.qd.esign.audit.SapplyWorkflowBatchAspect.Unit;
import nc.bs.uapbd.qd.esign.audit.SapplyWorkflowBatchAspect.UnitCallback;
import nc.itf.uap.pf.IWorkflowMachine;
import nc.itf.uap.pf.IplatFormEntry;
import nc.vo.ic.m4455.entity.SapplyBillHeadVO;
import nc.vo.ic.m4455.entity.SapplyBillBodyVO;
import nc.vo.ic.m4455.entity.SapplyBillVO;
import nc.vo.ic.general.util.ICLocationUtil;
import nc.vo.ic.location.ICLocationVO;
import nc.vo.ic.qd.esign.InventoryPfStateCommand;
import nc.vo.ic.qd.esign.SapplyPfBatchCommand;
import nc.vo.pub.AggregatedValueObject;
import nc.vo.pub.BusinessException;
import nc.vo.pub.CircularlyAccessibleValueObject;
import nc.vo.pub.IAttributeMeta;
import nc.vo.pub.ISuperVO;
import nc.vo.pub.IVOMeta;
import nc.vo.pub.workflownote.WorkflownoteVO;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.pubapp.pattern.model.meta.entity.bill.IBillMeta;

public final class SapplyWorkflowBatchAspectTests {
    private static final String REQUEST = "abcd0000000000000000000000000123";
    private static final String TOKEN = "ba980000000000000000000000000456";
    private static final String PARAMETER = SapplyWorkflowBatchAspect.REQUEST_PARAMETER;
    private static final IBillMeta META = new IBillMeta() {
        public Map<String, Object> getBusinessAttribute() { return Collections.emptyMap(); }
        public IAttributeMeta[] getChildForeignKeys() { return new IAttributeMeta[0]; }
        public IVOMeta[] getChildren() { return new IVOMeta[0]; }
        public String getComponentName() { return "TEST"; }
        public IVOMeta getParent() { throw new UnsupportedOperationException(); }
        public Class<? extends ISuperVO> getVOClass(IVOMeta meta) { throw new UnsupportedOperationException(); }
        public IVOMeta getVOMeta(Class<? extends ISuperVO> type) { throw new UnsupportedOperationException(); }
    };

    public static void main(String[] args) throws Exception { run(); }

    public static void run() throws Exception {
        verifyGuards();
        try { nativeAdvice(); }
        catch (Exception error) { throw error; }
        catch (Throwable error) { throw new AssertionError(error); }
        System.out.println("PASS SapplyWorkflowBatchAspectTests (native NC AOP and transaction guards)");
    }

    public static void verifyGuards() throws Exception {
        try {
            envelopeSerialization();
            entryBoundary();
            ordinaryCall();
            controlledCall();
            standardInventoryOrigin();
            rejectsUntrustedInput();
            guardedProceed();
            transactionFailure();
            distinguishRejectionFromUnknown();
            approvalContextGuard();
            approvalNativeMerge();
            submittedScopeGuard();
        } catch (Exception error) { throw error; }
        catch (Throwable error) { throw new AssertionError(error); }
        System.out.println("PASS SapplyWorkflowBatchAspectTests (PF pointcut and transaction guards)");
    }

    private static void envelopeSerialization() throws Exception {
        RequestEnvelope envelope = new RequestEnvelope(REQUEST, TOKEN);
        RequestEnvelope decoded = (RequestEnvelope) decode(encode(envelope));
        require(REQUEST.equals(decoded.getRequestId()) && TOKEN.equals(decoded.getExecutionToken()), "native transport preserves validated envelope");
        String debugParameters = marked().toString();
        require(!debugParameters.contains(REQUEST) && !debugParameters.contains(TOKEN)
                && debugParameters.contains("[受控出库申请请求]"), "PF map debug text redacts the credential");
        try {
            new RequestEnvelope(REQUEST, " " + TOKEN);
            throw new AssertionError("malformed envelope constructor accepted token");
        } catch (IllegalArgumentException expected) { }
        corruptToken(envelope);
        try {
            decode(encode(envelope));
            throw new AssertionError("malformed serialized token accepted");
        } catch (InvalidObjectException expected) { }
    }

    private static byte[] encode(RequestEnvelope envelope) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream stream = new ObjectOutputStream(bytes)) { stream.writeObject(envelope); }
        return bytes.toByteArray();
    }

    private static Object decode(byte[] bytes) throws Exception {
        try (ObjectInputStream stream = new ObjectInputStream(new ByteArrayInputStream(bytes))) { return stream.readObject(); }
    }

    private static void corruptToken(RequestEnvelope envelope) throws ReflectiveOperationException {
        Field token = RequestEnvelope.class.getDeclaredField("executionToken");
        token.setAccessible(true);
        token.set(envelope, "invalid");
    }

    private static void nativeAdvice() throws Throwable {
        final Fixture f = new Fixture();
        final WorkflownoteVO note = new WorkflownoteVO();
        f.callback = new UnitCallback() {
            public Object execute(Unit unit, NativeCall nativeCall) throws Throwable {
                require(unit.getWorkflowNote() == note, "actual PF workflow note");
                return nativeCall.proceed();
            }
        };
        Method method = workflowMethod(IWorkflowMachine.class);
        require(f.aspect.isSingleBillWorkflow(method), "native interface signature");
        require(!f.aspect.isSingleBillWorkflow(FakeWorkflow.class.getMethod("unrelated")), "unrelated workflow method");
        require(!f.aspect.isSingleBillWorkflow(FakeWorkflow.class.getMethod("processSingleBillFlow_RequiresNew")),
                "same-name overload is excluded");
        require(f.aspect.isBatchEntry(batchMethod(IplatFormEntry.class)), "native platform batch signature");
        require(!f.aspect.isBatchEntry(IplatFormEntry.class.getMethod("processBatch", String.class,
                WorkflownoteVO.class, String[].class, String[].class)), "native batch overload is excluded");
        final HashMap<String, Object> parameters = marked();
        FakeWorkflowImpl target = new FakeWorkflowImpl();
        FakeWorkflow woven = (FakeWorkflow) Aspects.createAndWeave(f.aspect, target, new Class<?>[]{FakeWorkflow.class});
        require(woven.unrelated() == target && f.lookups == 0, "framework skips unrelated method");
        FakeBatch batch = (FakeBatch) Aspects.createAndWeave(f.aspect, new FakeBatchImpl(f, woven), new Class<?>[]{FakeBatch.class});
        f.active = f.outer;
        require(batch.processBatch("SAVE", "4455", note, new SapplyBillVO[]{bill()}, null, parameters) == target,
                "framework around propagates the native result");
        require(f.lookups == 1 && target.calls == 1 && parameters.containsKey(PARAMETER), "framework cleaned only the per-unit map");
        target.failure = new BusinessException("native failure through advice");
        try {
            batch.processBatch("SAVE", "4455", note, new SapplyBillVO[]{bill()}, null, marked());
            throw new AssertionError("expected native failure through advice");
        } catch (BusinessException actual) {
            require(actual == target.failure && f.tx.rollbackOnly, "framework retains the native checked exception");
        }
    }

    private static void entryBoundary() throws Throwable {
        Fixture f = new Fixture();
        Joinpoint unit = new Joinpoint(bill(), marked());
        require(fail(f, unit) instanceof BusinessException && unit.proceeds == 0 && f.lookups == 0,
                "marked unit requires the native batch entry");

        f = new Fixture();
        f.outer.key = f.tx.key;
        unit = new Joinpoint(bill(), marked());
        Throwable duplicateKey = failInBatch(f, unit);
        require(duplicateKey instanceof BusinessException && duplicateKey.getMessage().contains("独立")
                && unit.proceeds == 0 && f.lookups == 0,
                "outer transaction cannot be used for the unit");

        f = new Fixture();
        HashMap<String, Object> parameters = marked();
        parameters.put(PARAMETER, decode(encode((RequestEnvelope) parameters.get(PARAMETER))));
        Joinpoint entry = batchPoint(parameters);
        try {
            f.aspect.aroundBatchEntry(entry);
            throw new AssertionError("deserialized challenge accepted by entry");
        } catch (BusinessException expected) {
            require(entry.proceeds == 0 && f.tx.rollbackOnly, "deserialized challenge stops before the native batch");
        }

        final Fixture nested = new Fixture();
        final Joinpoint nestedUnit = new Joinpoint(bill(), marked());
        final HashMap<String, Object> nestedParameters = copyParameters(nestedUnit);
        nested.callback = new UnitCallback() {
            public Object execute(Unit unit, NativeCall call) throws Throwable { return call.proceed(); }
        };
        entry = batchPoint(nestedParameters);
        entry.nativeCall = new NativeCall() {
            public Object proceed() throws Throwable {
                HashMap<String, Object> remote = new HashMap<String, Object>(nestedParameters);
                remote.put(PARAMETER, decode(encode((RequestEnvelope) remote.get(PARAMETER))));
                nested.active = nested.tx;
                try {
                    Joinpoint detached = new Joinpoint(bill(), remote);
                    require(fail(nested, detached) instanceof BusinessException && detached.proceeds == 0,
                            "serialization after entry loses the transaction binding");
                } finally { nested.active = nested.outer; }
                throw new BusinessException("injected outer failure");
            }
        };
        nested.active = nested.outer;
        try {
            nested.aspect.aroundBatchEntry(entry);
            throw new AssertionError("expected outer failure");
        } catch (BusinessException expected) {
            require("injected outer failure".equals(expected.getMessage()) && nested.outer.rollbackOnly, "outer error is preserved");
        } finally { nested.active = nested.tx; }
        nested.tx.rollbackOnly = false;
        require(fail(nested, nestedUnit) instanceof BusinessException && nested.lookups == 0,
                "transaction binding is cleared after outer failure");
    }

    private static void ordinaryCall() throws Throwable {
        Fixture f = new Fixture();
        Joinpoint jp = new Joinpoint(bill(), new HashMap<String, Object>());
        require(f.aspect.aroundSingleBill(jp) == jp.returnValue && jp.proceeds == 1, "unmarked Sapply delegates");
        Joinpoint outer = batchPoint(new HashMap<String, Object>());
        require(f.aspect.aroundBatchEntry(outer) == outer.returnValue && outer.proceeds == 1, "unmarked batch delegates");
        require(f.lookups == 0 && f.txAccesses == 0, "unmarked calls do not use the batch services");
        jp.nativeFailure = new BusinessException("native failure");
        require(fail(f, jp) == jp.nativeFailure && !f.tx.rollbackOnly, "ordinary exception is unchanged");
    }

    private static void controlledCall() throws Throwable {
        final Fixture f = new Fixture();
        final Joinpoint jp = new Joinpoint(bill(), marked());
        jp.nativeCall=new NativeCall(){public Object proceed(){
            require(!((Map<?,?>)jp.arguments[5]).containsKey(PARAMETER),"Reserved marker is absent inside native PF");
            return jp.returnValue;
        }};
        f.callback = new UnitCallback() {
            public Object execute(Unit unit, NativeCall nativeCall) throws Throwable {
                require(unit.getRequestId().equals(REQUEST) && unit.getExecutionToken().equals(TOKEN), "split envelope");
                require(unit.getAction().equals("SAVE") && unit.getBillType().equals("4455"), "native route");
                require(unit.getBill() == jp.arguments[3] && unit.getUserObject() == jp.arguments[4], "native bill and user payload");
                require(unit.getTransaction() == f.tx && unit.getTransactionKey() == f.tx.key, "callback transaction");
                require(unit.getParameters().size() == 1 && "kept".equals(unit.getParameters().get("native")), "reserved key excluded");
                require(((HashMap<?, ?>) jp.arguments[5]).containsKey(PARAMETER), "actual native map is untouched before proceed");
                try {
                    unit.getParameters().put("native", "changed");
                    throw new AssertionError("callback parameters should be read-only");
                } catch (UnsupportedOperationException expected) { }
                require(nativeCall.proceed() == jp.returnValue, "callback sees actual native result");
                return "ignored callback result";
            }
        };
        Joinpoint entry = batchPoint(copyParameters(jp));
        require(inBatch(f, entry, jp) == jp.returnValue && jp.proceeds == 1, "PF sees original result exactly once");
        require(((Map<?,?>)jp.arguments[5]).containsKey(PARAMETER),"Marker is restored if NC reuses the unit map");
        require(!f.tx.rollbackOnly, "successful transaction may commit");
        try {
            f.aspect.aroundBatchEntry(entry);
            throw new AssertionError("used challenge accepted again");
        } catch (BusinessException expected) {
            require(entry.proceeds == 1, "used batch challenge cannot proceed again");
        }
    }

    private static void standardInventoryOrigin() throws Throwable {
        final String originKey="nc.bs.scmpub.pf.ORIGIN_VO_PARAMETER";
        final Fixture f=new Fixture();final HashMap<String,Object> parameters=marked();
        parameters.put(PARAMETER,new RequestEnvelope(REQUEST,TOKEN,null,new String[]{"BILL"},true));
        final SapplyBillVO[] origin={ownedBill("BILL","G","O")};parameters.put(originKey,origin);
        final Joinpoint unit=new Joinpoint(ownedBill("BILL","G","O"),parameters);
        unit.nativeCall=new NativeCall(){public Object proceed(){
            require(((Map<?,?>)unit.arguments[5]).get(originKey)==origin,
                    "The native workflow must retain its original-bill parameter");
            return unit.returnValue;
        }};
        f.callback=new UnitCallback(){public Object execute(Unit value,NativeCall nativeCall) throws Throwable {
            require(!value.getParameters().containsKey(originKey),
                    "The server-derived original-bill array must not be treated as signed client parameters");
            return nativeCall.proceed();
        }};
        require(inBatch(f,batchPoint(copyParameters(unit)),unit)==unit.returnValue && unit.proceeds==1,
                "The native original-bill parameter should be passed through once");

        final Fixture invalid=new Fixture();HashMap<String,Object> wrong=marked();
        wrong.put(PARAMETER,new RequestEnvelope(REQUEST,TOKEN,null,new String[]{"BILL"},true));
        RequestEnvelope invalidEnvelope=(RequestEnvelope)wrong.get(PARAMETER);
        wrong.put(originKey,new SapplyBillVO[]{ownedBill("OTHER","G","O")});
        final Joinpoint wrongUnit=new Joinpoint(ownedBill("BILL","G","O"),wrong);
        invalid.callback=new UnitCallback(){public Object execute(Unit value,NativeCall call) throws Throwable{return call.proceed();}};
        require(failInBatch(invalid,wrongUnit) instanceof BusinessException && wrongUnit.proceeds==0,
                "A server-derived original bill outside the authorized selection must be rejected");
        require(invalidEnvelope.getRejectedBeforeNative().equals(Collections.singleton("BILL")),
                "An authorized bill rejected during origin validation has not entered native business");

        final Fixture injected=new Fixture();HashMap<String,Object> untrusted=marked();
        untrusted.put(originKey,new SapplyBillVO[]{ownedBill("BILL","G","O")});
        final Joinpoint injectedUnit=new Joinpoint(ownedBill("BILL","G","O"),untrusted);
        injected.callback=new UnitCallback(){public Object execute(Unit value,NativeCall call) throws Throwable{return call.proceed();}};
        require(failInBatch(injected,injectedUnit) instanceof BusinessException && injectedUnit.proceeds==0,
                "The original-bill parameter requires the verified standard callback");

        final Fixture absent=new Fixture();HashMap<String,Object> missing=marked();
        missing.put(PARAMETER,new RequestEnvelope(REQUEST,TOKEN,null,new String[]{"BILL"},true));
        final Joinpoint missingUnit=new Joinpoint(ownedBill("BILL","G","O"),missing);
        absent.callback=new UnitCallback(){public Object execute(Unit value,NativeCall call) throws Throwable{return call.proceed();}};
        require(failInBatch(absent,missingUnit) instanceof BusinessException && missingUnit.proceeds==0,
                "A verified standard callback must supply its native original-bill parameter");

        final Fixture tampered=new Fixture();final HashMap<String,Object> changed=marked();
        changed.put(PARAMETER,new RequestEnvelope(REQUEST,TOKEN,null,new String[]{"BILL"},true));
        RequestEnvelope changedEnvelope=(RequestEnvelope)changed.get(PARAMETER);
        final SapplyBillVO[] altered={ownedBill("BILL","G","O")};changed.put(originKey,altered);
        final Joinpoint changedUnit=new Joinpoint(ownedBill("BILL","G","O"),changed);
        tampered.callback=new UnitCallback(){public Object execute(Unit value,NativeCall call) throws Throwable {
            altered[0]=ownedBill("BILL","G","OTHER");return call.proceed();
        }};
        require(failInBatch(tampered,changedUnit) instanceof BusinessException && changedUnit.proceeds==0,
                "The original-bill ownership must be rechecked immediately before the native call");
        require(changedEnvelope.getRejectedBeforeNative().equals(Collections.singleton("BILL")),
                "An authorized bill rejected before the guarded native call must be marked unexecuted");

        final Fixture reordered=new Fixture();HashMap<String,Object> reversed=marked();
        reversed.put(PARAMETER,new RequestEnvelope(REQUEST,TOKEN,null,new String[]{"BILL","OTHER"},true));
        reversed.put(originKey,new SapplyBillVO[]{ownedBill("OTHER","G","O"),ownedBill("BILL","G","O")});
        final Joinpoint reorderedUnit=new Joinpoint(ownedBill("BILL","G","O"),reversed);
        reordered.callback=new UnitCallback(){public Object execute(Unit value,NativeCall call) throws Throwable{return call.proceed();}};
        Joinpoint reorderedEntry=new Joinpoint(batchMethod(IplatFormEntry.class),
                new Object[]{"SAVE","4455",null,new SapplyBillVO[]{ownedBill("BILL","G","O"),ownedBill("OTHER","G","O")},null,copyParameters(reorderedUnit)});
        try {inBatch(reordered,reorderedEntry,reorderedUnit);throw new AssertionError("Reordered native origin bills were accepted");}
        catch(BusinessException expected) {require(reorderedUnit.proceeds==0,"Reordered native bills must not reach the workflow");}
    }

    /** Test helper: simulate the standard callback between native batch and unit boundaries. */
    public static void verifyStandardDispatchBoundary(Map<String,Object> batchParameters,SapplyBillVO[] bills)
            throws Throwable {
        final String originKey="nc.bs.scmpub.pf.ORIGIN_VO_PARAMETER";
        final Fixture fixture=new Fixture();
        HashMap<String,Object> unitParameters=new HashMap<String,Object>(batchParameters);
        unitParameters.put(originKey,bills);
        final Joinpoint unit=new Joinpoint(bills[0],unitParameters);
        fixture.callback=new UnitCallback(){public Object execute(Unit value,NativeCall call) throws Throwable {
            require(!value.getParameters().containsKey(originKey),"Derived original bills must not enter signed parameters");
            return call.proceed();
        }};
        Joinpoint entry=new Joinpoint(batchMethod(IplatFormEntry.class),
                new Object[]{"SAVE","4455",null,bills,null,new HashMap<String,Object>(batchParameters)});
        require(inBatch(fixture,entry,unit)==unit.returnValue && unit.proceeds==1,
                "The dispatched standard callback must reach the native unit once");
    }

    private static void rejectsUntrustedInput() throws Throwable {
        Fixture f = new Fixture();
        Joinpoint jp = new Joinpoint(new AggregatedValueObject() {
            public CircularlyAccessibleValueObject[] getChildrenVO() { return null; }
            public CircularlyAccessibleValueObject getParentVO() { return null; }
            public void setChildrenVO(CircularlyAccessibleValueObject[] children) { throw new UnsupportedOperationException(); }
            public void setParentVO(CircularlyAccessibleValueObject parent) { throw new UnsupportedOperationException(); }
        }, marked());
        require(fail(f, jp) instanceof BusinessException && jp.proceeds == 0 && f.tx.rollbackOnly, "foreign bill rejected");
        f = new Fixture();
        HashMap<String, Object> parameters = marked();
        parameters.put(PARAMETER, REQUEST + ':' + TOKEN);
        jp = new Joinpoint(bill(), parameters);
        require(fail(f, jp) instanceof BusinessException && jp.proceeds == 0 && f.tx.rollbackOnly && f.lookups == 0,
                "plain string envelope rejected before native or callback");
        f = new Fixture();
        parameters = marked();
        corruptToken((RequestEnvelope) parameters.get(PARAMETER));
        jp = new Joinpoint(bill(), parameters);
        require(fail(f, jp) instanceof BusinessException && jp.proceeds == 0 && f.tx.rollbackOnly && f.lookups == 0,
                "malformed envelope object rejected before native or callback");
        f = new Fixture();
        jp = new Joinpoint(bill(), marked());
        require(failInBatch(f, jp) instanceof BusinessException && jp.proceeds == 0 && f.tx.rollbackOnly && f.lookups == 1,
                "missing service rejected");
        f = new Fixture();
        f.tx.key = null;
        jp = new Joinpoint(bill(), marked());
        require(fail(f, jp) instanceof BusinessException && jp.proceeds == 0 && f.lookups == 0,
                "missing transaction rejects before callback and native call");
    }

    private static void guardedProceed() throws Throwable {
        final Fixture replaced=new Fixture();final HashMap<String,Object> replacement=marked();
        final RequestEnvelope replacedEnvelope=(RequestEnvelope)replacement.get(PARAMETER);
        final Joinpoint replacedUnit=new Joinpoint(bill(),replacement);
        replaced.callback=new UnitCallback(){public Object execute(Unit unit,NativeCall nativeCall) throws Throwable {
            replacement.put(PARAMETER,new RequestEnvelope(REQUEST,TOKEN,null,new String[]{"BILL"}));
            return nativeCall.proceed();
        }};
        require(failInBatch(replaced,replacedUnit) instanceof BusinessException && replacedUnit.proceeds==0,
                "Replacing the active envelope before the native call must be rejected");
        require(replacedEnvelope.getRejectedBeforeNative().equals(Collections.singleton("BILL")),
                "A replaced envelope is rejected before entering the authorized native unit");
        Fixture f = new Fixture();
        Joinpoint jp = new Joinpoint(bill(), marked());
        f.callback = new UnitCallback() {
            public Object execute(Unit unit, NativeCall nativeCall) { return null; }
        };
        require(failInBatch(f, jp) instanceof BusinessException && jp.proceeds == 0 && f.tx.rollbackOnly, "missing native invocation rejected");
        f = new Fixture();
        jp = new Joinpoint(bill(), marked());
        f.callback = new UnitCallback() {
            public Object execute(Unit unit, NativeCall nativeCall) throws Throwable {
                nativeCall.proceed();
                try { nativeCall.proceed(); } catch (BusinessException expected) { }
                return null;
            }
        };
        require(failInBatch(f, jp) instanceof BusinessException && jp.proceeds == 1 && f.tx.rollbackOnly, "caught duplicate still rolls back");
        f = new Fixture();
        jp = new Joinpoint(bill(), marked());
        jp.nativeFailure = new BusinessException("native failure");
        f.callback = new UnitCallback() {
            public Object execute(Unit unit, NativeCall nativeCall) throws Throwable {
                try { nativeCall.proceed(); } catch (BusinessException swallowed) { }
                return null;
            }
        };
        require(failInBatch(f, jp) == jp.nativeFailure && jp.proceeds == 1 && f.tx.rollbackOnly, "native error remains visible if callback swallows it");
    }

    private static void transactionFailure() throws Throwable {
        final BusinessException auditFailure = new BusinessException("injected audit failure");
        Fixture f = new Fixture();
        Joinpoint jp = new Joinpoint(bill(), marked());
        f.callback = new UnitCallback() {
            public Object execute(Unit unit, NativeCall nativeCall) throws Throwable {
                nativeCall.proceed();
                throw auditFailure;
            }
        };
        require(failInBatch(f, jp) == auditFailure && jp.proceeds == 1 && f.tx.rollbackOnly, "audit failure marks original transaction");
        final Fixture changed = new Fixture();
        jp = new Joinpoint(bill(), marked());
        changed.callback = new UnitCallback() {
            public Object execute(Unit unit, NativeCall nativeCall) throws Throwable {
                nativeCall.proceed();
                changed.tx.key = new Object();
                return null;
            }
        };
        Throwable failure = failInBatch(changed, jp);
        require(failure instanceof BusinessException && failure.getCause() instanceof BusinessException
                && !changed.tx.rollbackOnly && jp.proceeds == 1, "transaction change refuses success without marking another transaction");
    }

    private static void distinguishRejectionFromUnknown() throws Throwable {
        Fixture before = new Fixture();
        Joinpoint beforeUnit = new Joinpoint(bill(), marked());
        RequestEnvelope beforeEnvelope = (RequestEnvelope) ((Map<?,?>) beforeUnit.arguments[5]).get(PARAMETER);
        before.callback = new UnitCallback() {
            public Object execute(Unit unit, NativeCall nativeCall) throws Throwable {
                throw new BusinessException("permission denied before native action");
            }
        };
        failInBatch(before, beforeUnit);
        require(beforeUnit.proceeds == 0 && beforeEnvelope.getRejectedBeforeNative().equals(Collections.singleton("BILL")),
                "A rejection before native execution is recorded by bill primary key");

        final boolean[] changed = new boolean[1];
        SapplyBillVO changingBill = new SapplyBillVO() {
            public IBillMeta getMetaData() { return META; }
            public SapplyBillHeadVO getHead() {
                return changed[0] ? null : new SapplyBillHeadVO() {
                    public String getCgeneralhid() { return "BILL"; }
                };
            }
        };
        Fixture unstable = new Fixture();
        Joinpoint unstableUnit = new Joinpoint(changingBill, marked());
        RequestEnvelope unstableEnvelope = (RequestEnvelope) ((Map<?,?>) unstableUnit.arguments[5]).get(PARAMETER);
        final BusinessException original = new BusinessException("rejected while changing input");
        unstable.callback = new UnitCallback() {
            public Object execute(Unit unit, NativeCall nativeCall) throws Throwable {
                changed[0] = true;
                throw original;
            }
        };
        require(failInBatch(unstable, unstableUnit) == original && unstable.tx.rollbackOnly
                && unstableEnvelope.getRejectedBeforeNative().equals(Collections.singleton("BILL")),
                "VO changes cannot mask the rejection or change its original identity");

        Fixture after = new Fixture();
        Joinpoint afterUnit = new Joinpoint(bill(), marked());
        RequestEnvelope afterEnvelope = (RequestEnvelope) ((Map<?,?>) afterUnit.arguments[5]).get(PARAMETER);
        after.callback = new UnitCallback() {
            public Object execute(Unit unit, NativeCall nativeCall) throws Throwable {
                nativeCall.proceed();
                throw new BusinessException("audit failure after native action");
            }
        };
        failInBatch(after, afterUnit);
        require(afterUnit.proceeds == 1 && afterEnvelope.getRejectedBeforeNative().isEmpty(),
                "A failure after native execution cannot be called a rejected, unexecuted bill");
    }

    private static void approvalContextGuard() throws Throwable {
        final SapplyBillVO first=approvalBill("BILL"),second=approvalBill("OTHER");
        WorkflownoteVO confirmed=new WorkflownoteVO();confirmed.setApproveresult("Y");confirmed.setChecknote("agreed");
        final WorkflownoteVO derived=new WorkflownoteVO();derived.setApproveresult("Y");derived.setChecknote("agreed");
        final HashMap<String,Object> plain=new HashMap<String,Object>();plain.put("batch","batch");plain.put("worknote",null);
        SapplyBatchApprovalProof proof=SapplyBatchApprovalProof.create(new SapplyPfBatchCommand("APPROVEU","4455",
                new SapplyBillVO[]{first,second},null,confirmed,plain));
        final BusinessObjectRef ref=new BusinessObjectRef(proof.signedIds()[0],"IC_SAPPLY","BILL","CODE",null);
        final OperationContext context=new OperationContext(REQUEST,REQUEST,"G","O","40080801","request",
                "Approve","approve","UPDATE",null,Collections.singletonList(ref));
        final InventoryPfStateCommand command=new InventoryPfStateCommand("4455",first,null,derived,plain);
        try{SapplyWorkflowBatchAspect.requireApprovalIntent(context,command);
            throw new AssertionError("An approval outside the native unit was accepted");
        }catch(BusinessException expected) { }
        HashMap<String,Object> parameters=new HashMap<String,Object>(plain);
        parameters.put(PARAMETER,new RequestEnvelope(REQUEST,TOKEN,proof,new String[]{"BILL","OTHER"},true));
        parameters.put("nc.bs.scmpub.pf.ORIGIN_VO_PARAMETER",new SapplyBillVO[]{first,second});
        Joinpoint unit=new Joinpoint(workflowMethod(IWorkflowMachine.class),
                new Object[]{"APPROVEU","4455",derived,first,null,parameters});
        Joinpoint entry=new Joinpoint(batchMethod(IplatFormEntry.class),
                new Object[]{"APPROVEU","4455",confirmed,new SapplyBillVO[]{first,second},null,copyParameters(unit)});
        Fixture fixture=new Fixture();fixture.callback=new UnitCallback(){
            public Object execute(Unit current,NativeCall nativeCall) throws Throwable {
                SapplyWorkflowBatchAspect.requireApprovalIntent(context,command);
                try{SapplyWorkflowBatchAspect.requireApprovalIntent(context,
                        new InventoryPfStateCommand("OTHER",first,null,derived,plain));
                    throw new AssertionError("A different native type was accepted");
                }catch(BusinessException expected) { }
                return nativeCall.proceed();
            }
        };
        inBatch(fixture,entry,unit);
        require(unit.proceeds==1,"Authorized batch approval reaches the native unit once");
        try{SapplyWorkflowBatchAspect.requireApprovalIntent(context,command);
            throw new AssertionError("The unit authorization leaked beyond the callback");
        }catch(BusinessException expected) { }
    }

    private static SapplyBillVO approvalBill(final String pk) {
        final SapplyBillHeadVO head=new SapplyBillHeadVO(){
            public String getCgeneralhid(){return pk;}
            public String getPk_group(){return "G";}
            public String getPk_org(){return "O";}
            public java.util.Set<String> usedAttributeNames(){return Collections.singleton("pk");}
            public Object getAttributeValue(String name){return "pk".equals(name)?pk:null;}
            public IVOMeta getMetaData(){return null;}
        };
        return new SapplyBillVO(){public IBillMeta getMetaData(){return META;}public SapplyBillHeadVO getHead(){return head;}};
    }

    private static void approvalNativeMerge() throws Throwable {
        approvalNativeMergeCase("valid");
        approvalNativeMergeCase("valid-base-array");
        approvalNativeMergeCase("valid-unapprove");
        approvalNativeMergeCase("signed-input-changed");
        approvalNativeMergeCase("converted-changed");
        approvalNativeMergeCase("origin-and-converted-changed");
        approvalNativeMergeCase("converted-changed-before-native");
        approvalNativeMergeCase("signed-input-changed-before-native");
        approvalNativeMergeCase("origin-changed-before-native");
        approvalNativeMergeCase("location-changed-before-native");
        approvalNativeMergeCase("native-argument-replaced");
        approvalNativeMergeCase("approval-check-skipped");
        approvalNativeMergeCase("note-changed-before-native");
        approvalNativeMergeCase("unapprove-note-changed-before-native");
    }

    private static void approvalNativeMergeCase(final String scenario) throws Throwable {
        final SapplyBillVO first=mergeBill("BILL",null),second=mergeBill("OTHER",null);
        final SapplyBillVO[] inputs={first,second};
        if("valid-base-array".equals(scenario))
            first.setChildren(MERGE_PARENT,new SapplyBillBodyVO[]{first.getBodys()[0]});
        final SapplyBillVO[] originals={mergeBill("BILL","persisted"),mergeBill("OTHER","other")};
        Map<Integer,Map<Integer,ICLocationVO[]>> locations=ICLocationUtil.getLocationVO(inputs);
        final SapplyBillVO[] converted={(SapplyBillVO)originals[0].clone(),(SapplyBillVO)originals[1].clone()};
        new nc.vo.pubapp.pattern.model.transfer.bill.ServerBillCombinClient().combine(converted,inputs);
        converted[0]=(SapplyBillVO)converted[0].clone();converted[1]=(SapplyBillVO)converted[1].clone();
        ICLocationUtil.setLocationVO(converted,locations);
        require("persisted".equals(converted[0].getHead().getVnote()),"The native merge must retain the stored field");
        require("stored-row".equals(converted[0].getBodys()[0].getAttributeValue("stored_body"))
                && "client-location".equals(converted[0].getBodys()[0].getLocationVOs()[0].getClocationid()),
                "The native merge retains stored body data and restores the submitted location");
        boolean undo=scenario.contains("unapprove");
        String action=undo?"UNAPPROVEU":"APPROVEU",button=undo?"UnApprove":"Approve";
        WorkflownoteVO note=undo?null:new WorkflownoteVO();
        if(note!=null){note.setApproveresult("Y");note.setChecknote("agreed");}
        final HashMap<String,Object> plain=new HashMap<String,Object>();plain.put("batch","batch");plain.put("worknote",null);
        SapplyBatchApprovalProof proof=SapplyBatchApprovalProof.create(new SapplyPfBatchCommand(action,"4455",inputs,null,note,plain));
        if("signed-input-changed".equals(scenario))first.getHead().setVnote("changed");
        if("converted-changed".equals(scenario))converted[0].getHead().setVnote("changed");
        final BusinessObjectRef ref=new BusinessObjectRef(proof.signedIds()[0],"IC_SAPPLY","BILL","CODE-BILL",null);
        final OperationContext context=new OperationContext(REQUEST,REQUEST,"G","O","40080801","request",
                button,button,"UPDATE",null,Collections.singletonList(ref));
        final WorkflownoteVO derived=new WorkflownoteVO();
        if(!undo){derived.setApproveresult("Y");derived.setChecknote("agreed");}
        HashMap<String,Object> parameters=new HashMap<String,Object>(plain);
        parameters.put(PARAMETER,new RequestEnvelope(REQUEST,TOKEN,proof,new String[]{"BILL","OTHER"},true));
        parameters.put("nc.bs.scmpub.pf.ORIGIN_VO_PARAMETER",originals);
        final Joinpoint unit=new Joinpoint(workflowMethod(IWorkflowMachine.class),
                new Object[]{action,"4455",derived,converted[0],null,parameters});
        Joinpoint entry=new Joinpoint(batchMethod(IplatFormEntry.class),
                new Object[]{action,"4455",note,inputs,null,copyParameters(unit)});
        Fixture fixture=new Fixture();fixture.callback=new UnitCallback(){
            public Object execute(Unit current,NativeCall nativeCall) throws Throwable {
                if("origin-and-converted-changed".equals(scenario)) {
                    originals[0].getHead().setVnote("changed");converted[0].getHead().setVnote("changed");
                }
                if(!"approval-check-skipped".equals(scenario))
                    SapplyWorkflowBatchAspect.requireApprovalIntent(context,
                            new InventoryPfStateCommand("4455",current.getBill(),null,derived,current.getParameters()));
                if("converted-changed-before-native".equals(scenario))converted[0].getHead().setVnote("changed");
                if("signed-input-changed-before-native".equals(scenario))first.getHead().setVnote("changed");
                if("origin-changed-before-native".equals(scenario))originals[0].getHead().setVnote("changed");
                if("location-changed-before-native".equals(scenario)) {
                    MergeLocation changed=new MergeLocation();changed.setClocationid("changed");
                    converted[0].getBodys()[0].setLocationVOs(new ICLocationVO[]{changed});
                }
                if("native-argument-replaced".equals(scenario))unit.arguments[3]=mergeBill("BILL","changed");
                if("note-changed-before-native".equals(scenario)
                        || "unapprove-note-changed-before-native".equals(scenario))derived.setChecknote("changed");
                return nativeCall.proceed();
            }
        };
        if(scenario.startsWith("valid")) {
            inBatch(fixture,entry,unit);
            require(unit.proceeds==1,"A faithful native merge must preserve the approved payload");
        } else {
            try {inBatch(fixture,entry,unit);throw new AssertionError("Approval mutation accepted: "+scenario);}
            catch(BusinessException expected){require(unit.proceeds==0,"Changed approval must not reach native: "+scenario);}
        }
    }

    private static SapplyBillVO mergeBill(String pk,String storedNote) {
        SapplyBillVO bill=new SapplyBillVO(){
            public IBillMeta getMetaData(){return MERGE_META;}
        };
        SapplyBillHeadVO head=new MergeHead();head.setCgeneralhid(pk);head.setPk_group("G");head.setPk_org("O");
        head.setVbillcode("CODE-"+pk);if(storedNote!=null)head.setVnote(storedNote);bill.setParent(head);
        MergeBody body=new MergeBody();body.setCgeneralbid(pk+"-ROW");body.setPseudoColumn(Integer.valueOf(1));
        if(storedNote!=null)body.setAttributeValue("stored_body","stored-row");
        MergeLocation location=new MergeLocation();location.setClocationid(storedNote==null?"client-location":"stored-location");
        body.setLocationVOs(new ICLocationVO[]{location});bill.setChildren(MERGE_PARENT,new MergeBody[]{body});return bill;
    }

    /** Test VO avoids requiring the running NC metadata service during this isolated merge check. */
    private static final class MergeHead extends SapplyBillHeadVO {
        private final Map<String,Object> values=new HashMap<String,Object>();
        public IVOMeta getMetaData(){return null;}
        public Object getAttributeValue(String name){return values.get(name);}
        public void setAttributeValue(String name,Object value){values.put(name,value);}
        public java.util.Set<String> usedAttributeNames(){return values.keySet();}
        public Object clone(){
            MergeHead result=new MergeHead();result.values.putAll(values);
            result.setStatus(getStatus());result.setDirty(isDirty());return result;
        }
    }

    public static final class MergeBody extends SapplyBillBodyVO {
        private final Map<String,Object> values=new HashMap<String,Object>();
        public IVOMeta getMetaData(){return null;}
        public Object getAttributeValue(String name){return values.get(name);}
        public void setAttributeValue(String name,Object value){values.put(name,value);}
        public java.util.Set<String> usedAttributeNames(){return values.keySet();}
        public Object clone(){
            MergeBody result=new MergeBody();result.values.putAll(values);result.setLocationVOs(getLocationVOs());
            result.setStatus(getStatus());result.setDirty(isDirty());return result;
        }
    }

    private static final class MergeLocation extends ICLocationVO {
        private final Map<String,Object> values=new HashMap<String,Object>();
        public IVOMeta getMetaData(){return null;}
        public Object getAttributeValue(String name){return values.get(name);}
        public void setAttributeValue(String name,Object value){values.put(name,value);}
        public java.util.Set<String> usedAttributeNames(){return values.keySet();}
    }

    private static final IVOMeta MERGE_PARENT=(IVOMeta)java.lang.reflect.Proxy.newProxyInstance(
            IVOMeta.class.getClassLoader(),new Class<?>[]{IVOMeta.class},new java.lang.reflect.InvocationHandler(){
                public Object invoke(Object proxy,Method method,Object[] args){throw new AssertionError(method.getName());}
            });
    private static final IBillMeta MERGE_META=new IBillMeta(){
        public Map<String,Object> getBusinessAttribute(){return Collections.emptyMap();}
        public IAttributeMeta[] getChildForeignKeys(){return new IAttributeMeta[0];}
        public IVOMeta[] getChildren(){return new IVOMeta[]{MERGE_PARENT};}
        public String getComponentName(){return "TEST";}
        public IVOMeta getParent(){return MERGE_PARENT;}
        public Class<? extends ISuperVO> getVOClass(IVOMeta meta){throw new UnsupportedOperationException();}
        public IVOMeta getVOMeta(Class<? extends ISuperVO> type){throw new UnsupportedOperationException();}
    };

    private static void submittedScopeGuard() throws Throwable {
        Fixture wrongEntry=new Fixture();
        HashMap<String,Object> parameters=marked();
        Joinpoint entry=new Joinpoint(batchMethod(IplatFormEntry.class),
                new Object[]{"SAVE","4455",null,new SapplyBillVO[]{approvalBill("OTHER")},null,parameters});
        wrongEntry.active=wrongEntry.outer;
        try{wrongEntry.aspect.aroundBatchEntry(entry);throw new AssertionError("A different submitted bill was accepted");}
        catch(BusinessException expected){require(entry.proceeds==0,"Wrong batch selection is rejected before native PF");}

        Fixture wrongUnit=new Fixture();
        Joinpoint excluded=new Joinpoint(approvalBill("OTHER"),marked());
        RequestEnvelope excludedEnvelope=(RequestEnvelope)((Map<?,?>)excluded.arguments[5]).get(PARAMETER);
        require(failInBatch(wrongUnit,excluded) instanceof BusinessException && excluded.proceeds==0 && wrongUnit.lookups==0,
                "A precheck-excluded bill cannot enter the native unit callback");
        require(excludedEnvelope.getRejectedBeforeNative().isEmpty(),
                "An unauthorized unit must not alter the selected bills' rejection receipts");

        final Fixture duplicate=new Fixture();
        duplicate.callback=new UnitCallback(){public Object execute(Unit unit,NativeCall call) throws Throwable{return call.proceed();}};
        HashMap<String,Object> shared=marked();
        final Joinpoint first=new Joinpoint(bill(),shared),second=new Joinpoint(bill(),shared);
        Joinpoint outer=batchPoint(shared);
        outer.nativeCall=new NativeCall(){public Object proceed() throws Throwable {
            duplicate.active=duplicate.tx;
            try{duplicate.aspect.aroundSingleBill(first);return duplicate.aspect.aroundSingleBill(second);}
            finally{duplicate.active=duplicate.outer;}
        }};
        duplicate.active=duplicate.outer;
        try{duplicate.aspect.aroundBatchEntry(outer);throw new AssertionError("A unit was executed twice");}
        catch(BusinessException expected){require(first.proceeds==1 && second.proceeds==0,"A submitted bill executes at most once");}
    }

    private static Throwable fail(Fixture f, Joinpoint jp) throws Throwable {
        try { f.aspect.aroundSingleBill(jp); }
        catch (Exception expected) { return expected; }
        throw new AssertionError("expected an exception");
    }

    private static Throwable failInBatch(Fixture f, Joinpoint unit) throws Throwable {
        try { inBatch(f, batchPoint(copyParameters(unit)), unit); }
        catch (Exception expected) { return expected; }
        throw new AssertionError("expected an exception");
    }

    private static Object inBatch(final Fixture f, Joinpoint entry, final Joinpoint unit) throws Throwable {
        entry.nativeCall = new NativeCall() {
            public Object proceed() throws Throwable {
                f.active = f.tx;
                try { return f.aspect.aroundSingleBill(unit); }
                finally { f.active = f.outer; }
            }
        };
        f.active = f.outer;
        try { return f.aspect.aroundBatchEntry(entry); }
        finally { f.active = f.tx; }
    }

    @SuppressWarnings("unchecked")
    private static HashMap<String, Object> copyParameters(Joinpoint unit) {
        return new HashMap<String, Object>((HashMap<String, Object>) unit.arguments[5]);
    }

    private static Joinpoint batchPoint(HashMap<String, Object> parameters) throws NoSuchMethodException {
        return new Joinpoint(batchMethod(IplatFormEntry.class),
                new Object[]{"SAVE", "4455", null, new SapplyBillVO[]{bill()}, null, parameters});
    }

    private static HashMap<String, Object> marked() {
        HashMap<String, Object> parameters = new HashMap<String, Object>();
        parameters.put(PARAMETER, new RequestEnvelope(REQUEST, TOKEN, null, new String[]{"BILL"}));
        parameters.put("native", "kept");
        return parameters;
    }

    private static Method workflowMethod(Class<?> type) throws NoSuchMethodException {
        return type.getMethod("processSingleBillFlow_RequiresNew", String.class, String.class,
                WorkflownoteVO.class, AggregatedValueObject.class, Object.class, HashMap.class);
    }

    private static Method batchMethod(Class<?> type) throws NoSuchMethodException {
        return type.getMethod("processBatch", String.class, String.class, WorkflownoteVO.class,
                AggregatedValueObject[].class, Object[].class, HashMap.class);
    }

    private static SapplyBillVO bill() {
        return new SapplyBillVO() {
            public IBillMeta getMetaData() { return META; }
            public SapplyBillHeadVO getHead() {
                return new SapplyBillHeadVO() { public String getCgeneralhid() { return "BILL"; } };
            }
        };
    }

    private static SapplyBillVO ownedBill(final String pk,final String group,final String org) {
        return new SapplyBillVO() {
            public IBillMeta getMetaData() { return META; }
            public SapplyBillHeadVO getHead() {
                return new SapplyBillHeadVO() {
                    public String getCgeneralhid(){return pk;}
                    public String getPk_group(){return group;}
                    public String getPk_org(){return org;}
                };
            }
        };
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public interface FakeWorkflow {
        Object processSingleBillFlow_RequiresNew(String action, String type, WorkflownoteVO note,
                AggregatedValueObject bill, Object user, HashMap<String, Object> parameters) throws BusinessException;
        Object processSingleBillFlow_RequiresNew();
        Object unrelated();
    }

    public static class FakeWorkflowImpl implements FakeWorkflow {
        int calls;
        BusinessException failure;
        public Object processSingleBillFlow_RequiresNew(String action, String type, WorkflownoteVO note,
                AggregatedValueObject bill, Object user, HashMap<String, Object> parameters) throws BusinessException {
            require(!parameters.containsKey(PARAMETER) && "kept".equals(parameters.get("native")), "actual native input");
            calls++;
            if (failure != null) throw failure;
            return this;
        }
        public Object processSingleBillFlow_RequiresNew() { return this; }
        public Object unrelated() { return this; }
    }

    public interface FakeBatch {
        Object processBatch(String action, String type, WorkflownoteVO note, AggregatedValueObject[] bills,
                Object[] users, HashMap<String, Object> parameters) throws BusinessException;
    }

    public static class FakeBatchImpl implements FakeBatch {
        private Fixture fixture;
        private FakeWorkflow workflow;

        public FakeBatchImpl() { }
        FakeBatchImpl(Fixture fixture, FakeWorkflow workflow) {
            this.fixture = fixture;
            this.workflow = workflow;
        }

        public Object processBatch(String action, String type, WorkflownoteVO note, AggregatedValueObject[] bills,
                Object[] users, HashMap<String, Object> parameters) throws BusinessException {
            fixture.active = fixture.tx;
            try {
                return workflow.processSingleBillFlow_RequiresNew(action, type, note, bills[0], null,
                        new HashMap<String, Object>(parameters));
            } finally { fixture.active = fixture.outer; }
        }
    }

    private static final class Fixture implements SapplyWorkflowBatchAspect.RuntimeAccess {
        final Tx tx = new Tx();
        final Tx outer = new Tx();
        Tx active = tx;
        final SapplyWorkflowBatchAspect aspect = new SapplyWorkflowBatchAspect(this);
        int lookups;
        int txAccesses;
        UnitCallback callback;
        public UnitCallback callback() { lookups++; return callback; }
        public TransactionSynchronizationRegistry transaction() { txAccesses++; return active; }
    }

    private static final class Tx implements TransactionSynchronizationRegistry {
        Object key = new Object();
        boolean rollbackOnly;
        public Object getTransactionKey() { return key; }
        public int getTransactionStatus() { return Status.STATUS_ACTIVE; }
        public boolean getRollbackOnly() { return rollbackOnly; }
        public void setRollbackOnly() { rollbackOnly = true; }
        public void putResource(Object key, Object value) { throw new UnsupportedOperationException(); }
        public Object getResource(Object key) { throw new UnsupportedOperationException(); }
        public void registerInterposedSynchronization(Synchronization sync) { throw new UnsupportedOperationException(); }
    }

    private static final class Joinpoint implements ProceedingJoinpoint {
        final Object[] arguments;
        final Object returnValue = new Object();
        final Method method;
        int proceeds;
        Throwable nativeFailure;
        NativeCall nativeCall;
        Joinpoint(AggregatedValueObject bill, HashMap<String, Object> parameters) throws NoSuchMethodException {
            this(workflowMethod(IWorkflowMachine.class), new Object[]{"SAVE", "4455", null, bill, new Object(), parameters});
        }
        Joinpoint(Method method, Object[] arguments) {
            this.method = method;
            this.arguments = arguments;
        }
        public Object[] getArgs() { return arguments; }
        public AccessibleObject getStaticPart() { return method; }
        @SuppressWarnings("unchecked") public <T> T proceed() throws Throwable {
            proceeds++;
            if (nativeFailure != null) throw nativeFailure;
            return (T) (nativeCall == null ? returnValue : nativeCall.proceed());
        }
        public Object getThis() { throw new UnsupportedOperationException(); }
        public Object getResult() { throw new UnsupportedOperationException(); }
        public Throwable getThrowable() { throw new UnsupportedOperationException(); }
        public Object getAspected() { throw new UnsupportedOperationException(); }
        public void setAttribute(String name, Object value) { throw new UnsupportedOperationException(); }
        public void removeAttribute(String name) { throw new UnsupportedOperationException(); }
        public void clearAttributes() { throw new UnsupportedOperationException(); }
    }
}
