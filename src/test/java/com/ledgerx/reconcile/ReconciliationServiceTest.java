package com.ledgerx.reconcile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ledgerx.account.Account;
import com.ledgerx.account.AccountRepository;
import com.ledgerx.account.AccountType;
import com.ledgerx.audit.AuditLog;
import com.ledgerx.audit.AuditLogRepository;
import com.ledgerx.reconcile.dto.ReconciliationResponse;
import com.ledgerx.transfer.EntryRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReconciliationServiceTest {

    @Mock
    private AccountRepository accounts;

    @Mock
    private EntryRepository entries;

    @Mock
    private AuditLogRepository auditLogs;

    private SimpleMeterRegistry meterRegistry;
    private ReconciliationService service;

    private final UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        service = new ReconciliationService(accounts, entries, auditLogs, meterRegistry);
    }

    private static Account createAccount(UUID id, UUID tenantId, String code, AccountType type, long balanceMinor) {
        try {
            var constructor = Account.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            Account a = constructor.newInstance();

            var fId = Account.class.getDeclaredField("id");
            fId.setAccessible(true);
            fId.set(a, id);

            var fTenant = Account.class.getDeclaredField("tenantId");
            fTenant.setAccessible(true);
            fTenant.set(a, tenantId);

            var fCode = Account.class.getDeclaredField("code");
            fCode.setAccessible(true);
            fCode.set(a, code);

            var fType = Account.class.getDeclaredField("type");
            fType.setAccessible(true);
            fType.set(a, type);

            var fBal = Account.class.getDeclaredField("balanceMinor");
            fBal.setAccessible(true);
            fBal.setLong(a, balanceMinor);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        return a;
    }

    @Test
    @DisplayName("reconciliation reports zero mismatches when all balances match entries")
    void allBalanced() {
        UUID a1 = UUID.randomUUID();
        UUID a2 = UUID.randomUUID();
        Account acc1 = createAccount(a1, tenantId, "1001", AccountType.asset, 5000);
        Account acc2 = createAccount(a2, tenantId, "1002", AccountType.asset, 10000);

        when(accounts.findAllOfTenant(tenantId)).thenReturn(List.of(acc1, acc2));
        when(entries.calculateNetBalanceByAccountId(a1)).thenReturn(5000L);
        when(entries.calculateNetBalanceByAccountId(a2)).thenReturn(10000L);

        ReconciliationResponse response = service.reconcile(tenantId);

        assertThat(response.checked()).isEqualTo(2);
        assertThat(response.mismatches()).isEmpty();
        assertThat(service.getLastMismatchCount()).isZero();
        assertThat(meterRegistry.get("ledgerx.reconcile.mismatches").gauge().value()).isZero();
        verify(auditLogs, never()).save(any());
    }

    @Test
    @DisplayName("reconciliation flags corrupted balance, logs audit row, and updates mismatch gauge")
    void corruptedBalanceDetected() {
        UUID a1 = UUID.randomUUID();
        Account acc1 = createAccount(a1, tenantId, "1001", AccountType.asset, 9999); // corrupted!

        when(accounts.findAllOfTenant(tenantId)).thenReturn(List.of(acc1));
        when(entries.calculateNetBalanceByAccountId(a1)).thenReturn(5000L); // expected is 5000

        ReconciliationResponse response = service.reconcile(tenantId);

        assertThat(response.checked()).isEqualTo(1);
        assertThat(response.mismatches()).hasSize(1);
        ReconciliationResponse.Mismatch mismatch = response.mismatches().get(0);
        assertThat(mismatch.accountId()).isEqualTo(a1);
        assertThat(mismatch.expected()).isEqualTo(5000L);
        assertThat(mismatch.actual()).isEqualTo(9999L);

        assertThat(service.getLastMismatchCount()).isEqualTo(1);
        assertThat(meterRegistry.get("ledgerx.reconcile.mismatches").gauge().value()).isEqualTo(1.0);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogs, times(1)).save(captor.capture());
        AuditLog savedAudit = captor.getValue();
        assertThat(savedAudit.getAction()).isEqualTo("RECONCILIATION_MISMATCH");
        assertThat(savedAudit.getEntityId()).isEqualTo(a1);
        assertThat(savedAudit.getBeforeState()).contains("9999");
        assertThat(savedAudit.getAfterState()).contains("5000");
    }
}
