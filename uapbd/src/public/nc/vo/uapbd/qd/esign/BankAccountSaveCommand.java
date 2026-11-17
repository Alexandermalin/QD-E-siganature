package nc.vo.uapbd.qd.esign;

import java.io.Serializable;
import nc.vo.bd.bankaccount.cust.CustBankaccUnionVO;

/**
 * 银行账户保存（新增或修改）的类型化载荷。
 * 保存涉及账户主档、币种、使用组织及关系行的可编辑内容，载荷保留原生联合 VO；
 * 服务端重新加载并核对所属组织、版本与影响范围，不信任客户端策略归属。
 */
public final class BankAccountSaveCommand implements Serializable {
    private static final long serialVersionUID=1L;

    /** 原生弹窗组装的联合数据：账户主档与所选档案的关系行。 */
    private final CustBankaccUnionVO union;
    /** 客户端观察到的账户原生版本（ts），修改时必须与锁定行一致。 */
    private final String version;
    private final String defaultCurrency;

    public BankAccountSaveCommand(CustBankaccUnionVO union,String version) {
        this(union,version,null);
    }

    public BankAccountSaveCommand(CustBankaccUnionVO union,String version,String defaultCurrency) {
        if (union == null) { throw new IllegalArgumentException("Bank account union is required"); }
        this.union = union;
        this.version = version;
        this.defaultCurrency = defaultCurrency;
    }

    public CustBankaccUnionVO getUnion() { return union; }
    public String getVersion() { return version; }
    public String getDefaultCurrency() { return defaultCurrency; }
}
