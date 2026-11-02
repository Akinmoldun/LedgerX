package com.ledgerx.reconcile;

import com.ledgerx.account.Account;
import com.ledgerx.account.AccountRepository;
import com.ledgerx.audit.AuditLog;
import com.ledgerx.audit.AuditLogRepository;
import com.ledgerx.reconcile.dto.ReconciliationResponse;
import com.ledgerx.transfer.EntryRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reconciliation service.
 * Verifies that accounts.balance_minor equals the sum of credits minus debits from entries.
 * Read-only against accounts/entries; records an audit log row and updates the gauge metric
 * when a mismatch is discovered.
 */
@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private final AccountRepository accounts;
    private final EntryRepository entries;
    private final AuditLogRepository auditLogs;
    private final AtomicLong mismatchGaugeValue = new AtomicLong(0);

    public ReconciliationService(
            AccountRepository accounts,
            EntryRepository entries,
            AuditLogRepository auditLogs,
            MeterRegistry meterRegistry) {
        this.accounts = accounts;
        this.entries = entries;
        this.auditLogs = auditLogs;

        Gauge.builder("ledgerx.reconcile.mismatches", mismatchGaugeValue, AtomicLong::get)
                .description("Number of balance mismatches found during last reconciliation")
                .register(meterRegistry);
    }

    /**
     * Executes reconciliation across all accounts for the given tenant.
     * Read-only for domain state (accounts and entries are never mutated).
     */
    @Transactional
    public ReconciliationResponse reconcile(UUID tenantId) {
        List<Account> accountList = accounts.findAllOfTenant(tenantId);
        List<ReconciliationResponse.Mismatch> mismatches = new ArrayList<>();

        for (Account acc : accountList) {
            long netEntries = entries.calculateNetBalanceByAccountId(acc.getId());
            long expected = netEntries;
            long actual = acc.getBalanceMinor();

            if (expected != actual) {
                log.error("Reconciliation mismatch for account {}: expected={}, actual={}",
                        acc.getId(), expected, actual);
                mismatches.add(new ReconciliationResponse.Mismatch(acc.getId(), expected, actual));

                AuditLog audit = AuditLog.forReconciliationMismatch(
                        tenantId,
                        acc.getId(),
                        expected,
                        actual,
                        "RECONCILIATION_MISMATCH: balance " + actual + " != expected " + expected
                );
                auditLogs.save(audit);
            }
        }

        mismatchGaugeValue.set(mismatches.size());
        return new ReconciliationResponse(accountList.size(), mismatches);
    }

    public long getLastMismatchCount() {
        return mismatchGaugeValue.get();
    }
}
