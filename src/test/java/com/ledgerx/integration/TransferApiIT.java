package com.ledgerx.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
 * Week 3 AC: one POST → 1 transfer (posted), 2 entries, both balances
 * changed; the `entries_balanced` trigger rejects an unbalanced manual
 * insert. Skips cleanly when Docker is absent (see base class) and always
 * runs on a Docker machine.
 */
class TransferApiIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("POST creates transfer posted, 2 entries, both balances changed")
    void happyPath() {
        UUID fromId = account("1000", 100000);
        UUID toId = account("4000", 0);
        String idempotencyKey = freshKey();

        ResponseEntity<TransferResponse> response = postTransfer(fromId, toId, 12500, "first transfer", idempotencyKey);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        TransferResponse t = response.getBody();
        assertThat(t).isNotNull();
        assertThat(t.status()).isEqualTo("posted");
        assertThat(t.amountMinor()).isEqualTo(12500);
        assertThat(t.postedAt()).isNotNull();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM entries WHERE transfer_id = ?::uuid", Integer.class, t.id())).isEqualTo(2);

        assertThat(balance(fromId)).isEqualTo(100000 - 12500);
        assertThat(balance(toId)).isEqualTo(12500);
    }

    @Test
    @DisplayName("entries_balanced trigger rejects an unbalanced manual insert")
    void unbalancedManualInsertIsRejected() {
        UUID fromId = account("2000", 5000);
        UUID transferId = UUID.randomUUID();
        String idempotencyKey = freshKey();

        jdbc.update("""
                INSERT INTO transfers (id, tenant_id, idempotency_key, currency, amount_minor)
                VALUES (?::uuid, ?::uuid, ?, 'USD', 3000)
                """, transferId.toString(), DEFAULT_TENANT, idempotencyKey);

        // one debit leg only → the deferrable trigger raises at commit
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO entries (transfer_id, account_id, direction, amount_minor)
                VALUES (?::uuid, ?::uuid, 'debit', 3000)
                """, transferId.toString(), fromId.toString()))
                .isInstanceOf(org.springframework.dao.DataAccessException.class)
                .hasStackTraceContaining("unbalanced transfer");

        // the failed insert left no entries; the transfer row itself committed
        assertThat(jdbc.queryForObject("SELECT count(*) FROM entries WHERE transfer_id = ?::uuid",
                Integer.class, transferId.toString())).isZero();
        jdbc.update("DELETE FROM transfers WHERE id = ?::uuid", transferId.toString());
    }

    @Test
    @DisplayName("POST with insufficient funds returns 422 and persists nothing")
    void insufficientFunds() {
        UUID fromId = account("3000", 100);
        UUID toId = account("6000", 0);
        String idempotencyKey = freshKey();

        ResponseEntity<Map> response = postTransferRaw(fromId, toId, 100000, "too much", idempotencyKey);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("error")).isEqualTo("INSUFFICIENT_FUNDS");
        // nothing persisted — rolled back at step 4 of §7.3
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transfers WHERE idempotency_key = ?",
                Integer.class, idempotencyKey)).isZero();
        assertThat(balance(fromId)).isEqualTo(100);
        assertThat(balance(toId)).isZero();
    }

    private ResponseEntity<TransferResponse> postTransfer(UUID fromId, UUID toId, long amountMinor, String description, String idempotencyKey) {
        return rest.exchange("/api/v1/transfers", HttpMethod.POST,
                new HttpEntity<>(body(fromId, toId, amountMinor, description), headers(idempotencyKey)),
                TransferResponse.class);
    }

    private ResponseEntity<Map> postTransferRaw(UUID fromId, UUID toId, long amountMinor, String description, String idempotencyKey) {
        return rest.exchange("/api/v1/transfers", HttpMethod.POST,
                new HttpEntity<>(body(fromId, toId, amountMinor, description), headers(idempotencyKey)),
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
        return "it-" + UUID.randomUUID();
    }
}
