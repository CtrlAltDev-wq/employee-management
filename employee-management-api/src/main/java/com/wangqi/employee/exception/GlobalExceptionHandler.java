package com.wangqi.employee.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Centralised exception handling for the whole application.
 * Converts domain exceptions into meaningful HTTP status codes and a
 * consistent {@link ErrorResponse} body.
 *
 * @author wangqi
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Domain 404: the requested employee does not exist.
     */
    @ExceptionHandler(EmployeeNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleEmployeeNotFound(EmployeeNotFoundException ex,
                                                               HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    /**
     * Domain 409: an employee with the same email already exists.
     */
    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateEmail(DuplicateEmailException ex,
                                                             HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    /**
     * Domain 404: the requested department does not exist.
     */
    @ExceptionHandler(DepartmentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleDepartmentNotFound(DepartmentNotFoundException ex,
                                                                 HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    /**
     * Domain 409: a department with the same name already exists.
     */
    @ExceptionHandler(DuplicateDepartmentException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateDepartment(DuplicateDepartmentException ex,
                                                                  HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    /**
     * Domain 409: the same Idempotency-Key arrived with a different payload.
     */
    @ExceptionHandler(IdempotencyKeyReuseException.class)
    public ResponseEntity<ErrorResponse> handleIdempotencyKeyReuse(IdempotencyKeyReuseException ex,
                                                                   HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    /**
     * Database-level conflict, e.g. two concurrent requests using the same
     * Idempotency-Key. Retrying the same request returns the stored result.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex,
                                                                      HttpServletRequest request) {
        return build(HttpStatus.CONFLICT,
                "The request conflicts with a concurrently created resource. Retry the same request.",
                request);
    }

    /**
     * Upstream 502: the third-party system failed after every retry.
     */
    @ExceptionHandler(ExternalSyncException.class)
    public ResponseEntity<ErrorResponse> handleExternalSyncFailure(ExternalSyncException ex,
                                                                   HttpServletRequest request) {
        return build(HttpStatus.BAD_GATEWAY, ex.getMessage(), request);
    }

    /**
     * Authentication 401: wrong username or password on login.
     * The message is intentionally generic so it does not reveal whether the
     * username exists.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationFailure(AuthenticationException ex,
                                                                     HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "Invalid username or password.", request);
    }

    /**
     * Bean validation 400: {@code @Valid} failed on the request body.
     * Every offending field is reported back to the client.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationFailure(MethodArgumentNotValidException ex,
                                                                 HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }

        ErrorResponse body = ErrorResponse.of(
                HttpStatus.BAD_REQUEST,
                "Validation failed for one or more fields.",
                request.getRequestURI(),
                fieldErrors
        );
        return ResponseEntity.badRequest().body(body);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(ErrorResponse.of(status, message, request.getRequestURI()));
    }
}
