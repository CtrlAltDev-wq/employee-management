package com.wangqi.employee.event;

import com.wangqi.employee.config.AsyncConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends the welcome notification for a new employee on the dedicated
 * {@code notificationExecutor} pool, never on the request thread.
 *
 * <p>{@link TransactionalEventListener} with {@code AFTER_COMMIT} is deliberate:
 * an {@code @EventListener} would fire before the transaction commits, so a
 * rolled-back create could still trigger a notification for an employee that
 * never existed.
 *
 * @author wangqi
 */
@Slf4j
@Component
public class EmployeeCreatedListener {

    @Async(AsyncConfig.NOTIFICATION_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEmployeeCreated(EmployeeCreatedEvent event) {
        // Stand-in for a real mail/webhook call. The point is that it runs on
        // the bounded worker pool, not inside the HTTP request.
        log.info("Welcome notification sent to employee {} <{}>", event.employeeId(), event.email());
    }
}
