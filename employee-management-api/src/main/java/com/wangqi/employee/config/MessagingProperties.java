package com.wangqi.employee.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Broker topology and switch for the employee event stream.
 *
 * <p>{@code enabled=false} replaces the AMQP publisher with an in-process one
 * and skips the listener container, which is how the test suite runs without a
 * broker. Everything else (exchange, routing key, queue names) is part of the
 * integration contract.
 *
 * @author wangqi
 */
@ConfigurationProperties(prefix = "app.messaging")
public record MessagingProperties(
        boolean enabled,
        String exchange,
        String routingKey,
        String queue,
        String deadLetterQueue
) {
}
