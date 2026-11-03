# Spec: Incremental Evolution from Monolith to Microservices

Status: **DRAFT — awaiting approval**
Author: wangqi
Date: 2026-10-23

## 1. Goal

Evolve the existing single-module `employee-management-api` into a small,
running microservice system **without rewriting business code**:

- one shared message contract module;
- the existing employee API keeps employees, departments, auth, cache, JWT and the event producer;
- a new `onboarding-service` owns the onboarding workflow and its consumer;
- a new `api-gateway` is the single public entry point;
- distributed tracing across gateway → HTTP service → RabbitMQ consumer;
- one `docker compose up` starts everything (pending Docker Desktop installation).

The selling point is **incremental strangler-style extraction**: the producer
is not modified when the consumer moves to another service — only the shared
contract is imported.

## 2. Scope

### In scope

| Item | Decision |
| ---- | -------- |
| Build | Root Maven aggregator (`packaging=pom`) with 4 modules |
| Shared contract | New `contracts` module, one `EmployeeCreatedMessage` record |
| Existing service | Stays at `employee-management-api`, port **8080**, keeps all existing features |
| Extracted service | New `onboarding-service`, port **8081**, owns onboarding entity/repository/consumer |
| Gateway | New `api-gateway`, port **8000**, Spring Cloud Gateway (reactive) |
| Tracing | Micrometer Tracing + Brave + Zipkin reporter in all three services; AMQP observation enabled |
| Local topology | Root `docker-compose.yml`: redis, rabbitmq, zipkin, employee-api, onboarding-service, gateway |
| Docs | README moved to repo root and rewritten around the new architecture |

### Explicit non-goals

- No service discovery / Eureka, no Config Server, no Kubernetes.
- No per-service database: each service keeps its own in-memory H2 for the demo;
  sharing a physical DB is named as a limitation.
- No transactional outbox, no CDC (already documented as a limitation).
- No gateway-level authentication: each downstream service validates the JWT.
- No JWT key rotation / JWKS: the HS256 secret is shared via `JWT_SECRET`.
- No UI changes; no new business endpoints other than `GET /api/onboarding/tasks`.

## 3. Target Architecture

```
                         :8000  api-gateway (Spring Cloud Gateway)
                                   │
          ┌────────────────────────┴─────────────────────────┐
          ▼                                                  ▼
 :8080 employee-management-api                     :8081 onboarding-service
 employees / departments / auth                     GET /api/onboarding/tasks
 Redis cache, JWT issue, External HR, JPA           own H2, JWT validate, consumer
          │                                                  ▲
          │ publish employee.created (after commit)          │ consume
          └──────────► RabbitMQ exchange employee.events ─────┘
                          queue employee.created.onboarding (+ DLQ)

 All three services ──► Zipkin :9411 (spans); traceId also in logs
```

Boundary rule: **the producer owns the exchange; the consumer owns the queue,
binding and DLQ.** The employee API no longer declares the onboarding queue.

## 4. Module Layout

```
employee-management/                        (root, Maven aggregator, compose, README)
├── pom.xml                                 modules: contracts, employee-management-api,
│                                                    onboarding-service, api-gateway
├── docker-compose.yml
├── .dockerignore
├── README.md
├── contracts/
│   ├── pom.xml
│   └── src/main/java/com/wangqi/contracts/messaging/EmployeeCreatedMessage.java
│   └── src/test/java/com/wangqi/contracts/messaging/EmployeeCreatedMessageTest.java
├── employee-management-api/               (existing, modified)
├── onboarding-service/                     (new)
└── api-gateway/                            (new)
```

### 4.1 `contracts`

`EmployeeCreatedMessage` moves from `com.wangqi.employee.messaging` to:

```java
package com.wangqi.contracts.messaging;

public record EmployeeCreatedMessage(
        Long employeeId,
        String fullName,
        String email,
        Instant occurredAt) {
}
```

Rationale: the FQCN is serialized by `Jackson2JsonMessageConverter` into the
`__TypeId__` header. Producer and consumer **must** use the identical class, so
the contract is a shared artifact, not a copied class.

- No runtime dependencies (plain Java + `Instant`).
- Test-scope `jackson-databind` + `jackson-datatype-jsr310`.
- Test proves JSON round trip and that the FQCN is exactly the documented one.

### 4.2 `employee-management-api` (modifications only)

**Deleted files**

| File | Reason |
| ---- | ------ |
| `messaging/EmployeeCreatedMessage.java` | moved to `contracts` |
| `messaging/EmployeeCreatedConsumer.java` | moved to onboarding-service |
| `messaging/OnboardingInitializer.java` | moved to onboarding-service |
| `entity/OnboardingTask.java` | moved to onboarding-service |
| `repository/OnboardingTaskRepository.java` | moved to onboarding-service |
| `src/test/.../messaging/OnboardingInitializerTest.java` | moved with the class |

**Edited files**

