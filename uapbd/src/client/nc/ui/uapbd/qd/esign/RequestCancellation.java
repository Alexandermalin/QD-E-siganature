package nc.ui.uapbd.qd.esign;

import nc.itf.uapbd.qd.esign.ISignatureService;

/** 窗口与所属拦截器共用的一次取消 RPC 尝试，避免关闭窗口和生命周期清理重复发出取消请求，并保留取消失败供调用方获知。 */
final class RequestCancellation {
    /** service：当前使用的服务实例或测试服务替身。 */
    private final ISignatureService service;
    /** requestId：贯通准备、签名、执行及日志关联的请求标识。 */
    private final String requestId;
    /** attempted：是否已尝试发出取消请求，失败也不自动重复调用。 */
    private boolean attempted;
    /** failure：最近一次失败原因，保留原始异常。 */
    private Exception failure;

    /** 保存本次请求及签名服务，让窗口和外层控制器共享同一个取消尝试状态。 */
    RequestCancellation(ISignatureService service, String requestId) {
        this.service = service;
        this.requestId = requestId;
    }

    /** 同一协调器最多尝试一次取消 RPC；重复调用返回或重抛已保存的失败，不重复发送请求。 */
    void cancel(String reason) throws Exception {
        if (!attempted) {
            attempted = true;
            try {
                service.cancel(requestId, reason);
            } catch (Exception error) {
                failure = error;
                throw error;
            }
        } else if (failure != null) {
            throw failure;
        }
    }
}
