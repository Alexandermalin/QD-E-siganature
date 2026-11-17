package nc.impl.ic.qd.esign;

import java.util.Collections;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.SQLException;
import nc.bs.dao.DAOException;
import nc.jdbc.framework.SQLParameter;
import nc.jdbc.framework.processor.BaseProcessor;
import nc.vo.ic.general.define.ICBillHeadVO;
import nc.vo.ic.m45.entity.PurchaseInHeadVO;
import nc.vo.ic.m40.entity.InitializtionInHeadVO;
import nc.vo.ic.m4c.entity.SaleOutHeadVO;
import nc.vo.ic.m4e.entity.TransInHeadVO;
import nc.vo.ic.m4q.entity.LocAdjustHeadVO;
import nc.vo.ic.m46.entity.FinProdInHeadVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.billtype.BilltypeVO;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;

/** Validates node-specific transaction types and the locked metadata query without a database. */
public final class InventoryTransactionTypesTests {
    public static void run() throws Exception {
        final OperationContext context = context("40080602", "G", "IC_PURCHASE_IN");
        final ICBillHeadVO custom = head("45-Cxx-08", "TYPE-PK");
        final Fixture lookup = new Fixture(row("TYPE-PK", "45-Cxx-08", "G", "40080602", "45", "ic_purchasein", 0));
        InventoryTransactionTypes.require(context, "IC_PURCHASE_IN", "45-Cxx-08", custom, false, lookup);
        require(lookup.calls == 1 && "TYPE-PK".equals(lookup.pk), "Subtype must use the header's locked metadata identity");
        InventoryTransactionTypes.require(context, "IC_PURCHASE_IN", "45-Cxx-08", head(null, "TYPE-PK"), true, lookup);
        reject(context, "IC_PURCHASE_IN", "45-Cxx-08", head(null, "TYPE-PK"), false, lookup);
        reject(context, "IC_PURCHASE_IN", "45-Cxx-08", head("45-Cxx-08", null), false, lookup);
        reject(context, "IC_PURCHASE_IN", "45-Cxx-08", head("45-OTHER", "TYPE-PK"), false, lookup);
        reject(context, "IC_PURCHASE_IN", "45-", custom, false, lookup);
        reject(context, "IC_PURCHASE_IN", " 45-Cxx-08", custom, false, lookup);
        reject(context("40080802", "G", "IC_PURCHASE_IN"), "IC_PURCHASE_IN", "45-Cxx-08", custom, false, lookup);

        lookup.row = row("TYPE-PK", "45-Cxx-08", "G", "40080602400", "45", "ic_purchasein", 0);
        reject(context, "IC_PURCHASE_IN", "45-Cxx-08", custom, false, lookup);
        lookup.row = row("TYPE-PK", "45-Cxx-08", "OTHER", "40080602", "45", "ic_purchasein", 0);
        reject(context, "IC_PURCHASE_IN", "45-Cxx-08", custom, false, lookup);
        lookup.row = row("TYPE-PK", "45-Cxx-08", "G", "40080602", "46", "ic_purchasein", 0);
        reject(context, "IC_PURCHASE_IN", "45-Cxx-08", custom, false, lookup);
        lookup.row = row("TYPE-PK", "45-Cxx-08", "G", "40080602", "45", "ic_other", 0);
        reject(context, "IC_PURCHASE_IN", "45-Cxx-08", custom, false, lookup);
        lookup.row = row("TYPE-PK", "45-Cxx-08", "G", "40080602", "45", "ic_purchasein", 1);
        reject(context, "IC_PURCHASE_IN", "45-Cxx-08", custom, false, lookup);
        lookup.row = row("OTHER-PK", "45-Cxx-08", "G", "40080602", "45", "ic_purchasein", 0);
        reject(context, "IC_PURCHASE_IN", "45-Cxx-08", custom, false, lookup);
        lookup.row = row("TYPE-PK", "45-OTHER", "G", "40080602", "45", "ic_purchasein", 0);
        reject(context, "IC_PURCHASE_IN", "45-Cxx-08", custom, false, lookup);
        lookup.row = null;
        reject(context, "IC_PURCHASE_IN", "45-Cxx-08", custom, false, lookup);

        lookup.row = row("TYPE-PK", "45-Cxx-08", "G", "40080602400", "45", "ic_purchasein", 0);
        reject(context, "IC_PURCHASE_IN", "45", head("45", "TYPE-PK"), false, lookup);
        lookup.row = row("TYPE-PK", "45", "G", null, null, "ic_purchasein", 0);
        InventoryTransactionTypes.require(context, "IC_PURCHASE_IN", "45", head("45", "TYPE-PK"), false, lookup);
        int before = lookup.calls;
        InventoryTransactionTypes.require(context, "IC_PURCHASE_IN", "45", head("45", null), false, lookup);
        require(before == lookup.calls, "The native base type without a type key must not query subtype metadata");
        reject(context, "IC_PURCHASE_IN", "45", head(null, null), false, lookup);
        InventoryTransactionTypes.require(context, "IC_PURCHASE_IN", "45", head(null, null), true, lookup);

        OperationContext outsourced = context("40080602400", "G", "IC_OUTSOURCE_IN");
        lookup.row = row("TYPE-PK", "45-Cxx-08", "G", "40080602400", "45", "ic_purchasein", 0);
        InventoryTransactionTypes.require(outsourced, "IC_OUTSOURCE_IN", "45-Cxx-08", custom, false, lookup);
        InventoryTransactionTypes.require(outsourced, "IC_OUTSOURCE_IN", "45-Cxx-08", head(null, "TYPE-PK"), true, lookup);
        reject(outsourced, "IC_OUTSOURCE_IN", "45-Cxx-08", head("45-Cxx-08", null), false, lookup);
        reject(outsourced, "IC_OUTSOURCE_IN", "45", head("45", null), true, lookup);
        reject(outsourced, "IC_OUTSOURCE_IN", "45-OTHER", head("45-OTHER", "TYPE-PK"), false, lookup);
        lookup.row = row("TYPE-PK", "45-Cxx-08", "G", "40080602", "45", "ic_purchasein", 0);
        reject(outsourced, "IC_OUTSOURCE_IN", "45-Cxx-08", custom, false, lookup);
        reject(context, "IC_PURCHASE_IN", "45-Cxx-08", custom, false,
                new Fixture(row("TYPE-PK", "45-Cxx-08", "G", "40080602400", "45", "ic_purchasein", 0)));
        OperationContext production = context("40080604400", "G", "IC_PRODUCTION_ORDER");
        ICBillHeadVO productionHead = new FinProdInHeadVO() {
            public String getVtrantypecode() { return "46-Cxx-2"; }
            public String getCtrantypeid() { return "TYPE-PK"; }
        };
        lookup.row = row("TYPE-PK", "46-Cxx-2", "G", "40080604400", "46", "ic_finprodin", 0);
        InventoryTransactionTypes.require(production, "IC_PRODUCTION_ORDER", "46-Cxx-2", productionHead, false, lookup);
        reject(production, "IC_PRODUCTION_ORDER", "46", productionHead, false, lookup);
        reject(production, "IC_PRODUCTION_ORDER", "46-OTHER", productionHead, false, lookup);
        lookup.row = row("TYPE-PK", "46-Cxx-2", "OTHER", "40080604400", "46", "ic_finprodin", 0);
        reject(production, "IC_PRODUCTION_ORDER", "46-Cxx-2", productionHead, false, lookup);

        OperationContext initial = context("40080402", "G", "IC_INITIAL_IN");
        ICBillHeadVO initialHead = new InitializtionInHeadVO() {
            public String getVtrantypecode() { return "40-TEST"; }
            public String getCtrantypeid() { return "TYPE-PK"; }
        };
        lookup.row = row("TYPE-PK", "40-TEST", "G", "40080402", "40", "ic_initializtionin", 0);
        InventoryTransactionTypes.require(initial, "IC_INITIAL_IN", "40-TEST", initialHead, false, lookup);
        lookup.row = row("TYPE-PK", "40-TEST", "G", "40080402", "40", "ic_openbal", 0);
        reject(initial, "IC_INITIAL_IN", "40-TEST", initialHead, false, lookup);
        OperationContext transfer = context("40080618", "G", "IC_TRANS_IN");
        ICBillHeadVO transferHead = new TransInHeadVO() {
            public String getVtrantypecode() { return "4E-TEST"; }
            public String getCtrantypeid() { return "TYPE-PK"; }
        };
        lookup.row = row("TYPE-PK", "4E-TEST", "G", "40080618", "4E", "ic_transin", 0);
        InventoryTransactionTypes.require(transfer, "IC_TRANS_IN", "4E-TEST", transferHead, false, lookup);
        OperationContext location = context("40081014", "G", "IC_LOC_ADJUST");
        ICBillHeadVO locationHead = new LocAdjustHeadVO() {
            public String getVtrantypecode() { return "4Q-TEST"; }
            public String getCtrantypeid() { return "TYPE-PK"; }
        };
        lookup.row = row("TYPE-PK", "4Q-TEST", "G", "40081014", "4Q", "ic_locadjust", 0);
        InventoryTransactionTypes.require(location, "IC_LOC_ADJUST", "4Q-TEST", locationHead, false, lookup);
        lookup.row = row("TYPE-PK", "4Q-TEST", "G", "40081014", "4Q", "ic_transin", 0);
        reject(location, "IC_LOC_ADJUST", "4Q-TEST", locationHead, false, lookup);
        OperationContext sale = context("40080802", "G", "IC_SALE_OUT");
        ICBillHeadVO saleHead = new SaleOutHeadVO() {
            public String getVtrantypecode() { return "4C-TEST"; }
            public String getCtrantypeid() { return "TYPE-PK"; }
        };
        lookup.row = row("TYPE-PK", "4C-TEST", "G", "40080802", "4C", "ic_saleout", 0);
        InventoryTransactionTypes.require(sale, "IC_SALE_OUT", "4C-TEST", saleHead, false, lookup);

        final BilltypeVO metadata = row("TYPE-PK", "45-Cxx-08", "G", "40080602", "45", "ic_purchasein", 0);
        BilltypeVO loaded = InventoryTransactionTypes.loadLocked("TYPE-PK", new InventoryTransactionTypes.Query() {
            public Object execute(String sql, SQLParameter args, BaseProcessor processor) throws DAOException {
                require(sql.contains("pk_billtypeid=?") && sql.endsWith("FOR UPDATE NOWAIT"), "Metadata query must lock the exact row");
                require(!sql.contains("TYPE-PK") && args.getCountParams() == 1 && "TYPE-PK".equals(args.get(0)),
                        "Metadata identity must be bound as a parameter");
                require(processor != null, "Metadata row processor is required");
                return metadata;
            }
        });
        require(loaded == metadata, "Locked metadata result was changed");
        BilltypeVO parsed = InventoryTransactionTypes.loadLocked("TYPE-PK", resultQuery(1));
        require(parsed != null && "TYPE-PK".equals(parsed.getPrimaryKey())
                && "45-Cxx-08".equals(parsed.getPk_billtypecode()) && "40080602".equals(parsed.getNodecode())
                && Integer.valueOf(0).equals(parsed.getDr()), "Locked metadata columns were not read correctly");
        require(InventoryTransactionTypes.loadLocked("TYPE-PK", resultQuery(0)) == null,
                "A missing metadata row must remain missing");
        try { InventoryTransactionTypes.loadLocked("TYPE-PK", resultQuery(2));
            throw new AssertionError("Duplicate locked metadata rows were accepted"); }
        catch (BusinessException expected) { }
        try { InventoryTransactionTypes.loadLocked("TYPE-PK", new InventoryTransactionTypes.Query() {
            public Object execute(String sql, SQLParameter args, BaseProcessor processor) { return "wrong type"; }
        }); throw new AssertionError("Invalid metadata result type was accepted"); }
        catch (BusinessException expected) { }
        try { InventoryTransactionTypes.loadLocked("TYPE-PK", new InventoryTransactionTypes.Query() {
            public Object execute(String sql, SQLParameter args, BaseProcessor processor) throws DAOException {
                throw new DAOException("fixture: lock unavailable");
            }
        }); throw new AssertionError("A metadata lock failure was ignored"); }
        catch (BusinessException expected) { }
        try {
            InventoryTransactionTypes.loadLocked(" TYPE-PK", new InventoryTransactionTypes.Query() {
                public Object execute(String sql, SQLParameter args, BaseProcessor processor) { throw new AssertionError("Malformed key reached query"); }
            });
            throw new AssertionError("Malformed metadata key accepted");
        } catch (BusinessException expected) { }
        System.out.println("PASS InventoryTransactionTypesTests (node/group subtype validation and parameterized locking; no Oracle)");
    }

