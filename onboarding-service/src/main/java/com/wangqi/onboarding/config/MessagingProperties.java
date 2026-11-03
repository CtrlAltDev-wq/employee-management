package com.wangqi.onboarding.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Broker settings owned by the <em>consumer</em>.
 *
 * <p>The employee API publishes to {@code exchange} with {@code routingKey};
 * this service owns the {@code queue}, the binding and the {@code deadLetterQueue}.
 * Topology ownership following the consumer is what made the extraction a
 * deployment change rather than a producer change.
 *
 * <p>{@code enabled=false} skips the listener container and the declarations,
 * which is how the test suite runs without a broker.
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
