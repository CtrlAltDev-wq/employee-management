package com.wangqi.employee.messaging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Stand-in publisher used when {@code app.messaging.enabled=false}: the event
 * is logged instead of being sent, so the application (and the test suite) runs
 * without a broker while keeping the same call path.
 *
 * @author wangqi
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.messaging", name = "enabled", havingValue = "false")
public class LocalEmployeeEventPublisher implements EmployeeEventPublisher {

    @Override
    public void publish(EmployeeCreatedMessage message) {
        log.info("[messaging disabled] employee.created for employee {} kept in-process",
                message.employeeId());
    }
}
