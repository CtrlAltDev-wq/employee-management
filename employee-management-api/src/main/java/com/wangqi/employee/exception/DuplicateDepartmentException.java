package com.wangqi.employee.exception;

/**
 * Thrown when a department is created with a name that already exists.
 * Mapped to HTTP 409 Conflict by {@link GlobalExceptionHandler}.
 *
 * @author wangqi
 */
public class DuplicateDepartmentException extends RuntimeException {

    public DuplicateDepartmentException(String name) {
        super("Department already exists with name: " + name);
    }
}
