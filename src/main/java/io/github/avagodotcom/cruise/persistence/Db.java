package io.github.avagodotcom.cruise.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class Db {
    private final String jdbcUrl;

    public Db(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    public Connection getConnection() throws SQLException {
        Connection c = DriverManager.getConnection(jdbcUrl);
        c.createStatement().execute("PRAGMA foreign_keys = ON");
        return c;
    }
}
