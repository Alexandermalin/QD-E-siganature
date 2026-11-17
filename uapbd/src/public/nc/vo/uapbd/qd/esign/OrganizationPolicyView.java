package nc.vo.uapbd.qd.esign;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 一次查询返回的组织总控、操作权限和按钮策略。 */
public final class OrganizationPolicyView implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String orgId;
    private final boolean masterEnabled;
    private final long controlVersion;
    private final boolean canSavePolicies;
    private final boolean canSaveMaster;
    private final List<ButtonPolicyRow> rows;

    public OrganizationPolicyView(String orgId, boolean masterEnabled, long controlVersion,
                                  boolean canSavePolicies, boolean canSaveMaster, List<ButtonPolicyRow> rows) {
        this.orgId = orgId;
        this.masterEnabled = masterEnabled;
        this.controlVersion = controlVersion;
        this.canSavePolicies = canSavePolicies;
        this.canSaveMaster = canSaveMaster;
        this.rows = Collections.unmodifiableList(new ArrayList<ButtonPolicyRow>(rows));
    }

    public String getOrgId() { return orgId; }
    public boolean isMasterEnabled() { return masterEnabled; }
    public long getControlVersion() { return controlVersion; }
    public boolean canSavePolicies() { return canSavePolicies; }
    public boolean canSaveMaster() { return canSaveMaster; }
    public List<ButtonPolicyRow> getRows() { return rows; }
}
