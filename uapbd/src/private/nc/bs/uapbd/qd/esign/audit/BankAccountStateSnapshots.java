package nc.bs.uapbd.qd.esign.audit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import nc.bs.uapbd.qd.esign.core.BankAccountImpactScope;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;

/** Physical rows needed for bank-account enable/disable, grouped by affected partner archive. */
public final class BankAccountStateSnapshots {
    private BankAccountStateSnapshots() { }
    public interface Reader {
        List<SnapshotRow> read(String table,String key,String parent,String match,String value,
                String scope,String scopeValue,boolean lock) throws BusinessException;
    }
    /** Caller must first acquire the native account and partner locks in the verified native order. */
    public static Map<String,List<SnapshotRow>> capture(String group,BankAccountImpactScope.Selection selection,
            Reader reader,boolean lock) throws BusinessException {
        EsignValues.require(group!=null && !group.trim().isEmpty() && selection!=null && reader!=null
            && !selection.getArchives().isEmpty(),"银行账户快照缺少集团或影响范围。");
        String account=selection.getAccount();
        List<SnapshotRow> masters=reader.read("bd_bankaccbas","pk_bankaccbas",null,"pk_bankaccbas",account,"pk_group",group,lock);
        EsignValues.require(masters!=null && masters.size()==1 && masters.get(0)!=null
            && "bd_bankaccbas".equals(masters.get(0).getEntity()) && account.equals(masters.get(0).getRowId())
            && account.equals(masters.get(0).getValues().get("pk_bankaccbas"))
            && group.equals(masters.get(0).getValues().get("pk_group")),"银行账户主行不存在或不属于当前集团。");
        List<SnapshotRow> children=reader.read("bd_bankaccsub","pk_bankaccsub","pk_bankaccbas","pk_bankaccbas",account,null,null,lock);
        List<SnapshotRow> links=reader.read("bd_custbank","pk_custbank","pk_cust","pk_bankaccbas",account,null,null,lock);
        EsignValues.require(children!=null && links!=null,"银行账户关联数据读取不完整。");
        Map<String,SnapshotRow> relationRows=index(links,"bd_custbank");
        Map<String,SnapshotRow> childRows=index(children,"bd_bankaccsub");
        for(SnapshotRow child:childRows.values()) EsignValues.require(account.equals(child.getValues().get("pk_bankaccbas"))
            && account.equals(child.getParentRowId()) && child.getRowId().equals(child.getValues().get("pk_bankaccsub")),
            "银行币种子行属于其他账户。");
        Map<String,SnapshotRow> uses=new LinkedHashMap<String,SnapshotRow>();
        for(SnapshotRow child:childRows.values()) {
            List<SnapshotRow> rows=reader.read("bd_bankaccuse","pk_bankaccuse","pk_bankaccsub","pk_bankaccsub",
                child.getRowId(),null,null,lock);
            EsignValues.require(rows!=null,"银行账户使用组织读取不完整。");
            for(SnapshotRow use:index(rows,"bd_bankaccuse").values()) {
                String useAccount=use.getValues().get("pk_bankaccbas");
                EsignValues.require(child.getRowId().equals(use.getValues().get("pk_bankaccsub"))
                    && child.getRowId().equals(use.getParentRowId()) && use.getRowId().equals(use.getValues().get("pk_bankaccuse"))
                    && (useAccount==null || account.equals(useAccount)) && uses.put(use.getRowId(),use)==null,
                    "银行账户使用组织的账户或币种关系不一致。");
            }
        }
        Map<String,List<SnapshotRow>> result=new LinkedHashMap<String,List<SnapshotRow>>();
        for(BankAccountImpactScope.AffectedArchive archive:selection.getArchives()) {
            boolean customer="BD_CUSTOMER_BANK".equals(archive.getDomain());
            EsignValues.require(customer || "BD_SUPPLIER_BANK".equals(archive.getDomain()),"银行账户关联了未知档案类型。");
            String table=customer?"bd_customer":"bd_supplier",key=customer?"pk_customer":"pk_supplier";
            List<SnapshotRow> roots=reader.read(table,key,null,key,archive.getArchive(),"pk_group",group,lock);
            EsignValues.require(roots!=null && roots.size()==1 && roots.get(0)!=null && table.equals(roots.get(0).getEntity())
                && archive.getArchive().equals(roots.get(0).getRowId()) && archive.getArchive().equals(roots.get(0).getValues().get(key))
                && group.equals(roots.get(0).getValues().get("pk_group"))
                && archive.getOwner().equals(roots.get(0).getValues().get("pk_org"))
                && archive.getCode().equals(roots.get(0).getValues().get("code")),"银行账户关联档案或所属组织已变化。");
            List<SnapshotRow> rows=new ArrayList<SnapshotRow>(roots);SnapshotRow master=masters.get(0);
            rows.add(new SnapshotRow(master.getEntity(),master.getRowId(),archive.getArchive(),master.getValues(),master.getTypes(),master.getLabels(),true));
            rows.addAll(childRows.values());
            rows.addAll(uses.values());
            for(String relation:archive.getRelations()) {
                SnapshotRow row=relationRows.remove(relation);
                EsignValues.require(row!=null && account.equals(row.getValues().get("pk_bankaccbas"))
                    && archive.getArchive().equals(row.getValues().get("pk_cust"))
                    && archive.getArchive().equals(row.getParentRowId()) && relation.equals(row.getValues().get("pk_custbank"))
                    && (customer?"1":"3").equals(row.getValues().get("accclass")),"银行账户关系或所属档案已变化。");
                rows.add(row);
            }
            String identity=archive.getDomain()+"\u0000"+archive.getArchive();
            EsignValues.require(result.put(identity,Collections.unmodifiableList(rows))==null,"银行账户影响范围包含重复档案。");
        }
        EsignValues.require(relationRows.isEmpty(),"银行账户新增了未经授权的关联关系。");
        return Collections.unmodifiableMap(result);
    }
    private static Map<String,SnapshotRow> index(List<SnapshotRow> rows,String table) throws BusinessException {
        Map<String,SnapshotRow> result=new LinkedHashMap<String,SnapshotRow>();
        for(SnapshotRow row:rows) EsignValues.require(row!=null && table.equals(row.getEntity()) && result.put(row.getRowId(),row)==null,
            "银行账户快照包含其他表或重复行。");
        return result;
    }
    public static Reader database() {
        return new Reader() {
            private final PhysicalSnapshotReader reader=new PhysicalSnapshotReader();
            public List<SnapshotRow> read(String table,String key,String parent,String match,String value,
                    String scope,String scopeValue,boolean lock) throws BusinessException {
                return lock?reader.readLocked(table,key,parent,match,value,scope,scopeValue,true)
                    :reader.read(table,key,parent,match,value,scope,scopeValue,true);
            }
        };
    }
}
