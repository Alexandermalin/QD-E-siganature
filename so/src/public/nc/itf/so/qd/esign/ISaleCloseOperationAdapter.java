package nc.itf.so.qd.esign;

import nc.itf.uapbd.qd.esign.spi.BusinessOperationAdapter;

/**
 * 销售订单关闭管理（节点 40060303，正式清单第 217–228 行）的固定服务端委托接口。
 *
 * <p>仅接受 12 项已核实按钮的类型化命令；行动作按订单与行主键授权，整单动作按订单主键授权。
 * 原生落点固定为 {@code nc.itf.so.m30.closemanage.ISaleOrderCloseManageMaintain} 的对应方法，
 * 客户端不能传入类名、方法名或 SQL。</p>
 */
public interface ISaleCloseOperationAdapter extends BusinessOperationAdapter {
}
