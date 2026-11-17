package nc.bs.uapbd.qd.esign.audit;

import java.io.IOException;
import java.io.Reader;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.xml.bind.DatatypeConverter;
import nc.bs.dao.BaseDAO;
import nc.bs.dao.DAOException;
import nc.jdbc.framework.SQLParameter;
import nc.jdbc.framework.processor.BaseProcessor;
import nc.jdbc.framework.processor.ResultSetProcessor;
import nc.md.model.ITable;

import nc.vo.uapbd.qd.esign.audit.SnapshotFieldLabels;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;

/** Reads physical columns of an adapter-selected business table using the calling NC transaction. */
public final class PhysicalSnapshotReader {
    interface QueryExecutor {
        Object execute(String sql,SQLParameter parameters,ResultSetProcessor processor) throws DAOException;
    }
    private final QueryExecutor query;
    private final Map<String, Map<String, String>> projections = new HashMap<String, Map<String, String>>();
    private final Set<String> sapplyVerifiedCharColumns = new TreeSet<String>();

    public PhysicalSnapshotReader() {
        this(new QueryExecutor() {
            private final BaseDAO dao=new BaseDAO();
            public Object execute(String sql,SQLParameter parameters,ResultSetProcessor processor) throws DAOException {
                return dao.executeQuery(sql,parameters,processor);
            }
        });
    }
    PhysicalSnapshotReader(QueryExecutor query) {
        if(query==null) throw new IllegalArgumentException("Physical snapshot query executor is required");
        this.query=query;
    }

    public List<SnapshotRow> readByPk(String table, String pkColumn, String pk) throws DAOException {
        return read(table, pkColumn, null, pkColumn, pk, true);
    }

    public List<SnapshotRow> read(String table, String keyColumn, String parentColumn,
            String matchColumn, String matchValue, boolean activeOnly) throws DAOException {
        return read(table, keyColumn, parentColumn, matchColumn, matchValue, null, null, activeOnly);
    }

    public List<SnapshotRow> read(String table, String keyColumn, String parentColumn,
            String matchColumn, String matchValue, String scopeColumn, String scopeValue, boolean activeOnly) throws DAOException {
        return read(table, keyColumn, parentColumn, matchColumn, matchValue, scopeColumn, scopeValue, activeOnly, false);
    }

    public List<SnapshotRow> readLocked(String table, String keyColumn, String parentColumn,
            String matchColumn, String matchValue, boolean activeOnly) throws DAOException {
        return read(table, keyColumn, parentColumn, matchColumn, matchValue, null, null, activeOnly, true);
    }

    public List<SnapshotRow> readLocked(String table, String keyColumn, String parentColumn,
            String matchColumn, String matchValue, String scopeColumn, String scopeValue, boolean activeOnly) throws DAOException {
        return read(table, keyColumn, parentColumn, matchColumn, matchValue, scopeColumn, scopeValue, activeOnly, true);
    }

    /** Read only explicitly approved inventory columns and reject unclassified schema additions. */
    public List<SnapshotRow> readInventory(String table, String keyColumn, String parentColumn,
            String billPk, boolean lock) throws DAOException {
        Set<String> allowed = InventoryAuditFields.forTable(table);
        String matched=billPk;
        if("ic_sapply_h".equals(table) || "ic_sapply_b".equals(table)) {
            requireSapplyCharColumn(table,"cgeneralhid");
            requireSapplyCharColumn(table,"ic_sapply_h".equals(table)?"pk_org":"cgeneralbid");
            matched=paddedSapplyKey(billPk);
        }
        return read(table, keyColumn, parentColumn, "cgeneralhid", matched, null, null, true, lock, false, allowed);
    }

