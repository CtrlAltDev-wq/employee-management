package com.wangqi.employee.service;

import com.wangqi.employee.dto.EmployeeRequest;
import com.wangqi.employee.dto.EmployeeResponse;

/**
 * Business operations available for employees.
 *
 * @author wangqi
 */
public interface EmployeeService {

    EmployeeResponse getEmployeeById(Long id);

    EmployeeResponse createEmployee(EmployeeRequest request);

    void deleteEmployee(Long id);
}
