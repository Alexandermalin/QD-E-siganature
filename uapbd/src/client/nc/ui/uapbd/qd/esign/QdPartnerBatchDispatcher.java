package nc.ui.uapbd.qd.esign;

import java.awt.Container;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import javax.swing.Action;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import nc.bs.framework.common.InvocationInfoProxy;
import nc.bs.framework.common.NCLocator;
import nc.itf.uapbd.qd.esign.IOperationService;
import nc.itf.uapbd.qd.esign.IArchiveBatchResultService;
import nc.itf.uapbd.qd.esign.IPolicyService;
import nc.itf.uap.bd.errorlog.IErrorLogQueryService;
import nc.ui.bd.pub.batchupdate.config.BatchUpdateContext;
import nc.ui.bd.pub.batchupdate.config.IBatchUpdateTab;
import nc.ui.uif2.model.AbstractAppModel;
import nc.ui.uif2.model.AbstractUIAppModel;
import nc.vo.bd.cust.CustomerVO;
import nc.vo.bd.material.MaterialVO;
import nc.vo.bd.errorlog.ErrLogResult;
import nc.vo.bd.errorlog.ErrLogReturnValue;
import nc.vo.bd.errorlog.ErrorLogVO;
import nc.vo.bd.errorlog.ErrorMsgVO;
import nc.vo.bd.errorlog.ErrorLogUtil;
import nc.vo.bd.supplier.SupplierVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.uapbd.qd.esign.ArchiveBatchResult;
import nc.vo.uapbd.qd.esign.ArchiveBatchStatus;
import nc.vo.uapbd.qd.esign.ArchiveBatchUpdateResult;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.OperationResponse;
import nc.vo.uapbd.qd.esign.PartnerBatchDraft;
import nc.vo.uapbd.qd.esign.PartnerOrgBatchPlan;

/** Candidate bridge from NC's fast-dialog worker to guarded main-tab batch requests. */
final class QdPartnerBatchDispatcher implements QdBatchUpdateTabGuard.Dispatcher {
    private final AbstractAppModel model;private final PartnerOperationOrganization operationScope;
    QdPartnerBatchDispatcher(AbstractUIAppModel model) {
        this(model,null);
    }
    QdPartnerBatchDispatcher(AbstractUIAppModel model,PartnerOperationOrganization scope) {
        if(!(model instanceof AbstractAppModel)) throw new IllegalArgumentException("Native archive model required");
        this.model=(AbstractAppModel)model;this.operationScope=scope;
    }

    public ErrLogReturnValue execute(BatchUpdateContext context,IBatchUpdateTab tab,String selectedAttribute,String[] fields,
            Map<String,Object> values,String[] permissionOrgs,String[] targetOrgs,String[] pks,boolean needReturn) throws BusinessException {
        if(context==null || tab==null || !tab.isMainTab() || !context.isFastBatchUpdate() || !needReturn
                || pks==null || pks.length==0)
            throw new BusinessException("该快速批改页签尚未完成受控接入。");
        final PartnerBatchDraft draft;
        try { draft=new PartnerBatchDraft(tab.getID(),tab.getCode(),selectedAttribute,fields,values); }
        catch(IllegalArgumentException error) { throw new BusinessException("快速批改包含尚未核实的字段值。",error); }
        final String[] selected=pks.clone();
        final BatchAnchor anchor=onEdt(new Callable<BatchAnchor>() {
            public BatchAnchor call() throws Exception { return BatchAnchor.capture(model,operationScope); }
        });
        final int chunks=(selected.length-1)/PartnerBatchDraft.MAX_SINGLE_TRANSACTION_OBJECTS+1;
        if(chunks>1) {
            boolean accepted=onEdt(new Callable<Boolean>() { public Boolean call() throws Exception {
                anchor.requireCurrent(model,operationScope);
                String text="本次选择 "+selected.length+" 个档案，将分为 "+chunks+" 批独立执行，每批分别授权。"
                    +"前面已完成的批次不会因后续批次取消或失败而回滚。";
                return Boolean.valueOf(JOptionPane.showConfirmDialog(dialogParent(model),text,"快速批改分批执行",
                    JOptionPane.OK_CANCEL_OPTION,JOptionPane.INFORMATION_MESSAGE)==JOptionPane.OK_OPTION);
            }}).booleanValue();
            if(!accepted) throw new BusinessException("快速批改已取消，尚未执行任何批次。");
        }
        List<QdPartnerBatchChunks.Completed> parts=QdPartnerBatchChunks.execute(selected,
            PartnerBatchDraft.MAX_SINGLE_TRANSACTION_OBJECTS,new QdPartnerBatchChunks.Operation() {
                public void execute(String[] batch,int number,int total,QdPartnerBatchChunks.Attempt attempt) throws BusinessException {
                    requireAnchor(anchor);
                    executeSingle(draft,batch,anchor,attempt);
                }
            });
        try {
            requireAnchor(anchor);
            final Class<? extends SuperVO> type=type(anchor.node);
            ErrLogReturnValue combined=QdPartnerBatchChunks.combine(parts,type,new QdPartnerBatchChunks.LogWriter() {
                public ErrLogResult write(List<String> messages) throws BusinessException {
                    return writeCombinedErrors(anchor,messages);
                }
            },new QdPartnerBatchChunks.NativeLogReader() {
                public String[] messageIds(String logId) throws BusinessException {
                    requireLogIdentity(anchor);
                    String[] result=NCLocator.getInstance().lookup(IErrorLogQueryService.class).queryErrorMsgPksByErrorLogPk(logId);
                    requireLogIdentity(anchor);return result;
                }
                public List<ErrorMsgVO> messages(String[] ids) throws BusinessException {
                    requireLogIdentity(anchor);
                    List<ErrorMsgVO> result=NCLocator.getInstance().lookup(IErrorLogQueryService.class).queryErrorMsgVOByPks(ids);
                    requireLogIdentity(anchor);return result;
                }
            });
            requireAnchor(anchor);return combined;
        } catch(BusinessException error) { throw QdPartnerBatchChunks.mergeFailure(parts,error); }
        catch(RuntimeException error) { throw QdPartnerBatchChunks.mergeFailure(parts,error); }
    }

