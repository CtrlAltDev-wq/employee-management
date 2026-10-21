package com.wangqi.employee.controller;

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
    @GetMapping
    public ResponseEntity<PageResponse<EmployeeResponse>> searchEmployees(
            @Valid @ModelAttribute EmployeeSearchCriteria criteria,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "Page index must be zero or greater") int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Page size must be at least 1")
            @Max(value = 100, message = "Page size must not exceed 100") int size,
            HttpServletRequest request) {

        // Raw values are read from the request, so "salary,desc" stays a single
        // token instead of being split by Spring's comma-aware collection converter.
        PageRequest pageRequest = PageRequest.of(page, size, parseSort(request.getParameterValues("sort")));
        return ResponseEntity.ok(employeeService.searchEmployees(criteria, pageRequest));
    }

    /**
     * {@code GET /api/employees/{id}} - returns a single employee.
     * Responds with 200 OK, or 404 Not Found when the id is unknown.
     */
    @GetMapping("/{id}")
    public ResponseEntity<EmployeeResponse> getEmployeeById(@PathVariable Long id) {
        return ResponseEntity.ok(employeeService.getEmployeeById(id));
    }

    /**
     * {@code POST /api/employees} - creates a new employee.
     * Responds with 201 Created and a {@code Location} header pointing at the created resource.
     *
     * <p>When the client sends an {@code Idempotency-Key}, a retry with the same
     * key and the same payload returns the original employee (same id) instead
     * of creating a duplicate, flagged with {@code Idempotent-Replay: true}.
     */
    @PostMapping
    public ResponseEntity<EmployeeResponse> createEmployee(
            @Valid @RequestBody EmployeeRequest request,
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
        return response.body(employee);
    }

    /**
     * {@code POST /api/employees/{id}/sync} - pushes an employee to the
     * external HR system, retrying transient failures with backoff.
     * Returns 502 when every attempt fails.
     */
    @PostMapping("/{id}/sync")
    public ResponseEntity<EmployeeSyncResponse> syncEmployee(@PathVariable Long id) {
        EmployeeResponse employee = employeeService.getEmployeeById(id);
        int attempts = externalHrClient.pushEmployee(ExternalEmployeePayload.from(employee));

        return ResponseEntity.ok(new EmployeeSyncResponse(id, attempts));
    }

    /**
     * {@code DELETE /api/employees/{id}} - removes an employee.
     * Responds with 204 No Content, or 404 Not Found when the id is unknown.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEmployee(@PathVariable Long id) {
        employeeService.deleteEmployee(id);
        return ResponseEntity.noContent().build();
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
