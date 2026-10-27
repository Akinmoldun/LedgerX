package com.ledgerx.account;

import com.ledgerx.common.TenantRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final AccountRepository accounts;
    private final TenantRepository tenants;

    public AccountService(AccountRepository accounts, TenantRepository tenants) {
        this.accounts = accounts;
        this.tenants = tenants;
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> list(UUID tenantId) {
        requireTenant(tenantId);
        return accounts.findAllOfTenant(tenantId).stream().map(AccountResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse get(UUID tenantId, UUID accountId) {
        requireTenant(tenantId);
        Account account = accounts.findByIdAndTenantId(accountId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("account not found: " + accountId));
        return AccountResponse.from(account);
    }

    private void requireTenant(UUID tenantId) {
        if (!tenants.existsById(tenantId)) {
            throw new EntityNotFoundException("unknown tenant: " + tenantId);
        }
    }
}
