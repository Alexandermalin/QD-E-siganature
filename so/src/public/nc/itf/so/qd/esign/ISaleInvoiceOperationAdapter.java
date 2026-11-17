package nc.itf.so.qd.esign;

import nc.itf.uapbd.qd.esign.spi.BusinessOperationAdapter;

/**
 * 销售发票维护（节点 40060501，正式清单第 239–244 行）的固定服务端委托接口。
 *
 * <p>保存/删除/提交/收回/审批/取消审批保留原生 PF 链路与组合校验器；本批不新增独立开票或红冲按钮，
 * 也不改造既有自动接口。若原生按钮会触发外部不可回滚副作用，追踪调用与事务边界前该分支保持关闭。</p>
 */
public interface ISaleInvoiceOperationAdapter extends BusinessOperationAdapter {
}
