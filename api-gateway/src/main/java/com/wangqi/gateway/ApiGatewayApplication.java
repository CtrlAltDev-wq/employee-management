package com.wangqi.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import reactor.core.publisher.Hooks;

/**
 * Single public entry point of the employee management system.
 *
 * <p>The gateway only routes. Authentication stays with the services that own
 * the data, so a compromised gateway cannot widen a caller's permissions. It
 * does create the trace for every incoming request and forwards it downstream,
 * which is why the gateway, both services and the consumer share one trace id.
 *
 * @author wangqi
 */
@SpringBootApplication
public class ApiGatewayApplication {

    static {
        // Propagate the tracing scope into Reactor threads so MDC traceId is loggable.
        Hooks.enableAutomaticContextPropagation();
    }

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
