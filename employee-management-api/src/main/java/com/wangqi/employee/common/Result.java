package com.wangqi.employee.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.wangqi.employee.observability.CorrelationIdFilter;
import io.swagger.v3.oas.annotations.media.Schema;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Map;

/**
 * Single response envelope for every endpoint, success or failure.
 *
 * <p>Success and error bodies share the same top-level shape, so a client can
 * use one parser and one interceptor instead of branching on the HTTP status.
 * The HTTP status code is still authoritative: {@code code} mirrors it rather
 * than replacing it, which keeps {@code 201 Created}, {@code 404 Not Found} and
 * {@code 502 Bad Gateway} meaningful for caches, proxies and monitoring.
 *
 * <p>Empty fields are omitted, so a plain success body stays small:
 * {@code {"code":200,"message":"OK","data":{...},"timestamp":"..."}}.
 *
 * @param code        HTTP status code, mirrored into the body
 * @param message     human-readable outcome, safe to display to an end user
 * @param data        payload; {@code null} for failures and for deletes
 * @param path        request URI that produced the error
 * @param requestId   correlation id, also returned as {@code X-Request-Id}
 * @param fieldErrors field name to validation message, for {@code 400} only
 * @param timestamp   server time the body was produced
 * @param <T>         payload type
 * @author wangqi
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(name = "Result", description = "Uniform response envelope shared by success and error responses")
public record Result<T>(

        @Schema(description = "HTTP status code, mirrored from the response status",
                example = "200")
        int code,

        @Schema(description = "Human-readable outcome message",
                example = "OK")
        String message,

        @Schema(description = "Response payload; absent when there is nothing to return")
        T data,

        @Schema(description = "Request URI that produced the response; present on errors",
                example = "/api/employees/99")
        String path,

        @Schema(description = "Correlation id of the request, also sent as the X-Request-Id header",
                example = "3b0f9c4e-8f7a-4c2b-9a1e-1f0b2c3d4e5f")
        String requestId,

        @Schema(description = "Field name to validation message; present on 400 responses",
                example = "{\"email\":\"Email must be a well-formed email address\"}")
        Map<String, String> fieldErrors,

        @Schema(description = "Server time the body was produced",
                example = "2024-01-01T10:15:30Z")
        Instant timestamp
) {

    private static final String SUCCESS_MESSAGE = "OK";

    /**
     * {@code 200 OK} with a payload.
     */
    public static <T> Result<T> ok(T data) {
        return ok(SUCCESS_MESSAGE, data);
    }

    /**
     * {@code 200 OK} with a custom message and a payload.
     */
    public static <T> Result<T> ok(String message, T data) {
        return new Result<>(HttpStatus.OK.value(), message, data, null, currentRequestId(), null, Instant.now());
    }

    /**
     * {@code 201 Created} with the created resource.
     */
    public static <T> Result<T> created(T data) {
        return new Result<>(HttpStatus.CREATED.value(), HttpStatus.CREATED.getReasonPhrase(),
                data, null, currentRequestId(), null, Instant.now());
    }

    /**
     * Failure without field-level detail.
     */
    public static Result<Void> failure(HttpStatus status, String message, String path) {
        return failure(status, message, path, null);
    }

    /**
     * Failure carrying per-field validation messages.
     */
    public static Result<Void> failure(HttpStatus status, String message, String path,
                                       Map<String, String> fieldErrors) {
        return new Result<>(status.value(), message, null, path, currentRequestId(), fieldErrors, Instant.now());
    }

    private static String currentRequestId() {
        return MDC.get(CorrelationIdFilter.MDC_KEY);
    }
}
