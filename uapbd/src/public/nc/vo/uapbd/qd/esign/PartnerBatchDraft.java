package nc.vo.uapbd.qd.esign;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import nc.vo.pub.lang.UFBoolean;
import nc.vo.pub.lang.UFDate;
import nc.vo.pub.lang.UFDateTime;
import nc.vo.pub.lang.UFDouble;

/** Immutable candidate draft for one native archive fast-batch submission. */
public final class PartnerBatchDraft implements Serializable {
    private static final long serialVersionUID=1L;
    /** NC's batch service starts paging at 200,000 records. */
    public static final int MAX_SINGLE_TRANSACTION_OBJECTS=199999;
    /** Organization tabs can have multiple physical rows per selected archive. */
    public static final int MAX_SINGLE_TRANSACTION_PAGE_ROWS=199999;
    private final String tabId,tabCode,selectedAttribute;private final String[] selected,targetOrgs;
    private final PartnerOrgBatchPlan.Page[] selectedPages;private final Map<String,Object> values;
    public PartnerBatchDraft(String tabId,String tabCode,String selectedAttribute,String[] selected,Map<String,Object> values) {
        this(tabId,tabCode,selectedAttribute,selected,values,null);
    }
    public PartnerBatchDraft(String tabId,String tabCode,String selectedAttribute,String[] selected,Map<String,Object> values,String[] targetOrgs) {
        this(tabId,tabCode,selectedAttribute,selected,values,targetOrgs,null);
    }
    /** A non-null page list fixes the exact physical rows selected by an organization-batch plan. */
    public PartnerBatchDraft(String tabId,String tabCode,String selectedAttribute,String[] selected,Map<String,Object> values,
            String[] targetOrgs,PartnerOrgBatchPlan.Page[] selectedPages) {
        if(selected==null || values==null) throw new IllegalArgumentException("Batch selection and values are required");
        this.tabId=tabId;this.tabCode=tabCode;this.selectedAttribute=selectedAttribute;this.selected=selected.clone();
        this.targetOrgs=targetOrgs==null?new String[0]:targetOrgs.clone();Set<String> unique=new HashSet<String>();
        for(String target:this.targetOrgs) if(target==null || !target.matches("[A-Za-z0-9_-]{1,20}") || !unique.add(target))
            throw new IllegalArgumentException("Invalid or duplicate batch target organization");
        if(selectedPages!=null && (this.targetOrgs.length==0 || selectedPages.length>MAX_SINGLE_TRANSACTION_PAGE_ROWS))
            throw new IllegalArgumentException("Invalid organization page selection");
        this.selectedPages=selectedPages==null?null:selectedPages.clone();Set<String> targets=new HashSet<String>(unique);unique.clear();
        if(this.selectedPages!=null)for(PartnerOrgBatchPlan.Page page:this.selectedPages)
            if(page==null || !validPk(page.getId()) || !validPk(page.getRoot()) || !validPk(page.getTarget())
                    || !targets.contains(page.getTarget()) || !unique.add(page.getId()))
                throw new IllegalArgumentException("Invalid or duplicate organization page identity");
        this.values=Collections.unmodifiableMap(copy(values));
    }
    public String getTabId() { return tabId; }
    public String getTabCode() { return tabCode; }
    public String getSelectedAttribute() { return selectedAttribute; }
    public String[] getSelected() { return selected.clone(); }
    /** Older serialized main-tab drafts have no targetOrgs field and remain main-tab drafts. */
    public String[] getTargetOrgs() { return targetOrgs==null?new String[0]:targetOrgs.clone(); }
    /** Null selection means all matching pages; an empty explicit selection is a planned no-op. */
    public boolean hasPageSelection() { return selectedPages!=null; }
    public PartnerOrgBatchPlan.Page[] getSelectedPages() { return selectedPages==null?null:selectedPages.clone(); }
    public String[] getSelectedPageIds() {
        if(selectedPages==null)return null;String[] result=new String[selectedPages.length];
        for(int i=0;i<result.length;i++)result[i]=selectedPages[i].getId();return result;
    }
    public Map<String,Object> getValues() { return copy(values); }
    private static Map<String,Object> copy(Map<String,Object> source) {
        Map<String,Object> result=new LinkedHashMap<String,Object>();
        for(Map.Entry<String,Object> item:source.entrySet()) {
            if(item.getKey()==null) throw new IllegalArgumentException("Batch field name is required");
            result.put(item.getKey(),copyValue(item.getValue()));
        }
        return result;
    }
    private static Object copyValue(Object value) {
        if(value==null) return null;Class<?> type=value.getClass();
        if(type==Date.class || type==java.sql.Date.class || type==java.sql.Time.class || type==java.sql.Timestamp.class)
            return ((Date)value).clone();
        if(type==UFDate.class) return ((UFDate)value).clone();
        if(type==UFDateTime.class) return ((UFDateTime)value).clone();
        if(type==UFDouble.class) return ((UFDouble)value).clone();
        if(type==String.class || type==Boolean.class || type==Byte.class || type==Short.class || type==Integer.class
            || type==Long.class || type==Float.class || type==Double.class || type==BigDecimal.class || type==BigInteger.class
            || type==UFBoolean.class) return value;
        throw new IllegalArgumentException("Unsupported batch value type");
    }
    private static boolean validPk(String value) { return value!=null && value.matches("[A-Za-z0-9_-]{1,20}"); }
}
