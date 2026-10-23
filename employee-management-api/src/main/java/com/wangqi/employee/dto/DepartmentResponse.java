package com.wangqi.employee.dto;

import com.wangqi.employee.entity.Department;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Department representation including its employees.
 *
 * <p>The employee list is materialised as a concrete {@code ArrayList} because
 * the response is cached with type information enabled: the JDK-internal
 * immutable list returned by {@code Stream.toList()} cannot be reconstructed on
 * deserialisation.
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
                .collect(Collectors.toCollection(ArrayList::new));

        return new DepartmentResponse(department.getId(), department.getName(), employees);
    }
}
