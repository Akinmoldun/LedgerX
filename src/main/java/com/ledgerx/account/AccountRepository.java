package com.ledgerx.account;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    /**
     * All accounts for a tenant. Native query with explicit columns to prevent schema drift.
     */
    @Query(value = """
        SELECT id, tenant_id, code, name, type, currency, balance_minor,
               version, allow_overdraft, created_at
        FROM accounts
        WHERE tenant_id = :tenantId
        """, nativeQuery = true)
    List<Account> findAllOfTenant(@Param("tenantId") UUID tenantId);

    Optional<Account> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * Pessimistic row lock taken in deterministic ID order so concurrent
     * transfers that share an account always acquire locks in the same order
     * and never deadlock.
     */
    @Query(value = """
        SELECT id, tenant_id, code, name, type, currency, balance_minor,
               version, allow_overdraft, created_at
        FROM accounts
        WHERE id IN (:ids)
        ORDER BY id
        FOR UPDATE
        """, nativeQuery = true)
    List<Account> lockByIdsOrdered(@Param("ids") List<UUID> ids);
}
