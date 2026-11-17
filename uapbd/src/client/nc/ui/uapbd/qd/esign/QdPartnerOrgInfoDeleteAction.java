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
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.MaterialPageDeleteCommand;
import nc.vo.uapbd.qd.esign.MaterialPageDeleteResult;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.PartnerOrgPage;

/** The native asynchronous page delete uses a separate guarded transaction for every selected row. */
public final class QdPartnerOrgInfoDeleteAction extends DeleteAction implements OperationContextProvider,AuthorizedRequestConsumer {
    private static final long serialVersionUID=1L;
    private final QdPartnerOrgInfoModelService service;
    private final PartnerOrgPage page;
    private PartnerOperationOrganization operationScope;
    private volatile ActionEvent activeEvent;
    private volatile boolean workerStarted;
    private volatile OperationContext authorized;
    private volatile String dispatched;
    private volatile Map<String,SuperVO> selectedRows;
    private volatile MaterialPageDeleteResult result;

    public QdPartnerOrgInfoDeleteAction(QdPartnerOrgInfoModelService service,String button) {
        this.service=service;this.page=service==null?null:service.page();
        if(page==null || !page.deleteButton.equals(button)) throw new IllegalArgumentException("Partner page delete configuration mismatch");
    }
    public void setOperationScope(PartnerOperationOrganization value) { operationScope=value; }
    @Override public void actionPerformed(ActionEvent event) {
        if(activeEvent!=null) return;activeEvent=event;workerStarted=false;
        try { super.actionPerformed(event); } finally { if(!workerStarted) clearAttempt(); }
    }
    @Override public boolean beforeStartDoAction(final ActionEvent event) throws Exception {
        if(event!=activeEvent || workerStarted || model().getService()!=service) throw new BusinessException("往来档案页签删除接入状态已变化。");
        if(!super.beforeStartDoAction(event)) return false;
        final ActionInterceptor original=getInterceptor();
        ElectronicSignatureActionInterceptor signing=new ElectronicSignatureActionInterceptor() {
            @Override public void afterDoActionSuccessed(Action action,ActionEvent finished) {
                try { super.afterDoActionSuccessed(action,finished);if(original!=null) original.afterDoActionSuccessed(action,finished); }
                catch(RuntimeException error) { QdPartnerOrgInfoDeleteAction.super.doAfterFailure(finished,error);clearAttempt();throw error; }
                catch(Error error) { QdPartnerOrgInfoDeleteAction.super.doAfterFailure(finished,error);clearAttempt();throw error; }
                finally { setInterceptor(original); }
            }
            @Override public boolean afterDoActionFailed(Action action,ActionEvent finished,Throwable error) {
                try { boolean handled=super.afterDoActionFailed(action,finished,error);
                    return original==null?handled:original.afterDoActionFailed(action,finished,error) && handled; }
                finally { setInterceptor(original);clearAttempt(); }
            }
        };
        signing.setContextProvider(this);signing.setRequestConsumer(this);
        CompositeActionInterceptor chain=new CompositeActionInterceptor();chain.addInterceptor(signing);
        try {
            setInterceptor(chain);SignatureLifecycleInterceptor outer=SignatureLifecycleInstaller.install(this,signing);
            if(!outer.beforeDoAction(this,event)) {
                super.doAfterFailure(event,new BusinessException("往来档案页签删除已取消。"));setInterceptor(original);return false;
            }
            if(authorized==null || !sameSelection(authorized)) throw new BusinessException("往来档案页签选择已变化，请重新删除。");
            workerStarted=true;return true;
        } catch(Exception error) {
            signing.finishLifecycle(signing.lifecycleGeneration(),this,event,error);setInterceptor(original);throw error;
        } catch(Error error) {
            signing.finishLifecycle(signing.lifecycleGeneration(),this,event,error);setInterceptor(original);throw error;
        }
    }
    @Override public void doAction(ActionEvent event) throws Exception {
        OperationContext request=authorized;Map<String,SuperVO> rows=selectedRows;
        if(event!=activeEvent || !workerStarted || request==null || rows==null || dispatched!=null) throw new BusinessException("往来档案页签删除缺少一次性授权。");
        dispatched=request.getRequestId();
        MaterialPageDeleteResult receipt=NCLocator.getInstance().lookup(IMaterialPageDeleteService.class)
            .execute(request.getRequestId(),new MaterialPageDeleteCommand(rows));
        QdMaterialOrgInfoDeleteAction.validateReceipt(request,receipt);result=receipt;
    }
    @Override public void doAfterSuccess(ActionEvent event) {
        MaterialPageDeleteResult receipt=result;Map<String,SuperVO> rows=selectedRows;
        try {
            if(receipt==null || rows==null) throw new BusinessException("无法确认删除结果，请重新查询。");
            List<SuperVO> removed=new ArrayList<SuperVO>();int succeeded=0,failed=0,unknown=0;
            for(MaterialPageDeleteResult.Unit unit:receipt.getUnits()) {
                if("SUCCESS".equals(unit.getState())) { succeeded++;SuperVO row=rows.get(unit.getClientObjectId());
                    if(row!=null && model().findBusinessData(row)>=0) removed.add(row);
                } else if("FAILED".equals(unit.getState())) failed++;else unknown++;
            }
            if(!removed.isEmpty()) model().directlyDelete(removed.toArray(new Object[removed.size()]));
            if("SUCCESS".equals(receipt.getState())) super.doAfterSuccess(event);
            else {
                super.doAfterFailure(event,new BusinessException("档案页签删除未全部成功。"));
                JOptionPane.showMessageDialog(getParentContainer(),"删除成功 "+succeeded+" 条，失败 "+failed+" 条，待核对 "+unknown
                    +" 条。请求号："+receipt.getRequestId(),"档案页签删除结果",JOptionPane.WARNING_MESSAGE);
            }
        } catch(Exception error) {
            super.doAfterFailure(event,error);
            JOptionPane.showMessageDialog(getParentContainer(),"业务结果已返回，但界面更新失败，请重新查询并按请求号核对。请求号："
                +(receipt==null?"未知":receipt.getRequestId()),"档案页签删除结果",JOptionPane.WARNING_MESSAGE);
        } finally { clearAttempt(); }
    }
    public OperationContext createContext(Action action,ActionEvent event) throws BusinessException {
        if(action!=this || event!=activeEvent || model().getService()!=service) throw new BusinessException("档案页签删除上下文无效。");
        String node=model().getContext().getNodeCode(),group=model().getContext().getPk_group();
        String org=page.groupNode.equals(node)?group:operationScope==null?null:operationScope.requireOperationOrganization();
        Object raw=model().getBaseInfoVO();
        if(group==null || org==null || !(raw instanceof SuperVO) || PartnerOrgPage.forAction(node,page.deleteButton,"DELETE")!=page)
            throw new BusinessException("无法确定档案页签的节点或操作组织。");
        SuperVO root=(SuperVO)raw;
        if(!page.rootTable.equals(root.getTableName()) || root.getPrimaryKey()==null || root.getAttributeValue("pk_org")==null
                || !group.equals(root.getAttributeValue("pk_group"))) throw new BusinessException("无法确定往来档案及所属组织。");
        Object code=root.getAttributeValue("code");
        Map<String,SuperVO> rows=new LinkedHashMap<String,SuperVO>();List<BusinessObjectRef> refs=new ArrayList<BusinessObjectRef>();
        Set<String> keys=new HashSet<String>();
        for(Object selected:selection()) {
            SuperVO row=service.requireRow(selected);
            if(!group.equals(row.getAttributeValue("pk_group")) || !root.getPrimaryKey().equals(row.getAttributeValue(page.rootKey))
                    || !keys.add(row.getPrimaryKey())) throw new BusinessException("选择包含其他档案或重复的数据行。");
            String id=UUID.randomUUID().toString();rows.put(id,row);
            refs.add(new BusinessObjectRef(id,page.domain,row.getPrimaryKey(),code instanceof String?(String)code:null,null));
        }
        if(refs.isEmpty()) throw new BusinessException("请选择待删除的档案页签数据行。");
        selectedRows=rows;
        return new OperationContext(null,null,group,org,node,page.rootTable.equals("bd_customer")?"客户":"供应商",
            page.deleteButton,"组织信息删除","DELETE",null,refs,OperationContext.OWNER_POLICY_PROTOCOL);
    }
    public boolean isAvailable(OperationContext context) {
        if(context==null || !OperationContext.OWNER_POLICY_PROTOCOL.equals(context.getPolicyProtocol()) || !"DELETE".equals(context.getOperationType())
                || PartnerOrgPage.forAction(context.getNodeCode(),context.getButtonCode(),"DELETE")!=page
                || context.getGroupId()==null || context.getOrgId()==null || context.getObjects().isEmpty()) return false;
        for(BusinessObjectRef ref:context.getObjects()) if(ref==null || !page.domain.equals(ref.getObjectType()) || ref.getObjectPk()==null) return false;
        return true;
    }
    public boolean bind(OperationContext context) {
        if(authorized!=null || !isAvailable(context) || context.getRequestId()==null || !sameSelection(context)) return false;
        authorized=context;return true;
    }
    public boolean wasDispatched(String id) { return id!=null && id.equals(dispatched); }
    public void release(String id) { if(authorized!=null && authorized.getRequestId().equals(id)) { authorized=null;dispatched=null; } }
    public Container getParentContainer() { return model().getContext().getEntranceUI(); }
    public String getCurrentUserDisplayName() { return InvocationInfoProxy.getInstance().getUserCode(); }
    private boolean sameSelection(OperationContext context) {
        if(!isAvailable(context) || selectedRows==null || !context.getGroupId().equals(model().getContext().getPk_group())
                || !context.getNodeCode().equals(model().getContext().getNodeCode())) return false;
        String org=page.groupNode.equals(context.getNodeCode())?context.getGroupId():null;
        if(org==null) try { org=operationScope==null?null:operationScope.requireOperationOrganization(); } catch(BusinessException error) { return false; }
        Object raw=model().getBaseInfoVO();
        if(!context.getOrgId().equals(org) || !(raw instanceof SuperVO) || !page.rootTable.equals(((SuperVO)raw).getTableName())
                || ((SuperVO)raw).getPrimaryKey()==null || !context.getGroupId().equals(((SuperVO)raw).getAttributeValue("pk_group"))) return false;
        return sameRows(context,selectedRows,selection(),((SuperVO)raw).getPrimaryKey(),page);
    }
    static boolean sameRows(OperationContext context,Map<String,SuperVO> bound,Object[] selected,String root,PartnerOrgPage page) {
        if(context==null || bound==null || selected==null || root==null || page==null
                || bound.size()!=context.getObjects().size() || selected.length!=bound.size()) return false;
        Set<String> before=new HashSet<String>(),after=new HashSet<String>(),ids=new HashSet<String>();
        for(BusinessObjectRef ref:context.getObjects()) {
            if(ref==null || ref.getClientObjectId()==null || ref.getObjectPk()==null
                    || !ids.add(ref.getClientObjectId()) || !before.add(ref.getObjectPk())) return false;
            SuperVO row=bound.get(ref.getClientObjectId());
            if(!matches(row,ref.getObjectPk(),context.getGroupId(),root,page)) return false;
        }
        for(Object raw:selected) {
            if(!(raw instanceof SuperVO)) return false;SuperVO row=(SuperVO)raw;
            if(!matches(row,row.getPrimaryKey(),context.getGroupId(),root,page) || !after.add(row.getPrimaryKey())) return false;
        }
        return before.equals(after);
    }
    private static boolean matches(SuperVO row,String pk,String group,String root,PartnerOrgPage page) {
        return row!=null && page.type.isInstance(row) && pk!=null && group!=null && pk.equals(row.getPrimaryKey())
            && group.equals(row.getAttributeValue("pk_group")) && root.equals(row.getAttributeValue(page.rootKey));
    }
    private Object[] selection() {
        Integer[] rows=model().getSelectedOperaRows();
        Object[] selected=rows!=null && rows.length>1?model().getSelectedOperaDatas():new Object[]{model().getSelectedData()};
        return selected==null?new Object[0]:selected;
    }
    private OrgInfoBillManageModel model() { return (OrgInfoBillManageModel)getModel(); }
    private void clearAttempt() { activeEvent=null;workerStarted=false;authorized=null;dispatched=null;selectedRows=null;result=null; }
}
