# LedgerX - 2-Minute Demo Video Recording Script

This script provides an exact, top-to-bottom walkthrough to record a 2-minute product demonstration of LedgerX.
All commands use the live Fly.io deployment (`https://ledgerx.fly.dev`). You can also substitute `http://localhost:8080` if demonstrating locally.

---

## Preparation and Screen Setup

1. **Terminal:** Open a clean terminal window with high-contrast text and `curl` + `jq` installed.
2. **Browser:** Open two tabs:
   - Tab 1: [https://ledgerx.fly.dev/swagger-ui.html](https://ledgerx.fly.dev/swagger-ui.html)
   - Tab 2: [https://ledgerx.fly.dev/actuator/prometheus](https://ledgerx.fly.dev/actuator/prometheus)
3. **Environment Setup:** Run in your terminal before recording to store common variables:
   ```bash
   export BASE_URL="https://ledgerx.fly.dev"
   export TENANT_ID="00000000-0000-0000-0000-000000000001"
   export ADMIN_KEY="ledgerx-admin-dev-key"
   ```

---

## Timeline and Step-by-Step Script

```
0:00 - 0:20  Introduction & Live Swagger UI
0:20 - 0:35  Inspect Chart of Accounts
0:35 - 0:55  Post Double-Entry Transfer (201 Created)
0:55 - 1:10  Get Transfer Status & Idempotency Replay (200 OK)
1:10 - 1:30  Verify Audit Trail & Append-Only Log
1:30 - 1:45  Kafka Outage Resilience & Transactional Outbox
1:45 - 2:00  Balance Reconciliation Engine & Wrap-up
```

---

### Step 1: Introduction and Live Swagger UI (0:00 - 0:20)
**Action:** Show the browser tab with Swagger UI at `https://ledgerx.fly.dev/swagger-ui.html`.  
**Voiceover:** *"Welcome to LedgerX. This is a production-grade double-entry financial ledger built in Java 21 and Spring Boot 3.4. It guarantees exact-once execution using deterministic pessimistic locking, a transactional outbox, append-only audit logging, and automated balance reconciliation."*

---

### Step 2: Fetch Active Accounts (0:20 - 0:35)
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
    "name": "Demo Operating Account",
    "type": "asset",
    "currency": "USD",
    "balanceMinor": 100000,
    "createdAt": "2026-11-04T20:51:58Z"
  },
  {
    "id": "22222222-2222-2222-2222-222222222222",
    "code": "4000",
    "name": "Demo Merchant Revenue",
    "type": "revenue",
    "currency": "USD",
    "balanceMinor": 0,
    "createdAt": "2026-11-04T20:51:58Z"
  },
  {
    "id": "33333333-3333-3333-3333-333333333333",
    "code": "3000",
    "name": "Demo Equity Funding",
    "type": "equity",
    "currency": "USD",
    "balanceMinor": -100000,
    "createdAt": "2026-11-04T20:51:58Z"
  }
]
```

*(Save the operational account IDs into your shell variables)*:
```bash
export FROM_ID="11111111-1111-1111-1111-111111111111"
export TO_ID="22222222-2222-2222-2222-222222222222"
export IDEMPOTENCY_KEY="demo-$(uuidgen | cut -c1-8)"
```

---

### Step 3: Post a Double-Entry Transfer (0:35 - 0:55)
**Action:** Post a $150.00 (`15000` minor units) transfer from the operating account to merchant revenue:

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
location: /api/v1/transfers/a7f34e62-2345-4b51-9e8a-129087612345

{
  "id": "a7f34e62-2345-4b51-9e8a-129087612345",
  "tenantId": "00000000-0000-0000-0000-000000000001",
  "status": "posted",
  "currency": "USD",
  "amountMinor": 15000,
  "description": "Demo wire transfer",
  "createdAt": "2026-11-04T22:25:00Z",
  "postedAt": "2026-11-04T22:25:00Z"
}
```

*(Export the returned transfer ID)*:
```bash
export TRANSFER_ID="a7f34e62-2345-4b51-9e8a-129087612345"
```

---

### Step 4: Get Transfer Status and Idempotency Replay (0:55 - 1:10)
**Action 1 (GET Transfer):** Inspect the posted transfer by its ID:

