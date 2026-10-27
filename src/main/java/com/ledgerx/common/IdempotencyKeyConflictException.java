package com.ledgerx.common;

public class IdempotencyKeyConflictException extends DomainException {
    public IdempotencyKeyConflictException(String message) {
        super("IDEMPOTENCY_KEY_CONFLICT", message);
    }
}
