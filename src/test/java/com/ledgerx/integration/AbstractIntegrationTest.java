package com.ledgerx.integration;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
public abstract class AbstractIntegrationTest {

    public static final String DEFAULT_TENANT = "00000000-0000-0000-0000-000000000001";

    @Container
    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

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
