# Simple Employee Management API

A minimal, production-minded REST API built with **Java 17** and **Spring Boot 3**.
It is intentionally small in scope but demonstrates the engineering practices
that matter in a real backend service: clean layering, **global exception
handling**, **strict request validation**, correct **HTTP status codes**,
**stateless JWT security**, **resilient third-party integration with retries and
idempotency**, **paginated dynamic search**, **a Redis read cache with explicit
invalidation**, **event-driven decoupling through RabbitMQ**, **self-documenting
OpenAPI**, **correlation-id tracing and metrics**, **bounded async execution**, 
**automated tests**, a **container image**, and a measured fix for the classic
**JPA N+1 query problem**.

> Author: **wangqi**

---

## Tech Stack

| Layer            | Technology                                          |
| ---------------- | --------------------------------------------------- |
| Language         | Java 17                                             |
| Framework        | Spring Boot 3.3.5, Spring Web (Spring MVC)          |
| Persistence      | Spring Data JPA / Hibernate 6.5                     |
| Database         | H2 (in-memory)                                      |
| Cache            | Redis 7 + Spring Cache (`@Cacheable`, `@CacheEvict`)|
| Messaging        | RabbitMQ 3.13 + Spring AMQP, publisher confirms, dead-letter queue |
| Validation       | Jakarta Bean Validation (Hibernate Validator)       |
| Security         | Spring Security 6, JWT (JJWT 0.12), BCrypt          |
| Integration      | Spring `RestClient` over JDK `HttpClient`, bounded retries with exponential backoff |
| API Docs         | springdoc-openapi 2.6 (Swagger UI)                  |
| Observability    | Spring Boot Actuator, Micrometer, Prometheus        |
| Async            | Spring `@Async` with a bounded `ThreadPoolTaskExecutor` |
| Packaging        | Docker (multi-stage build), Docker Compose          |
| Boilerplate      | Lombok                                              |
| Testing          | JUnit 5, Mockito, MockMvc, AssertJ                  |
| Build            | Maven 3.9                                           |

---

## Key Features

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
  by a consumer instead of the request thread. The publisher does not know who
  listens, the consumer is **idempotent** (duplicate deliveries are proven not
  to create a second task), and a rejected message lands on a **dead-letter
  queue** rather than being redelivered forever.
- **Bounded Async Execution** — new-employee notifications run on a dedicated
  pool with explicit core/max/queue limits, caller-runs rejection, and MDC
  propagation, so background work can neither starve requests nor lose its
  trace context.
- **Containerised** — a multi-stage `Dockerfile` builds with the full JDK and
  ships a non-root JRE image with a health check; `docker compose up` starts the
  API, Redis and RabbitMQ together.

> **What this project demonstrates:** I can find a performance problem at the
> SQL level and prove the fix with query-count assertions, add stateless JWT
> authentication with correct `401`/`403` semantics, make a third-party
> integration safe to retry by combining bounded exponential backoff with
> database-backed idempotency, push filtering into SQL behind a whitelisted sort
> surface, take the database off the hot read path with a Redis cache whose
> invalidation is asserted in tests, decouple the write path with an
> event stream that other services can subscribe to, and still operate the
> result — correlation ids, metrics, a bounded async pool, a health-checked
> container — without breaking the API's error contract.

| Claim | Evidence in this repository |
| ----- | --------------------------- |
| The N+1 query is really gone | `DepartmentQueryCountTest` — **4** statements before the fix, **1** after |
| The cache really removes database work | `CacheBehaviorIntegrationTest` — **1** statement on the first read, **0** on the second; eviction asserted on create/delete |
| Retries cannot create duplicates | `IdempotencyIntegrationTest` — replay with `Idempotent-Replay: true`, `409` on key reuse |
| Security distinguishes unauthenticated from unauthorised | `SecurityIntegrationTest` — `401` without a token, `403` for the wrong role |
| Every failure is a structured contract | `EmployeeControllerTest` — field-level `400` bodies; correlation id asserted in `CorrelationIdFilterTest` |
| Async work cannot starve or lose the trace | `AsyncConfigTest` — pool bounds plus MDC propagation |
| A redelivered message cannot do the work twice | `OnboardingInitializerTest` — second delivery returns `false`, one row |
| Broker topology is what the README says | `MessagingConfigTest` — durable topic exchange, DLQ wiring, JSON round trip |
| All of the above is green | **56 tests**, `mvn test` |

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
Onboarding is not started by the request thread — a consumer owns it, and any
other service can subscribe to the same event without the API changing.

