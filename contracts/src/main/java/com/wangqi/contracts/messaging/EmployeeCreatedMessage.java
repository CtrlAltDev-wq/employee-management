package com.wangqi.contracts.messaging;

import java.time.Instant;

/**
 * Wire format of the {@code employee.created} event.
 *
 * <p>This type lives in its own module because its fully qualified name is part
 * of the message: {@code Jackson2JsonMessageConverter} writes it into the
 * {@code __TypeId__} header, and the consumer resolves that header back to a
 * class. Two copies under different package names would fail to deserialise
 * even if their fields matched, so producer and consumer depend on this single
 * artifact instead.
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