    /** Candidate wiring for a future verified organization-tab entry; execute() still rejects that entry. */
    ErrLogReturnValue executeOrganizationCandidate(BatchUpdateContext context,IBatchUpdateTab tab,String selectedAttribute,String[] fields,
            Map<String,Object> values,String[] targetOrgs,String[] pks,boolean needReturn) throws BusinessException {
        if(context==null || tab==null || tab.isMainTab() || !context.isFastBatchUpdate() || !needReturn
                || targetOrgs==null || targetOrgs.length==0 || pks==null || pks.length==0)
            throw new BusinessException("组织页签快速批改的选择不完整。");
        final PartnerBatchDraft draft;
        try { draft=new PartnerBatchDraft(tab.getID(),tab.getCode(),selectedAttribute,fields,values,targetOrgs); }
        catch(IllegalArgumentException error) { throw new BusinessException("组织页签快速批改的字段或目标不正确。",error); }
        final String[] selected=pks.clone();
        final BatchAnchor anchor=onEdt(new Callable<BatchAnchor>() {
            public BatchAnchor call() throws Exception { return BatchAnchor.capture(model,operationScope); }
        });
        final OperationContext planning=batchContext(anchor,draft,selected,Integer.MAX_VALUE);
        QdPartnerOrgBatchPlan.Reader reader=new QdPartnerOrgBatchPlan.Reader() {
            public PartnerOrgBatchPlan read(OperationContext request) throws BusinessException {
                requireAnchor(anchor);requireLogIdentity(anchor);
                PartnerOrgBatchPlan plan=NCLocator.getInstance().lookup(IPolicyService.class).planOrganizationBatch(request);
                requireLogIdentity(anchor);requireAnchor(anchor);return plan;
            }
        };
        QdPartnerOrgBatchWorkflow.Hooks hooks=new QdPartnerOrgBatchWorkflow.Hooks() {
            public void requireCurrent() throws BusinessException { requireAnchor(anchor);requireLogIdentity(anchor); }
            public boolean approve(final int roots,final long pages,final int slices) throws BusinessException {
                return onEdt(new Callable<Boolean>() { public Boolean call() throws Exception {
                    anchor.requireCurrent(model,operationScope);
                    String text="本次选择 "+roots+" 个档案，计划处理 "+pages+" 条组织页签，分为 "+slices+" 批。"
                        +"每批分别授权和提交；已完成的批次不会因后续取消或失败而回滚。"
                        +(pages==0?"没有匹配页签，业务结果将记录为未变化。":"计划后新增的页签不会加入本次选择。");
                    return Boolean.valueOf(JOptionPane.showConfirmDialog(dialogParent(model),text,"组织页签快速批改",
                        JOptionPane.OK_CANCEL_OPTION,JOptionPane.INFORMATION_MESSAGE)==JOptionPane.OK_OPTION);
                }}).booleanValue();
            }
            public void execute(QdPartnerOrgBatchSlices.Slice slice,int number,int total,QdPartnerOrgBatchProgress.Attempt attempt)
                    throws BusinessException {
                requireAnchor(anchor);executeOrganizationSingle(slice,anchor,attempt);
            }
            public ErrLogResult write(List<String> messages) throws BusinessException {
                requireCurrent();ErrLogResult result=writeCombinedErrors(anchor,messages);requireCurrent();return result;
            }
            public String[] messageIds(String logId) throws BusinessException {
                requireCurrent();String[] result=NCLocator.getInstance().lookup(IErrorLogQueryService.class)
                    .queryErrorMsgPksByErrorLogPk(logId);requireCurrent();return result;
            }
            public List<ErrorMsgVO> messages(String[] ids) throws BusinessException {
                requireCurrent();List<ErrorMsgVO> result=NCLocator.getInstance().lookup(IErrorLogQueryService.class)
                    .queryErrorMsgVOByPks(ids);requireCurrent();return result;
            }
        };
        return QdPartnerOrgBatchWorkflow.execute(planning,reader,hooks);
    }

