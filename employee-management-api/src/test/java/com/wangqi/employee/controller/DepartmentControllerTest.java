package com.wangqi.employee.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangqi.employee.dto.DepartmentResponse;
import com.wangqi.employee.dto.EmployeeSummary;
import com.wangqi.employee.service.DepartmentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web layer tests for {@link DepartmentController}.
 *
 * @author wangqi
 */
@WebMvcTest(DepartmentController.class)
class DepartmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DepartmentService departmentService;

    @Test
    @DisplayName("GET /api/departments returns departments with their employees")
    void shouldReturnDepartmentsWithEmployees() throws Exception {
        when(departmentService.getAllDepartmentsWithEmployees()).thenReturn(List.of(
                new DepartmentResponse(1L, "Engineering", List.of(
                        new EmployeeSummary(100L, "Qi", "Wang", "wangqi@example.com", "Backend Engineer")
                ))
        ));

        mockMvc.perform(get("/api/departments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Engineering"))
                .andExpect(jsonPath("$[0].employees[0].email").value("wangqi@example.com"));
    }

    @Test
    @DisplayName("POST /api/departments returns 201 Created with a Location header")
    void shouldCreateDepartment() throws Exception {
        when(departmentService.createDepartment(any()))
                .thenReturn(new DepartmentResponse(10L, "Engineering", List.of()));

        mockMvc.perform(post("/api/departments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DepartmentRequestPayload("Engineering"))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/departments/10"))
                .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    @DisplayName("POST /api/departments returns 400 when the name is blank")
    void shouldReturn400WhenNameIsBlank() throws Exception {
        mockMvc.perform(post("/api/departments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DepartmentRequestPayload(" "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    private record DepartmentRequestPayload(String name) {
    }
}
