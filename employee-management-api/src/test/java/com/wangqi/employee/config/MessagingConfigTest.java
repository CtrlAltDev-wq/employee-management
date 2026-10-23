package com.wangqi.employee.config;

import com.wangqi.employee.messaging.EmployeeCreatedMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Asserts the broker topology and the message contract without a running
 * broker: declarations are plain objects, so their names, durability and
 * dead-letter wiring can be checked directly.
 *
 * @author wangqi
 */
class MessagingConfigTest {

    private static final MessagingProperties PROPERTIES = new MessagingProperties(
            true, "employee.events", "employee.created",
            "employee.created.onboarding", "employee.created.onboarding.dlq");

    private final RabbitMqConfig config = new RabbitMqConfig();

    @Test
    @DisplayName("the exchange is durable and topic-typed, so new consumers can bind by routing key")
    void shouldDeclareDurableTopicExchange() {
        TopicExchange exchange = config.employeeEventsExchange(PROPERTIES);

        assertThat(exchange.getName()).isEqualTo("employee.events");
        assertThat(exchange.getType()).isEqualTo("topic");
        assertThat(exchange.isDurable()).isTrue();
    }

    @Test
    @DisplayName("the onboarding queue is durable and parks rejected messages on a dead-letter queue")
    void shouldDeclareQueueWithDeadLetterQueue() {
        Queue queue = config.onboardingQueue(PROPERTIES);

        assertThat(queue.getName()).isEqualTo("employee.created.onboarding");
        assertThat(queue.isDurable()).isTrue();
        assertThat(queue.getArguments())
                .containsEntry("x-dead-letter-exchange", "")
                .containsEntry("x-dead-letter-routing-key", "employee.created.onboarding.dlq");
        assertThat(config.onboardingDeadLetterQueue(PROPERTIES).isDurable()).isTrue();
    }

    @Test
    @DisplayName("the binding routes employee.created to the onboarding queue")
    void shouldBindQueueWithRoutingKey() {
        Binding binding = config.onboardingBinding(
                config.onboardingQueue(PROPERTIES),
                config.employeeEventsExchange(PROPERTIES),
                PROPERTIES);

        assertThat(binding.getExchange()).isEqualTo("employee.events");
        assertThat(binding.getDestination()).isEqualTo("employee.created.onboarding");
        assertThat(binding.getRoutingKey()).isEqualTo("employee.created");
    }

    @Test
    @DisplayName("the JSON converter round-trips the event payload")
    void shouldRoundTripMessagePayload() {
        MessageConverter converter = config.jsonMessageConverter();
        EmployeeCreatedMessage original = new EmployeeCreatedMessage(
                42L, "Alice Chen", "alice@example.com", Instant.parse("2026-10-23T02:43:06Z"));

        Message amqpMessage = converter.toMessage(original, new MessageProperties());
        Object restored = converter.fromMessage(amqpMessage);

        assertThat(restored).isEqualTo(original);
    }
}