    private void executeOrganizationSingle(final QdPartnerOrgBatchSlices.Slice slice,final BatchAnchor anchor,
            final QdPartnerOrgBatchProgress.Attempt attempt) throws BusinessException {
        final Session session=onEdt(new Callable<Session>() { public Session call() throws Exception {
            anchor.requireCurrent(model,operationScope);return new Session(model,slice.draft(),slice.roots(),operationScope,anchor,slice);
        }});
        boolean accepted=false,completed=false,receiptConfirmed=false;Throwable problem=null;OperationContext dispatchedRequest=null;
        try {
            accepted=onEdt(new Callable<Boolean>() { public Boolean call() { return Boolean.valueOf(session.begin()); } }).booleanValue();
            if(!accepted)throw new BusinessException("组织页签快速批改已取消或授权未通过。",session.signing.getLastError());
            if(Thread.currentThread().isInterrupted())throw new BusinessException("组织页签快速批改已中断。");
            final OperationContext request=session.authorized;
            if(request==null || !onEdt(new Callable<Boolean>() { public Boolean call() { return Boolean.valueOf(session.isAvailable(request)); } }).booleanValue())
                throw new BusinessException("组织页签快速批改授权范围不完整。");
            attempt.dispatched(request);session.dispatched=request.getRequestId();dispatchedRequest=request;
            OperationResponse response=NCLocator.getInstance().lookup(IOperationService.class).execute(request.getRequestId(),slice.draft());
            attempt.confirmed(response,readBatchStatus(request,anchor));receiptConfirmed=true;
            onEdt(new Callable<Object>() { public Object call() { session.lifecycle.afterDoActionSuccessed(session.action,session.event);return null; } });
            completed=true;
        } catch(BusinessException failure) { problem=failure;throw failure; }
        catch(RuntimeException failure) { BusinessException wrapped=new BusinessException("组织页签快速批改客户端处理失败。",failure);problem=wrapped;throw wrapped; }
        catch(Error failure) { problem=failure;throw failure; }
        finally {
            if(accepted && !completed) {
                final Throwable original=problem;
                try { onEdt(new Callable<Object>() { public Object call() { session.lifecycle.afterDoActionFailed(session.action,session.event,original);return null; } }); }
                catch(BusinessException cleanup) { if(problem==null)throw cleanup;problem.addSuppressed(cleanup); }
                catch(RuntimeException cleanup) { if(problem==null)throw cleanup;problem.addSuppressed(cleanup); }
                catch(Error cleanup) { if(problem==null)throw cleanup;problem.addSuppressed(cleanup); }
            }
            if(dispatchedRequest!=null && !receiptConfirmed && problem!=null && !(problem instanceof Error))
                recoverOrganizationAfterFailure(dispatchedRequest,anchor,attempt,problem);
        }
    }

