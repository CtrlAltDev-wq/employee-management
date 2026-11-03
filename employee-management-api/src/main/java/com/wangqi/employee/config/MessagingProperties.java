package com.wangqi.employee.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Broker settings owned by the <em>producer</em>.
 *
 * <p>Only the exchange and the routing key live here: the onboarding queue,
 * its binding and its dead-letter queue belong to the consumer service, which
 * declares them. That split is what lets the consumer move to another process
 * without touching the producer.
 *
 * <p>{@code enabled=false} replaces the AMQP publisher with an in-process one,
 * which is how the test suite runs without a broker.
 *
 * @author wangqi
 */
@ConfigurationProperties(prefix = "app.messaging")
public record MessagingProperties(
        boolean enabled,
        String exchange,
        String routingKey
) {
}
