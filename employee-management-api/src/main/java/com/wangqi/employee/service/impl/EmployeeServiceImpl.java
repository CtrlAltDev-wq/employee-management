package com.wangqi.employee.service.impl;

import com.wangqi.employee.config.CacheConfig;
import com.wangqi.employee.dto.EmployeeCreationResult;
import com.wangqi.employee.dto.EmployeeRequest;
import com.wangqi.employee.dto.EmployeeResponse;
import com.wangqi.employee.dto.EmployeeSearchCriteria;
import com.wangqi.employee.dto.PageResponse;
import com.wangqi.employee.entity.Department;
import com.wangqi.employee.entity.Employee;
import com.wangqi.employee.entity.IdempotencyRecord;
import com.wangqi.employee.event.EmployeeCreatedEvent;
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
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Cacheable(cacheNames = CacheConfig.EMPLOYEES, key = "#id")
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
    @CacheEvict(cacheNames = CacheConfig.DEPARTMENTS, allEntries = true)
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

        Employee saved = employeeRepository.save(employee);
        // Picked up after commit by the async notification listener.
        eventPublisher.publishEvent(new EmployeeCreatedEvent(
                saved.getId(), saved.getFirstName() + " " + saved.getLastName(), saved.getEmail()));
        return EmployeeResponse.from(saved);
    }

    @Override
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConfig.EMPLOYEES, key = "#id"),
            @CacheEvict(cacheNames = CacheConfig.DEPARTMENTS, allEntries = true)
    })
    @Transactional
    public void deleteEmployee(Long id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new EmployeeNotFoundException(id));
        employeeRepository.delete(employee);
    }
}