    private ErrLogReturnValue executeSingle(final PartnerBatchDraft draft,final String[] selected,
            final BatchAnchor anchor,final QdPartnerBatchChunks.Attempt attempt) throws BusinessException {
        final Session session=onEdt(new Callable<Session>() {
            public Session call() throws Exception {
                if(anchor!=null) anchor.requireCurrent(model,operationScope);
                return new Session(model,draft,selected,operationScope,anchor);
            }
        });
        boolean accepted=false,completed=false,receiptConfirmed=false;Throwable problem=null;OperationContext dispatchedRequest=null;
        try {
            accepted=onEdt(new Callable<Boolean>() { public Boolean call() { return Boolean.valueOf(session.begin()); } }).booleanValue();
            if(!accepted) throw new BusinessException("快速批改已取消或授权未通过。",session.signing.getLastError());
            if(Thread.currentThread().isInterrupted()) throw new BusinessException("快速批改已中断，请重新发起。");
            final OperationContext request=session.authorized;
            if(request==null || request.getRequestId()==null || !onEdt(new Callable<Boolean>() {
                    public Boolean call() { return Boolean.valueOf(session.isAvailable(request)); }
                }).booleanValue())
                throw new BusinessException("快速批改授权范围不完整，请重新发起。");
            final String requestId=request.getRequestId();
            if(attempt!=null) attempt.dispatched(requestId);
            session.dispatched=requestId;dispatchedRequest=request;
            OperationResponse response=NCLocator.getInstance().lookup(IOperationService.class).execute(requestId,draft);
            if(response==null || response.isReplayed() || !requestId.equals(response.getRequestId())
                || !(response.getBusinessResult() instanceof ArchiveBatchUpdateResult))
                throw new BusinessException("无法确认快速批改结果，请核对请求号："+requestId);
            ArchiveBatchUpdateResult businessResult=(ArchiveBatchUpdateResult)response.getBusinessResult();
            ErrLogReturnValue result=validateResult(request,businessResult);
            if(attempt!=null) attempt.confirmed(result,businessResult.getNativeResult().getErrLogResult());
            receiptConfirmed=true;
            onEdt(new Callable<Object>() { public Object call() { session.lifecycle.afterDoActionSuccessed(session.action,session.event);return null; } });
            completed=true;return result;
        } catch(BusinessException failure) {
            BusinessException reported=attempt==null?dispatchFailure(session.dispatched,receiptConfirmed,failure):failure;
            problem=reported;throw reported;
        } catch(RuntimeException failure) {
            BusinessException reported=attempt==null?dispatchFailure(session.dispatched,receiptConfirmed,failure)
                :new BusinessException("快速批改客户端处理失败。",failure);
            problem=reported;throw reported;
        } catch(Error failure) {
            problem=failure;throw failure;
        } finally {
            if(accepted && !completed) {
                final Throwable original=problem;
                try { onEdt(new Callable<Object>() { public Object call() { session.lifecycle.afterDoActionFailed(session.action,session.event,original);return null; } }); }
                catch(BusinessException cleanup) { if(problem==null) throw cleanup;problem.addSuppressed(cleanup); }
                catch(RuntimeException cleanup) { if(problem==null) throw cleanup;problem.addSuppressed(cleanup); }
                catch(Error cleanup) { if(problem==null) throw cleanup;problem.addSuppressed(cleanup); }
            }
            // Cleanup may transition an unclaimed READY request to FAILED. Read its status afterwards.
            if(attempt!=null && dispatchedRequest!=null && !receiptConfirmed && problem!=null && !(problem instanceof Error))
                recoverAfterFailure(dispatchedRequest,anchor,attempt,problem);
        }
    }

    private void recoverAfterFailure(OperationContext request,final BatchAnchor anchor,QdPartnerBatchChunks.Attempt attempt,Throwable failure) {
        if(Thread.currentThread().isInterrupted()) return;
        try {
            QdPartnerBatchRecovery.Summary status=readRecovery(request,anchor);
            attempt.recovered(status);
            while(!Thread.currentThread().isInterrupted()) {
                final QdPartnerBatchRecovery.Summary shown=status;
                int choice=onEdt(new Callable<Integer>() { public Integer call() throws Exception {
                    anchor.requireCurrent(model,operationScope);
                    Object[] options=shown.terminal()?new Object[]{"关闭"}:new Object[]{"刷新状态","关闭"};
                    return Integer.valueOf(JOptionPane.showOptionDialog(dialogParent(model),new QdPartnerBatchRecoveryPanel(shown),
                        "快速批改结果核对",JOptionPane.DEFAULT_OPTION,JOptionPane.INFORMATION_MESSAGE,null,options,options[options.length-1]));
                }}).intValue();
                if(shown.terminal() || choice!=0 || Thread.currentThread().isInterrupted()) break;
                try { status=readRecovery(request,anchor);attempt.recovered(status); }
                catch(BusinessException error) {
                    if(error!=failure) failure.addSuppressed(error);
                    showRecoveryRefreshFailure();break;
                } catch(RuntimeException error) {
                    if(error!=failure) failure.addSuppressed(error);
                    showRecoveryRefreshFailure();break;
                }
            }
        } catch(BusinessException error) { if(error!=failure) failure.addSuppressed(error); }
        catch(RuntimeException error) { if(error!=failure) failure.addSuppressed(error); }
    }

