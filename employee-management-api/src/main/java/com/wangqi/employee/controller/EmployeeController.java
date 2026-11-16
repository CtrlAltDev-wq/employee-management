package com.wangqi.employee.controller;

import com.wangqi.employee.common.Result;
import com.wangqi.employee.dto.EmployeeCreationResult;
import com.wangqi.employee.dto.EmployeeRequest;
import com.wangqi.employee.dto.EmployeeResponse;
import com.wangqi.employee.dto.EmployeeSearchCriteria;
import com.wangqi.employee.dto.EmployeeSyncResponse;
import com.wangqi.employee.dto.PageResponse;
import com.wangqi.employee.exception.InvalidSortParameterException;
import com.wangqi.employee.integration.ExternalEmployeePayload;
import com.wangqi.employee.integration.ExternalHrClient;
import com.wangqi.employee.service.EmployeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * REST endpoints for employee management.
 *
 * @author wangqi
 */
@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
@Tag(name = "Employees", description = "Create, search, read and delete employees")
public class EmployeeController {

    static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    static final String IDEMPOTENT_REPLAY_HEADER = "Idempotent-Replay";

    /**
     * Sortable properties. Whitelisting keeps client input out of the JPA
     * property path, so an unknown or malicious {@code sort} value is a clean
     * 400 instead of a persistence-layer error.
     */
    private static final Set<String> SORTABLE_FIELDS = Set.of(
            "id", "firstName", "lastName", "email", "jobTitle", "salary", "createdAt");

    private final EmployeeService employeeService;
    private final ExternalHrClient externalHrClient;

