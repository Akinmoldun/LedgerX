package com.ledgerx.transfer;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferRepository extends JpaRepository<Transfer, UUID> {

    /** Fast-path idempotency lookup; the database unique constraint provides the authoritative race guard. */
    Optional<Transfer> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);

    Optional<Transfer> findByIdAndTenantId(UUID id, UUID tenantId);
}
