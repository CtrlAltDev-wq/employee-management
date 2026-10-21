package com.wangqi.employee.service;

import com.wangqi.employee.dto.DepartmentRequest;
import com.wangqi.employee.dto.DepartmentResponse;
import com.wangqi.employee.entity.Department;
import com.wangqi.employee.entity.Employee;
import com.wangqi.employee.exception.DuplicateDepartmentException;
import com.wangqi.employee.repository.DepartmentRepository;
import com.wangqi.employee.service.impl.DepartmentServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DepartmentServiceImpl} using Mockito.
 *
 * @author wangqi
 */
@ExtendWith(MockitoExtension.class)
class DepartmentServiceImplTest {

    @Mock
    private DepartmentRepository departmentRepository;

    @InjectMocks
    private DepartmentServiceImpl departmentService;

    @Test
    @DisplayName("createDepartment persists the entity and returns the generated id")
    void shouldCreateDepartment() {
        when(departmentRepository.existsByName("Engineering")).thenReturn(false);
        when(departmentRepository.save(any(Department.class))).thenAnswer(invocation -> {
            Department department = invocation.getArgument(0);
            department.setId(10L);
            return department;
        });

        DepartmentResponse response = departmentService.createDepartment(new DepartmentRequest("Engineering"));

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.name()).isEqualTo("Engineering");
        assertThat(response.employees()).isEmpty();
        verify(departmentRepository).save(any(Department.class));
    }

    @Test
    @DisplayName("createDepartment rejects a duplicate department name")
    void shouldRejectDuplicateDepartmentName() {
        when(departmentRepository.existsByName("Engineering")).thenReturn(true);

        assertThatThrownBy(() -> departmentService.createDepartment(new DepartmentRequest("Engineering")))
                .isInstanceOf(DuplicateDepartmentException.class)
                .hasMessageContaining("Engineering");

        verify(departmentRepository, never()).save(any(Department.class));
    }

    @Test
    @DisplayName("getAllDepartmentsWithEmployees maps the fetched aggregate into DTOs")
    void shouldMapDepartmentsWithEmployees() {
        Department department = Department.builder()
                .id(1L)
                .name("Engineering")
                .employees(List.of(
                        Employee.builder()
                                .id(100L)
                                .firstName("Qi")
                                .lastName("Wang")
                                .email("wangqi@example.com")
                                .jobTitle("Backend Engineer")
                                .salary(new BigDecimal("5000.00"))
                                .build()
                ))
                .build();

        when(departmentRepository.findAllWithEmployees()).thenReturn(List.of(department));

        List<DepartmentResponse> responses = departmentService.getAllDepartmentsWithEmployees();

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).name()).isEqualTo("Engineering");
        assertThat(responses.get(0).employees()).hasSize(1);
        assertThat(responses.get(0).employees().get(0).email()).isEqualTo("wangqi@example.com");
        verify(departmentRepository).findAllWithEmployees();
    }
}
