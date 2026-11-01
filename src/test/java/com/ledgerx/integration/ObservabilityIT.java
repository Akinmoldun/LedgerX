package com.ledgerx.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.ledgerx.transfer.dto.TransferResponse;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Week 10 AC verification test:
 * Validates that /actuator/prometheus exposes the §10 metrics and that
 * the RequestIdMdcFilter sets the X-Request-Id header on HTTP responses.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "management.endpoints.web.exposure.include=health,info,prometheus",
                "management.endpoint.prometheus.enabled=true",
                "management.prometheus.metrics.export.enabled=true"
        }
)
@AutoConfigureObservability
class ObservabilityIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("RequestIdMdcFilter echoes or sets X-Request-Id header on responses")
    void requestIdHeaderPresentOnResponse() {
        String testRequestId = UUID.randomUUID().toString();
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Request-Id", testRequestId);

        ResponseEntity<String> response = rest.exchange(
                "/actuator/health",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst("X-Request-Id")).isEqualTo(testRequestId);
    }

    @Test
    @DisplayName("Prometheus endpoint exposes ledgerx custom metrics")
    void prometheusEndpointExposesLedgerxMetrics() {
        UUID from = account("OBS-A", 10_000);
        UUID to = account("OBS-B", 0);

        // Execute a transfer to emit transfer metrics
        HttpHeaders headers = new HttpHeaders();
        headers.set("Idempotency-Key", "obs-" + UUID.randomUUID());
        headers.set("X-Tenant-Id", DEFAULT_TENANT);
        rest.exchange("/api/v1/transfers", HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "fromAccountId", from,
                        "toAccountId", to,
                        "amountMinor", 1_000,
                        "currency", "USD",
                        "description", "observability test"), headers),
                TransferResponse.class);

        // Fetch /actuator/prometheus
        ResponseEntity<String> promResponse = rest.exchange(
                "/actuator/prometheus",
                HttpMethod.GET,
                null,
                String.class
        );

        assertThat(promResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = promResponse.getBody();
        assertThat(body).isNotNull();

        // Verify §10 metric names appear in the Prometheus output
        assertThat(body).contains("ledgerx_transfer_posted");
        assertThat(body).contains("ledgerx_outbox_pending");
        assertThat(body).contains("ledgerx_reconcile_mismatches");
        assertThat(body).contains("ledgerx_transfer_post_duration_seconds");
    }
}
