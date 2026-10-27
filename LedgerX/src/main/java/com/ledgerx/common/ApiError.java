package com.ledgerx.common;

/** Canonical error body: { "error": "CODE", "message": "...", "requestId": "..." } (§9). */
public record ApiError(String error, String message, String requestId) {
}
