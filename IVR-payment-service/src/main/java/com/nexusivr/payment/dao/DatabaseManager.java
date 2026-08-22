package com.nexusivr.payment.dao;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Manages PostgreSQL database connection pool using HikariCP for IVR-payment-service.
 * Reuses the same environment configuration standards as the rest of the platform.
 */
public class DatabaseManager {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);

    private static volatile DataSource dataSource;

    private DatabaseManager() {
    }

    public static synchronized void initDataSource() {
        if (dataSource != null) {
            return;
        }

        Properties envProps = new Properties();
        loadDotEnv(envProps);

        String jdbcUrl = resolveConfig("db.url", "DATABASE_URL", "DB_URL", envProps, "jdbc:postgresql://localhost:5432/nexusivr");
        String username = resolveConfig("db.user", "DATABASE_USER", "DB_USER", envProps, "nexusivr");
        String password = resolveConfig("db.password", "DATABASE_PASSWORD", "DB_PASSWORD", envProps, "");
        String driverClass = resolveConfig("db.driver", "DATABASE_DRIVER", "DB_DRIVER", envProps, "org.postgresql.Driver");

        logger.info("Initializing HikariCP DataSource for Database: {}", jdbcUrl);

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName(driverClass);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setIdleTimeout(300000);
        config.setConnectionTimeout(3000);
        config.setInitializationFailTimeout(1000);
        config.setAutoCommit(true);

        try {
            dataSource = new HikariDataSource(config);
            logger.info("HikariCP DataSource initialized successfully.");
        } catch (Exception e) {
            logger.warn("Could not initialize database pool (URL: {}): {}", jdbcUrl, e.getMessage());
            dataSource = null;
        }
    }

    /**
     * Obtains a JDBC Connection from the HikariCP pool.
     */
    public static Connection getConnection() throws SQLException {
        if (dataSource == null) {
            initDataSource();
        }
        if (dataSource == null) {
            throw new SQLException("Database is currently offline or unreachable");
        }
        return dataSource.getConnection();
    }

    /**
     * Set a custom DataSource (useful for in-memory H2 tests).
     */
    public static synchronized void setDataSourceForTesting(DataSource customDs) {
        dataSource = customDs;
    }

    /**
     * Shutdown connection pool on application destroy.
     */
    public static synchronized void shutdown() {
        if (dataSource instanceof HikariDataSource hikariDs && !hikariDs.isClosed()) {
            logger.info("Closing HikariCP DataSource...");
            hikariDs.close();
            logger.info("HikariCP DataSource closed.");
        }
        dataSource = null;
    }

    private static String resolveConfig(String sysProp, String envVar1, String envVar2, Properties envProps, String fallback) {
        String val = System.getProperty(sysProp);
        if (val != null && !val.isBlank()) return val.trim();

        val = System.getenv(envVar1);
        if (val != null && !val.isBlank()) return val.trim();

        val = System.getenv(envVar2);
        if (val != null && !val.isBlank()) return val.trim();

        val = envProps.getProperty(envVar1);
        if (val != null && !val.isBlank()) return val.trim();

        val = envProps.getProperty(envVar2);
        if (val != null && !val.isBlank()) return val.trim();

        val = envProps.getProperty(sysProp);
        if (val != null && !val.isBlank()) return val.trim();

        return fallback;
    }

    private static void loadDotEnv(Properties targetProps) {
        File[] candidates = new File[]{
                new File(".env"),
                new File("../.env"),
                new File("IVR-payment-service/.env")
        };
        for (File f : candidates) {
            if (f.exists() && f.isFile()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(f, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) continue;
                        int eqIdx = line.indexOf('=');
                        if (eqIdx <= 0) continue;
                        String key = line.substring(0, eqIdx).trim();
                        String value = line.substring(eqIdx + 1).trim();
                        if ((value.startsWith("\"") && value.endsWith("\"")) ||
                            (value.startsWith("'") && value.endsWith("'"))) {
                            value = value.substring(1, value.length() - 1);
                        }
                        if (!key.isEmpty()) targetProps.setProperty(key, value);
                    }
                } catch (Exception ignored) {
                }
                return;
            }
        }
    }
}