| File | Change |
| ---- | ------ |
| `pom.xml` | add `com.wangqi:contracts:1.0.0`; add `micrometer-tracing-bridge-brave` + `zipkin-reporter-brave` |
| `messaging/EmployeeEventRelay.java` | import `com.wangqi.contracts.messaging.EmployeeCreatedMessage` |
| `messaging/EmployeeEventPublisher.java` | same import |
| `messaging/AmqpEmployeeEventPublisher.java` | same import |
| `messaging/LocalEmployeeEventPublisher.java` | same import |
| `config/MessagingProperties.java` | slim to `enabled`, `exchange`, `routingKey` (queue/DLQ now owned by the consumer) |
| `config/RabbitMqConfig.java` | keep durable `TopicExchange`, JSON `MessageConverter`, `RabbitTemplate` + confirm callback; **remove** queue, DLQ and binding beans |
| `src/main/resources/application.yml` | add tracing/zipkin config, `spring.rabbitmq.template.observation-enabled: true`, log pattern gains `%X{traceId}` |
| `src/test/.../config/MessagingConfigTest.java` | keep exchange/durability + JSON round-trip; queue/DLQ/binding assertions move to onboarding-service |
| `src/test/.../messaging/EmployeeEventRelayTest.java` | import the contract record |
| `Dockerfile` | multi-module build: copy root + all module POMs, `mvn -pl employee-management-api -am clean package -DskipTests` |
| `docker-compose.yml` | deleted; replaced by root compose |

Public behaviour of the employee API is unchanged (same routes, same status
codes, same error contract, same cache behaviour).

### 4.3 `onboarding-service` (new)

Package root: `com.wangqi.onboarding`

| File | Responsibility |
| ---- | -------------- |
| `OnboardingServiceApplication.java` | `@SpringBootApplication` |
| `config/MessagingProperties.java` | `enabled`, `exchange`, `routingKey`, `queue`, `deadLetterQueue` |
| `config/RabbitMqConfig.java` | durable topic exchange, durable queue with DLQ args, DLQ, binding, `Jackson2JsonMessageConverter`; guarded by `app.messaging.enabled` |
| `entity/OnboardingTask.java` | same table/unique constraint as before |
| `repository/OnboardingTaskRepository.java` | `existsByEmployeeId` |
| `service/OnboardingInitializer.java` | transactional, idempotent; reads contract record |
| `messaging/EmployeeCreatedConsumer.java` | `@RabbitListener(queues = "${app.messaging.queue}")` |
| `controller/OnboardingTaskController.java` | `GET /api/onboarding/tasks` → `200` `List<OnboardingTaskResponse>` ordered by `createdAt` desc |
| `dto/OnboardingTaskResponse.java` | `id`, `employeeId`, `fullName`, `email`, `createdAt` |
| `security/JwtProperties.java` | `app.jwt.secret`, `app.jwt.expiration` |
| `security/JwtService.java` | parse + verify HS256 token; expose `subject` and `role` claim; invalid → `Optional.empty()` |
| `security/JwtAuthenticationFilter.java` | stateless: build `UsernamePasswordAuthenticationToken` from subject + role claim; **no DB lookup** |
| `security/SecurityConfig.java` | stateless, CSRF off, `/api/**` authenticated, actuator health/info/metrics open, JSON 401 entry point |
| `security/RestAuthenticationEntryPoint.java` (+ `exception/ApiError.java`) | same JSON error shape as the employee API |
| `src/main/resources/application.yml` | port 8081, own H2 `jdbc:h2:mem:onboardingdb`, RabbitMQ, tracing/zipkin, AMQP listener observation enabled |
| `src/test/resources/application.properties` | `app.messaging.enabled=false`, `management.tracing.enabled=false` |
| `Dockerfile` | multi-module build, port 8081 |

Why a local JWT validator instead of importing `JwtService` from the other
module: services must be independently deployable; only the **message contract**
is shared. The shared-secret limitation (no JWKS) is documented.

### 4.4 `api-gateway` (new)

Package root: `com.wangqi.gateway`

| File | Responsibility |
| ---- | -------------- |
| `ApiGatewayApplication.java` | `@SpringBootApplication` |
| `src/main/resources/application.yml` | port 8000; routes below; tracing/zipkin; log pattern with `%X{traceId}` |
| `src/test/.../GatewayRoutingTest.java` | assert route ids, path predicates, and target URIs from `RouteDefinitionLocator` |
| `pom.xml` | `spring-cloud-starter-gateway`, actuator, tracing, test; Spring Cloud BOM **2023.0.3** |
| `Dockerfile` | multi-module build, port 8000 |

Routes (URIs overridable by env):

| id | predicate | target | env var |
| -- | --------- | ------ | ------- |
| `employee-api` | `/api/auth/**`, `/api/employees/**`, `/api/departments/**`, `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html` | `http://localhost:8080` | `EMPLOYEE_API_URI` |
| `onboarding-service` | `/api/onboarding/**` | `http://localhost:8081` | `ONBOARDING_SERVICE_URI` |

