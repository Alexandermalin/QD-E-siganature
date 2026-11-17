package nc.impl.uapbd.qd.esign;

import java.util.List;
import javax.transaction.TransactionSynchronizationRegistry;
import nc.bs.dao.BaseDAO;
import nc.bs.framework.common.InvocationInfoProxy;
import nc.bs.framework.common.NCLocator;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.bs.uapbd.qd.esign.core.NativeTransactionBoundaryProbe;
import nc.bs.uapbd.qd.esign.persistence.QdEsignStore;
import nc.itf.uap.pf.IPFWorkflowQry;
import nc.jdbc.framework.SQLParameter;
import nc.jdbc.framework.processor.ArrayListProcessor;
import nc.vo.pub.BusinessException;
import nc.vo.pub.compiler.PfParameterVO;
import nc.vo.pub.workflownote.WorkflownoteVO;
import nc.vo.wfengine.pub.WFTask;
import nc.vo.wfengine.pub.WfTaskType;

final class InventorySapplyWorkflowApproval {
    private static final Object TRANSACTION_RESOURCE = new Object();
    private static final String NEED_AUTHORIZATION = "该出库申请单待办缺少本次审批的电子签名授权。";

    private InventorySapplyWorkflowApproval() {
    }

    static void register(TransactionSynchronizationRegistry transaction, PfParameterVO parameters, String billPk,
            String requestId) throws BusinessException {
        EsignValues.require(transaction.getResource(TRANSACTION_RESOURCE) == null, NEED_AUTHORIZATION);
        Approval stamp = new Approval(parameters);
        EsignValues.require(same(billPk, stamp.bill) && stamp.complete() && requestId != null, NEED_AUTHORIZATION);
        transaction.putResource(TRANSACTION_RESOURCE, stamp);
        EsignValues.require(transaction.getResource(TRANSACTION_RESOURCE) == stamp, NEED_AUTHORIZATION);
    }

    static void require(PfParameterVO parameters) throws BusinessException {
        Approval presented = new Approval(parameters);
        if (!controlled(presented)) return;
        TransactionSynchronizationRegistry transaction = NativeTransactionBoundaryProbe.requireActiveTransaction();
        Object active = transaction.getResource(TRANSACTION_RESOURCE);
        if (!(active instanceof Approval) || !((Approval) active).consume(presented,
                InvocationInfoProxy.getInstance().getUserId())) {
            transaction.setRollbackOnly();
            throw new BusinessException(NEED_AUTHORIZATION);
        }
    }

    static void checkStarting(PfParameterVO parameters) throws BusinessException {
        Approval presented = new Approval(parameters);
        if (!controlled(presented)) return;
        WFTask supplied = task(parameters);
        if (supplied == null) return;
        EsignValues.require(supplied.getTaskType() == WfTaskType.Makebill.getIntValue()
                && clean(supplied.getApproveResult()) == null, NEED_AUTHORIZATION);
        if (presented.task == null) return;
        WFTask persisted = NCLocator.getInstance().lookup(IPFWorkflowQry.class).queryWFTaskByPk(presented.task);
        EsignValues.require(persisted == null || persisted.getTaskType() == WfTaskType.Makebill.getIntValue()
                && same(presented.bill, clean(persisted.getBillversionPK()))
                && same(presented.process, clean(persisted.getWfProcessInstancePK()))
                && same(presented.activity, clean(persisted.getActivityInstancePK())), NEED_AUTHORIZATION);
    }

    static void checkAlternative(PfParameterVO parameters) throws BusinessException {
        EsignValues.require(!controlled(new Approval(parameters)), NEED_AUTHORIZATION);
    }

    static void confirm() throws BusinessException {
        TransactionSynchronizationRegistry transaction = NativeTransactionBoundaryProbe.requireActiveTransaction();
        Object active = transaction.getResource(TRANSACTION_RESOURCE);
        if (!(active instanceof Approval) || !((Approval) active).consumed) {
            transaction.setRollbackOnly();
            throw new BusinessException("出库申请单审批未经过受控的原生流程服务，无法完成本次审批。");
        }
    }

    private static boolean controlled(Approval presented) throws BusinessException {
        if (presented.bill == null && presented.task == null && presented.noteTask == null && presented.process == null
                && presented.activity == null && presented.note == null && presented.taskBill == null
                && presented.noteBill == null) {
            return false;
        }
        QdEsignStore store = new QdEsignStore();
        String sessionGroup = InvocationInfoProxy.getInstance().getGroupId();
        boolean required = false;
        for (Object value : persistedScopes(presented)) {
            Object[] row = (Object[]) value;
            String billPk = clean((String) row[0]);
            String group = clean((String) row[1]);
            String org = clean((String) row[2]);
            if (!PolicyServiceImpl.resolveLegacy(store, group, org, "40080801", "Approve").isControlled()) {
                continue;
            }
            EsignValues.require(!required && same(group, sessionGroup)
                    && (presented.bill == null || same(billPk, presented.bill)), NEED_AUTHORIZATION);
            required = true;
        }
        return required;
    }

