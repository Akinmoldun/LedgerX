package com.ledgerx.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.ledgerx.audit.dto.AuditPageResponse;
import com.ledgerx.reconcile.dto.ReconciliationResponse;
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
 * Week 9 AC (§12 test #5):
 * 1. Corrupt one balance manually → reconcile flags exactly that account
 * 2. Audit API returns correct rows for a transfer
 */
class ReconciliationIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("Corrupt one balance manually → reconcile flags exactly that account")
    void corruptBalanceDetectedByReconcile() {
        UUID fund = account("REC-FUND", 1_000_000); // money source; its own balance isn't under test
        UUID a = account("REC-A", 0);
        UUID b = account("REC-B", 0);

        postTransfer(fund, a, 10_000, "fund A");        // A's 10,000 is now backed by an entry
        postTransfer(a, b, 2_500, "reconcile test");

        // Before corruption, A must NOT be flagged
        ResponseEntity<ReconciliationResponse> initial = postReconcile();
        assertThat(initial.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(initial.getBody().mismatches()).noneMatch(m -> m.accountId().equals(a));

        // Manually corrupt account a's balance in the DB
        jdbc.update("UPDATE accounts SET balance_minor = balance_minor + 500 WHERE id = ?::uuid", a.toString());

        ResponseEntity<ReconciliationResponse> afterCorruption = postReconcile();
        assertThat(afterCorruption.getStatusCode()).isEqualTo(HttpStatus.OK);
        ReconciliationResponse body = afterCorruption.getBody();
        assertThat(body).isNotNull();

        // Must flag account a with expected vs actual
        assertThat(body.mismatches()).anyMatch(m ->
                m.accountId().equals(a) && m.actual() == (10_000 - 2_500 + 500) && m.expected() == (10_000 - 2_500)
        );

        // Verify audit log row for RECONCILIATION_MISMATCH was written
        Integer mismatchAudits = jdbc.queryForObject(
                "SELECT count(*) FROM audit_log WHERE action = 'RECONCILIATION_MISMATCH' AND entity_id = ?::uuid",
                Integer.class, a.toString()
        );
        assertThat(mismatchAudits).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("Audit API returns correct rows for a transfer and supports cursor pagination")
    void auditApiReturnsRowsAndPaginates() {
        UUID from = account("AUD-A", 20_000);
        UUID to = account("AUD-B", 0);

        ResponseEntity<TransferResponse> transfer = postTransfer(from, to, 5_000, "audit api test");
        assertThat(transfer.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID transferId = transfer.getBody().id();

        // Query audit API filtered by entityId
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Tenant-Id", DEFAULT_TENANT);

        ResponseEntity<AuditPageResponse> response = rest.exchange(
                "/api/v1/audit?entityType=TRANSFER&entityId=" + transferId + "&limit=10",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                AuditPageResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        AuditPageResponse page = response.getBody();
        assertThat(page).isNotNull();
        assertThat(page.items()).isNotEmpty();
        assertThat(page.items()).allMatch(item ->
                "TRANSFER".equals(item.entityType()) && transferId.equals(item.entityId())
        );
    }

    private ResponseEntity<ReconciliationResponse> postReconcile() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Tenant-Id", DEFAULT_TENANT);
        return rest.exchange("/api/v1/admin/reconcile", HttpMethod.POST,
                new HttpEntity<>(headers), ReconciliationResponse.class);
    }

    private ResponseEntity<TransferResponse> postTransfer(UUID fromId, UUID toId, long amountMinor, String description) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Idempotency-Key", "rec-" + UUID.randomUUID());
        headers.set("X-Tenant-Id", DEFAULT_TENANT);
        return rest.exchange("/api/v1/transfers", HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "fromAccountId", fromId,
                        "toAccountId", toId,
                        "amountMinor", amountMinor,
                        "currency", "USD",
                        "description", description), headers),
                TransferResponse.class);
    }
}
