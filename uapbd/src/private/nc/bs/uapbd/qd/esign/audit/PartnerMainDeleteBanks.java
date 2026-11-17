package nc.bs.uapbd.qd.esign.audit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;

/** Physical account graph used by the candidate partner-master delete transaction. */
public final class PartnerMainDeleteBanks {
    private PartnerMainDeleteBanks() { }

    public interface Reader {
        List<SnapshotRow> read(String table,String key,String match,String value,boolean lock) throws BusinessException;
    }

    public static final class Selection {
        private final String group,archive,type;
        private final Map<String,Account> accounts;
        private final List<SnapshotRow> rows;
        private Selection(String group,String archive,String type,Map<String,Account> accounts,List<SnapshotRow> rows) {
            this.group=group;this.archive=archive;this.type=type;
            this.accounts=Collections.unmodifiableMap(new LinkedHashMap<String,Account>(accounts));
            this.rows=Collections.unmodifiableList(new ArrayList<SnapshotRow>(rows));
        }
        public String getGroup() { return group; }
        public String getArchive() { return archive; }
        public String getDomain() { return "1".equals(type)?"BD_CUSTOMER":"BD_SUPPLIER"; }
        public List<SnapshotRow> getRows() { return rows; }

        /** Only these accounts may be put into NC's native master-delete bank array. */
        public Set<String> getExclusiveAccounts() {
            Set<String> result=new TreeSet<String>();
            for(Account account:accounts.values()) if(account.external.isEmpty()) result.add(account.id);
            return Collections.unmodifiableSet(result);
        }

        /** One authoritative relation per exclusive account; array contents never come from the client. */
        public Map<String,String> getExclusiveRelations() {
            Map<String,String> result=new LinkedHashMap<String,String>();
            for(Account account:accounts.values()) if(account.external.isEmpty()) result.put(account.id,account.own.first());
            return Collections.unmodifiableMap(result);
        }

        public List<String> evidence() {
            List<String> result=new ArrayList<String>();
            for(SnapshotRow row:rows) result.add("bank-row:"+SnapshotSupport.rowFingerprint(row));
            Collections.sort(result);
            return Collections.unmodifiableList(result);
        }
    }

    /** Includes deleted relationship rows: NC deletes by physical archive and account class without a dr filter. */
    public static Selection capture(String group,String domain,String archive,Reader reader,boolean lock) throws BusinessException {
        EsignValues.require(present(group) && present(archive) && reader!=null
            && ("BD_CUSTOMER".equals(domain) || "BD_SUPPLIER".equals(domain)),"主档删除的银行账户范围不完整。");
        String type="BD_CUSTOMER".equals(domain)?"1":"3";
        Map<String,SnapshotRow> own=index(read(reader,"bd_custbank","pk_custbank","pk_cust",archive,lock),"bd_custbank");
        Map<String,Set<String>> accounts=new TreeMap<String,Set<String>>();
        for(SnapshotRow row:own.values()) {
            EsignValues.require(archive.equals(row.getValues().get("pk_cust")),"银行账户关系不属于待删主档。");
            if(!type.equals(row.getValues().get("accclass"))) continue;
            String id=row.getValues().get("pk_bankaccbas");
            EsignValues.require(present(id),"主档银行关系缺少账户主键。");
            Set<String> links=accounts.get(id);
            if(links==null) { links=new TreeSet<String>();accounts.put(id,links); }
            links.add(row.getRowId());
        }
        Map<String,Account> result=new LinkedHashMap<String,Account>();
        Map<String,SnapshotRow> rows=new TreeMap<String,SnapshotRow>();
        for(Map.Entry<String,Set<String>> entry:accounts.entrySet()) {
            Account account=readAccount(group,archive,type,entry.getKey(),reader,lock,true);
            EsignValues.require(account.own.equals(entry.getValue()),"银行账户关联范围读取不一致。");
            result.put(entry.getKey(),account);
            add(rows,account.rows);
        }
        return new Selection(group,archive,type,result,new ArrayList<SnapshotRow>(rows.values()));
    }

