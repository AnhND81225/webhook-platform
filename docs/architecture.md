# Architecture

## 1. Version 1 Architecture

Version 1 uses a modular monolith with PostgreSQL-backed asynchronous delivery.

```text
                    React Dashboard
                           |
                           | REST
                           v
              +--------------------------+
              |   Webhook Platform API   |
              |      Spring Boot         |
              +------------+-------------+
                           |
                           v
                PostgreSQL database
                           |
                  webhook_deliveries
                           |
                           v
              +--------------------------+
              |    Delivery Worker       |
              +------------+-------------+
                           |
                     HTTPS + HMAC
                           |
          +----------------+----------------+
          v                v                v
      Analytics       Notification     Quality Monitor
```

AI Study Assistant publishes events into the same platform:

```text
AI Study Assistant
       |
       | POST /api/v1/events
       | API Key
       v
Webhook Platform
```

The database runtime differs by environment without changing repository or JPA logic:

```text
LOCAL

Spring Boot
    |
    v
Docker PostgreSQL

PRODUCTION

Vercel
    |
    | HTTPS
    v
AWS EC2
    |
    v
Nginx
    |
    v
Dockerized Spring Boot
    |
    | PostgreSQL :5432
    v
AWS RDS for PostgreSQL
```

AWS RDS for PostgreSQL is the only production database target. Production credentials and the JDBC URL are supplied at runtime; the application and image contain no RDS endpoint or AWS-specific database logic.

---

# 2. Why Modular Monolith

Version 1 does not need microservices.

The system has clear internal modules but can be deployed as one backend application.

Benefits:

- easier local development
- easier transactions
- simpler deployment
- easier debugging
- lower infrastructure cost
- enough separation for future extraction

---

# 3. Suggested Modules

```text
application
apikey
endpoint
subscription
event
delivery
retry
signature
security
common
```

## Application Module

Responsibilities:

- manage producer applications
- application status
- application metadata

## API Key Module

Responsibilities:

- issue API keys
- validate API keys
- revoke API keys
- update last-used metadata

## Endpoint Module

Responsibilities:

- endpoint CRUD
- endpoint status
- signing secret lifecycle
- endpoint health metadata

## Subscription Module

Responsibilities:

- subscribe endpoint to event types
- prevent duplicate subscriptions
- query matching endpoints

## Event Module

Responsibilities:

- ingest producer events
- validate source event ID
- enforce idempotency
- persist immutable events
- discover matching subscriptions
- create deliveries

## Delivery Module

Responsibilities:

- claim pending delivery work
- construct outbound request
- invoke endpoint
- persist attempts
- update delivery state

## Retry Module

Responsibilities:

- classify retryable outcomes
- calculate next retry time
- transition failed attempts into retrying state
- enforce max attempts

## Signature Module

Responsibilities:

- construct HMAC signature
- produce signature headers
- provide shared verification specification

## Security Module

Responsibilities:

- producer API key authentication
- Google OIDC dashboard authentication
- local authenticated-user identity
- backend session, CORS, and CSRF boundaries
- sensitive log filtering

---

# 4. Event Ingestion Flow

```text
Producer
   |
   | POST /events
   v
Event Controller
   |
   v
API Key Authentication
   |
   v
Event Validation
   |
   v
Idempotency Check
   |
   +-- duplicate --> return existing event
   |
   v
Persist WebhookEvent
   |
   v
Find Matching Subscriptions
   |
   v
Create WebhookDelivery rows
   |
   v
Commit Transaction
   |
   v
Return 202
```

Important:

External webhook HTTP requests must not occur before the producer request returns.

---

# 5. Delivery Worker Flow

```text
Worker loop
   |
   v
Claim eligible deliveries
   |
   v
Mark PROCESSING
   |
   v
Load event + endpoint
   |
   v
Build canonical request body
   |
   v
POST to endpoint
   |
   v
2xx --> DELIVERED
retryable failure --> RETRY_SCHEDULED in PostgreSQL
terminal failure / exhausted budget --> FAILED
```

---

# 6. Database-Backed Work Queue

Version 1 uses `webhook_deliveries` as durable work state.

Eligible work:

```text
status = PENDING

or

status = RETRY_SCHEDULED
AND next_retry_at <= now
```

Recommended worker claim design:

```sql
SELECT ...
FROM webhook_deliveries
WHERE ...
ORDER BY created_at
FOR UPDATE SKIP LOCKED
LIMIT :batchSize;
```

The final implementation may use an equivalent safe pattern.

---

# 7. Transaction Boundaries

## Event Ingestion Transaction

The following should occur in one transaction:

```text
persist event
+
create matching deliveries
```

This prevents an event from being persisted without its initial delivery records after subscription resolution.

## Delivery Attempt Transaction

Do not hold a database transaction open for the entire remote HTTP call if avoidable.

A safer pattern:

1. atomically claim delivery
2. commit claim
3. create an `IN_PROGRESS` attempt bound to the claim token and commit it
4. perform one external HTTP call outside a database transaction
5. persist the terminal attempt outcome and final delivery transition in one transaction

This reduces long-held DB locks.

---

# 8. Concurrency

Potential problem:

```text
Worker A ----+
             +--> same delivery
Worker B ----+
```

The architecture must prevent normal concurrent double-claiming.

Use:

- row locking
- atomic claim update
- `SKIP LOCKED`
- or an equivalent tested mechanism

Crash scenarios may still produce duplicate outbound requests due to at-least-once semantics.

---

# 9. Failure Scenarios

## Consumer Returns 5xx

```text
attempt stored
delivery -> RETRY_SCHEDULED
next_retry_at assigned from the M8 policy
```

## Consumer Times Out

```text
attempt stored with timeout error
delivery -> RETRY_SCHEDULED when attempt budget remains
```

The consumer may already have processed the request.

Therefore duplicate delivery is possible.

## Worker Crashes Before HTTP Request

Claim timeout/recovery strategy must eventually make the delivery eligible again.

A future field may be introduced if needed:

```text
processing_started_at
```

The worker may requeue stale `PROCESSING` rows.

## Worker Crashes After Consumer Processes but Before DB Update

The delivery may be sent again after recovery.

This is consistent with at-least-once semantics.

---

# 10. AI Study Assistant Integration

Recommended initial integration:

```text
AI Study Assistant
       |
       | successful business event
       v
WebhookEventPublisher
       |
       | HTTP POST
       v
Webhook Platform
```

Initial event origins:

```text
AI solution completed
-> ai.solution.completed

AI grade completed
-> ai.grade.completed

AI answer reported
-> ai.answer.reported
```

The AI Study Assistant should not know about:

- endpoint subscriptions
- retries
- HMAC consumer signing
- delivery attempts

It only publishes domain events.

---

# 11. Future Transactional Outbox

Direct HTTP publishing from AI Study Assistant has a possible failure:

```text
save business state -> success
publish event -> failure
```

A later production-hardening milestone may introduce a transactional outbox inside AI Study Assistant.

```text
Business Transaction
   |
   +-- save solution
   |
   +-- save outbox event
   |
   v
COMMIT

Outbox Publisher
   |
   v
Webhook Platform
```

Transactional outbox is not required for initial Webhook Platform MVP unless explicitly included in implementation scope.

---

# 12. Future Scaling Path

Only after measurement demonstrates need:

```text
Version 1
PostgreSQL worker queue

        ↓

Version 2
Redis for rate limiting / caching where justified

        ↓

Version 3
Kafka or dedicated queue for higher event throughput
```

Do not add future infrastructure preemptively.
