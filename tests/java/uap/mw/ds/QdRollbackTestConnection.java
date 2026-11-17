package uap.mw.ds;

import java.sql.Connection;

/** A test-only NC connection whose physical JDBC delegate is controlled by the test. */
public final class QdRollbackTestConnection extends DBConnection {
    private int returned;

    public QdRollbackTestConnection(Connection delegate) {
        super(delegate,new UAPDataSource());
        setStatus(uap.mw.trans.util.ConnectionStatus.INUSE);
    }

    @Override public void backPool() { returned++; }
    @Override public String getDataSourceName() { return "qd-test"; }
    public int returnedCount() { return returned; }
}
