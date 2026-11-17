package nc.itf.pu.qd.esign;

import nc.itf.uapbd.qd.esign.spi.BusinessOperationAdapter;

/** 采购订单本地适配服务契约，只允许电子签名网关授权执行业务，不作为绕过网关的远程入口。 */
public interface IPurchaseOrderOperationAdapter extends BusinessOperationAdapter { }
