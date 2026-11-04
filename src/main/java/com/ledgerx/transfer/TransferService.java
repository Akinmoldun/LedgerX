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
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.persistence.EntityNotFoundException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
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
    private final MeterRegistry meterRegistry;

    public TransferService(TransferRepository transfers, EntryRepository entries,
                           AccountRepository accounts, TenantRepository tenants,
                           LedgerEventRepository ledgerEvents, AuditLogRepository auditLogs,
                           EventSequenceService sequences, OutboxRepository outbox,
                           ObjectMapper objectMapper,
                           MeterRegistry meterRegistry) {
        this.transfers = transfers;
        this.entries = entries;
        this.accounts = accounts;
        this.tenants = tenants;
        this.ledgerEvents = ledgerEvents;
        this.auditLogs = auditLogs;
        this.sequences = sequences;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Executes transfer processing in a single atomic transaction. Ledger events,
     * outbox rows, and audit logs are persisted within the same transaction.
     * Kafka is never called inside the transfer transaction: the transactional outbox
     * decouples database state transitions from message broker delivery.
     * Metrics:
     * - ledgerx.transfer.posted (counter, tag currency)
     * - ledgerx.transfer.failed (counter, tag reason)
     * - ledgerx.transfer.post.duration (timer)
     */
    @Transactional
    public TransferPostResult post(UUID tenantId, CreateTransferRequest req, String idempotencyKey) {
        long startNanos = System.nanoTime();
        try {
            TransferPostResult result = doProcessPost(tenantId, req, idempotencyKey);
            if (result.created()) {
                meterRegistry.counter("ledgerx.transfer.posted", "currency", req.currency()).increment();
            }
            return result;
        } catch (InsufficientFundsException ex) {
            meterRegistry.counter("ledgerx.transfer.failed", "reason", "insufficient_funds").increment();
            throw ex;
        } catch (CurrencyMismatchException ex) {
            meterRegistry.counter("ledgerx.transfer.failed", "reason", "currency_mismatch").increment();
            throw ex;
        } catch (IdempotencyKeyConflictException ex) {
            meterRegistry.counter("ledgerx.transfer.failed", "reason", "idempotency_conflict").increment();
            throw ex;
        } catch (SelfTransferException ex) {
            meterRegistry.counter("ledgerx.transfer.failed", "reason", "self_transfer").increment();
            throw ex;
        } catch (IllegalArgumentException ex) {
            meterRegistry.counter("ledgerx.transfer.failed", "reason", "invalid_argument").increment();
            throw ex;
        } catch (EntityNotFoundException ex) {
            meterRegistry.counter("ledgerx.transfer.failed", "reason", "not_found").increment();
            throw ex;
        } catch (RuntimeException ex) {
            meterRegistry.counter("ledgerx.transfer.failed", "reason", "unexpected").increment();
            throw ex;
        } finally {
            meterRegistry.timer("ledgerx.transfer.post.duration")
                    .record(Duration.ofNanos(System.nanoTime() - startNanos));
        }
    }

    private TransferPostResult doProcessPost(UUID tenantId, CreateTransferRequest req, String idempotencyKey) {
        // 1) Fast path - idempotent replay returns the ORIGINAL result and exits
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
        // Lock both accounts in deterministic ID order to prevent deadlocks before reading.
        Map<UUID, Account> locked = accounts
                .lockByIdsOrdered(List.of(req.fromAccountId(), req.toAccountId())).stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));
        Account from = locked.get(req.fromAccountId());
        Account to = locked.get(req.toAccountId());
        if (from == null || !tenantId.equals(from.getTenantId())) {
            throw new EntityNotFoundException("account not found: " + req.fromAccountId());
        }
        if (to == null || !tenantId.equals(to.getTenantId())) {
            throw new EntityNotFoundException("account not found: " + req.toAccountId());
        }
        if (!req.currency().equals(from.getCurrency()) || !req.currency().equals(to.getCurrency())) {
            throw new CurrencyMismatchException("transfer currency must match both accounts");
        }

        // Funds check on the locked row (unless overdraft is allowed)
        if (!from.isAllowOverdraft() && from.getBalanceMinor() < req.amountMinor()) {
            throw new InsufficientFundsException("account " + from.getId() + " has insufficient funds");
        }

        // Insert transfer with status=pending; database unique constraint guards idempotency
        Transfer t = new Transfer(tenantId, idempotencyKey, req.currency(), req.amountMinor(), req.description());
        transfers.save(t);

        // Insert debit and credit entries atomically; trigger checks balance at commit
        entries.insertTransferEntries(t.getId(), from.getId(), to.getId(), req.amountMinor());

        // Mutate account balances via domain intent methods
        from.debit(req.amountMinor());
        to.credit(req.amountMinor());

        // Mark transfer posted
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

        // Append immutable ledger event log entry in the same transaction
        ledgerEvents.save(new LedgerEvent(
                tenantId,
                "transfer",
                t.getId(),
                sequences.next("transfer", t.getId()),
                "TransferPosted",
                payload));

        // Insert outbox entry in the same transaction for asynchronous relay publishing
        outbox.save(new OutboxEntry(
                "transfer",
                t.getId(),
                "TransferPosted",
                payload,
                jsonOf(Map.of("eventId", eventId.toString(),
                              "schema", "ledgerx.transfer.posted.v1"))));

        // Insert audit log row in the same transaction
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
     * Replays a transfer after a concurrent insert race condition caused a unique constraint violation.
     */
    @Transactional(readOnly = true)
    public TransferPostResult replayAfterConflict(UUID tenantId, CreateTransferRequest req, String idempotencyKey) {
        Optional<Transfer> existing = transfers.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey);
        if (existing.isPresent()) {
            Transfer same = existing.get();
            ensureSameIntent(tenantId, req, same);
            return new TransferPostResult(TransferResponse.from(same), false);
        }
        throw new DataIntegrityViolationException("Unique constraint violation on idempotency key: " + idempotencyKey);
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