    /** Capture only batch records referenced by authorized bill rows, within the same group. */
    public List<SnapshotRow> readInventoryBatchReferences(List<String> keys,String group,boolean lock) throws DAOException {
        return readInventoryBatchReferences(keys,group,lock,false);
    }
    List<SnapshotRow> readSapplyBatchReferences(List<String> keys,String group,boolean lock) throws DAOException {
        return readInventoryBatchReferences(keys,group,lock,true);
    }
    private List<SnapshotRow> readInventoryBatchReferences(List<String> keys,String group,boolean lock,boolean sapply) throws DAOException {
        if(keys==null || group==null || group.trim().isEmpty() || !group.equals(group.trim()))
            throw new IllegalArgumentException("Inventory batch references require a group and key list");
        Set<String> unique=new TreeSet<String>();
        for(String key:keys) if(key==null || key.trim().isEmpty() || !key.equals(key.trim())
                || "~".equals(key) || (sapply && !key.matches("[A-Za-z0-9_-]{1,20}")) || !unique.add(key))
            throw new IllegalArgumentException("Invalid or duplicate inventory batch reference");
        List<String> sorted=new ArrayList<String>(unique);List<SnapshotRow> result=new ArrayList<SnapshotRow>();
        if(sapply && !sorted.isEmpty())requireSapplyCharColumn("scm_batchcode","pk_batchcode");
        for(int start=0;start<sorted.size();start+=900) {
            int end=Math.min(start+900,sorted.size());
            List<String> chunk=new ArrayList<String>();
            for(String key:sorted.subList(start,end))chunk.add(sapply?paddedSapplyKey(key):key);
            // A referenced row may already be logically deleted; capture its actual state as evidence.
            List<SnapshotRow> part=readMany("scm_batchcode","pk_batchcode",null,"pk_batchcode",chunk,
                    "pk_group",group,false,lock,true,InventoryAuditFields.forTable("scm_batchcode"));
            if(part==null) throw new DAOException("Inventory batch reference query returned no row set");
            for(SnapshotRow row:part) {
                if(row==null)throw new DAOException("Inventory batch reference row is missing");
                result.add(!sapply?row:new SnapshotRow(row.getEntity(),rtrimChar(row.getRowId()),row.getParentRowId(),
                        row.getValues(),row.getTypes(),row.getLabels(),row.hasPhysicalColumns()));
            }
        }
        return result;
    }

    /** Read only extension rows belonging to body identities already scoped to the authorized bill. */
    public List<SnapshotRow> readSapplyExtensions(List<String> keys,boolean lock) throws DAOException {
        if(keys==null)throw new IllegalArgumentException("Outbound-request body keys are required");
        Set<String> unique=new TreeSet<String>();
        for(String key:keys) if(key==null || key.trim().isEmpty() || !key.equals(key.trim()) || !unique.add(key))
            throw new IllegalArgumentException("Invalid or duplicate outbound-request body key");
        List<String> sorted=new ArrayList<String>(unique);List<SnapshotRow> result=new ArrayList<SnapshotRow>();
        if(!sorted.isEmpty())requireSapplyCharColumn("ic_sapply_e","cgeneralbid");
        for(int start=0;start<sorted.size();start+=900) {
            int end=Math.min(start+900,sorted.size());
            List<String> padded=new ArrayList<String>();
            for(String key:sorted.subList(start,end))padded.add(paddedSapplyKey(key));
            List<SnapshotRow> part=readMany("ic_sapply_e","cgeneralbid","cgeneralbid","cgeneralbid",
                    padded,null,null,false,lock,false,InventoryAuditFields.forTable("ic_sapply_e"));
            if(part==null)throw new DAOException("Outbound-request extension query returned no row set");
            result.addAll(part);
        }
        return result;
    }

    private void requireSapplyCharColumn(String table,String column) throws DAOException {
        String identity=table+"."+column;if(sapplyVerifiedCharColumns.contains(identity))return;
        SQLParameter parameters=new SQLParameter();parameters.addParam(table.toUpperCase(Locale.ENGLISH));parameters.addParam(column.toUpperCase(Locale.ENGLISH));
        Object value=query.execute("SELECT data_type,char_length FROM user_tab_columns WHERE table_name=? AND column_name=?",
                parameters,new BaseProcessor(){
            private static final long serialVersionUID=1L;
            public Object processResultSet(ResultSet rs) throws SQLException {
                if(!rs.next())return Boolean.FALSE;
                boolean valid="CHAR".equals(rs.getString(1)) && rs.getInt(2)==20 && !rs.wasNull();
                if(rs.next())throw new SQLException("Ambiguous outbound-request WMS key metadata");
                return Boolean.valueOf(valid);
            }
        });
        if(!Boolean.TRUE.equals(value))throw new DAOException("Outbound-request identity is not the reviewed CHAR(20): "+identity);
        sapplyVerifiedCharColumns.add(identity);
    }

