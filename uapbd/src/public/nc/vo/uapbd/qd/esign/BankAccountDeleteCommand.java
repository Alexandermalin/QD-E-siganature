package nc.vo.uapbd.qd.esign;

import java.io.Serializable;
import nc.vo.bd.bankaccount.cust.CustBankaccUnionVO;

/**
 * 银行账户删除的类型化载荷，指向所选档案与账户的关系；
 * 共享账户只删除本档案关系，账户主档是否删除由原生按剩余关系决定，服务端逐行核对结果。
 */
public final class BankAccountDeleteCommand implements Serializable {
    private static final long serialVersionUID=1L;

    /** 原生弹窗组装的联合数据：待删除的关系行与账户主档。 */
    private final CustBankaccUnionVO union;
    /** 客户端观察到的账户原生版本（ts）。 */
    private final String version;

    public BankAccountDeleteCommand(CustBankaccUnionVO union,String version) {
        if (union == null) { throw new IllegalArgumentException("Bank account union is required"); }
        this.union = union;
        this.version = version;
    }

    public CustBankaccUnionVO getUnion() { return union; }
    public String getVersion() { return version; }
}
