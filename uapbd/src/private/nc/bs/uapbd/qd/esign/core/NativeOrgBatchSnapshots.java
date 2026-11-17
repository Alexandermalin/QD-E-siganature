package nc.bs.uapbd.qd.esign.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import nc.bs.uapbd.qd.esign.audit.NativeDeleteMetadataGraph;
import nc.bs.uapbd.qd.esign.audit.SnapshotSupport;
import nc.md.MDBaseQueryFacade;
import nc.md.model.IBean;
import nc.md.model.ITable;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;

/** Physical page and metadata-linked row evidence for a previously authorized organization-tab selection. */
public final class NativeOrgBatchSnapshots {
    private NativeOrgBatchSnapshots() { }

    public static final class Capture {
        private final NativeOrgBatchSelection.Impact impact;
        private final Map<String,NativeDeleteMetadataGraph.Capture> graphs;
        private final Map<String,List<SnapshotRow>> pages;
        private final String fingerprint;
        private final Capture previous;
        private Capture(NativeOrgBatchSelection.Impact impact,Map<String,NativeDeleteMetadataGraph.Capture> graphs,
                Map<String,List<SnapshotRow>> pages,Capture previous) {
            this.impact=impact;
            this.previous=previous;
            this.graphs=Collections.unmodifiableMap(new LinkedHashMap<String,NativeDeleteMetadataGraph.Capture>(graphs));
            this.pages=Collections.unmodifiableMap(new LinkedHashMap<String,List<SnapshotRow>>(pages));
            StringBuilder evidence=new StringBuilder(pack("org-batch-snapshot-v1",impact.getFingerprint()));
            for(NativeOrgBatchSelection.Row row:impact.getRows()) {
                NativeDeleteMetadataGraph.Capture graph=graphs.get(row.getId());
                List<String> facts=new ArrayList<String>();
                for(SnapshotRow item:graph.getRows()) facts.add(SnapshotSupport.rowFingerprint(item));
                Collections.sort(facts);
                evidence.append(pack(row.getId(),Integer.toString(graph.metadataEvidence().size())));
                for(String fact:graph.metadataEvidence()) evidence.append(pack(fact));
                evidence.append(pack(Integer.toString(facts.size())));
                for(String fact:facts) evidence.append(pack(fact));
            }
            this.fingerprint=SnapshotSupport.sha256(evidence.toString());
        }
        /** Page identifiers are synthetic reconciliation IDs, never client-supplied archive identities. */
        public List<BusinessObjectRef> pageObjects() {
            List<BusinessObjectRef> result=new ArrayList<BusinessObjectRef>();
            for(NativeOrgBatchSelection.Row row:impact.getRows())
                result.add(new BusinessObjectRef(row.getId(),impact.getPage().domain,row.getId(),null,null));
            return Collections.unmodifiableList(result);
        }
        public Map<String,List<SnapshotRow>> pages() { return pages; }
        public String getFingerprint() { return fingerprint; }
        boolean matches(NativeOrgBatchSelection.Impact expected) {
            return expected!=null && impact.getFingerprint().equals(expected.getFingerprint());
        }
        boolean follows(Capture expected) { return expected!=null && previous==expected; }
        void requireMetadata(NativeDeleteMetadataGraph.Capture expected) throws BusinessException {
            EsignValues.require(expected!=null,"组织页签批改缺少已锁定的元数据范围。");
            for(NativeDeleteMetadataGraph.Capture graph:graphs.values())
                EsignValues.require(graph.metadataEvidence().equals(expected.metadataEvidence())
                    && graph.tableNames().equals(expected.tableNames()),"组织页签关联元数据在锁定后发生变化。");
        }
        /** Group page and child evidence under the originally authorized archive identity. */
        public Map<String,List<SnapshotRow>> byArchive(String group,String domain,List<BusinessObjectRef> roots)
                throws BusinessException {
            EsignValues.require(impact.matches(group,domain,roots),"组织页签审计对象与授权范围不一致。");
            Map<String,String> clients=new HashMap<String,String>();
            Map<String,List<SnapshotRow>> mutable=new LinkedHashMap<String,List<SnapshotRow>>();
            for(BusinessObjectRef ref:roots) {
                clients.put(ref.getObjectPk(),ref.getClientObjectId());
                mutable.put(ref.getClientObjectId(),new ArrayList<SnapshotRow>());
            }
            for(NativeOrgBatchSelection.Row row:impact.getRows()) {
                String client=clients.get(row.getRoot());
                EsignValues.require(client!=null && mutable.containsKey(client),"组织页签审计缺少所属档案。");
                mutable.get(client).addAll(pages.get(row.getId()));
            }
            Map<String,List<SnapshotRow>> result=new LinkedHashMap<String,List<SnapshotRow>>();
            for(Map.Entry<String,List<SnapshotRow>> entry:mutable.entrySet())
                result.put(entry.getKey(),Collections.unmodifiableList(entry.getValue()));
            return Collections.unmodifiableMap(result);
        }
    }

