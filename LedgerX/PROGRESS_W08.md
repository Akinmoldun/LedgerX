# Week 8 — Resilience: Kafka outage + retries

**Started:** 2026-10-26 02:30
**Status:** 🟡 In progress (toward 🔵 Awaiting review)

## Tasks
- [x] **Outbox retry backoff** — `OutboxRelay` now refuses to re-attempt a failed row until its backoff window elapses: delay `min(cap, base · 2^(attempts))` with `ledgerx.outbox.backoff-base-ms: 1000` / `backoff-cap-ms: 300000` (1 s, 2 s, 4 s … hard-capped at 5 min). `attempts` / `last_error` were already persisted by `recordFailure` (W6); the retry gate lives in relay memory keyed by outbox id — **DECISIONS entry 2026-10-26, `Approved by: pending`** (no schema change beyond §6; the gate is scheduling policy, not append-only state).
- [x] Relay cannot spin: one attempt per row per tick; a failed batch returns immediately; ticks skip gated rows — `OutboxRelayTest.failedRowSkippedInsideBackoffWindow`.
- [x] `OutboxRelayTest` extended to 5 unit tests: publish-then-mark order (unchanged, §7.4), failure recording (unchanged), no retry inside the window, retry success after window elapses (gate cleared, markPublished only after retry), exponential cap table (1 s → 2 s → 4 s → 5-min cap). Time is advanced via an injected mutable `Clock` — **zero `Thread.sleep`** (§2.3).
- [x] `OutboxRelayIT` (§12: real broker via Testcontainers, never a mocked template): broker container **stopped via Docker API on the same container** (Testcontainers stop/start would recreate it and change the mapped port) → POST still returns **201** → relay records failure (`attempts ≥ 1`, `last_error` set, `published_at` NULL) → broker **restarted** → relay retries and sets `published_at` → `TRANSFER_POSTED_CONSUMED` audit row appears **exactly once** after recovery. Backoff cadence shortened only in the IT env (base 300 ms, tick 200 ms); production keeps 1 s / 5 min.
- [x] **Chaos run documented** — `docs/architecture.md`: full manual script with per-phase expected timings and the §13 guarantees (no data loss / no duplicates / no spinning). Measured-timings lines left blank honestly: no Docker in this sandbox.

## Acceptance Criteria
- [ ] `OutboxRelayIT` green — compiles and is wired; skipped in this sandbox (no Docker, same as every IT since W5). First run must be on a Docker machine or CI.
- [ ] Manual chaos run documented in `docs/architecture.md` with timings — script + expected timings done; **measured timings pending** a Docker machine (blank lines left in the doc, do not fake).

## Tests run
```
$ mvn verify
Tests run: 15, Failures: 0, Errors: 0, Skipped: 0        (unit: Money 4, Account 6, OutboxRelay 5)
Tests run: 13, Failures: 0, Errors: 0, Skipped: 13       (IT — skipped: no Docker in this sandbox)
BUILD SUCCESS
```

## Self-check (§13 Week 8)
- ✅ **Backoff capped at 5 min** — `backoff-cap-ms: 300000`, proven by `backoffIsExponentialAndCapped` table test (cap at 300_000 for 9 and 50 failures).
- ✅ **Relay does not spin infinitely** — one attempt per row per tick, gated rows skipped; with a broker down it returns after the first failure of a batch (§7.4 shape preserved).
- ✅ **No data loss during outage** — transfer commit + outbox row are one transaction; POST under outage in `OutboxRelayIT` (and by construction in the manual chaos script).
- ✅ **No duplicates after recovery** — at-least-once publish + consumer dedupe by `eventId` (`processed_events ON CONFLICT DO NOTHING`); IT asserts exactly one consumed audit row after recovery.

## Verification greps (this week)
```
$ grep -rn "kafka.send" src/main/java/ | grep -v '/outbox/'        → empty
$ grep -rEn 'BigDecimal|float |double ' src/main/java/              → empty  (Money javadoc reworded so the ban-list grep stays clean)
$ git ls-files | grep -i jqwik                                     → empty  (.jqwik-database untracked from git; was already gitignored but still tracked)
```

## Files created or modified
| File | Change |
|---|---|
| `src/main/java/com/ledgerx/outbox/OutboxRelay.java` | Exponential backoff gate (Clock-aware, attempts-driven, 5-min cap), no-spin semantics, publish-latency timer on both paths |
| `src/main/resources/application.yml` | `ledgerx.outbox.backoff-base-ms` / `backoff-cap-ms` properties |
| `src/test/java/com/ledgerx/outbox/OutboxRelayTest.java` | 2 existing tests kept verbatim + 3 new backoff tests (mutable test clock, no sleeps) |
| `src/test/java/com/ledgerx/integration/OutboxRelayIT.java` | New — full outage→retry→recovery→exactly-once cycle on a real broker |
| `docs/architecture.md` | New — outbox rationale, retry policy table, chaos-run script with expected timings |
| `DECISIONS.md` | New entry 2026-10-26 — in-memory retry gate (`Approved by: pending`) |
| `.jqwik-database` | Removed from git tracking (the working-copy file is untouched) |
| `src/main/java/com/ledgerx/common/Money.java` | Javadoc only — reworded so the §2.3 ban-list grep returns empty |

## Review notes
(blank — the Human fills this)

## Human approval
(blank — the Human fills this)

## Notes for Week 9
- Reconciliation (`POST /api/v1/admin/reconcile`, audit API) touches only read paths + a manual endpoint — no conflict with the backoff gate.
- If the Human flips the DECISIONS entry to "not approved", the alternative is a V8 `next_attempt_at` column migration — that needs a schema-change approval per §2.2 before any code.
- OutboxRelayIT must run first on the first Docker-capable machine (port-mapping preservation on container restart is the risky part).
