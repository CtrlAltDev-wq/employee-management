package com.wangqi.employee.service;

import com.wangqi.employee.dto.EmployeeResponse;
import com.wangqi.employee.entity.Employee;
import com.wangqi.employee.exception.EmployeeNotFoundException;
import com.wangqi.employee.repository.EmployeeRepository;
import com.wangqi.employee.service.impl.EmployeeServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    @DisplayName("getEmployeeById throws EmployeeNotFoundException when the id is unknown")
    void shouldThrowWhenEmployeeNotFound() {
        when(employeeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeService.getEmployeeById(99L))
                .isInstanceOf(EmployeeNotFoundException.class)
                .hasMessageContaining("99");

        verify(employeeRepository).findById(99L);
    }
}
