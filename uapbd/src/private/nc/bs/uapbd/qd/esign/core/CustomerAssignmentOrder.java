package nc.bs.uapbd.qd.esign.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import nc.bs.framework.common.NCLocator;
import nc.bs.uapbd.qd.esign.audit.SnapshotSupport;
import nc.bs.uapbd.qd.esign.persistence.QdEsignStore;
import nc.itf.bd.cust.baseinfo.ICustBaseInfoQueryService;
import nc.vo.pub.BusinessException;

/** Candidate customer dependency evidence for independently committed assignment units. */
public final class CustomerAssignmentOrder {
    private CustomerAssignmentOrder() { }
    public interface Reader {
        /** Return the native superior customers after verifying that they belong to the same group. */
        List<String> superiorCustomers(String group,String customer) throws BusinessException;
    }
    public static final class Selection {
        private final List<String> order;private final String fingerprint;
        private Selection(List<String> order,String fingerprint) {
            this.order=Collections.unmodifiableList(order);this.fingerprint=fingerprint;
        }
        public List<String> getOrder() { return order; }
        public String getFingerprint() { return fingerprint; }
    }
    /** Assign ancestors first; for cancellation, process selected descendants first. */
    public static Selection select(String group,String operation,List<String> customers,Reader reader) throws BusinessException {
        EsignValues.require(validPk(group) && ("ASSIGN".equals(operation) || "CANCEL_ASSIGN".equals(operation))
            && customers!=null && !customers.isEmpty() && customers.size()<=1000 && reader!=null,
            "客户分配缺少有效的档案依赖范围。");
        Set<String> selected=new TreeSet<String>();
        for(String customer:customers) EsignValues.require(validPk(customer) && selected.add(customer),"客户分配档案重复或无效。");
        Map<String,Set<String>> ancestors=new TreeMap<String,Set<String>>();
        StringBuilder evidence=new StringBuilder(pack("customer-assignment-hierarchy-v1",group,operation));
        for(String customer:selected) {
            List<String> queried=reader.superiorCustomers(group,customer);
            EsignValues.require(queried!=null && queried.size()<=1000,"无法核实客户的上级关系。");
            Set<String> parents=new TreeSet<String>();
            for(String parent:queried) EsignValues.require(validPk(parent) && !customer.equals(parent) && parents.add(parent),
                "客户上级关系包含重复或无效档案。");
            ancestors.put(customer,parents);evidence.append(pack(customer,Integer.toString(parents.size())));
            for(String parent:parents) evidence.append(pack(parent));
        }
        // Resolve only dependencies among selected customers; unselected superior evidence is still signed.
        Map<String,Set<String>> outgoing=new HashMap<String,Set<String>>();Map<String,Integer> indegree=new HashMap<String,Integer>();
        for(String id:selected) { outgoing.put(id,new HashSet<String>());indegree.put(id,0); }
        for(String child:selected) for(String parent:ancestors.get(child)) if(selected.contains(parent)) {
            String from="ASSIGN".equals(operation)?parent:child,to="ASSIGN".equals(operation)?child:parent;
            if(outgoing.get(from).add(to)) indegree.put(to,indegree.get(to)+1);
        }
        TreeSet<String> ready=new TreeSet<String>();List<String> order=new ArrayList<String>();
        for(String id:selected) if(indegree.get(id)==0) ready.add(id);
        while(!ready.isEmpty()) {
            String current=ready.pollFirst();order.add(current);
            for(String next:outgoing.get(current)) { int degree=indegree.get(next)-1;indegree.put(next,degree);if(degree==0) ready.add(next); }
        }
        EsignValues.require(order.size()==selected.size(),"客户上级关系存在循环，无法确定分配顺序。");
        return new Selection(order,SnapshotSupport.sha256(evidence.toString()));
    }
    public static Reader database() {
        return new Reader() {
            private final QdEsignStore store=new QdEsignStore();
            public List<String> superiorCustomers(String group,String customer) throws BusinessException {
                ICustBaseInfoQueryService service=NCLocator.getInstance().lookup(ICustBaseInfoQueryService.class);
                EsignValues.require(service!=null,"无法获取原生客户上级查询服务。");
                String[] values=service.querySuperiorCustomerPks(customer);List<String> result=new ArrayList<String>();
                if(values!=null) for(String parent:values) {
                    Map<String,Object> row=store.loadBaseArchiveOwner(group,"BD_CUSTOMER",parent,false);
                    EsignValues.require(row!=null && group.equals(EsignValues.text(row,"pk_group")),"客户上级档案不存在或跨集团。");
                    result.add(parent);
                }
                return result;
            }
        };
    }
    private static boolean validPk(String value) { return value!=null && value.matches("[A-Za-z0-9_-]{1,20}"); }
    private static String pack(String... values) {
        StringBuilder result=new StringBuilder();
        for(String value:values) result.append(value==null?-1:value.length()).append(':').append(value==null?"":value);
        return result.toString();
    }
}
