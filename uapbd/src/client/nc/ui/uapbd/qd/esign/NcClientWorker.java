package nc.ui.uapbd.qd.esign;

import java.awt.Container;
import java.util.concurrent.ExecutionException;
import javax.swing.SwingWorker;
import nc.bs.framework.common.InvocationInfoProxy;
import nc.bs.framework.comn.NetStreamContext;
import nc.ui.pub.beans.MessageDialog;

/**
 * 携带每任务原生调用身份的 SwingWorker，在后台执行远程调用并在界面线程处理结果；执行完毕恢复线程身份，不以静态字段共享业务上下文。
 */
abstract class NcClientWorker<T> extends SwingWorker<T, Void> {
    /** parent：后台任务提示所用的父容器。 */
    private final Container parent;
    /** source：任务创建时捕获的原生调用身份。 */
    private final Identity source = new Identity();

    /** 保存提示所用的父容器，并通过字段初始化捕获创建任务时的原生身份，供后台线程调用及最终恢复。 */
    NcClientWorker(Container parent) { this.parent = parent; }
    /** 在携带原生身份的后台线程执行本次远程调用，异常交由统一完成回调处理。 */
    protected abstract T callService() throws Exception;
    /** 在 Swing 界面线程处理成功结果并更新本次请求对应的控件或表格。 */
    protected abstract void completed(T result);
    /** 在界面线程处理后台失败并提示用户，不把失败结果当作成功数据展示。 */
    protected void failed(Throwable error) {
        MessageDialog.showErrorDlg(parent, "电子签名", error.getMessage() == null
                ? "请求未完成，请查看系统异常记录。" : error.getMessage());
    }
    /** 无论成功或失败都执行的结束钩子，用于清理凭据、解除忙碌状态和刷新按钮可用性。 */
    protected void settled() { }

    /** 捕获后台线程原身份、应用任务身份后执行调用，并在 finally 恢复原线程上下文。 */
    @Override
    protected final T doInBackground() throws Exception {
        Identity previous = new Identity();
        try {
            source.apply();
            return callService();
        } finally {
            previous.apply();
            source.token = null;
            previous.token = null;
        }
    }

    /** 在界面线程取回后台结果，解包异常并调用成功或失败处理，最后始终执行 settled。 */
    @Override
    protected final void done() {
        try {
            completed(get());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            failed(error);
        } catch (ExecutionException error) {
            failed(error.getCause() == null ? error : error.getCause());
        } catch (RuntimeException error) {
            failed(error);
        } finally {
            settled();
        }
    }

    /** 单个后台任务的原生身份快照，临时持有传输所需字段，后台调用结束后恢复原线程身份；令牌引用不得进入业务 DTO 或日志。 */
    private static final class Identity {
        /**
         * user：原生调用用户主键。
         * userCode：NC 用户编码。
         * group：NC 集团身份字段。
         * groupNumber：原生调用身份中的集团编码。
         * dataSource：当前 NC 数据源名称。
         * language：原生调用语言代码。
         * center：原生业务中心编码。
         * zone：原生身份时区配置。
         * runAs：原生代理执行身份。
         * industry：原生行业上下文。
         * action：原生调用动作标识。
         * device：原生设备标识。
         */
        private final String user, userCode, group, groupNumber, dataSource, language, center, zone,
                runAs, industry, action, device;
        /** sysId：原生系统标识。 */
        private final byte sysId;
        /** businessDate：原生业务日期的时间值。 */
        private final long businessDate;
        /** token：任务期间临时持有的原生传输令牌引用，结束后清除字段引用，不写入业务 DTO 或日志。 */
        private byte[] token;
        /** 从当前原生调用上下文读取身份字段及传输令牌引用，仅供本任务后台调用与最终恢复使用。 */
        Identity() {
            InvocationInfoProxy info = InvocationInfoProxy.getInstance();
            user = info.getUserId(); userCode = info.getUserCode(); group = info.getGroupId();
            groupNumber = info.getGroupNumber(); dataSource = info.getUserDataSource();
            language = info.getLangCode(); center = info.getBizCenterCode();
            zone = info.getTimeZone(); sysId = info.getSysid();
            runAs = info.getRunAs(); industry = info.getHyCode(); action = info.getBusiAction();
            device = info.getDeviceId(); businessDate = info.getBizDateTimeForRMI();
            // 原生令牌只用于平台传输，不进入业务 DTO 或日志。
            token = NetStreamContext.getToken();
        }
        /** 把此身份快照应用到当前线程的原生调用上下文，供 RPC 正确识别调用者。 */
        void apply() {
            InvocationInfoProxy info = InvocationInfoProxy.getInstance();
            info.setUserId(user); info.setUserCode(userCode); info.setGroupId(group);
            info.setGroupNumber(groupNumber); info.setUserDataSource(dataSource);
            info.setLangCode(language); info.setBizCenterCode(center); info.setTimeZone(zone);
            info.setRunAs(runAs); info.setHyCode(industry); info.setBusiAction(action);
            info.setDeviceId(device); info.setBizDateTime(businessDate);
            info.setSysid(sysId); NetStreamContext.setToken(token);
        }
    }
}
