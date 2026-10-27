package com.ledgerx.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ledgerx.transfer.dto.TransferResponse;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Week 5 AC: after POST → 1 transfer, 2 entries, 1 ledger_event, 1 audit_log;
 * `deny_mutation` throws on UPDATE attempt AND on DELETE attempt (two
 * separate tests).
 */
class EventStoreAuditIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("POST produces 1 transfer + 2 entries + 1 ledger_event + 1 audit_log in one tx")
    void postProducesEventAndAudit() throws Exception {
        UUID fromId = account("9000", 50000);
        UUID toId = account("9500", 0);
        String key = freshKey();

        ResponseEntity<TransferResponse> response = post(fromId, toId, 12500, "event store test", key);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID transferId = response.getBody().id();

        Integer eventCount = jdbc.queryForObject(
                "SELECT count(*) FROM ledger_events WHERE aggregate_type = 'transfer' AND aggregate_id = ?::uuid",
                Integer.class, transferId.toString());
        assertThat(eventCount).isEqualTo(1);

        // event content: TransferPosted, sequence 1, payload carries the transfer
        Map<String, Object> event = jdbc.queryForMap("""
                SELECT event_type, sequence, tenant_id, payload ->> 'transferId' as transfer_id,
                       payload ->> 'amountMinor' as amount_minor
                FROM ledger_events WHERE aggregate_type = 'transfer' AND aggregate_id = ?::uuid
                """, transferId.toString());
        assertThat(event.get("event_type")).isEqualTo("TransferPosted");
        assertThat(((Number) event.get("sequence")).longValue()).isEqualTo(1L);
        assertThat(event.get("transfer_id")).isEqualTo(transferId.toString());
        assertThat(event.get("amount_minor")).isEqualTo("12500");

        Map<String, Object> audit = jdbc.queryForMap("""
                SELECT action, entity_type, entity_id, before, after, reason
                FROM audit_log WHERE entity_type = 'TRANSFER' AND entity_id = ?::uuid
                """, transferId.toString());
        assertThat(audit.get("action")).isEqualTo("TRANSFER_POSTED");
        assertThat(audit.get("entity_id")).isEqualTo(transferId.toString());
        assertThat(audit.get("before")).isNull();
        // after holds the same payload
        ObjectMapper mapper = new ObjectMapper();
        var after = mapper.readTree((String) audit.get("after"));
        assertThat(after.get("transferId").asText()).isEqualTo(transferId.toString());
        assertThat((String) audit.get("reason")).contains("idempotency key");
    }

    @Test
    @DisplayName("deny_mutation trigger throws on UPDATE attempt of ledger_events")
    void updateOnLedgerEventsFails() {
        UUID fromId = account("9100", 50000);
        UUID toId = account("9600", 0);

        ResponseEntity<TransferResponse> response = post(fromId, toId, 1000, "update attempt", freshKey());
        UUID transferId = response.getBody().id();

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE ledger_events SET event_type = 'Hacked' WHERE aggregate_type = 'transfer' AND aggregate_id = ?::uuid",
                transferId.toString()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only table");
    }

    @Test
    @DisplayName("deny_mutation trigger throws on DELETE attempt of ledger_events")
    void deleteOnLedgerEventsFails() {
        UUID fromId = account("9200", 50000);
        UUID toId = account("9700", 0);

        ResponseEntity<TransferResponse> response = post(fromId, toId, 1000, "delete attempt", freshKey());
        UUID transferId = response.getBody().id();

        assertThatThrownBy(() -> jdbc.update(
                "DELETE FROM ledger_events WHERE aggregate_type = 'transfer' AND aggregate_id = ?::uuid",
                transferId.toString()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("not allowed");
    }

    private ResponseEntity<TransferResponse> post(UUID fromId, UUID toId, long amountMinor, String description, String key) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Idempotency-Key", key);
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

    private static String freshKey() {
        return "ev-" + UUID.randomUUID();
    }
}
