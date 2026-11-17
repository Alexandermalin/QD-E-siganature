package nc.bs.uapbd.qd.esign.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import nc.bs.uapbd.qd.esign.audit.PhysicalSnapshotReader;
import nc.bs.uapbd.qd.esign.persistence.QdEsignStore;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;

/** Determines every customer/supplier linked to a shared physical bank-account master. */
public final class BankAccountImpactScope {
    private BankAccountImpactScope() { }
    public interface Reader {
        List<Relation> relations(String account,boolean lock) throws BusinessException;
        Owner owner(String group,String domain,String relation,boolean lock) throws BusinessException;
    }
    public static final class Relation {
        public final String id,account,archive;public final Integer accountClass;
        public Relation(String id,String account,String archive,Integer accountClass) {
            this.id=id;this.account=account;this.archive=archive;this.accountClass=accountClass;
        }
    }
    public static final class Owner {
        public final String group,organization,archive,code;
        public Owner(String group,String organization,String archive,String code) {
            this.group=group;this.organization=organization;this.archive=archive;this.code=code;
        }
    }
    public static final class AffectedArchive {
        private final String domain,archive,owner,code;private final List<String> relations;
        private AffectedArchive(String domain,Owner owner,List<String> relations) {
            this.domain=domain;this.archive=owner.archive;this.owner=owner.organization;this.code=owner.code;
            this.relations=Collections.unmodifiableList(new ArrayList<String>(relations));
        }
        public String getDomain() { return domain; }
        public String getArchive() { return archive; }
        public String getOwner() { return owner; }
        public String getCode() { return code; }
        public List<String> getRelations() { return relations; }
    }
    public static final class Selection {
        private final String account,selected;private final List<AffectedArchive> archives;
        private Selection(String account,String selected,List<AffectedArchive> archives) {
            this.account=account;this.selected=selected;this.archives=Collections.unmodifiableList(archives);
        }
        public String getAccount() { return account; }
        public String getSelectedRelation() { return selected; }
        public List<AffectedArchive> getArchives() { return archives; }
    }

    /** The selected relation identifies the account; all other links are discovered on the server. */
    public static Selection select(String group,String account,BusinessObjectRef selected,Reader reader,boolean lock)
            throws BusinessException {
        EsignValues.require(present(group) && present(account) && selected!=null && present(selected.getObjectPk())
            && ("BD_CUSTOMER_BANK".equals(selected.getObjectType()) || "BD_SUPPLIER_BANK".equals(selected.getObjectType()))
            && reader!=null,"银行账户影响范围缺少有效的集团或关系主键。");
        List<Relation> input=reader.relations(account,lock);
        EsignValues.require(input!=null && !input.isEmpty() && input.size()<=1000,"银行账户关联范围为空或超出受控上限。");
        Map<String,Relation> unique=new LinkedHashMap<String,Relation>();
        for(Relation relation:input) EsignValues.require(relation!=null && present(relation.id) && present(relation.archive)
            && account.equals(relation.account) && unique.put(relation.id,relation)==null,"银行账户关系不完整或重复。");
        Relation root=unique.get(selected.getObjectPk());
        EsignValues.require(root!=null && selected.getObjectType().equals(domain(root.accountClass)),"选中银行账户关系或类型已变化。");
        List<Relation> ordered=new ArrayList<Relation>(unique.values());Collections.sort(ordered,new Comparator<Relation>() {
            public int compare(Relation a,Relation b) { return a.id.compareTo(b.id); }
        });
        Map<String,Owner> owners=new LinkedHashMap<String,Owner>();Map<String,List<String>> memberships=new LinkedHashMap<String,List<String>>();
        for(Relation relation:ordered) {
            String domain=domain(relation.accountClass);
            EsignValues.require(domain!=null,"银行账户关联了尚未支持的档案类型。");
            Owner owner=reader.owner(group,domain,relation.id,lock);
            EsignValues.require(owner!=null && group.equals(owner.group) && relation.archive.equals(owner.archive)
                && present(owner.organization) && present(owner.code),"银行账户关联档案不存在、跨集团或所属组织无法确定。");
            String key=domain+"\u0000"+owner.archive;Owner previous=owners.get(key);
            if(previous==null) { owners.put(key,owner);memberships.put(key,new ArrayList<String>()); }
            else EsignValues.require(Objects.equals(previous.organization,owner.organization) && Objects.equals(previous.code,owner.code),
                "同一关联档案出现不一致的所属组织或编码。");
            memberships.get(key).add(relation.id);
        }
        List<AffectedArchive> result=new ArrayList<AffectedArchive>();
        for(Map.Entry<String,Owner> item:owners.entrySet()) {
            String domain=item.getKey().substring(0,item.getKey().indexOf('\u0000'));
            result.add(new AffectedArchive(domain,item.getValue(),memberships.get(item.getKey())));
        }
        return new Selection(account,selected.getObjectPk(),result);
    }

    /** Recheck the full membership and owner set before the business transaction may commit. */
    public static void verifyUnchanged(Selection prepared,Selection current) throws BusinessException {
        EsignValues.require(prepared!=null && current!=null && prepared.account.equals(current.account)
            && prepared.selected.equals(current.selected) && prepared.archives.size()==current.archives.size(),"银行账户影响范围已变化。");
        Map<String,AffectedArchive> expected=new LinkedHashMap<String,AffectedArchive>();
        for(AffectedArchive a:prepared.archives) expected.put(a.domain+"\u0000"+a.archive,a);
        for(AffectedArchive b:current.archives) {
            AffectedArchive a=expected.remove(b.domain+"\u0000"+b.archive);
            EsignValues.require(a!=null && a.owner.equals(b.owner) && a.code.equals(b.code) && a.relations.equals(b.relations),
                "银行账户的关联档案、所属组织或关系行已变化。");
        }
        EsignValues.require(expected.isEmpty(),"银行账户影响范围已变化。");
    }

    /** Server reader uses only fixed identifiers and queries owners from the verified parent archive. */
    public static Reader database() {
        return new Reader() {
            public List<Relation> relations(String account,boolean lock) throws BusinessException {
                PhysicalSnapshotReader reader=new PhysicalSnapshotReader();
                List<SnapshotRow> rows=lock?reader.readLocked("bd_custbank","pk_custbank","pk_cust","pk_bankaccbas",account,true)
                    :reader.read("bd_custbank","pk_custbank","pk_cust","pk_bankaccbas",account,true);
                List<Relation> result=new ArrayList<Relation>();
                for(SnapshotRow row:rows) {
                    String raw=row.getValues().get("accclass");Integer type=null;
                    try { if(raw!=null) type=Integer.valueOf(raw); } catch(NumberFormatException ignored) { }
                    result.add(new Relation(row.getRowId(),row.getValues().get("pk_bankaccbas"),row.getValues().get("pk_cust"),type));
                }
                return result;
            }
            public Owner owner(String group,String domain,String relation,boolean lock) throws BusinessException {
                Map<String,Object> row=new QdEsignStore().loadBaseArchiveOwner(group,domain,relation,lock);
                return row==null?null:new Owner(EsignValues.text(row,"pk_group"),EsignValues.text(row,"pk_org"),
                    EsignValues.text(row,"pk_archive_root"),EsignValues.text(row,"code"));
            }
        };
    }
    private static String domain(Integer type) {
        return Integer.valueOf(1).equals(type)?"BD_CUSTOMER_BANK":Integer.valueOf(3).equals(type)?"BD_SUPPLIER_BANK":null;
    }
    private static boolean present(String value) { return value!=null && !value.trim().isEmpty(); }
}
