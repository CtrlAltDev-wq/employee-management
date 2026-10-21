package com.wangqi.employee.integration;

import com.wangqi.employee.dto.EmployeeResponse;

/**
 * Wire format expected by the third-party HR system.
 *
 * @author wangqi
 */
public record ExternalEmployeePayload(
        Long employeeId,
        String fullName,
        String email,
        String jobTitle
) {

    public static ExternalEmployeePayload from(EmployeeResponse employee) {
        return new ExternalEmployeePayload(
                employee.id(),
                employee.firstName() + " " + employee.lastName(),
                employee.email(),
                employee.jobTitle()
        );
    }
}