    private static List<?> persistedScopes(Approval presented) throws BusinessException {
        SQLParameter bindings = new SQLParameter();
        for (String id : new String[] {presented.bill, presented.noteBill, presented.taskBill, presented.process,
                presented.task, presented.noteTask, presented.activity, presented.note, presented.note}) {
            bindings.addParam(id);
            bindings.addParam(id);
        }
        return (List<?>) new BaseDAO().executeQuery("SELECT DISTINCT h.cgeneralhid,h.pk_group,h.pk_org"
                + " FROM ic_sapply_h h"
                + " JOIN (SELECT ? AS bill_pk FROM dual"
                + " UNION ALL SELECT RPAD(?,20,' ') FROM dual"
                + " UNION ALL SELECT ? FROM dual"
                + " UNION ALL SELECT RPAD(?,20,' ') FROM dual"
                + " UNION ALL SELECT ? FROM dual"
                + " UNION ALL SELECT RPAD(?,20,' ') FROM dual"
                + " UNION ALL SELECT i.billversionpk FROM pub_wf_instance i"
                + " WHERE i.pk_wf_instance IN (?,RPAD(?,20,' '))"
                + " OR i.pk_wf_instance IN (SELECT t.pk_wf_instance FROM pub_wf_task t"
                + " WHERE t.pk_wf_task IN (?,RPAD(?,20,' '),?,RPAD(?,20,' ')))"
                + " OR i.pk_wf_instance IN (SELECT a.pk_wf_instance FROM pub_wf_actinstance a"
                + " WHERE a.pk_wf_actinstance IN (?,RPAD(?,20,' ')))"
                + " UNION ALL SELECT n.billversionpk FROM pub_workflownote n"
                + " WHERE n.pk_checkflow IN (?,RPAD(?,20,' '))"
                + " UNION ALL SELECT n.billid FROM pub_workflownote n"
                + " WHERE n.pk_checkflow IN (?,RPAD(?,20,' '))) k"
                + " ON (h.cgeneralhid=RTRIM(k.bill_pk)"
                + " OR h.cgeneralhid=RPAD(k.bill_pk,20,' '))"
                + " WHERE NVL(h.dr,0)=0", bindings, new ArrayListProcessor());
    }

    static final class Approval {
        private final String bill;
        private final String noteBill;
        private final String taskBill;
        private final String operator;
        private final String note;
        private final String task;
        private final String noteTask;
        private final String process;
        private final String activity;
        private final String decision;
        private boolean consumed;

        Approval(PfParameterVO parameters) {
            WorkflownoteVO nativeNote = parameters == null ? null : parameters.m_workFlow;
            WFTask nativeTask = task(parameters);
            bill = clean(parameters == null ? null : parameters.m_billVersionPK);
            operator = clean(parameters == null ? null : parameters.m_operator);
            note = clean(nativeNote == null ? null : nativeNote.getPk_checkflow());
            noteBill = clean(nativeNote == null ? null : nativeNote.getBillVersionPK());
            noteTask = clean(nativeNote == null ? null : nativeNote.getPk_wf_task());
            decision = clean(nativeNote == null ? null : nativeNote.getApproveresult());
            task = clean(nativeTask == null ? null : nativeTask.getTaskPK());
            taskBill = clean(nativeTask == null ? null : nativeTask.getBillversionPK());
            process = clean(nativeTask == null ? null : nativeTask.getWfProcessInstancePK());
            activity = clean(nativeTask == null ? null : nativeTask.getActivityInstancePK());
        }

        boolean complete() {
            return same(bill, noteBill) && (taskBill == null || same(bill, taskBill)) && same(task, noteTask)
                    && operator != null && note != null && process != null && activity != null && decision != null;
        }

        boolean consume(Approval current, String principal) {
            if (consumed || !current.complete() || !same(operator, principal) || !same(operator, current.operator)
                    || !same(bill, current.bill) || !same(note, current.note) || !same(task, current.task)
                    || !same(process, current.process) || !same(activity, current.activity) || !same(decision, current.decision)) {
                return false;
            }
            consumed = true;
            return true;
        }
    }

    private static WFTask task(PfParameterVO parameters) {
        WorkflownoteVO note = parameters == null ? null : parameters.m_workFlow;
        return note == null || note.getTaskInfo() == null ? null : note.getTaskInfo().getTask();
    }

    private static boolean same(String a, String b) {
        return a != null && a.equals(b);
    }

    private static String clean(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
