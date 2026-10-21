package com.wangqi.employee.integration;

import com.wangqi.employee.config.ExternalHrProperties;
import com.wangqi.employee.exception.ExternalSyncException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Retry-policy tests for {@link ExternalHrClient}, using a mocked HTTP server.
 *
 * @author wangqi
 */
class ExternalHrClientTest {

    private static final String ENDPOINT = "http://external-hr.test" + ExternalHrClient.EMPLOYEES_PATH;

    private final ExternalEmployeePayload payload =
            new ExternalEmployeePayload(1L, "Qi Wang", "qi@example.com", "Backend Engineer");

    private MockRestServiceServer server;
    private ExternalHrClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://external-hr.test");
        server = MockRestServiceServer.bindTo(builder).build();

        ExternalHrProperties properties = new ExternalHrProperties(
                "http://external-hr.test", 3, Duration.ofMillis(5), Duration.ofSeconds(1));
        client = new ExternalHrClient(builder.build(), properties);
    }

    @Test
    @DisplayName("retries transient failures and succeeds on the third attempt")
    void shouldSucceedAfterRetries() {
        server.expect(requestTo(ENDPOINT)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo(ENDPOINT)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo(ENDPOINT)).andRespond(withSuccess());

        int attempts = client.pushEmployee(payload);

        assertThat(attempts).isEqualTo(3);
        server.verify();
    }

    @Test
    @DisplayName("does not retry a 4xx response")
    void shouldNotRetryClientError() {
        server.expect(requestTo(ENDPOINT)).andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> client.pushEmployee(payload))
                .isInstanceOf(ExternalSyncException.class)
                .hasMessageContaining("400")
                .hasMessageContaining("not retried");

        // verify() would fail if the client had sent a second request.
        server.verify();
    }

    @Test
    @DisplayName("gives up after the configured number of attempts")
    void shouldGiveUpAfterMaxAttempts() {
        server.expect(ExpectedCount.times(3), requestTo(ENDPOINT))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.pushEmployee(payload))
                .isInstanceOf(ExternalSyncException.class)
                .hasMessageContaining("3 attempts");

        server.verify();
    }
}
