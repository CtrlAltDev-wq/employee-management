# Tasks: Microservices Evolution

Legend: `[ ]` pending, `[~]` in progress, `[x]` done.
All paths relative to `d:\workspace_upwork\employee-management`.

## T1 — Root aggregator and build hygiene

- [x] Create root `pom.xml`: `groupId com.wangqi`, `artifactId employee-management`,
  `version 1.0.0`, `packaging pom`, modules in this order:
  `contracts`, `employee-management-api`, `onboarding-service`, `api-gateway`.
- [x] Replace root `.gitignore` with `target/`, `**/target/`, `.metadata/`,
  `.idea/`, `*.iml`, `.vscode/`, `*.log`, `*.mv.db`.
- [x] Create root `.dockerignore`: `**/target/`, `.git/`, `.metadata/`, `*.log`.
- [x] Verify `mvn -q -DskipTests package` resolves the reactor once all modules exist.

## T2 — `contracts` module

- [x] `contracts/pom.xml`: parent `spring-boot-starter-parent 3.3.5`,
  `artifactId contracts 1.0.0`, only test deps (`spring-boot-starter-test`,
  `jackson-datatype-jsr310`).
- [x] Move `EmployeeCreatedMessage` to
  `contracts/src/main/java/com/wangqi/contracts/messaging/EmployeeCreatedMessage.java`,
  same four components, Javadoc states the FQCN is part of the wire contract.
- [x] Add `EmployeeCreatedMessageTest`: Jackson round trip with
  `JavaTimeModule`, and assert `getName()` is
  `com.wangqi.contracts.messaging.EmployeeCreatedMessage`.

## T3 — `employee-management-api` changes

- [x] `pom.xml`: add `com.wangqi:contracts:1.0.0` and
  `micrometer-tracing-bridge-brave` + `zipkin-reporter-brave` (versions from the Boot BOM).
- [x] Delete `EmployeeCreatedMessage`, `EmployeeCreatedConsumer`,
  `OnboardingInitializer`, `OnboardingTask`, `OnboardingTaskRepository`,
  `OnboardingInitializerTest`.
- [x] Repoint imports in `EmployeeEventRelay`, `EmployeeEventPublisher`,
  `AmqpEmployeeEventPublisher`, `LocalEmployeeEventPublisher`,
  `EmployeeEventRelayTest`, `MessagingConfigTest`.
- [x] Slim `MessagingProperties` to `enabled`, `exchange`, `routingKey`.
- [x] `RabbitMqConfig`: remove `onboardingQueue`, `onboardingDeadLetterQueue`,
  `onboardingBinding`; keep exchange, converter and `RabbitTemplate` with confirm callback.
- [x] Rewrite `MessagingConfigTest` to assert the producer-side exchange and JSON
  round trip only.
- [x] `application.yml`: add `management.tracing.sampling.probability: 1.0`,
  `management.zipkin.tracing.endpoint: ${ZIPKIN_ENDPOINT:http://localhost:9411/api/v2/spans}`,
  `spring.rabbitmq.template.observation-enabled: true`, log pattern with
  `trace=%X{traceId:-none}` and `request=%X{requestId:-none}`.
- [x] Replace `Dockerfile` with the multi-module variant (copy root + all module
  POMs, `mvn -pl employee-management-api -am clean package -DskipTests`).
- [x] Delete module `docker-compose.yml` (superseded by root compose).

## T4 — `onboarding-service`

- [x] `pom.xml`: web, data-jpa, validation, security, actuator, prometheus, amqp,
  jjwt (api/impl/jackson 0.12.6), h2, lombok, contracts, tracing deps,
  `spring-boot-starter-test`, `spring-security-test`.
- [x] `OnboardingServiceApplication`.
- [x] Copy `OnboardingTask` + `OnboardingTaskRepository` into
  `com.wangqi.onboarding.entity` / `.repository`.
- [x] Move `OnboardingInitializer` to `com.wangqi.onboarding.service`, import contract.
- [x] Move `EmployeeCreatedConsumer` to `com.wangqi.onboarding.messaging`.
- [x] `config/MessagingProperties` + `config/RabbitMqConfig` (exchange, queue,
  DLQ, binding, JSON converter; `@ConditionalOnProperty app.messaging.enabled`).
- [x] `dto/OnboardingTaskResponse` + `controller/OnboardingTaskController`
  (`GET /api/onboarding/tasks`, newest first).
- [x] `security/` stateless JWT: `JwtProperties`, `JwtService` (subject + role),
  `JwtAuthenticationFilter` (no DB), `SecurityConfig`, `RestAuthenticationEntryPoint`,
  `exception/ApiError`.
- [x] `application.yml`: port 8081, H2 `jdbc:h2:mem:onboardingdb`, rabbit config,
  listener observation enabled, `default-requeue-rejected: false`, tracing/zipkin.
- [x] `src/test/resources/application.properties`: `app.messaging.enabled=false`,
  `management.tracing.enabled=false`.
- [x] Tests: `OnboardingInitializerTest` (migrated), `RabbitMqTopologyTest`,
  `OnboardingApiTest` (401 / 200 with shared-secret token), `JwtServiceTest`.
- [x] `Dockerfile` (port 8081).

## T5 — `api-gateway`

- [x] `pom.xml` with Spring Cloud BOM `2023.0.3`, `spring-cloud-starter-gateway`,
  actuator, tracing, test.
- [x] `ApiGatewayApplication`.
- [x] `application.yml`: port 8000, two routes with env-overridable URIs, tracing,
  log pattern with traceId.
- [x] `GatewayRoutingTest` using `RouteDefinitionLocator`.
- [x] `Dockerfile` (port 8000).

## T6 — Root `docker-compose.yml`

- [x] redis, rabbitmq, zipkin, employee-api, onboarding-service, gateway;
  health checks, volumes, shared `JWT_SECRET`, service-to-service URIs,
  `ZIPKIN_ENDPOINT`.

## T7 — README

- [x] Move `employee-management-api/README.md` to root `README.md`.
- [x] Update intro, tech stack (Spring Cloud Gateway, Micrometer Tracing, Zipkin,
  contracts), Key Features, Claim→Evidence table.
- [x] New "Architecture: evolving from a monolith" section: why, boundary rule,
  producer/consumer ownership, extraction evidence (producer diff is imports only).
- [x] Rewrite Messaging section: consumer now lives in `onboarding-service`.
- [x] Extend Observability section with distributed tracing and the shared trace id.
- [x] Rewrite Docker section for six services and gateway-first URLs.
- [x] Update API Endpoints table with `GET /api/onboarding/tasks` and the gateway base URL.
- [x] Rewrite Getting Started (aggregator build, per-module run, gateway at 8000).
- [x] Replace Project Structure tree with the multi-module tree.
- [x] Update "What This Project Demonstrates" and the test count from the actual run.

## T8 — Verification

- [x] `mvn -q -DskipTests package` at root.
- [x] `mvn -q test` at root; capture counts.
- [x] Start the three jars locally (onboarding with messaging disabled) and verify
  through the gateway: `401`, login, `200 /api/departments`, `200 /api/onboarding/tasks`.
- [x] Verify one identical `traceId` in gateway and employee-api logs for one request.
- [x] Stop all local processes.
- [ ] Docker compose end-to-end is **blocked until the user installs Docker Desktop**;
  after installation run `docker compose up --build` and check queue counters,
  the onboarding row, the shared trace id, and Zipkin.

## Approval gate

Implementation starts only after `spec.md` is approved.
