package nc.vo.uapbd.qd.esign;

import java.io.Serializable;

/** Draft fields that determine which production rows the native save can modify. */
public final class MaterialProdImpact implements Serializable {
    private static final long serialVersionUID=1L;
    private final String targetOrg,costClass;
    private final boolean classFeature;
    public MaterialProdImpact(String targetOrg,String costClass,boolean classFeature) {
        this.targetOrg=targetOrg;this.costClass=costClass;this.classFeature=classFeature;
    }
    public String getTargetOrg() { return targetOrg; }
    public String getCostClass() { return costClass; }
    public boolean isClassFeature() { return classFeature; }
}
