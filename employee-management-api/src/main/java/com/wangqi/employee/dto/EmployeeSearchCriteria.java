package com.wangqi.employee.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Optional filters for {@code GET /api/employees}. Every field is nullable;
 * absent filters are simply not applied.
 *
 * <p>Text filters are case-insensitive "contains" matches. Salary bounds are
 * inclusive.
 *
 * @author wangqi
 */
@Schema(name = "EmployeeSearchCriteria", description = "Optional filters for the employee search; omitted filters are not applied")
public record EmployeeSearchCriteria(

        @Schema(description = "Case-insensitive contains match on the given name", example = "Qi")
        @Size(max = 50, message = "firstName filter must not exceed 50 characters")
        String firstName,

        @Schema(description = "Case-insensitive contains match on the family name", example = "Wang")
        @Size(max = 50, message = "lastName filter must not exceed 50 characters")
        String lastName,

        @Schema(description = "Case-insensitive contains match on the email", example = "example.com")
        @Size(max = 120, message = "email filter must not exceed 120 characters")
        String email,

        @Schema(description = "Case-insensitive contains match on the job title", example = "engineer")
        @Size(max = 100, message = "jobTitle filter must not exceed 100 characters")
        String jobTitle,

        @Schema(description = "Exact department id", example = "1")
        Long departmentId,

        @Schema(description = "Inclusive lower salary bound", example = "5000")
        BigDecimal minSalary,

        @Schema(description = "Inclusive upper salary bound", example = "9000")
        BigDecimal maxSalary
) {

    /**
     * Cross-field constraint Bean Validation cannot express on a single
     * component: a range whose lower bound exceeds its upper bound can only
     * ever return an empty page, which is almost always a client bug.
     */
    @AssertTrue(message = "minSalary must be less than or equal to maxSalary")
    @Schema(hidden = true)
    public boolean isSalaryRangeValid() {
        return minSalary == null || maxSalary == null || minSalary.compareTo(maxSalary) <= 0;
    }
}
