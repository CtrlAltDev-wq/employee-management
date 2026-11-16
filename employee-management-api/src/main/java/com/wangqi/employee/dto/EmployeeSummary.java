package com.wangqi.employee.dto;

import com.wangqi.employee.entity.Employee;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Lightweight employee projection embedded in a department response.
 * Keeps the payload small and avoids exposing the full entity graph.
 *
 * @author wangqi
 */
@Schema(name = "EmployeeSummary", description = "Employee projection embedded in a department response")
public record EmployeeSummary(

        @Schema(description = "Employee id", example = "100")
        Long id,

        @Schema(description = "Given name", example = "Qi")
        String firstName,

        @Schema(description = "Family name", example = "Wang")
        String lastName,

        @Schema(description = "Work email", example = "wangqi@example.com")
        String email,

        @Schema(description = "Job title", example = "Backend Engineer")
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
