# Simple Employee Management API

A minimal, production-minded REST API built with **Java 17** and **Spring Boot 3**.
It is intentionally small in scope but demonstrates the engineering practices
that matter in a real backend service: clean layering, **global exception
handling**, **strict request validation**, correct **HTTP status codes**,
**automated tests**, and a measured fix for the classic **JPA N+1 query problem**.

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

> **What this module demonstrates:** I can diagnose an N+1 query problem at the
> SQL level, fix it with an `@EntityGraph` fetch, and prove the improvement with
> query-count assertions in JUnit.

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

## API Endpoints

| Method   | Path                  | Request Body                                             | Success             | Error                        |
| -------- | --------------------- | -------------------------------------------------------- | ------------------- | ---------------------------- |
| `GET`    | `/api/employees/{id}` | —                                                        | `200 OK`            | `404 Not Found`              |
| `POST`   | `/api/employees`      | JSON (`firstName`, `lastName`, `email`, `jobTitle`, `salary`, optional `departmentId`) | `201 Created`  | `400 Bad Request`, `404 Not Found`, `409 Conflict` |
| `DELETE` | `/api/employees/{id}` | —                                                        | `204 No Content`    | `404 Not Found`              |
| `POST`   | `/api/departments`    | JSON (`name`)                                            | `201 Created`       | `400 Bad Request`, `409 Conflict` |
| `GET`    | `/api/departments`    | —                                                        | `200 OK`            | —                            |

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
# Create a department -> 201 Created
curl -X POST http://localhost:8080/api/departments \
  -H "Content-Type: application/json" \
  -d '{"name":"Engineering"}'

# Create an employee -> 201 Created
curl -X POST http://localhost:8080/api/employees \
  -H "Content-Type: application/json" \
  -d '{"firstName":"Qi","lastName":"Wang","email":"wangqi@example.com","jobTitle":"Backend Engineer","salary":5000,"departmentId":1}'

# List departments with employees -> 200 OK, one SQL statement
curl http://localhost:8080/api/departments

# Read an employee back -> 200 OK
curl http://localhost:8080/api/employees/1

# Invalid id -> 404 Not Found
curl http://localhost:8080/api/employees/999

# Invalid payload -> 400 Bad Request with field errors
curl -X POST http://localhost:8080/api/employees \
  -H "Content-Type: application/json" \
  -d '{"firstName":"","lastName":"","email":"not-an-email"}'
```

The in-memory H2 console is available at `http://localhost:8080/h2-console`
(JDBC URL `jdbc:h2:mem:employeedb`, user `sa`, empty password).

---

## Project Structure

```
src/main/java/com/wangqi/employee
├── EmployeeManagementApplication.java   # Spring Boot entry point
├── controller/
│   ├── EmployeeController.java          # Employee REST layer
│   └── DepartmentController.java        # Department REST layer
├── service/                             # Service contracts
│   └── impl/                            # Business logic
├── repository/
│   ├── EmployeeRepository.java          # Spring Data JPA repository
│   └── DepartmentRepository.java        # Entity-graph fetch for N+1 fix
├── entity/
│   ├── Employee.java                    # JPA entity (LAZY many-to-one)
│   └── Department.java                  # JPA entity (LAZY one-to-many)
├── dto/                                 # Request / response payloads
└── exception/                           # Domain exceptions + GlobalExceptionHandler

src/test/java/com/wangqi/employee
├── service/EmployeeServiceImplTest.java        # Mockito unit tests
├── service/DepartmentServiceImplTest.java      # Mockito unit tests
├── controller/EmployeeControllerTest.java      # MockMvc web tests
├── controller/DepartmentControllerTest.java    # MockMvc web tests
├── repository/DepartmentQueryCountTest.java    # Asserts 4 statements vs 1
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
