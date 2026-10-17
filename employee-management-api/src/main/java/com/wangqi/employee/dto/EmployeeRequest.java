package com.wangqi.employee.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Payload used to create a new employee.
 * All constraints here are enforced by {@code @Valid} on the controller layer.
 *
 * @author wangqi
 */
public record EmployeeRequest(

        @NotBlank(message = "First name is required")
        @Size(max = 50, message = "First name must not exceed 50 characters")
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(max = 50, message = "Last name must not exceed 50 characters")
        String lastName,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a well-formed email address")
        @Size(max = 120, message = "Email must not exceed 120 characters")
        String email,

        @Size(max = 100, message = "Job title must not exceed 100 characters")
        String jobTitle,

        @NotNull(message = "Salary is required")
        @Min(value = 0, message = "Salary must be greater than or equal to 0")
        BigDecimal salary
) {
}
