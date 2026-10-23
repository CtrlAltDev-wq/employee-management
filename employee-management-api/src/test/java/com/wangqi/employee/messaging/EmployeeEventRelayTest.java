package com.wangqi.employee.messaging;

import com.wangqi.employee.event.EmployeeCreatedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for the domain-event to broker bridge.
 *
 * @author wangqi
 */
class EmployeeEventRelayTest {

    private final EmployeeEventPublisher publisher = mock(EmployeeEventPublisher.class);
    private final EmployeeEventRelay relay = new EmployeeEventRelay(publisher);

    @Test
    @DisplayName("a committed employee event is mapped to a flat message and published")
    void shouldPublishMappedMessage() {
        relay.onEmployeeCreated(new EmployeeCreatedEvent(42L, "Alice Chen", "alice@example.com"));

        ArgumentCaptor<EmployeeCreatedMessage> captor = ArgumentCaptor.forClass(EmployeeCreatedMessage.class);
        verify(publisher).publish(captor.capture());

        EmployeeCreatedMessage published = captor.getValue();
        assertThat(published.employeeId()).isEqualTo(42L);
        assertThat(published.fullName()).isEqualTo("Alice Chen");
        assertThat(published.email()).isEqualTo("alice@example.com");
        assertThat(published.occurredAt()).isNotNull();
    }

    @Test
    @DisplayName("a broker outage is logged instead of failing an already committed request")
    void shouldSwallowBrokerFailure() {
        doThrow(new IllegalStateException("broker down")).when(publisher).publish(any());

        assertThatCode(() -> relay.onEmployeeCreated(
                new EmployeeCreatedEvent(7L, "Bob Li", "bob@example.com")))
                .doesNotThrowAnyException();
    }
}
