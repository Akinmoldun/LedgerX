package com.ledgerx.integration;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    public static final String DEFAULT_TENANT = "00000000-0000-0000-0000-000000000001";

    /**
     * One Postgres for the whole JVM (singleton pattern): started once, reaped by
     * Ryuk at JVM exit. Starting a container per test class cost ~65s each on this
     * Docker setup. Data dir on tmpfs removes WSL2 disk I/O from initdb/startup.
     * All ITs use random account codes/ids, so sharing the DB is safe.
     */
    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16")
                    .withTmpFs(Map.of("/var/lib/postgresql/data", "rw"))
                    .withStartupTimeout(Duration.ofMinutes(5));

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    public static boolean dockerAvailable() {
        try {
            return org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Autowired
    protected TestRestTemplate rest;

    @Autowired
    protected JdbcTemplate jdbc;

    protected UUID account(String code, long balanceMinor) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO accounts (id, tenant_id, code, name, type, currency, balance_minor)
                VALUES (?::uuid, ?::uuid, ?, ?, 'asset', 'USD', ?)
                """, id.toString(), DEFAULT_TENANT,
                code + "-" + suffix(), "IT Account " + code, balanceMinor);
        return id;
    }

    protected long balance(UUID accountId) {
        return jdbc.queryForObject(
                "SELECT balance_minor FROM accounts WHERE id = ?::uuid",
                Long.class, accountId.toString());
    }

    protected static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
