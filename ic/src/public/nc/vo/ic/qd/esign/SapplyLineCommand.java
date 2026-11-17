package nc.vo.ic.qd.esign;

import java.io.Serializable;

/** The row identities are carried by the authorized request, not by an untrusted bill payload. */
public final class SapplyLineCommand implements Serializable {
    private static final long serialVersionUID=1L;
    private final boolean resumeBudget;
    public SapplyLineCommand(boolean resumeBudget) { this.resumeBudget=resumeBudget; }
    public boolean isResumeBudget() { return resumeBudget; }
}
