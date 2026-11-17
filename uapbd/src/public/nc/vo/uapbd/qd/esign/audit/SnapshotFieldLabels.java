package nc.vo.uapbd.qd.esign.audit;

/** Stable labels for verified NC material columns without a database column comment. */
public final class SnapshotFieldLabels {
    private SnapshotFieldLabels() { }

    public static String known(String table, String code) {
        if (code == null) { return null; }
        if ("__parent__".equals(code)) { return "父数据行"; }
        if ("bd_materialconvert".equalsIgnoreCase(table)) {
            switch (code) {
                case "pk_materialconvert": return "换算定义主键";
                case "pk_material": return "物料基本信息主键";
                case "pk_measdoc": return "计量单位名称";
                case "pk_apartmeasdoc": return "拆解单位名称";
                case "measrate": return "主单位/辅单位";
                case "fixedflag": return "固定换算";
                case "ispiecemangage": return "件数管理";
                case "isprodmeasdoc": return "生产默认单位";
                case "ispumeasdoc": return "采购默认单位";
                case "isretailmeasdoc": return "零售默认单位";
                case "issalemeasdoc": return "销售默认单位";
                case "isstockmeasdoc": return "库存默认单位";
                case "isstorebalance": return "结存";
                case "showorder": return "辅计量拆解显示顺序号";
                default: return null;
            }
        }
        if ("bd_material".equalsIgnoreCase(table)) {
            switch (code) {
                case "pk_material": return "物料主键";
                case "code": return "物料编码";
                case "name": return "物料名称";
                case "pk_org": return "所属组织";
                case "pk_group": return "所属集团";
                case "pk_marbasclass": return "物料分类";
                case "materialspec": return "规格";
                case "materialtype": return "型号";
                case "pk_measdoc": return "主计量单位";
                case "enablestate": return "启用状态";
                default: return null;
            }
        }
        return null;
    }
}
