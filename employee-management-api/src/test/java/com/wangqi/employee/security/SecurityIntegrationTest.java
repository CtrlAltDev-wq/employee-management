package com.wangqi.employee.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end security tests: login, 401 vs 403, and role-based write access.
 *
 * @author wangqi
 */
@SpringBootTest(properties = "app.demo-users.enabled=false")
@AutoConfigureMockMvc
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void seedUsers() {
        employeeRepository.deleteAll();
        userAccountRepository.deleteAll();

        userAccountRepository.save(UserAccount.builder()
                .username("admin")
                .password(passwordEncoder.encode("admin123"))
                .role(Role.ROLE_ADMIN)
                .build());
        userAccountRepository.save(UserAccount.builder()
                .username("viewer")
                .password(passwordEncoder.encode("viewer123"))
                .role(Role.ROLE_USER)
                .build());
    }

    @Test
    @DisplayName("a request without a token is rejected with a JSON 401")
    void shouldReturn401WithoutToken() throws Exception {
        mockMvc.perform(get("/api/departments"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("Authentication required: send a valid Bearer token."))
                .andExpect(jsonPath("$.path").value("/api/departments"));
    }

    @Test
    @DisplayName("a malformed token is rejected with 401, not 500")
    void shouldReturn401ForMalformedToken() throws Exception {
        mockMvc.perform(get("/api/departments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("a valid token grants access to protected endpoints")
    void shouldAllowAccessWithValidToken() throws Exception {
        String token = login("viewer", "viewer123");

        mockMvc.perform(get("/api/departments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("wrong credentials return 401 with a generic message")
    void shouldReturn401ForWrongPassword() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new Credentials("admin", "wrong-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password."));
    }

    @Test
    @DisplayName("a regular user is forbidden from creating employees (401 vs 403)")
    void shouldReturn403WhenRoleIsInsufficient() throws Exception {
        String token = login("viewer", "viewer123");

        mockMvc.perform(post("/api/employees")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validEmployee())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("an admin can create employees")
    void shouldAllowAdminToCreateEmployee() throws Exception {
        String token = login("admin", "admin123");
        EmployeePayload payload = validEmployee();

        mockMvc.perform(post("/api/employees")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value(payload.email()));
    }

    @Test
    @DisplayName("an admin can update an employee through PUT")
    void shouldAllowAdminToUpdateEmployee() throws Exception {
        String token = login("admin", "admin123");
        EmployeePayload payload = validEmployee();

        String createdBody = mockMvc.perform(post("/api/employees")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long id = objectMapper.readTree(createdBody).at("/data/id").asLong();

        EmployeePayload updated = new EmployeePayload(
                "Updated", "Person", payload.email(), "Staff Engineer", 9000);

        mockMvc.perform(put("/api/employees/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updated)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.firstName").value("Updated"))
                .andExpect(jsonPath("$.data.jobTitle").value("Staff Engineer"));
    }

    @Test
    @DisplayName("a regular user is forbidden from updating employees")
    void shouldReturn403WhenViewerUpdatesEmployee() throws Exception {
        String token = login("viewer", "viewer123");

        mockMvc.perform(put("/api/employees/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validEmployee())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    private String login(String username, String password) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Credentials(username, password))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode body = objectMapper.readTree(response);
        return body.get("data").get("accessToken").asText();
    }

    private EmployeePayload validEmployee() {
        return new EmployeePayload("Qi", "Wang", "admin-" + UUID.randomUUID() + "@example.com", "Backend Engineer", 5000);
    }

    private record Credentials(String username, String password) {
    }

    private record EmployeePayload(String firstName, String lastName, String email, String jobTitle, int salary) {
    }
}
