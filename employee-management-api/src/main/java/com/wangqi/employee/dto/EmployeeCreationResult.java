package com.wangqi.employee.dto;

/**
 * Result of a create request, including whether it was served from an
 * idempotency record instead of creating a new employee.
 *
 * @author wangqi
 */
public record EmployeeCreationResult(
        EmployeeResponse employee,
        boolean replayed
) {
}
