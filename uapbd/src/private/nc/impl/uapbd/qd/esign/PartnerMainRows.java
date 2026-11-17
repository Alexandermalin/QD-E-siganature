package nc.impl.uapbd.qd.esign;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import nc.bs.uapbd.qd.esign.audit.PhysicalSnapshotReader;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.vo.bd.cust.CustomerVO;
import nc.vo.bd.supplier.SupplierVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.pub.VOStatus;
import nc.vo.uapbd.qd.esign.PartnerMainSaveDraft;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;

/** Physical rows edited as part of the native customer and supplier main card. */
final class PartnerMainRows {
    private static final String[][] CUSTOMER={
        {"bd_custlinkman","pk_custlinkman"},{"bd_custvat","pk_custvat"},{"bd_custtaxtypes","pk_custtaxtypes"}
    };
    private static final String[][] SUPPLIER={
        {"bd_suplinkman","pk_suplinkman"},{"bd_supvat","pk_supvat"},{"bd_supcountrytaxes","pk_supcountrytaxes"}
    };

    private PartnerMainRows() { }

    static List<SnapshotRow> read(PhysicalSnapshotReader reader,boolean customer,String id,boolean lock) throws BusinessException {
        List<SnapshotRow> result=new ArrayList<SnapshotRow>();
        String parent=customer?"pk_customer":"pk_supplier";
        for(String[] table:customer?CUSTOMER:SUPPLIER) {
            result.addAll(lock?reader.readLocked(table[0],table[1],parent,parent,id,true)
                :reader.read(table[0],table[1],parent,parent,id,true));
        }
        return result;
    }

    static void prepare(SuperVO main,List<SnapshotRow> stored) throws BusinessException {
        Map<String,SnapshotRow> rows=new HashMap<String,SnapshotRow>();
        for(SnapshotRow row:stored) rows.put(row.getEntity()+"\u0000"+row.getRowId(),row);
        String pk=main.getPrimaryKey();
        if(main instanceof CustomerVO) {
            CustomerVO value=(CustomerVO)main;
            owned(value.getCustcontacts(),CUSTOMER[0][0],"pk_customer",pk,rows);
            owned(value.getCustvat(),CUSTOMER[1][0],"pk_customer",pk,rows);
            owned(value.getCusttaxtypes(),CUSTOMER[2][0],"pk_customer",pk,rows);
            requireUnchanged(value.getCustbanks(),value.getFinances(),value.getSales(),value.getCreditctrls());
            value.setCustbanks(null);
            value.setFinances(null);
            value.setSales(null);
            value.setCreditctrls(null);
        } else {
            SupplierVO value=(SupplierVO)main;
            owned(value.getSuplinkman(),SUPPLIER[0][0],"pk_supplier",pk,rows);
            owned(value.getSupvat(),SUPPLIER[1][0],"pk_supplier",pk,rows);
            owned(value.getSupcountrytaxes(),SUPPLIER[2][0],"pk_supplier",pk,rows);
            requireUnchanged(value.getSupbankacc(),value.getSupfinance(),value.getSupstock());
            value.setSupbankacc(null);
            value.setSupfinance(null);
            value.setSupstock(null);
        }
    }

    private static void owned(SuperVO[] children,String table,String parentColumn,String parent,Map<String,SnapshotRow> stored)
            throws BusinessException {
        if(children==null) return;
        Set<String> seen=new HashSet<String>();
        for(SuperVO child:children) {
            EsignValues.require(child!=null,"主档子行数据不完整。");
            String parentId=PartnerMainSaveDraft.normalizeParent((String)child.getAttributeValue(parentColumn));
            EsignValues.require(parentId==null || parentId.equals(parent),"主档子行属于其他档案。");
            String id=child.getPrimaryKey();
            if(id==null) {
                EsignValues.require(child.getStatus()==VOStatus.NEW,"新增主档子行缺少原生新增状态。");
                continue;
            }
            SnapshotRow row=stored.get(table+"\u0000"+id);
            EsignValues.require(row!=null && seen.add(id) && child.getStatus()!=VOStatus.NEW,
                "主档子行不存在或重复。");
            if(child.getStatus()!=VOStatus.UNCHANGED) {
                Object version=child.getAttributeValue("ts");
                String expected=row.getValues().get("ts");
                EsignValues.require(expected==null?version==null:version!=null && expected.equals(version.toString()),
                    "主档子行版本已变化，请刷新后再保存。");
            }
        }
    }

    /** The main-card RPC must not carry changes intended for separate bank and organization actions. */
    private static void requireUnchanged(SuperVO[]... pages) throws BusinessException {
        for(SuperVO[] page:pages) if(page!=null) for(SuperVO row:page)
            EsignValues.require(row!=null && row.getPrimaryKey()!=null && row.getStatus()==VOStatus.UNCHANGED,
                "银行账户及组织页签的改动需从对应功能保存。");
    }
}
