package com.wangqi.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Asserts that the gateway really exposes the two routes the README documents.
 * Reading the route definitions proves the YAML was bound and turned into
 * routing state, which a properties assertion alone would not.
 *
 * @author wangqi
 */
@SpringBootTest
class GatewayRoutingTest {

    @Autowired
    private RouteDefinitionLocator routeDefinitionLocator;

    @Test
    @DisplayName("the employee API route forwards auth, employees and departments")
    void shouldRouteEmployeeApi() {
        RouteDefinition route = route("employee-api");

        assertThat(route.getUri().toString()).isEqualTo("http://localhost:8080");
        assertThat(pathPatterns(route))
                .contains("/api/auth/**", "/api/employees/**", "/api/departments/**");
    }

    @Test
    @DisplayName("the onboarding route forwards the onboarding namespace")
    void shouldRouteOnboardingService() {
        RouteDefinition route = route("onboarding-service");

        assertThat(route.getUri().toString()).isEqualTo("http://localhost:8081");
        assertThat(pathPatterns(route)).containsExactly("/api/onboarding/**");
    }

    private RouteDefinition route(String id) {
        List<RouteDefinition> routes = routeDefinitionLocator.getRouteDefinitions()
                .collectList()
                .block();

        assertThat(routes).isNotNull();
        return routes.stream()
                .filter(route -> id.equals(route.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No route with id " + id));
    }

    private List<String> pathPatterns(RouteDefinition route) {
        // Shortcut predicates are expanded into _genkey_0, _genkey_1, ...
        return route.getPredicates().get(0).getArgs().entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("_genkey_"))
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .toList();
    }
}
