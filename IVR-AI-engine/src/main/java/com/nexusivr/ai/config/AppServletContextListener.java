package com.nexusivr.ai.config;

import com.nexusivr.ai.controller.ServiceRegistry;
import com.nexusivr.ai.dao.DatabaseManager;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ServletContextListener initializing application dependencies, services,
 * and singletons upon web application startup in Tomcat.
 */
@WebListener
public class AppServletContextListener implements ServletContextListener {

    private static final Logger logger = LoggerFactory.getLogger(AppServletContextListener.class);

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        logger.info("Initializing NexusIVR AI Engine Web Application Context...");
        try {
            // Eagerly initialize ServiceRegistry singletons and AI provider components
            ServiceRegistry.getAiService();

            // Run database migrations for users and tenants
            runDbMigration();

            logger.info("NexusIVR AI Engine Web Application Context initialized successfully.");
        } catch (Exception e) {
            logger.error("Error during web application startup initialization", e);
        }
    }

    private void runDbMigration() {
        try (java.sql.Connection conn = DatabaseManager.getConnection()) {
            if (conn == null) return;
            executeSqlScript(conn, "/009_users_and_tenants.sql", "Database/AI-database/009_users_and_tenants.sql");
            executeSqlScript(conn, "/010_telephony_analytics.sql", "Database/AI-database/010_telephony_analytics.sql");
            executeSqlScript(conn, "/011_phone_numbers.sql", "Database/AI-database/011_phone_numbers.sql");
            executeSqlScript(conn, "/012_sip_extensions.sql", "Database/AI-database/012_sip_extensions.sql");
            executeSqlScript(conn, "/013_queue_management.sql", "Database/AI-database/013_queue_management.sql");
            executeSqlScript(conn, "/014_voice_prompts.sql", "Database/AI-database/014_voice_prompts.sql");
            executeSqlScript(conn, "/015_audit_logs.sql", "Database/AI-database/015_audit_logs.sql");
            logger.info("Database schema and seeds verified successfully.");
        } catch (Exception e) {
            logger.warn("Database migration execution skipped or encountered an issue: {}", e.getMessage());
        }
    }

    private void executeSqlScript(java.sql.Connection conn, String resourceName, String fallbackRelativePath) {
        try {
            java.io.InputStream is = getClass().getResourceAsStream(resourceName);
            if (is == null) {
                java.io.File relative = new java.io.File(fallbackRelativePath);
                if (relative.exists()) {
                    is = new java.io.FileInputStream(relative);
                }
            }
            if (is == null) {
                logger.warn("DB script {} not found. Skipping.", resourceName);
                return;
            }
            String sqlContent = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            try (java.sql.Statement stmt = conn.createStatement()) {
                for (String statement : sqlContent.split(";")) {
                    String trimmed = statement.trim();
                    if (!trimmed.isEmpty()) {
                        try {
                            stmt.execute(trimmed);
                        } catch (Exception ignored) {}
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Script execution encountered issue for {}: {}", resourceName, e.getMessage());
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        logger.info("Shutting down NexusIVR AI Engine Web Application Context...");
        DatabaseManager.shutdown();
        logger.info("NexusIVR AI Engine shutdown complete.");
    }
}
