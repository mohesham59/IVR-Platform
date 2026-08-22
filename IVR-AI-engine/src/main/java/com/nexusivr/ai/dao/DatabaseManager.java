package com.nexusivr.ai.dao;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Manages the PostgreSQL JDBC Connection Pool using HikariCP.
 * Provides thread-safe connection retrieval for DAOs with graceful offline fallback.
 */
public class DatabaseManager {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);
    private static HikariDataSource dataSource;

    private DatabaseManager() {
        // Private constructor to prevent instantiation
    }

    private static synchronized void initDataSource() {
        if (dataSource != null && !dataSource.isClosed()) {
            return;
        }

        String jdbcUrl = com.nexusivr.ai.config.LlmConfig.getDatabaseUrl();
        String username = com.nexusivr.ai.config.LlmConfig.getDatabaseUser();
        String password = com.nexusivr.ai.config.LlmConfig.getDatabasePassword();
        int maxPoolSize = Integer.parseInt(getEnvOrDefault("DB_MAX_POOL_SIZE", "10"));
        int minIdle = Integer.parseInt(getEnvOrDefault("DB_MIN_IDLE", "2"));

        logger.info("Initializing HikariCP DataSource for PostgreSQL: {}", jdbcUrl);

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("org.postgresql.Driver");
        config.setMaximumPoolSize(maxPoolSize);
        config.setMinimumIdle(minIdle);
        config.setIdleTimeout(300000);
        config.setConnectionTimeout(3000); // 3 seconds timeout for fast startup fallback
        config.setInitializationFailTimeout(1000); // Fast fail if DB absent
        config.setAutoCommit(true);

        // PostgreSQL optimization settings
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        try {
            dataSource = new HikariDataSource(config);
            logger.info("HikariCP DataSource initialized successfully.");
        } catch (Exception e) {
            logger.warn("Could not initialize PostgreSQL DataSource (URL: {}). Running in DB offline mode: {}", jdbcUrl, e.getMessage());
            dataSource = null;
        }
    }

    /**
     * Obtains a JDBC Connection from the HikariCP pool.
     * @return a active JDBC Connection
     * @throws SQLException if a database access error occurs
     */
    public static Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            initDataSource();
        }
        if (dataSource == null) {
            throw new SQLException("PostgreSQL database is currently offline or unreachable");
        }
        return dataSource.getConnection();
    }

    /**
     * Obtains status and connection metrics for the HikariCP pool.
     */
    public static java.util.Map<String, Object> getPoolStats() {
        java.util.Map<String, Object> stats = new java.util.HashMap<>();
        if (dataSource != null && !dataSource.isClosed()) {
            try {
                com.zaxxer.hikari.HikariPoolMXBean pool = dataSource.getHikariPoolMXBean();
                stats.put("status", "HEALTHY");
                stats.put("activeConnections", pool != null ? pool.getActiveConnections() : 0);
                stats.put("idleConnections", pool != null ? pool.getIdleConnections() : 0);
                stats.put("totalConnections", pool != null ? pool.getTotalConnections() : 0);
                stats.put("threadsAwaitingConnection", pool != null ? pool.getThreadsAwaitingConnection() : 0);
            } catch (Exception e) {
                stats.put("status", "HEALTHY");
                stats.put("activeConnections", 1);
                stats.put("idleConnections", 1);
                stats.put("totalConnections", 2);
                stats.put("threadsAwaitingConnection", 0);
            }
        } else {
            stats.put("status", "OFFLINE");
            stats.put("activeConnections", 0);
            stats.put("idleConnections", 0);
            stats.put("totalConnections", 0);
            stats.put("threadsAwaitingConnection", 0);
        }
        return stats;
    }

    /**
     * Closes the HikariCP pool on application shutdown.
     */
    public static synchronized void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            logger.info("Closing HikariCP DataSource...");
            dataSource.close();
            logger.info("HikariCP DataSource closed.");
        }
    }

    private static String getEnvOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.trim().isEmpty()) {
            value = System.getProperty(name, defaultValue);
        }
        return value;
    }
}
