# Employee Management — Microservices Reference System

Three independently deployable Spring Boot services that began as one REST API
and were split **incrementally**: an employee API, an onboarding consumer, and a
Spring Cloud Gateway in front of both, with Micrometer/Zipkin tracing that
follows a request across the HTTP and AMQP boundaries.

It demonstrates the practices that matter in a real backend service:
**event-driven service boundaries**, **an API gateway**, **distributed tracing**,
clean layering, **global exception handling**, **strict request validation**,
correct **HTTP status codes**, **stateless JWT security**, **resilient
third-party integration with retries and idempotency**, **paginated dynamic
search**, **a Redis read cache with explicit invalidation**, **event-driven
decoupling through RabbitMQ**, **self-documenting OpenAPI**, **correlation-id
tracing and metrics**, **bounded async execution**, **automated tests**, three
**container images**, and a measured fix for the classic **JPA N+1 query
problem**.

> Author: **wangqi**

---

## Tech Stack

| Layer            | Technology                                          |
| ---------------- | --------------------------------------------------- |
| Language         | Java 17                                             |
| Framework        | Spring Boot 3.3.5, Spring Web (MVC)                 |
| Gateway          | Spring Cloud Gateway 4.1.x, Spring Cloud 2023.0.3   |
| Service split    | 4 Maven modules: `contracts`, `employee-management-api`, `onboarding-service`, `api-gateway` |
| Persistence      | Spring Data JPA / Hibernate 6.5                     |
| Database         | H2 (in-memory, one instance per service)            |
| Cache            | Redis 7 + Spring Cache (`@Cacheable`, `@CacheEvict`)|
| Messaging        | RabbitMQ 3.13 + Spring AMQP, publisher confirms, dead-letter queue, one consumer service |
| Tracing          | Micrometer Tracing 1.3 (Brave bridge), Zipkin 3     |
| Validation       | Jakarta Bean Validation (Hibernate Validator)       |
| Security         | Spring Security 6, JWT (JJWT 0.12), BCrypt          |
| Integration      | Spring `RestClient` over JDK `HttpClient`, bounded retries with exponential backoff |
| API Docs         | springdoc-openapi 2.6 (Swagger UI)                  |
| Observability    | Spring Boot Actuator, Micrometer, Prometheus        |
| Async            | Spring `@Async` with a bounded `ThreadPoolTaskExecutor` |
| Packaging        | Docker (multi-stage builds), Docker Compose (6 services) |
| Boilerplate      | Lombok                                              |
| Testing          | JUnit 5, Mockito, MockMvc, AssertJ                  |
| Build            | Maven 3.9 (root reactor)                            |

---

## Key Features

- **Incremental Microservice Split** — the onboarding consumer was extracted
  from the employee API into its own service, and the write path did not change:
  the producer kept publishing the same event to the same exchange. Only the
  message contract is shared, through a dedicated `contracts` module whose class
  name travels in the AMQP `__TypeId__` header. Topology ownership moved with
  the consumer (queue, binding, DLQ), so the producer no longer knows who listens.
- **API Gateway** — a Spring Cloud Gateway on `:8000` is the only public entry
  point. It routes `/api/auth`, `/api/employees` and `/api/departments` to the
  employee API and `/api/onboarding` to the onboarding service. Authentication
  stays downstream: a gateway cannot widen a caller's permissions.
- **Distributed Tracing** — every request gets a trace at the gateway; the trace
  context is forwarded over HTTP and over AMQP
  (`spring.rabbitmq.template.observation-enabled` /
  `listener.simple.observation-enabled`), so the gateway, the API and the
  consumer log the **same** `traceId`. Spans are exported to Zipkin at `:9411`.
- **Global Exception Handling** — a single `@RestControllerAdvice` translates
  domain exceptions into meaningful HTTP responses. Clients never receive a raw
  stack trace or an accidental `500` for a predictable error.
- **Strict Request Validation** — DTOs are annotated with `@NotBlank`, `@Email`,
  `@Min`, `@Size` and validated with `@Valid`. When validation fails, the API
  returns a structured `400 Bad Request` that lists **exactly which fields are
  wrong and why**.
- **Standard HTTP Status Codes** — `201 Created` (with a `Location` header) on
  creation, `200 OK` on a successful read, `204 No Content` on deletion,
  `404 Not Found` for unknown ids and `409 Conflict` for duplicate emails.
- **Automated Tests** — the service layer is unit-tested with Mockito, and the
  web layer is tested with `@WebMvcTest` + `MockMvc`.
- **JPA Performance (N+1 elimination)** — listing departments together with
  their employees uses a single `@EntityGraph` fetch instead of one extra
  query per department. **The difference is asserted in tests, not claimed**:
  4 statements before the fix, 1 after it.
- **Stateless JWT Security** — login issues an HS256 token, a request filter
  validates it, and role-based rules return **`401` for missing credentials**
  and **`403` for insufficient roles** using the same JSON error contract as
  every other failure.
- **Resilient Integrations** — outbound calls to a third-party HR system retry
  with exponential backoff on timeouts and `5xx` responses, never on `4xx`, and
  surface an exhausted retry budget as **`502 Bad Gateway`** instead of a raw
  connector exception.
- **Idempotent Writes** — `POST /api/employees` accepts an `Idempotency-Key`
  header. A repeated key with the same payload replays the original response
  (`Idempotent-Replay: true`) without creating a duplicate; the same key with a
  different payload returns **`409 Conflict`**.
- **Paginated Dynamic Search** — `GET /api/employees` filters by name, email,
  job title, department and salary range with JPA `Specification`, returns a
  stable `PageResponse` envelope, caps the page size, and whitelists sortable
  fields so bad input becomes a clean `400` instead of a persistence error.
- **Redis Read Cache** — `GET /api/employees/{id}` and `GET /api/departments`
  are served from Redis with per-cache TTLs (10m / 5m), namespaced keys and
  human-readable JSON values, and every write that changes a cached view
  evicts it. **Measured, not claimed**: a test asserts the second read sends
  **zero** SQL statements, and hit/miss counters are exposed through Actuator.
- **Self-Documenting API** — springdoc generates an OpenAPI 3 description, and
  Swagger UI at `/swagger-ui.html` lets a client authenticate with the JWT and
  exercise every endpoint without writing a single line of code.
- **Observability** — every request gets a correlation id (`X-Request-Id`),
  propagated into the SLF4J MDC and into error payloads, and exposed through
  Actuator plus a Prometheus scrape endpoint.
- **Event-Driven Decoupling** — a committed employee emits an
  `employee.created` message to a RabbitMQ topic exchange; onboarding is started
  by the **onboarding service**, not by the request thread. The publisher does not
  know who listens, the consumer is **idempotent** (duplicate deliveries are
  proven not to create a second task), and a rejected message lands on a
  **dead-letter queue** rather than being redelivered forever.
