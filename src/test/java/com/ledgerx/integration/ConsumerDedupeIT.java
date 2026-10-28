package com.ledgerx.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Week 7 AC (§12 test #4): the same eventId published twice must produce
 * exactly ONE audit row — dedupe via processed_events ON CONFLICT DO NOTHING
 * in the same transaction as the side effect. Uses a REAL broker (§12 rule)
 * via Testcontainers; skipped cleanly without Docker, always runs on a
 * Docker machine.
 */
@Testcontainers
class ConsumerDedupeIT extends AbstractIntegrationTest {

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @DynamicPropertySource
    static void kafkaBootstrap(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Test
    @DisplayName("same eventId twice → exactly one consumed audit row")
    void sameEventIdTwiceOneAuditRow() {
        UUID transferId = UUID.randomUUID();
        String eventId = UUID.randomUUID().toString();
        String payload = dedupePayload(eventId, transferId);
        String topic = "ledgerx.transfer.posted.v1";

        kafkaTemplate.send(topic, transferId.toString(), payload);
        kafkaTemplate.send(topic, transferId.toString(), payload);

        await().atMost(30, TimeUnit.SECONDS).until(() ->
                consumedRows(transferId) == 1L);

        // settle a bit longer — a duplicate side effect would push the count to 2
        await().atLeast(2, TimeUnit.SECONDS);
        assertThat(consumedRows(transferId)).isEqualTo(1L);
    }

    private static String dedupePayload(String eventId, UUID transferId) {
        return "{\"eventId\":\"" + eventId
                + "\",\"transferId\":\"" + transferId
                + "\",\"tenantId\":\"" + DEFAULT_TENANT
                + "\",\"currency\":\"USD\",\"amountMinor\":1250,\"postedAt\":"
                + Instant.now().toEpochMilli() + "}";
    }

    private long consumedRows(UUID transferId) {
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM audit_log WHERE action = 'TRANSFER_POSTED_CONSUMED' AND entity_id = ?::uuid",
                Long.class, transferId.toString());
        return count == null ? 0L : count;
    }
}
