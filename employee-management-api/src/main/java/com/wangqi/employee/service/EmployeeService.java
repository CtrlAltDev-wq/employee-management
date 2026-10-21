package com.wangqi.employee.service;

import com.wangqi.employee.dto.EmployeeCreationResult;
import com.wangqi.employee.dto.EmployeeRequest;
import com.wangqi.employee.dto.EmployeeResponse;

/**
 * Business operations available for employees.
 *
 * @author wangqi
 */
public interface EmployeeService {

    EmployeeResponse getEmployeeById(Long id);

    /**
     * @param idempotencyKey optional client-supplied key; when present, a
     *                       replay returns the original result instead of
     *                       creating a second employee
     */
    EmployeeCreationResult createEmployee(EmployeeRequest request, String idempotencyKey);

    void deleteEmployee(Long id);
}
