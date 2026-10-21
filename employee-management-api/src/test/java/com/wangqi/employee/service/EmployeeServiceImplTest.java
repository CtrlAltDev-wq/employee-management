package com.wangqi.employee.service;

import com.wangqi.employee.dto.EmployeeResponse;
import com.wangqi.employee.dto.EmployeeSearchCriteria;
import com.wangqi.employee.dto.PageResponse;
import com.wangqi.employee.entity.Employee;
import com.wangqi.employee.exception.EmployeeNotFoundException;
import com.wangqi.employee.repository.DepartmentRepository;
import com.wangqi.employee.repository.EmployeeRepository;
import com.wangqi.employee.service.impl.EmployeeServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link EmployeeServiceImpl} using Mockito.
 *
 * @author wangqi
 */
@ExtendWith(MockitoExtension.class)
class EmployeeServiceImplTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private IdempotencyService idempotencyService;

    @InjectMocks
    private EmployeeServiceImpl employeeService;

    @Test
    @DisplayName("getEmployeeById returns the employee when it exists")
    void shouldReturnEmployeeWhenFound() {
        Employee employee = Employee.builder()
                .id(1L)
                .firstName("Qi")
                .lastName("Wang")
                .email("wangqi@example.com")
                .jobTitle("Backend Engineer")
                .salary(new BigDecimal("5000.00"))
                .build();

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));

        EmployeeResponse response = employeeService.getEmployeeById(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.firstName()).isEqualTo("Qi");
        assertThat(response.lastName()).isEqualTo("Wang");
        assertThat(response.email()).isEqualTo("wangqi@example.com");
        assertThat(response.salary()).isEqualByComparingTo("5000.00");
        verify(employeeRepository).findById(1L);
    }

    @Test
    @DisplayName("searchEmployees returns the requested page mapped to response records")
    void shouldReturnPagedEmployeesWhenSearching() {
        Employee employee = Employee.builder()
                .id(7L)
                .firstName("Qi")
                .lastName("Wang")
                .email("qi.wang@example.com")
                .jobTitle("Backend Engineer")
                .salary(new BigDecimal("6500.00"))
                .build();

        Page<Employee> page = new PageImpl<>(List.of(employee), PageRequest.of(0, 1), 3);
        when(employeeRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        PageResponse<EmployeeResponse> response = employeeService.searchEmployees(
                new EmployeeSearchCriteria("Qi", null, null, null, null, null, null),
                PageRequest.of(0, 1, Sort.by("salary").descending()));

        assertThat(response.content()).singleElement()
                .satisfies(item -> assertThat(item.email()).isEqualTo("qi.wang@example.com"));
        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(1);
        assertThat(response.totalElements()).isEqualTo(3);
        assertThat(response.totalPages()).isEqualTo(3);
        verify(employeeRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("getEmployeeById throws EmployeeNotFoundException when the id is unknown")
    void shouldThrowWhenEmployeeNotFound() {
        when(employeeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeService.getEmployeeById(99L))
                .isInstanceOf(EmployeeNotFoundException.class)
                .hasMessageContaining("99");

        verify(employeeRepository).findById(99L);
    }
}
