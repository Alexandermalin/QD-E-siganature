package nc.bs.uapbd.qd.esign.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import nc.bs.uapbd.qd.esign.audit.PhysicalSnapshotReader;
import nc.bs.uapbd.qd.esign.persistence.QdEsignStore;
import nc.jdbc.framework.SQLParameter;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.PartnerMainSaveDraft;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;

/** Candidate scope for NC customer/supplier updates, including shared master and structure side effects. */
public final class PartnerMainSaveImpact {
    private PartnerMainSaveImpact() { }
    public interface Reader {
        Archive archive(String group,String domain,String id,boolean lock) throws BusinessException;
        Structure structure(String domain,String id,boolean lock) throws BusinessException;
        List<Structure> children(String domain,String parent,boolean lock) throws BusinessException;
        List<Structure> prefix(String domain,String innercode,boolean lock) throws BusinessException;
    }
    public static final class Archive {
        public final String domain,id,group,owner,code,parent;
        public Archive(String domain,String id,String group,String owner,String code,String parent) {
            this.domain=domain;this.id=id;this.group=group;this.owner=owner;this.code=code;this.parent=parent;
        }
    }
    public static final class Structure {
        public final String id,parent,innercode,deleted;
        public Structure(String id,String parent,String innercode,String deleted) {
            this.id=id;this.parent=parent;this.innercode=innercode;this.deleted=deleted;
        }
    }
    public static final class Selection {
        private final String group,domain,selected,desiredParent;private final boolean counterpart;
        private final List<Archive> archives;private final Map<String,String> evidence;
        private Selection(String group,String domain,String selected,String parent,boolean counterpart,Map<String,Archive> archives,
                Map<String,String> evidence) {
            this.group=group;this.domain=domain;this.selected=selected;this.desiredParent=parent;this.counterpart=counterpart;
            this.archives=Collections.unmodifiableList(new ArrayList<Archive>(archives.values()));
            this.evidence=Collections.unmodifiableMap(new TreeMap<String,String>(evidence));
        }
        public List<Archive> getArchives() { return archives; }
        public String getDomain() { return domain; }
        public String getSelected() { return selected; }
        public String getDesiredParent() { return desiredParent; }
        public boolean hasCounterpart() { return counterpart; }
        public String fingerprint() {
            StringBuilder canonical=new StringBuilder(pack(group,domain,selected,desiredParent,counterpart?"1":"0"));
            for(Map.Entry<String,String> item:evidence.entrySet()) canonical.append(pack(item.getKey(),item.getValue()));
            return nc.bs.uapbd.qd.esign.audit.SnapshotSupport.sha256(canonical.toString());
        }
    }
    public static Selection select(String group,String domain,String selected,String proposedParent,boolean counterpart,Reader reader,boolean lock)
            throws BusinessException {
        EsignValues.require(present(group) && validDomain(domain) && present(selected) && reader!=null,"客户或供应商保存缺少有效的主档。");
        String parent=PartnerMainSaveDraft.normalizeParent(proposedParent);
        EsignValues.require(parent==null || !selected.equals(parent),"上级档案不正确。");
        Map<String,Archive> affected=new TreeMap<String,Archive>();Map<String,String> evidence=new TreeMap<String,String>();
        Archive root=requireArchive(reader,group,domain,selected,lock);add(affected,evidence,root);
        // A client flag cannot prove that a shared customer/supplier counterpart is absent.
        Archive other=reader.archive(group,opposite(domain),selected,lock);
        EsignValues.require(!counterpart || other!=null,"关联客户或供应商不存在，请重新核对。");
        boolean actualCounterpart=other!=null;
        if(actualCounterpart) {
            EsignValues.require(opposite(domain).equals(other.domain) && selected.equals(other.id) && group.equals(other.group)
                && present(other.owner) && present(other.code),"关联客户或供应商跨集团或所属组织不明确。");
            add(affected,evidence,other);
        }
        String old=PartnerMainSaveDraft.normalizeParent(root.parent);
        if(!Objects.equals(old,parent)) {
            if(parent!=null) {
                Archive reference=requireArchive(reader,group,domain,parent,lock);
                evidence.put("reference:"+domain+"\u0000"+parent,pack(reference.group,reference.owner,reference.code,reference.parent));
            }
            Structure selectedRow=reader.structure(domain,selected,lock);record(evidence,domain,selected,selectedRow);
            List<Structure> descendants=Collections.emptyList();
            List<Structure> childRows=reader.children(domain,selected,lock);
            validateRows(childRows,selected,null);recordList(evidence,domain,"children",selected,childRows);
            if(selectedRow!=null && (parent!=null || !childRows.isEmpty())) {
                EsignValues.require(present(selectedRow.innercode) && selectedRow.innercode.matches("[A-Za-z0-9]{1,200}"),
                    "客户或供应商结构内部编码无法安全核实。");
                descendants=reader.prefix(domain,selectedRow.innercode,lock);
                validateRows(descendants,null,selectedRow.innercode);recordList(evidence,domain,"prefix",selectedRow.innercode,descendants);
                boolean includesRoot=false;
                for(Structure row:descendants) {
                    if(selected.equals(row.id)) includesRoot=true;
                    if(parent!=null) EsignValues.require(!parent.equals(row.id),"不能把档案移动到自己的下级。");
                    add(affected,evidence,requireArchive(reader,group,domain,row.id,lock));
                }
                EsignValues.require(includesRoot,"客户或供应商结构范围已变化。");
            } else EsignValues.require(selectedRow!=null || parent!=null || childRows.isEmpty(),"客户或供应商结构不完整。");
            if(old!=null) {
                List<Structure> oldChildren=reader.children(domain,old,lock);
                validateRows(oldChildren,old,null);recordList(evidence,domain,"children",old,oldChildren);
                boolean otherChild=false;for(Structure child:oldChildren) if(!selected.equals(child.id)) otherChild=true;
                if(!otherChild) {
                    Structure oldRow=reader.structure(domain,old,lock);record(evidence,domain,old,oldRow);
                    if(oldRow!=null) add(affected,evidence,requireArchive(reader,group,domain,old,lock));
                }
            }
            if(parent!=null) {
                Structure parentRow=reader.structure(domain,parent,lock);record(evidence,domain,parent,parentRow);
                if(parentRow==null) add(affected,evidence,requireArchive(reader,group,domain,parent,lock));
            }
        }
        EsignValues.require(affected.size()<=1000,"客户或供应商保存影响超过当前授权上限。");
        return new Selection(group,domain,selected,parent,actualCounterpart,affected,evidence);
    }
    /** Invalidate authorization if an owner, counterpart, structure row or its native prefix range changes. */
    public static void verifyUnchanged(Selection previous,Selection current) throws BusinessException {
        EsignValues.require(previous!=null && current!=null && previous.group.equals(current.group) && previous.domain.equals(current.domain)
            && previous.selected.equals(current.selected) && Objects.equals(previous.desiredParent,current.desiredParent)
            && previous.counterpart==current.counterpart && previous.evidence.equals(current.evidence),"客户或供应商保存影响范围已变化，请重新发起操作。");
    }
    private static Archive requireArchive(Reader reader,String group,String domain,String id,boolean lock) throws BusinessException {
        Archive row=reader.archive(group,domain,id,lock);
        EsignValues.require(row!=null && domain.equals(row.domain) && id.equals(row.id) && group.equals(row.group)
            && present(row.owner) && present(row.code),"客户或供应商影响档案不存在、跨集团或所属组织不明确。");
        return row;
    }
    private static void add(Map<String,Archive> result,Map<String,String> evidence,Archive archive) throws BusinessException {
        String key=archive.domain+"\u0000"+archive.id;
        Archive previous=result.put(key,archive);
        if(previous!=null) EsignValues.require(Objects.equals(previous.owner,archive.owner) && Objects.equals(previous.code,archive.code)
            && Objects.equals(previous.parent,archive.parent),"同一档案的所属组织或数据不一致。");
        evidence.put("archive:"+key,pack(archive.group,archive.owner,archive.code,archive.parent));
    }
    private static void record(Map<String,String> evidence,String domain,String id,Structure row) throws BusinessException {
        if(row!=null) EsignValues.require(id.equals(row.id),"客户或供应商结构主键不一致。");
        String key="row:"+domain+"\u0000"+id,value=row==null?"<missing>":pack(row.id,row.parent,row.innercode,row.deleted);
        String previous=evidence.put(key,value);EsignValues.require(previous==null || previous.equals(value),"客户或供应商结构读取不一致。");
    }
    private static void recordList(Map<String,String> evidence,String domain,String kind,String value,List<Structure> rows)
            throws BusinessException {
        Map<String,String> keys=new TreeMap<String,String>();
        for(Structure row:rows) { record(evidence,domain,row.id,row);keys.put(row.id,"1"); }
        evidence.put("list:"+domain+":"+kind+":"+value,pack(keys.keySet().toArray(new String[keys.size()])));
    }
    private static void validateRows(List<Structure> rows,String parent,String prefix) throws BusinessException {
        EsignValues.require(rows!=null,"客户或供应商结构范围不可用。");Set<String> unique=new HashSet<String>();
        for(Structure row:rows) EsignValues.require(row!=null && present(row.id) && unique.add(row.id)
            && (parent==null || parent.equals(row.parent)) && (prefix==null || row.innercode!=null && row.innercode.startsWith(prefix)),
            "客户或供应商结构范围包含重复或不匹配的行。");
    }
    private static String pack(String... values) {
        StringBuilder result=new StringBuilder();for(String value:values) result.append(value==null?-1:value.length()).append(':').append(value==null?"":value);
        return result.toString();
    }
    private static boolean validDomain(String domain) { return "BD_CUSTOMER".equals(domain) || "BD_SUPPLIER".equals(domain); }
    private static String opposite(String domain) { return "BD_CUSTOMER".equals(domain)?"BD_SUPPLIER":"BD_CUSTOMER"; }
    private static boolean present(String value) { return value!=null && !value.trim().isEmpty(); }

