package com.wangqi.employee.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the CORS policy wired into the security filter chain.
 *
 * <p>The browser never hits this path in development or production because
 * Vite and Nginx proxy {@code /api}, but a direct browser-to-API deployment
 * would: a preflight must be answered before authentication.
 *
 * @author wangqi
 */
@SpringBootTest(properties = "app.demo-users.enabled=false")
@AutoConfigureMockMvc
class CorsIntegrationTest {

    private static final String SPA_ORIGIN = "http://localhost:5173";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("a preflight from the configured SPA origin is allowed without a token")
    void shouldAllowPreflightFromConfiguredOrigin() throws Exception {
        mockMvc.perform(options("/api/employees")
                        .header(HttpHeaders.ORIGIN, SPA_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, SPA_ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("POST")));
    }

    @Test
    @DisplayName("an origin outside the allow-list is rejected")
    void shouldRejectUnknownOrigin() throws Exception {
        mockMvc.perform(options("/api/employees")
                        .header(HttpHeaders.ORIGIN, "https://evil.example.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
