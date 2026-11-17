package nc.vo.uapbd.qd.esign;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Durable batch status, usable after a lost response and after archival. */
public final class ArchiveBatchStatus implements Serializable {
    private static final long serialVersionUID=1L;
    private final String requestId,state;
    private final List<Unit> units;
    private final Organization organization;
    public ArchiveBatchStatus(String requestId,String state,List<Unit> units) {
        this(requestId,state,units,null);
    }
    public ArchiveBatchStatus(String requestId,String state,List<Unit> units,Organization organization) {
        this.requestId=requestId;this.state=state;this.units=Collections.unmodifiableList(new ArrayList<Unit>(units));
        this.organization=organization;
    }
    public String getRequestId() { return requestId; }
    public String getState() { return state; }
    public List<Unit> getUnits() { return units; }
    /** Null for a main-tab batch or a response from an older server. */
    public Organization getOrganization() { return organization; }
    public static final class Organization implements Serializable {
        private static final long serialVersionUID=1L;
        private final String tabCode;private final List<String> targets;private final boolean resultAvailable;
        private final List<PageHeader> headers;private final List<Page> pages;
        private final List<PartnerOrgBatchPlan.Page> preparedPages;
        public Organization(String tabCode,List<String> targets,boolean resultAvailable,List<PageHeader> headers,List<Page> pages) {
            this(tabCode,targets,resultAvailable,headers,pages,null);
        }
        public Organization(String tabCode,List<String> targets,boolean resultAvailable,List<PageHeader> headers,List<Page> pages,
                List<PartnerOrgBatchPlan.Page> preparedPages) {
            if(tabCode==null || targets==null || headers==null || pages==null)throw new IllegalArgumentException("Organization batch details are required");
            this.tabCode=tabCode;this.targets=Collections.unmodifiableList(new ArrayList<String>(targets));
            this.resultAvailable=resultAvailable;this.headers=Collections.unmodifiableList(new ArrayList<PageHeader>(headers));
            this.pages=Collections.unmodifiableList(new ArrayList<Page>(pages));
            this.preparedPages=preparedPages==null?null:Collections.unmodifiableList(new ArrayList<PartnerOrgBatchPlan.Page>(preparedPages));
        }
        public String getTabCode() { return tabCode; }
        public List<String> getTargets() { return targets; }
        public boolean isResultAvailable() { return resultAvailable; }
        public List<PageHeader> getHeaders() { return headers; }
        public List<Page> getPages() { return pages; }
        /** Older requests may retain only target evidence and cannot reconstruct the prepared page set. */
        public boolean hasPreparedSelection() { return preparedPages!=null; }
        public List<PartnerOrgBatchPlan.Page> getPreparedPages() {
            return preparedPages==null?Collections.<PartnerOrgBatchPlan.Page>emptyList():preparedPages;
        }
    }
    public static final class PageHeader implements Serializable {
        private static final long serialVersionUID=1L;
        private final String clientId;private final int pageCount;
        public PageHeader(String clientId,int pageCount) { this.clientId=clientId;this.pageCount=pageCount; }
        public String getClientId() { return clientId; }
        public int getPageCount() { return pageCount; }
    }
    public static final class Page implements Serializable {
        private static final long serialVersionUID=1L;
        private final String clientId,pagePk,targetOrg,state,failureStage;
        public Page(String clientId,String pagePk,String targetOrg,String state,String failureStage) {
            this.clientId=clientId;this.pagePk=pagePk;this.targetOrg=targetOrg;this.state=state;this.failureStage=failureStage;
        }
        public String getClientId() { return clientId; }
        public String getPagePk() { return pagePk; }
        public String getTargetOrg() { return targetOrg; }
        public String getState() { return state; }
        public String getFailureStage() { return failureStage; }
    }
    public static final class Unit implements Serializable {
        private static final long serialVersionUID=1L;
        private final String clientId,objectPk,code,ownerOrg,targetOrg,state,errorCode;
        public Unit(String clientId,String objectPk,String code,String ownerOrg,String targetOrg,String state,String errorCode) {
            this.clientId=clientId;this.objectPk=objectPk;this.code=code;this.ownerOrg=ownerOrg;
            this.targetOrg=targetOrg;this.state=state;this.errorCode=errorCode;
        }
        public String getClientId() { return clientId; }
        public String getObjectPk() { return objectPk; }
        public String getCode() { return code; }
        public String getOwnerOrg() { return ownerOrg; }
        public String getTargetOrg() { return targetOrg; }
        public String getState() { return state; }
        public String getErrorCode() { return errorCode; }
    }
}
