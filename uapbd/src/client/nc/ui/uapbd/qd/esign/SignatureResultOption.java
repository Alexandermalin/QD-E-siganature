package nc.ui.uapbd.qd.esign;

/** 签名结果筛选只表达身份确认事实，不表示业务保存结果。 */
enum SignatureResultOption {
    ALL(null, "全部"),
    SUCCESS("SUCCESS", "签名成功"),
    PASSWORD_INCORRECT("PASSWORD_INCORRECT", "密码错误"),
    ACCOUNT_LOCKED("ACCOUNT_LOCKED", "账号锁定"),
    ACCOUNT_UNAVAILABLE("ACCOUNT_UNAVAILABLE", "账号不可用"),
    CANCELLED("CANCELLED", "已取消"),
    EXPIRED("EXPIRED", "已过期");

    private final String code, label;
    SignatureResultOption(String code, String label) { this.code=code; this.label=label; }
    String code() { return code; }
    @Override public String toString() { return label; }
    static String display(Object value) {
        if (value == null) { return ""; }
        String raw=String.valueOf(value);
        for (SignatureResultOption option:values()) { if (raw.equals(option.code)) { return option.label; } }
        return raw;
    }
}
