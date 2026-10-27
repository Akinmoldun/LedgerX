package com.ledgerx.common;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Read-only projection of the tenants table. Rows are never created or updated by the app. */
@Entity
@Table(name = "tenants")
public class Tenant {

    @Id
    private UUID id;

    private String name;

    private Instant createdAt;

    protected Tenant() {
        // JPA
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
