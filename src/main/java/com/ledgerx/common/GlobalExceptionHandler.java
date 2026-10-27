package com.ledgerx.common;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/** Maps every failure to the JSON error contract; no logic, no {@code @Transactional}. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiError> domain(DomainException ex, HttpServletRequest request) {
        HttpStatus status = statusFor(ex);
        return ResponseEntity.status(status).body(body(ex.getErrorCode(), ex, request));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .orElse("validation failed");
        return ResponseEntity.badRequest().body(body("VALIDATION", message, request));
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiError> notFound(EntityNotFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body("NOT_FOUND", ex, request));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> statusError(ResponseStatusException ex, HttpServletRequest request) {
        String code = ex.getStatusCode() == HttpStatus.BAD_REQUEST ? "VALIDATION" : ex.getStatusCode().toString();
        return ResponseEntity.status(ex.getStatusCode()).body(body(code, ex.getReason(), request));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception ex, HttpServletRequest request) {
        return ResponseEntity.internalServerError().body(body("INTERNAL_ERROR", ex, request));
    }

    private static HttpStatus statusFor(DomainException ex) {
        return switch (ex.getErrorCode()) {
            case "INSUFFICIENT_FUNDS", "CURRENCY_MISMATCH" -> HttpStatus.UNPROCESSABLE_ENTITY;
            case "IDEMPOTENCY_KEY_CONFLICT" -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };
    }

    private static String requestId(HttpServletRequest request) {
        if (request == null) {
            return UUID.randomUUID().toString();
        }
        return Optional.ofNullable(request.getHeader("X-Request-Id"))
                .orElseGet(() -> UUID.randomUUID().toString());
    }

    private static ApiError body(String code, Exception ex, HttpServletRequest request) {
        return body(code, ex.getMessage(), request);
    }

    private static ApiError body(String code, String message, HttpServletRequest request) {
        return new ApiError(code, message, requestId(request));
    }
}
