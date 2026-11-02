package com.ledgerx.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerx.audit.AuditLog;
import com.ledgerx.audit.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Audit consumer. Deduplication rule: inserting into `processed_events`
 * with `ON CONFLICT DO NOTHING` executes within the same database transaction as the
 * side effect (the audit log row). `eventId` acts as the deduplication key so
 * at-least-once message delivery produces an exactly-once side effect.
 */
@Component
public class AuditConsumer {

    private static final Logger log = LoggerFactory.getLogger(AuditConsumer.class);
    private static final String CONSUMER = "audit-consumer";

    private final ProcessedEventRepository processedEvents;
    private final AuditLogRepository auditLogs;
    private final ObjectMapper objectMapper;

    public AuditConsumer(ProcessedEventRepository processedEvents,
                         AuditLogRepository auditLogs,
                         ObjectMapper objectMapper) {
        this.processedEvents = processedEvents;
        this.auditLogs = auditLogs;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${ledgerx.kafka.topic:ledgerx.transfer.posted.v1}", groupId = CONSUMER)
    @Transactional
    public void onTransferPosted(String payload) throws Exception {
        JsonNode event;
        try {
            event = objectMapper.readTree(payload);
        } catch (Exception parseEx) {
            log.warn("unparsable payload dropped (schema violation): {}", parseEx.getMessage());
            return;
        }
        if (event.get("eventId") == null || event.get("eventId").isNull()) {
            log.warn("payload without eventId dropped (schema violation)");
            return;
        }

        java.util.UUID eventId;
        try {
            eventId = java.util.UUID.fromString(event.get("eventId").asText());
        } catch (IllegalArgumentException badId) {
            log.warn("payload with malformed eventId dropped (schema violation)");
            return;
        }
        if (event.get("transferId") == null || event.get("tenantId") == null) {
            log.warn("payload missing transferId/tenantId dropped (schema violation)");
            return;
        }

        int inserted = processedEvents.recordProcessed(eventId, CONSUMER);
        if (inserted == 0) {
            log.info("event {} already processed - skipping side effect", eventId);
            return;
        }

        java.util.UUID transferId = java.util.UUID.fromString(event.get("transferId").asText());
        java.util.UUID tenantId = java.util.UUID.fromString(event.get("tenantId").asText());
        auditLogs.save(AuditLog.forTransferConsumed(
                tenantId,
                transferId,
                payload,
                "consumed TransferPosted eventId=" + eventId));
        log.info("consumed TransferPosted: transfer={}, eventId={}", transferId, eventId);
    }
}
