package com.ledgerx.common;

/**
 * Base for domain rule violations with a stable machine-readable code used in
 * the error body. Subclasses map to HTTP statuses in
 * {@link GlobalExceptionHandler}.
 */
public abstract class DomainException extends RuntimeException {

    private final String errorCode;

    protected DomainException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