    private void recoverOrganizationAfterFailure(OperationContext request,final BatchAnchor anchor,
            QdPartnerOrgBatchProgress.Attempt attempt,Throwable failure) {
        if(Thread.currentThread().isInterrupted())return;
        try {
            ArchiveBatchStatus raw=readBatchStatus(request,anchor);attempt.recovered(raw);
            QdPartnerBatchRecovery.Summary status=QdPartnerBatchRecovery.verify(request,raw);
            while(!Thread.currentThread().isInterrupted()) {
                final QdPartnerBatchRecovery.Summary shown=status;
                int choice=onEdt(new Callable<Integer>() { public Integer call() throws Exception {
                    anchor.requireCurrent(model,operationScope);
                    Object[] options=shown.terminal()?new Object[]{"关闭"}:new Object[]{"刷新状态","关闭"};
                    return Integer.valueOf(JOptionPane.showOptionDialog(dialogParent(model),new QdPartnerBatchRecoveryPanel(shown),
                        "组织页签批改结果核对",JOptionPane.DEFAULT_OPTION,JOptionPane.INFORMATION_MESSAGE,null,options,options[options.length-1]));
                }}).intValue();
                if(shown.terminal() || choice!=0 || Thread.currentThread().isInterrupted())break;
                try { raw=readBatchStatus(request,anchor);attempt.recovered(raw);status=QdPartnerBatchRecovery.verify(request,raw); }
                catch(BusinessException error) { if(error!=failure)failure.addSuppressed(error);showRecoveryRefreshFailure();break; }
                catch(RuntimeException error) { if(error!=failure)failure.addSuppressed(error);showRecoveryRefreshFailure();break; }
            }
        } catch(BusinessException error) { if(error!=failure)failure.addSuppressed(error); }
        catch(RuntimeException error) { if(error!=failure)failure.addSuppressed(error); }
    }

    private void showRecoveryRefreshFailure() throws BusinessException {
        onEdt(new Callable<Object>() { public Object call() {
            JOptionPane.showMessageDialog(dialogParent(model),"刷新失败，已保留上一次查询结果。请稍后按请求号核对。",
                "快速批改结果核对",JOptionPane.ERROR_MESSAGE);return null;
        }});
    }

    private QdPartnerBatchRecovery.Summary readRecovery(OperationContext request,BatchAnchor anchor) throws BusinessException {
        return QdPartnerBatchRecovery.verify(request,readBatchStatus(request,anchor));
    }

    private ArchiveBatchStatus readBatchStatus(OperationContext request,BatchAnchor anchor) throws BusinessException {
        if(request==null || request.getRequestId()==null)throw new BusinessException("快速批改恢复查询缺少请求号。");
        requireAnchor(anchor);requireLogIdentity(anchor);
        ArchiveBatchStatus status=NCLocator.getInstance().lookup(IArchiveBatchResultService.class).getResult(request.getRequestId());
        requireLogIdentity(anchor);requireAnchor(anchor);return status;
    }

    static BusinessException dispatchFailure(String requestId,boolean receiptConfirmed,Throwable failure) {
        if(requestId==null && failure instanceof BusinessException) return (BusinessException)failure;
        String message=requestId==null?"快速批改未完成。":receiptConfirmed
            ?"快速批改业务结果已确认，但客户端后续处理失败，请查询请求号："+requestId
            :"快速批改结果尚未确认，请查询请求号："+requestId;
        return new BusinessException(message,failure);
    }

    private void requireAnchor(final BatchAnchor anchor) throws BusinessException {
        onEdt(new Callable<Object>() { public Object call() throws Exception {
            anchor.requireCurrent(model,operationScope);return null;
        }});
    }

    /** NC's error-detail window queries one log id, so multi-batch errors need their own native log. */
    private static ErrLogResult writeCombinedErrors(BatchAnchor anchor,List<String> messages) throws BusinessException {
        requireLogIdentity(anchor);
        String md="10140CUG".equals(anchor.node) || "10140CUO".equals(anchor.node)?"e4f48eaf-5567-4383-a370-a59cb3e8a451"
            :"10140SUG".equals(anchor.node) || "10140SUO".equals(anchor.node)?"720dcc7c-ff19-48f4-b9c5-b90906682f45"
            :"c7dc0ccd-8872-4eee-8882-160e8f49dfad";
        ErrorLogUtil log=new ErrorLogUtil(md,anchor.user,"快速批改",true);log.setNewTransaction(true);
        for(int start=0;start<messages.size();start+=500) {
            if(Thread.currentThread().isInterrupted()) throw new BusinessException("快速批改错误日志汇总已中断。");
            int end=Math.min(start+500,messages.size());
            log.addLogMsgArrayBatch(messages.subList(start,end).toArray(new String[end-start]));
            log.writeLogMsgBatch();
        }
        requireLogIdentity(anchor);
        return log.getErrLogResult();
    }

