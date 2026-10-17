package com.wangqi.employee.exception;

/**
 * Thrown when an employee is created with an email that is already registered.
 * Mapped to HTTP 409 Conflict by {@link GlobalExceptionHandler}.
 *
 * @author wangqi
 */
public class DuplicateEmailException extends RuntimeException {

    public DuplicateEmailException(String email) {
        super("Employee already exists with email: " + email);
    }
}