    /** Uses fixed NC tables and the same unfiltered innercode prefix as NC's structure update. */
    public static Reader database() {
        return new Reader() {
            private final PhysicalSnapshotReader physical=new PhysicalSnapshotReader();private final QdEsignStore store=new QdEsignStore();
            public Archive archive(String group,String domain,String id,boolean lock) throws BusinessException {
                String table="BD_CUSTOMER".equals(domain)?"bd_customer":"bd_supplier";
                String key="BD_CUSTOMER".equals(domain)?"pk_customer":"pk_supplier";
                // Native paired-master synchronization retrieves by physical PK without a group predicate.
                List<SnapshotRow> rows=lock?physical.readLocked(table,key,null,key,id,false)
                    :physical.read(table,key,null,key,id,false);
                if(rows.isEmpty()) return null;EsignValues.require(rows.size()==1,"客户或供应商主档不唯一。");
                Map<String,String> v=rows.get(0).getValues();String parent="BD_CUSTOMER".equals(domain)?"pk_customer_main":"pk_supplier_main";
                EsignValues.require(v.containsKey(parent),"客户或供应商上级物理字段不存在。");
                return new Archive(domain,id,v.get("pk_group"),v.get("pk_org"),v.get("code"),v.get(parent));
            }
            public Structure structure(String domain,String id,boolean lock) throws BusinessException {
                List<SnapshotRow> rows=read(domain,"id",id,lock);EsignValues.require(rows.size()<=1,"客户或供应商结构主键重复。");
                return rows.isEmpty()?null:convert(rows.get(0));
            }
            public List<Structure> children(String domain,String parent,boolean lock) throws BusinessException {
                return convert(read(domain,"parent",parent,lock));
            }
            public List<Structure> prefix(String domain,String prefix,boolean lock) throws BusinessException {
                String table=table(domain),key=key(domain);SQLParameter params=new SQLParameter();params.addParam(prefix+"%");
                List<Map<String,Object>> rows=store.list("SELECT "+key+" row_id,pk_parent,innercode,dr FROM "+table
                    +" WHERE innercode LIKE ? ORDER BY "+key+(lock?" FOR UPDATE NOWAIT":""),params);
                List<Structure> result=new ArrayList<Structure>();
                for(Map<String,Object> row:rows) result.add(new Structure(EsignValues.text(row,"row_id"),EsignValues.text(row,"pk_parent"),
                    EsignValues.text(row,"innercode"),EsignValues.text(row,"dr")));
                return result;
            }
            private List<SnapshotRow> read(String domain,String mode,String value,boolean lock) throws BusinessException {
                String table=table(domain),key=key(domain),match="id".equals(mode)?key:"pk_parent";
                return lock?physical.readLocked(table,key,null,match,value,false):physical.read(table,key,null,match,value,false);
            }
            private List<Structure> convert(List<SnapshotRow> rows) {
                List<Structure> result=new ArrayList<Structure>();for(SnapshotRow row:rows) result.add(convert(row));return result;
            }
            private Structure convert(SnapshotRow row) {
                return new Structure(row.getRowId(),row.getValues().get("pk_parent"),row.getValues().get("innercode"),row.getValues().get("dr"));
            }
            private String table(String domain) throws BusinessException {
                EsignValues.require(validDomain(domain),"未知客户或供应商结构类型。");return "BD_CUSTOMER".equals(domain)?"bd_custstructure":"bd_supstructure";
            }
            private String key(String domain) throws BusinessException {
                EsignValues.require(validDomain(domain),"未知客户或供应商结构类型。");return "BD_CUSTOMER".equals(domain)?"pk_customer":"pk_supplier";
            }
        };
    }
}
