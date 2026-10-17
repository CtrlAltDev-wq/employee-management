package com.wangqi.employee.dto;

import com.wangqi.employee.entity.Employee;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Response payload returned to API clients. Keeps the persistence model
 * decoupled from the public API contract.
 *
 * @author wangqi
 */
public record EmployeeResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        String jobTitle,
        BigDecimal salary,
        Instant createdAt
) {

    public static EmployeeResponse from(Employee employee) {
        return new EmployeeResponse(
                employee.getId(),
                employee.getFirstName(),
                employee.getLastName(),
                employee.getEmail(),
                employee.getJobTitle(),
                employee.getSalary(),
                employee.getCreatedAt()
        );
    }
}
