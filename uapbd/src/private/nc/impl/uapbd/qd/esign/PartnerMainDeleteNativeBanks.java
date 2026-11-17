package nc.impl.uapbd.qd.esign;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import nc.bs.dao.BaseDAO;
import nc.bs.uapbd.qd.esign.audit.PartnerMainDeleteBanks;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.vo.bd.cust.CustbankVO;
import nc.vo.bd.cust.CustomerVO;
import nc.vo.bd.supplier.SupplierVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;

/** Builds the bank array that the native master-delete service uses to delete account masters. */
final class PartnerMainDeleteNativeBanks {
    private PartnerMainDeleteNativeBanks() { }
    interface Loader { CustbankVO load(String relation) throws BusinessException; }

    static void prepare(SuperVO archive,PartnerMainDeleteBanks.Selection scope,Loader loader) throws BusinessException {
        boolean customer=archive!=null && archive.getClass()==CustomerVO.class;
        EsignValues.require((customer || archive!=null && archive.getClass()==SupplierVO.class) && scope!=null && loader!=null
            && (customer?"BD_CUSTOMER":"BD_SUPPLIER").equals(scope.getDomain())
            && scope.getArchive().equals(archive.getPrimaryKey()) && scope.getGroup().equals(archive.getAttributeValue("pk_group")),
            "原生删除的主档与银行账户授权范围不一致。");
        List<CustbankVO> rows=new ArrayList<CustbankVO>();
        for(Map.Entry<String,String> entry:scope.getExclusiveRelations().entrySet()) {
            CustbankVO row=loader.load(entry.getValue());
            EsignValues.require(row!=null && entry.getValue().equals(row.getPrimaryKey())
                && entry.getKey().equals(row.getAttributeValue("pk_bankaccbas"))
                && scope.getArchive().equals(row.getAttributeValue("pk_cust"))
                && Integer.valueOf(customer?1:3).equals(row.getAttributeValue("accclass")),"银行账户关系已变化，无法安全删除主档。");
            rows.add(row);
        }
        CustbankVO[] safe=rows.toArray(new CustbankVO[rows.size()]);
        if(customer) ((CustomerVO)archive).setCustbanks(safe);else ((SupplierVO)archive).setSupbankacc(safe);
    }

    static Loader database() {
        return new Loader() {
            public CustbankVO load(String relation) throws BusinessException {
                return (CustbankVO)new BaseDAO().retrieveByPK(CustbankVO.class,relation);
            }
        };
    }
}
