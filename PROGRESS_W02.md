# Week 2 — Accounts domain

**Started:** 2026-10-25 00:16
**Status:** 🔵 Awaiting review

## Tasks
- [x] V2 migration (`V2__accounts.sql`) — `account_type` enum, `accounts` table with `(tenant_id, code)` unique, `balance_minor >= 0` check, tenant index — spec-exact
- [x] `Account` entity — `debit/credit` intent methods only; **no setters on financial fields** (`balanceMinor`, `currency`, `version`); `@Version` optimistic lock; Postgres enum mapped via `@JdbcTypeCode(SqlTypes.NAMED_ENUM)`
- [x] `AccountRepository` — `findAllOfTenant` (explicit columns), `findByIdAndTenantId`, **`lockByIdsOrdered`** = `ORDER BY id FOR UPDATE` with explicit columns (P6)
- [x] `AccountService` — `@Transactional` boundary; validates `X-Tenant-Id` against DB via `TenantRepository` (`com.ledgerx.common.Tenant` read-only projection)
- [x] `AccountController` (GET `/api/v1/accounts`, GET `/api/v1/accounts/{id}`) — Swagger `@Tag`/`@Operation`, DTO records, **no `@Transactional` on controller**
- [x] springdoc-openapi-starter-webmvc-ui **2.6.0** added (inside §4.2 list, no DECISIONS entry needed)
- [x] `unit/MoneyTest` (4 tests) + `com.ledgerx.account/AccountTest` (6 tests) — placed in the account package so the protected JPA constructor is reachable without weakening it

## Acceptance Criteria
- [ ] Create tenant + 2 accounts via SQL; GET returns them — **not verifiable in this sandbox** (no Docker/DB; seed + curl per §18 on your machine)
- [x] Both tests green — `mvn verify` ✅
- [ ] Swagger shows both endpoints — **not verifiable here** (needs running app); springdoc is wired at `/v3/api-docs` + `/swagger-ui.html` and both endpoints carry OpenAPI annotations

## Tests run
```
$ mvn verify
Tests run: 4  -- in com.ledgerx.unit.MoneyTest
Tests run: 6  -- in com.ledgerx.account.AccountTest
Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Self-check (§13 Week 2)
- ✅ No BigDecimal anywhere (`src/main` grep clean — only a javadoc line *mentioning* the ban)
- ✅ No setters on `Account` financial fields (enforced + tested by `noFinancialSetters` test)
- ✅ `lockByIdsOrdered` uses `ORDER BY id FOR UPDATE`
- ✅ DTOs are records
- ✅ No `@Transactional` on controller
- ✅ No `SELECT *` in repositories (grep clean — one javadoc mention)

## Limitations (sandbox)
Same as Week 1: no Docker daemon → the three DB/Swagger-dependent ACs need manual verification on your machine (`cd docker && docker compose up -d; mvn spring-boot:run -Dspring-boot.run.profiles=local`, then seed per §18). Compile + full unit-test gate ran here.

## Review notes
<left blank — the Human fills this>

## Human approval
<left blank — the Human fills this>

## Notes for Week 3
- `ApiError` + `GlobalExceptionHandler` arrive Week 3; controller currently uses a local `EntityNotFoundException` handler + `ResponseStatusException` — superseded next week.
- Account is never inserted by app code yet (Week 3 inserts transfers/entries only); `@GeneratedValue(UUID)` present for later inserts.
- `lockByIdsOrdered` is wired but unused until transfers — exercised by ConcurrencyIT in Week 4.
