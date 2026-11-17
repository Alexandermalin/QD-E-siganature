package nc.impl.uapbd.qd.esign;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import nc.bs.uapbd.qd.esign.audit.NativeDeleteMetadataGraph;
import nc.bs.uapbd.qd.esign.audit.PartnerMainDeleteBanks;
import nc.bs.uapbd.qd.esign.audit.PhysicalSnapshotReader;
import nc.bs.uapbd.qd.esign.audit.SnapshotSupport;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.bs.uapbd.qd.esign.core.PartnerMainDeleteScope;
import nc.bs.uapbd.qd.esign.core.PartnerMainSaveImpact;
import nc.md.model.IBean;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;

/** Server-side evidence for a candidate partner-main delete; no client-selected table or service is accepted. */
final class PartnerMainDeleteData {
    private static final String[][] CUSTOMER={
        {"bd_custorg","pk_custorg","pk_customer"},{"bd_custfinance","pk_custfinance","pk_customer"},
        {"bd_custsale","pk_custsale","pk_customer"},{"bd_custcreditctl","pk_custcreditctl","pk_customer"},
        {"bd_custlinkman","pk_custlinkman","pk_customer"},{"bd_custvat","pk_custvat","pk_customer"},
        {"bd_custtaxtypes","pk_custtaxtypes","pk_customer"},{"bd_customer_c","pk_commondata","pk_doc"}
    };
    private static final String[][] SUPPLIER={
        {"bd_suporg","pk_suporg","pk_supplier"},{"bd_supfinance","pk_supfinance","pk_supplier"},
        {"bd_supstock","pk_supstock","pk_supplier"},{"bd_suplinkman","pk_suplinkman","pk_supplier"},
        {"bd_supvat","pk_supvat","pk_supplier"},{"bd_supcountrytaxes","pk_supcountrytaxes","pk_supplier"},
        {"bd_supplier_c","pk_commondata","pk_doc"},
        {"srmsm_supplierext_h","pk_supplier","pk_supplier"},{"srmsm_supplierext_a","pk_supplierext_a","pk_supplier"},
        {"srmsm_supplierext_i","pk_supplierext_i","pk_supplier"},{"srmsm_supplierext_p","pk_supplierext_p","pk_supplier"},
        {"srmsm_supplierext_q","pk_supplierext_q","pk_supplier"}
    };
    private static final String[][] COMMON={
        {"bd_cust_supplier","pk_cust_sup","pk_cust_sup"},{"bd_cust_supplier_c","pk_commondata","pk_doc"},
        {"bd_ansylog","pk_anydelte","pk_basdoc"}
    };
    private PartnerMainDeleteData() { }

    static final class State {
        final String group,domain,archive;final List<SnapshotRow> rows;final List<String> evidence;
        final PartnerMainDeleteBanks.Selection banks;final List<Graph> graphs;
        State(String group,String domain,String archive,Map<String,SnapshotRow> rows,Set<String> configuration,
                PartnerMainDeleteBanks.Selection banks,List<Graph> graphs) {
            this.group=group;this.domain=domain;this.archive=archive;
            this.rows=Collections.unmodifiableList(new ArrayList<SnapshotRow>(rows.values()));this.banks=banks;
            this.graphs=Collections.unmodifiableList(new ArrayList<Graph>(graphs));
            Set<String> facts=new TreeSet<String>(configuration);
            for(SnapshotRow row:this.rows) facts.add("row:"+SnapshotSupport.rowFingerprint(row));
            this.evidence=Collections.unmodifiableList(new ArrayList<String>(facts));
        }
    }
    private static final class Graph {
        final IBean bean;final NativeDeleteMetadataGraph.Capture before;
        Graph(IBean bean,NativeDeleteMetadataGraph.Capture before) { this.bean=bean;this.before=before; }
    }

    static PartnerMainDeleteScope.Reader reader(final String node) {
        return new PartnerMainDeleteScope.Reader() {
            private final PartnerMainSaveImpact.Reader base=PartnerMainSaveImpact.database();
            public PartnerMainSaveImpact.Archive archive(String group,String domain,String id,boolean lock) throws BusinessException {
                return base.archive(group,domain,id,lock);
            }
            public PartnerMainSaveImpact.Structure structure(String domain,String id,boolean lock) throws BusinessException {
                return base.structure(domain,id,lock);
            }
            public List<PartnerMainSaveImpact.Structure> children(String domain,String id,boolean lock) throws BusinessException {
                return base.children(domain,id,lock);
            }
            public List<PartnerMainSaveImpact.Structure> prefix(String domain,String id,boolean lock) throws BusinessException {
                return base.prefix(domain,id,lock);
            }
            public List<String> dependencies(String group,String domain,String archive,boolean lock) throws BusinessException {
                return capture(node,group,domain,archive,lock).evidence;
            }
        };
    }

