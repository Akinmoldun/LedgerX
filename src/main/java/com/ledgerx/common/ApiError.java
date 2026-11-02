package com.ledgerx.common;

/** Canonical error body: { "error": "CODE", "message": "...", "requestId": "..." }. */
public record ApiError(String error, String message, String requestId) {
}
