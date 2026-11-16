package com.wangqi.employee.exception;

import com.wangqi.employee.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Centralised exception handling for the whole application.
 *
 * <p>Every handler returns the same {@link Result} envelope the controllers use,
 * so a client never has to parse two different error shapes. Domain exceptions
 * are mapped to meaningful HTTP status codes; anything unexpected is logged with
 * its stack trace and reported as a generic {@code 500} so internal details never
 * leak to the caller.
 *
 * @author wangqi
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Domain 404: the requested employee does not exist.
     */
    @ExceptionHandler(EmployeeNotFoundException.class)
    public ResponseEntity<Result<Void>> handleEmployeeNotFound(EmployeeNotFoundException ex,
                                                               HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    /**
     * Domain 409: an employee with the same email already exists.
     */
    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<Result<Void>> handleDuplicateEmail(DuplicateEmailException ex,
                                                             HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    /**
     * Domain 404: the requested department does not exist.
     */
    @ExceptionHandler(DepartmentNotFoundException.class)
    public ResponseEntity<Result<Void>> handleDepartmentNotFound(DepartmentNotFoundException ex,
                                                                 HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    /**
     * Domain 409: a department with the same name already exists.
     */
    @ExceptionHandler(DuplicateDepartmentException.class)
    public ResponseEntity<Result<Void>> handleDuplicateDepartment(DuplicateDepartmentException ex,
                                                                  HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    /**
     * Domain 409: the same Idempotency-Key arrived with a different payload.
     */
    @ExceptionHandler(IdempotencyKeyReuseException.class)
    public ResponseEntity<Result<Void>> handleIdempotencyKeyReuse(IdempotencyKeyReuseException ex,
                                                                 HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    /**
     * Database-level conflict, e.g. two concurrent requests using the same
     * Idempotency-Key. Retrying the same request returns the stored result.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Result<Void>> handleDataIntegrityViolation(DataIntegrityViolationException ex,
                                                                    HttpServletRequest request) {
        return build(HttpStatus.CONFLICT,
                "The request conflicts with a concurrently created resource. Retry the same request.",
                request);
    }

    /**
     * Upstream 502: the third-party system failed after every retry.
     */
    @ExceptionHandler(ExternalSyncException.class)
    public ResponseEntity<Result<Void>> handleExternalSyncFailure(ExternalSyncException ex,
                                                                 HttpServletRequest request) {
        return build(HttpStatus.BAD_GATEWAY, ex.getMessage(), request);
    }

    /**
     * Authentication 401: wrong username or password on login.
     * The message is intentionally generic so it does not reveal whether the
     * username exists.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Result<Void>> handleAuthenticationFailure(AuthenticationException ex,
                                                                   HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "Invalid username or password.", request);
    }

    /**
     * Query parameter 400: the requested sort field or direction is not
     * supported. Rejecting it early prevents an invalid property expression
     * from reaching the persistence layer.
     */
    @ExceptionHandler(InvalidSortParameterException.class)
    public ResponseEntity<Result<Void>> handleInvalidSortParameter(InvalidSortParameterException ex,
                                                                  HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    /**
     * Query parameter 400: a constraint on a {@code @RequestParam} failed
     * (for example {@code size} above the maximum page size).
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<Result<Void>> handleHandlerMethodValidation(HandlerMethodValidationException ex,
                                                                     HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getAllValidationResults().forEach(result -> {
            String parameter = result.getMethodParameter().getParameterName();
            result.getResolvableErrors().forEach(error -> {
                // A field error is keyed by the property it belongs to; a
                // class-level constraint (e.g. the salary range rule on
                // EmployeeSearchCriteria) has no property, so it is keyed by
                // the parameter name instead.
                String key = error instanceof FieldError fieldError
                        ? fieldError.getField()
                        : (parameter != null ? parameter : "parameter");
                fieldErrors.putIfAbsent(key, error.getDefaultMessage());
            });
        });

        return badRequest("Validation failed for one or more request parameters.", request, fieldErrors);
    }

    /**
     * Bean validation 400: {@code @Valid} failed on the request body.
     * Every offending field is reported back to the client.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValidationFailure(MethodArgumentNotValidException ex,
                                                               HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }

        return badRequest("Validation failed for one or more fields.", request, fieldErrors);
    }

    /**
     * 400: the body is not valid JSON, or a value has the wrong type
     * (for example {@code "salary": "a lot"}). Without this mapping Jackson's
     * exception would surface as a {@code 500}.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleUnreadableBody(HttpMessageNotReadableException ex,
                                                            HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST,
                "Malformed request body: it must be valid JSON matching the documented schema.",
                request);
    }

    /**
     * 400: a path variable or query parameter cannot be converted to the
     * declared type, e.g. {@code GET /api/employees/abc}.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                           HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST,
                "Parameter '" + ex.getName() + "' has an invalid value: " + ex.getValue(),
                request);
    }

    /**
     * 400: a required query parameter is missing.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<Void>> handleMissingParameter(MissingServletRequestParameterException ex,
                                                               HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST,
                "Required request parameter '" + ex.getParameterName() + "' is missing.",
                request);
    }

    /**
     * 405: the path exists but does not support this HTTP method.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                                 HttpServletRequest request) {
        return build(HttpStatus.METHOD_NOT_ALLOWED,
                "HTTP method " + ex.getMethod() + " is not supported for this endpoint.",
                request);
    }

    /**
     * 415: the request body cannot be consumed, typically a missing or
     * unsupported {@code Content-Type}.
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex,
                                                                   HttpServletRequest request) {
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Unsupported Content-Type '" + ex.getContentType() + "'. Use application/json.",
                request);
    }

    /**
     * 500: anything not anticipated. The stack trace goes to the log, never to
     * the client, and the correlation id lets the log line be found again.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception for {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected internal error occurred. Quote the requestId when reporting it.",
                request);
    }

    private ResponseEntity<Result<Void>> badRequest(String message, HttpServletRequest request,
                                                    Map<String, String> fieldErrors) {
        return ResponseEntity.badRequest()
                .body(Result.failure(HttpStatus.BAD_REQUEST, message, request.getRequestURI(), fieldErrors));
    }

    private ResponseEntity<Result<Void>> build(HttpStatus status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(Result.failure(status, message, request.getRequestURI()));
    }
}