    private static void requireLogIdentity(BatchAnchor anchor) throws BusinessException {
        InvocationInfoProxy identity=InvocationInfoProxy.getInstance();
        if(!anchor.group.equals(identity.getGroupId()) || !anchor.user.equals(identity.getUserId()))
            throw new BusinessException("快速批改日志处理期间操作用户或集团已变化。");
    }

    private static Class<? extends SuperVO> type(String node) throws BusinessException {
        if("10140CUG".equals(node) || "10140CUO".equals(node)) return CustomerVO.class;
        if("10140SUG".equals(node) || "10140SUO".equals(node)) return SupplierVO.class;
        if("10140MAG".equals(node) || "10140MAO".equals(node)) return MaterialVO.class;
        throw new BusinessException("快速批改节点不正确。");
    }

    /** Validate returned units and avoid showing a successful summary when NC omitted filtered error messages. */
    static ErrLogReturnValue validateResult(OperationContext request,ArchiveBatchUpdateResult result) throws BusinessException {
        if(request==null || result==null || result.getNativeResult()==null || result.getUnits().size()!=request.getObjects().size())
            throw new BusinessException("快速批改结果不完整，请按请求号核对。");
        Map<String,BusinessObjectRef> expected=new HashMap<String,BusinessObjectRef>();Set<String> success=new HashSet<String>();
        List<BusinessObjectRef> failed=new ArrayList<BusinessObjectRef>();
        for(BusinessObjectRef ref:request.getObjects()) if(ref==null || ref.getObjectPk()==null || expected.put(ref.getClientObjectId(),ref)!=null)
            throw new BusinessException("快速批改授权对象重复或不完整。");
        for(ArchiveBatchResult.Unit unit:result.getUnits()) {
            BusinessObjectRef ref=unit==null?null:expected.remove(unit.getClientId());
            if(ref==null || !ref.getObjectPk().equals(unit.getObjectPk())) throw new BusinessException("快速批改结果与授权对象不一致。");
            if("SUCCESS".equals(unit.getState())) success.add(ref.getObjectPk());
            else if("FAILED".equals(unit.getState())) failed.add(ref);
            else if(!"UNCHANGED".equals(unit.getState())) throw new BusinessException("快速批改对象结果不明确。");
        }
        ErrLogReturnValue original=result.getNativeResult();
        if(!expected.isEmpty() || original.getErrLogResult()==null || original.getTotalNum()!=request.getObjects().size())
            throw new BusinessException("快速批改原生结果不完整。");
        Object raw=original.getReturnValue();Set<String> returned=new HashSet<String>();
        if(raw!=null && !(raw instanceof SuperVO[])) throw new BusinessException("快速批改原生返回类型不正确。");
        boolean customer="10140CUG".equals(request.getNodeCode()) || "10140CUO".equals(request.getNodeCode());
        boolean material="10140MAG".equals(request.getNodeCode()) || "10140MAO".equals(request.getNodeCode());
        if(raw!=null) for(SuperVO row:(SuperVO[])raw) {
            String pk=row==null?null:row.getPrimaryKey();
            if(row==null || (customer?row.getClass()!=CustomerVO.class:material?row.getClass()!=MaterialVO.class:row.getClass()!=SupplierVO.class)
                    || pk==null || !success.contains(pk) || !returned.add(pk)) throw new BusinessException("快速批改原生成功对象不一致。");
        }
        if(!returned.equals(success)) throw new BusinessException("快速批改原生结果缺少成功档案。");
        ErrLogResult errors=original.getErrLogResult();
        if(errors.getErrorMessagegNum()<0 || (errors.getErrorMessagegNum()>0 && failed.isEmpty()))
            throw new BusinessException("快速批改原生错误与对象结果不一致。");
        ErrorLogVO log=errors.getErrorLog();
        if(log==null) { log=new ErrorLogVO();log.setOperationtype("快速批改"); }
        if(failed.isEmpty()) {
            if(errors.getErrorLog()!=null) return original;
            return new ErrLogReturnValue(new ErrLogResult(log,0,errors.getErrorMsgs()),raw,original.getTotalNum());
        }
        List<ErrorMsgVO> messages=new ArrayList<ErrorMsgVO>();
        if(errors.getErrorMsgs()!=null) for(ErrorMsgVO item:errors.getErrorMsgs()) if(item!=null) messages.add(item);
        for(BusinessObjectRef item:failed) {
            ErrorMsgVO message=new ErrorMsgVO();String code=item.getBillCode();
            message.setErrormsg("档案 "+(code==null?item.getObjectPk():code)+" 未通过原生批改校验，未写入。");messages.add(message);
        }
        return new ErrLogReturnValue(new ErrLogResult(log,failed.size(),messages.toArray(new ErrorMsgVO[messages.size()])),raw,original.getTotalNum());
    }

