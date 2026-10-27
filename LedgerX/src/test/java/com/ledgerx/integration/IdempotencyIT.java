package com.ledgerx.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.ledgerx.transfer.dto.TransferResponse;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Week 4 AC (§12 test #1): same key twice → same ID, one entry pair.
 * Also covers 409 key reuse with a different body and the required
 * Idempotency-Key header enforcement.
 */
class IdempotencyIT extends AbstractIntegrationTest {

    private static final long AMOUNT = 2500;

    @Test
    @DisplayName("same key twice → same ID, one entry pair, balances move once")
    void sameKeySameResult() {
        UUID fromId = account("7000", 100000);
        UUID toId = account("8000", 0);
        String key = freshKey();

        ResponseEntity<TransferResponse> first =
                post(fromId, toId, AMOUNT, "withdraw once", key);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(first.getBody()).isNotNull();

        ResponseEntity<TransferResponse> second =
                post(fromId, toId, AMOUNT, "withdraw once", key);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK); // idempotent replay
        assertThat(second.getBody()).isNotNull();

        assertThat(second.getBody().id()).isEqualTo(first.getBody().id());

        // exactly one entry pair, balance moved exactly once
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM entries WHERE transfer_id = ?::uuid", Integer.class, first.getBody().id()))
                .isEqualTo(2);
        assertThat(balance(fromId)).isEqualTo(100000 - AMOUNT);
        assertThat(balance(toId)).isEqualTo(AMOUNT);
    }

    @Test
    @DisplayName("same key with different body → 409 IDEMPOTENCY_KEY_CONFLICT")
    void differentBodySameKeyConflicts() {
        UUID fromId = account("7500", 100000);
        UUID toId = account("8500", 0);
        String key = freshKey();

        ResponseEntity<TransferResponse> first =
                post(fromId, toId, AMOUNT, "original intent", key);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Map> replayDifferent = raw(fromId, toId, 9999, "changed intent", key);
        assertThat(replayDifferent.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(replayDifferent.getBody()).isNotNull();
        assertThat(replayDifferent.getBody().get("error")).isEqualTo("IDEMPOTENCY_KEY_CONFLICT");

        // the original transfer is unchanged
        assertThat(balance(fromId)).isEqualTo(100000 - AMOUNT);
    }

    @Test
    @DisplayName("missing Idempotency-Key header → 400")
    void missingHeaderIsRejected() {
        UUID fromId = account("7600", 100000);
        UUID toId = account("8600", 0);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Tenant-Id", DEFAULT_TENANT);
        ResponseEntity<Map> response = rest.exchange("/api/v1/transfers", HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "fromAccountId", fromId,
                        "toAccountId", toId,
                        "amountMinor", AMOUNT,
                        "currency", "USD"), headers),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private ResponseEntity<TransferResponse> post(UUID fromId, UUID toId, long amountMinor, String description, String key) {
        return rest.exchange("/api/v1/transfers", HttpMethod.POST,
                new HttpEntity<>(body(fromId, toId, amountMinor, description), headers(key)),
                TransferResponse.class);
    }

    private ResponseEntity<Map> raw(UUID fromId, UUID toId, long amountMinor, String description, String key) {
        return rest.exchange("/api/v1/transfers", HttpMethod.POST,
                new HttpEntity<>(body(fromId, toId, amountMinor, description), headers(key)),
                Map.class);
    }

    private static Map<String, Object> body(UUID fromId, UUID toId, long amountMinor, String description) {
        return Map.of(
                "fromAccountId", fromId,
                "toAccountId", toId,
                "amountMinor", amountMinor,
                "currency", "USD",
                "description", description);
    }

    private static HttpHeaders headers(String idempotencyKey) {
        HttpHeaders hs = new HttpHeaders();
        hs.set("Idempotency-Key", idempotencyKey);
        hs.set("X-Tenant-Id", DEFAULT_TENANT);
        return hs;
    }

    private static String freshKey() {
        return "idem-" + UUID.randomUUID();
    }
}
