package com.wangqi.onboarding.controller;

import com.wangqi.onboarding.entity.OnboardingTask;
import com.wangqi.onboarding.repository.OnboardingTaskRepository;
import com.wangqi.onboarding.security.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end test of the onboarding read endpoint: the service must reject an
 * anonymous caller with a JSON 401 and accept a token signed by the employee
 * API with the shared secret.
 *
 * @author wangqi
 */
@SpringBootTest
@AutoConfigureMockMvc
class OnboardingApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OnboardingTaskRepository onboardingTaskRepository;

    @Autowired
    private JwtProperties jwtProperties;

    @BeforeEach
    void seed() {
        onboardingTaskRepository.deleteAll();
        onboardingTaskRepository.save(OnboardingTask.builder()
                .employeeId(501L)
                .fullName("Alice Chen")
                .email("alice@example.com")
                .build());
    }

    @Test
    @DisplayName("a request without a token is rejected with a JSON 401")
    void shouldReturn401WithoutToken() throws Exception {
        mockMvc.perform(get("/api/onboarding/tasks"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.path").value("/api/onboarding/tasks"));
    }

    @Test
    @DisplayName("a token from the employee API grants access to the onboarding tasks")
    void shouldReturnTasksWithToken() throws Exception {
        mockMvc.perform(get("/api/onboarding/tasks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].employeeId").value(501))
                .andExpect(jsonPath("$[0].fullName").value("Alice Chen"))
                .andExpect(jsonPath("$[0].email").value("alice@example.com"));
    }

    private String token() {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject("viewer")
                .claim("role", "ROLE_USER")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}