    private static String operationOrganization(AbstractAppModel model,PartnerOperationOrganization scope) throws BusinessException {
        if(model==null || model.getContext()==null) throw new BusinessException("快速批改缺少操作上下文。");
        String node=model.getContext().getNodeCode();
        if("10140MAG".equals(node)) return model.getContext().getPk_group();
        if("10140MAO".equals(node)) {
            if(scope==null) throw new BusinessException("无法确定物料快速批改操作业务单元，请重新查询。");
            return scope.requireOperationOrganization();
        }
        return QdPartnerContextOrganizations.operationOrganization(model,"快速批改");
    }
    private static Container dialogParent(AbstractAppModel model) {
        Container root=model.getContext().getEntranceUI();if(root==null) return null;Window owner=SwingUtilities.getWindowAncestor(root);
        Window active=KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
        for(Window current=active;current!=null;current=current.getOwner()) if(current==owner && active.isShowing()) return active;
        return root;
    }
    static final class BatchAnchor {
        final String group,node,org,user;
        BatchAnchor(String group,String node,String org,String user) { this.group=group;this.node=node;this.org=org;this.user=user; }
        static BatchAnchor capture(AbstractAppModel model,PartnerOperationOrganization scope) throws BusinessException {
            if(model==null || model.getContext()==null) throw new BusinessException("快速批改缺少操作上下文。");
            String group=model.getContext().getPk_group(),node=model.getContext().getNodeCode(),org=operationOrganization(model,scope);
            InvocationInfoProxy identity=InvocationInfoProxy.getInstance();String user=identity.getUserId();
            type(node);
            if(group==null || org==null || user==null || !group.equals(identity.getGroupId()))
                throw new BusinessException("快速批改操作用户、集团或组织不完整。");
            return new BatchAnchor(group,node,org,user);
        }
        void requireCurrent(AbstractAppModel model,PartnerOperationOrganization scope) throws BusinessException {
            requireSame(capture(model,scope));
        }
        void requireSame(BatchAnchor current) throws BusinessException {
            if(group==null || node==null || org==null || user==null || current==null)
                throw new BusinessException("快速批改操作上下文不完整。");
            if(!group.equals(current.group) || !node.equals(current.node) || !org.equals(current.org) || !user.equals(current.user))
                throw new BusinessException("快速批改操作用户、集团、节点或组织已变化，请重新查询。");
        }
    }

    static OperationContext batchContext(BatchAnchor anchor,PartnerBatchDraft draft,String[] selected,int maximum) throws BusinessException {
        if(anchor==null || anchor.group==null || anchor.org==null || anchor.node==null || anchor.user==null || draft==null
                || selected==null || selected.length==0 || maximum<1 || selected.length>maximum)
            throw new BusinessException("快速批改缺少有效的档案选择或操作上下文。");
        boolean customer="10140CUG".equals(anchor.node) || "10140CUO".equals(anchor.node);
        boolean material="10140MAG".equals(anchor.node) || "10140MAO".equals(anchor.node);
        if(!(customer || material || "10140SUG".equals(anchor.node) || "10140SUO".equals(anchor.node)))
            throw new BusinessException("快速批改入口不正确。");
        Set<String> unique=new HashSet<String>();List<BusinessObjectRef> refs=new ArrayList<BusinessObjectRef>();
        for(String pk:selected) {
            if(pk==null || !pk.matches("[A-Za-z0-9_-]{1,20}") || !unique.add(pk))throw new BusinessException("快速批改档案主键不正确或重复。");
            refs.add(new BusinessObjectRef(UUID.randomUUID().toString(),customer?"BD_CUSTOMER":material?"BD_MATERIAL":"BD_SUPPLIER",pk,null,null));
        }
        return new OperationContext(null,null,anchor.group,anchor.org,anchor.node,customer?"客户":material?"物料":"供应商",
            "BatchUpdate","快速批改","UPDATE",null,refs,OperationContext.OWNER_POLICY_PROTOCOL,null,null,null,draft);
    }

