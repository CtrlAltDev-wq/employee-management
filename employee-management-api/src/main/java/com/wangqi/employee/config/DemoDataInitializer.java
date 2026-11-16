package com.wangqi.employee.config;

import com.wangqi.employee.entity.Department;
import com.wangqi.employee.entity.Employee;
import com.wangqi.employee.repository.DepartmentRepository;
import com.wangqi.employee.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Seeds a small, realistic organisation so that a fresh checkout shows a
 * populated directory instead of an empty table. Disable with
 * {@code app.demo-data.enabled=false} in any real deployment.
 *
 * <p>The check is "is the database empty", not "has this run before": a
 * deployment that already holds data is never overwritten, and deleting the
 * demo rows is enough to stop them from coming back on the next start.
 *
 * @author wangqi
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.demo-data", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DemoDataInitializer implements ApplicationRunner {

    private static final List<String> DEPARTMENT_NAMES = List.of(
            "Engineering", "Product", "Design", "Sales", "Marketing", "People Operations");

    private static final List<Seed> EMPLOYEES = List.of(
            new Seed("Alice", "Chen", "Engineering Manager", 14200, "Engineering"),
            new Seed("Bruno", "Silva", "Senior Backend Engineer", 11800, "Engineering"),
            new Seed("Chloe", "Martin", "Frontend Engineer", 9600, "Engineering"),
            new Seed("Daniel", "Okafor", "Platform Engineer", 10400, "Engineering"),
            new Seed("Elena", "Petrova", "QA Engineer", 8200, "Engineering"),
            new Seed("Farid", "Haddad", "Backend Engineer", 9100, "Engineering"),
            new Seed("Grace", "Kim", "Data Engineer", 10900, "Engineering"),
            new Seed("Hugo", "Almeida", "Site Reliability Engineer", 11500, "Engineering"),
            new Seed("Iris", "Novak", "Frontend Engineer", 9400, "Engineering"),
            new Seed("Jonas", "Weber", "Mobile Engineer", 9800, "Engineering"),
            new Seed("Kenji", "Sato", "Staff Engineer", 15100, "Engineering"),
            new Seed("Lena", "Fischer", "Security Engineer", 12200, "Engineering"),
            new Seed("Marta", "Rossi", "Product Manager", 12600, "Product"),
            new Seed("Nikhil", "Rao", "Product Owner", 11200, "Product"),
            new Seed("Olivia", "Brown", "Product Analyst", 8900, "Product"),
            new Seed("Pavel", "Sokolov", "Technical Program Manager", 10800, "Product"),
            new Seed("Quinn", "Murphy", "UX Researcher", 8600, "Product"),
            new Seed("Rafael", "Costa", "Design Lead", 12400, "Design"),
            new Seed("Sofia", "Lindgren", "Product Designer", 9300, "Design"),
            new Seed("Tomas", "Novak", "UX Designer", 8800, "Design"),
            new Seed("Uma", "Patel", "Brand Designer", 8100, "Design"),
            new Seed("Victor", "Moreau", "Sales Director", 13800, "Sales"),
            new Seed("Wendy", "Zhao", "Account Executive", 9200, "Sales"),
            new Seed("Xander", "Bakker", "Sales Engineer", 10100, "Sales"),
            new Seed("Yara", "Haddad", "Account Manager", 8700, "Sales"),
            new Seed("Zane", "Miller", "Business Development Rep", 7300, "Sales"),
            new Seed("Aisha", "Rahman", "Sales Operations Analyst", 8000, "Sales"),
            new Seed("Ben", "Turner", "Marketing Manager", 10500, "Marketing"),
            new Seed("Carla", "Mendes", "Content Strategist", 7600, "Marketing"),
            new Seed("Dmitri", "Volkov", "Growth Marketer", 8400, "Marketing"),
            new Seed("Eva", "Kowalski", "SEO Specialist", 7200, "Marketing"),
            new Seed("Felix", "Andersen", "Marketing Analyst", 7900, "Marketing"),
            new Seed("Gina", "Duarte", "HR Business Partner", 9400, "People Operations"),
            new Seed("Henry", "Clarke", "Talent Acquisition Partner", 8100, "People Operations"),
            new Seed("Isabel", "Santos", "People Operations Specialist", 6900, "People Operations"),
            new Seed("Jakub", "Nowak", "Recruiter", 7500, "People Operations"),
            new Seed("Keira", "Walsh", "Office Manager", 6100, "People Operations"),
            new Seed("Leo", "Ferrari", "Payroll Specialist", 7100, "People Operations"),
            new Seed("Maya", "Ibrahim", "HR Analyst", 7800, "People Operations"),
            new Seed("Noah", "Schmidt", "Learning Specialist", 7400, "People Operations"),
            new Seed("Olga", "Ivanova", "Compensation Analyst", 8600, "People Operations"),
            new Seed("Peter", "Hansen", "Workplace Coordinator", 5800, "People Operations"));

    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (employeeRepository.count() > 0 || departmentRepository.count() > 0) {
            return;
        }

        Map<String, Department> departments = DEPARTMENT_NAMES.stream()
                .map(name -> departmentRepository.save(Department.builder().name(name).build()))
                .collect(Collectors.toMap(Department::getName, Function.identity()));

        EMPLOYEES.forEach(seed -> employeeRepository.save(Employee.builder()
                .firstName(seed.firstName())
                .lastName(seed.lastName())
                .email(emailOf(seed))
                .jobTitle(seed.jobTitle())
                .salary(BigDecimal.valueOf(seed.salary()))
                .department(departments.get(seed.department()))
                .build()));
    }

    private static String emailOf(Seed seed) {
        return (seed.firstName() + "." + seed.lastName()).toLowerCase(Locale.ROOT) + "@example.com";
    }

    private record Seed(String firstName, String lastName, String jobTitle, long salary, String department) {
    }
}
