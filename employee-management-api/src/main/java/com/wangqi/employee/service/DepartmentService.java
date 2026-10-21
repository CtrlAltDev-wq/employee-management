package com.wangqi.employee.service;

import com.wangqi.employee.dto.DepartmentRequest;
import com.wangqi.employee.dto.DepartmentResponse;

import java.util.List;

/**
 * Business operations available for departments.
 *
 * @author wangqi
 */
public interface DepartmentService {

    DepartmentResponse createDepartment(DepartmentRequest request);

    List<DepartmentResponse> getAllDepartmentsWithEmployees();
}