    private static final class Session implements OperationContextProvider,AuthorizedRequestConsumer {
        final AbstractAppModel model;final PartnerBatchDraft draft;final String[] selected;final PartnerOperationOrganization operationScope;
        final BatchAnchor anchor;final QdPartnerOrgBatchSlices.Slice organizationSlice;
        final QdExplicitSignatureAction action;final ActionEvent event;final ElectronicSignatureActionInterceptor signing;
        final SignatureLifecycleInterceptor lifecycle;OperationContext authorized;volatile String dispatched;
        Session(AbstractAppModel model,PartnerBatchDraft draft,String[] selected,PartnerOperationOrganization scope,BatchAnchor anchor) {
            this(model,draft,selected,scope,anchor,null);
        }
        Session(AbstractAppModel model,PartnerBatchDraft draft,String[] selected,PartnerOperationOrganization scope,BatchAnchor anchor,
                QdPartnerOrgBatchSlices.Slice organizationSlice) {
            this.model=model;this.draft=draft;this.selected=selected.clone();this.operationScope=scope;this.anchor=anchor;action=new QdExplicitSignatureAction();
            this.organizationSlice=organizationSlice;
            event=new ActionEvent(action,ActionEvent.ACTION_PERFORMED,"BatchUpdate");signing=new ElectronicSignatureActionInterceptor();
            signing.setContextProvider(this);signing.setRequestConsumer(this);lifecycle=action.install(signing);
        }
        boolean begin() { return lifecycle.beforeDoAction(action,event); }
        public OperationContext createContext(Action source,ActionEvent actual) throws BusinessException {
            if(anchor!=null) anchor.requireCurrent(model,operationScope);
            if(source!=action || actual!=event || model.getContext()==null)
                throw new BusinessException("快速批改缺少原生档案选择。");
            return batchContext(anchor==null?BatchAnchor.capture(model,operationScope):anchor,draft,selected,
                PartnerBatchDraft.MAX_SINGLE_TRANSACTION_OBJECTS);
        }
        public boolean isAvailable(OperationContext context) {
            if(context==null || model.getContext()==null || !OperationContext.OWNER_POLICY_PROTOCOL.equals(context.getPolicyProtocol())
                || !ArchiveBatchResult.isPartnerBatchUpdate(context.getNodeCode(),context.getButtonCode(),context.getOperationType())
                || context.getGroupId()==null || context.getOrgId()==null || !context.getNodeCode().equals(model.getContext().getNodeCode())
                || !context.getGroupId().equals(model.getContext().getPk_group())
                || context.getObjects().size()!=selected.length || context.getPartnerBatchDraft()==null) return false;
            try {
                if(anchor!=null) anchor.requireCurrent(model,operationScope);
                if(!context.getOrgId().equals(QdPartnerBatchDispatcher.operationOrganization(model,operationScope))) return false;
            }
            catch(BusinessException error) { return false; }
            if(organizationSlice!=null && context.getRequestId()!=null)try { organizationSlice.requireAuthorized(context); }
            catch(BusinessException error) { return false; }
            Set<String> pks=new HashSet<String>();for(BusinessObjectRef ref:context.getObjects()) pks.add(ref.getObjectPk());
            Set<String> expected=new HashSet<String>();for(String pk:selected) expected.add(pk);return pks.equals(expected) && pks.size()==selected.length;
        }
        public boolean bind(OperationContext value) { if(authorized!=null || value==null || value.getRequestId()==null || !isAvailable(value)) return false;authorized=value;return true; }
        public boolean wasDispatched(String id) { return id!=null && id.equals(dispatched); }
        public void release(String id) { if(authorized!=null && authorized.getRequestId().equals(id)) authorized=null; }
        public Container getParentContainer() { return dialogParent(model); }
        public String getCurrentUserDisplayName() { return InvocationInfoProxy.getInstance().getUserCode(); }
    }
    /** Wait for the EDT even if the worker is interrupted, so an enqueued signing dialog cannot outlive its caller. */
    static <T> T onEdt(Callable<T> task) throws BusinessException {
        // AWT initialization may itself consume an existing interrupt; save it before touching Swing.
        boolean interrupted=Thread.interrupted();
        try {
            if(SwingUtilities.isEventDispatchThread()) try { return task.call(); }
            catch(BusinessException error) { throw error; } catch(Exception error) { throw new BusinessException("快速批改界面处理失败。",error); }
            FutureTask<T> future=new FutureTask<T>(task);SwingUtilities.invokeLater(future);
            while(true) try { return future.get(); }
            catch(InterruptedException error) { interrupted=true; }
            catch(ExecutionException error) {
                Throwable cause=error.getCause();if(cause instanceof BusinessException) throw (BusinessException)cause;
                if(cause instanceof Error) throw (Error)cause;throw new BusinessException("快速批改界面处理失败。",cause);
            }
        } finally { if(interrupted) Thread.currentThread().interrupt(); }
    }
}