    public static Capture capture(NativeOrgBatchSelection.Impact impact,boolean lock) throws BusinessException {
        EsignValues.require(impact!=null,"组织页签审计缺少授权范围。");
        return capture(impact,MDBaseQueryFacade.getInstance().getBeanByID(impact.getPage().metadataId),
            NativeDeleteMetadataGraph.database(),lock);
    }
    public static Capture recapture(Capture prior,boolean lock) throws BusinessException {
        EsignValues.require(prior!=null,"组织页签审计缺少执行前快照。");
        return recapture(prior,MDBaseQueryFacade.getInstance().getBeanByID(prior.impact.getPage().metadataId),
            NativeDeleteMetadataGraph.database(),lock);
    }

    /** Resolve every metadata-linked table even when the selected page has no rows. */
    static NativeDeleteMetadataGraph.Capture metadata(NativeOrgBatchPage page,IBean bean) throws BusinessException {
        table(page,bean);
        return NativeDeleteMetadataGraph.captureForUpdate(bean,Collections.<SnapshotRow>emptyList(),
            new NativeDeleteMetadataGraph.Reader() {
                public List<SnapshotRow> read(ITable ignored,String match,String value,boolean lock) throws BusinessException {
                    throw new BusinessException("组织页签元数据预检不能读取业务行。");
                }
            },false,page.childAttributes());
    }

    static Capture capture(NativeOrgBatchSelection.Impact impact,IBean bean,NativeDeleteMetadataGraph.Reader reader,boolean lock)
            throws BusinessException {
        EsignValues.require(impact!=null && reader!=null,"组织页签审计缺少范围或读取器。");
        ITable table=table(impact.getPage(),bean);
        Map<String,NativeDeleteMetadataGraph.Capture> graphs=new LinkedHashMap<String,NativeDeleteMetadataGraph.Capture>();
        Map<String,List<SnapshotRow>> pages=new LinkedHashMap<String,List<SnapshotRow>>();Set<String> physical=new HashSet<String>();
        for(NativeOrgBatchSelection.Row row:impact.getRows()) {
            List<SnapshotRow> roots=reader.read(table,impact.getPage().key,row.getId(),lock);
            EsignValues.require(roots!=null && roots.size()==1,"组织页签审计主行不存在或不唯一。");
            verifyRoot(impact.getPage(),row,roots.get(0));
            NativeDeleteMetadataGraph.Capture graph=NativeDeleteMetadataGraph.captureForUpdate(bean,roots,reader,lock,
                impact.getPage().childAttributes());
            add(row,graph,graphs,pages,physical);
        }
        return new Capture(impact,graphs,pages,null);
    }

