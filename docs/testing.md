# Testing Strategy

## 1. Goal

Testing must verify both business correctness and reliability behavior.

The most important question is not only:

```text
Does the endpoint return 200?
```

but also:

```text
What happens when consumers fail, time out, retry, or workers run concurrently?
```

---

# 2. Testing Layers

Use:

1. unit tests
2. repository/integration tests
3. API integration tests
4. worker integration tests
5. end-to-end tests
6. failure tests
7. optional load tests

---

# 3. Unit Tests

## Retry Policy

Test:

```text
2xx -> success
400 -> terminal
401 -> terminal
408 -> retry
429 -> retry
500 -> retry
503 -> retry
timeout -> retry
```

Test backoff calculations.

## HMAC Signing

Given:

```text
secret
timestamp
raw body
```

the signature must be deterministic.

Changing any signed input must change verification result.

M9 verifies an independent known HMAC-SHA256 vector, exact transmitted body bytes, required signing headers, fresh retry signatures, endpoint-secret isolation, AES-GCM tamper rejection, V1-to-V9 migration, and terminal no-send behavior for missing signing material.

## State Transitions

Test allowed transitions:

```text
PENDING -> PROCESSING
PROCESSING -> DELIVERED
PROCESSING -> RETRY_SCHEDULED
PROCESSING -> FAILED
RETRY_SCHEDULED -> PROCESSING
```

Invalid transitions should be rejected or prevented.

---

# 4. Repository and Database Integration Tests

Test:

- application persistence
- API key metadata persistence
- unique application/source event idempotency
- unique endpoint/event subscription
- event JSONB persistence
- delivery persistence
- attempt ordering
- migration correctness

M2 additionally verifies the V1-to-V2 migration on PostgreSQL, Hibernate schema validation, owner/slug uniqueness, owner and Application foreign keys, status/environment checks, unique key hashes, revocation consistency, and restricted deletes.

M3 additionally verifies V1-to-V3 migration and Hibernate validation, Application-scoped endpoint/subscription ownership, endpoint status updates preserving subscriptions, exact subscription uniqueness, local versus hosted URL validation, CSRF protection, and cross-user `404` behavior.

M4 additionally verifies V1-to-V4 migration and Hibernate validation, API-key-only producer authentication, generic invalid-credential responses, disabled Application/revoked-key rejection, stateless separation from dashboard sessions, JSONB event persistence, 1 MiB request enforcement with and without `Content-Length`, idempotent replay/conflict behavior, and concurrent duplicate ingestion producing one row.

Important constraint test:

```text
UNIQUE(application_id, source_event_id)
```

---

# 5. Event Ingestion Integration Tests

## Scenario: New Event

Given:

- valid producer API key
- application exists
- two active subscribed endpoints

When:

```http
POST /api/v1/events
```

Then:

- one event is persisted
- two deliveries are persisted
- response returns before consumers are contacted
- status is accepted

## Scenario: Duplicate Event

Given existing:

```text
application_id = app_01
source_event_id = source_123
```

When the producer submits the same event again:

Then:

- no second event is created
- no duplicate automatic deliveries are created
- existing event is returned

---

# 6. Worker Integration Tests

Use a mock HTTP server.

Test:

## Immediate Success

Consumer:

```text
200
```

Expected:

```text
1 attempt
DELIVERED
```

## Temporary Failure

Consumer behavior:

```text
500
500
200
```

Expected:

```text
3 attempts
final DELIVERED
```

## Permanent Retryable Failure

Consumer:

```text
503
503
503
503
503
```

Expected:

```text
5 attempts
FAILED
```

## Terminal Client Error

Consumer:

```text
400
```

Expected:

```text
1 attempt
FAILED
no automatic retry
```

## Timeout

Consumer intentionally delays beyond client timeout.

Expected:

```text
attempt records timeout
delivery RETRY_SCHEDULED or FAILED according to budget
```

---

# 7. Concurrency Tests

Critical test:

Run multiple workers against the same pending delivery set.