    /** Must run before commit with the same locks as capture; shared account rows may not change. */
    public static List<SnapshotRow> verifyAfter(Selection before,Reader reader,boolean lock) throws BusinessException {
        EsignValues.require(before!=null && reader!=null,"缺少银行账户删除前快照。");
        for(SnapshotRow row:read(reader,"bd_custbank","pk_custbank","pk_cust",before.archive,lock))
            EsignValues.require(!before.type.equals(row.getValues().get("accclass")),"主档删除后仍存在银行账户关系。");
        Map<String,SnapshotRow> result=new TreeMap<String,SnapshotRow>();
        for(Account prior:before.accounts.values()) {
            Account current=readAccount(before.group,before.archive,before.type,prior.id,reader,lock,false);
            for(SnapshotRow row:prior.rows) {
                boolean ownLink="bd_custbank".equals(row.getEntity()) && prior.own.contains(row.getRowId());
                if(ownLink || prior.external.isEmpty()) {
                    String key=key(row.getEntity());
                    EsignValues.require(read(reader,row.getEntity(),key,key,row.getRowId(),lock).isEmpty(),
                        "待删除银行关系或独占账户子行被保留或转移，必须回滚。");
                }
            }
            // A deleted currency can leave a use row whose account column is null. Recheck its old parent identity.
            Map<String,SnapshotRow> afterRows=new TreeMap<String,SnapshotRow>();add(afterRows,current.rows);
            for(SnapshotRow row:prior.rows) if("bd_bankaccsub".equals(row.getEntity()))
                add(afterRows,read(reader,"bd_bankaccuse","pk_bankaccuse","pk_bankaccsub",row.getRowId(),lock));
            current=new Account(current.id,current.own,current.external,new ArrayList<SnapshotRow>(afterRows.values()));
            EsignValues.require(current.own.isEmpty(),"主档银行账户关系未删除。");
            if(!prior.external.isEmpty()) {
                EsignValues.require(prior.external.equals(current.external) && sameShared(prior,current),
                    "共享银行账户或其他档案关系发生变化，必须回滚删除。");
            } else EsignValues.require(current.external.isEmpty() && current.rows.isEmpty(),
                "独占银行账户或其子行未被完整删除，或删除期间新增了其他档案关系。");
            add(result,current.rows);
        }
        return Collections.unmodifiableList(new ArrayList<SnapshotRow>(result.values()));
    }

    private static Account readAccount(String group,String archive,String type,String account,Reader reader,
            boolean lock,boolean requireMaster) throws BusinessException {
        Map<String,SnapshotRow> rows=new TreeMap<String,SnapshotRow>();
        List<SnapshotRow> master=read(reader,"bd_bankaccbas","pk_bankaccbas","pk_bankaccbas",account,lock);
        EsignValues.require(master.size()<=1 && (!requireMaster || master.size()==1),"银行账户主行缺失或重复。");
        for(SnapshotRow row:master) EsignValues.require(account.equals(row.getRowId())
            && account.equals(row.getValues().get("pk_bankaccbas")) && group.equals(row.getValues().get("pk_group")),
            "银行账户主行不属于当前集团。");
        add(rows,master);
        List<SnapshotRow> subs=read(reader,"bd_bankaccsub","pk_bankaccsub","pk_bankaccbas",account,lock);
        for(SnapshotRow row:subs) EsignValues.require(account.equals(row.getValues().get("pk_bankaccbas")),"银行账户币种关联不一致。");
        add(rows,subs);
        List<SnapshotRow> uses=read(reader,"bd_bankaccuse","pk_bankaccuse","pk_bankaccbas",account,lock);
        add(rows,uses);
        for(SnapshotRow sub:subs) {
            for(SnapshotRow use:read(reader,"bd_bankaccuse","pk_bankaccuse","pk_bankaccsub",sub.getRowId(),lock)) {
                String linked=use.getValues().get("pk_bankaccbas");
                EsignValues.require(linked==null || account.equals(linked),"银行账户使用组织关联了其他账户。");
                add(rows,Collections.singletonList(use));
            }
        }
        Set<String> own=new TreeSet<String>(),external=new TreeSet<String>();
        for(SnapshotRow row:read(reader,"bd_custbank","pk_custbank","pk_bankaccbas",account,lock)) {
            EsignValues.require(account.equals(row.getValues().get("pk_bankaccbas")) && present(row.getValues().get("pk_cust"))
                && present(row.getValues().get("accclass")),"银行账户关系不完整。");
            (archive.equals(row.getValues().get("pk_cust")) && type.equals(row.getValues().get("accclass"))?own:external)
                .add(row.getRowId());
            add(rows,Collections.singletonList(row));
        }
        return new Account(account,own,external,new ArrayList<SnapshotRow>(rows.values()));
    }