    /** Re-read historical identities as well as the current graph, detecting moved or newly linked child rows. */
    static Capture recapture(Capture prior,IBean bean,NativeDeleteMetadataGraph.Reader reader,boolean lock)
            throws BusinessException {
        EsignValues.require(prior!=null && reader!=null,"组织页签审计缺少执行前范围或读取器。");
        table(prior.impact.getPage(),bean);
        Map<String,NativeDeleteMetadataGraph.Capture> graphs=new LinkedHashMap<String,NativeDeleteMetadataGraph.Capture>();
        Map<String,List<SnapshotRow>> pages=new LinkedHashMap<String,List<SnapshotRow>>();Set<String> physical=new HashSet<String>();
        for(NativeOrgBatchSelection.Row row:prior.impact.getRows()) {
            NativeDeleteMetadataGraph.Capture old=prior.graphs.get(row.getId());
            EsignValues.require(old!=null,"组织页签审计缺少执行前元数据范围。");
            List<SnapshotRow> historical=NativeDeleteMetadataGraph.recapture(old,reader,lock);
            SnapshotRow root=null;
            for(SnapshotRow candidate:historical) if(prior.impact.getPage().table.equals(candidate.getEntity())
                    && row.getId().equals(candidate.getRowId())) {
                EsignValues.require(root==null,"组织页签审计主行重复。");root=candidate;
            }
            verifyRoot(prior.impact.getPage(),row,root);
            NativeDeleteMetadataGraph.Capture current=NativeDeleteMetadataGraph.captureForUpdate(bean,Collections.singletonList(root),reader,lock,
                prior.impact.getPage().childAttributes());
            EsignValues.require(old.metadataEvidence().equals(current.metadataEvidence())
                && PartnerStateSnapshots.sameRows(historical,current.getRows()),"组织页签关联或元数据范围发生变化，业务必须回滚。");
            add(row,current,graphs,pages,physical);
        }
        return new Capture(prior.impact,graphs,pages,prior);
    }

    private static ITable table(NativeOrgBatchPage page,IBean bean) throws BusinessException {
        EsignValues.require(page!=null && bean!=null && page.metadataId.equals(bean.getID()) && bean.getTable()!=null
            && page.table.equalsIgnoreCase(bean.getTable().getName()) && page.key.equalsIgnoreCase(bean.getTable().getPrimaryKeyName()),
            "组织页签审计元数据与已授权页签不一致。");
        return bean.getTable();
    }
    private static void verifyRoot(NativeOrgBatchPage page,NativeOrgBatchSelection.Row selected,SnapshotRow actual)
            throws BusinessException {
        EsignValues.require(actual!=null && page.table.equals(actual.getEntity()) && selected.getId().equals(actual.getRowId())
            && selected.getId().equals(actual.getValues().get(page.key)) && selected.getRoot().equals(actual.getValues().get(page.rootKey))
            && selected.getTarget().equals(actual.getValues().get("pk_org")) && selected.getGroup().equals(actual.getValues().get("pk_group")),
            "组织页签审计行已转移或不属于已授权范围。");
    }
    private static void add(NativeOrgBatchSelection.Row row,NativeDeleteMetadataGraph.Capture graph,
            Map<String,NativeDeleteMetadataGraph.Capture> graphs,Map<String,List<SnapshotRow>> pages,Set<String> physical)
            throws BusinessException {
        EsignValues.require(graphs.put(row.getId(),graph)==null,"组织页签审计行重复。");
        List<SnapshotRow> copy=new ArrayList<SnapshotRow>();
        for(SnapshotRow item:graph.getRows()) {
            EsignValues.require(item!=null && item.getEntity()!=null && item.getRowId()!=null
                && physical.add(item.getEntity()+"\u0000"+item.getRowId()),"组织页签审计物理行跨页签重复或缺少标识。");
            copy.add(item);
        }
        EsignValues.require(!copy.isEmpty(),"组织页签审计快照为空。");
        pages.put(row.getId(),Collections.unmodifiableList(copy));
    }
    private static String pack(String... values) {
        StringBuilder result=new StringBuilder();
        for(String value:values) result.append(value==null?-1:value.length()).append(':').append(value==null?"":value);
        return result.toString();
    }
}
