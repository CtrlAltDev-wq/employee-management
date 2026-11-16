package com.wangqi.employee.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Outcome of an outbound synchronization attempt.
 *
 * @author wangqi
 */
@Schema(name = "EmployeeSyncResponse", description = "Outcome of pushing an employee to the external HR system")
public record EmployeeSyncResponse(

        @Schema(description = "Employee that was synchronised", example = "1")
        Long employeeId,

        @Schema(description = "Number of HTTP attempts actually made before success", example = "2")
        int attempts
) {
}
