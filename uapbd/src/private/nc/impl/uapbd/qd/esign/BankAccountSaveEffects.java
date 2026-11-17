package nc.impl.uapbd.qd.esign;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import nc.bs.uapbd.qd.esign.audit.AuditCaptureScope;
import nc.bs.uapbd.qd.esign.audit.BankAccountStateSnapshots;
import nc.bs.uapbd.qd.esign.audit.PhysicalSnapshotReader;
import nc.bs.uapbd.qd.esign.core.BankAccountImpactScope;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;

/** Verifies the native account mutation and captures changes to other default-account relations. */
final class BankAccountSaveEffects {
    private BankAccountSaveEffects() { }

    static Map<String,List<SnapshotRow>> accountRows(String group,BankAccountImpactScope.Selection selection,boolean lock)
            throws BusinessException {
        Map<String,List<SnapshotRow>> result=new LinkedHashMap<String,List<SnapshotRow>>();
        for(Map.Entry<String,List<SnapshotRow>> entry:BankAccountStateSnapshots.capture(group,selection,
                BankAccountStateSnapshots.database(),lock).entrySet())
            result.put(entry.getKey(),new ArrayList<SnapshotRow>(entry.getValue()));
        return result;
    }

    static Map<String,String> archiveKeys(BankAccountImpactScope.Selection selection) {
        Map<String,String> result=new LinkedHashMap<String,String>();
        for(BankAccountImpactScope.AffectedArchive archive:selection.getArchives())
            result.put(key(archive.getDomain(),archive.getArchive()),archive.getOwner());
        return result;
    }

    static Map<String,SnapshotRow> siblingRelations(Set<String> archives,String account,boolean lock) throws BusinessException {
        PhysicalSnapshotReader reader=new PhysicalSnapshotReader();
        Map<String,SnapshotRow> result=new LinkedHashMap<String,SnapshotRow>();
        for(String archive:archives) {
            List<SnapshotRow> rows=lock?reader.readLocked("bd_custbank","pk_custbank","pk_cust","pk_cust",archive,true)
                :reader.read("bd_custbank","pk_custbank","pk_cust","pk_cust",archive,true);
            for(SnapshotRow row:rows) if(account==null || !Objects.equals(account,row.getValues().get("pk_bankaccbas")))
                result.put(row.getRowId(),row);
        }
        return result;
    }

    static Set<String> archiveIds(BankAccountImpactScope.Selection selection) {
        Set<String> ids=new LinkedHashSet<String>();
        for(BankAccountImpactScope.AffectedArchive archive:selection.getArchives()) ids.add(archive.getArchive());
        return ids;
    }

    static void includeDefaultChanges(Map<String,List<SnapshotRow>> before,Map<String,List<SnapshotRow>> after,
            Map<String,SnapshotRow> oldRelations,Map<String,SnapshotRow> newRelations) throws BusinessException {
        EsignValues.require(oldRelations.keySet().equals(newRelations.keySet()),
            "原生银行账户操作改变了其他账户的关系范围，业务必须回滚。");
        for(SnapshotRow old:oldRelations.values()) {
            SnapshotRow current=newRelations.get(old.getRowId());
            if(old.getValues().equals(current.getValues())) continue;
            String accountClass=old.getValues().get("accclass");
            String archive=key("1".equals(accountClass)?"BD_CUSTOMER_BANK":"3".equals(accountClass)?"BD_SUPPLIER_BANK":"",
                old.getParentRowId());
            EsignValues.require(after.containsKey(archive) && sameOutsideDefault(old,current),
                "原生银行账户操作修改了未经授权的其他账户关系，业务必须回滚。");
            List<SnapshotRow> previous=before.get(archive);
            if(previous==null) { previous=new ArrayList<SnapshotRow>();before.put(archive,previous); }
            previous.add(old);
            after.get(archive).add(current);
        }
    }

    static boolean sameOutsideDefault(SnapshotRow before,SnapshotRow after) {
        Map<String,String> previous=new LinkedHashMap<String,String>(before.getValues());
        Map<String,String> current=new LinkedHashMap<String,String>(after.getValues());
        for(String field:new String[]{"isdefault","pk_oldcustbank","ts"}) {
            previous.remove(field);
            current.remove(field);
        }
        return previous.equals(current);
    }

    static void requireOnlyDefaultChanges(Map<String,List<SnapshotRow>> before,Map<String,List<SnapshotRow>> after)
            throws BusinessException {
        for(Map.Entry<String,List<SnapshotRow>> archive:before.entrySet()) {
            Map<String,SnapshotRow> current=new LinkedHashMap<String,SnapshotRow>();
            for(SnapshotRow row:after.get(archive.getKey())) current.put(key(row.getEntity(),row.getRowId()),row);
            for(SnapshotRow old:archive.getValue()) {
                SnapshotRow row=current.remove(key(old.getEntity(),old.getRowId()));
                EsignValues.require(row!=null && ("bd_custbank".equals(old.getEntity())?sameOutsideDefault(old,row)
                    :old.getValues().equals(row.getValues())),"设置默认账户改变了其他数据，业务必须回滚。");
            }
            EsignValues.require(current.isEmpty(),"设置默认账户产生了未经授权的新行，业务必须回滚。");
        }
    }

    static void capture(OperationContext context,BankAccountImpactScope.Selection previous,AuditCaptureScope scope,
            Map<String,List<SnapshotRow>> before,Map<String,List<SnapshotRow>> after) throws BusinessException {
        if(!scope.isEnabled()) return;
        Map<String,String> keys=new LinkedHashMap<String,String>();
        if(previous!=null) for(BankAccountImpactScope.AffectedArchive archive:previous.getArchives())
            for(String relation:archive.getRelations()) keys.put(relation,key(archive.getDomain(),archive.getArchive()));
        for(BusinessObjectRef ref:context.getObjects()) {
            if(!scope.isEnabledFor(ref.getClientObjectId())) continue;
            String archive=previous==null?key(ref.getObjectType(),ref.getObjectPk()):keys.get(ref.getObjectPk());
            List<SnapshotRow> old=before.get(archive),current=after.get(archive);
            if(old!=null && !old.isEmpty()) scope.before(ref.getClientObjectId(),old);
            if(current!=null && !current.isEmpty()) scope.after(ref.getClientObjectId(),current);
        }
    }

    static String key(String domain,String pk) { return domain+"\u0000"+pk; }
}
