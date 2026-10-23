package com.wangqi.employee.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Broker topology for the employee event stream.
 *
 * <ul>
 *   <li>a durable topic exchange, so a routing key can fan the event out to
 *       additional consumers later without touching the publisher;</li>
 *   <li>a durable queue bound to {@code employee.created} — durable means the
 *       event survives a broker restart, unlike an in-memory queue;</li>
 *   <li>a dead-letter queue, so a message the consumer rejects is parked for
 *       inspection instead of being redelivered forever;</li>
 *   <li>JSON payloads, so the contract stays language-neutral and readable in
 *       the management UI.</li>
 * </ul>
 *
 * @author wangqi
 */
@Slf4j
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

    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        // Correlated confirms are enabled in application.yml; without this
        // callback a nack would only exist in the broker's metrics.
        template.setConfirmCallback((correlation, ack, cause) -> {
            if (!ack) {
                log.error("Broker rejected a message (correlation={}): {}",
                        correlation != null ? correlation.getId() : "-", cause);
            }
        });
        return template;
    }
}