- **Bounded Async Execution** — new-employee notifications run on a dedicated
  pool with explicit core/max/queue limits, caller-runs rejection, and MDC
  propagation, so background work can neither starve requests nor lose its
  trace context.
- **Containerised** — multi-stage Dockerfiles build with the full JDK and ship
  non-root JRE images with health checks; `docker compose up` starts the gateway,
  both services, Redis, RabbitMQ and Zipkin together.

> **What this project demonstrates:** I can find a performance problem at the
> SQL level and prove the fix with query-count assertions, add stateless JWT
> authentication with correct `401`/`403` semantics, make a third-party
> integration safe to retry by combining bounded exponential backoff with
> database-backed idempotency, push filtering into SQL behind a whitelisted sort
> surface, take the database off the hot read path with a Redis cache whose
> invalidation is asserted in tests, decouple the write path with an
> event stream that other services can subscribe to, **extract the consumer into
> its own deployable service without touching the producer**, put both services
> behind a gateway, and **keep one trace id across gateway → HTTP → queue →
> consumer** — while still operating the result with correlation ids, metrics, a
> bounded async pool, health-checked containers and an unchanged error contract.

| Claim | Evidence in this repository |
| ----- | --------------------------- |
| The N+1 query is really gone | `DepartmentQueryCountTest` — **4** statements before the fix, **1** after |
| The cache really removes database work | `CacheBehaviorIntegrationTest` — **1** statement on the first read, **0** on the second; eviction asserted on create/delete |
| Retries cannot create duplicates | `IdempotencyIntegrationTest` — replay with `Idempotent-Replay: true`, `409` on key reuse |
| Security distinguishes unauthenticated from unauthorised | `SecurityIntegrationTest` — `401` without a token, `403` for the wrong role |
| Every failure is a structured contract | `EmployeeControllerTest` — field-level `400` bodies; correlation id asserted in `CorrelationIdFilterTest` |
| The split contract is really shared | `EmployeeCreatedMessageTest` — JSON round trip and the stable FQCN sent as `__TypeId__` |
| The consumer really owns its topology | `RabbitMqTopologyTest` — durable queue, DLQ arguments, binding, JSON round trip |
| The extracted service really authenticates the issuer's tokens | `OnboardingApiTest` — `401` anonymously, `200` with an HS256 token signed by the employee API's secret; `JwtServiceTest` rejects tampered/expired tokens |
| The gateway really exposes both services | `GatewayRoutingTest` — route ids, path predicates and target URIs |
| Async work cannot starve or lose the trace | `AsyncConfigTest` — pool bounds plus MDC propagation |
| A redelivered message cannot do the work twice | `OnboardingInitializerTest` — second delivery returns `false`, one row |
| Broker topology is what the README says | `MessagingConfigTest` — durable producer exchange; queue/DLQ asserted from the consumer side |
| All of the above is green | **68 tests**, `mvn test` at the repository root |

---

## Architecture: Evolving a Monolith into Services

The original API already published `employee.created` to RabbitMQ, but the
consumer lived inside the same process. That made the extraction cheap **by
design**: the boundary was already an event, not a method call.

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

 All three services ──► Zipkin :9411 (spans); traceId is also in every log line
