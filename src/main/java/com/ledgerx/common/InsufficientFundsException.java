package com.ledgerx.common;

public class InsufficientFundsException extends DomainException {
    public InsufficientFundsException(String message) {
        super("INSUFFICIENT_FUNDS", message);
    }
}
