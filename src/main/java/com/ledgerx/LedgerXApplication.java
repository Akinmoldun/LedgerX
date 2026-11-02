package com.ledgerx;

import java.net.URI;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class LedgerXApplication {

    public static void main(String[] args) {
        normalizeDatabaseUrl();
        SpringApplication.run(LedgerXApplication.class, args);
    }

    /**
     * Normalizes standard cloud database URIs (e.g. Neon, Supabase, Fly postgresql://user:pass@host/db)
     * into JDBC-compatible format (jdbc:postgresql://host:port/db) with credentials extracted.
     */
    static void normalizeDatabaseUrl() {
        String raw = System.getenv("LEDGERX_DB_URL");
        if (raw == null || raw.isBlank()) {
            raw = System.getenv("DB_URL");
        }
        if (raw == null || raw.isBlank()) {
            raw = System.getenv("DATABASE_URL");
        }
        if (raw == null || raw.isBlank()) {
            return;
        }

        if (raw.startsWith("postgres://") || raw.startsWith("postgresql://")) {
            try {
                URI uri = URI.create(raw.replaceFirst("^postgres://", "postgresql://"));
                String userInfo = uri.getUserInfo();
                if (userInfo != null) {
                    String[] parts = userInfo.split(":", 2);
                    if (System.getProperty("spring.datasource.username") == null
                            && System.getenv("DB_USER") == null
                            && System.getenv("LEDGERX_DB_USER") == null) {
                        System.setProperty("spring.datasource.username", parts[0]);
                    }
                    if (parts.length > 1
                            && System.getProperty("spring.datasource.password") == null
                            && System.getenv("DB_PASSWORD") == null
                            && System.getenv("LEDGERX_DB_PASSWORD") == null) {
                        System.setProperty("spring.datasource.password", parts[1]);
                    }
                }
                int port = uri.getPort() > 0 ? uri.getPort() : 5432;
                String path = uri.getPath() != null ? uri.getPath() : "/ledgerx";
                String query = uri.getQuery() != null ? "?" + uri.getQuery() : "";
                String jdbcUrl = "jdbc:postgresql://" + uri.getHost() + ":" + port + path + query;
                System.setProperty("spring.datasource.url", jdbcUrl);
            } catch (Exception e) {
                String fallback = raw.startsWith("jdbc:") ? raw : "jdbc:" + raw;
                System.setProperty("spring.datasource.url", fallback);
            }
        }
    }
}
