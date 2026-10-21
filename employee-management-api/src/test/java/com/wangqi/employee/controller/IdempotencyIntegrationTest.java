package com.wangqi.employee.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangqi.employee.entity.Role;
import com.wangqi.employee.entity.UserAccount;
import com.wangqi.employee.repository.EmployeeRepository;
import com.wangqi.employee.repository.IdempotencyRecordRepository;
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
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that a retried request carrying the same {@code Idempotency-Key}
 * returns the original result instead of creating a duplicate employee.
 *
 * @author wangqi
 */
@SpringBootTest(properties = "app.demo-users.enabled=false")
@AutoConfigureMockMvc
class IdempotencyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        employeeRepository.deleteAll();
        idempotencyRecordRepository.deleteAll();
        userAccountRepository.deleteAll();

        userAccountRepository.save(UserAccount.builder()
                .username("admin")
                .password(passwordEncoder.encode("admin123"))
                .role(Role.ROLE_ADMIN)
                .build());

        adminToken = login();
    }

    @Test
    @DisplayName("the same key and payload return the original employee without duplicating it")
    void shouldReturnOriginalResultForRetry() throws Exception {
        String key = UUID.randomUUID().toString();
        String payload = employeeJson("idempotent@example.com");

        MvcResult first = create(payload, key)
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult second = create(payload, key)
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replay", "true"))
                .andReturn();

        assertThat(employeeId(second)).isEqualTo(employeeId(first));
        assertThat(employeeRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("the same key with a different payload is rejected with 409")
    void shouldRejectKeyReuseWithDifferentPayload() throws Exception {
        String key = UUID.randomUUID().toString();

        create(employeeJson("first@example.com"), key).andExpect(status().isCreated());

        create(employeeJson("second@example.com"), key)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("different request payload")));

        assertThat(employeeRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("requests without a key keep creating new employees")
    void shouldCreateNormallyWithoutKey() throws Exception {
        create(employeeJson("one@example.com"), null).andExpect(status().isCreated());
        create(employeeJson("two@example.com"), null).andExpect(status().isCreated());

        assertThat(employeeRepository.count()).isEqualTo(2);
    }

    private org.springframework.test.web.servlet.ResultActions create(String payload, String key)
            throws Exception {
        var request = post("/api/employees")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload);

        if (key != null) {
            request.header("Idempotency-Key", key);
        }

        return mockMvc.perform(request);
    }

    private String employeeJson(String email) throws Exception {
        return objectMapper.writeValueAsString(new EmployeePayload(
                "Qi", "Wang", email, "Backend Engineer", 5000));
    }

    private long employeeId(MvcResult result) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("id").asLong();
    }

    private String login() throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new Credentials("admin", "admin123"))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response).get("accessToken").asText();
    }

    private record Credentials(String username, String password) {
    }

    private record EmployeePayload(String firstName, String lastName, String email,
                                   String jobTitle, int salary) {
    }
}
