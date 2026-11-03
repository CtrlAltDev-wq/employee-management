package com.wangqi.employee.config;

import lombok.extern.slf4j.Slf4j;
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
 * Producer-side broker topology for the employee event stream.
 *
 * <p>Only the exchange is declared here. The onboarding queue, its DLQ and the
 * binding are declared by the consuming service: topology ownership follows the
 * consumer, so extracting that consumer into its own process required no change
 * to this class. Declaring the durable topic exchange on the producer side means
 * a publish never fails with {@code 404 NOT_FOUND} just because no consumer has
 * started yet.
 *
 * <p>Payloads are JSON so the contract stays language-neutral and readable in
 * the management UI.
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