```bash
curl -s -X GET "$BASE_URL/api/v1/transfers/$TRANSFER_ID" \
  -H "X-Tenant-Id: $TENANT_ID" | jq .
```

**Expected Output:**
```json
{
  "id": "a7f34e62-2345-4b51-9e8a-129087612345",
  "tenantId": "00000000-0000-0000-0000-000000000001",
  "status": "posted",
  "currency": "USD",
  "amountMinor": 15000,
  "description": "Demo wire transfer",
  "createdAt": "2026-11-04T22:25:00Z",
  "postedAt": "2026-11-04T22:25:00Z"
}
```

**Action 2 (Idempotency Replay):** Re-send the exact same POST request with the identical `Idempotency-Key`:

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

**Voiceover:** *"If a network timeout causes a client retry, LedgerX catches the duplicate key at the database constraint layer and replays the original transfer with HTTP 200 without double-spending or creating duplicate ledger entries."*

**Expected Output:** HTTP `200 OK` with identical transfer payload.

---

### Step 5: Verify Audit Trail (1:10 - 1:30)
**Action:** Retrieve the paginated audit trail for this transfer:

```bash
curl -s -X GET "$BASE_URL/api/v1/audit?entityType=TRANSFER&limit=5" \
  -H "X-Tenant-Id: $TENANT_ID" | jq .
```

**Expected Output:**
```json
{
  "items": [
    {
      "id": 1,
      "tenantId": "00000000-0000-0000-0000-000000000001",
      "actorId": null,
      "action": "TRANSFER_POSTED",
      "entityType": "TRANSFER",
      "entityId": "a7f34e62-2345-4b51-9e8a-129087612345",
      "before": null,
      "after": "{\"status\":\"posted\",\"currency\":\"USD\",\"amountMinor\":15000}",
      "reason": "posted with idempotency key demo-...",
      "requestId": "3c9b88a1-...",
      "ip": null,
      "createdAt": "2026-11-04T22:25:00Z"
    }
  ],
  "nextCursor": null,
  "hasMore": false
}
```

**Voiceover:** *"Every state modification generates an immutable audit record capturing actor, before/after snapshots, action name, and correlation request IDs with base64 cursor pagination."*

---

### Step 6: Transactional Outbox and Kafka Resilience (1:30 - 1:45)
**Action:** Query Prometheus metrics to inspect outbox processing:

```bash
curl -s "$BASE_URL/actuator/prometheus" | grep -E "ledgerx_outbox|ledgerx_transfer"
```

**Voiceover:** *"In payments, dual-write is fatal. Publishing directly to Kafka inside the transfer transaction risks committing without publishing, or publishing without committing. LedgerX solves this with the Transactional Outbox Pattern: Kafka is never called inside the transfer transaction. Instead, events are stored in PostgreSQL and a background relay dispatches them to Kafka using SKIP LOCKED and durable exponential backoff. Even if Kafka goes down completely, customer transfers continue to succeed, and events drain automatically upon broker recovery."*

---

### Step 7: Automated Balance Reconciliation (1:45 - 2:00)
**Action:** Trigger the balance reconciliation endpoint using the secure admin key:

```bash
curl -s -X POST "$BASE_URL/api/v1/admin/reconcile" \
  -H "X-Tenant-Id: $TENANT_ID" \
  -H "X-Admin-Key: $ADMIN_KEY" | jq .
```

**Expected Output:**
```json
{
  "checked": 3,
  "mismatches": []
}
```

**Voiceover:** *"Finally, the automated reconciliation engine computes account balances from the sum of all immutable entries and verifies them against cached balances. Zero mismatches found. LedgerX provides ironclad financial integrity from the ground up."*

---

## Checkpoint Checklist for the Recording

- [ ] Clear audio with voiceover explaining *why* (not just what).
- [ ] Terminal font size at 16pt+ for readability in 1080p/4K video.
- [ ] Shows HTTP `201 Created` -> HTTP `200 OK` on idempotency replay.
- [ ] Shows `GET /api/v1/transfers/$TRANSFER_ID` returning `status='posted'`.
- [ ] Shows `/actuator/health` or `/actuator/prometheus` metrics.
- [ ] Shows `reconcile` returning 0 mismatches (`"mismatches": []`).
