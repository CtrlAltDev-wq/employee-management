# Simple Employee Management API

A minimal, production-minded REST API built with **Java 17** and **Spring Boot 3**.
It is intentionally small in scope but demonstrates the engineering practices
that matter in a real backend service: clean layering, **global exception
handling**, **strict request validation**, correct **HTTP status codes**,
**stateless JWT security**, **resilient third-party integration with retries and
idempotency**, **paginated dynamic search**, **self-documenting OpenAPI**, 
**automated tests**, and a measured fix for the classic **JPA N+1 query
problem**.

> Author: **wangqi**

---

## Tech Stack

| Layer            | Technology                                   |
| ---------------- | -------------------------------------------- |
| Language         | Java 17                                      |
| Framework        | Spring Boot 3, Spring Web (Spring MVC)       |
| Persistence      | Spring Data JPA / Hibernate                  |
| Database         | H2 (in-memory)                               |
| Validation       | Jakarta Bean Validation (Hibernate Validator)|
| Security         | Spring Security, JWT (JJWT 0.12), BCrypt     |
| Integration      | Spring `RestClient` over JDK `HttpClient` with connect/read timeouts |
| API Docs         | springdoc-openapi (Swagger UI)               |
| Boilerplate      | Lombok                                       |
| Testing          | JUnit 5, Mockito, MockMvc, AssertJ           |
| Build            | Maven                                        |

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
- **Self-Documenting API** — springdoc generates an OpenAPI 3 description, and
  Swagger UI at `/swagger-ui.html` lets a client authenticate with the JWT and
  exercise every endpoint without writing a single line of code.

> **What this project demonstrates:** I can diagnose an N+1 query problem at the
> SQL level and prove the fix with query-count assertions, add stateless JWT
> authentication with correct `401`/`403` semantics, make a third-party
> integration safe to retry by combining bounded exponential backoff with
> database-backed idempotency, and build a paginated dynamic search that pushes
> filtering into SQL while keeping untrusted input out of the query — without
> breaking the API's error contract.

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

### Run the application

```bash
mvn spring-boot:run
```

The API starts on `http://localhost:8080`.

Retry and timeout behaviour is configurable in `application.yml`
(`app.external-hr.max-attempts`, `initial-backoff`, `timeout`, `base-url`).
`JWT_SECRET` and `EXTERNAL_HR_URL` can be overridden through environment
variables.

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
├── service/                             # Service contracts
│   ├── IdempotencyService.java          # Key lookup, SHA-256 fingerprint, replay
│   └── impl/                            # Business logic
├── repository/
│   ├── EmployeeRepository.java          # Spring Data JPA repository
│   ├── EmployeeSpecifications.java      # Dynamic search predicates
│   ├── DepartmentRepository.java        # Entity-graph fetch for N+1 fix
│   ├── UserAccountRepository.java       # Credentials lookup
│   └── IdempotencyRecordRepository.java # Idempotency-Key storage
├── entity/
│   ├── Employee.java                    # JPA entity (LAZY many-to-one)
│   ├── Department.java                  # JPA entity (LAZY one-to-many)
│   ├── UserAccount.java                 # BCrypt credentials + role
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
└── support/QueryCounter.java                   # Hibernate StatementInspector
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
- A documented API: the OpenAPI document is generated from the running code, so
  it cannot drift from the implementation the way a hand-written spec does.
