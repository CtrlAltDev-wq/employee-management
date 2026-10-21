package com.wangqi.employee.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangqi.employee.entity.Employee;
import com.wangqi.employee.entity.Role;
import com.wangqi.employee.entity.UserAccount;
import com.wangqi.employee.repository.EmployeeRepository;
import com.wangqi.employee.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests for paginated, filtered search: real JPA Specifications,
 * real pagination, and the 400 responses for unsupported query parameters.
 *
 * @author wangqi
 */
@SpringBootTest(properties = "app.demo-users.enabled=false")
@AutoConfigureMockMvc
class EmployeeSearchIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        employeeRepository.deleteAll();
        userAccountRepository.deleteAll();

        userAccountRepository.save(UserAccount.builder()
                .username("admin")
                .password(passwordEncoder.encode("admin123"))
                .role(Role.ROLE_ADMIN)
                .build());

        List.of(
                employee("Alice", "Chen", "alice@example.com", "Senior Backend Engineer", "9000.00"),
                employee("Bob", "Li", "bob@example.com", "Backend Engineer", "6000.00"),
                employee("Carol", "Wang", "carol@example.com", "QA Engineer", "4000.00"),
                employee("Dave", "Zhao", "dave@example.com", "Product Manager", "7000.00"),
                employee("Erin", "Xu", "erin@example.com", "Junior Engineer", "3000.00")
        ).forEach(employeeRepository::save);

        token = login("admin", "admin123");
    }

    @Test
    @DisplayName("GET /api/employees pages the result set and honours sort=field,desc")
    void shouldReturnFirstPageSortedBySalaryDescending() throws Exception {
        mockMvc.perform(get("/api/employees")
                        .param("page", "0")
                        .param("size", "2")
                        .param("sort", "salary,desc")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].email").value("alice@example.com"))
                .andExpect(jsonPath("$.content[1].email").value("dave@example.com"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false));
    }

    @Test
    @DisplayName("GET /api/employees combines a case-insensitive text filter with a salary bound")
    void shouldFilterByJobTitleAndMinimumSalary() throws Exception {
        mockMvc.perform(get("/api/employees")
                        .param("jobTitle", "ENGINEER")
                        .param("minSalary", "5000")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].email",
                        containsInAnyOrder("alice@example.com", "bob@example.com")));
    }

    @Test
    @DisplayName("GET /api/employees rejects an unsupported sort field with 400")
    void shouldRejectUnsupportedSortField() throws Exception {
        mockMvc.perform(get("/api/employees")
                        .param("sort", "password,asc")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Unsupported sort field 'password'")));
    }

    @Test
    @DisplayName("GET /api/employees rejects a page size above the cap with 400")
    void shouldRejectPageSizeAboveCap() throws Exception {
        mockMvc.perform(get("/api/employees")
                        .param("size", "500")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.size").exists());
    }

    private Employee employee(String firstName, String lastName, String email,
                              String jobTitle, String salary) {
        return Employee.builder()
                .firstName(firstName)
                .lastName(lastName)
                .email(email)
                .jobTitle(jobTitle)
                .salary(new BigDecimal(salary))
                .build();
    }

    private String login(String username, String password) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new Credentials(username, password))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode body = objectMapper.readTree(response);
        return body.get("accessToken").asText();
    }

    private record Credentials(String username, String password) {
    }
}
