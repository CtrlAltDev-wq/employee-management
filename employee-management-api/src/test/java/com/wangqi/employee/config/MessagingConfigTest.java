package com.wangqi.employee.config;

import com.wangqi.contracts.messaging.EmployeeCreatedMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.MessageConverter;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Asserts the producer-side topology and the message contract without a running
 * broker: declarations are plain objects, so their names and durability can be
 * checked directly.
 *
 * <p>The queue, DLQ and binding moved to the consuming service; they are
 * asserted in {@code RabbitMqTopologyTest} over there.
 *
 * @author wangqi
 */
class MessagingConfigTest {

    private static final MessagingProperties PROPERTIES = new MessagingProperties(
            true, "employee.events", "employee.created");

    private final RabbitMqConfig config = new RabbitMqConfig();

    @Test
    @DisplayName("the producer declares the durable topic exchange that consumers bind to")
    void shouldDeclareDurableTopicExchange() {
        TopicExchange exchange = config.employeeEventsExchange(PROPERTIES);

        assertThat(exchange.getName()).isEqualTo("employee.events");
        assertThat(exchange.getType()).isEqualTo("topic");
        assertThat(exchange.isDurable()).isTrue();
    }

    @Test
    @DisplayName("the JSON converter round-trips the shared contract with its type header")
    void shouldRoundTripMessagePayload() {
        MessageConverter converter = config.jsonMessageConverter();
        EmployeeCreatedMessage original = new EmployeeCreatedMessage(
                42L, "Alice Chen", "alice@example.com", Instant.parse("2026-10-23T02:43:06Z"));

        Message amqpMessage = converter.toMessage(original, new MessageProperties());
        Object restored = converter.fromMessage(amqpMessage);
        Object typeId = amqpMessage.getMessageProperties().getHeader("__TypeId__");

        assertThat(typeId).isEqualTo("com.wangqi.contracts.messaging.EmployeeCreatedMessage");
        assertThat(restored).isEqualTo(original);
    }
}