    static State capture(String node,String group,String domain,String archive,boolean lock) throws BusinessException {
        boolean customer="BD_CUSTOMER".equals(domain);
        EsignValues.require(customer && ("10140CUG".equals(node) || "10140CUO".equals(node))
            || "BD_SUPPLIER".equals(domain) && ("10140SUG".equals(node) || "10140SUO".equals(node)),
            "客户或供应商删除节点与档案类型不一致。");
        String table=customer?"bd_customer":"bd_supplier",key=customer?"pk_customer":"pk_supplier";
        PhysicalSnapshotReader physical=new PhysicalSnapshotReader();
        List<SnapshotRow> roots=read(physical,table,key,key,archive,lock);
        EsignValues.require(roots.size()==1 && group.equals(roots.get(0).getValues().get("pk_group")),
            "客户或供应商主档不存在或不属于当前集团。");
        CustomerAssignmentRoutePlan customerRoutes=customer?new CustomerAssignmentRoutePlan():null;
        SupplierAssignmentRoutePlan supplierRoutes=customer?null:new SupplierAssignmentRoutePlan();
        IBean base=customer?customerRoutes.baseBean():supplierRoutes.baseBean();
        EsignValues.require(base!=null && base.getTable()!=null && table.equalsIgnoreCase(base.getTable().getName())
            && key.equalsIgnoreCase(base.getTable().getPrimaryKeyName()),"客户或供应商主档元数据不一致。");
        Set<String> evidence=new TreeSet<String>();
        List<String> routes=customer?customerRoutes.allRouteEvidence():supplierRoutes.allRouteEvidence();
        for(String route:routes) evidence.add("route:"+SnapshotSupport.sha256(route));
        String extension=customer?null:"nc/ui/bd/supplierext/maintain/config/supplierext"
            +("10140SUO".equals(node)?"_org":"")+".xml";
        boolean configured=ArchiveExtensionConfig.requireKnown(node,extension);
        evidence.add("extension:"+Boolean.toString(configured));

        Map<String,SnapshotRow> rows=new TreeMap<String,SnapshotRow>();List<Graph> graphs=new ArrayList<Graph>();
        collectGraph(base,roots,group,archive,lock,rows,evidence,graphs);
        List<IBean> pages=customer?customerRoutes.allRouteBeans():supplierRoutes.allRouteBeans();
        for(IBean page:pages) {
            EsignValues.require(page!=null && page.getTable()!=null,"客户或供应商组织页签元数据缺失。");
            List<SnapshotRow> pageRoots=physical.readMetadata(page.getTable(),null,key,archive,lock);
            collectGraph(page,pageRoots,group,archive,lock,rows,evidence,graphs);
        }
        for(String[] item:customer?CUSTOMER:SUPPLIER) {
            List<SnapshotRow> selected=read(physical,item[0],item[1],item[2],archive,lock);
            if(item[0].startsWith("srmsm_supplierext_")) for(SnapshotRow row:selected)
                EsignValues.require(group.equals(row.getValues().get("pk_group")),"供应商扩展包含其他集团的数据。");
            add(rows,selected);
        }
        for(String[] item:COMMON) add(rows,read(physical,item[0],item[1],item[2],archive,lock));
        PartnerMainDeleteBanks.Selection banks=PartnerMainDeleteBanks.capture(group,domain,archive,PartnerMainDeleteBanks.database(),lock);
        add(rows,banks.getRows());
        return new State(group,domain,archive,rows,evidence,banks,graphs);
    }

    private static void collectGraph(IBean bean,List<SnapshotRow> roots,String group,String archive,boolean lock,Map<String,SnapshotRow> rows,
            Set<String> evidence,List<Graph> graphs) throws BusinessException {
        NativeDeleteMetadataGraph.Capture graph=NativeDeleteMetadataGraph.capture(bean,roots,NativeDeleteMetadataGraph.database(),lock);
        guardGraph(graph.getRows(),group,archive);
        add(rows,graph.getRows());evidence.addAll(graph.evidence());graphs.add(new Graph(bean,graph));
    }
    private static void guardGraph(List<SnapshotRow> rows,String group,String archive) throws BusinessException {
        for(SnapshotRow row:rows) {
            String entity=row.getEntity();
            if("bd_customer".equals(entity) || "bd_supplier".equals(entity) || "bd_cust_supplier".equals(entity))
                EsignValues.require(archive.equals(row.getRowId()) && group.equals(row.getValues().get("pk_group")),
                    "删除级联涉及其他主档，需要单独核实其权限与审计范围。");
            EsignValues.require(!"bd_material".equals(entity) && !"bd_stordoc".equals(entity) && !"bd_rack".equals(entity)
                && !"bd_custclass".equals(entity) && !"bd_supplierclass".equals(entity) && !"bd_marbasclass".equals(entity),
                "删除级联涉及其他类型的基础档案，需要单独核实其权限与审计范围。");
        }
    }

