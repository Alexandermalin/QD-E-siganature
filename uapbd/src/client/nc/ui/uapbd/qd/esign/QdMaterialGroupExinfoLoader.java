package nc.ui.uapbd.qd.esign;

import nc.ui.bd.uitabextend.ExinfoLoader;
import nc.vo.bd.uitabextend.UITabExtVO;

/** Keeps the native extension catalog and replaces only the verified group material save configurations. */
public final class QdMaterialGroupExinfoLoader extends ExinfoLoader {
    private boolean candidatePagesEnabled;
    private boolean candidatePageDeletesEnabled;

    /** Set only when the corresponding candidate routes have been installed for site validation. */
    public void setCandidatePagesEnabled(boolean value) { candidatePagesEnabled = value; }
    public void setCandidatePageDeletesEnabled(boolean value) { candidatePageDeletesEnabled = value; }

    @Override
    public UITabExtVO[] queryExInfos() {
        UITabExtVO[] extensions = super.queryExInfos();
        if (!"10140MAG".equals(getCurrent_md_ID())) {
            return extensions;
        }
        UITabExtVO[] result = extensions.clone();
        for (int index = 0; index < result.length; index++) {
            UITabExtVO entry = result[index];
            if ("10140MAG".equals(entry.getMetadataid())) {
                result[index] = replaceVerifiedPath(entry, candidatePagesEnabled, candidatePageDeletesEnabled);
            }
        }
        return result;
    }

    static UITabExtVO replaceVerifiedPath(UITabExtVO entry, boolean candidatePagesEnabled, boolean candidatePageDeletesEnabled) {
        String path;
        if ("material_fi".equals(entry.getExbean_name()) && "fi".equals(entry.getNodekey())
                && "nc/ui/bd/material/fi/material_fi_config.xml".equals(entry.getEx_configpath())) {
            path = candidatePageDeletesEnabled ? "nc/ui/uapbd/qd/esign/QdMaterialFiDeleteConfig.xml"
                : "nc/ui/uapbd/qd/esign/QdMaterialFiConfig.xml";
        } else if ("material_pu".equals(entry.getExbean_name()) && "pu".equals(entry.getNodekey())
                && "nc/ui/bd/material/pu/material_pu_config.xml".equals(entry.getEx_configpath())) {
            path = candidatePageDeletesEnabled ? "nc/ui/uapbd/qd/esign/QdMaterialPuDeleteConfig.xml"
                : "nc/ui/uapbd/qd/esign/QdMaterialPuConfig.xml";
        } else if (candidatePagesEnabled && "material_sale".equals(entry.getExbean_name()) && "sale".equals(entry.getNodekey())
                && "nc/ui/bd/material/sale/material_sale_config.xml".equals(entry.getEx_configpath())) {
            path = candidatePageDeletesEnabled ? "nc/ui/uapbd/qd/esign/QdMaterialSaleDeleteConfig.xml"
                : "nc/ui/uapbd/qd/esign/QdMaterialSaleConfig.xml";
        } else if (candidatePagesEnabled && "material_stock".equals(entry.getExbean_name()) && "stock".equals(entry.getNodekey())
                && "nc/ui/bd/material/stock/material_stock_config.xml".equals(entry.getEx_configpath())) {
            path = candidatePageDeletesEnabled ? "nc/ui/uapbd/qd/esign/QdMaterialStockDeleteConfig.xml"
                : "nc/ui/uapbd/qd/esign/QdMaterialStockConfig.xml";
        } else if (candidatePagesEnabled && "material_plan".equals(entry.getExbean_name()) && "plan".equals(entry.getNodekey())
                && "nc/ui/bd/material/plan/material_plan_config.xml".equals(entry.getEx_configpath())) {
            path = candidatePageDeletesEnabled ? "nc/ui/uapbd/qd/esign/QdMaterialPlanDeleteConfig.xml"
                : "nc/ui/uapbd/qd/esign/QdMaterialPlanConfig.xml";
        } else if (candidatePagesEnabled && "material_cost".equals(entry.getExbean_name()) && "cost".equals(entry.getNodekey())
                && "nc/ui/bd/material/cost/material_cost_config.xml".equals(entry.getEx_configpath())) {
            path = candidatePageDeletesEnabled ? "nc/ui/uapbd/qd/esign/QdMaterialCostDeleteConfig.xml"
                : "nc/ui/uapbd/qd/esign/QdMaterialCostConfig.xml";
        } else if (candidatePagesEnabled && "material_prod".equals(entry.getExbean_name()) && "prod".equals(entry.getNodekey())
                && "nc/ui/bd/material/prod/material_prod_config.xml".equals(entry.getEx_configpath())) {
            path = candidatePageDeletesEnabled ? "nc/ui/uapbd/qd/esign/QdMaterialProdBothConfig.xml"
                : "nc/ui/uapbd/qd/esign/QdMaterialProdConfig.xml";
        } else if (candidatePageDeletesEnabled && "material_prod".equals(entry.getExbean_name()) && "prod".equals(entry.getNodekey())
                && "nc/ui/bd/material/prod/material_prod_config.xml".equals(entry.getEx_configpath())) {
            path = "nc/ui/uapbd/qd/esign/QdMaterialProdDeleteConfig.xml";
        } else {
            return entry;
        }
        UITabExtVO replacement = (UITabExtVO) entry.clone();
        replacement.setEx_configpath(path);
        return replacement;
    }
}
