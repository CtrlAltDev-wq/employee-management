package com.wangqi.employee.dto;

import com.wangqi.employee.entity.Department;
import io.swagger.v3.oas.annotations.media.Schema;

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
@Schema(name = "DepartmentResponse", description = "A department and the employees assigned to it")
public record DepartmentResponse(

        @Schema(description = "Generated id", example = "1")
        Long id,

        @Schema(description = "Unique department name", example = "Engineering")
        String name,

        @Schema(description = "Employees assigned to this department; empty when none are assigned")
        List<EmployeeSummary> employees
) {

    public static DepartmentResponse from(Department department) {
        List<EmployeeSummary> employees = department.getEmployees().stream()
                .map(EmployeeSummary::from)
                .collect(Collectors.toCollection(ArrayList::new));

        return new DepartmentResponse(department.getId(), department.getName(), employees);
    }
}
