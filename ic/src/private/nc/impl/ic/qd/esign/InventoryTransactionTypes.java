package nc.impl.ic.qd.esign;

import java.sql.ResultSet;
import java.sql.SQLException;
import nc.bs.dao.BaseDAO;
import nc.bs.dao.DAOException;
import nc.jdbc.framework.SQLParameter;
import nc.jdbc.framework.processor.BaseProcessor;
import nc.vo.ic.general.define.ICBillHeadVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.billtype.BilltypeVO;
import nc.vo.uapbd.qd.esign.InventoryBillScope;
import nc.vo.uapbd.qd.esign.OperationContext;

/** Validates a transaction subtype against locked server-side NC metadata. */
final class InventoryTransactionTypes {
    interface Lookup { BilltypeVO lock(String pk) throws BusinessException; }
    interface Query { Object execute(String sql, SQLParameter args, BaseProcessor processor) throws DAOException; }

    private InventoryTransactionTypes() { }

    static void require(OperationContext context, String objectType, String billType, ICBillHeadVO head,
            boolean draft, Lookup lookup) throws BusinessException {
        String expected = InventoryBillRoute.nativeBillType(objectType);
        String supplied = clean(billType);
        check(context != null && clean(context.getGroupId()) != null && clean(context.getNodeCode()) != null
                && expected != null && objectType.equals(InventoryBillScope.objectType(context.getNodeCode()))
                && supplied != null && supplied.equals(billType) && head != null,
                "Inventory transaction type context is incomplete");
        if (InventoryBillScope.OUTSOURCE_IN.equals(objectType)) {
            check("45-Cxx-08".equals(supplied), "Outsourced receipt requires its registered transaction type");
        } else if (InventoryBillScope.PRODUCTION_ORDER.equals(objectType)) {
            check("46-Cxx-2".equals(supplied), "Production order requires its registered transaction type");
        }
        boolean base = expected.equals(supplied);
        if (base) {
            check(draft ? InventoryBillRoute.matchesNewBillType(objectType, supplied, head)
                    : InventoryBillRoute.matchesBillType(objectType, supplied, head),
                    "Inventory PF transaction type differs from the bill");
        } else {
            check(supplied.startsWith(expected + "-") && supplied.length() > expected.length() + 1
                    && supplied.length() <= 100, "Inventory transaction subtype is invalid");
            String headType = clean(head.getVtrantypecode());
            check(draft && headType == null || supplied.equals(headType),
                    "Inventory PF transaction type differs from the bill");
        }
        String typePk = clean(head.getCtrantypeid());
        if (base && typePk == null) return;
        check(typePk != null && lookup != null, "Inventory transaction type identity is missing");
        BilltypeVO actual = lookup.lock(typePk);
        String actualNode = actual == null ? null : clean(actual.getNodecode());
        String actualParent = actual == null ? null : clean(actual.getParentbilltype());
        check(actual != null && typePk.equals(clean(actual.getPrimaryKey()))
                && supplied.equals(clean(actual.getPk_billtypecode()))
                && context.getGroupId().equals(clean(actual.getPk_group()))
                && (base && actualNode == null || context.getNodeCode().equals(actualNode))
                && (base && actualParent == null || expected.equals(actualParent))
                && component(objectType).equals(clean(actual.getComponent()))
                && (actual.getDr() == null || actual.getDr().intValue() == 0),
                "Inventory transaction subtype is not authorized for this node and group");
    }

    static BilltypeVO loadLocked(String pk) throws BusinessException {
        return loadLocked(pk, new Query() {
            public Object execute(String sql, SQLParameter args, BaseProcessor processor) throws DAOException {
                return new BaseDAO().executeQuery(sql, args, processor);
            }
        });
    }

    static BilltypeVO loadLocked(String pk, Query query) throws BusinessException {
        check(clean(pk) != null && pk.equals(clean(pk)) && query != null,
                "Inventory transaction subtype identity is invalid");
        SQLParameter args = new SQLParameter();
        args.addParam(pk);
        Object value = query.execute("SELECT pk_billtypeid,pk_billtypecode,pk_group,nodecode,"
                + "parentbilltype,component,dr FROM bd_billtype WHERE pk_billtypeid=? FOR UPDATE NOWAIT", args,
                new BaseProcessor() {
                    private static final long serialVersionUID = 1L;
                    public Object processResultSet(ResultSet rs) throws SQLException {
                        if (!rs.next()) return null;
                        BilltypeVO row = new BilltypeVO();
                        row.setPrimaryKey(rs.getString(1));
                        row.setPk_billtypecode(rs.getString(2));
                        row.setPk_group(rs.getString(3));
                        row.setNodecode(rs.getString(4));
                        row.setParentbilltype(rs.getString(5));
                        row.setComponent(rs.getString(6));
                        int dr = rs.getInt(7);
                        row.setDr(rs.wasNull() ? null : Integer.valueOf(dr));
                        if (rs.next()) throw new SQLException("Duplicate inventory transaction subtype identity");
                        return row;
                    }
                });
        check(value == null || value instanceof BilltypeVO, "Invalid inventory transaction subtype result");
        return (BilltypeVO) value;
    }

    private static String component(String type) throws BusinessException {
        if (InventoryBillScope.PURCHASE_IN.equals(type)) return "ic_purchasein";
        if (InventoryBillScope.SALE_OUT.equals(type)) return "ic_saleout";
        if (InventoryBillScope.TRANS_IN.equals(type)) return "ic_transin";
        if (InventoryBillScope.INITIAL_IN.equals(type)) return "ic_initializtionin";
        if (InventoryBillScope.LOC_ADJUST.equals(type)) return "ic_locadjust";
        if (InventoryBillScope.GENERAL_IN.equals(type)) return "ic_generalin";
        if (InventoryBillScope.PRODUCT_IN.equals(type)) return "ic_finprodin";
        if (InventoryBillScope.SUBCONT_IN.equals(type)) return "ic_subcontin";
        if (InventoryBillScope.GENERAL_OUT.equals(type)) return "ic_generalout";
        if (InventoryBillScope.MATERIAL_OUT.equals(type)) return "ic_materialout";
        if (InventoryBillScope.TRANS_OUT.equals(type)) return "ic_transout";
        if (InventoryBillScope.OUTSOURCE_IN.equals(type)) return "ic_purchasein";
        if (InventoryBillScope.PRODUCTION_ORDER.equals(type)) return "ic_finprodin";
        if (InventoryBillScope.SAPPLY.equals(type)) return "ic_sapply";
        throw new BusinessException("Unsupported inventory transaction subtype");
    }

    static boolean sameIdentity(ICBillHeadVO first, ICBillHeadVO second) {
        if (first == null || second == null) return false;
        return sameIdentity(identity(first), second);
    }

    static boolean sameIdentity(String expected, ICBillHeadVO head) {
        if (head == null) return false;
        String actual = identity(head);
        return expected == null ? actual == null : expected.equals(actual);
    }

    static String identity(ICBillHeadVO head) {
        return head == null ? null : clean(head.getCtrantypeid());
    }

    private static String clean(String value) {
        if (value == null) return null;
        String result = value.trim();
        return result.isEmpty() || "~".equals(result) ? null : result;
    }

    private static void check(boolean condition, String message) throws BusinessException {
        if (!condition) throw new BusinessException(message);
    }
}
