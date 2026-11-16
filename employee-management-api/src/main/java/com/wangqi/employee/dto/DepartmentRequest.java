package com.wangqi.employee.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload used to create a department.
 *
 * @author wangqi
 */
@Schema(name = "DepartmentRequest", description = "Payload used to create a department")
public record DepartmentRequest(

        @Schema(description = "Unique department name", example = "Engineering",
                requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 100)
        @NotBlank(message = "Department name is required")
        @Size(max = 100, message = "Department name must not exceed 100 characters")
        String name
) {
}
