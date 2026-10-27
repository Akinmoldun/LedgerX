package com.ledgerx.transfer;

import com.ledgerx.account.Account;
import com.ledgerx.account.AccountRepository;
import com.ledgerx.audit.AuditLog;
import com.ledgerx.audit.AuditLogRepository;
import com.ledgerx.common.CurrencyMismatchException;
import com.ledgerx.common.InsufficientFundsException;
import com.ledgerx.common.IdempotencyKeyConflictException;
import com.ledgerx.common.SelfTransferException;
import com.ledgerx.common.TenantRepository;
import com.ledgerx.ledger.EventSequenceService;
import com.ledgerx.ledger.LedgerEvent;
import com.ledgerx.ledger.LedgerEventRepository;
import com.ledgerx.outbox.OutboxEntry;
import com.ledgerx.outbox.OutboxRepository;
import com.ledgerx.transfer.dto.CreateTransferRequest;
import com.ledgerx.transfer.dto.TransferPostResult;
import com.ledgerx.transfer.dto.TransferResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferService {

    private final TransferRepository transfers;
    private final EntryRepository entries;
    private final AccountRepository accounts;
    private final TenantRepository tenants;
    private final LedgerEventRepository ledgerEvents;
    private final AuditLogRepository auditLogs;
    private final EventSequenceService sequences;
    private final OutboxRepository outbox;
    private final ObjectMapper objectMapper;

    public TransferService(TransferRepository transfers, EntryRepository entries,
                           AccountRepository accounts, TenantRepository tenants,
                           LedgerEventRepository ledgerEvents, AuditLogRepository auditLogs,
                           EventSequenceService sequences, OutboxRepository outbox,
                           ObjectMapper objectMapper) {
        this.transfers = transfers;
        this.entries = entries;
        this.accounts = accounts;
        this.tenants = tenants;
        this.ledgerEvents = ledgerEvents;
        this.auditLogs = auditLogs;
        this.sequences = sequences;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    /**
     * Exact shape of §7.3. Steps 8 (ledger_event), 9 (outbox) and 10 (audit_log)
     * are deliberately omitted — they arrive in Weeks 5–6. Any step throwing
     * rolls the whole transaction back; Kafka being unreachable cannot make a
     * transfer fail (relays are the only publishers, P5).
     */
    @Transactional
    public TransferPostResult post(UUID tenantId, CreateTransferRequest req, String idempotencyKey) {
        // 1) Fast path — idempotent replay returns the ORIGINAL result and exits
        Optional<Transfer> existing =
                transfers.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey);
        if (existing.isPresent()) {
            Transfer same = existing.get();
            ensureSameIntent(tenantId, req, same);
            return new TransferPostResult(TransferResponse.from(same), false);
        }

        // 2) Validate: accounts exist, same tenant, same currency, no self-transfer
        requireTenant(tenantId);
        if (req.amountMinor() <= 0) {
            throw new IllegalArgumentException("amountMinor must be > 0");
        }
        if (req.fromAccountId().equals(req.toAccountId())) {
            throw new SelfTransferException("from and to accounts must differ");
        }
        Account from = accounts.findByIdAndTenantId(req.fromAccountId(), tenantId)
                .orElseThrow(() -> new EntityNotFoundException("account not found: " + req.fromAccountId()));
        Account to = accounts.findByIdAndTenantId(req.toAccountId(), tenantId)
                .orElseThrow(() -> new EntityNotFoundException("account not found: " + req.toAccountId()));
        if (!req.currency().equals(from.getCurrency()) || !req.currency().equals(to.getCurrency())) {
            throw new CurrencyMismatchException("transfer currency must match both accounts");
        }

        // 3) Lock both accounts in deterministic order (P6)
        Map<UUID, Account> locked = accounts
                .lockByIdsOrdered(List.of(req.fromAccountId(), req.toAccountId())).stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));
        from = locked.get(req.fromAccountId());
        to = locked.get(req.toAccountId());

        // 4) Funds check (unless allow_overdraft) — on the locked row
        if (!from.isAllowOverdraft() && from.getBalanceMinor() < req.amountMinor()) {
            throw new InsufficientFundsException("account " + from.getId() + " has insufficient funds");
        }

        // 5) Insert transfer (status=pending) — unique constraint protects idempotency (P4)
        Transfer t = new Transfer(tenantId, idempotencyKey, req.currency(), req.amountMinor(), req.description());
        transfers.save(t);

        // 6) Insert 2 entries (trigger checks balance at COMMIT) — one statement
        entries.insertTransferEntries(t.getId(), from.getId(), to.getId(), req.amountMinor());

        // 7) Mutate balances via intent methods
        from.debit(req.amountMinor());
        to.credit(req.amountMinor());

        // 11) Mark posted
        t.markPosted();

        UUID eventId = UUID.randomUUID();

        String payload = jsonOf(Map.of(
                "eventId", eventId.toString(),
                "transferId", t.getId().toString(),
                "tenantId", tenantId.toString(),
                "currency", req.currency(),
                "amountMinor", req.amountMinor(),
                "status", t.getStatus().getDbValue(),
                "postedAt", t.getPostedAt().toString()));

        // 8) Append ledger_event — same @Transactional (Week 5)
        ledgerEvents.save(new LedgerEvent(
                tenantId,
                "transfer",
                t.getId(),
                sequences.next("transfer", t.getId()),
                "TransferPosted",
                payload));

        // 9) Insert outbox row — same @Transactional (§7.3); relay publishes it (P5)
        outbox.save(new OutboxEntry(
                "transfer",
                t.getId(),
                "TransferPosted",
                payload,
                jsonOf(Map.of("eventId", eventId.toString(),
                              "schema", "ledgerx.transfer.posted.v1"))));

        // 10) Insert audit_log row — same @Transactional (P8; Week 5)
        auditLogs.save(AuditLog.forTransferPosted(
                tenantId,
                t.getId(),
                payload,
                "posted with idempotency key " + idempotencyKey,
                null));

        return new TransferPostResult(TransferResponse.from(t), true);
    }

    @Transactional(readOnly = true)
    public TransferResponse get(UUID tenantId, UUID transferId) {
        requireTenant(tenantId);
        Transfer t = transfers.findByIdAndTenantId(transferId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("transfer not found: " + transferId));
        return TransferResponse.from(t);
    }

    /**
     * 409 when the same key is reused with a different body: compares every
     * request field against the stored transfer and its entries.
     */
    private void ensureSameIntent(UUID tenantId, CreateTransferRequest req, Transfer same) {
        List<Entry> legs = entries.findByTransferId(same.getId());
        UUID debited = legs.stream().filter(e -> Entry.DEBIT.equals(e.getDirection()))
                .findFirst().map(Entry::getAccountId).orElse(null);
        UUID credited = legs.stream().filter(e -> Entry.CREDIT.equals(e.getDirection()))
                .findFirst().map(Entry::getAccountId).orElse(null);
        boolean sameBody = req.amountMinor() == same.getAmountMinor()
                && req.currency().equals(same.getCurrency())
                && Objects.equals(req.description(), same.getDescription())
                && req.fromAccountId().equals(debited)
                && req.toAccountId().equals(credited)
                && same.getTenantId().equals(tenantId);
        if (!sameBody) {
            throw new IdempotencyKeyConflictException(
                    "Idempotency-Key already used by transfer " + same.getId());
        }
    }

    private void requireTenant(UUID tenantId) {
        if (!tenants.existsById(tenantId)) {
            throw new EntityNotFoundException("unknown tenant: " + tenantId);
        }
    }

    private String jsonOf(Map<String, ?> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("payload serialization failed", e);
        }
    }
}
