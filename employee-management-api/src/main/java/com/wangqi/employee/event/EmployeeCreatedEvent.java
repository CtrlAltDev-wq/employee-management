package com.wangqi.employee.event;

/**
 * Published after an employee has been persisted. Listeners run outside the
 * request thread, so notification work cannot slow down or fail the create.
 *
 * @author wangqi
 */
public record EmployeeCreatedEvent(Long employeeId, String fullName, String email) {
}
