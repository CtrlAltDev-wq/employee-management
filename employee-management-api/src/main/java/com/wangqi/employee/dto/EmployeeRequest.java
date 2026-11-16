package com.wangqi.employee.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Payload used to create a new employee.
 * All constraints here are enforced by {@code @Valid} on the controller layer.
 *
 * @author wangqi
 */
@Schema(name = "EmployeeRequest", description = "Payload used to create an employee")
public record EmployeeRequest(

        @Schema(description = "Given name", example = "Qi", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 50)
        @NotBlank(message = "First name is required")
        @Size(max = 50, message = "First name must not exceed 50 characters")
        String firstName,

        @Schema(description = "Family name", example = "Wang", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 50)
        @NotBlank(message = "Last name is required")
        @Size(max = 50, message = "Last name must not exceed 50 characters")
        String lastName,

        @Schema(description = "Unique work email", example = "wangqi@example.com",
                requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 120)
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a well-formed email address")
        @Size(max = 120, message = "Email must not exceed 120 characters")
        String email,

        @Schema(description = "Job title shown in the UI", example = "Backend Engineer", maxLength = 100)
        @Size(max = 100, message = "Job title must not exceed 100 characters")
        String jobTitle,

        @Schema(description = "Annual salary; zero is allowed, negatives are not",
                example = "5000.00", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Salary is required")
        @DecimalMin(value = "0.0", message = "Salary must be greater than or equal to 0")
        BigDecimal salary,

        @Schema(description = "Optional department id; must exist when supplied", example = "1")
        @Positive(message = "Department id must be a positive number")
        Long departmentId
) {
}
