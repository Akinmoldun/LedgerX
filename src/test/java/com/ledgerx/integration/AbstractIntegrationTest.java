package com.ledgerx.integration;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for every integration test. Boots the real app against a real
 * Postgres 16 (Testcontainers); integration tests are skipped cleanly when
 * Docker is unavailable so `mvn verify` stays green everywhere — on a Docker
 * machine they always run (§12).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@ContextConfiguration(initializers = AbstractIntegrationTest.Initializer.class)
@Testcontainers(disabledWithoutDocker = true)
public abstract class AbstractIntegrationTest {

    public static final String DEFAULT_TENANT = "00000000-0000-0000-0000-000000000001";

    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        if (dockerAvailable()) {
            POSTGRES.start();
        }
    }

    public static boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Autowired
    protected TestRestTemplate rest;

    @Autowired
    protected JdbcTemplate jdbc;

    /** Inserts an asset account under the default tenant; rows only exist for this test. */
    protected UUID account(String code, long balanceMinor) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO accounts (id, tenant_id, code, name, type, currency, balance_minor)
                VALUES (?::uuid, ?::uuid, ?, ?, 'asset', 'USD', ?)
                """, id.toString(), DEFAULT_TENANT, code + "-" + suffix(), "IT Account " + code, balanceMinor);
        return id;
    }

    protected long balance(UUID accountId) {
        return jdbc.queryForObject(
                "SELECT balance_minor FROM accounts WHERE id = ?::uuid", Long.class, accountId.toString());
    }

    protected static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /** @see SpringBootTest */
    public static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext context) {
            if (!dockerAvailable()) {
                return;
            }
            TestPropertyValues.of(
                    "spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                    "spring.datasource.username=" + POSTGRES.getUsername(),
                    "spring.datasource.password=" + POSTGRES.getPassword())
                    .applyTo(context.getEnvironment());
        }
    }
}
