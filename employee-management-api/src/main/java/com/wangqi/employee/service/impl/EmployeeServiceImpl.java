package com.wangqi.employee.service.impl;

import com.wangqi.employee.dto.EmployeeCreationResult;
import com.wangqi.employee.dto.EmployeeRequest;
import com.wangqi.employee.dto.EmployeeResponse;
import com.wangqi.employee.dto.EmployeeSearchCriteria;
import com.wangqi.employee.dto.PageResponse;
import com.wangqi.employee.entity.Department;
import com.wangqi.employee.entity.Employee;
import com.wangqi.employee.entity.IdempotencyRecord;
import com.wangqi.employee.exception.DepartmentNotFoundException;
import com.wangqi.employee.exception.DuplicateEmailException;
import com.wangqi.employee.exception.EmployeeNotFoundException;
import com.wangqi.employee.exception.IdempotencyKeyReuseException;
import com.wangqi.employee.repository.DepartmentRepository;
import com.wangqi.employee.repository.EmployeeRepository;
import com.wangqi.employee.repository.EmployeeSpecifications;
import com.wangqi.employee.service.EmployeeService;
import com.wangqi.employee.service.IdempotencyService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Default {@link EmployeeService} implementation.
 *
 * @author wangqi
 */
@Service
@RequiredArgsConstructor
public class EmployeeServiceImpl implements EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final IdempotencyService idempotencyService;

    @Override
    @Transactional(readOnly = true)
    public EmployeeResponse getEmployeeById(Long id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new EmployeeNotFoundException(id));
        return EmployeeResponse.from(employee);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<EmployeeResponse> searchEmployees(EmployeeSearchCriteria criteria, Pageable pageable) {
        Page<Employee> page = employeeRepository.findAll(EmployeeSpecifications.matching(criteria), pageable);
        return PageResponse.of(page.map(EmployeeResponse::from));
    }

    @Override
    @Transactional
    public EmployeeCreationResult createEmployee(EmployeeRequest request, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return new EmployeeCreationResult(create(request), false);
        }

        String requestHash = idempotencyService.fingerprint(request);
        Optional<IdempotencyRecord> existing = idempotencyService.find(idempotencyKey);

        if (existing.isPresent()) {
            IdempotencyRecord record = existing.get();
            if (!record.getRequestHash().equals(requestHash)) {
                throw new IdempotencyKeyReuseException(idempotencyKey);
            }
            return new EmployeeCreationResult(idempotencyService.readResponse(record), true);
        }

        EmployeeResponse created = create(request);
        // Stored in the same transaction as the employee: either both persist or neither does.
        idempotencyService.remember(idempotencyKey, requestHash, created);
        return new EmployeeCreationResult(created, false);
    }

    private EmployeeResponse create(EmployeeRequest request) {
        if (employeeRepository.existsByEmail(request.email())) {
            throw new DuplicateEmailException(request.email());
        }

        Department department = null;
        if (request.departmentId() != null) {
            department = departmentRepository.findById(request.departmentId())
                    .orElseThrow(() -> new DepartmentNotFoundException(request.departmentId()));
        }

        Employee employee = Employee.builder()
                .firstName(request.firstName())
                .lastName(request.lastName())
                .email(request.email())
                .jobTitle(request.jobTitle())
                .salary(request.salary())
                .department(department)
                .build();

        return EmployeeResponse.from(employeeRepository.save(employee));
    }

    @Override
    @Transactional
    public void deleteEmployee(Long id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new EmployeeNotFoundException(id));
        employeeRepository.delete(employee);
    }
}
