package nc.bs.uapbd.qd.esign.core;

/** 服务实现与离线测试共用的纯状态规则，分别判断是否可认证、可执行业务及业务状态是否已结束。 */
public final class RequestRules {
    /** 工具类不提供实例，使用静态方法完成本类职责。 */
    private RequestRules(){}
    /** 仅当签名和业务都为 PENDING 时允许本次认证，结束后的请求不能重复尝试。 */
    public static boolean mayAuthenticate(String signature,String business){return "PENDING".equals(signature)&&"PENDING".equals(business);}
    /** 归属策略只要求事由时，不验证密码，也不能把该状态当作签名成功。 */
    public static boolean mayConfirmReason(boolean ownerPolicy,String signature,String business){return ownerPolicy&&"NOT_REQUIRED".equals(signature)&&"PENDING".equals(business);}
    /** 仅当签名成功或无需签名，且业务为 READY 时允许认领执行。 */
    public static boolean mayExecute(String signature,String business){return ("SUCCESS".equals(signature)||"NOT_REQUIRED".equals(signature))&&"READY".equals(business);}
    /** 判断业务状态是否为成功、失败、取消或过期终态，供结束时间及归档规则使用。 */
    public static boolean terminal(String business){return "SUCCESS".equals(business)||"PARTIAL".equals(business)||"FAILED".equals(business)||"CANCELLED".equals(business)||"EXPIRED".equals(business);}
}
