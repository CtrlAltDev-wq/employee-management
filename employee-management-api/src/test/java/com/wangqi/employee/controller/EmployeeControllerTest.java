package com.wangqi.employee.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangqi.employee.exception.EmployeeNotFoundException;
import com.wangqi.employee.integration.ExternalHrClient;
import com.wangqi.employee.service.EmployeeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web layer tests for {@link EmployeeController} using {@code @WebMvcTest},
 * covering parameter validation and global exception handling.
 *
 * @author wangqi
 */
@WebMvcTest(EmployeeController.class)
@AutoConfigureMockMvc(addFilters = false)
class EmployeeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EmployeeService employeeService;

    @MockBean
    private ExternalHrClient externalHrClient;

    @Test
    @DisplayName("POST /api/employees returns 400 when required fields are missing or invalid")
    void shouldReturn400WhenRequestIsInvalid() throws Exception {
        String invalidBody = objectMapper.writeValueAsString(Map.of(
                "firstName", "",
                "lastName", "",
                "email", "not-an-email"
        ));

        mockMvc.perform(post("/api/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.path").value("/api/employees"))
                .andExpect(jsonPath("$.fieldErrors.firstName").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists());
    }

    @Test
    @DisplayName("GET /api/employees/{id} returns 404 when the employee does not exist")
    void shouldReturn404WhenEmployeeNotFound() throws Exception {
        when(employeeService.getEmployeeById(99L)).thenThrow(new EmployeeNotFoundException(99L));

        mockMvc.perform(get("/api/employees/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("Employee not found with id: 99"));
    }

    @Test
    @DisplayName("PUT /api/employees/{id} returns 404 when the employee does not exist")
    void shouldReturn404WhenUpdatingUnknownEmployee() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "firstName", "Qi",
                "lastName", "Wang",
                "email", "qi.wang@example.com",
                "salary", 5000));

        when(employeeService.updateEmployee(org.mockito.ArgumentMatchers.eq(99L), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new EmployeeNotFoundException(99L));

        mockMvc.perform(put("/api/employees/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.path").value("/api/employees/99"));
    }

    @Test
    @DisplayName("PUT /api/employees/{id} validates the body before reaching the service")
    void shouldReturn400WhenUpdateBodyIsInvalid() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "firstName", "",
                "lastName", "",
                "email", "not-an-email"));

        mockMvc.perform(put("/api/employees/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.fieldErrors.email").exists());
    }
}
