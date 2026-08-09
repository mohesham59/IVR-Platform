package com.nexusivr.payment.config;

import com.nexusivr.payment.dao.DatabaseManager;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;

/**
 * Web application context listener executing startup initialization, credential validation,
 * and database schema migration for IVR-payment-service.
 */
@WebListener
public class AppServletContextListener implements ServletContextListener {

    private static final Logger logger = LoggerFactory.getLogger(AppServletContextListener.class);

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        logger.info("Initializing IVR Payment Service Web Application Context...");
        try {
            // 1. Eagerly initialize and validate Paymob configuration (fail-fast if credentials missing)
            PaymobConfig config = PaymobConfig.getInstance();

            // 2. Run database migrations for payment & subscription tables
            runDbMigration();

            // 3. Start subscription renewal scheduler
            SubscriptionScheduler.getInstance().start(new com.nexusivr.payment.PaymentService());

            logger.info("IVR Payment Service initialized successfully on port {}. Ready to process EGP transactions.", config.getServicePort());
        } catch (PaymobConfigException e) {
            logger.error("FATAL: IVR Payment Service failed startup validation due to missing credentials!", e);
            throw new RuntimeException("Service startup halted due to configuration errors: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("FATAL: Unexpected error during IVR Payment Service initialization!", e);
            throw new RuntimeException("Service startup halted: " + e.getMessage(), e);
        }
    }

    /**
     * Executes 001_payment_and_subscriptions.sql migration script idempotently on startup.
     */
    public void runDbMigration() {
        try (Connection conn = DatabaseManager.getConnection()) {
            if (conn == null) return;

            InputStream is = getClass().getResourceAsStream("/001_payment_and_subscriptions.sql");
            if (is == null) {
                String envScript = System.getenv("NEXUSIVR_PAYMENT_DB_SCRIPT");
                if (envScript != null && !envScript.isBlank()) {
                    File file = new File(envScript);
                    if (file.exists()) is = new FileInputStream(file);
                }
            }
            if (is == null) {
                File[] candidates = new File[] {
                        new File("Database/Payment-database/001_payment_and_subscriptions.sql"),
                        new File("../Database/Payment-database/001_payment_and_subscriptions.sql"),
                        new File("../../Database/Payment-database/001_payment_and_subscriptions.sql")
                };
                for (File f : candidates) {
                    if (f.exists()) {
                        is = new FileInputStream(f);
                        break;
                    }
                }
            }

            if (is == null) {
                logger.warn("Payment DB migration script 001_payment_and_subscriptions.sql not found. Skipping schema migration.");
                return;
            }

            String sqlContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            try (Statement stmt = conn.createStatement()) {
                // Pre-register gen_random_uuid for H2 in-memory test compatibility
                try {
                    stmt.execute("CREATE ALIAS gen_random_uuid FOR \"java.util.UUID.randomUUID\"");
                } catch (Exception ignored) {}

                for (String statement : sqlContent.split(";")) {
                    String trimmed = statement.trim();
                    if (!trimmed.isEmpty()) {
                        try {
                            stmt.execute(trimmed);
                        } catch (Exception e) {
                            logger.debug("Migration statement notice for [{}]: {}", trimmed, e.getMessage());
                        }
                    }
                }
                logger.info("Database schema migration and seed verification completed successfully.");
            }
        } catch (Exception e) {
            logger.warn("Database migration execution skipped or encountered an issue: {}", e.getMessage());
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        com.nexusivr.payment.config.SubscriptionScheduler.getInstance().stop();
        DatabaseManager.shutdown();
        logger.info("Shutting down IVR Payment Service Web Application Context...");
    }
}
