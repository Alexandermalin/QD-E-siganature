package nc.bs.uapbd.qd.esign.core;

import java.lang.reflect.Method;
import nc.bs.bd.assignservice.AssignBaseService;
import nc.bs.framework.common.InvocationInfo;
import nc.bs.framework.common.InvocationInfoProxy;

/** 核对本机 NC 分配工作线程的实际上下文复制规则，不调用服务或数据库。 */
public final class NativeAssignmentContextTests {
    private NativeAssignmentContextTests() { }

    public static void run() throws Exception {
        final Throwable[] failures = new Throwable[1];
        Thread request = new Thread(new Runnable() {
            public void run() {
                try {
                    InvocationInfoProxy proxy = InvocationInfoProxy.getInstance();
                    proxy.setBizDateTime(1L);
                    proxy.setCallId("QD_OFFLINE_CALL_ID");
                    proxy.setGroupId("G"); proxy.setUserId("U");
                    proxy.setProperty("QD_OFFLINE_ONLY", "NOT_INHERITED");
                    Method snapshot = AssignBaseService.class.getDeclaredMethod("getInvocationInfo");
                    snapshot.setAccessible(true);
                    final InvocationInfo info = (InvocationInfo)snapshot.invoke(null);
                    final Method restore = AssignBaseService.class.getDeclaredMethod("setInvocationInfoWithLogUtilPK",
                            InvocationInfo.class, String.class);
                    restore.setAccessible(true);
                    for (int i = 0; i < 2; i++) {
                        Thread worker = new Thread(new Runnable() {
                            public void run() {
                                try {
                                    restore.invoke(null, info, "NATIVE_LOG_KEY");
                                    InvocationInfoProxy current = InvocationInfoProxy.getInstance();
                                    check("QD_OFFLINE_CALL_ID".equals(current.getCallId()), "callId is copied");
                                    check("G".equals(current.getGroupId()) && "U".equals(current.getUserId()),
                                            "group and user are copied");
                                    check(current.getProperty("QD_OFFLINE_ONLY") == null,
                                            "arbitrary request properties are not copied");
                                    check("NATIVE_LOG_KEY".equals(current.getProperty("assignerrorlogutilkey")),
                                            "only the explicit native error-log property is restored");
                                } catch (Throwable failure) { failures[0] = failure; }
                            }
                        });
                        worker.start(); worker.join();
                        if (failures[0] != null) { return; }
                    }
                } catch (Throwable failure) { failures[0] = failure; }
            }
        });
        request.start(); request.join();
        if (failures[0] != null) { throw new Exception("Native assignment context probe failed", failures[0]); }
        System.out.println("PASS NativeAssignmentContextTests (two offline NC worker contexts; no NC server or transaction)");
    }

    private static void check(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
    }
}
