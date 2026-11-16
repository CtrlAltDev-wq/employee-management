package com.wangqi.employee.dto;

import com.wangqi.employee.entity.Employee;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Response payload returned to API clients. Keeps the persistence model
 * decoupled from the public API contract.
 *
 * @author wangqi
 */
@Schema(name = "EmployeeResponse", description = "An employee as exposed by the API")
public record EmployeeResponse(

        @Schema(description = "Generated id", example = "1")
        Long id,

        @Schema(description = "Given name", example = "Qi")
        String firstName,

        @Schema(description = "Family name", example = "Wang")
        String lastName,

        @Schema(description = "Unique work email", example = "wangqi@example.com")
        String email,

        @Schema(description = "Job title", example = "Backend Engineer")
        String jobTitle,

        @Schema(description = "Annual salary", example = "5000.00")
        BigDecimal salary,

        @Schema(description = "Department id; absent when the employee is unassigned", example = "1")
        Long departmentId,

        @Schema(description = "Creation time in UTC", example = "2024-01-01T10:15:30Z")
        Instant createdAt
) {

    public static EmployeeResponse from(Employee employee) {
        // The association is lazy: reading the identifier through the proxy does
        // not initialise it, so mapping a page of employees issues no extra query.
        Long departmentId = employee.getDepartment() == null ? null : employee.getDepartment().getId();

        return new EmployeeResponse(
                employee.getId(),
                employee.getFirstName(),
                employee.getLastName(),
                employee.getEmail(),
                employee.getJobTitle(),
                employee.getSalary(),
                departmentId,
                employee.getCreatedAt()
        );
    }
}
