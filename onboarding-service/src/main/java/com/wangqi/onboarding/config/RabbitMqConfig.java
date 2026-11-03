package com.wangqi.onboarding.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Consumer-side topology for the {@code employee.created} stream.
 *
 * <ul>
 *   <li>the durable topic exchange is declared on both sides with identical
 *       arguments, which RabbitMQ treats as an idempotent declare;</li>
 *   <li>a durable queue bound to {@code employee.created} — durable means the
 *       event survives a broker restart, unlike an in-memory queue;</li>
 *   <li>a dead-letter queue, so a message the consumer rejects is parked for
 *       inspection instead of being redelivered forever;</li>
 *   <li>the same JSON converter as the producer, resolving the
 *       {@code __TypeId__} header against the shared {@code contracts} module.</li>
 * </ul>
 *
 * @author wangqi
 */
@Configuration
@EnableConfigurationProperties(MessagingProperties.class)
@ConditionalOnProperty(prefix = "app.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RabbitMqConfig {

    @Bean
    TopicExchange employeeEventsExchange(MessagingProperties properties) {
        return new TopicExchange(properties.exchange(), true, false);
    }

    @Bean
    Queue onboardingQueue(MessagingProperties properties) {
        return QueueBuilder.durable(properties.queue())
                // The default exchange routes a rejected message to the DLQ.
                .deadLetterExchange("")
                .deadLetterRoutingKey(properties.deadLetterQueue())
                .build();
    }

    @Bean
    Queue onboardingDeadLetterQueue(MessagingProperties properties) {
        return QueueBuilder.durable(properties.deadLetterQueue()).build();
    }

    @Bean
    Binding onboardingBinding(Queue onboardingQueue, TopicExchange employeeEventsExchange,
                              MessagingProperties properties) {
        return BindingBuilder.bind(onboardingQueue)
                .to(employeeEventsExchange)
                .with(properties.routingKey());
    }

    @Bean
    MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
