package com.wangqi.employee.service.impl;

import com.wangqi.employee.config.CacheConfig;
import com.wangqi.employee.dto.DepartmentRequest;
import com.wangqi.employee.dto.DepartmentResponse;
import com.wangqi.employee.entity.Department;
import com.wangqi.employee.exception.DuplicateDepartmentException;
import com.wangqi.employee.repository.DepartmentRepository;
import com.wangqi.employee.service.DepartmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Default {@link DepartmentService} implementation.
 *
 * @author wangqi
 */
@Service
@RequiredArgsConstructor
public class DepartmentServiceImpl implements DepartmentService {

    private final DepartmentRepository departmentRepository;

    @Override
    @CacheEvict(cacheNames = CacheConfig.DEPARTMENTS, allEntries = true)
    @Transactional
    public DepartmentResponse createDepartment(DepartmentRequest request) {
        if (departmentRepository.existsByName(request.name())) {
            throw new DuplicateDepartmentException(request.name());
        }

        Department department = Department.builder()
                .name(request.name())
                .build();

        return DepartmentResponse.from(departmentRepository.save(department));
    }

    /**
     * Uses the entity-graph fetch so the whole aggregate is loaded in one
     * statement. The mapping happens inside the transaction, while the
     * association is guaranteed to be initialised.
     *
     * <p>Returns a concrete {@code ArrayList} rather than an immutable
     * {@code Stream.toList()} result: the cached value is serialised with type
     * information, and a JDK-internal immutable list class cannot be
     * reconstructed on deserialisation.
     */
    @Override
    @Cacheable(cacheNames = CacheConfig.DEPARTMENTS)
    @Transactional(readOnly = true)
    public List<DepartmentResponse> getAllDepartmentsWithEmployees() {
        return departmentRepository.findAllWithEmployees().stream()
                .map(DepartmentResponse::from)
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
