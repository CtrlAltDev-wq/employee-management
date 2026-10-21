package com.wangqi.employee.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload used to create a department.
 *
 * @author wangqi
 */
public record DepartmentRequest(

        @NotBlank(message = "Department name is required")
        @Size(max = 100, message = "Department name must not exceed 100 characters")
        String name
) {
}
