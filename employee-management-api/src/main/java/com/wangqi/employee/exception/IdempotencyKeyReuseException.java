package com.wangqi.employee.exception;

/**
 * Thrown when the same {@code Idempotency-Key} is reused with a different
 * request payload, which almost always indicates a client bug or key collision.
 * Mapped to HTTP 409 Conflict.
 *
 * @author wangqi
 */
public class IdempotencyKeyReuseException extends RuntimeException {

    public IdempotencyKeyReuseException(String key) {
        super("Idempotency-Key '" + key + "' was already used with a different request payload.");
    }
}
