package com.wangqi.contracts.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The contract is shared between services, so the two things that can silently
 * break a deployment are asserted here: the JSON shape and the class name that
 * travels in the {@code __TypeId__} AMQP header.
 *
 * @author wangqi
 */
class EmployeeCreatedMessageTest {

    private static final String TYPE_ID = "com.wangqi.contracts.messaging.EmployeeCreatedMessage";

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    @Test
    @DisplayName("the event round-trips through JSON without losing fields")
    void shouldRoundTripThroughJson() throws Exception {
        EmployeeCreatedMessage original = new EmployeeCreatedMessage(
                42L, "Alice Chen", "alice@example.com", Instant.parse("2026-10-23T02:43:06Z"));

        String json = objectMapper.writeValueAsString(original);
        EmployeeCreatedMessage restored = objectMapper.readValue(json, EmployeeCreatedMessage.class);

        assertThat(restored).isEqualTo(original);
        assertThat(json).contains("\"employeeId\":42", "\"occurredAt\":\"2026-10-23T02:43:06Z\"");
    }

    @Test
    @DisplayName("the fully qualified name is stable because it is sent as __TypeId__")
    void shouldKeepTheDocumentedFullyQualifiedName() {
        assertThat(EmployeeCreatedMessage.class.getName()).isEqualTo(TYPE_ID);
    }
}