    /** Re-read original identities, all direct archive links, and historical metadata cascade paths. */
    static List<SnapshotRow> after(State before,boolean lock) throws BusinessException {
        EsignValues.require(before!=null,"客户或供应商删除缺少执行前快照。");
        boolean customer="BD_CUSTOMER".equals(before.domain);String relation=customer?"pk_customer":"pk_supplier";
        PhysicalSnapshotReader physical=new PhysicalSnapshotReader();Map<String,SnapshotRow> rows=new TreeMap<String,SnapshotRow>();
        NativeDeleteMetadataGraph.Reader metadata=NativeDeleteMetadataGraph.database();
        for(Graph graph:before.graphs) {
            List<SnapshotRow> roots=physical.readMetadata(graph.bean.getTable(),null,relation,before.archive,lock);
            NativeDeleteMetadataGraph.Capture current=NativeDeleteMetadataGraph.capture(graph.bean,roots,metadata,lock);
            EsignValues.require(graph.before.metadataEvidence().equals(current.metadataEvidence()),"主档删除期间级联元数据发生变化。");
            guardGraph(current.getRows(),before.group,before.archive);add(rows,current.getRows());
            List<SnapshotRow> historic=NativeDeleteMetadataGraph.recapture(graph.before,metadata,lock);
            guardGraph(historic,before.group,before.archive);add(rows,historic);
        }
        String[][] direct=customer?CUSTOMER:SUPPLIER;
        for(String[] item:direct) add(rows,read(physical,item[0],item[1],item[2],before.archive,lock));
        for(String[] item:COMMON) add(rows,read(physical,item[0],item[1],item[2],before.archive,lock));
        for(SnapshotRow prior:before.rows) {
            String key=fixedKey(prior.getEntity(),customer);
            if(key!=null) add(rows,read(physical,prior.getEntity(),key,key,prior.getRowId(),lock));
        }
        add(rows,PartnerMainDeleteBanks.verifyAfter(before.banks,PartnerMainDeleteBanks.database(),lock));
        for(SnapshotRow old:before.rows) {
            SnapshotRow current=rows.get(old.getEntity()+"\u0000"+old.getRowId());if(current==null) continue;
            String ownerField=fixedOwnerField(old.getEntity(),customer);
            if(ownerField!=null && before.archive.equals(old.getValues().get(ownerField)))
                EsignValues.require(before.archive.equals(current.getValues().get(ownerField)),"主档删除将关联行转移到其他档案，必须回滚。");
        }
        return Collections.unmodifiableList(new ArrayList<SnapshotRow>(rows.values()));
    }

    /** A successful native delete may retain common-data settings, but not archive, page or extension rows. */
    static void requireClean(State before,List<SnapshotRow> after) throws BusinessException {
        EsignValues.require(before!=null && after!=null,"客户或供应商删除缺少事后快照。");
        Set<String> affected=new TreeSet<String>();
        for(Graph graph:before.graphs) affected.addAll(graph.before.tableNames());
        for(String[] item:"BD_CUSTOMER".equals(before.domain)?CUSTOMER:SUPPLIER) affected.add(item[0]);
        affected.add("bd_cust_supplier");
        for(SnapshotRow row:after) if(affected.contains(row.getEntity()) && !retainedTable(row.getEntity()))
            throw new BusinessException("原生主档删除遗留关联或扩展行："+row.getEntity()+"，必须回滚。");
    }
    private static boolean retainedTable(String table) {
        return "bd_customer_c".equals(table) || "bd_supplier_c".equals(table) || "bd_cust_supplier_c".equals(table)
            || "bd_ansylog".equals(table) || "bd_custstructure".equals(table) || "bd_supstructure".equals(table)
            || "bd_custbank".equals(table) || "bd_bankaccbas".equals(table)
            || "bd_bankaccsub".equals(table) || "bd_bankaccuse".equals(table);
    }

    private static String fixedKey(String table,boolean customer) {
        if((customer?"bd_customer":"bd_supplier").equals(table)) return customer?"pk_customer":"pk_supplier";
        if("bd_bankaccbas".equals(table)) return "pk_bankaccbas";
        if("bd_bankaccsub".equals(table)) return "pk_bankaccsub";
        if("bd_bankaccuse".equals(table)) return "pk_bankaccuse";
        if("bd_custbank".equals(table)) return "pk_custbank";
        for(String[] item:customer?CUSTOMER:SUPPLIER) if(item[0].equals(table)) return item[1];
        for(String[] item:COMMON) if(item[0].equals(table)) return item[1];
        return null;
    }
    private static String fixedOwnerField(String table,boolean customer) {
        for(String[] item:customer?CUSTOMER:SUPPLIER) if(item[0].equals(table)) return item[2];
        return null;
    }
    private static List<SnapshotRow> read(PhysicalSnapshotReader reader,String table,String key,String match,String value,boolean lock)
            throws BusinessException {
        return lock?reader.readLocked(table,key,null,match,value,false):reader.read(table,key,null,match,value,false);
    }
    private static void add(Map<String,SnapshotRow> rows,List<SnapshotRow> values) throws BusinessException {
        for(SnapshotRow row:values) {
            String identity=row.getEntity()+"\u0000"+row.getRowId();SnapshotRow old=rows.put(identity,row);
            EsignValues.require(old==null || SnapshotSupport.json(old).equals(SnapshotSupport.json(row)),"客户或供应商删除物理快照不一致。");
        }
    }
}