    private static List<SnapshotRow> read(Reader reader,String table,String key,String match,String value,boolean lock)
            throws BusinessException {
        List<SnapshotRow> rows=reader.read(table,key,match,value,lock);
        EsignValues.require(rows!=null,"银行账户物理数据读取不完整。");
        for(SnapshotRow row:rows) EsignValues.require(row!=null && table.equals(row.getEntity())
            && row.getRowId().equals(row.getValues().get(key)) && value.equals(row.getValues().get(match)),
            "银行账户物理数据不属于所查询范围。");
        return rows;
    }

    private static Map<String,SnapshotRow> index(List<SnapshotRow> rows,String table) throws BusinessException {
        Map<String,SnapshotRow> result=new TreeMap<String,SnapshotRow>();
        for(SnapshotRow row:rows) EsignValues.require(table.equals(row.getEntity()) && result.put(row.getRowId(),row)==null,
            "银行账户物理数据包含重复行。");
        return result;
    }

    private static void add(Map<String,SnapshotRow> result,List<SnapshotRow> rows) throws BusinessException {
        for(SnapshotRow row:rows) {
            String key=row.getEntity()+"\u0000"+row.getRowId();SnapshotRow previous=result.put(key,row);
            EsignValues.require(previous==null || SnapshotSupport.json(previous).equals(SnapshotSupport.json(row)),
                "银行账户物理行通过不同关联读取时不一致。");
        }
    }

    private static boolean sameShared(Account prior,Account current) {
        Map<String,String> left=new TreeMap<String,String>(),right=new TreeMap<String,String>();
        for(SnapshotRow row:prior.rows) if(!"bd_custbank".equals(row.getEntity()) || !prior.own.contains(row.getRowId()))
            left.put(row.getEntity()+"\u0000"+row.getRowId(),SnapshotSupport.json(row));
        for(SnapshotRow row:current.rows) right.put(row.getEntity()+"\u0000"+row.getRowId(),SnapshotSupport.json(row));
        return left.equals(right);
    }

    public static Reader database() {
        return new Reader() {
            private final PhysicalSnapshotReader physical=new PhysicalSnapshotReader();
            public List<SnapshotRow> read(String table,String key,String match,String value,boolean lock) throws BusinessException {
                return lock?physical.readLocked(table,key,null,match,value,false):physical.read(table,key,null,match,value,false);
            }
        };
    }

    private static boolean present(String value) { return value!=null && !value.trim().isEmpty(); }
    private static String key(String table) {
        if("bd_custbank".equals(table)) return "pk_custbank";
        if("bd_bankaccbas".equals(table)) return "pk_bankaccbas";
        if("bd_bankaccsub".equals(table)) return "pk_bankaccsub";
        if("bd_bankaccuse".equals(table)) return "pk_bankaccuse";
        throw new IllegalArgumentException("Unexpected bank snapshot table");
    }

    private static final class Account {
        final String id;final java.util.SortedSet<String> own,external;final List<SnapshotRow> rows;
        Account(String id,Set<String> own,Set<String> external,List<SnapshotRow> rows) {
            this.id=id;this.own=new TreeSet<String>(own);this.external=new TreeSet<String>(external);this.rows=rows;
        }
    }
}
