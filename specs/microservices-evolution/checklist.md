# Checklist: Microservices Evolution

Legend: `[x]` verified, `[ ]` not verified yet.

## Structure and build

- [x] Root `pom.xml` is `packaging=pom` and aggregates `contracts`,
  `employee-management-api`, `onboarding-service`, `api-gateway`.
- [x] `mvn -B -DskipTests package` at the root builds all four modules.
- [x] Every module has an executable Spring Boot jar (gateway included).
- [x] `.gitignore` and `.dockerignore` exist at the root and exclude build output.

## Shared contract

- [x] `EmployeeCreatedMessage` lives only in `com.wangqi.contracts.messaging`.
- [x] Producer and consumer both import `com.wangqi.contracts.messaging.EmployeeCreatedMessage`.
- [x] `EmployeeCreatedMessageTest` asserts the JSON round trip and the exact FQCN.

## employee-management-api

- [x] The consumer, `OnboardingInitializer`, `OnboardingTask` and its repository
  are gone from this module.
- [x] `RabbitMqConfig` declares only the durable topic exchange, the JSON
  converter and the template; no queue, DLQ or binding.
- [x] `MessagingProperties` exposes only `enabled`, `exchange` and `routingKey`.
- [x] Public employee/department/auth behaviour is unchanged (existing tests pass).
- [x] Tracing dependencies and `management.tracing` / `zipkin` config are present.
- [x] `spring.rabbitmq.template.observation-enabled: true` is set.
- [x] Log pattern contains `trace=%X{traceId:-none}`.

## onboarding-service

- [x] Runs on port 8081 with its own in-memory H2 (`onboardingdb`).
- [x] Owns the queue (`employee.created.onboarding`), its DLQ and the binding.
- [x] Consumer and initializer are idempotent (`employee_id` unique constraint).
- [x] `GET /api/onboarding/tasks` returns tasks newest first.
- [x] JWT is verified with the shared secret, with no database lookup.
- [x] Actuator is open, `/api/**` is authenticated and returns JSON `401`.
- [x] `RabbitMqTopologyTest`, `OnboardingInitializerTest`, `JwtServiceTest` and
  `OnboardingApiTest` pass without a broker.

## api-gateway

- [x] Runs on port 8000 with two routes, URIs overridable by environment.
- [x] `/api/auth`, `/api/employees`, `/api/departments`, Swagger paths route to 8080.
- [x] `/api/onboarding/**` routes to 8081.
- [x] No authentication filter at the gateway (documented decision).
- [x] `GatewayRoutingTest` asserts route ids, URIs and path predicates.
- [x] Reactor context propagation is enabled so the gateway log carries the trace id.

## Tracing

- [x] All three services sample at 100% and export to `${ZIPKIN_ENDPOINT}`.
- [x] AMQP observation is enabled on the producer template and the consumer listener.
- [x] One request through the gateway produced the same `traceId` in the gateway
  log and in the employee-api log (verified locally).
- [x] A request routed to onboarding also shares one `traceId` across both logs.

## Containers

- [x] Root `docker-compose.yml` defines redis, rabbitmq, zipkin, employee-api,
  onboarding-service and api-gateway.
- [x] All three images build from the repository root context.
- [x] `JWT_SECRET`, `ZIPKIN_ENDPOINT`, `EMPLOYEE_API_URI` and
  `ONBOARDING_SERVICE_URI` are wired between services.
- [x] Health checks gate start-up order (services wait for their dependencies).
- [ ] `docker compose up --build` end-to-end run (blocked: Docker Desktop is not
  installed on this machine yet).

## Tests and local run

- [x] Root `mvn -B test` passes: 68 tests (contracts 2, employee-api 52,
  onboarding-service 12, api-gateway 2), zero failures.
- [x] Tests need no broker, Redis or Zipkin.
- [x] Locally, through the gateway: `401` without a token, `200` after login for
  `/api/departments`, `200` for `/api/onboarding/tasks` with the same token.
- [x] With `app.messaging.enabled=false`, both services report
  `/actuator/health` = `200 UP` instead of a Rabbit-induced `503`.

## README

- [x] README sits at the repository root and describes the microservice system.
- [x] Tech stack lists Spring Cloud Gateway, Micrometer Tracing and Zipkin.
- [x] An architecture section explains the incremental extraction and the
  producer/consumer ownership rule.
- [x] Messaging, security and observability sections describe the split.
- [x] API endpoints include `GET /api/onboarding/tasks` and the gateway base URL.
- [x] Getting Started covers the reactor build, per-module start-up and `:8000`.
- [x] Project structure reflects the four modules.
- [x] Limitations (shared secret, per-service H2, no outbox, no gateway auth)
  are stated explicitly.
