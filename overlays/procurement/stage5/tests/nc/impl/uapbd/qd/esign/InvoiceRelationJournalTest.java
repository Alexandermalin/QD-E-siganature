package nc.impl.uapbd.qd.esign;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import nc.bs.dao.DAOException;
import nc.jdbc.framework.SQLParameter;
import nc.jdbc.framework.processor.ResultSetProcessor;
import nc.vo.pub.BusinessException;

public final class InvoiceRelationJournalTest {
    public static void main(String[] args) throws Exception {
        Class.forName("org.hsqldb.jdbc.JDBCDriver");
        try (Connection connection = DriverManager.getConnection("jdbc:hsqldb:mem:relationjournal", "SA", "")) {
            createFixture(connection);
            InvoiceRelationJournalImpl journal = new InvoiceRelationJournalImpl(new JdbcFixture(connection));
            reject(new Operation() { public void run(InvoiceRelationJournalImpl service) throws BusinessException {
                service.finish("REQUEST-A");
            }}, journal);
            journal.checkpoint_RequiresNew("REQUEST-A", "EXTERNAL_UNKNOWN");
            expect("EXTERNAL_UNKNOWN", phase(connection, "REQUEST-A"));
            reject(new Operation() { public void run(InvoiceRelationJournalImpl service) throws BusinessException {
                service.assertClear("INVOICE-A");
            }}, journal);
            reject(new Operation() { public void run(InvoiceRelationJournalImpl service) throws BusinessException {
                service.checkpoint_RequiresNew("REQUEST-A", "EXTERNAL_ACCEPTED");
            }}, journal);
            journal.checkpoint_RequiresNew("REQUEST-B", "EXTERNAL_ACCEPTED");
            connection.setAutoCommit(false);
            journal.finish("REQUEST-B");
            journal.assertClear("INVOICE-B");
            connection.rollback();
            reject(new Operation() { public void run(InvoiceRelationJournalImpl service) throws BusinessException {
                service.assertClear("INVOICE-B");
            }}, journal);
            journal.finish("REQUEST-B");
            connection.commit();
            journal.assertClear("INVOICE-B");
            journal.assertClear("INVOICE-C");
            expect("EXTERNAL_ACCEPTED", phase(connection, "REQUEST-B"));
        }
    }

    private static void createFixture(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("create table qd_es_inv_ext (request_id varchar(64) primary key, "
                    + "invoice_pk varchar(20) not null, phase varchar(24) not null, updated_at varchar(24), "
                    + "resolved_at varchar(24), user_id varchar(20), resolved_by varchar(20), "
                    + "resolution_note varchar(1000), active_invoice_pk varchar(20) generated always as "
                    + "(case when resolved_at is null then invoice_pk else null end))");
            statement.execute("insert into qd_es_inv_ext (request_id, invoice_pk, phase) "
                    + "values ('REQUEST-A', 'INVOICE-A', 'EXTERNAL_STARTED'), "
                    + "('REQUEST-B', 'INVOICE-B', 'EXTERNAL_STARTED')");
        }
    }

    private static String phase(Connection connection, String request) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("select phase from qd_es_inv_ext where request_id = ?")) {
            query.setString(1, request);
            try (ResultSet rows = query.executeQuery()) {
                if (rows.next()) return rows.getString(1);
            }
        }
        throw new AssertionError("Missing journal row " + request);
    }

    private static void expect(String expected, String actual) {
        if (!expected.equals(actual)) throw new AssertionError("Expected " + expected + ", got " + actual);
    }

    private static void reject(Operation operation, InvoiceRelationJournalImpl journal) throws Exception {
        try {
            operation.run(journal);
        } catch (DAOException e) {
            throw new AssertionError("Invalid SQL rather than a rejected journal operation", e);
        } catch (BusinessException expected) {
            return;
        }
        throw new AssertionError("Expected journal operation to fail");
    }

    private interface Operation {
        void run(InvoiceRelationJournalImpl service) throws BusinessException;
    }

    private static final class JdbcFixture implements InvoiceRelationJournalImpl.Sql {
        private final Connection connection;

        private JdbcFixture(Connection connection) {
            this.connection = connection;
        }

        public int update(String sql, SQLParameter parameters) throws DAOException {
            try (PreparedStatement statement = prepare(sql, parameters)) {
                return statement.executeUpdate();
            } catch (SQLException e) {
                throw new DAOException(e);
            }
        }

        public Object query(String sql, SQLParameter parameters, ResultSetProcessor processor) throws DAOException {
            try (PreparedStatement statement = prepare(sql, parameters);
                    ResultSet rows = statement.executeQuery()) {
                return processor.handleResultSet(rows);
            } catch (SQLException e) {
                throw new DAOException(e);
            }
        }

        private PreparedStatement prepare(String sql, SQLParameter parameters) throws SQLException {
            PreparedStatement statement = connection.prepareStatement(sql);
            for (int index = 0; index < parameters.getCountParams(); index++) {
                statement.setObject(index + 1, parameters.get(index));
            }
            return statement;
        }
    }
}
