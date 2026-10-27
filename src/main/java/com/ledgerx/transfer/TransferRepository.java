package com.ledgerx.transfer;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferRepository extends JpaRepository<Transfer, UUID> {

    /** Fast-path idempotency lookup (step 1 of §7.3); unique constraint is the real guard (P4). */
    Optional<Transfer> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);

    Optional<Transfer> findByIdAndTenantId(UUID id, UUID tenantId);
}
