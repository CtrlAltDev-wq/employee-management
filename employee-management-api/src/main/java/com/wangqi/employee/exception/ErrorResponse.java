package com.wangqi.employee.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.wangqi.employee.observability.CorrelationIdFilter;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Map;

/**
 * Uniform error payload returned by every failing request, so that clients
 * always receive a predictable JSON structure instead of a raw stack trace.
 *
 * <p>{@code requestId} is the same correlation id returned in the
 * {@code X-Request-Id} response header, so a client can quote it when reporting
 * an issue and it can be grepped straight out of the logs.
 *
 * @author wangqi
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        String requestId,
        Map<String, String> fieldErrors
) {

    public static ErrorResponse of(HttpStatus status, String message, String path) {
        return new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(),
                message, path, currentRequestId(), null);
    }

    public static ErrorResponse of(HttpStatus status, String message, String path, Map<String, String> fieldErrors) {
        return new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(),
                message, path, currentRequestId(), fieldErrors);
    }

    private static String currentRequestId() {
        return MDC.get(CorrelationIdFilter.MDC_KEY);
    }
}
