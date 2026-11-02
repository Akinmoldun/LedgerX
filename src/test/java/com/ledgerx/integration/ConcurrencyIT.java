package com.ledgerx.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.ledgerx.transfer.dto.TransferResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Concurrency integration test: 100 parallel transfers produce no negative balance and no
 * deadlock. Transfers alternate directions on the same account pair so
 * concurrent requests test opposing lock acquisition orders: deterministic
 * lockByIdsOrdered (ORDER BY id) prevents deadlocks by establishing a global lock order.
 */
class ConcurrencyIT extends AbstractIntegrationTest {

    private static final int TRANSFER_COUNT = 100;
    private static final long AMOUNT = 10;
    private static final long INITIAL_EACH = 1_000_000;

    @Test
    @DisplayName("100 parallel transfers both directions: no deadlock, no negative balance, sum invariant")
    void hundredParallelNoDeadlockNoNegative() throws Exception {
        UUID a = account("A", INITIAL_EACH);
        UUID b = account("B", INITIAL_EACH);
        long initialSum = INITIAL_EACH * 2;

        List<UUID> transferIds = java.util.Collections.synchronizedList(new ArrayList<>());
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < TRANSFER_COUNT; i++) {
            final boolean aToB = (i % 2 == 0);
            final int index = i;
            tasks.add(() -> {
                ResponseEntity<TransferResponse> response = post(aToB ? a : b, aToB ? b : a, AMOUNT, "concurrent " + index);
                if (response.getBody() != null) {
                    transferIds.add(response.getBody().id());
                }
                return response.getStatusCode().value();
            });
        }

        ExecutorService pool = Executors.newFixedThreadPool(16);
        try {
            // invokeAll with timeout → a deadlock surfaces as TimeoutException, not a hang
            List<java.util.concurrent.Future<Integer>> futures =
                    pool.invokeAll(tasks, 90, TimeUnit.SECONDS);
            for (java.util.concurrent.Future<Integer> f : futures) {
                assertThat(f.get()).isEqualTo(201);
            }
        } finally {
            pool.shutdownNow();
        }

        Long sum = jdbc.queryForObject(
                "SELECT sum(balance_minor) FROM accounts WHERE id IN (?::uuid, ?::uuid)",
                Long.class, a.toString(), b.toString());
        assertThat(sum).isEqualTo(initialSum);

        Long aBalance = jdbc.queryForObject(
                "SELECT balance_minor FROM accounts WHERE id = ?::uuid", Long.class, a.toString());
        Long bBalance = jdbc.queryForObject(
                "SELECT balance_minor FROM accounts WHERE id = ?::uuid", Long.class, b.toString());
        assertThat(aBalance).isNotNegative();
        assertThat(bBalance).isNotNegative();

        // exactly 2 legs for each of the 100 transfers created by THIS test
        String placeholders = String.join(",", java.util.Collections.nCopies(transferIds.size(), "?::uuid"));
        Object[] idArgs = transferIds.stream().map(UUID::toString).toArray();
        Integer legsForOurs = jdbc.queryForObject(
                "SELECT count(*) FROM entries WHERE transfer_id IN (" + placeholders + ")",
                Integer.class, idArgs);
        assertThat(legsForOurs).isEqualTo(TRANSFER_COUNT * 2);
        assertThat(transferIds).hasSize(TRANSFER_COUNT);
    }

    private ResponseEntity<TransferResponse> post(UUID fromId, UUID toId, long amountMinor, String description) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Idempotency-Key", "conc-" + UUID.randomUUID());
        headers.set("X-Tenant-Id", DEFAULT_TENANT);
        return rest.exchange("/api/v1/transfers", HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "fromAccountId", fromId,
                        "toAccountId", toId,
                        "amountMinor", amountMinor,
                        "currency", "USD",
                        "description", description), headers),
                TransferResponse.class);
    }
}
