package com.wangqi.employee.exception;

/**
 * Thrown when an employee cannot be found for the requested identifier.
 * Mapped to HTTP 404 Not Found by {@link GlobalExceptionHandler}.
 *
 * @author wangqi
 */
public class EmployeeNotFoundException extends RuntimeException {

    public EmployeeNotFoundException(Long id) {
        super("Employee not found with id: " + id);
    }
}
