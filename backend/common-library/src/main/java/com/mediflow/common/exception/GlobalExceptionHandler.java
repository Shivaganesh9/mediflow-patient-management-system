package com.mediflow.common.exception;

import com.mediflow.common.response.ApiResponse;
import com.mediflow.common.security.RevocationUnavailableException;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;

/**
 * Translates exceptions into the shared {@link ApiResponse} error contract.
 *
 * <p>Client mistakes never fall through to the generic 500 handler, and database outages map to
 * 503 (retryable) rather than 500 so callers and load balancers can react correctly. Messages
 * are deliberately generic: no stack traces, SQL, credentials or patient data are exposed.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ObjectProvider<MeterRegistry> meterRegistryProvider;

    public GlobalExceptionHandler(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.meterRegistryProvider = meterRegistryProvider;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.failure(ex.getMessage(), null));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(ConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.failure(ex.getMessage(), null));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure(ex.getMessage(), null));
    }

    @ExceptionHandler(DateTimeParseException.class)
    public ResponseEntity<ApiResponse<Void>> handleDateTimeParse(DateTimeParseException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure("Invalid date value", null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure("Validation failed", errors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure(ex.getMessage(), null));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodValidation(HandlerMethodValidationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure("Validation failed", null));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        log.debug("Malformed request body: {}", ex.getMostSpecificCause().getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure("Malformed or unreadable request body", null));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiResponse.failure("Unsupported media type", null));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure("Invalid value for parameter '" + ex.getName() + "'", null));
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MissingRequestHeaderException.class})
    public ResponseEntity<ApiResponse<Void>> handleMissingInput(Exception ex) {
        String name = ex instanceof MissingServletRequestParameterException parameter
                ? parameter.getParameterName()
                : ((MissingRequestHeaderException) ex).getHeaderName();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure("Missing required value '" + name + "'", null));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.failure("Resource not found", null));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.failure("HTTP method not supported for this resource", null));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(OptimisticLockingFailureException ex) {
        log.debug("Concurrent modification detected: {}", ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.failure(
                        "The record was modified concurrently; reload it and retry", null));
    }

    @ExceptionHandler(DataAccessResourceFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleDatabaseUnavailable(DataAccessResourceFailureException ex) {
        log.error("Database unavailable: {}", ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.failure("Data store is temporarily unavailable. Please retry shortly.", null));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(AuthenticationException ex) {
        // Deliberately generic: never reveal whether the username exists or the password was wrong.
        log.info("Authentication failed: {}", ex.getClass().getSimpleName());
        recordAuthenticationFailure("credentials");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.failure("Invalid username or password", null));
    }

    @ExceptionHandler(AuthenticationFailedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationFailed(AuthenticationFailedException ex) {
        recordAuthenticationFailure("login");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.failure(ex.getMessage(), null));
    }

    @ExceptionHandler(InactiveAccountException.class)
    public ResponseEntity<ApiResponse<Void>> handleInactiveAccount(InactiveAccountException ex) {
        recordAuthenticationFailure("inactive-account");
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.failure(ex.getMessage(), null));
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ApiResponse<Void>> handleTooManyRequests(TooManyRequestsException ex) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()));
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .headers(headers)
                .body(ApiResponse.failure(ex.getMessage(), null));
    }

    @ExceptionHandler(RevocationUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleRevocationUnavailable(RevocationUnavailableException ex) {
        log.error("Token revocation store unavailable: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.failure(
                        "Authentication is temporarily unavailable. Please retry shortly.", null));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.failure("Access denied", null));
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleServiceUnavailable(ServiceUnavailableException ex) {
        log.error("Downstream dependency unavailable: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.failure(ex.getMessage(), null));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.failure("Request conflicts with an existing record", null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure("Internal server error", null));
    }

    private void recordAuthenticationFailure(String reason) {
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry != null) {
            registry.counter("mediflow.security.authentication.failures", "reason", reason).increment();
        }
    }
}
