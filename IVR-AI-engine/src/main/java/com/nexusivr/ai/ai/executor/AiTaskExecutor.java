package com.nexusivr.ai.ai.executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Dedicated thread pool executor for long-running AI and LLM provider calls.
 * Isolates external AI network calls and retries from Tomcat request-handling worker threads.
 */
public class AiTaskExecutor {

    private static final Logger logger = LoggerFactory.getLogger(AiTaskExecutor.class);

    private static final int CORE_POOL_SIZE = 10;
    private static final int MAXIMUM_POOL_SIZE = 30;
    private static final long KEEP_ALIVE_TIME_SECONDS = 60L;
    private static final int QUEUE_CAPACITY = 50;

    private static final ThreadPoolExecutor executor;
    private static final ScheduledExecutorService timeoutScheduler;

    static {
        AtomicInteger threadCount = new AtomicInteger(1);
        executor = new ThreadPoolExecutor(
                CORE_POOL_SIZE,
                MAXIMUM_POOL_SIZE,
                KEEP_ALIVE_TIME_SECONDS,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY),
                r -> {
                    Thread t = new Thread(r, "ai-worker-" + threadCount.getAndIncrement());
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        timeoutScheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "ai-timeout-scheduler");
            t.setDaemon(true);
            return t;
        });

        logger.info("[AiTaskExecutor] Initialized dedicated AI thread pool: core={}, max={}, queueCap={}",
                CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, QUEUE_CAPACITY);
    }

    private AiTaskExecutor() {}

    /**
     * Executes a Callable AI task on the dedicated AI worker thread pool with a specified timeout.
     */
    public static <T> T execute(Callable<T> task, long timeoutMs) throws Exception {
        Future<T> future = executor.submit(task);
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            logger.warn("[AiTaskExecutor] AI operation timed out after {}ms on worker thread", timeoutMs);
            throw e;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw new RuntimeException(cause);
        }
    }

    /**
     * Submits a Callable AI task to the dedicated AI worker thread pool.
     */
    public static <T> Future<T> submit(Callable<T> task) {
        return executor.submit(task);
    }

    public static int getActiveCount() {
        return executor.getActiveCount();
    }

    public static int getPoolSize() {
        return executor.getPoolSize();
    }

    public static int getQueueSize() {
        return executor.getQueue().size();
    }

    /**
     * Gracefully shuts down the executor pool.
     */
    public static void shutdown() {
        logger.info("[AiTaskExecutor] Shutting down AI thread pool...");
        executor.shutdown();
        timeoutScheduler.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
