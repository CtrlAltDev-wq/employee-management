package com.wangqi.employee.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangqi.employee.config.CorsProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Stateless security configuration.
 *
 * <p>Rules of thumb:
 * <ul>
 *   <li>public: login, and the H2 console for local development;</li>
 *   <li>ADMIN: every write operation;</li>
 *   <li>any authenticated user: reads.</li>
 * </ul>
 *
 * @author wangqi
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties({JwtProperties.class, CorsProperties.class})
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtService jwtService,
                                                   UserDetailsService userDetailsService,
                                                   ObjectMapper objectMapper,
                                                   CorsConfigurationSource corsConfigurationSource) throws Exception {
        RestAuthenticationEntryPoint entryPoint = new RestAuthenticationEntryPoint(objectMapper);
        RestAccessDeniedHandler accessDeniedHandler = new RestAccessDeniedHandler(objectMapper);
        JwtAuthenticationFilter jwtAuthenticationFilter =
                new JwtAuthenticationFilter(jwtService, userDetailsService);

        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/auth/**", "/h2-console/**",
                                "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
                                "/actuator/health/**", "/actuator/info",
                                "/actuator/metrics/**", "/actuator/prometheus").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/employees", "/api/employees/**", "/api/departments").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/employees/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/employees/**").hasRole("ADMIN")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * One CORS policy for the whole API, driven by configuration so the same
     * image can be deployed anywhere ({@code CORS_ALLOWED_ORIGINS=https://app.example.com}).
     * Preflight requests short-circuit before the JWT filter, which is why
     * browsers never see a 401 on {@code OPTIONS}.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins() == null
                ? List.of()
                : properties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "Idempotency-Key", "X-Request-Id"));
        configuration.setExposedHeaders(List.of("X-Request-Id", "Idempotent-Replay", "Location"));
        // Authentication travels in the Authorization header, never in a cookie,
        // so credentials stay disabled and the origin list is the only gate.
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(properties.maxAgeSeconds());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}