No authentication at the gateway by design (documented).

## 5. Distributed Tracing

Both services and the gateway add:

```xml
<dependency>io.micrometer:micrometer-tracing-bridge-brave</dependency>
<dependency>io.zipkin.reporter2:zipkin-reporter-brave</dependency>
```

Common configuration:

```yaml
management:
  tracing:
    sampling:
      probability: 1.0
  zipkin:
    tracing:
      endpoint: ${ZIPKIN_ENDPOINT:http://localhost:9411/api/v2/spans}
```

- HTTP propagation: W3C `traceparent` — gateway creates the trace, downstream services join it.
- AMQP propagation: `spring.rabbitmq.template.observation-enabled: true` on the producer,
  `spring.rabbitmq.listener.simple.observation-enabled: true` on the consumer, so
  `traceId` crosses the queue.
- Log pattern: `%d ... %-5level [trace=%X{traceId:-none} request=%X{requestId:-none}] ...`
  (`requestId` only in employee-api, which has `CorrelationIdFilter`).
- With no Zipkin container the reporter fails quietly; trace ids remain in logs,
  so tracing is still demonstrable offline.

## 6. Docker / Local Topology

Root `docker-compose.yml` services:

| Service | Image / build | Ports | Depends on |
| ------- | ------------- | ----- | ---------- |
| `redis` | `redis:7-alpine` | 6379 | — |
| `rabbitmq` | `rabbitmq:3.13-management-alpine` | 5672, 15672 | — |
| `zipkin` | `openzipkin/zipkin:3` | 9411 | — |
| `employee-api` | build root context, `employee-management-api/Dockerfile` | 8080 | redis healthy, rabbitmq healthy |
| `onboarding-service` | build root context, `onboarding-service/Dockerfile` | 8081 | rabbitmq healthy |
| `api-gateway` | build root context, `api-gateway/Dockerfile` | 8000 | employee-api healthy, onboarding-service healthy |

Shared environment: `JWT_SECRET` for employee-api + onboarding-service;
`ZIPKIN_ENDPOINT=http://zipkin:9411/api/v2/spans` for all three;
`EMPLOYEE_API_URI=http://employee-api:8080`, `ONBOARDING_SERVICE_URI=http://onboarding-service:8081`
for the gateway.

## 7. Tests

All tests must run with **no broker, no Redis, no Zipkin**.

| Module | Test | Proves |
| ------ | ---- | ------ |
| contracts | `EmployeeCreatedMessageTest` | JSON round trip; stable FQCN used as `__TypeId__` |
| employee-api | existing suite (minus migrated tests) | unchanged behaviour, 401/403, cache, N+1, idempotency, relay, topology producer side |
| onboarding-service | `OnboardingInitializerTest` (migrated) | redelivery is a no-op; one row |
| onboarding-service | `RabbitMqTopologyTest` | durable queue, DLQ args, binding, JSON round trip |
| onboarding-service | `OnboardingApiTest` | `401` without token; `200` + payload with a token signed by the shared secret |
| onboarding-service | `JwtServiceTest` | valid token parsed (subject/role); tampered/expired token rejected |
| api-gateway | `GatewayRoutingTest` | both routes, predicates, target URIs |

## 8. Verification Steps

1. `mvn -q -DskipTests package` at the root (contracts is built in the same reactor).
2. `mvn -q test` at the root; report the actual test count.
3. Local run without Docker:
   - `java -jar employee-management-api/target/*.jar` (messaging can stay enabled; no listener exists there),
   - `java -jar onboarding-service/target/*.jar --app.messaging.enabled=false`,
   - `java -jar api-gateway/target/*.jar`.
   - Assert through the gateway: `401` without token, `200` after login for
     `/api/departments`, `200` for `/api/onboarding/tasks` with the same token.
   - Assert the same `traceId` appears in the gateway log and the employee-api
     log for one request.
4. Docker end-to-end (**requires the user to install Docker Desktop**):
   `docker compose up --build`, create an employee through `:8000`, verify the
   onboarding row via `GET :8000/api/onboarding/tasks`, verify the producer and
   consumer log lines share one `traceId`, and inspect Zipkin at `:9411`.

## 9. Risks and Trade-offs (to be stated in README)

- Shared HS256 secret is a demo shortcut; production should use asymmetric keys/JWKS.
- Each service keeps its own H2 instance; a real deployment needs a per-service
  database and a data-ownership story for onboarding (employee snapshot in the event).
- Publishing after commit still has the commit→publish gap (outbox not implemented).
- Gateway has no auth and no rate limiting; downstream services enforce auth.
- If the employee API starts and publishes before the consumer's queue is bound,
  RabbitMQ drops that message (exchange exists, no binding). Accepted for the demo;
  production would pre-declare topology or use a queue per consumer with durable bindings.

## 10. Approval

Please approve this spec before implementation starts. After approval the
execution order is in `tasks.md`.
