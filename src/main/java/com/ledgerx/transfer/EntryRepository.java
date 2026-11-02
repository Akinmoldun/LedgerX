package com.ledgerx.transfer;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EntryRepository extends JpaRepository<Entry, UUID> {

    /**
     * Both legs of a transfer inserted in a single statement.
     * Deferrable constraint trigger {@code entries_balanced} evaluates at COMMIT.
     */
    @Modifying
    @Query(value = """
        INSERT INTO entries (transfer_id, account_id, direction, amount_minor)
        VALUES
          (:transferId, :debitAccountId,  'debit',  :amountMinor),
          (:transferId, :creditAccountId, 'credit', :amountMinor)
        """, nativeQuery = true)
    int insertTransferEntries(@Param("transferId") UUID transferId,
                              @Param("debitAccountId") UUID debitAccountId,
                              @Param("creditAccountId") UUID creditAccountId,
                              @Param("amountMinor") long amountMinor);

    /** Returns whole Entry objects (a column-list select cannot be mapped to List<Entry>). */
    @Query("""
        select e
        from Entry e
        where e.transferId = :transferId
        """)
    List<Entry> findByTransferId(@Param("transferId") UUID transferId);

    /**
     * Calculates the sum of credits minus sum of debits for an account across all posted entries.
     * Note: Debits and credits are explicit directions.
     */
    @Query(value = """
        SELECT COALESCE(SUM(CASE WHEN direction = 'credit' THEN amount_minor ELSE -amount_minor END), 0)
        FROM entries
        WHERE account_id = :accountId
        """, nativeQuery = true)
    long calculateNetBalanceByAccountId(@Param("accountId") UUID accountId);
}
