package nc.ui.uapbd.qd.esign;

import java.awt.Container;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.swing.Action;
import javax.swing.JOptionPane;
import nc.bs.framework.common.InvocationInfoProxy;
import nc.bs.framework.common.NCLocator;
import nc.itf.uapbd.qd.esign.IMaterialPageDeleteService;
import nc.ui.bd.pub.orginfo.model.OrgInfoBillManageModel;
import nc.ui.pubapp.uif2app.actions.interceptor.CompositeActionInterceptor;
import nc.ui.uif2.actions.ActionInterceptor;
import nc.ui.uif2.actions.DeleteAction;
import nc.vo.bd.material.MaterialVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.MaterialOrgPage;
import nc.vo.uapbd.qd.esign.MaterialPageDeleteCommand;
import nc.vo.uapbd.qd.esign.MaterialPageDeleteResult;
import nc.vo.uapbd.qd.esign.OperationContext;

/** Retains NC's confirmation and asynchronous worker while dispatching one authorized batch. */
public final class QdMaterialOrgInfoDeleteAction extends DeleteAction
        implements OperationContextProvider, AuthorizedRequestConsumer {
    private static final long serialVersionUID = 1L;
    private final MaterialOrgPage page;
    private final QdMaterialOrgInfoModelService service;
    private QdMaterialListModelDataManager operationScope;
    private volatile ActionEvent activeEvent;
    private volatile boolean workerStarted;
    private volatile OperationContext authorized;
    private volatile String dispatched;
    private volatile Map<String,SuperVO> selectedRows;
    private volatile MaterialPageDeleteResult result;

    public QdMaterialOrgInfoDeleteAction(QdMaterialOrgInfoModelService service, String button) {
        this.service=service;
        this.page=MaterialOrgPage.forDeleteButton(button);
        if (service==null || page==null || !page.domain.equals(service.getObjectType())) {
            throw new IllegalArgumentException("Material page delete configuration mismatch");
        }
    }
    public void setOperationScope(QdMaterialListModelDataManager value) { operationScope=value; }

    @Override public void actionPerformed(ActionEvent event) {
        if (activeEvent!=null) return;
        activeEvent=event;workerStarted=false;
        try { super.actionPerformed(event); }
        finally { if (!workerStarted) clearAttempt(); }
    }

    @Override public boolean beforeStartDoAction(final ActionEvent event) throws Exception {
        if (event!=activeEvent || workerStarted || model().getService()!=service) {
            throw new BusinessException("物料页签删除接入状态已变化，请重新发起。");
        }
        if (!super.beforeStartDoAction(event)) return false;
        final ActionInterceptor original=getInterceptor();
        ElectronicSignatureActionInterceptor signing=new ElectronicSignatureActionInterceptor() {
            @Override public void afterDoActionSuccessed(Action action,ActionEvent finished) {
                try {
                    super.afterDoActionSuccessed(action,finished);
                    if (original!=null) original.afterDoActionSuccessed(action,finished);
                } catch (RuntimeException error) {
                    QdMaterialOrgInfoDeleteAction.super.doAfterFailure(finished,error);
                    clearAttempt();
                    throw error;
                } catch (Error error) {
                    QdMaterialOrgInfoDeleteAction.super.doAfterFailure(finished,error);
                    clearAttempt();
                    throw error;
                } finally { setInterceptor(original); }
            }
            @Override public boolean afterDoActionFailed(Action action,ActionEvent finished,Throwable error) {
                try {
                    boolean handled=super.afterDoActionFailed(action,finished,error);
                    return original==null ? handled : original.afterDoActionFailed(action,finished,error) && handled;
                } finally { setInterceptor(original); clearAttempt(); }
            }
        };
        signing.setContextProvider(this);signing.setRequestConsumer(this);
        CompositeActionInterceptor chain=new CompositeActionInterceptor();chain.addInterceptor(signing);
        SignatureLifecycleInterceptor outer=null;
        try {
            setInterceptor(chain);
            outer=SignatureLifecycleInstaller.install(this,signing);
            if (!outer.beforeDoAction(this,event)) {
                super.doAfterFailure(event,new BusinessException("物料页签删除已取消。"));
                setInterceptor(original);
                return false;
            }
            if (authorized==null || !sameSelection(authorized)) {
                throw new BusinessException("物料页签选择已变化，请重新发起删除。");
            }
            workerStarted=true;
            return true;
        } catch (Exception error) {
            signing.finishLifecycle(signing.lifecycleGeneration(),this,event,error);
            setInterceptor(original);
            throw error;
        } catch (Error error) {
            signing.finishLifecycle(signing.lifecycleGeneration(),this,event,error);
            setInterceptor(original);
            throw error;
        }
    }

    @Override public void doAction(ActionEvent event) throws Exception {
        OperationContext request=authorized;Map<String,SuperVO> rows=selectedRows;
        if (event!=activeEvent || !workerStarted || request==null || rows==null || dispatched!=null) {
            throw new BusinessException("物料页签删除缺少一次性授权。");
        }
        dispatched=request.getRequestId();
        MaterialPageDeleteResult receipt=NCLocator.getInstance().lookup(IMaterialPageDeleteService.class)
            .execute(request.getRequestId(),new MaterialPageDeleteCommand(rows));
        validateReceipt(request,receipt);
        result=receipt;
    }

    @Override public void doAfterSuccess(ActionEvent event) {
        MaterialPageDeleteResult receipt=result;Map<String,SuperVO> rows=selectedRows;
        try {
            if (receipt==null || rows==null) throw new BusinessException("无法确认删除结果，请重新查询。");
            List<SuperVO> removed=new ArrayList<SuperVO>();
            int succeeded=0,failed=0,unknown=0;
            for (MaterialPageDeleteResult.Unit unit:receipt.getUnits()) {
                if ("SUCCESS".equals(unit.getState())) {
                    succeeded++;
                    SuperVO row=rows.get(unit.getClientObjectId());
                    if (row!=null && model().findBusinessData(row)>=0) removed.add(row);
                } else if ("FAILED".equals(unit.getState())) failed++; else unknown++;
            }
            if (!removed.isEmpty()) model().directlyDelete(removed.toArray(new Object[removed.size()]));
            if ("SUCCESS".equals(receipt.getState())) super.doAfterSuccess(event);
            else {
                super.doAfterFailure(event,new BusinessException("物料页签删除未全部成功。"));
                JOptionPane.showMessageDialog(getParentContainer(),"删除成功 " + succeeded + " 条，失败 " + failed
                    + " 条，待核对 " + unknown + " 条。请求号：" + receipt.getRequestId(),"物料页签删除结果",
                    JOptionPane.WARNING_MESSAGE);
            }
        } catch (Exception error) {
            super.doAfterFailure(event,error);
            JOptionPane.showMessageDialog(getParentContainer(),"业务结果已返回，但界面更新失败，请重新查询并按请求号核对。请求号："
                + (receipt==null?"未知":receipt.getRequestId()),
                "物料页签删除结果",JOptionPane.WARNING_MESSAGE);
        } finally { clearAttempt(); }
    }

    @Override public OperationContext createContext(Action action,ActionEvent event) throws BusinessException {
        if (action!=this || event!=activeEvent || model().getService()!=service) throw new BusinessException("物料页签删除上下文不正确。");
        String node=model().getContext().getNodeCode(),group=model().getContext().getPk_group();
        String org="10140MAG".equals(node)?group:operationScope==null?null:operationScope.requireOperationOrganization();
        Object base=model().getBaseInfoVO();
        if (!(base instanceof MaterialVO) || group==null || org==null) throw new BusinessException("无法确定物料和操作组织。");
        MaterialVO material=(MaterialVO)base;
        if (!group.equals(material.getPk_group()) || material.getPrimaryKey()==null || material.getPk_org()==null) {
            throw new BusinessException("无法确定物料所属集团和组织。");
        }
        Map<String,SuperVO> rows=new LinkedHashMap<String,SuperVO>();List<BusinessObjectRef> refs=new ArrayList<BusinessObjectRef>();
        Set<String> keys=new HashSet<String>();
        for (Object selected:selection()) {
            SuperVO row=service.requireRow(selected);
            if (!group.equals(row.getAttributeValue("pk_group")) || !material.getPrimaryKey().equals(row.getAttributeValue("pk_material"))
                    || !keys.add(row.getPrimaryKey())) throw new BusinessException("物料页签选择包含其他物料或重复数据行。");
            String id=UUID.randomUUID().toString();rows.put(id,row);
            refs.add(new BusinessObjectRef(id,page.domain,row.getPrimaryKey(),material.getCode(),null));
        }
        if (refs.isEmpty()) throw new BusinessException("请选择待删除的物料页签数据行。");
        selectedRows=rows;
        return new OperationContext(null,null,group,org,node,"10140MAG".equals(node)?"物料-集团":"物料-业务单元",
            page.deleteButton,page.label.replace("保存","删除"),"DELETE",null,refs,OperationContext.OWNER_POLICY_PROTOCOL);
    }

    @Override public boolean isAvailable(OperationContext context) {
        if (context==null || !OperationContext.OWNER_POLICY_PROTOCOL.equals(context.getPolicyProtocol())
                || !"DELETE".equals(context.getOperationType()) || !page.deleteButton.equals(context.getButtonCode())
                || !("10140MAG".equals(context.getNodeCode()) || "10140MAO".equals(context.getNodeCode()))
                || context.getGroupId()==null || context.getOrgId()==null || context.getObjects().isEmpty()) return false;
        for (BusinessObjectRef ref:context.getObjects()) if (!page.domain.equals(ref.getObjectType()) || ref.getObjectPk()==null) return false;
        return true;
    }
    @Override public boolean bind(OperationContext context) {
        if (authorized!=null || !isAvailable(context) || context.getRequestId()==null || !sameSelection(context)) return false;
        authorized=context;return true;
    }
    @Override public boolean wasDispatched(String requestId) { return requestId!=null && requestId.equals(dispatched); }
    @Override public void release(String requestId) {
        if (authorized!=null && authorized.getRequestId().equals(requestId)) { authorized=null;dispatched=null; }
    }
    @Override public Container getParentContainer() { return model().getContext().getEntranceUI(); }
    @Override public String getCurrentUserDisplayName() { return InvocationInfoProxy.getInstance().getUserCode(); }

    static void validateReceipt(OperationContext context,MaterialPageDeleteResult receipt) throws BusinessException {
        if (context==null || context.getRequestId()==null || receipt==null || !context.getRequestId().equals(receipt.getRequestId())
                || receipt.getUnits()==null) throw new BusinessException("删除结果不完整，请按请求号核对。");
        Map<String,String> expected=new LinkedHashMap<String,String>();
        boolean partner=MaterialPageDeleteResult.isPartnerMain(context.getNodeCode(),context.getButtonCode(),context.getOperationType());
        for (BusinessObjectRef ref:context.getObjects()) if(!partner || ref.getParentClientObjectId()==null)
            expected.put(ref.getClientObjectId(),ref.getObjectPk());
        if(expected.isEmpty() || receipt.getUnits().size()!=expected.size()) throw new BusinessException("删除结果不完整，请按请求号核对。");
        Set<String> seen=new HashSet<String>();Map<String,String> states=new LinkedHashMap<String,String>();
        boolean success=false,failed=false,unknown=false;
        for (MaterialPageDeleteResult.Unit unit:receipt.getUnits()) {
            if (unit==null || !seen.add(unit.getClientObjectId()) || !expected.containsKey(unit.getClientObjectId())
                    || !expected.get(unit.getClientObjectId()).equals(unit.getObjectPk())) {
                throw new BusinessException("删除结果对象与授权范围不一致，请按请求号核对。");
            }
            if ("SUCCESS".equals(unit.getState())) success=true;
            else if ("FAILED".equals(unit.getState())) failed=true;
            else if ("SKIPPED".equals(unit.getState()) && MaterialPageDeleteResult.isWarehouse(
                    context.getNodeCode(),context.getButtonCode(),context.getOperationType())) failed=true;
            else if ("UNKNOWN".equals(unit.getState())) unknown=true;
            else throw new BusinessException("删除结果状态不完整，请按请求号核对。");
            states.put(unit.getClientObjectId(),unit.getState());
        }
        if(MaterialPageDeleteResult.isWarehouse(context.getNodeCode(),context.getButtonCode(),context.getOperationType())) {
            boolean stopped=false,prior=false;
            for(BusinessObjectRef ref:context.getObjects()) {
                String state=states.get(ref.getClientObjectId());
                if("SKIPPED".equals(state)) {
                    if(!prior) throw new BusinessException("仓库删除的未执行状态不正确，请按请求号核对。");
                    stopped=true;
                } else {
                    if(stopped) throw new BusinessException("仓库删除停止后存在其他执行结果，请按请求号核对。");
                    if("FAILED".equals(state) || "UNKNOWN".equals(state)) stopped=true;
                }
                prior=true;
            }
        }
        String calculated=unknown?"UNKNOWN":failed?(success?"PARTIAL":"FAILED"):"SUCCESS";
        if (!calculated.equals(receipt.getState())) throw new BusinessException("删除批次状态不一致，请按请求号核对。");
    }

    private boolean sameSelection(OperationContext context) {
        if (!isAvailable(context) || selectedRows==null || !context.getGroupId().equals(model().getContext().getPk_group())
                || !context.getNodeCode().equals(model().getContext().getNodeCode())) return false;
        String org="10140MAG".equals(context.getNodeCode())?context.getGroupId():null;
        if (org==null) try { org=operationScope==null?null:operationScope.requireOperationOrganization(); }
        catch (BusinessException error) { return false; }
        if (!context.getOrgId().equals(org)) return false;
        Object base=model().getBaseInfoVO();
        if (!(base instanceof MaterialVO) || ((MaterialVO)base).getPrimaryKey()==null
                || !context.getGroupId().equals(((MaterialVO)base).getPk_group())) return false;
        return sameRows(context,selectedRows,selection(),((MaterialVO)base).getPrimaryKey());
    }
    /** Recheck both the signed payload and the actual rows after the modal signature dialog. */
    static boolean sameRows(OperationContext context,Map<String,SuperVO> bound,Object[] selected,String materialPk) {
        if (context==null || bound==null || selected==null || materialPk==null
                || bound.size()!=context.getObjects().size() || selected.length!=bound.size()) return false;
        Set<String> before=new HashSet<String>(),after=new HashSet<String>(),ids=new HashSet<String>();
        for (BusinessObjectRef ref:context.getObjects()) {
            if (ref==null || ref.getClientObjectId()==null || ref.getObjectPk()==null
                    || !ids.add(ref.getClientObjectId()) || !before.add(ref.getObjectPk())) return false;
            SuperVO row=bound.get(ref.getClientObjectId());
            if (!matchesRow(row,ref.getObjectPk(),context.getGroupId(),materialPk)) return false;
        }
        for (Object raw:selected) {
            if (!(raw instanceof SuperVO)) return false;
            SuperVO row=(SuperVO)raw;
            if (!matchesRow(row,row.getPrimaryKey(),context.getGroupId(),materialPk)
                    || !after.add(row.getPrimaryKey())) return false;
        }
        return before.equals(after);
    }
    private static boolean matchesRow(SuperVO row,String pk,String group,String materialPk) {
        return row!=null && pk!=null && group!=null && pk.equals(row.getPrimaryKey())
            && group.equals(row.getAttributeValue("pk_group")) && materialPk.equals(row.getAttributeValue("pk_material"));
    }
    private Object[] selection() {
        Integer[] rows=model().getSelectedOperaRows();
        Object[] selected=rows!=null && rows.length>1 ? model().getSelectedOperaDatas() : new Object[]{model().getSelectedData()};
        return selected==null?new Object[0]:selected;
    }
    private OrgInfoBillManageModel model() { return (OrgInfoBillManageModel)getModel(); }
    private void clearAttempt() { activeEvent=null;workerStarted=false;authorized=null;dispatched=null;selectedRows=null;result=null; }
}
