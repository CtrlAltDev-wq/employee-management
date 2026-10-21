package com.wangqi.employee.integration;

import com.wangqi.employee.config.ExternalHrProperties;
import com.wangqi.employee.exception.ExternalSyncException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Pushes employee data to an external HR system.
 *
 * <p>Retry policy:
 * <ul>
 *   <li>retries timeouts / connection failures and 5xx responses;</li>
 *   <li>never retries 4xx responses — they are deterministic client errors and
 *       retrying only multiplies the damage;</li>
 *   <li>uses exponential backoff, capped at {@code maxAttempts}.</li>
 * </ul>
 *
 * @author wangqi
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExternalHrClient {

    static final String EMPLOYEES_PATH = "/api/hr/employees";

    private final RestClient externalHrRestClient;
    private final ExternalHrProperties properties;

    /**
     * @return the number of attempts it took to succeed
     * @throws ExternalSyncException when every attempt fails
     * @throws HttpClientErrorException for a non-retryable 4xx response
     */
    public int pushEmployee(ExternalEmployeePayload payload) {
        RestClientException lastFailure = null;

        for (int attempt = 1; attempt <= properties.maxAttempts(); attempt++) {
            try {
                externalHrRestClient.post()
                        .uri(EMPLOYEES_PATH)
                        .body(payload)
                        .retrieve()
                        .toBodilessEntity();

                log.info("External HR sync succeeded for employee {} on attempt {}",
                        payload.employeeId(), attempt);
                return attempt;

            } catch (HttpClientErrorException ex) {
                // 4xx: the payload or credentials are wrong. Retrying cannot help.
                throw new ExternalSyncException(
                        "External HR rejected the request with status "
                                + ex.getStatusCode().value() + " (not retried)", ex);

            } catch (HttpServerErrorException | ResourceAccessException ex) {
                lastFailure = ex;
                log.warn("External HR sync failed for employee {} on attempt {}/{}: {}",
                        payload.employeeId(), attempt, properties.maxAttempts(), ex.getMessage());

                if (attempt < properties.maxAttempts()) {
                    backoff(attempt);
                }
            }
        }

        throw new ExternalSyncException(
                "External HR sync failed after " + properties.maxAttempts() + " attempts",
                lastFailure);
    }

    private void backoff(int attempt) {
        long delayMillis = properties.initialBackoff().toMillis() * (1L << (attempt - 1));

        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ExternalSyncException("Interrupted while backing off before retry", ex);
        }
    }
}