    private static OperationContext context(String node, String group, String type) {
        return new OperationContext("R", "B", group, "O", node, "inventory", "Save", "Save", "UPDATE", null,
                Collections.singletonList(new BusinessObjectRef("item", type, "A", "CODE", null)));
    }
    private static ICBillHeadVO head(final String type, final String pk) {
        return new PurchaseInHeadVO() {
            public String getVtrantypecode() { return type; }
            public String getCtrantypeid() { return pk; }
        };
    }
    private static InventoryTransactionTypes.Query resultQuery(final int count) {
        return new InventoryTransactionTypes.Query() {
            public Object execute(String sql, SQLParameter args, BaseProcessor processor) throws DAOException {
                ResultSet rows = (ResultSet) Proxy.newProxyInstance(ResultSet.class.getClassLoader(),
                        new Class<?>[] { ResultSet.class }, new InvocationHandler() {
                            int index;
                            public Object invoke(Object proxy, Method method, Object[] args) {
                                if ("next".equals(method.getName())) return Boolean.valueOf(index++ < count);
                                if ("getString".equals(method.getName())) {
                                    String[] values = { "TYPE-PK", "45-Cxx-08", "G", "40080602", "45", "ic_purchasein" };
                                    return values[((Integer) args[0]).intValue() - 1];
                                }
                                if ("getInt".equals(method.getName())) return Integer.valueOf(0);
                                if ("wasNull".equals(method.getName())) return Boolean.FALSE;
                                throw new AssertionError("Unexpected result-set method " + method.getName());
                            }
                        });
                try { return processor.processResultSet(rows); }
                catch (SQLException failure) { throw new DAOException("Invalid metadata result", failure); }
            }
        };
    }
    private static BilltypeVO row(String pk, String code, String group, String node, String parent, String component, int dr) {
        BilltypeVO row = new BilltypeVO(); row.setPrimaryKey(pk); row.setPk_billtypecode(code); row.setPk_group(group);
        row.setNodecode(node); row.setParentbilltype(parent); row.setComponent(component); row.setDr(Integer.valueOf(dr));
        return row;
    }
    private static void reject(OperationContext context, String type, String billType, ICBillHeadVO head,
            boolean draft, Fixture lookup) throws Exception {
        try { InventoryTransactionTypes.require(context, type, billType, head, draft, lookup); throw new AssertionError("Invalid transaction type accepted"); }
        catch (BusinessException expected) { }
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static final class Fixture implements InventoryTransactionTypes.Lookup {
        BilltypeVO row; int calls; String pk;
        Fixture(BilltypeVO row) { this.row = row; }
        public BilltypeVO lock(String pk) { this.pk = pk; calls++; return row; }
    }
}
