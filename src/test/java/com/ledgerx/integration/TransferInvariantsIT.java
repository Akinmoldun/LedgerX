package com.ledgerx.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.ledgerx.LedgerXApplication;
import com.ledgerx.transfer.TransferService;
import com.ledgerx.transfer.dto.CreateTransferRequest;
import com.ledgerx.transfer.dto.TransferPostResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.lifecycle.AfterContainer;
import net.jqwik.api.lifecycle.BeforeContainer;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Week 4 property test (jqwik). For any random sequence of N transfers
 * (1 ≤ N ≤ 20) between a pool of 3 accounts, verify:
 *
 * (a) per transfer, debit sum == credit sum over its entries,
 * (b) the sum of `accounts.balance_minor` across all accounts is invariant,
 * (c) every `transfer.id` appears in exactly 2 entries.
 *
 * NOTE: jqwik runs on its own JUnit engine, which does not apply Spring's
 * {@code SpringExtension} — so this class bootstraps the app + its own
 * Postgres container manually instead of extending {@link
 * AbstractIntegrationTest}.
 */
class TransferInvariantsIT {

    private static final PostgreSQLContainer<?> PG = AbstractIntegrationTest.POSTGRES;
    private static final AtomicInteger TRY = new AtomicInteger();

    private static ConfigurableApplicationContext app;
    private static TransferService transfers;
    private static JdbcTemplate jdbc;

    @BeforeContainer
    static void boot() {
        System.setProperty("spring.datasource.url", PG.getJdbcUrl());
        System.setProperty("spring.datasource.username", PG.getUsername());
        System.setProperty("spring.datasource.password", PG.getPassword());
        app = new SpringApplicationBuilder(LedgerXApplication.class)
                .profiles("test")
                .properties("server.port=0")
                .run();
        transfers = app.getBean(TransferService.class);
        jdbc = new JdbcTemplate(app.getBean(DataSource.class));
    }

    @AfterContainer
    static void off() {
        if (app != null) {
            app.close();
        }
        System.clearProperty("spring.datasource.url");
        System.clearProperty("spring.datasource.username");
        System.clearProperty("spring.datasource.password");
    }

    @Property
    void invariantsHold(@ForAll @IntRange(min = 1, max = 20) int n) {
        UUID tenantId = UUID.fromString(AbstractIntegrationTest.DEFAULT_TENANT);
        List<UUID> pool = List.of(account(1_000_000), account(1_000_000), account(1_000_000));
        long initialSum = pool.stream().mapToLong(this::balance).sum();

        Random rnd = new Random(n * 7919L + TRY.incrementAndGet());
        List<UUID> transferIds = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            int from = rnd.nextInt(3);
            int to = (from + 1 + rnd.nextInt(2)) % 3; // always different
            long amountMinor = 100 + rnd.nextInt(5000);

            TransferPostResult result = transfers.post(
                    tenantId,
                    new CreateTransferRequest(pool.get(from), pool.get(to), amountMinor, "USD", "property " + i),
                    "prop-" + UUID.randomUUID());
            transferIds.add(result.transfer().id());
        }

        // (c) exactly 2 entries per transfer — and (a) legs balance per transfer
        long totalDebits = 0;
        long totalCredits = 0;
        for (UUID transferId : transferIds) {
            Integer legs = jdbc.queryForObject(
                    "SELECT count(*) FROM entries WHERE transfer_id = ?::uuid", Integer.class, transferId.toString());
            assertThat(legs).isEqualTo(2);

            long debits = jdbc.queryForObject("""
                    SELECT coalesce(sum(amount_minor), 0) FROM entries
                    WHERE transfer_id = ?::uuid AND direction = 'debit'
                    """, Long.class, transferId.toString());
            long credits = jdbc.queryForObject("""
                    SELECT coalesce(sum(amount_minor), 0) FROM entries
                    WHERE transfer_id = ?::uuid AND direction = 'credit'
                    """, Long.class, transferId.toString());
            assertThat(debits).isEqualTo(credits);
            totalDebits += debits;
            totalCredits += credits;
        }

        // (a) global debit == credit over the whole random sequence
        assertThat(totalDebits).isEqualTo(totalCredits);

        // (b) sum of balances across all accounts is invariant
        long finalSum = pool.stream().mapToLong(this::balance).sum();
        assertThat(finalSum).isEqualTo(initialSum);
    }

    private UUID account(long balanceMinor) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO accounts (id, tenant_id, code, name, type, currency, balance_minor)
                VALUES (?::uuid, ?::uuid, ?, ?, 'asset', 'USD', ?)
                """, id.toString(), AbstractIntegrationTest.DEFAULT_TENANT,
                "prop-" + UUID.randomUUID().toString().substring(0, 12),
                "Property pool account", balanceMinor);
        return id;
    }

    private long balance(UUID accountId) {
        return jdbc.queryForObject(
                "SELECT balance_minor FROM accounts WHERE id = ?::uuid", Long.class, accountId.toString());
    }

}