```
POST /api/employees
        │  transaction commits
        ▼
@TransactionalEventListener(AFTER_COMMIT)      EmployeeEventRelay
        │
        ▼
RabbitTemplate.convertAndSend("employee.events", "employee.created", json)
        │
        ▼
topic exchange ──► employee.created.onboarding ──► EmployeeCreatedConsumer
   (durable)              (durable)                      │
                                                          ▼
                                              OnboardingInitializer (idempotent)
                                                          │
                                      failure ──► employee.created.onboarding.dlq
```

| Element | Why it is shaped this way |
| ------- | ------------------------- |
| `AFTER_COMMIT` | A message must never announce an employee whose transaction rolled back. |
| Topic exchange | Routing keys let new consumers (`employee.created.mail`, `.provisioning`) bind later without touching the publisher. |
| Durable queue | The event survives a broker restart; an in-memory queue would silently drop it. |
| Dead-letter queue | A message the consumer rejects is parked for inspection instead of being redelivered forever (`default-requeue-rejected: false`). |
| Publisher confirms | The broker acknowledges receipt; a nack is logged rather than assumed successful. |
| Idempotent consumer | Brokers deliver **at least once**. `OnboardingInitializer` checks `employee_id` before writing, and a unique constraint enforces it at the database level — the second delivery is a no-op, not a second onboarding task. |
| Failure isolation | If publishing fails after the commit, the relay logs and returns: the employee exists, so a `500` would describe something the caller cannot fix. |

**Deliberate trade-off (the honest limitation):** publishing happens after the
commit but outside it, so a crash in that window loses the event. The production
fix is a **transactional outbox** (write the event in the same transaction, relay
it afterwards) or CDC. It is not implemented here because it adds a table and a
poller without teaching anything new about the broker itself.

**Verification without a broker** — the test suite runs with
`app.messaging.enabled=false`, which swaps in `LocalEmployeeEventPublisher` and
skips the listener container:

| Test | What it proves |
| ---- | -------------- |
| `MessagingConfigTest` | durable topic exchange, routing key, DLQ arguments, JSON payload round trip |
| `EmployeeEventRelayTest` | domain event is mapped to the flat message; a broker outage does not fail the request |
| `OnboardingInitializerTest` | first delivery creates a task, redelivery is ignored, one row in the table |

To watch it end to end, run `docker compose up` and inspect the queue counters
in the management UI at `http://localhost:15672`; the application log shows
`Published employee.created for employee 1` and then
`Onboarding initialised for employee 1`.

Configuration:

| Property | Default | Environment variable |
| -------- | ------- | -------------------- |
| `spring.rabbitmq.host` | `127.0.0.1` | `RABBITMQ_HOST` |
| `spring.rabbitmq.port` | `5672` | `RABBITMQ_PORT` |
| `spring.rabbitmq.username` / `password` | `wangqi` / `wangqi` | `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` |
| `app.messaging.enabled` | `true` | `MESSAGING_ENABLED` |
| `app.messaging.exchange` / `queue` | `employee.events` / `employee.created.onboarding` | — |

---

## Security: Stateless JWT Authentication

`POST /api/auth/login` exchanges credentials for a signed HS256 token. Every
subsequent request sends it as `Authorization: Bearer <token>`; no session or
cookie is involved, so the API scales horizontally without shared state.

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
  development default only.

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

## Observability: Correlation IDs, Health, and Metrics

Every request carries a correlation id. If the caller supplies a safe
`X-Request-Id` (1–64 characters of `A-Z a-z 0-9 . _ -`) it is reused, so an API
gateway's logs line up with ours; otherwise a UUID is generated. The id is:

- echoed back in the `X-Request-Id` response header,
- placed in the SLF4J MDC as `requestId`, which the log pattern renders,
- included as `requestId` in every `ErrorResponse`,
- copied into async worker threads (see the next section),
- removed in a `finally` block so a pooled thread never leaks another request's id.

```
2026-10-23 10:14:02.118 INFO  [7f3a9c1e-...] c.w.e.event.EmployeeCreatedListener - Welcome notification sent ...
```

| Endpoint                  | Purpose                                        |
| ------------------------- | ---------------------------------------------- |
| `/actuator/health`        | Liveness/readiness with component details      |
| `/actuator/metrics`       | Micrometer metric names and values             |
| `/actuator/metrics/http.server.requests` | Latency and counts per URI, method, status |
| `/actuator/prometheus`    | Prometheus scrape format                      |
| `/actuator/info`          | Build/app metadata                            |

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

