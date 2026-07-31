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
            logger.info("NexusIVR AI Engine Web Application Context initialized successfully.");
        } catch (Exception e) {
            logger.error("Error during web application startup initialization", e);
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        logger.info("Shutting down NexusIVR AI Engine Web Application Context...");
        DatabaseManager.shutdown();
        logger.info("NexusIVR AI Engine shutdown complete.");
    }
}
