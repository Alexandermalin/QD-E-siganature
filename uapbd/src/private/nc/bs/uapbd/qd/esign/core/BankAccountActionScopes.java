package nc.bs.uapbd.qd.esign.core;

import nc.vo.pub.BusinessException;

/** Formal catalog actions used when one physical account affects different partner domains. */
public final class BankAccountActionScopes {
    private BankAccountActionScopes() { }
    public static String scopeFor(String domain,String operation) throws BusinessException {
        boolean customer="BD_CUSTOMER_BANK".equals(domain),supplier="BD_SUPPLIER_BANK".equals(domain);
        EsignValues.require(customer || supplier,"银行账户影响范围包含未知档案类型。");
        if("INSERT".equals(operation) || "UPDATE".equals(operation)) return customer?"QD-ES-E153DD629D00947F":"QD-ES-E70DEE0145D79FDE";
        if("DELETE".equals(operation)) return customer?"QD-ES-70EC3A5C19555BD7":"QD-ES-00DF272BF31F0AA2";
        if("DISABLE".equals(operation)) return customer?"QD-ES-7493FA0F20E75AF0":"QD-ES-24C25407F1C36AF6";
        if("ENABLE".equals(operation)) return customer?"QD-ES-DDC46B3D9F641DFF":"QD-ES-B3A292BB1D2F3E84";
        throw new BusinessException("银行账户动作不属于正式操作清单。");
    }
}