## Docker: One Command

```bash
docker compose up --build
# API:      http://localhost:8080
# Swagger:  http://localhost:8080/swagger-ui.html
# Health:   http://localhost:8080/actuator/health
# Redis:    localhost:6379 (password from REDIS_PASSWORD, default `wangqi`)
# RabbitMQ: amqp://localhost:5672, UI at http://localhost:15672 (wangqi/wangqi)
```

- **Multi-stage build** — Maven + full JDK in the build stage, a JRE-only Alpine
  runtime stage. Dependencies are cached in their own layer, so a source change
  does not re-download them.
- **Non-root** — the container runs as an unprivileged `app` user.
- **Health check** — the image and Compose both probe `/actuator/health`, so
  orchestration sees "starting" versus "healthy" instead of guessing.
- **Configuration by environment** — `JWT_SECRET`, `EXTERNAL_HR_URL`,
  `REDIS_HOST`, `REDIS_PASSWORD`, `RABBITMQ_HOST` and `RABBITMQ_PASSWORD` are
  overridable without rebuilding the image.
- **Redis service** — Compose starts Redis with a password and an AOF file, and
  the API waits for its health check before starting, so the first cached read
  cannot race the cache server.
- **RabbitMQ service** — a management-enabled broker with a persistent volume and
  a `rabbitmq-diagnostics ping` health check; the management UI is on
  `http://localhost:15672` so the exchange, queue and DLQ can be inspected while
  the API runs.

---

## API Endpoints

| Method   | Path                  | Request Body                                             | Success             | Error                        |
| -------- | --------------------- | -------------------------------------------------------- | ------------------- | ---------------------------- |
| `POST`   | `/api/auth/login`     | JSON (`username`, `password`)                            | `200 OK`            | `400 Bad Request`, `401 Unauthorized` |
| `GET`    | `/api/employees/{id}` | —                                                        | `200 OK`            | `401 Unauthorized`, `404 Not Found` |
| `GET`    | `/api/employees`      | query params (`firstName`, `lastName`, `email`, `jobTitle`, `departmentId`, `minSalary`, `maxSalary`, `page`, `size`, `sort`) | `200 OK` | `400 Bad Request`, `401 Unauthorized` |
| `POST`   | `/api/employees`      | JSON (`firstName`, `lastName`, `email`, `jobTitle`, `salary`, optional `departmentId`); optional `Idempotency-Key` header | `201 Created`  | `400 Bad Request`, `401 Unauthorized`, `403 Forbidden`, `404 Not Found`, `409 Conflict` |
| `POST`   | `/api/employees/{id}/sync` | — (pushes the employee to the external HR system)   | `200 OK`            | `401 Unauthorized`, `404 Not Found`, `502 Bad Gateway` |
| `DELETE` | `/api/employees/{id}` | —                                                        | `204 No Content`    | `401 Unauthorized`, `403 Forbidden`, `404 Not Found` |
| `POST`   | `/api/departments`    | JSON (`name`)                                            | `201 Created`       | `400 Bad Request`, `401 Unauthorized`, `403 Forbidden`, `409 Conflict` |
| `GET`    | `/api/departments`    | —                                                        | `200 OK`            | `401 Unauthorized`           |

All endpoints under `/api/**` except `/api/auth/**` require a Bearer token.

Interactive documentation is served at `http://localhost:8080/swagger-ui.html`;
the raw OpenAPI 3 document is at `/v3/api-docs`. Use the **Authorize** button
with the token from `POST /api/auth/login` to call protected endpoints directly
from the browser.

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
  `MESSAGING_ENABLED=false` and the event stays in-process.
- Docker (optional, for the container workflow; it starts both servers for you)

### Run the application

```bash
mvn spring-boot:run
```

The API starts on `http://localhost:8080`.

Retry and timeout behaviour is configurable in `application.yml`
(`app.external-hr.max-attempts`, `initial-backoff`, `timeout`, `base-url`).
`JWT_SECRET`, `EXTERNAL_HR_URL`, `REDIS_HOST`, `REDIS_PORT`,
`REDIS_PASSWORD`, `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USERNAME` and
`RABBITMQ_PASSWORD` can be overridden through environment variables.

Or run the packaged image:

```bash
docker compose up --build
```

### Run the tests

```bash
mvn test
```

### Try it out

