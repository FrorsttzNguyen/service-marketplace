package com.hien.marketplace.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Async configuration for event processing.
 *
 * WHY: Event listeners should run asynchronously to:
 * - Not block the main transaction thread
 * - Prevent transaction rollback if notification fails
 * - Improve response time for API calls
 *
 * Phase 3 Fix: Implements async event processing as documented in learning docs.
 * See: docs/html/vi/phase3/05-observer-pattern.html
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Thread pool executor for event listeners.
     *
     * WHY: Dedicated thread pool for notifications:
     * - Isolates notification processing from main application
     * - Prevents notification failures from affecting core business logic
     * - Allows tuning thread pool size independently
     *
     * Configuration:
     * - corePoolSize: 5 threads always available
     * - maxPoolSize: 10 threads during high load
     * - queueCapacity: 100 tasks can queue before rejection
     * - threadNamePrefix: Helps identify async threads in logs
     */
    @Bean(name = "eventTaskExecutor")
    public Executor eventTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("event-");
        executor.initialize();
        return executor;
    }
}
