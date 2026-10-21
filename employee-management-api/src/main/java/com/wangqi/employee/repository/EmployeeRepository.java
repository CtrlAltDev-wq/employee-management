package com.wangqi.employee.repository;

import com.wangqi.employee.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link Employee}.
 *
 * <p>{@link JpaSpecificationExecutor} backs the dynamic, paginated search
 * endpoint: filters are compiled into a single SQL statement instead of being
 * applied in memory.
 *
 * @author wangqi
 */
@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long>,
        JpaSpecificationExecutor<Employee> {

    boolean existsByEmail(String email);
}