    private static String paddedSapplyKey(String value) {
        if(value==null || !value.matches("[A-Za-z0-9_-]{1,20}"))throw new IllegalArgumentException("Invalid outbound-request key");
        StringBuilder padded=new StringBuilder(value);while(padded.length()<20)padded.append(' ');return padded.toString();
    }
    private static String rtrimChar(String value) {
        if(value==null)return null;int end=value.length();while(end>0 && value.charAt(end-1)==' ')end--;
        return value.substring(0,end);
    }

    /** Reads a table selected from the server's NC metadata and linked by the caller's verified association. */
    public List<SnapshotRow> readMetadata(ITable table,String parentColumn,String matchColumn,String matchValue,
            boolean lock) throws DAOException {
        if(table==null || table.getPrimaryKeyName()==null) throw new IllegalArgumentException("Metadata table and primary key required");
        return read(table.getName(),table.getPrimaryKeyName(),parentColumn,matchColumn,matchValue,null,null,false,lock,true);
    }

    private List<SnapshotRow> read(String table, String keyColumn, String parentColumn, String matchColumn, String matchValue,
            String scopeColumn, String scopeValue, boolean activeOnly, boolean lock) throws DAOException {
        return read(table,keyColumn,parentColumn,matchColumn,matchValue,scopeColumn,scopeValue,activeOnly,lock,false);
    }

    private List<SnapshotRow> read(String table, String keyColumn, String parentColumn, String matchColumn, String matchValue,
            String scopeColumn, String scopeValue, boolean activeOnly, boolean lock, boolean metadataTable) throws DAOException {
        return read(table, keyColumn, parentColumn, matchColumn, matchValue, scopeColumn, scopeValue,
                activeOnly, lock, metadataTable, null);
    }

    private List<SnapshotRow> read(String table, String keyColumn, String parentColumn, String matchColumn, String matchValue,
            String scopeColumn, String scopeValue, boolean activeOnly, boolean lock, boolean metadataTable,
            Set<String> allowedColumns) throws DAOException {
        return readMany(table,keyColumn,parentColumn,matchColumn,Collections.singletonList(matchValue),scopeColumn,
                scopeValue,activeOnly,lock,metadataTable,allowedColumns);
    }

