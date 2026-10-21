package com.wangqi.employee.exception;

/**
 * Thrown when a department cannot be found for the requested identifier.
 * Mapped to HTTP 404 Not Found by {@link GlobalExceptionHandler}.
 *
 * @author wangqi
 */
public class DepartmentNotFoundException extends RuntimeException {

    public DepartmentNotFoundException(Long id) {
        super("Department not found with id: " + id);
    }
}
