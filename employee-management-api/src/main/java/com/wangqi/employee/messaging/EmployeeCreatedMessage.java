package com.wangqi.employee.messaging;

import java.time.Instant;

/**
 * Wire format of the {@code employee.created} event.
 *
 * <p>Deliberately a flat DTO with no reference to the JPA entity: the message is
 * a public contract consumed by other services, so it must not leak the
 * persistence model or serialise a lazy association.
 *
 * @author wangqi
 */
public record EmployeeCreatedMessage(
        Long employeeId,
        String fullName,
        String email,
        Instant occurredAt
) {
}
