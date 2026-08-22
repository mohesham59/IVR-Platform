package com.nexusivr.payment.config;

import com.nexusivr.payment.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Singleton scheduler running daily automated subscription renewal tasks.
 */
public class SubscriptionScheduler {

    private static final Logger logger = LoggerFactory.getLogger(SubscriptionScheduler.class);

    private static final SubscriptionScheduler INSTANCE = new SubscriptionScheduler();
    private ScheduledExecutorService scheduler;

    private SubscriptionScheduler() {}

    public static SubscriptionScheduler getInstance() {
        return INSTANCE;
    }

    public synchronized void start(PaymentService paymentService) {
        if (scheduler != null && !scheduler.isShutdown()) {
            logger.info("Subscription renewal scheduler is already running.");
            return;
        }

        scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "nexusivr-payment-scheduler");
            t.setDaemon(true);
            return t;
        });

        // Job 1: Daily subscription renewal (runs every 24h after 1-minute initial delay)
        logger.info("Starting automated subscription renewal scheduler (Interval: 24 hours).");
        scheduler.scheduleAtFixedRate(() -> {
            try {
                paymentService.renewSubscriptionsDueToday();
            } catch (Exception e) {
                logger.error("Error executing scheduled subscription renewal: {}", e.getMessage(), e);
            }
        }, 1, 24 * 60, TimeUnit.MINUTES);

        // Job 2: Stale PENDING transaction expiry (runs every 5 minutes after 1-minute initial delay)
        logger.info("Starting stale-PENDING transaction cleanup scheduler (Interval: 5 minutes, threshold: 45 minutes).");
        scheduler.scheduleAtFixedRate(() -> {
            try {
                paymentService.expireStalePendingTransactions();
            } catch (Exception e) {
                logger.error("Error executing stale-PENDING cleanup: {}", e.getMessage(), e);
            }
        }, 1, 5, TimeUnit.MINUTES);
    }

    public synchronized void stop() {
        if (scheduler != null && !scheduler.isShutdown()) {
            logger.info("Stopping subscription renewal scheduler...");
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