```bash
# 1. Log in as the seeded admin -> 200 OK, capture the token
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | jq -r .accessToken)

# 2. Unauthenticated request -> 401 Unauthorized (JSON)
curl http://localhost:8080/api/departments

# 3. Create a department -> 201 Created
curl -X POST http://localhost:8080/api/departments \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"Engineering"}'

# 4. Create an employee -> 201 Created
curl -X POST http://localhost:8080/api/employees \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"firstName":"Qi","lastName":"Wang","email":"wangqi@example.com","jobTitle":"Backend Engineer","salary":5000,"departmentId":1}'

# 5. List departments with employees -> 200 OK, one SQL statement
curl http://localhost:8080/api/departments -H "Authorization: Bearer $TOKEN"

# 6. Invalid payload -> 400 Bad Request with field errors
curl -X POST http://localhost:8080/api/employees \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"firstName":"","lastName":"","email":"not-an-email"}'

# 7. Send the same create request twice with one Idempotency-Key -> 201 both times,
#    the second carries "Idempotent-Replay: true" and no second employee is created
curl -i -X POST http://localhost:8080/api/employees \
  -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-Key: 9b2f0c1e-4a7d-4f2a-9c31-8d5b6e0a1f22" \
  -H "Content-Type: application/json" \
  -d '{"firstName":"Qi","lastName":"Wang","email":"idem@example.com","jobTitle":"Backend Engineer","salary":5000}'

# 8. Push the employee to the external HR system -> 502 after 3 attempts
#    (the default base URL http://localhost:1 is intentionally unreachable)
curl -i -X POST http://localhost:8080/api/employees/1/sync -H "Authorization: Bearer $TOKEN"

# 9. Paginated, filtered search -> 200 OK with a PageResponse envelope
curl -s "http://localhost:8080/api/employees?jobTitle=engineer&minSalary=5000&page=0&size=10&sort=salary,desc" \
  -H "Authorization: Bearer $TOKEN"

# 10. Unsupported sort field -> 400 Bad Request, listing the allowed fields
curl -s "http://localhost:8080/api/employees?sort=password,asc" -H "Authorization: Bearer $TOKEN"

# 11. Read the same employee twice, then inspect the cache
curl -s http://localhost:8080/api/employees/1 -H "Authorization: Bearer $TOKEN"
curl -s http://localhost:8080/api/employees/1 -H "Authorization: Bearer $TOKEN"
#    -> 1 miss, 1 hit; the second call sends no SQL at all
curl -s "http://localhost:8080/actuator/metrics/cache.gets?tag=cache:employees&tag=result:hit" \
  -H "Authorization: Bearer $TOKEN"
#    -> the key lives in Redis as readable JSON with a TTL
redis-cli -a wangqi TTL employee-api:employees::1

# 12. Create an employee and watch the event leave the API
#     -> log: "Published employee.created for employee N to exchange 'employee.events'"
#     -> log: "Onboarding initialised for employee N <...>"
#     -> RabbitMQ UI (http://localhost:15672): messages delivered on
#        employee.created.onboarding, no messages resting in the DLQ
```

Log in as `viewer` / `viewer123` and repeat step 3 to see the **403 Forbidden**
response for an authenticated user without the `ADMIN` role.

The in-memory H2 console is available at `http://localhost:8080/h2-console`
(JDBC URL `jdbc:h2:mem:employeedb`, user `sa`, empty password).

---

## Project Structure

