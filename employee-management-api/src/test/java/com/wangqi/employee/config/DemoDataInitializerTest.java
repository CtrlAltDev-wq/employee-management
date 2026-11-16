package com.wangqi.employee.config;

import com.wangqi.employee.repository.DepartmentRepository;
import com.wangqi.employee.repository.EmployeeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The demo organisation is what a fresh checkout shows, so it is worth one test:
 * it has to appear on an empty database and must never duplicate itself.
 *
 * <p>The datasource is renamed so this context does not share the in-memory
 * database other suites write to.
 *
 * @author wangqi
 */
@SpringBootTest(properties = {
        "app.demo-data.enabled=true",
        "spring.datasource.url=jdbc:h2:mem:demodata;DB_CLOSE_DELAY=-1;MODE=MySQL"
})
class DemoDataInitializerTest {

    @Autowired
    private DemoDataInitializer demoDataInitializer;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Test
    @DisplayName("the demo organisation is seeded once and never duplicated")
    void shouldSeedOnlyWhenEmpty() throws Exception {
        assertThat(departmentRepository.count()).isEqualTo(6);
        assertThat(employeeRepository.count()).isEqualTo(42);

        demoDataInitializer.run(new DefaultApplicationArguments());

        assertThat(departmentRepository.count()).isEqualTo(6);
        assertThat(employeeRepository.count()).isEqualTo(42);
    }
}
