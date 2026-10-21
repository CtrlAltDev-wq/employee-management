package com.wangqi.employee.dto;

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
public record EmployeeSearchCriteria(
        String firstName,
        String lastName,
        String email,
        String jobTitle,
        Long departmentId,
        BigDecimal minSalary,
        BigDecimal maxSalary
) {
}
