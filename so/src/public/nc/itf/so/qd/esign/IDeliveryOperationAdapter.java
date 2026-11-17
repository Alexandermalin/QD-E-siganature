package nc.itf.so.qd.esign;

import nc.itf.uapbd.qd.esign.spi.BusinessOperationAdapter;

/**
 * 发货单维护（节点 40060402，正式清单第 229–238 行）的固定服务端委托接口。
 *
 * <p>保存/删除/提交/收回/审批/取消审批保留原生 PF 链路；整单关闭/打开与出库行关闭/打开
 * 固定委托 {@code nc.itf.so.m4331.IDeliveryAssitFunc}。行动作必须由服务端重新核对行索引与行主键，
 * 不能按表格行号授权；发货单与销售订单即使按钮同名也不共享动作授权。</p>
 */
public interface IDeliveryOperationAdapter extends BusinessOperationAdapter {
}
