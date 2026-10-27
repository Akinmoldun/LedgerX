package com.ledgerx.common;

public class SelfTransferException extends DomainException {
    public SelfTransferException(String message) {
        super("SELF_TRANSFER", message);
    }
}
