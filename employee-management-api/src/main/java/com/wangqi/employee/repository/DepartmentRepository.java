package com.wangqi.employee.repository;

import com.wangqi.employee.entity.Department;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link Department}.
 *
 * @author wangqi
 */
@Repository
public interface DepartmentRepository extends JpaRepository<Department, Long> {

    boolean existsByName(String name);

    /**
     * Loads every department together with its employees in a <b>single</b>
     * query. Without the {@link EntityGraph}, iterating {@code getEmployees()}
     * over the result triggers one additional SELECT per department (N+1).
     */
    @EntityGraph(attributePaths = "employees")
    @Query("select distinct d from Department d")
    List<Department> findAllWithEmployees();
}
