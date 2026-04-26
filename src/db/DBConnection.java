package db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnection {
    private DBConnection() {
    }

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException ex) {
            throw new IllegalStateException(
                    "MySQL JDBC Driver not found. Add src/lib/mysql-connector-j-9.3.0.jar to classpath.", ex);
        }
    }

    public static Connection getConnection() throws SQLException {
        try {
            return DriverManager.getConnection(DBConfig.URL, DBConfig.USER, DBConfig.PASSWORD);
        } catch (SQLException ex) {
            String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
            if (message.contains("access denied")) {
                throw new SQLException(
                        "Access denied for MySQL user '" + DBConfig.USER + "'. " +
                                "Set credentials in src/database/db.properties or environment variables " +
                                "(EVENT_DB_USER, EVENT_DB_PASSWORD).",
                        ex);
            }
            throw ex;
        }
    }
}
