package com.nexusivr.ai.monitoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Health and capacity monitoring service that periodically inspects Tomcat / web container
 * thread pool utilization and logs WARN alerts when capacity utilization exceeds 80%.
 */
public class ThreadPoolMonitorService {

    private static final Logger logger = LoggerFactory.getLogger(ThreadPoolMonitorService.class);

    private static final double UTILIZATION_WARN_THRESHOLD = 80.0; // 80% capacity warning
    private static final long CHECK_INTERVAL_SECONDS = 10L;

    private static ScheduledExecutorService scheduler;
    private static boolean running = false;

    public static synchronized void start() {
        if (running) return;
        running = true;

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "thread-pool-capacity-monitor");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(ThreadPoolMonitorService::checkThreadUtilization,
                5L, CHECK_INTERVAL_SECONDS, TimeUnit.SECONDS);
        logger.info("[ThreadPoolMonitor] Started thread pool capacity monitoring service (interval: {}s, threshold: {}%).",
                CHECK_INTERVAL_SECONDS, UTILIZATION_WARN_THRESHOLD);
    }

    public static synchronized void stop() {
        if (!running) return;
        running = false;
        if (scheduler != null) {
            logger.info("[ThreadPoolMonitor] Stopping thread pool capacity monitoring service...");
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void checkThreadUtilization() {
        try {
            boolean checkedJmx = checkTomcatJmxThreadPool();
            if (!checkedJmx) {
                checkJvmThreadMXBean();
            }
        } catch (Exception e) {
            logger.debug("[ThreadPoolMonitor] Thread capacity check error: {}", e.getMessage());
        }
    }

    private static boolean checkTomcatJmxThreadPool() {
        try {
            MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            Set<ObjectName> names = server.queryNames(new ObjectName("*:type=ThreadPool,*"), null);
            if (names.isEmpty()) {
                names = server.queryNames(new ObjectName("Catalina:type=ThreadPool,*"), null);
            }
            if (names.isEmpty()) {
                return false;
            }

            for (ObjectName name : names) {
                Object currentThreadsBusyObj = server.getAttribute(name, "currentThreadsBusy");
                Object maxThreadsObj = server.getAttribute(name, "maxThreads");

                if (currentThreadsBusyObj instanceof Number busyNum && maxThreadsObj instanceof Number maxNum) {
                    int busyThreads = busyNum.intValue();
                    int maxThreads = maxNum.intValue();
                    if (maxThreads > 0) {
                        double utilization = ((double) busyThreads / maxThreads) * 100.0;
                        if (utilization >= UTILIZATION_WARN_THRESHOLD) {
                            logger.warn("[ThreadPoolMonitor] WARN: High server thread pool utilization detected on pool '{}': {} busy threads out of {} max (utilization {}%). Potential request saturation.",
                                    name.getKeyProperty("name"), busyThreads, maxThreads, String.format("%.1f", utilization));
                        } else {
                            logger.debug("[ThreadPoolMonitor] Thread pool '{}' healthy: {}/{} busy ({}%).",
                                    name.getKeyProperty("name"), busyThreads, maxThreads, String.format("%.1f", utilization));
                        }
                    }
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void checkJvmThreadMXBean() {
        ThreadMXBean mxBean = ManagementFactory.getThreadMXBean();
        int totalThreads = mxBean.getThreadCount();
        long[] threadIds = mxBean.getAllThreadIds();

        int busyWorkerThreads = 0;
        for (long id : threadIds) {
            var info = mxBean.getThreadInfo(id);
            if (info != null) {
                String threadName = info.getThreadName().toLowerCase();
                if (threadName.contains("exec") || threadName.contains("http-bio") ||
                    threadName.contains("http-nio") || threadName.contains("tomcat")) {
                    if (info.getThreadState() == Thread.State.RUNNABLE || info.getThreadState() == Thread.State.TIMED_WAITING) {
                        busyWorkerThreads++;
                    }
                }
            }
        }

        // Assuming standard max 200 pool capacity
        int estimatedCapacity = 200;
        double utilization = ((double) busyWorkerThreads / estimatedCapacity) * 100.0;
        if (utilization >= UTILIZATION_WARN_THRESHOLD || busyWorkerThreads >= 30) {
            logger.warn("[ThreadPoolMonitor] WARN: High server worker thread count detected: {} active worker threads (total JVM threads: {}). Potential request saturation.",
                    busyWorkerThreads, totalThreads);
        } else {
            logger.debug("[ThreadPoolMonitor] JVM Worker threads healthy: {} active / {} total JVM threads.",
                    busyWorkerThreads, totalThreads);
        }
    }
}
