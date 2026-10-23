package com.wangqi.employee.messaging;

import com.wangqi.employee.config.MessagingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Publishes {@code employee.created} to RabbitMQ.
 *
 * <p>The template is configured with correlated publisher confirms, so a broker
 * nack is logged instead of being silently lost.
 *
 * @author wangqi
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AmqpEmployeeEventPublisher implements EmployeeEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final MessagingProperties properties;

    @Override
    public void publish(EmployeeCreatedMessage message) {
        rabbitTemplate.convertAndSend(properties.exchange(), properties.routingKey(), message);
        log.info("Published {} for employee {} to exchange '{}'",
                properties.routingKey(), message.employeeId(), properties.exchange());
    }
}
