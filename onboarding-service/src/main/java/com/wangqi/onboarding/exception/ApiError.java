package com.wangqi.onboarding.exception;

import org.springframework.http.HttpStatus;

import java.time.Instant;

/**
 * Error body of the onboarding service. Mirrors the employee API contract so a
 * client sees one error shape regardless of which service answered.
 *
 * @author wangqi
 */
public record ApiError(
        int status,
        String error,
        String message,
        String path,
        Instant timestamp
) {

    public static ApiError of(HttpStatus status, String message, String path) {
        return new ApiError(status.value(), status.getReasonPhrase(), message, path, Instant.now());
    }
}
