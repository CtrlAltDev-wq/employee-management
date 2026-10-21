package com.wangqi.employee.controller;

import com.wangqi.employee.dto.EmployeeCreationResult;
import com.wangqi.employee.dto.EmployeeRequest;
import com.wangqi.employee.dto.EmployeeResponse;
import com.wangqi.employee.dto.EmployeeSyncResponse;
import com.wangqi.employee.integration.ExternalEmployeePayload;
import com.wangqi.employee.integration.ExternalHrClient;
import com.wangqi.employee.service.EmployeeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

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

    private final EmployeeService employeeService;
    private final ExternalHrClient externalHrClient;

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
}
