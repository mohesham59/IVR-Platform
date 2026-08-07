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
            java.io.InputStream is = getClass().getResourceAsStream("/009_users_and_tenants.sql");
            if (is == null) {
                String envScript = System.getenv("NEXUSIVR_DB_SCRIPT");
                if (envScript != null && !envScript.isBlank()) {
                    java.io.File file = new java.io.File(envScript);
                    if (file.exists()) {
                        is = new java.io.FileInputStream(file);
                    }
                }
            }
            if (is == null) {
                java.io.File relative = new java.io.File("Database/AI-database/009_users_and_tenants.sql");
                if (relative.exists()) {
                    is = new java.io.FileInputStream(relative);
                }
            }
            if (is == null) {
                logger.warn("DB migration script not found on classpath, via NEXUSIVR_DB_SCRIPT, or relative to working dir. Skipping schema migration.");
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
                logger.info("Database schema and seeds verified successfully.");
            }
        } catch (Exception e) {
            logger.warn("Database migration execution skipped or encountered an issue: {}", e.getMessage());
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        logger.info("Shutting down NexusIVR AI Engine Web Application Context...");
        DatabaseManager.shutdown();
        logger.info("NexusIVR AI Engine shutdown complete.");
    }
}
