package com.ledgerx.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.kafka.ConfluentKafkaContainer;

/**
 * Outbox relay integration test with live broker:
 * verifies fault tolerance during broker outage:
 *
 * <pre>
 *   broker stopped -> POST still returns 201 (outbox rows wait, no loss)
 *   -> relay records failure: attempts >= 1, last_error set, published_at NULL
 *   -> broker restarted -> relay retries with exponential backoff -> published_at set
 *   -> consumer delivers the event exactly once (dedupe), no duplicates after recovery
 * </pre>
 *
 * The broker container is stopped and started via the Docker API on the same container.
 */
@Testcontainers
class OutboxRelayIT extends AbstractIntegrationTest {

    private static final int FIXED_KAFKA_PORT = freePort();

    private static int freePort() {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Container
    static final ConfluentKafkaContainer KAFKA =
            new ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"))
                    // Pin the host port: Docker may hand out a NEW random host port when a
                    // stopped container is started again, which would strand the producer
                    // (bootstrap servers + advertised listener are fixed at first start).
                    .withCreateContainerCmdModifier(cmd -> cmd.getHostConfig().withPortBindings(
                            new PortBinding(Ports.Binding.bindPort(FIXED_KAFKA_PORT), new ExposedPort(9092))))
                    .withStartupTimeout(Duration.ofMinutes(5));

    @DynamicPropertySource
    static void kafkaAndBackoff(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        // Faster backoff cadence ONLY in integration tests - production keeps
        // base 1s / cap 5min from application.yml.
        registry.add("ledgerx.outbox.relay-delay-ms", () -> "200");
        registry.add("ledgerx.outbox.backoff-base-ms", () -> "300");
    }

    private static void stopBroker() {
        DockerClientFactory.lazyClient()
                .stopContainerCmd(KAFKA.getContainerId()).exec();
    }

    private static void startBroker() {
        DockerClientFactory.lazyClient()
                .startContainerCmd(KAFKA.getContainerId()).exec();
    }

    @BeforeEach
    void clearUnpublishedOutboxBacklogFromOtherTests() {
        // Shared Postgres container accumulates unpublished outbox rows from other ITs.
        // Clearing unpublished entries ensures OutboxRelayIT processes its own transfer row
        // immediately rather than timing out waiting on preceding tests' leftover rows.
        jdbc.update("DELETE FROM outbox WHERE published_at IS NULL");
    }

    @Test
    @DisplayName("kafka outage: transfer still postable, failure recorded, retry publishes exactly once after restart")
    void outagePostRetryRecoverExactlyOnce() {
        UUID from = account("outbox-it-from", 20_000);
        UUID to = account("outbox-it-to", 0);

        // 1) broker down
        stopBroker();

        // 2) POST still works - the transfer commits, the outbox row waits
        UUID transferId = postTransfer(from, to);
        assertThat(balance(from)).isEqualTo(7_500L); // 12 500 minor moved
        assertThat(balance(to)).isEqualTo(12_500L);

        // 3) relay records the failure - attempts >= 1, last_error set, unpublished
        await().atMost(30, TimeUnit.SECONDS).until(() -> outboxRow(transferId).get("attempts") != null
                && ((Number) outboxRow(transferId).get("attempts")).intValue() >= 1);
        Map<String, Object> mid = outboxRow(transferId);
        assertThat(mid.get("last_error")).isNotNull();
        assertThat(mid.get("published_at"))
                .as("row must stay unpublished at least until the broker returns")
                .isNull();

        // 4) broker back: relay retries
        startBroker();
        await().atMost(120, TimeUnit.SECONDS).until(() ->
                outboxRow(transferId).get("published_at") != null);

        // 5) the event arrives exactly once after recovery (deduplication via processed_events)
        await().atMost(30, TimeUnit.SECONDS).until(() ->
                consumedRows(transferId) == 1L);
        // settle a bit longer: a duplicate side effect would push the count to 2
        await().atLeast(2, TimeUnit.SECONDS);
        assertThat(consumedRows(transferId)).isEqualTo(1L);
        assertThat(consumedRows(transferId) - 1L).isZero();
    }

    private UUID postTransfer(UUID from, UUID to) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Tenant-Id", DEFAULT_TENANT);
        headers.set("Idempotency-Key", "outbox-it-" + UUID.randomUUID());
        Map<String, Object> body = Map.of(
                "fromAccountId", from.toString(),
                "toAccountId", to.toString(),
                "amountMinor", 12_500,
                "currency", "USD",
                "description", "outbox outage IT");
        ResponseEntity<String> response = rest.postForEntity(
                "/api/v1/transfers", new HttpEntity<>(body, headers), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        String location = response.getHeaders().getLocation().getPath();
        return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    }

    private Map<String, Object> outboxRow(UUID transferId) {
        return jdbc.queryForMap("""
                SELECT attempts, last_error, published_at
                FROM outbox
                WHERE aggregate_id = ?::uuid
                """, transferId.toString());
    }

    private long consumedRows(UUID transferId) {
        Long count = jdbc.queryForObject("""
                SELECT count(*)
                FROM audit_log
                WHERE action = 'TRANSFER_POSTED_CONSUMED'
                  AND entity_id = ?::uuid
                """, Long.class, transferId.toString());
        return count != null ? count : 0L;
    }
}
