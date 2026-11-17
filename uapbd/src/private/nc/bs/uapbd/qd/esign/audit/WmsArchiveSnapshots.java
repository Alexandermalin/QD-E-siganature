package nc.bs.uapbd.qd.esign.audit;

import java.util.Collections;
import java.util.List;
import nc.bs.dao.BaseDAO;
import nc.jdbc.framework.SQLParameter;
import nc.jdbc.framework.processor.ColumnProcessor;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;

/** Physical WMS delivery rows written by the installed QDNC archive triggers. */
public final class WmsArchiveSnapshots {
    private WmsArchiveSnapshots() { }

    public static List<SnapshotRow> read(PhysicalSnapshotReader reader,String domain,String pk,boolean lock) throws BusinessException {
        String trigger,table,parent;
        if("BD_CUSTOMER".equals(domain)) { trigger="BD_CUSTOMER_TRG";table="customerljpwms";parent="pk_customer"; }
        else if("BD_SUPPLIER".equals(domain)) { trigger="BD_SUPPLIER_TRG";table="vendorljpwms";parent="pk_supplier"; }
        else if("BD_STORDOC".equals(domain)) { trigger="BD_STORDOC_TRG";table="warehouseljpwms";parent="pk_stordoc"; }
        else return Collections.emptyList();
        SQLParameter params=new SQLParameter();
        params.addParam(trigger);
        Object status=new BaseDAO().executeQuery("SELECT status FROM user_triggers WHERE trigger_name=?",params,new ColumnProcessor());
        if(!"ENABLED".equals(status)) return Collections.emptyList();
        return lock?reader.readLocked(table,"id",parent,parent,pk,false):reader.read(table,"id",parent,parent,pk,false);
    }
}
