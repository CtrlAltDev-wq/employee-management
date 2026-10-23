package com.wangqi.employee.config;

import com.wangqi.employee.observability.CorrelationIdFilter;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Dedicated, bounded thread pool for outbound side effects (notifications).
 *
 * <p>Why a dedicated pool instead of the default one:
 * <ul>
 *   <li>an isolated pool means a slow notification path cannot starve request
 *       threads or any other async work;</li>
 *   <li>core/max/queue are explicit, so the memory cost is predictable;</li>
 *   <li>{@link CallerRunsPolicy} gives natural backpressure: once the queue is
 *       full the caller runs the task itself instead of silently dropping it;</li>
 *   <li>a {@link TaskDecorator} copies the MDC correlation id into the worker
 *       thread, so async log lines stay attributable to the originating
 *       request.</li>
 * </ul>
 *
 * @author wangqi
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    public static final String NOTIFICATION_EXECUTOR = "notificationExecutor";

    @Bean(name = NOTIFICATION_EXECUTOR)
    public ThreadPoolTaskExecutor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("notification-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setTaskDecorator(new MdcTaskDecorator());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }

    static class MdcTaskDecorator implements TaskDecorator {

        @Override
        public Runnable decorate(Runnable runnable) {
            Map<String, String> context = MDC.getCopyOfContextMap();
            return () -> {
                Map<String, String> previous = MDC.getCopyOfContextMap();
                try {
                    if (context != null) {
                        MDC.setContextMap(context);
                    }
                    runnable.run();
                } finally {
                    if (previous != null) {
                        MDC.setContextMap(previous);
                    } else {
                        MDC.clear();
                    }
                }
            };
        }
    }
}
