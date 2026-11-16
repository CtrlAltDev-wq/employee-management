package com.wangqi.employee.controller;

import com.wangqi.employee.common.Result;
import com.wangqi.employee.dto.DepartmentRequest;
import com.wangqi.employee.dto.DepartmentResponse;
import com.wangqi.employee.service.DepartmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * REST endpoints for departments.
 *
 * @author wangqi
 */
@RestController
@RequestMapping("/api/departments")
@RequiredArgsConstructor
@Tag(name = "Departments", description = "Create departments and list them with their employees")
public class DepartmentController {

    private final DepartmentService departmentService;

    /**
     * {@code POST /api/departments} - creates a department.
     */
    @Operation(summary = "Create a department", description = "Requires the ADMIN role. The name must be unique.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created; Location header points at the new department"),
            @ApiResponse(responseCode = "400", description = "Bean validation failed; see fieldErrors"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            @ApiResponse(responseCode = "403", description = "Authenticated but not an ADMIN"),
            @ApiResponse(responseCode = "409", description = "A department with this name already exists")
    })
    @PostMapping
    public ResponseEntity<Result<DepartmentResponse>> createDepartment(
            @Valid @RequestBody DepartmentRequest request) {
        DepartmentResponse created = departmentService.createDepartment(request);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();

        return ResponseEntity.created(location).body(Result.created(created));
    }

    /**
     * {@code GET /api/departments} - returns every department with its
     * employees, loaded through a single query (no N+1).
     */
    @Operation(
            summary = "List departments with employees",
            description = "Served from the Redis read cache when warm; one SQL statement on a miss, thanks to an entity graph.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Every department with its employees"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token")
    })
    @GetMapping
    public ResponseEntity<Result<List<DepartmentResponse>>> getAllDepartments() {
        return ResponseEntity.ok(Result.ok(departmentService.getAllDepartmentsWithEmployees()));
    }
}
