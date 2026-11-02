package com.ledgerx.common;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/** Maps every failure to the JSON error contract; no logic, no {@code @Transactional}. */
@Hidden
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Hidden
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiError> domain(DomainException ex, HttpServletRequest request) {
        HttpStatus status = statusFor(ex);
        return ResponseEntity.status(status).body(body(ex.getErrorCode(), ex, request));
    }

    @Hidden
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .orElse("validation failed");
        return ResponseEntity.badRequest().body(body("VALIDATION", message, request));
    }

    @Hidden
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiError> missingHeader(MissingRequestHeaderException ex, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(body("VALIDATION", "missing required header: " + ex.getHeaderName(), request));
    }

    @Hidden
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiError> notFound(EntityNotFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body("NOT_FOUND", ex, request));
    }

    @Hidden
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> statusError(ResponseStatusException ex, HttpServletRequest request) {
        String code = ex.getStatusCode() == HttpStatus.BAD_REQUEST ? "VALIDATION" : ex.getStatusCode().toString();
        return ResponseEntity.status(ex.getStatusCode()).body(body(code, ex.getReason(), request));
    }

    @Hidden
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception ex, HttpServletRequest request) {
        log.error("unhandled exception -> 500", ex);
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
