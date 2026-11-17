package nc.bs.uapbd.qd.esign.audit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.bs.uapbd.qd.esign.core.PartnerMainSaveImpact;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;

/** Candidate physical main/shared/structure snapshots for each affected customer or supplier. */
public final class PartnerMainSaveSnapshots {
    private PartnerMainSaveSnapshots() { }
    public interface Reader {
        List<SnapshotRow> read(String table,String key,String value,String scope,String scopeValue,boolean active,boolean lock)
            throws BusinessException;
    }
    public static Map<String,List<SnapshotRow>> capture(String group,PartnerMainSaveImpact.Selection selection,
            Map<String,String> afterOwners,boolean after,Reader reader,boolean lock) throws BusinessException {
        EsignValues.require(group!=null && selection!=null && !selection.getArchives().isEmpty() && afterOwners!=null && reader!=null,
            "客户或供应商快照缺少影响范围。");
        for(String override:afterOwners.keySet()) {
            String selected=selection.getSelected();
            boolean present=false;for(PartnerMainSaveImpact.Archive item:selection.getArchives())
                if(override.equals(item.domain+"\u0000"+item.id)) present=true;
            EsignValues.require(after && (override.equals("BD_CUSTOMER\u0000"+selected) || override.equals("BD_SUPPLIER\u0000"+selected))
                && present && afterOwners.get(override)!=null,"客户或供应商快照包含非法的所属组织变更。");
        }
        Map<String,List<SnapshotRow>> result=new LinkedHashMap<String,List<SnapshotRow>>();
        for(PartnerMainSaveImpact.Archive archive:selection.getArchives()) {
            boolean customer="BD_CUSTOMER".equals(archive.domain);
            EsignValues.require(customer || "BD_SUPPLIER".equals(archive.domain),"客户或供应商快照档案类型无效。");
            String identity=archive.domain+"\u0000"+archive.id,rootTable=customer?"bd_customer":"bd_supplier",
                rootKey=customer?"pk_customer":"pk_supplier";
            List<SnapshotRow> roots=reader.read(rootTable,rootKey,archive.id,"pk_group",group,true,lock);
            String owner=afterOwners.containsKey(identity)?afterOwners.get(identity):archive.owner;
            EsignValues.require(roots!=null && roots.size()==1 && roots.get(0)!=null && rootTable.equals(roots.get(0).getEntity())
                && archive.id.equals(roots.get(0).getRowId()) && archive.id.equals(roots.get(0).getValues().get(rootKey))
                && group.equals(roots.get(0).getValues().get("pk_group")) && owner.equals(roots.get(0).getValues().get("pk_org"))
                && (after || archive.code.equals(roots.get(0).getValues().get("code"))),"客户或供应商主档、集团或所属组织已变化。");
            List<SnapshotRow> rows=new ArrayList<SnapshotRow>(roots);
            String structureTable=customer?"bd_custstructure":"bd_supstructure";
            List<SnapshotRow> structure=reader.read(structureTable,rootKey,archive.id,null,null,false,lock);
            EsignValues.require(structure!=null && structure.size()<=1,"客户或供应商结构行读取不完整。");
            if(!structure.isEmpty()) {
                SnapshotRow row=structure.get(0);
                EsignValues.require(row!=null && structureTable.equals(row.getEntity()) && archive.id.equals(row.getRowId())
                    && archive.id.equals(row.getValues().get(rootKey)),"客户或供应商结构行主键不一致。");
                rows.add(new SnapshotRow(row.getEntity(),row.getRowId(),row.getValues().get("pk_parent"),row.getValues(),row.getTypes(),row.getLabels(),true));
            }
            if(selection.getSelected().equals(archive.id)) {
                List<SnapshotRow> shared=reader.read("bd_cust_supplier","pk_cust_sup",archive.id,"pk_group",group,true,lock);
                EsignValues.require(shared!=null && shared.size()==1 && shared.get(0)!=null && "bd_cust_supplier".equals(shared.get(0).getEntity())
                    && archive.id.equals(shared.get(0).getRowId()) && archive.id.equals(shared.get(0).getValues().get("pk_cust_sup"))
                    && group.equals(shared.get(0).getValues().get("pk_group")),"客户供应商共享主档不存在或不属于当前集团。");
                SnapshotRow row=shared.get(0);rows.add(new SnapshotRow(row.getEntity(),row.getRowId(),archive.id,
                    row.getValues(),row.getTypes(),row.getLabels(),true));
            }
            EsignValues.require(result.put(identity,Collections.unmodifiableList(rows))==null,"客户或供应商快照档案重复。");
        }
        return Collections.unmodifiableMap(result);
    }
    public static Reader database() {
        return new Reader() {
            private final PhysicalSnapshotReader reader=new PhysicalSnapshotReader();
            public List<SnapshotRow> read(String table,String key,String value,String scope,String scopeValue,boolean active,boolean lock)
                    throws BusinessException {
                return lock?reader.readLocked(table,key,null,key,value,scope,scopeValue,active)
                    :reader.read(table,key,null,key,value,scope,scopeValue,active);
            }
        };
    }
}