    private List<SnapshotRow> readMany(String table, String keyColumn, String parentColumn, String matchColumn,
            List<String> matchValues, String scopeColumn, String scopeValue, boolean activeOnly, boolean lock,
            boolean metadataTable, Set<String> allowedColumns) throws DAOException {
        final String entity = identifier(table);
        final String key = identifier(keyColumn);
        final String parent = parentColumn == null ? null : identifier(parentColumn);
        String match = identifier(matchColumn);
        String scope = scopeColumn == null ? null : identifier(scopeColumn);
        if ((!metadataTable && !businessTable(entity)) || matchValues == null || matchValues.isEmpty() || matchValues.size()>900
                || (scope != null && (scopeValue == null || scopeValue.length() == 0))) {
            throw new IllegalArgumentException("Physical snapshot requires a scoped business row query");
        }
        for(String value:matchValues) if(value==null || value.length()==0)
            throw new IllegalArgumentException("Physical snapshot match value is missing");
        final Map<String, String> labels = restrictColumns(entity, projection(entity), allowedColumns);
        Set<String> columns = labels.keySet();
        if (!columns.contains(key) || !columns.contains(match) || (parent != null && !columns.contains(parent))
                || (scope != null && !columns.contains(scope))) {
            throw new DAOException("Physical snapshot row mapping differs from table " + entity);
        }
        StringBuilder sql = new StringBuilder("SELECT ");
        for (String column : columns) {
            if (sql.length() > "SELECT ".length()) { sql.append(','); }
            sql.append('"').append(column.toUpperCase(Locale.ENGLISH)).append('"');
        }
        sql.append(" FROM ").append(entity).append(" WHERE ").append(match);
        SQLParameter args = new SQLParameter();
        if(matchValues.size()==1) sql.append("=?");
        else {
            sql.append(" IN (");
            for(int i=0;i<matchValues.size();i++) { if(i>0)sql.append(',');sql.append('?'); }
            sql.append(')');
        }
        for(String value:matchValues) args.addParam(value);
        if (scope != null) { sql.append(" AND ").append(scope).append("=?"); args.addParam(scopeValue); }
        if (activeOnly && columns.contains("dr")) { sql.append(" AND NVL(dr,0)=0"); }
        sql.append(" ORDER BY ").append(key);
        if (lock) { sql.append(" FOR UPDATE NOWAIT"); }
        @SuppressWarnings("unchecked") List<SnapshotRow> rows = (List<SnapshotRow>) query.execute(sql.toString(), args,
                new BaseProcessor() {
                    private static final long serialVersionUID = 1L;
                    public Object processResultSet(ResultSet rs) throws SQLException {
                        List<SnapshotRow> result = new ArrayList<SnapshotRow>();
                        ResultSetMetaData md = rs.getMetaData();
                        int count = md.getColumnCount();
                        Map<String, String> types = new HashMap<String, String>();
                        String[] fields = new String[count];
                        int[] kinds = new int[count];
                        for (int i = 0; i < count; i++) {
                            fields[i] = md.getColumnLabel(i + 1).toLowerCase(Locale.ENGLISH);
                            kinds[i] = md.getColumnType(i + 1);
                            types.put(fields[i], md.getColumnTypeName(i + 1));
                        }
                        while (rs.next()) {
                            Map<String, String> values = new HashMap<String, String>();
                            for (int i = 0; i < count; i++) { values.put(fields[i], value(rs, i + 1, kinds[i])); }
                            if (values.get(key) == null || (parent != null && values.get(parent) == null)) {
                                throw new SQLException("Physical snapshot row is missing its identity in " + entity);
                            }
                            result.add(new SnapshotRow(entity, values.get(key), values.get(parent), values, types, labels, true));
                        }
                        return result;
                    }
                });
        return rows;
    }

