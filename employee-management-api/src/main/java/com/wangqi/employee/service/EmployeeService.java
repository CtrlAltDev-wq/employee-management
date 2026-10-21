package com.wangqi.employee.service;

import com.wangqi.employee.dto.EmployeeCreationResult;
import com.wangqi.employee.dto.EmployeeRequest;
import com.wangqi.employee.dto.EmployeeResponse;
import com.wangqi.employee.dto.EmployeeSearchCriteria;
import com.wangqi.employee.dto.PageResponse;
import org.springframework.data.domain.Pageable;

/**
 * Business operations available for employees.
 *
 * @author wangqi
 */
public interface EmployeeService {

    EmployeeResponse getEmployeeById(Long id);

    /**
     * Paginated, filtered search. Filtering and sorting happen in the database;
     * only the requested page is materialised.
     */
    PageResponse<EmployeeResponse> searchEmployees(EmployeeSearchCriteria criteria, Pageable pageable);

    /**
     * @param idempotencyKey optional client-supplied key; when present, a
     *                       replay returns the original result instead of
     *                       creating a second employee
     */
    EmployeeCreationResult createEmployee(EmployeeRequest request, String idempotencyKey);

    void deleteEmployee(Long id);
}
