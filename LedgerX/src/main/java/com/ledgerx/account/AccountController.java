package com.ledgerx.account;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Account read endpoints. Controllers do validation, header extraction, HTTP
 * status and DTO mapping only — no business logic, no {@code @Transactional}
 * (§7.1).
 */
@RestController
@Tag(name = "Accounts", description = "Chart-of-accounts read endpoints")
public class AccountController {

    private final AccountService accounts;

    public AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping("/api/v1/accounts")
    @Operation(summary = "List accounts (tenant scoped)")
    public List<AccountResponse> list(
            @RequestHeader("X-Tenant-Id")
            @Parameter(description = "Tenant id", required = true) String tenantId) {
        return accounts.list(parseTenantId(tenantId));
    }

    @GetMapping("/api/v1/accounts/{id}")
    @Operation(summary = "Get one account")
    public AccountResponse get(
            @RequestHeader("X-Tenant-Id")
            @Parameter(description = "Tenant id", required = true) String tenantId,
            @PathVariable UUID id) {
        return accounts.get(parseTenantId(tenantId), id);
    }

    private UUID parseTenantId(String tenantId) {
        try {
            return UUID.fromString(tenantId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Tenant-Id must be a UUID");
        }
    }
}
