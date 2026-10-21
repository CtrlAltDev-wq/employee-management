# Simple Employee Management API

A minimal, production-minded REST API built with **Java 17** and **Spring Boot 3**.
It is intentionally small in scope but demonstrates the engineering practices
that matter in a real backend service: clean layering, **global exception
handling**, **strict request validation**, correct **HTTP status codes**,
**stateless JWT security**, **automated tests**, and a measured fix for the
classic **JPA N+1 query problem**.

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

> **What this project demonstrates:** I can diagnose an N+1 query problem at the
> SQL level and prove the fix with query-count assertions, and I can add
> stateless JWT authentication with correct `401`/`403` semantics to an existing
> API without breaking its error contract.

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

## API Endpoints

| Method   | Path                  | Request Body                                             | Success             | Error                        |
| -------- | --------------------- | -------------------------------------------------------- | ------------------- | ---------------------------- |
| `POST`   | `/api/auth/login`     | JSON (`username`, `password`)                            | `200 OK`            | `400 Bad Request`, `401 Unauthorized` |
| `GET`    | `/api/employees/{id}` | —                                                        | `200 OK`            | `401 Unauthorized`, `404 Not Found` |
| `POST`   | `/api/employees`      | JSON (`firstName`, `lastName`, `email`, `jobTitle`, `salary`, optional `departmentId`) | `201 Created`  | `400 Bad Request`, `401 Unauthorized`, `403 Forbidden`, `404 Not Found`, `409 Conflict` |
| `DELETE` | `/api/employees/{id}` | —                                                        | `204 No Content`    | `401 Unauthorized`, `403 Forbidden`, `404 Not Found` |
| `POST`   | `/api/departments`    | JSON (`name`)                                            | `201 Created`       | `400 Bad Request`, `401 Unauthorized`, `403 Forbidden`, `409 Conflict` |
| `GET`    | `/api/departments`    | —                                                        | `200 OK`            | `401 Unauthorized`           |

All endpoints under `/api/**` except `/api/auth/**` require a Bearer token.

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
├── config/DemoUserInitializer.java      # Seeds demo admin/viewer accounts
├── controller/
│   ├── AuthController.java              # Login endpoint
│   ├── EmployeeController.java          # Employee REST layer
│   └── DepartmentController.java        # Department REST layer
├── service/                             # Service contracts
│   └── impl/                            # Business logic
├── repository/
│   ├── EmployeeRepository.java          # Spring Data JPA repository
│   ├── DepartmentRepository.java        # Entity-graph fetch for N+1 fix
│   └── UserAccountRepository.java       # Credentials lookup
├── entity/
│   ├── Employee.java                    # JPA entity (LAZY many-to-one)
│   ├── Department.java                  # JPA entity (LAZY one-to-many)
│   ├── UserAccount.java                 # BCrypt credentials + role
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
