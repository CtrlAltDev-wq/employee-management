package com.wangqi.employee.repository;

import com.wangqi.employee.entity.Department;
import com.wangqi.employee.entity.Employee;
import com.wangqi.employee.support.QueryCounter;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the N+1 fix with a measurable query count instead of a claim.
 * The test seeds 3 departments with 5 employees each, clears the persistence
 * context, then compares the naive lazy-loading path with the entity-graph path.
 *
 * @author wangqi
 */
@DataJpaTest(properties =
        "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                + "com.wangqi.employee.support.QueryCounter")
class DepartmentQueryCountTest {

    private static final int DEPARTMENTS = 3;
    private static final int EMPLOYEES_PER_DEPARTMENT = 5;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private TestEntityManager testEntityManager;

    @BeforeEach
    void seedAndResetCounter() {
        EntityManager entityManager = testEntityManager.getEntityManager();

        for (int d = 1; d <= DEPARTMENTS; d++) {
            Department department = Department.builder()
                    .name("Department " + d)
                    .build();
            entityManager.persist(department);

            for (int e = 1; e <= EMPLOYEES_PER_DEPARTMENT; e++) {
                entityManager.persist(Employee.builder()
                        .firstName("First" + e)
                        .lastName("Last" + d)
                        .email("d" + d + ".e" + e + "@example.com")
                        .jobTitle("Engineer")
                        .salary(new BigDecimal("5000.00"))
                        .department(department)
                        .build());
            }
        }

        testEntityManager.flush();
        testEntityManager.clear();
        QueryCounter.reset();
    }

    @Test
    @DisplayName("Naive lazy loading issues 1 + N queries (the N+1 problem)")
    void naiveLazyLoadingProducesNPlusOneQueries() {
        List<Department> departments = departmentRepository.findAll();

        departments.forEach(department ->
                assertThat(department.getEmployees()).hasSize(EMPLOYEES_PER_DEPARTMENT));

        // 1 query for the departments + 1 query per department for its employees.
        assertThat(QueryCounter.count()).isEqualTo(1 + DEPARTMENTS);
    }

    @Test
    @DisplayName("Entity graph loads the same data in exactly one query")
    void entityGraphLoadsEverythingInOneQuery() {
        List<Department> departments = departmentRepository.findAllWithEmployees();

        assertThat(departments).hasSize(DEPARTMENTS);
        departments.forEach(department ->
                assertThat(department.getEmployees()).hasSize(EMPLOYEES_PER_DEPARTMENT));

        assertThat(QueryCounter.count()).isEqualTo(1);
    }
}