    /**
     * {@code GET /api/employees} - paginated, filtered search.
     *
     * <p>Text filters are case-insensitive contains matches, salary bounds are
     * inclusive, and the page size is capped so a client cannot ask the server
     * to materialise an unbounded result set.
     */
    @Operation(
            summary = "Search employees",
            description = """
                    Paginated, filtered search. Text filters are case-insensitive
                    "contains" matches, salary bounds are inclusive, and the page size
                    is capped at 100. Sort fields are whitelisted; anything else is a 400.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "A page of employees"),
            @ApiResponse(responseCode = "400", description = "Invalid page, size or sort parameter",
                    content = @Content(examples = @ExampleObject(value = """
                            {"code":400,"message":"Unsupported sort field 'password'. Allowed fields: createdAt, email, firstName, id, jobTitle, lastName, salary","path":"/api/employees","timestamp":"2024-01-01T10:15:30Z"}
                            """))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token")
    })
    @GetMapping
    public ResponseEntity<Result<PageResponse<EmployeeResponse>>> searchEmployees(
            @Valid @ModelAttribute EmployeeSearchCriteria criteria,
            @Parameter(description = "Zero-based page index", example = "0")
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "Page index must be zero or greater") int page,
            @Parameter(description = "Page size, 1 to 100", example = "20")
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Page size must be at least 1")
            @Max(value = 100, message = "Page size must not exceed 100") int size,
            HttpServletRequest request) {

        // Raw values are read from the request, so "salary,desc" stays a single
        // token instead of being split by Spring's comma-aware collection converter.
        PageRequest pageRequest = PageRequest.of(page, size, parseSort(request.getParameterValues("sort")));
        return ResponseEntity.ok(Result.ok(employeeService.searchEmployees(criteria, pageRequest)));
    }

    /**
     * {@code GET /api/employees/{id}} - returns a single employee.
     * Responds with 200 OK, or 404 Not Found when the id is unknown.
     */
    @Operation(summary = "Get one employee", description = "Returns a single employee, served from the Redis read cache when warm.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The employee"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            @ApiResponse(responseCode = "404", description = "No employee with this id")
    })
    @GetMapping("/{id}")
    public ResponseEntity<Result<EmployeeResponse>> getEmployeeById(
            @Parameter(description = "Employee id", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(Result.ok(employeeService.getEmployeeById(id)));
    }

    /**
     * {@code POST /api/employees} - creates a new employee.
     * Responds with 201 Created and a {@code Location} header pointing at the created resource.
     *
     * <p>When the client sends an {@code Idempotency-Key}, a retry with the same
     * key and the same payload returns the original employee (same id) instead
     * of creating a duplicate, flagged with {@code Idempotent-Replay: true}.
     */
    @Operation(
            summary = "Create an employee",
            description = """
                    Requires the ADMIN role. Send an optional Idempotency-Key header to make the
                    call safe to retry: the same key with the same payload replays the original
                    result, the same key with a different payload returns 409.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created; Location header points at the new employee"),
            @ApiResponse(responseCode = "400", description = "Bean validation failed; see fieldErrors"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            @ApiResponse(responseCode = "403", description = "Authenticated but not an ADMIN"),
            @ApiResponse(responseCode = "404", description = "departmentId does not exist"),
            @ApiResponse(responseCode = "409", description = "Email already used, or Idempotency-Key reused with a different payload")
    })
    @PostMapping
    public ResponseEntity<Result<EmployeeResponse>> createEmployee(
            @Valid @RequestBody EmployeeRequest request,
            @Parameter(in = ParameterIn.HEADER, name = IDEMPOTENCY_KEY_HEADER,
                    description = "Optional client-generated key that makes the POST safe to retry",
                    example = "9b2f0c1e-4a7d-4f2a-9c31-8d5b6e0a1f22")
            @RequestHeader(name = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey) {

        EmployeeCreationResult result = employeeService.createEmployee(request, idempotencyKey);
        EmployeeResponse employee = result.employee();

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(employee.id())
                .toUri();

        ResponseEntity.BodyBuilder response = ResponseEntity.created(location);
        if (result.replayed()) {
            response.header(IDEMPOTENT_REPLAY_HEADER, "true");
        }
        return response.body(Result.created(employee));
    }

    /**
     * {@code PUT /api/employees/{id}} - replaces the mutable fields of an employee.
     * The id is taken from the path, never from the body.
     */
    @Operation(
            summary = "Update an employee",
            description = """
                    Requires the ADMIN role. Replaces every mutable field, so send the
                    complete representation. The employee may keep its own email; any other
                    employee already using it is a 409.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated; the response carries the new representation"),
            @ApiResponse(responseCode = "400", description = "Bean validation failed; see fieldErrors"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            @ApiResponse(responseCode = "403", description = "Authenticated but not an ADMIN"),
            @ApiResponse(responseCode = "404", description = "No employee with this id, or departmentId does not exist"),
            @ApiResponse(responseCode = "409", description = "Email already used by another employee")
    })
    @PutMapping("/{id}")
    public ResponseEntity<Result<EmployeeResponse>> updateEmployee(
            @Parameter(description = "Employee id", example = "1") @PathVariable Long id,
            @Valid @RequestBody EmployeeRequest request) {
        EmployeeResponse updated = employeeService.updateEmployee(id, request);
        return ResponseEntity.ok(Result.ok("Employee updated successfully.", updated));
    }

    /**
     * {@code POST /api/employees/{id}/sync} - pushes an employee to the
     * external HR system, retrying transient failures with backoff.
     * Returns 502 when every attempt fails.
     */
    @Operation(
            summary = "Push an employee to the external HR system",
            description = "Retries timeouts and 5xx with exponential backoff, never retries 4xx, and reports 502 when the retry budget is exhausted.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Synchronised; attempts reports how many calls were made"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            @ApiResponse(responseCode = "404", description = "No employee with this id"),
            @ApiResponse(responseCode = "502", description = "The upstream system failed after every retry")
    })
    @PostMapping("/{id}/sync")
    public ResponseEntity<Result<EmployeeSyncResponse>> syncEmployee(
            @Parameter(description = "Employee id", example = "1") @PathVariable Long id) {
        EmployeeResponse employee = employeeService.getEmployeeById(id);
        int attempts = externalHrClient.pushEmployee(ExternalEmployeePayload.from(employee));

        return ResponseEntity.ok(Result.ok(new EmployeeSyncResponse(id, attempts)));
    }

    /**
     * {@code DELETE /api/employees/{id}} - removes an employee.
     *
     * <p>Returns {@code 200 OK} with an empty envelope rather than
     * {@code 204 No Content}: every response in this API carries the same body
     * shape, so the client interceptor has a single code path.
     */
    @Operation(
            summary = "Delete an employee",
            description = "Requires the ADMIN role. Responds 200 with an empty data field so that every response keeps the same envelope.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Deleted"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            @ApiResponse(responseCode = "403", description = "Authenticated but not an ADMIN"),
            @ApiResponse(responseCode = "404", description = "No employee with this id")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Result<Void>> deleteEmployee(
            @Parameter(description = "Employee id", example = "1") @PathVariable Long id) {
        employeeService.deleteEmployee(id);
        return ResponseEntity.ok(Result.<Void>ok("Employee deleted successfully.", null));
    }

    private Sort parseSort(String[] sortParameters) {
        if (sortParameters == null || sortParameters.length == 0) {
            return Sort.by(Sort.Order.asc("id"));
        }

        List<Sort.Order> orders = new ArrayList<>();
        for (String parameter : sortParameters) {
            String[] parts = parameter.split(",", 2);
            String field = parts[0].trim();
            if (!SORTABLE_FIELDS.contains(field)) {
                throw new InvalidSortParameterException("Unsupported sort field '" + field
                        + "'. Allowed fields: " + String.join(", ", SORTABLE_FIELDS.stream().sorted().toList()));
            }

            Sort.Direction direction = Sort.Direction.ASC;
            if (parts.length > 1) {
                String requested = parts[1].trim();
                if ("desc".equalsIgnoreCase(requested)) {
                    direction = Sort.Direction.DESC;
                } else if (!"asc".equalsIgnoreCase(requested)) {
                    throw new InvalidSortParameterException("Unsupported sort direction '" + requested
                            + "'. Use 'asc' or 'desc'.");
                }
            }
            orders.add(new Sort.Order(direction, field));
        }
        return Sort.by(orders);
    }
}