    static Map<String, String> restrictColumns(String table, Map<String, String> available,
            Set<String> allowed) throws DAOException {
        if (allowed == null) return available;
        Map<String, String> selected = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> column : available.entrySet()) {
            if (!allowed.contains(column.getKey())) {
                throw new DAOException("Inventory audit column requires review: " + table + "." + column.getKey());
            }
            selected.put(column.getKey(), column.getValue());
        }
        return selected;
    }

    private Map<String, String> projection(final String table) throws DAOException {
        Map<String, String> existing = projections.get(table);
        if (existing != null) { return existing; }
        SQLParameter args = new SQLParameter();
        args.addParam(table.toUpperCase(Locale.ENGLISH));
        @SuppressWarnings("unchecked") Map<String, String> columns = (Map<String, String>) query.execute(
                "SELECT c.column_name,m.comments FROM user_tab_columns c LEFT JOIN user_col_comments m"
                    + " ON m.table_name=c.table_name AND m.column_name=c.column_name WHERE c.table_name=? ORDER BY c.column_id",
                args, new BaseProcessor() {
                    private static final long serialVersionUID = 1L;
                    public Object processResultSet(ResultSet rs) throws SQLException {
                        Map<String, String> names = new LinkedHashMap<String, String>();
                        while (rs.next()) {
                            String name = rs.getString(1);
                            if (SnapshotSupport.sensitive(name)) { continue; }
                            String code = identifier(name);
                            String label = rs.getString(2);
                            if (label == null || label.trim().isEmpty() || label.length() > 200) {
                                label = SnapshotFieldLabels.known(table, code);
                            }
                            names.put(code, label);
                        }
                        return names;
                    }
                });
        if (columns.isEmpty()) { throw new DAOException("Physical snapshot table metadata not found: " + table); }
        projections.put(table, columns);
        return columns;
    }

    private static String value(ResultSet rs, int column, int kind) throws SQLException {
        switch (kind) {
            case Types.CLOB:
            case Types.NCLOB:
            case Types.LONGVARCHAR:
            case Types.LONGNVARCHAR:
                try (Reader input = rs.getCharacterStream(column)) {
                    if (input == null) { return null; }
                    StringBuilder text = new StringBuilder();
                    char[] buffer = new char[4096];
                    int n;
                    while ((n = input.read(buffer)) != -1) { text.append(buffer, 0, n); }
                    return text.toString();
                } catch (IOException e) {
                    throw new SQLException("Cannot read complete physical snapshot value", e);
                }
            case Types.BINARY:
            case Types.VARBINARY:
            case Types.LONGVARBINARY:
            case Types.BLOB:
                byte[] bytes = rs.getBytes(column);
                return bytes == null ? null : "base64:" + DatatypeConverter.printBase64Binary(bytes);
            case Types.DECIMAL:
            case Types.NUMERIC:
                return SnapshotSupport.normalize(rs.getBigDecimal(column));
            default:
                return rs.getString(column);
        }
    }

    private static String identifier(String name) {
        if (name == null || !name.matches("[A-Za-z][A-Za-z0-9_$#]{0,29}")) {
            throw new IllegalArgumentException("Invalid Oracle snapshot identifier");
        }
        return name.toLowerCase(Locale.ENGLISH);
    }

    private static boolean businessTable(String table) {
        return table.startsWith("bd_") || "srmsm_marbasclsext".equals(table) || "srmsm_marbasclsext_b".equals(table)
                || "srmsm_supplierext_h".equals(table) || "srmsm_supplierext_a".equals(table)
                || "srmsm_supplierext_i".equals(table) || "srmsm_supplierext_p".equals(table) || "srmsm_supplierext_q".equals(table)
                || "customerljpwms".equals(table) || "vendorljpwms".equals(table) || "warehouseljpwms".equals(table)
                || "so_saleorder".equals(table) || "so_saleorder_b".equals(table)
                || "ic_purchasein_h".equals(table) || "ic_purchasein_b".equals(table) || "ic_purchasein_l".equals(table)
                || "ic_saleout_h".equals(table) || "ic_saleout_b".equals(table) || "ic_saleout_l".equals(table)
                || "ic_transin_h".equals(table) || "ic_transin_b".equals(table) || "ic_transin_l".equals(table)
                || "ic_openbal_h".equals(table) || "ic_openbal_b".equals(table) || "ic_openbal_l".equals(table)
                || "ic_locadjust_h".equals(table) || "ic_locadjust_b".equals(table) || "ic_locadjust_l".equals(table)
                || "ic_generalin_h".equals(table) || "ic_generalin_b".equals(table) || "ic_generalin_l".equals(table)
                || "ic_finprodin_h".equals(table) || "ic_finprodin_b".equals(table) || "ic_finprodin_l".equals(table)
                || "ic_subcontin_h".equals(table) || "ic_subcontin_b".equals(table) || "ic_subcontin_l".equals(table)
                || "ic_generalout_h".equals(table) || "ic_generalout_b".equals(table) || "ic_generalout_l".equals(table)
                || "ic_material_h".equals(table) || "ic_material_b".equals(table) || "ic_material_l".equals(table)
                || "ic_transout_h".equals(table) || "ic_transout_b".equals(table) || "ic_transout_l".equals(table)
                || "ic_sapply_h".equals(table) || "ic_sapply_b".equals(table) || "ic_sapply_e".equals(table)
                || "po_order".equals(table) || "po_order_b".equals(table) || "po_order_payment".equals(table);
    }
}