```

| Boundary decision | Why |
| ----------------- | --- |
| Split by **event**, not by entity | The consumer only needs a snapshot (`employeeId`, name, email) that the event already carried; no synchronous call back to the API is required. |
| Share the **contract**, not the code | Producer and consumer depend on `contracts`. `Jackson2JsonMessageConverter` puts the class name in `__TypeId__`; two copies under different packages would fail to deserialise. |
| Topology belongs to the **consumer** | The onboarding service owns the queue, binding and DLQ. The producer only declares the exchange. Extracting the consumer therefore changed **no producer logic** — only imports of the shared DTO. |
| Each service has its **own database** | The onboarding service never reads employee tables. The event is the data-transfer boundary. |
| Authenticate **downstream**, not at the gateway | Both services validate the same HS256 token. A gateway that trusts itself is not a security boundary. |

**What the extraction actually changed in the producer:** the four messaging
classes that remained (`EmployeeEventRelay`, `EmployeeEventPublisher`,
`AmqpEmployeeEventPublisher`, `LocalEmployeeEventPublisher`) only had their
`EmployeeCreatedMessage` import repointed. The consumer, its entity, repository
and idempotency logic moved to `onboarding-service` unchanged.

**Deliberate trade-offs, stated rather than hidden:**

- The HS256 key is shared through `JWT_SECRET`. Production would use asymmetric
  keys and a JWKS endpoint so only the issuer holds the private key.
- H2 is still in-memory and per service. A real deployment needs a per-service
  database; onboarding owns an employee **snapshot** inside the event.
- No service discovery: Compose service names (or localhost) are configured
  explicitly. Eureka/Consul was left out because it would add infrastructure
  without demonstrating a new backend skill.
- If the API publishes before the consumer's queue is bound, RabbitMQ drops the
  message (the exchange exists, the binding does not). Compose starts the
  consumer first; production would pre-declare topology.
- The commit→publish gap is still closed only by a documented outbox proposal,
  not by an implementation.

---

## JPA Performance: Fixing the N+1 Problem

`Department` → `Employee` is a lazy one-to-many association. The naive way to
serialise a department list is to load the departments and then touch
`getEmployees()` on each one:

```java
List<Department> departments = departmentRepository.findAll();   // 1 query
departments.forEach(d -> d.getEmployees().size());               // +1 query each
```

With 3 departments and 5 employees each, Hibernate sends **4 statements** to the
database. Add 50 departments and you get 51 statements — the classic N+1.

The repository exposes an entity-graph query that fetches the whole aggregate
in one round trip:

```java
@EntityGraph(attributePaths = "employees")
@Query("select distinct d from Department d")
List<Department> findAllWithEmployees();
```

| Access path                                   | Statements executed | Result       |
| --------------------------------------------- | ------------------- | ------------ |
| `findAll()` + lazy `getEmployees()`           | **4**               | N+1 problem  |
| `findAllWithEmployees()` with `@EntityGraph`   | **1**               | fixed        |

Both numbers are measured by `DepartmentQueryCountTest`, which registers a
Hibernate `StatementInspector` and asserts the exact statement count. The test
fails if someone removes the entity graph or reintroduces a lazy traversal.

---

## Search: Pagination, Dynamic Filters, and Safe Sorting

```
GET /api/employees?jobTitle=engineer&minSalary=5000&page=0&size=20&sort=salary,desc
```

| Parameter      | Default | Rules                                                       |
| -------------- | ------- | ----------------------------------------------------------- |
| `firstName`    | —       | case-insensitive contains                                   |
| `lastName`     | —       | case-insensitive contains                                   |
| `email`        | —       | case-insensitive contains                                   |
| `jobTitle`     | —       | case-insensitive contains                                   |
| `departmentId` | —       | exact match                                                 |
| `minSalary`    | —       | inclusive lower bound                                       |
| `maxSalary`    | —       | inclusive upper bound                                       |
| `page`         | `0`     | zero-based; negative → `400`                                |
| `size`         | `20`    | `1..100`; above the cap → `400`                             |
| `sort`         | `id,asc`| `field` or `field,asc\|desc`, repeatable                    |

```json
{
  "content": [
    {
      "id": 1,
      "firstName": "Alice",
      "lastName": "Chen",
      "email": "alice@example.com",
      "jobTitle": "Senior Backend Engineer",
      "salary": 9000.00,
      "createdAt": "2024-01-01T10:15:30Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "first": true,
  "last": true
}
```

Implementation notes:

- Filters are compiled into a single SQL statement with `Specification`
  (`EmployeeSpecifications`). Nothing is loaded and filtered in memory, and an
  absent filter contributes no predicate at all.
- The response is a **stable `PageResponse` envelope**, not Spring's internal
  `Page` serialisation, which changes shape between versions and is not a
  contract you should hand to a client.
- `size` is capped at 100; without a cap one request can ask the server to
  materialise the whole table.
- Sort fields come from a **whitelist** (`id`, `firstName`, `lastName`, `email`,
  `jobTitle`, `salary`, `createdAt`). An unknown field or direction returns
  `400` with the allowed values, so untrusted input never reaches a JPA
  property path.
- `sort=salary,desc` is read from the raw request parameters — Spring's
  comma-aware converter would otherwise split it into two separate values.

Covered by `EmployeeSearchIntegrationTest` (paging, sorting, combined filters,
rejected sort field, rejected page size) and a Mockito unit test for the
service mapping.

---

## Caching: Redis on the Hot Read Path

Two endpoints are hit on almost every page load and are cached:

| Cache         | Key                                    | TTL  | Evicted by |
| ------------- | -------------------------------------- | ---- | ---------- |
| `employees`   | the employee id                        | 10m  | `DELETE /api/employees/{id}` |
| `departments` | one entry (`SimpleKey.EMPTY`)          | 5m   | `POST /api/departments`, `POST /api/employees`, `DELETE /api/employees/{id}` |

The department listing embeds its employees, so an employee write invalidates
it as well — the cache does not know about joins, and pretending otherwise is
how stale data reaches users.

```java
@Cacheable(cacheNames = CacheConfig.EMPLOYEES, key = "#id")
@Transactional(readOnly = true)
public EmployeeResponse getEmployeeById(Long id) { ... }

@Caching(evict = {
        @CacheEvict(cacheNames = CacheConfig.EMPLOYEES, key = "#id"),
        @CacheEvict(cacheNames = CacheConfig.DEPARTMENTS, allEntries = true)
})
public void deleteEmployee(Long id) { ... }
```

Design decisions:

- **Per-cache TTL** — a missed eviction self-heals within minutes instead of
  staying wrong forever.
- **Keys are namespaced** (`employee-api:employees::1`) so several services can
  share one Redis instance without collisions, and an operator can see which
  application owns a key.
- **Values are JSON, not JDK-serialised bytes** — `redis-cli GET` returns a
  readable payload. Records are `final`, so the serializer is configured with
  `DefaultTyping.EVERYTHING`; otherwise a cached record would deserialize into a
  `LinkedHashMap` and fail on the *second* request. For the same reason the
  department list is materialised as an `ArrayList` (`Stream.toList()` returns a
  JDK-internal immutable class that cannot be reconstructed).
- **`null` is never cached**, so an unknown id keeps returning a real `404`
  instead of a cached "not found".
- **Deliberately not cached** — user lookups (authorities must be reloaded per
  request, see the security notes) and search results (the key space is
  unbounded and the hit ratio is poor).
- **Statistics enabled** — hit/miss counters are exposed as
  `GET /actuator/metrics/cache.gets?tag=cache:employees&tag=result:hit`, so the
  cache is tuned from measurements rather than intuition.

**Verification.** `CacheBehaviorIntegrationTest` counts SQL statements through a
Hibernate `StatementInspector` and asserts **1 statement on the first read, 0 on
the second**, plus eviction on delete and on create. Confirmed against a live
Redis instance:

```
127.0.0.1:6379> TTL employee-api:employees::2
(integer) 599
127.0.0.1:6379> GET employee-api:employees::2
"{\"@class\":\"com.wangqi.employee.dto.EmployeeResponse\",\"id\":[\"java.lang.Long\",2],
 \"firstName\":\"Bob\", ... \"createdAt\":[\"java.time.Instant\",\"2026-10-23T02:43:06.233618Z\"]}"
```

Configuration (`application.yml`, overridable by environment):

| Property                  | Default     | Environment variable |
| ------------------------- | ----------- | -------------------- |
| `spring.data.redis.host`  | `127.0.0.1` | `REDIS_HOST`         |
| `spring.data.redis.port`  | `6379`      | `REDIS_PORT`         |
| `spring.data.redis.password` | `wangqi` | `REDIS_PASSWORD`     |
| `app.cache.provider`      | `redis`     | `CACHE_PROVIDER`     |
| `app.cache.employee-ttl`  | `10m`       | —                    |
| `app.cache.department-ttl`| `5m`        | —                    |

Tests set `app.cache.provider=simple` and `spring.cache.type=simple` (see
`src/test/resources/application.properties`), so `mvn test` runs without a Redis
server while still exercising the same `@Cacheable` / `@CacheEvict` annotations.

---

## Messaging: Decoupling Employee Onboarding

`POST /api/employees` commits the row, then emits an `employee.created` event.
Onboarding is not started by the request thread, and since the extraction it is
not even started in the same process: `onboarding-service` owns the consumer.
Any other service can subscribe to the same event without the API changing.

```
POST /api/employees                                employee-management-api
        │  transaction commits
        ▼
@TransactionalEventListener(AFTER_COMMIT)          EmployeeEventRelay
        │
        ▼
RabbitTemplate.convertAndSend("employee.events", "employee.created", json)
        │  (+ trace context in the message headers)
        ▼
topic exchange ──► employee.created.onboarding ──► EmployeeCreatedConsumer
   (producer &          (owned by the                    │   onboarding-service
    consumer declare)    onboarding service)              ▼
                                              OnboardingInitializer (idempotent)
                                                          │
                                      failure ──► employee.created.onboarding.dlq
```

| Element | Why it is shaped this way |
| ------- | ------------------------- |
| `AFTER_COMMIT` | A message must never announce an employee whose transaction rolled back. |
| Topic exchange | Routing keys let new consumers (`employee.created.mail`, `.provisioning`) bind later without touching the publisher. |
| Producer owns the exchange, consumer owns the queue | The producer only needs somewhere to publish. Queue, binding and DLQ moved with the consumer, which is why the extraction required no producer logic change. |
| Durable queue | The event survives a broker restart; an in-memory queue would silently drop it. |
| Dead-letter queue | A message the consumer rejects is parked for inspection instead of being redelivered forever (`default-requeue-rejected: false`). |
| Publisher confirms | The broker acknowledges receipt; a nack is logged rather than assumed successful. |
| Idempotent consumer | Brokers deliver **at least once**. `OnboardingInitializer` checks `employee_id` before writing, and a unique constraint enforces it at the database level — the second delivery is a no-op, not a second onboarding task. |
| Shared contract | `com.wangqi.contracts.messaging.EmployeeCreatedMessage` is the only shared Java type. Its FQCN is sent as `__TypeId__`, so a copied class would break deserialisation. |
| Failure isolation | If publishing fails after the commit, the relay logs and returns: the employee exists, so a `500` would describe something the caller cannot fix. |

**Deliberate trade-off (the honest limitation):** publishing happens after the
commit but outside it, so a crash in that window loses the event. The production
fix is a **transactional outbox** (write the event in the same transaction, relay
it afterwards) or CDC. It is not implemented here because it adds a table and a
poller without teaching anything new about the broker itself.

**Verification without a broker** — both services' test suites run with
`app.messaging.enabled=false`: the employee API swaps in
`LocalEmployeeEventPublisher`, the onboarding service skips its listener
container and topology declarations.

| Test (module) | What it proves |
| ------------- | -------------- |
| `MessagingConfigTest` (api) | producer-side durable topic exchange and the JSON `__TypeId__` header |
| `EmployeeEventRelayTest` (api) | domain event is mapped to the flat contract; a broker outage does not fail the request |
| `EmployeeCreatedMessageTest` (contracts) | the shared JSON shape and the stable FQCN |
| `RabbitMqTopologyTest` (onboarding) | queue durability, DLQ arguments, binding and JSON round trip |
| `OnboardingInitializerTest` (onboarding) | first delivery creates a task, redelivery is ignored, one row in the table |

To watch it end to end, run `docker compose up` and create an employee through
the gateway. Inspect the queue counters in the management UI at
`http://localhost:15672`; the API log shows
`Published employee.created for employee 1` and the **onboarding** service log
shows `Onboarding initialised for employee 1`, both with the same `traceId`.

Configuration:

| Property | Default | Environment variable |
| -------- | ------- | -------------------- |
| `spring.rabbitmq.host` | `127.0.0.1` | `RABBITMQ_HOST` |
| `spring.rabbitmq.port` | `5672` | `RABBITMQ_PORT` |
| `spring.rabbitmq.username` / `password` | `wangqi` / `wangqi` | `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` |
| `app.messaging.enabled` | `true` | `MESSAGING_ENABLED` |
| `app.messaging.exchange` / `routing-key` | `employee.events` / `employee.created` | — |
| `app.messaging.queue` / `dead-letter-queue` | `employee.created.onboarding` / `employee.created.onboarding.dlq` | onboarding service only |

---

## Security: Stateless JWT Authentication

`POST /api/auth/login` exchanges credentials for a signed HS256 token. Every
subsequent request sends it as `Authorization: Bearer <token>`; no session or
cookie is involved, so the services scale horizontally without shared state.

The gateway forwards the header untouched. The employee API validates the token
against its user table; the onboarding service validates the same signature and
reads the `role` claim, **without a user table and without a database call**, so
the extracted service can answer independently.

| Situation                                  | Response |
| ------------------------------------------ | -------- |
| No token, malformed token, or expired token | `401 Unauthorized` |
| Valid token but insufficient role           | `403 Forbidden` |
| Wrong username or password on login         | `401 Unauthorized` (generic message) |

Access rules:

| Operation                      | Required role          |
| ------------------------------ | ---------------------- |
| `POST /api/auth/login`         | public                 |
| `GET /api/employees/{id}`      | any authenticated user |
| `GET /api/departments`         | any authenticated user |
| `POST /api/employees`          | `ADMIN`                |
| `POST /api/departments`        | `ADMIN`                |
| `DELETE /api/employees/{id}`   | `ADMIN`                |

Implementation notes:

- Passwords are stored as **BCrypt** hashes. The login failure message is
  deliberately generic so it does not reveal whether the username exists.
- Authorities are **reloaded from the database on every request**, so disabling
  or downgrading an account takes effect immediately, even if its token has not
  expired yet.
- Malformed or tampered tokens are treated as unauthenticated and return `401`,
  never a `500`.
- `401` and `403` reuse the shared `ErrorResponse` contract, so security errors
  look exactly like validation and domain errors.
- The signing key comes from `JWT_SECRET`; the value in `application.yml` is a
  development default only. The same value must be given to the employee API and
  the onboarding service (Compose does this), otherwise the extracted service
  rejects every token. Production would replace the shared HS256 secret with
  asymmetric keys and a JWKS endpoint.

Demo accounts (seeded at startup; disable with `app.demo-users.enabled=false`):

| Username | Password    | Role    |
| -------- | ----------- | ------- |
| `admin`  | `admin123`  | `ADMIN` |
| `viewer` | `viewer123` | `USER`  |

---

## Integrations: Retries, Timeouts, and Idempotency

`POST /api/employees/{id}/sync` pushes an employee to an external HR system
(`app.external-hr.base-url`). Third-party systems fail in ways the caller cannot
control, so the client is written to survive transient failures and to make
retrying safe.

### Retry policy

| Upstream outcome                     | Behaviour                                              |
| ------------------------------------ | ------------------------------------------------------ |
| `4xx`                                | **Not retried** — the request itself is wrong; fail fast with `502` |
| `5xx`                                | Retried with exponential backoff                        |
| Connection refused / read timeout    | Retried with exponential backoff                        |
| Retries exhausted                    | `502 Bad Gateway` with a single `ErrorResponse` body    |

Each attempt is bounded by a connect and read timeout (2s by default), so a
hanging upstream cannot pin a Tomcat worker indefinitely. Backoff starts at
200 ms and doubles per attempt (`max-attempts: 3` by default), which keeps a
recovering dependency from being hammered:

```
attempt 1 --fail--> wait 200ms
attempt 2 --fail--> wait 400ms
attempt 3 --fail--> 502 Bad Gateway
```

The loop is a few lines of plain Java on top of Spring's `RestClient` — no
Spring Retry, no Resilience4j, no extra runtime dependency. The number of
attempts actually made is returned to the caller:

```json
{ "employeeId": 1, "attempts": 2 }
```

### Idempotent writes

A network retry can deliver the same `POST` twice. Without protection the second
arrival creates a second employee. `POST /api/employees` therefore accepts an
optional `Idempotency-Key` header:

| Scenario                                         | Response                                              |
| ------------------------------------------------ | ----------------------------------------------------- |
| First request with a new key                     | `201 Created`, record stored                          |
| Same key + identical payload                     | `201 Created`, **original body replayed**, `Idempotent-Replay: true` |
| Same key + different payload                     | `409 Conflict`                                        |
| No key                                           | Normal behaviour, nothing recorded                     |
| Two concurrent requests with the same key        | One wins; the loser gets `409 Conflict` and may retry |

How it works:

- The key is the **primary key** of the `idempotency_records` table, so
  uniqueness is enforced by the database, not by application-level locking.
- A **SHA-256 fingerprint** of the canonical request JSON is stored next to the
  key. Key reuse with a different payload is therefore detectable and rejected
  instead of silently returning someone else's result.
- The record is written **inside the same transaction** as the employee, so
  there is no window in which an employee exists without its idempotency record
  (or vice versa).
- Replay is byte-for-byte identical to the original response, so clients can
  safely retry timeouts and connection resets.

Covered by `ExternalHrClientTest` (retry until success, `4xx` not retried, give
up after the budget) and `IdempotencyIntegrationTest` (replay, `409` on payload
mismatch, no duplicate rows).

---

## Observability: Correlation IDs, Distributed Tracing, Health, and Metrics

Every request carries a correlation id. If the caller supplies a safe
`X-Request-Id` (1–64 characters of `A-Z a-z 0-9 . _ -`) it is reused, so an API
gateway's logs line up with ours; otherwise a UUID is generated. The id is:

- echoed back in the `X-Request-Id` response header,
- placed in the SLF4J MDC as `requestId`, which the log pattern renders,
- included as `requestId` in every `ErrorResponse`,
- copied into async worker threads (see the next section),
- removed in a `finally` block so a pooled thread never leaks another request's id.

### Distributed tracing

The correlation id ties one service's log lines together; the **trace id** ties
a whole request across services. All three applications include Micrometer
Tracing with the Brave bridge and the Zipkin reporter:

| Hop | Mechanism |
| --- | --------- |
| client → gateway | the gateway starts a span and accepts an incoming `traceparent` |
| gateway → employee API / onboarding service | W3C `traceparent` header, injected by the tracing HTTP instrumentation |
| employee API → RabbitMQ → onboarding consumer | AMQP observation: the producer injects the trace context into message headers, `spring.rabbitmq.listener.simple.observation-enabled` continues it on the consumer side |
| everywhere → Zipkin | spans sent to `${ZIPKIN_ENDPOINT}` (`http://localhost:9411/api/v2/spans` by default) |

Every log line renders both ids, so one grep proves the propagation:

```
2026-10-23 10:14:02.118 INFO  [trace=7f3a9c1e5d2b4a17 request=3b0f...] c.w.e.messaging.AmqpEmployeeEventPublisher - Published employee.created for employee 42 ...
2026-10-23 10:14:02.341 INFO  [trace=7f3a9c1e5d2b4a17] c.w.o.messaging.EmployeeCreatedConsumer - Onboarding initialised for employee 42 <alice@example.com>
```

`management.tracing.sampling.probability=1.0` traces every request in this demo;
a production service would sample a small percentage. If Zipkin is not running,
the reporter fails quietly and the trace ids are still in the logs, so tracing
is demonstrable offline.

| Endpoint                  | Purpose                                        |
| ------------------------- | ---------------------------------------------- |
| `/actuator/health`        | Liveness/readiness with component details      |
| `/actuator/metrics`       | Micrometer metric names and values             |
| `/actuator/metrics/http.server.requests` | Latency and counts per URI, method, status |
| `/actuator/prometheus`    | Prometheus scrape format                      |
| `/actuator/info`          | Build/app metadata                            |
| `http://localhost:9411`   | Zipkin UI: search by trace id, inspect spans   |

`http.server.requests` is collected automatically by Micrometer, so there is no
hand-written timing code that can drift from reality. These endpoints are open
in this demo for reviewers; in a real deployment they belong behind a network
policy or an authenticated scrape.

---

## Async: A Bounded Pool Instead of the Default

Creating an employee publishes an `EmployeeCreatedEvent` inside the
transaction. Two listeners react to it: this one sends the welcome notification
on the worker pool, and `EmployeeEventRelay` publishes the event to RabbitMQ
(see the messaging section). The notification listener is:

```java
@Async(AsyncConfig.NOTIFICATION_EXECUTOR)
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onEmployeeCreated(EmployeeCreatedEvent event) { ... }
```

Both listeners run after the commit, for the same reason.

- **`AFTER_COMMIT`, not `@EventListener`.** A plain listener fires before the
  transaction commits, so a create that later rolls back would still send a
  welcome notification for an employee that never existed.
- **A dedicated, bounded executor.** The default `@Async` executor is unbounded:
  under load it silently grows a thread per task. The notification pool is
  explicit:

| Setting            | Value                    | Why                                             |
| ------------------ | ------------------------ | ----------------------------------------------- |
| core / max threads | 2 / 4                    | predictable memory and CPU                      |
| queue capacity     | 50                       | bounded backlog                                 |
| rejection policy   | `CallerRunsPolicy`       | backpressure instead of silently dropping work   |
| task decorator     | copies the MDC map       | async logs keep the originating `requestId`     |
| shutdown           | waits up to 10s          | in-flight notifications finish on deploy        |

A slow notification path therefore cannot starve HTTP request threads or any
other async work. `AsyncConfigTest` asserts the pool bounds and proves that the
correlation id survives the thread hand-off.

---

## Docker: One Command, Six Services

```bash
docker compose up --build
# Gateway:   http://localhost:8000   <-- the only public entry point
# Swagger:   http://localhost:8000/swagger-ui.html
# Employee:  http://localhost:8080/actuator/health
# Onboard.:  http://localhost:8081/actuator/health
# Zipkin:    http://localhost:9411
# Redis:     localhost:6379 (password from REDIS_PASSWORD, default `wangqi`)
# RabbitMQ:  amqp://localhost:5672, UI at http://localhost:15672 (wangqi/wangqi)
```

Three application images plus three infrastructure containers:

| Service            | Image / Dockerfile                  | Port | Depends on (healthy)      |
| ------------------ | ----------------------------------- | ---- | ------------------------- |
| `api-gateway`      | `api-gateway/Dockerfile`            | 8000 | employee-api, onboarding  |
| `employee-api`     | `employee-management-api/Dockerfile`| 8080 | redis, rabbitmq           |
| `onboarding-service`| `onboarding-service/Dockerfile`    | 8081 | rabbitmq                  |
| `redis`            | `redis:7-alpine`                    | 6379 | —                         |
| `rabbitmq`         | `rabbitmq:3.13-management-alpine`   | 5672 / 15672 | —                |
| `zipkin`           | `openzipkin/zipkin:3`               | 9411 | —                         |

- **Multi-stage build per module** — Maven + full JDK in the build stage, a
  JRE-only Alpine runtime stage. The build context is the repository root so a
  module can resolve its siblings; only the POMs are copied before
  `mvn -pl <module> -am package`, which keeps the dependency layer cacheable.
- **Non-root** — every application container runs as an unprivileged `app` user.
- **Health checks** — each image and Compose probe `/actuator/health`, so
  orchestration sees "starting" versus "healthy" instead of guessing. The
  gateway waits for both services and the services wait for their dependencies,
  which removes the start-up races a naive compose file hides.
- **Configuration by environment** — `JWT_SECRET` is shared by the API (issuer),
  the onboarding service (verifier) and the gateway tests; `EMPLOYEE_API_URI` and
  `ONBOARDING_SERVICE_URI` are the gateway's route targets;
  `ZIPKIN_ENDPOINT` points every service at the tracer; `EXTERNAL_HR_URL`,
  `REDIS_*` and `RABBITMQ_*` are overridable without rebuilding an image.
- **Redis service** — a password-protected cache with an AOF file, healthy
  before the API starts, so the first cached read cannot race the cache server.
- **RabbitMQ service** — a management-enabled broker with a persistent volume and
  a `rabbitmq-diagnostics ping` health check; the UI on `http://localhost:15672`
  shows the exchange, the onboarding queue and its DLQ.
- **Zipkin service** — the trace store behind the shared trace id described
  above; open `http://localhost:9411` and search by service name to see the
  `gateway → employee-api → rabbit → onboarding-service` span tree.

---

## API Endpoints

All paths below are public through the gateway at `http://localhost:8000`; the
port of the owning service is shown in the last column.

| Method   | Path                  | Owner            | Request Body                                             | Success             | Error                        |
| -------- | --------------------- | ---------------- | -------------------------------------------------------- | ------------------- | ---------------------------- |
| `POST`   | `/api/auth/login`     | employee-api     | JSON (`username`, `password`)                            | `200 OK`            | `400 Bad Request`, `401 Unauthorized` |
| `GET`    | `/api/employees/{id}` | employee-api     | —                                                        | `200 OK`            | `401 Unauthorized`, `404 Not Found` |
| `GET`    | `/api/employees`      | employee-api     | query params (`firstName`, `lastName`, `email`, `jobTitle`, `departmentId`, `minSalary`, `maxSalary`, `page`, `size`, `sort`) | `200 OK` | `400 Bad Request`, `401 Unauthorized` |
| `POST`   | `/api/employees`      | employee-api     | JSON (`firstName`, `lastName`, `email`, `jobTitle`, `salary`, optional `departmentId`); optional `Idempotency-Key` header | `201 Created`  | `400 Bad Request`, `401 Unauthorized`, `403 Forbidden`, `404 Not Found`, `409 Conflict` |
| `POST`   | `/api/employees/{id}/sync` | employee-api | — (pushes the employee to the external HR system)        | `200 OK`            | `401 Unauthorized`, `404 Not Found`, `502 Bad Gateway` |
| `DELETE` | `/api/employees/{id}` | employee-api     | —                                                        | `204 No Content`    | `401 Unauthorized`, `403 Forbidden`, `404 Not Found` |
| `POST`   | `/api/departments`    | employee-api     | JSON (`name`)                                            | `201 Created`       | `400 Bad Request`, `401 Unauthorized`, `403 Forbidden`, `409 Conflict` |
| `GET`    | `/api/departments`    | employee-api     | —                                                        | `200 OK`            | `401 Unauthorized`           |
| `GET`    | `/api/onboarding/tasks` | onboarding-service | —                                                     | `200 OK` (newest first) | `401 Unauthorized`       |

All endpoints under `/api/**` except `/api/auth/**` require a Bearer token. The
onboarding service verifies the **same** token with the shared secret; it reads
`sub` and `role` from the claims without touching the employee database, which is
why the token issued by `employee-api` works unchanged through the gateway.

Interactive documentation is served at `http://localhost:8000/swagger-ui.html`
(proxied to the employee API); the raw OpenAPI 3 document is at `/v3/api-docs`.
Use the **Authorize** button with the token from `POST /api/auth/login` to call
protected endpoints directly from the browser.

### Login — request and response

```json
{
  "username": "admin",
  "password": "admin123"
}
```

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresInSeconds": 3600,
  "username": "admin"
}
```

### Create employee — request

```json
{
  "firstName": "Qi",
  "lastName": "Wang",
  "email": "wangqi@example.com",
  "jobTitle": "Backend Engineer",
  "salary": 5000.00
}
```

### Create employee — success response (`201 Created`)

```json
{
  "id": 1,
  "firstName": "Qi",
  "lastName": "Wang",
  "email": "wangqi@example.com",
  "jobTitle": "Backend Engineer",
  "salary": 5000.00,
  "createdAt": "2024-01-01T10:15:30Z"
}
```

### Validation failure response (`400 Bad Request`)

```json
{
  "timestamp": "2024-01-01T10:15:30Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed for one or more fields.",
  "path": "/api/employees",
  "fieldErrors": {
    "email": "Email must be a well-formed email address",
    "firstName": "First name is required",
    "lastName": "Last name is required",
    "salary": "Salary is required"
  }
}
```

### Not found response (`404 Not Found`)

```json
{
  "timestamp": "2024-01-01T10:15:30Z",
  "status": 404,
  "error": "Not Found",
  "message": "Employee not found with id: 99",
  "path": "/api/employees/99"
}
```

---

## Getting Started

### Prerequisites

- JDK 17 or newer
- Maven 3.8+
- Redis 6+ running locally (the default password is `wangqi`; override with
  `REDIS_PASSWORD`). To skip Redis entirely, run with
  `CACHE_PROVIDER=simple` and the same in-memory cache the tests use.
- RabbitMQ 3.x running locally (username/password default to `wangqi`; override
  with `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD`). To run without a broker, set
  `app.messaging.enabled=false` on both services and the event stays in-process
  in the employee API.
- Docker Desktop (optional, for the six-container workflow; it starts everything
  for you, including Zipkin)
- Zipkin (optional locally; without it traces are still created and logged, they
  are simply not exported)

### Build the reactor

The repository is a four-module Maven reactor. Build and test from the root:

```bash
mvn -DskipTests package   # builds contracts, employee-management-api,
                          # onboarding-service and api-gateway
mvn test                  # runs every module's tests (no external servers needed)
```

### Run the services

Each service is a separate Spring Boot application. In three terminals:

```bash
# 1. Employee API (owner of /api/auth, /api/employees, /api/departments)
mvn -pl employee-management-api spring-boot:run            # http://localhost:8080

# 2. Onboarding service (owner of /api/onboarding, consumer of employee.created)
mvn -pl onboarding-service spring-boot:run                # http://localhost:8081

# 3. Gateway (the only entry point a client should use)
mvn -pl api-gateway spring-boot:run                       # http://localhost:8000
```

Call everything through `http://localhost:8000`, not the service ports — the
gateway is what routes `/api/employees` to `:8080` and `/api/onboarding` to
`:8081`.

When you run without a broker (`app.messaging.enabled=false`), the Rabbit health
indicator is disabled along with messaging, so `/actuator/health` still reports
`UP`. In Compose the indicator is on, which is why the containers only become
healthy once RabbitMQ answers.

Retry and timeout behaviour is configurable in the employee API's
`application.yml` (`app.external-hr.max-attempts`, `initial-backoff`, `timeout`,
`base-url`). `JWT_SECRET` is shared by both services; `EXTERNAL_HR_URL`,
`REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`, `RABBITMQ_HOST`, `RABBITMQ_PORT`,
`RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD`, `ZIPKIN_ENDPOINT`,
`EMPLOYEE_API_URI` and `ONBOARDING_SERVICE_URI` can be overridden through
environment variables.

Or run the packaged containers:

```bash
docker compose up --build
```

### Run the tests

```bash
mvn test                                   # all modules
mvn -pl onboarding-service test            # one module
mvn -pl api-gateway test                   # route definitions only
```

### Try it out

```bash
# 1. Log in as the seeded admin through the gateway -> 200 OK, capture the token
TOKEN=$(curl -s -X POST http://localhost:8000/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | jq -r .accessToken)

# 2. Unauthenticated request -> 401 Unauthorized (JSON), produced downstream
curl http://localhost:8000/api/departments

# 3. Create a department -> 201 Created (routed to employee-api)
curl -X POST http://localhost:8000/api/departments \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"Engineering"}'

# 4. Create an employee -> 201 Created
curl -X POST http://localhost:8000/api/employees \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"firstName":"Qi","lastName":"Wang","email":"wangqi@example.com","jobTitle":"Backend Engineer","salary":5000,"departmentId":1}'

# 5. List departments with employees -> 200 OK, one SQL statement
curl http://localhost:8000/api/departments -H "Authorization: Bearer $TOKEN"

# 6. Invalid payload -> 400 Bad Request with field errors
curl -X POST http://localhost:8000/api/employees \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"firstName":"","lastName":"","email":"not-an-email"}'

# 7. Send the same create request twice with one Idempotency-Key -> 201 both times,
#    the second carries "Idempotent-Replay: true" and no second employee is created
curl -i -X POST http://localhost:8000/api/employees \
  -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-Key: 9b2f0c1e-4a7d-4f2a-9c31-8d5b6e0a1f22" \
  -H "Content-Type: application/json" \
  -d '{"firstName":"Qi","lastName":"Wang","email":"idem@example.com","jobTitle":"Backend Engineer","salary":5000}'

# 8. Push the employee to the external HR system -> 502 after 3 attempts
#    (the default base URL http://localhost:1 is intentionally unreachable)
curl -i -X POST http://localhost:8000/api/employees/1/sync -H "Authorization: Bearer $TOKEN"

# 9. Paginated, filtered search -> 200 OK with a PageResponse envelope
curl -s "http://localhost:8000/api/employees?jobTitle=engineer&minSalary=5000&page=0&size=10&sort=salary,desc" \
  -H "Authorization: Bearer $TOKEN"

# 10. Unsupported sort field -> 400 Bad Request, listing the allowed fields
curl -s "http://localhost:8000/api/employees?sort=password,asc" -H "Authorization: Bearer $TOKEN"

# 11. Read the same employee twice, then inspect the cache
curl -s http://localhost:8000/api/employees/1 -H "Authorization: Bearer $TOKEN"
curl -s http://localhost:8000/api/employees/1 -H "Authorization: Bearer $TOKEN"
#    -> 1 miss, 1 hit; the second call sends no SQL at all
curl -s "http://localhost:8080/actuator/metrics/cache.gets?tag=cache:employees&tag=result:hit" \
  -H "Authorization: Bearer $TOKEN"
#    -> the key lives in Redis as readable JSON with a TTL
redis-cli -a wangqi TTL employee-api:employees::1

# 12. Create an employee and watch the event leave the API
#     employee-api log: "Published employee.created for employee N to exchange 'employee.events'"
#     onboarding-service log: "Onboarding initialised for employee N <...>"
#     Both lines carry the same trace=... as the gateway log for that request.
#     RabbitMQ UI (http://localhost:15672): messages delivered on
#     employee.created.onboarding, no messages resting in the DLQ.

# 13. Read the onboarding tasks created by the other service
curl -s http://localhost:8000/api/onboarding/tasks -H "Authorization: Bearer $TOKEN"
#     -> the same token is accepted by onboarding-service (shared secret, no DB lookup)

# 14. Inspect the trace end to end
open http://localhost:9411    # search by serviceName=api-gateway
```

Log in as `viewer` / `viewer123` and repeat step 3 to see the **403 Forbidden**
response for an authenticated user without the `ADMIN` role.

The in-memory H2 consoles are available per service:
`http://localhost:8080/h2-console` (`jdbc:h2:mem:employeedb`, user `sa`, empty
password) and `http://localhost:8081/h2-console`
(`jdbc:h2:mem:onboardingdb`, same credentials). Service actuators are reachable
directly on their own ports; the gateway routes only `/api/**` and the Swagger
paths, which keeps the operational surface off the public entry point.

---

## Project Structure

```
employee-management/                      # Maven reactor (packaging: pom)
├── pom.xml                               # Aggregator: contracts, api, onboarding, gateway
├── docker-compose.yml                    # Gateway + 2 services + Redis + RabbitMQ + Zipkin
├── .dockerignore
├── contracts/                            # Shared wire contract (the only shared code)
│   └── com/wangqi/contracts/messaging/
│       └── EmployeeCreatedMessage.java   # FQCN travels in the AMQP __TypeId__ header
├── employee-management-api/              # Service 1: system of record for employees
│   └── src/main/java/com/wangqi/employee
│       ├── EmployeeManagementApplication.java
│       ├── config/
│       │   ├── AsyncConfig.java           # Bounded notification pool + MDC decorator
│       │   ├── CacheConfig.java           # Redis cache manager, TTLs, JSON serializer
│       │   ├── CacheProperties.java
│       │   ├── MessagingProperties.java   # enabled / exchange / routing-key only
│       │   ├── RabbitMqConfig.java        # Exchange + JSON converter (topology owner:
│       │   │                              #   the consumer service, not this one)
│       │   ├── DemoUserInitializer.java
│       │   ├── ExternalHrProperties.java
│       │   ├── RestClientConfig.java
│       │   └── OpenApiConfig.java
│       ├── controller/                    # Auth, Employee, Department REST layers
│       ├── integration/                   # ExternalHrClient (bounded retries) + payload
│       ├── observability/                 # CorrelationIdFilter (X-Request-Id -> MDC)
│       ├── event/                         # EmployeeCreatedEvent + @Async after-commit hook
│       ├── messaging/
│       │   ├── EmployeeEventPublisher.java      # Transport-agnostic interface
│       │   ├── AmqpEmployeeEventPublisher.java  # RabbitMQ publisher
│       │   ├── LocalEmployeeEventPublisher.java # In-process fallback
│       │   └── EmployeeEventRelay.java          # AFTER_COMMIT -> broker
│       ├── service/ + service/impl/       # Business logic, idempotency
│       ├── repository/                    # JPA repositories, Specifications
│       ├── entity/                        # Employee, Department, UserAccount, ...
│       ├── security/                      # Stateless JWT, 401/403 handlers
│       ├── dto/ + exception/              # Payloads, domain exceptions, advice
│       └── resources/application.yml      # Port 8080, tracing, cache, rabbit
├── onboarding-service/                    # Service 2: owns the consumer + its data
│   └── src/main/java/com/wangqi/onboarding
│       ├── OnboardingServiceApplication.java
│       ├── config/
│       │   ├── MessagingProperties.java   # enabled / exchange / routing-key / queue / DLQ
│       │   └── RabbitMqConfig.java        # Exchange, queue + DLQ, binding, converter
│       ├── messaging/EmployeeCreatedConsumer.java   # @RabbitListener
│       ├── service/OnboardingInitializer.java       # @Transactional, idempotent
│       ├── controller/OnboardingTaskController.java # GET /api/onboarding/tasks
│       ├── repository/OnboardingTaskRepository.java # existsByEmployeeId
│       ├── entity/OnboardingTask.java               # unique employee_id
│       ├── security/                      # Verify-only JWT (no user table)
│       ├── dto/ + exception/              # OnboardingTaskResponse, ApiError
│       └── resources/application.yml      # Port 8081, its own H2, listener observation
├── api-gateway/                           # Service 3: the public entry point
│   └── src/main/java/com/wangqi/gateway/ApiGatewayApplication.java
│       └── resources/application.yml      # Port 8000, two routes, traceId logging
│
├── employee-management-api/src/test/java/com/wangqi/employee
│   ├── service/EmployeeServiceImplTest.java         # Mockito unit tests
│   ├── service/DepartmentServiceImplTest.java
│   ├── controller/EmployeeControllerTest.java       # MockMvc web tests
│   ├── controller/DepartmentControllerTest.java
│   ├── repository/DepartmentQueryCountTest.java     # 4 statements vs 1
│   ├── security/JwtServiceTest.java                 # Signing, expiry, tampering
│   ├── security/SecurityIntegrationTest.java        # Login, 401 vs 403, roles
│   ├── integration/ExternalHrClientTest.java        # Retry, no retry on 4xx, give up
│   ├── controller/IdempotencyIntegrationTest.java   # Replay, 409, no duplicates
│   ├── controller/EmployeeSearchIntegrationTest.java
│   ├── config/AsyncConfigTest.java                  # Pool bounds + MDC propagation
│   ├── config/CacheConfigTest.java                  # TTLs + JSON round trip
│   ├── config/MessagingConfigTest.java              # Producer exchange + __TypeId__
│   ├── messaging/EmployeeEventRelayTest.java        # Mapping, broker outage
│   ├── observability/CorrelationIdFilterTest.java
│   ├── support/QueryCounter.java                    # Hibernate StatementInspector
│   └── service/CacheBehaviorIntegrationTest.java    # 1 statement then 0; eviction
├── onboarding-service/src/test/java/com/wangqi/onboarding
│   ├── service/OnboardingInitializerTest.java       # Duplicate delivery is a no-op
│   ├── config/RabbitMqTopologyTest.java             # Queue, DLQ args, binding, converter
│   ├── security/JwtServiceTest.java                 # Shared-secret verification
│   └── controller/OnboardingApiTest.java            # 401, and 200 with a shared token
├── contracts/src/test/java/com/wangqi/contracts
│   └── messaging/EmployeeCreatedMessageTest.java    # JSON round trip + FQCN assertion
├── api-gateway/src/test/java/com/wangqi/gateway
│   └── GatewayRoutingTest.java                      # Route URIs and Path predicates
│
└── specs/microservices-evolution/         # spec.md, tasks.md, checklist.md
```

Each module keeps its own `Dockerfile`; all three are built from the repository
root context so Maven can resolve `contracts` and the sibling POMs.

---

## What This Project Demonstrates

- Clear separation of concerns (controller / service / repository / DTO).
- Defensive API design: validation at the boundary, predictable error contracts.
- Correct use of HTTP semantics, including `Location` on `201 Created`.
- Testable code through constructor injection and interface-driven services.
- SQL-level awareness: an N+1 query is identified, fixed with `@EntityGraph`,
  and the fix is locked in by an automated query-count test.
- Security at the boundary: BCrypt storage, stateless JWT validation, explicit
  role rules, and consistent JSON `401`/`403` responses.
- Integration resilience: timeouts on every attempt, bounded exponential
  backoff, no retries for client errors, and `502` instead of a leaked connector
  exception.
- Correctness under retries: idempotency keys stored in the database, request
  fingerprints to detect key reuse, and replay of the original response so a
  duplicate `POST` cannot create a duplicate row.
- Query discipline on the read path too: filtering and sorting are pushed into
  SQL, the page size is capped, the sort surface is whitelisted, and the
  response is a stable envelope rather than a framework-internal type.
- Performance where it matters: the hot read endpoints are served from Redis,
  the database is skipped entirely on a hit (asserted as 0 SQL statements in a
  test), invalidations are mapped to every write that changes a cached view, and
  hit/miss counters are exposed so the cache can be tuned from data.
- Architecture where it pays off: the write path is decoupled through a durable
  topic exchange, the event is published only after the commit, consumers are
  idempotent because delivery is at-least-once, failures park on a dead-letter
  queue instead of looping, and a second service can subscribe without a code
  change here. The remaining gap (an outbox between commit and publish) is
  named rather than hidden.
- A documented API: the OpenAPI document is generated from the running code, so
  it cannot drift from the implementation the way a hand-written spec does.
- Operability: a correlation id that survives the async boundary, a health and
  metrics surface an orchestrator can act on, an explicit thread-pool budget,
  and an image that runs as a non-root user with a real health probe.
- Microservice evolution without a rewrite: service boundaries were drawn along
  an existing event edge, so the consumer moved to its own deployable service and
  the producer's change was limited to an import and a POM dependency. The wire
  contract lives in one small shared module because the class name is part of the
  AMQP header; topology ownership moved to the consumer, which is why the
  producer no longer declares a queue it does not read.
- Gateway discipline: one public entry point, routes declared in configuration
  and asserted in a test, and authentication left downstream where it can
  actually be enforced.
- Distributed tracing across transport protocols: a single trace id is carried
  over HTTP (`traceparent`) and over AMQP (observation-enabled template and
  listener), so a gateway request and the consumer that reacts to it minutes
  later can still be correlated in logs and in Zipkin.
- A pragmatic split: one database per service, an event-carried snapshot instead
  of a synchronous lookup, and a shared-secret JWT that avoids a second user
  table. The trade-offs (shared secret distribution, no outbox, in-memory H2)
  are written down rather than glossed over.
