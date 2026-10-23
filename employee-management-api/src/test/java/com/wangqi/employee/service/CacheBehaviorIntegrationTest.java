package com.wangqi.employee.service;

import com.wangqi.employee.config.CacheConfig;
import com.wangqi.employee.dto.DepartmentResponse;
import com.wangqi.employee.dto.EmployeeRequest;
import com.wangqi.employee.dto.EmployeeResponse;
import com.wangqi.employee.entity.Department;
import com.wangqi.employee.entity.Employee;
import com.wangqi.employee.exception.EmployeeNotFoundException;
import com.wangqi.employee.repository.DepartmentRepository;
import com.wangqi.employee.repository.EmployeeRepository;
import com.wangqi.employee.support.QueryCounter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.interceptor.SimpleKey;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the cache actually removes database work, and that every write that
 * can change a cached view evicts it. The cache is an in-memory
 * {@code ConcurrentMapCacheManager} (see {@code src/test/resources/application.properties}):
 * the caching annotations are the same ones Redis serves in production, so the
 * behaviour under test is the real behaviour.
 *
 * <p>Statements are counted with Hibernate's {@code StatementInspector}: the
 * second read must send <b>zero</b> SQL to the database.
 *
 * @author wangqi
 */
@SpringBootTest(properties = {
        "app.demo-users.enabled=false",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                + "com.wangqi.employee.support.QueryCounter"
})
class CacheBehaviorIntegrationTest {

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private DepartmentService departmentService;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void resetState() {
        employeeRepository.deleteAll();
        departmentRepository.deleteAll();
        cacheManager.getCache(CacheConfig.EMPLOYEES).clear();
        cacheManager.getCache(CacheConfig.DEPARTMENTS).clear();
        QueryCounter.reset();
    }

    @Test
    @DisplayName("The second read of the same employee is served from the cache, not the database")
    void secondReadOfEmployeeIsServedFromCache() {
        Employee saved = employeeRepository.save(employee("alice@example.com"));
        QueryCounter.reset();

        EmployeeResponse first = employeeService.getEmployeeById(saved.getId());
        int statementsForFirstRead = QueryCounter.count();

        EmployeeResponse second = employeeService.getEmployeeById(saved.getId());

        assertThat(statementsForFirstRead).isEqualTo(1);
        assertThat(QueryCounter.count()).isEqualTo(1);
        assertThat(second).isEqualTo(first);
        assertThat(cacheManager.getCache(CacheConfig.EMPLOYEES).get(saved.getId())).isNotNull();
    }

    @Test
    @DisplayName("The department listing is cached and reuses the entity-graph query only once")
    void departmentListingIsCached() {
        Department department = departmentRepository.save(Department.builder().name("Engineering").build());
        employeeRepository.save(employeeWithDepartment("bob@example.com", department));
        employeeRepository.save(employeeWithDepartment("carol@example.com", department));
        QueryCounter.reset();

        List<DepartmentResponse> first = departmentService.getAllDepartmentsWithEmployees();
        int statementsForFirstRead = QueryCounter.count();

        List<DepartmentResponse> second = departmentService.getAllDepartmentsWithEmployees();

        assertThat(statementsForFirstRead).isEqualTo(1);
        assertThat(QueryCounter.count()).isEqualTo(1);
        assertThat(second).isEqualTo(first);
        assertThat(first.get(0).employees()).hasSize(2);
    }

    @Test
    @DisplayName("Deleting an employee evicts its cache entry")
    void deleteEvictsEmployeeCache() {
        Employee saved = employeeRepository.save(employee("dave@example.com"));
        employeeService.getEmployeeById(saved.getId());
        assertThat(cacheManager.getCache(CacheConfig.EMPLOYEES).get(saved.getId())).isNotNull();

        employeeService.deleteEmployee(saved.getId());

        assertThat(cacheManager.getCache(CacheConfig.EMPLOYEES).get(saved.getId())).isNull();

        QueryCounter.reset();
        assertThatThrownBy(() -> employeeService.getEmployeeById(saved.getId()))
                .isInstanceOf(EmployeeNotFoundException.class);
        assertThat(QueryCounter.count()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Creating an employee evicts the department listing, which embeds its employees")
    void createEvictsDepartmentListing() {
        Cache departments = cacheManager.getCache(CacheConfig.DEPARTMENTS);
        departmentService.getAllDepartmentsWithEmployees();
        assertThat(departments.get(SimpleKey.EMPTY)).isNotNull();

        employeeService.createEmployee(new EmployeeRequest(
                "Erin", "Xu", "erin@example.com", "QA Engineer", new BigDecimal("4000.00"), null), null);

        assertThat(departments.get(SimpleKey.EMPTY)).isNull();
    }

    private Employee employee(String email) {
        return Employee.builder()
                .firstName("First")
                .lastName("Last")
                .email(email)
                .jobTitle("Backend Engineer")
                .salary(new BigDecimal("5000.00"))
                .build();
    }

    private Employee employeeWithDepartment(String email, Department department) {
        Employee employee = employee(email);
        employee.setDepartment(department);
        return employee;
    }
}
