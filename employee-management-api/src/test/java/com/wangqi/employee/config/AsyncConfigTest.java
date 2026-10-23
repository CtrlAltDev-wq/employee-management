package com.wangqi.employee.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the notification pool is bounded and that the correlation id
 * survives the hand-off to a worker thread.
 *
 * @author wangqi
 */
class AsyncConfigTest {

    private ThreadPoolTaskExecutor executor;

    @AfterEach
    void tearDown() {
        MDC.clear();
        if (executor != null) {
            executor.shutdown();
        }
    }

    @Test
    @DisplayName("the notification executor has explicit bounds and a caller-runs rejection policy")
    void shouldConfigureBoundedExecutor() {
        executor = new AsyncConfig().notificationExecutor();

        assertThat(executor.getCorePoolSize()).isEqualTo(2);
        assertThat(executor.getMaxPoolSize()).isEqualTo(4);
        assertThat(executor.getThreadPoolExecutor().getQueue().remainingCapacity()).isEqualTo(50);
        assertThat(executor.getThreadNamePrefix()).isEqualTo("notification-");
        assertThat(executor.getThreadPoolExecutor().getRejectedExecutionHandler())
                .isInstanceOf(ThreadPoolExecutor.CallerRunsPolicy.class);
    }

    @Test
    @DisplayName("the MDC correlation id is propagated to the worker thread")
    void shouldPropagateCorrelationIdToWorkerThread() throws Exception {
        executor = new AsyncConfig().notificationExecutor();
        MDC.put("requestId", "req-123");

        String observed = executor.submit(() -> MDC.get("requestId")).get(2, TimeUnit.SECONDS);

        assertThat(observed).isEqualTo("req-123");
        assertThat(MDC.get("requestId")).isEqualTo("req-123");
    }
}
