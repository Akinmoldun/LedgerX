# LedgerX — 2-Minute Demo Video Recording Script

This script provides an exact, top-to-bottom walkthrough to record a 2-minute product demonstration of LedgerX.
All commands use the live Fly.io deployment (`https://ledgerx.fly.dev`). You can also substitute `http://localhost:8080` if demonstrating locally.

---

## Preparation & Screen Setup

1. **Terminal:** Open a clean terminal window with high-contrast text and `curl` + `jq` installed.
2. **Browser:** Open two tabs:
   - Tab 1: [https://ledgerx.fly.dev/swagger-ui.html](https://ledgerx.fly.dev/swagger-ui.html)
   - Tab 2: [https://ledgerx.fly.dev/actuator/prometheus](https://ledgerx.fly.dev/actuator/prometheus)
3. **Environment Setup:** Run in your terminal before recording to store common variables:
   ```bash
   export BASE_URL="https://ledgerx.fly.dev"
   export TENANT_ID="00000000-0000-0000-0000-000000000001"
   ```

---

## Timeline & Step-by-Step Script

```
0:00 - 0:20  Introduction & Architecture Overview
0:20 - 0:45  Double-Entry Transfer (201 Created & Balanced Invariant)
0:45 - 1:05  Idempotency & Status Verification
1:05 - 1:25  Audit Trail & Append-Only Log
1:25 - 1:45  Kafka Outage Resilience & Transactional Outbox
1:45 - 2:00  Balance Reconciliation Engine & Wrap-up
```

---

### Step 1: Introduction & Live Swagger UI (0:00 – 0:20)
**Action:** Show the browser tab with Swagger UI at `https://ledgerx.fly.dev/swagger-ui.html`.  
**Voiceover:** *"Welcome to LedgerX. This is a production-grade double-entry financial ledger built in Java 21 and Spring Boot 3.4. It guarantees exact-once execution using deterministic pessimistic locking, a transactional outbox, and automated balance reconciliation."*

---

### Step 2: Fetch Active Accounts (0:20 – 0:30)
**Action:** Switch to the terminal and query the pre-seeded tenant accounts:

```bash
curl -s -X GET "$BASE_URL/api/v1/accounts" \
  -H "X-Tenant-Id: $TENANT_ID" | jq .
```

**Expected Output:**
```json
[
  {
    "id": "11111111-1111-1111-1111-111111111111",
    "code": "1000",
    "name": "Operating Checking",
    "type": "asset",
    "currency": "USD",
    "balanceMinor": 100000
  },
  {
    "id": "22222222-2222-2222-2222-222222222222",
    "code": "4000",
    "name": "Merchant Services Revenue",
    "type": "revenue",
    "currency": "USD",
    "balanceMinor": 0
  }
]
```

*(Note: Save the two IDs into your shell variables)*:
```bash
export FROM_ID="11111111-1111-1111-1111-111111111111"
export TO_ID="22222222-2222-2222-2222-222222222222"
export IDEMPOTENCY_KEY="demo-$(uuidgen | cut -c1-8)"
```

---

### Step 3: Post a Double-Entry Transfer (0:30 – 0:50)
**Action:** Post a $150.00 (`15000` minor units) transfer:

```bash
curl -i -s -X POST "$BASE_URL/api/v1/transfers" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: $TENANT_ID" \
  -H "Idempotency-Key: $IDEMPOTENCY_KEY" \
  -d "{
    \"fromAccountId\": \"$FROM_ID\",
    \"toAccountId\": \"$TO_ID\",
    \"amountMinor\": 15000,
    \"currency\": \"USD\",
    \"description\": \"Demo wire transfer\"
  }"
```

**Voiceover:** *"We execute a \$150 transfer. In a single ACID transaction, LedgerX acquires ordered pessimistic locks to prevent deadlocks, verifies available funds, creates equal debit and credit entries enforced by PostgreSQL triggers, records an append-only event, and writes to the transactional outbox table."*

**Expected Output:**
```http
HTTP/2 201 
content-type: application/json
x-request-id: 3c9b88a1-...

{
  "id": "...",
  "fromAccountId": "11111111-1111-1111-1111-111111111111",
  "toAccountId": "22222222-2222-2222-2222-222222222222",
  "amountMinor": 15000,
  "currency": "USD",
  "status": "posted",
  "description": "Demo wire transfer",
  "createdAt": "..."
}
```

---

### Step 4: Demonstrate Idempotency Replay (0:50 – 1:05)
**Action:** Re-send the exact same curl command with the same `Idempotency-Key`:

```bash
curl -i -s -X POST "$BASE_URL/api/v1/transfers" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: $TENANT_ID" \
  -H "Idempotency-Key: $IDEMPOTENCY_KEY" \
  -d "{
    \"fromAccountId\": \"$FROM_ID\",
    \"toAccountId\": \"$TO_ID\",
    \"amountMinor\": 15000,
    \"currency\": \"USD\",
    \"description\": \"Demo wire transfer\"
  }"
```

**Voiceover:** *"If a network timeout causes a retry, LedgerX catches the duplicate key at the database constraint layer and replays the original transfer with HTTP 200 without double-spending or creating duplicate ledger entries."*

**Expected Output:** HTTP `200 OK` with identical transfer payload.

---

### Step 5: Verify Audit Trail (1:05 – 1:25)
**Action:** Retrieve the paginated audit trail for this transfer:

```bash
curl -s -X GET "$BASE_URL/api/v1/audit?entityType=TRANSFER&limit=5" \
  -H "X-Tenant-Id: $TENANT_ID" | jq .
```

**Voiceover:** *"Every state modification generates an immutable audit record capturing actor, before/after snapshots, action name, and correlation request IDs with base64 cursor pagination."*

---

### Step 6: Transactional Outbox & Resilience (1:25 – 1:45)
**Action:** Show the Prometheus metrics endpoint or explain the outbox relay:

```bash
curl -s "$BASE_URL/actuator/prometheus" | grep -E "ledgerx_outbox|ledgerx_transfer"
```

**Voiceover:** *"In payments, dual-write is fatal. Publishing directly to Kafka inside a database transaction risks committing without publishing, or publishing without committing. LedgerX solves this with the Transactional Outbox Pattern: the event is stored in the database transaction, and a background relay dispatches it to Kafka with `SKIP LOCKED` and durable exponential backoff. Even if Kafka goes down completely, customer transfers continue to succeed, and events drain automatically upon recovery."*

---

### Step 7: Automated Balance Reconciliation (1:45 – 2:00)
**Action:** Trigger the balance reconciliation endpoint:

```bash
curl -s -X POST "$BASE_URL/api/v1/admin/reconcile" \
  -H "X-Tenant-Id: $TENANT_ID" | jq .
```

**Expected Output:**
```json
{
  "accountsChecked": 2,
  "mismatches": 0,
  "mismatchAccountIds": []
}
```

**Voiceover:** *"Finally, the automated reconciliation engine computes account balances from the sum of all immutable entries and verifies them against cached balances. Zero mismatches found. LedgerX provides ironclad financial integrity from the ground up."*

---

## Checkpoint Checklist for the Recording

- [ ] Clear audio with voiceover explaining *why* (not just what).
- [ ] Terminal font size at 16pt+ for readability in 1080p/4K video.
- [ ] Shows HTTP `201 Created` $\rightarrow$ HTTP `200 OK` on idempotency replay.
- [ ] Shows `/actuator/health` or `/actuator/prometheus` metrics.
- [ ] Shows `reconcile` returning `"mismatches": 0`.