Expected:

- each delivery is normally claimed by one worker
- no normal concurrent duplicate processing
- all deliveries eventually leave claimable state

Test the database claim mechanism used by the implementation.

---

# 8. Crash Recovery Scenarios

Where practical, test:

## Stale Processing Recovery

Given:

```text
delivery = PROCESSING
processing_started_at is stale
```

When recovery logic runs:

Then the delivery becomes eligible for processing again according to documented rules.

If stale-processing recovery is not implemented in the first milestone, record it as an explicit limitation.

---

# 9. Manual Retry Tests

Given:

```text
delivery = FAILED
```

When developer manually retries:

Then:

- previous attempts remain
- one new attempt is allowed according to retry policy
- attempt numbering continues
- result is recorded

---

# 10. API Security Tests

Dashboard authentication tests include:

- first Google login creates one local user
- repeat login reuses the Google subject mapping
- profile claims synchronize without email-based account merging
- missing or unverified identity claims fail
- `/api/v1/auth/me` returns safe local fields or `401`
- logout requires CSRF and invalidates the session
- logout expires the `WEBHOOK_SESSION` cookie
- OAuth success removes the authorized Google client while preserving the local authenticated session
- fixed OAuth redirects ignore user-controlled redirect parameters
- configured CORS accepts the trusted origin and rejects unrelated origins
- production configuration keeps the host-only session cookie `Secure` with `SameSite=Lax`
- `/healthz` remains public

M2 dashboard API tests additionally include:

- authenticated and CSRF-protected Application/API-key mutations
- owner-scoped Application create, list, detail, and patch
- same-owner slug conflict and real concurrent collision handling
- cross-user Application and API-key operations returning `404`
- 32-byte `SecureRandom` credentials with environment markers
- raw keys returned once and never persisted or listed
- SHA-256 digest and safe-prefix persistence
- irreversible, idempotent revocation
- `last_used_at` remaining null until producer authentication exists

Use mocked OIDC identities and PostgreSQL integration tests. Automated tests must not call Google.

Producer API security tests, when that later milestone is implemented, include:

Test:

- missing API key -> 401
- invalid API key -> 401
- revoked API key -> 401
- valid API key -> accepted
- producer cannot impersonate another application
- raw key never appears in list endpoints

---

# 11. HMAC Tests

Given known vectors:

```text
secret
timestamp
body
```

test:

- expected signature
- modified body
- modified timestamp
- wrong secret
- constant-time comparison path where practical

---

# 12. Frontend Tests

Important dashboard flows:

- Overview loads metrics
- endpoint list filters correctly
- event details show deliveries
- delivery details show attempts
- failed delivery exposes retry action
- loading state
- empty state
- error state
- retrying state
- desktop layout at 1440px
- desktop layout at 1280px
- no page-level horizontal overflow

`DESIGN.md` remains the source of truth for visual behavior.

---

# 13. End-to-End Demo Scenario

The main portfolio demo should use:

Application:

```text
AI Study Assistant
```

Event:

```text
ai.solution.completed
```

Three endpoints:

```text
AI Analytics
Discord Notifications
Quality Monitor
```

Behavior:

```text
AI Analytics
-> 200
-> DELIVERED

Discord Notifications
-> 500
-> 500
-> 200
-> DELIVERED

Quality Monitor
-> repeated 503
-> FAILED
```

The dashboard should show the complete event -> delivery -> attempt relationship.

---

# 14. Load Testing

Load testing is not required for initial MVP completion.

After core behavior is correct, measure:

- event ingestion requests/second
- delivery throughput
- worker concurrency
- PostgreSQL query performance
- retry backlog behavior
- p95 delivery latency

Only introduce Kafka/Redis after measurement demonstrates a real bottleneck.

---

# 15. Definition of Verified Feature

A feature is verified only if:

- tests were actually executed
- expected scenarios passed
- build passed
- migration passed
- no unrelated failures were ignored

Do not report unexecuted checks as successful.
