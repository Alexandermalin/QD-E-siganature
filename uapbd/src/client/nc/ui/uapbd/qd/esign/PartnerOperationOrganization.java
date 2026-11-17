package nc.ui.uapbd.qd.esign;

import nc.vo.pub.BusinessException;

/** The business unit explicitly selected in the native archive query. */
public interface PartnerOperationOrganization {
    String requireOperationOrganization() throws BusinessException;
}
