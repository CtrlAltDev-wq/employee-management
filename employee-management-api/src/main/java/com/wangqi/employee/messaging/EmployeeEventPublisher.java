package com.wangqi.employee.messaging;

/**
 * Publishes domain events to the outside world.
 *
 * <p>Kept as an interface so the transport is a detail: AMQP in a running
 * system, an in-process logger when messaging is disabled.
 *
 * @author wangqi
 */
public interface EmployeeEventPublisher {

    void publish(EmployeeCreatedMessage message);
}
