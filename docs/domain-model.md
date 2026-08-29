# Domain Model

## 1. Overview

The core domain is built around three distinct concepts:

```text
WebhookEvent
    |
    | creates
    v
WebhookDelivery
    |
    | contains
    v
WebhookDeliveryAttempt
```

These concepts must remain separate.

- An **event** represents something that happened.
- A **delivery** represents the responsibility to send that event to one endpoint.
- An **attempt** represents one actual outbound HTTP request.

---

## 2. Entity Relationship Summary

```text
Application
   |
   +--< ApiKey
   |
   +--< WebhookEvent
             |
             +--< WebhookDelivery >-- WebhookEndpoint
                         |
                         +--< WebhookDeliveryAttempt

WebhookEndpoint
   |
   +--< WebhookSubscription
```

Recommended cardinalities:

```text
Application 1 --- N ApiKey
Application 1 --- N WebhookEvent

WebhookEndpoint 1 --- N WebhookSubscription

WebhookEvent 1 --- N WebhookDelivery
WebhookEndpoint 1 --- N WebhookDelivery

WebhookDelivery 1 --- N WebhookDeliveryAttempt
```

---

# 3. Application

## Purpose

Represents a producer application that publishes events.

Example:

```text
AI Study Assistant
```

## Suggested Fields

```text
id
name
slug
status
environment
created_at
updated_at
```

## Suggested Status

```text
ACTIVE
DISABLED
```

## Notes

Application identity is separate from user identity.

Version 1 does not require organization/team modeling.

---

# 4. ApiKey

## Purpose

Authenticates a producer application calling the event ingestion API.

## Suggested Fields

```text
id
application_id
name
key_prefix
key_hash
status
last_used_at
expires_at
created_at
revoked_at
```

## Suggested Status

```text
ACTIVE
REVOKED
EXPIRED
```

## Invariants

- API key belongs to exactly one application.
- Raw API key is returned only at creation.
- Raw API key must not be persisted.
- API key metadata may be shown in the dashboard.

---

# 5. WebhookEndpoint

## Purpose

Represents one external HTTP destination.

Example:

```text
https://analytics.example.com/webhooks/ai
```

## Suggested Fields

```text
id
name
url
signing_secret_encrypted
status
consecutive_failure_count
last_success_at
last_failure_at
created_at
updated_at
```

## Suggested Status

```text
ACTIVE
DISABLED
FAILING
```

## Notes

`FAILING` is operational state information and should not be treated as a permanent configuration mode.

The platform should not send new webhook deliveries to a disabled endpoint unless product behavior explicitly changes.

---

# 6. WebhookSubscription

## Purpose

Represents a subscription between an endpoint and an event type.

Example:

```text
Endpoint: AI Analytics
Event type: ai.solution.completed
```

## Suggested Fields

```text
id
endpoint_id
event_type
created_at
```

## Constraint

```text
UNIQUE(endpoint_id, event_type)
```

## Notes

Version 1 uses exact event-type matching.

Wildcard subscriptions are out of scope.

---

# 7. WebhookEvent

## Purpose

Represents one immutable event received from a producer.

Example:

```text
ai.solution.completed
```

## Suggested Fields

```text
id
application_id
source_event_id
event_type
payload
occurred_at
received_at
created_at
```

Recommended payload storage:

```text
PostgreSQL JSONB
```

## Important Invariants

- Event data is immutable after successful ingestion.
- `source_event_id` identifies the event in the producer.
- Internal `id` identifies the event inside the Webhook Platform.

Recommended uniqueness:

```text
UNIQUE(application_id, source_event_id)
```

This provides producer-level idempotency.

## Example

```json
{
  "id": "evt_01J67FC92X",
  "sourceEventId": "evt-ai-982",
  "eventType": "ai.solution.completed",
  "payload": {
    "solutionId": "sol_7392",
    "subject": "mathematics",
    "status": "COMPLETED"
  }
}
```

---

# 8. WebhookDelivery

## Purpose

Represents delivery of one event to one endpoint.

If one event has three subscribed endpoints, three deliveries are created.

## Suggested Fields

```text
id
event_id
endpoint_id
status
attempt_count
max_attempts
next_retry_at
last_attempt_at
delivered_at
created_at
updated_at
```

## Status

```text
PENDING
PROCESSING
RETRYING
DELIVERED
FAILED
```

## Important Invariants

- One delivery references exactly one event.
- One delivery references exactly one endpoint.
- Delivery owns the aggregate retry state.
- Attempt history is stored separately.
- `attempt_count` must correspond to persisted attempts.

Recommended uniqueness for initial automatic fan-out:

```text
UNIQUE(event_id, endpoint_id)
```

Manual retry should not create duplicate base deliveries unless a future design explicitly changes this behavior.

---

# 9. WebhookDeliveryAttempt

## Purpose

Represents one physical outbound HTTP request.

## Suggested Fields

```text
id
delivery_id
attempt_number
started_at
finished_at
request_headers
request_body
response_status
response_headers
response_body
latency_ms
error_type
error_message
created_at
```

## Notes

Request and response data must be safely stored.

Do not store raw secrets or authorization credentials.

Payload size limits should be enforced.

## Example

```text
Delivery: del_001

Attempt 1 -> HTTP 500 -> 812 ms
Attempt 2 -> HTTP 500 -> 624 ms
Attempt 3 -> HTTP 200 -> 142 ms
```

---

# 10. Derived Concepts

## Endpoint Health

Endpoint health is derived from recent delivery behavior.

Version 1 may use fields such as:

```text
consecutive_failure_count
last_success_at
last_failure_at
```

Avoid creating a separate health-history table until needed.

## Event Type

Version 1 does not require a separate `event_types` database table.

Event type may be represented as a validated string:

```text
ai.solution.completed
```

A separate event catalog can be introduced later if dynamic registration becomes necessary.

---

# 11. Aggregate Boundaries

Suggested conceptual aggregates:

### Application Aggregate

```text
Application
ApiKey
```

### Endpoint Aggregate

```text
WebhookEndpoint
WebhookSubscription
```

### Event/Delivery Flow

```text
WebhookEvent
WebhookDelivery
WebhookDeliveryAttempt
```

Avoid deep bidirectional JPA relationships across all entities.

Prefer explicit queries and IDs where they reduce accidental loading and N+1 issues.

---

# 12. Key Domain Questions and Decisions

## Can one event create zero deliveries?

Yes.

An event may have no matching subscriptions.

The event must still be persisted.

## Can one event create multiple deliveries?

Yes.

One per matching endpoint.

## Can one delivery have multiple attempts?

Yes.

Retry creates additional attempts for the same delivery.

## Can one attempt belong to multiple deliveries?

No.

## Can a failed delivery later become delivered?

Yes, if an explicit manual retry is allowed and succeeds.

Attempt history must remain intact.

## Is exactly-once delivery guaranteed?

No.

Version 1 guarantees at-least-once delivery behavior.

Duplicates remain possible in crash/timeout scenarios.
