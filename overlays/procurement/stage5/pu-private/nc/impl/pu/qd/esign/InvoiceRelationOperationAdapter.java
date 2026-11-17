package nc.impl.pu.qd.esign;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import nc.bs.framework.common.NCLocator;
import nc.itf.pu.qd.esign.IInvoiceRelationOperationAdapter;
import nc.itf.uapbd.qd.esign.IInvoiceRelationJournal;
import nc.vo.pu.qd.esign.InvoiceRelationCommand;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;

public final class InvoiceRelationOperationAdapter implements IInvoiceRelationOperationAdapter {
    @Override public void validate(OperationContext context, Serializable raw) throws BusinessException {
        requireCommand(context, raw);
    }

    @Override public Serializable execute(final OperationContext context, Serializable raw) throws BusinessException {
        final InvoiceRelationCommand command = requireCommand(context, raw);
        final InvoiceRelationNative plan = InvoiceRelationNative.prepare(context, command);
        return executeTracked(context, command.getInvoicePk(), plan.externalNumbers,
                NCLocator.getInstance().lookup(IInvoiceRelationJournal.class), new NativeCall() {
                    @Override public String external() throws Exception { return plan.external(); }
                    @Override public void local() throws BusinessException { plan.local(); }
                });
    }

    interface NativeCall {
        String external() throws Exception;
        void local() throws BusinessException;
    }

    static String executeTracked(OperationContext context, String invoicePk, String externalNumbers,
            IInvoiceRelationJournal journal, NativeCall call) throws BusinessException {
        journal.assertClear(invoicePk);
        if (externalNumbers.isEmpty()) {
            call.local();
            return invoicePk;
        }

        String requestId = context.getRequestId();
        journal.start_RequiresNew(context, invoicePk, externalNumbers);
        String phase;
        try {
            phase = call.external();
        } catch (Exception ex) {
            phase = "EXTERNAL_UNKNOWN";
        }
        try {
            journal.checkpoint_RequiresNew(requestId, phase);
            if (IInvoiceRelationJournal.EXTERNAL_ACCEPTED.equals(phase)) {
                call.local();
                journal.finish(requestId);
                return invoicePk;
            }
        } catch (BusinessException ex) {
            BusinessException failure = pending(requestId, phase);
            failure.addSuppressed(ex);
            throw failure;
        } catch (RuntimeException ex) {
            BusinessException failure = pending(requestId, phase);
            failure.addSuppressed(ex);
            throw failure;
        }
        throw pending(requestId, phase);
    }

    private static BusinessException pending(String requestId, String phase) {
        return new BusinessException("数电发票关联结果待核对。请求号 " + requestId + "，外部阶段 " + phase + "。");
    }

    @Override public List<BusinessObjectRef> describeResult(OperationContext context, Serializable result)
            throws BusinessException {
        BusinessObjectRef ref = context.getObjects().get(0);
        if (!ref.getObjectPk().equals(result)) throw new BusinessException("采购发票关联回传结果与授权对象不一致。");
        return Collections.singletonList(ref);
    }

    private static InvoiceRelationCommand requireCommand(OperationContext context, Serializable raw)
            throws BusinessException {
        if (context == null || !(raw instanceof InvoiceRelationCommand)
                || !InvoiceRelationCommand.NODE.equals(context.getNodeCode()) || context.getObjects().size() != 1) {
            throw new BusinessException("采购发票关联缺少当前动作授权。");
        }
        InvoiceRelationCommand command = (InvoiceRelationCommand) raw;
        command.validateShape();
        BusinessObjectRef ref = context.getObjects().get(0);
        if (!command.getButton().equals(context.getButtonCode())
                || !InvoiceRelationCommand.operation(command.getButton()).equals(context.getOperationType())
                || !InvoiceRelationCommand.OBJECT_TYPE.equals(ref.getObjectType()) || ref.getParentClientObjectId() != null
                || !command.getInvoicePk().equals(ref.getObjectPk()) || !command.bindingId().equals(ref.getClientObjectId())) {
            throw new BusinessException("采购发票或所选数电发票与签章范围不匹配。");
        }
        return command;
    }
}
