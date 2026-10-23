package com.wangqi.employee.messaging;

import com.wangqi.employee.event.EmployeeCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;

/**
 * Bridges the in-process domain event to the message broker.
 *
 * <p>Runs {@code AFTER_COMMIT}: a message must never be published for an
 * employee whose transaction rolled back. A broker failure is logged, not
 * rethrown — the row is already committed, so failing the HTTP request would
 * report an error the caller cannot act on. The gap between commit and publish
 * is exactly what a transactional outbox closes; that is documented as the
 * deliberate limitation of this module.
 *
 * @author wangqi
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmployeeEventRelay {

    private final EmployeeEventPublisher publisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEmployeeCreated(EmployeeCreatedEvent event) {
        try {
            publisher.publish(new EmployeeCreatedMessage(
                    event.employeeId(), event.fullName(), event.email(), Instant.now()));
        } catch (RuntimeException ex) {
            log.error("Could not publish employee.created for employee {}", event.employeeId(), ex);
        }
    }
}
