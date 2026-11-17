package nc.ui.uapbd.qd.esign;

import java.util.ArrayList;
import java.util.List;
import nc.ui.bd.pub.actions.BDBatchUpdateWizardAction;
import nc.ui.bd.pub.batchupdate.config.BatchUpdateContext;
import nc.ui.bd.pub.batchupdate.config.IBatchUpdateTab;

/** Candidate fast-dialog hook. The original wizard remains unchanged until explicitly enabled by a node config. */
public final class QdPartnerBatchWizardAction extends BDBatchUpdateWizardAction {
    private static final long serialVersionUID=1L;private boolean candidateEnabled;private PartnerOperationOrganization operationScope;
    public void setCandidateEnabled(boolean enabled) { candidateEnabled=enabled; }
    public void setOperationScope(PartnerOperationOrganization value) { operationScope=value; }
    @Override protected BatchUpdateContext getBatchUpdateContext() {
        BatchUpdateContext context=super.getBatchUpdateContext();
        if(!candidateEnabled || context==null || context.getTabs()==null) return context;
        QdBatchUpdateTabGuard.Dispatcher dispatcher=new QdPartnerBatchDispatcher(getModel(),operationScope);
        List<IBatchUpdateTab> tabs=new ArrayList<IBatchUpdateTab>();
        for(IBatchUpdateTab tab:context.getTabs()) tabs.add(new QdBatchUpdateTabGuard(context,tab,dispatcher));
        context.setTabs(tabs);return context;
    }
}
