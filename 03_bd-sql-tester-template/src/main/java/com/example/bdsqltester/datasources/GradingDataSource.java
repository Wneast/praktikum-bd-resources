// src/main/java/com/example/bdsqltester/datasources/GradingDataSource.java
package com.example.bdsqltester.datasources;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

public class GradingDataSource {

    private static HikariDataSource ds;

    static {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://localhost:5432/sql-tester");
        config.setUsername("postgres"); // Use postgres user
        config.setPassword("12345"); // Use postgres password
        config.setDriverClassName("org.postgresql.Driver");
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(5);
        config.setIdleTimeout(30000);
        config.setConnectionTimeout(10000);
        config.setMaxLifetime(60000);
        config.setPoolName("GradingPool");
        config.setInitializationFailTimeout(-1); // Initialize even if connection fails

        ds = new HikariDataSource(config);
    }

    public static Connection getConnection() throws SQLException {
        return ds.getConnection();
    }

    private GradingDataSource() {}
}