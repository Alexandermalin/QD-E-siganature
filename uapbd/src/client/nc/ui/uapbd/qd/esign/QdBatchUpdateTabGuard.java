package nc.ui.uapbd.qd.esign;

import java.util.Map;
import nc.bs.bd.batchupdate.IBatchUpdateService;
import nc.bs.uif2.validation.Validator;
import nc.ui.bd.pub.batchupdate.config.BatchUpdateContext;
import nc.ui.bd.pub.batchupdate.config.IBatchUpdateEditListener;
import nc.ui.bd.pub.batchupdate.config.IBatchUpdateTab;
import nc.ui.bd.ref.AbstractRefModel;
import nc.vo.bd.errorlog.ErrLogReturnValue;
import nc.vo.pub.BusinessException;

/** Candidate tab wrapper for the native fast-batch dialog; it does not register itself on a node. */
final class QdBatchUpdateTabGuard implements IBatchUpdateTab {
    interface Dispatcher {
        ErrLogReturnValue execute(BatchUpdateContext context,IBatchUpdateTab original,String selectedAttribute,
            String[] fields,Map<String,Object> values,String[] permissionOrgs,String[] targetOrgs,String[] selectedPks,boolean needReturn)
            throws BusinessException;
    }
    private final BatchUpdateContext context;private final IBatchUpdateTab nativeTab;private final Dispatcher dispatcher;
    QdBatchUpdateTabGuard(BatchUpdateContext context,IBatchUpdateTab nativeTab,Dispatcher dispatcher) {
        if(context==null || nativeTab==null) throw new IllegalArgumentException("Native batch tab and context are required");
        this.context=context;this.nativeTab=nativeTab;this.dispatcher=dispatcher;
    }
    public String getID() { return nativeTab.getID(); }
    public String getCode() { return nativeTab.getCode(); }
    public String getName() { return nativeTab.getName(); }
    public boolean isMainTab() { return nativeTab.isMainTab(); }
    public AbstractRefModel getOrgRefModel(BatchUpdateContext value) { return nativeTab.getOrgRefModel(value); }
    public String getTempletNodeKey() { return nativeTab.getTempletNodeKey(); }
    public String[] getAttrs() { return nativeTab.getAttrs(); }
    public String[] getOtherAttrsByAttr(String value) { return nativeTab.getOtherAttrsByAttr(value); }
    public String[] getSingleOrgAttrs() { return nativeTab.getSingleOrgAttrs(); }
    public IBatchUpdateEditListener getListenerByAttr(String value) { return nativeTab.getListenerByAttr(value); }
    public Validator getValidatorByAttr(String value) { return nativeTab.getValidatorByAttr(value); }
    public IBatchUpdateService getBatchUpdateService(final String selectedAttribute) {
        final IBatchUpdateService service=nativeTab.getBatchUpdateService(selectedAttribute);
        return new IBatchUpdateService() {
            public ErrLogReturnValue batchUpdateByPks(String[] fields,Map<String,Object> values,String[] permissionOrgs,
                    String[] targetOrgs,String[] selectedPks,boolean needReturn) throws BusinessException {
                if(!context.isFastBatchUpdate()) return requireNative(service).batchUpdateByPks(fields,values,permissionOrgs,targetOrgs,selectedPks,needReturn);
                if(dispatcher==null || fields==null || fields.length==0 || values==null || selectedPks==null || selectedPks.length==0 || !needReturn)
                    throw new BusinessException("快速批改受控入口或所选数据不完整，请联系管理员。");
                return dispatcher.execute(context,nativeTab,selectedAttribute,fields,values,permissionOrgs,targetOrgs,selectedPks,needReturn);
            }
            public ErrLogReturnValue batchUpdateByCondition(String[] fields,Map<String,Object> values,String[] permissionOrgs,
                    String[] targetOrgs,String condition) throws BusinessException {
                if(context.isFastBatchUpdate()) throw new BusinessException("快速批改必须明确选择档案，不能直接按条件执行。");
                return requireNative(service).batchUpdateByCondition(fields,values,permissionOrgs,targetOrgs,condition);
            }
        };
    }
    private static IBatchUpdateService requireNative(IBatchUpdateService service) throws BusinessException {
        if(service==null) throw new BusinessException("原生批改页签服务不可用。");return service;
    }
}
