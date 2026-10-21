package com.wangqi.employee.dto;

import com.wangqi.employee.entity.Employee;

/**
 * Lightweight employee projection embedded in a department response.
 * Keeps the payload small and avoids exposing the full entity graph.
 *
 * @author wangqi
 */
public record EmployeeSummary(
        Long id,
        String firstName,
        String lastName,
        String email,
        String jobTitle
) {

    public static EmployeeSummary from(Employee employee) {
        return new EmployeeSummary(
                employee.getId(),
                employee.getFirstName(),
                employee.getLastName(),
                employee.getEmail(),
                employee.getJobTitle()
        );
    }
}