```
src/main/java/com/wangqi/employee
├── EmployeeManagementApplication.java   # Spring Boot entry point
├── config/
│   ├── AsyncConfig.java                 # Bounded notification pool + MDC decorator
│   ├── CacheConfig.java                 # Redis cache manager, TTLs, JSON serializer
│   ├── CacheProperties.java             # app.cache tuning knobs
│   ├── MessagingProperties.java         # app.messaging topology + switch
│   ├── RabbitMqConfig.java              # Exchange, queue, DLQ, JSON converter
│   ├── DemoUserInitializer.java         # Seeds demo admin/viewer accounts
│   ├── ExternalHrProperties.java        # app.external-hr tuning knobs
│   ├── RestClientConfig.java            # RestClient with connect/read timeouts
│   └── OpenApiConfig.java               # OpenAPI metadata + bearer scheme
├── controller/
│   ├── AuthController.java              # Login endpoint
│   ├── EmployeeController.java          # Employee REST layer: search, create, sync
│   └── DepartmentController.java        # Department REST layer
├── integration/
│   ├── ExternalHrClient.java            # Bounded retries with backoff
│   └── ExternalEmployeePayload.java     # Outbound DTO
├── observability/
│   └── CorrelationIdFilter.java         # X-Request-Id -> MDC -> response header
├── event/
│   ├── EmployeeCreatedEvent.java        # Domain event
│   └── EmployeeCreatedListener.java     # @Async after-commit notification
├── messaging/
│   ├── EmployeeCreatedMessage.java      # Wire contract (flat DTO)
│   ├── EmployeeEventPublisher.java      # Transport-agnostic publisher interface
│   ├── AmqpEmployeeEventPublisher.java  # RabbitMQ publisher
│   ├── LocalEmployeeEventPublisher.java # In-process fallback (messaging disabled)
│   ├── EmployeeEventRelay.java          # AFTER_COMMIT -> broker
│   ├── EmployeeCreatedConsumer.java     # @RabbitListener
│   └── OnboardingInitializer.java       # Idempotent onboarding start
├── service/                             # Service contracts
│   ├── IdempotencyService.java          # Key lookup, SHA-256 fingerprint, replay
│   └── impl/                            # Business logic
├── repository/
│   ├── EmployeeRepository.java          # Spring Data JPA repository
│   ├── EmployeeSpecifications.java      # Dynamic search predicates
│   ├── DepartmentRepository.java        # Entity-graph fetch for N+1 fix
│   ├── UserAccountRepository.java       # Credentials lookup
│   ├── OnboardingTaskRepository.java    # Duplicate-delivery check
│   └── IdempotencyRecordRepository.java # Idempotency-Key storage
├── entity/
│   ├── Employee.java                    # JPA entity (LAZY many-to-one)
│   ├── Department.java                  # JPA entity (LAZY one-to-many)
│   ├── UserAccount.java                 # BCrypt credentials + role
│   ├── OnboardingTask.java              # Unique per employee (idempotent consumer)
│   ├── IdempotencyRecord.java           # Key (PK) + request hash + response
│   └── Role.java                        # ROLE_USER / ROLE_ADMIN
├── security/
│   ├── SecurityConfig.java              # Stateless filter chain + rules
│   ├── JwtService.java                  # Sign / parse / validate tokens
│   ├── JwtAuthenticationFilter.java     # Bearer token filter
│   ├── JwtProperties.java               # app.jwt configuration
│   ├── AppUserDetailsService.java       # DB-backed UserDetailsService
│   ├── RestAuthenticationEntryPoint.java # JSON 401
│   └── RestAccessDeniedHandler.java     # JSON 403
├── dto/                                 # Request / response payloads
└── exception/                           # Domain exceptions + GlobalExceptionHandler

src/test/java/com/wangqi/employee
├── service/EmployeeServiceImplTest.java        # Mockito unit tests
├── service/DepartmentServiceImplTest.java      # Mockito unit tests
├── controller/EmployeeControllerTest.java      # MockMvc web tests
├── controller/DepartmentControllerTest.java    # MockMvc web tests
├── repository/DepartmentQueryCountTest.java    # Asserts 4 statements vs 1
├── security/JwtServiceTest.java                # Signing, expiry, tampering
├── security/SecurityIntegrationTest.java       # Login, 401 vs 403, roles
├── integration/ExternalHrClientTest.java       # Retry, no retry on 4xx, give up
├── controller/IdempotencyIntegrationTest.java  # Replay, 409, no duplicates
├── controller/EmployeeSearchIntegrationTest.java # Paging, filters, sort, 400s
├── config/AsyncConfigTest.java                 # Pool bounds + MDC propagation
├── config/CacheConfigTest.java                 # TTLs + JSON round-trip of cached DTOs
├── config/MessagingConfigTest.java             # Topology, DLQ, JSON payload
├── messaging/EmployeeEventRelayTest.java       # Event mapping, broker outage
├── messaging/OnboardingInitializerTest.java    # Duplicate delivery is a no-op
├── observability/CorrelationIdFilterTest.java  # Generate, reuse, sanitise
├── support/QueryCounter.java                   # Hibernate StatementInspector
└── service/CacheBehaviorIntegrationTest.java   # 1 statement then 0; eviction on writes

src/test/resources/application.properties       # cache/messaging switches (no servers needed)

Dockerfile               # multi-stage build (JDK build -> JRE runtime)
docker-compose.yml       # API + Redis + RabbitMQ, one-command local run
.dockerignore
```

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
