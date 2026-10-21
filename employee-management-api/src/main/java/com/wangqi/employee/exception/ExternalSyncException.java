package com.wangqi.employee.exception;

/**
 * Thrown when an outbound integration keeps failing after every retry.
 * Mapped to HTTP 502 Bad Gateway.
 *
 * @author wangqi
 */
public class ExternalSyncException extends RuntimeException {

    public ExternalSyncException(String message, Throwable cause) {
        super(message, cause);
    }
}
