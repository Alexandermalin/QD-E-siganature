package nc.vo.uapbd.qd.esign;

import java.io.Serializable;

/** Immutable selected identity and observed native version; all editable business fields are reloaded on the server. */
public final class BankAccountStateCommand implements Serializable {
    private static final long serialVersionUID=1L;
    private final String relation,account,version;
    public BankAccountStateCommand(String relation,String account,String version) {
        this.relation=relation;this.account=account;this.version=version;
    }
    public String getRelation() { return relation; }
    public String getAccount() { return account; }
    public String getVersion() { return version; }
}
