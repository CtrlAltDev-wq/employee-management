package com.wangqi.employee.dto;

/**
 * Outcome of an outbound synchronization attempt.
 *
 * @author wangqi
 */
public record EmployeeSyncResponse(
        Long employeeId,
        int attempts
) {
}
