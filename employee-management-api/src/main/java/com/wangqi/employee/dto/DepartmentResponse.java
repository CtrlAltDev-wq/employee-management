package com.wangqi.employee.dto;

import com.wangqi.employee.entity.Department;

import java.util.List;

/**
 * Department representation including its employees.
 *
 * @author wangqi
 */
public record DepartmentResponse(
        Long id,
        String name,
        List<EmployeeSummary> employees
) {

    public static DepartmentResponse from(Department department) {
        List<EmployeeSummary> employees = department.getEmployees().stream()
                .map(EmployeeSummary::from)
                .toList();

        return new DepartmentResponse(department.getId(), department.getName(), employees);
    }
}
