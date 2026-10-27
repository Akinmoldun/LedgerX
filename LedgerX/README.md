# LedgerX

Double-entry ledger API with a transactional outbox, append-only event log, idempotent transfers, and reconciliation.

**Java 21 · Spring Boot 3.4 · PostgreSQL 16 · Kafka (KRaft) · Flyway**

## Quickstart

### 1. Start the infrastructure

```bash
cd docker
docker compose up -d
docker compose ps        # all services healthy
cd ..
```

| Service | URL | Credentials |
|---|---|---|
| PostgreSQL 16 | `localhost:5432/ledgerx` | `ledgerx` / `ledgerx` |
| Kafka (KRaft) | `localhost:9092` | — |
| Schema Registry | `http://localhost:8081` | — |
| Prometheus | `http://localhost:9099` | — |
| Grafana | `http://localhost:3000` | `admin` / `admin` |

### 2. Run the app

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### 3. Verify

```bash
curl http://localhost:8080/actuator/health   # → {"status":"UP"}
curl http://localhost:8080/actuator/prometheus | head -30
# Swagger: http://localhost:8080/swagger-ui.html
```

## Tests

```bash
mvn verify                     # unit + integration
mvn -q -DskipTests compile     # compile only
```

Integration tests use Testcontainers and require only Docker.

## Status

Development follows the 12-week plan in `docs/LedgerX_BUILD_SPEC.md`. Current state: **Week 1 — awaiting review.** See `PROGRESS.md` and `PROGRESS_W01.md`.

## Known limitations

- No authentication yet (OAuth/OIDC is a later project).
- Single currency per transfer; no reversals API yet.
- No frontend.
