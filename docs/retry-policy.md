# Retry Policy

## 1. Goal

The retry policy defines when a failed webhook delivery should be attempted again and when it should become terminally failed.

The policy must be deterministic, testable, and centralized.

---

# 2. Delivery Guarantee

Version 1 provides:

```text
AT-LEAST-ONCE DELIVERY
```

A consumer may receive the same delivery more than once.

Consumers should deduplicate using a stable delivery identifier such as:

```http
X-Webhook-Delivery-Id: del_01J...
```

---

# 3. Default Attempt Limit

Default maximum attempts:

```text
5
```

The initial HTTP request counts as attempt 1.

---

# 4. Default Backoff Schedule

Recommended MVP schedule:

```text
Attempt 1 fails
-> retry after 10 seconds

Attempt 2 fails
-> retry after 30 seconds

Attempt 3 fails
-> retry after 2 minutes

Attempt 4 fails
-> retry after 10 minutes

Attempt 5 fails
-> FAILED
```

This schedule may later become configuration-driven.

Version 1 should avoid user-configurable retry policies unless explicitly required.

---

# 5. Outcome Classification

## Successful

Any HTTP response:

```text
200-299
```

Result:

```text
DELIVERED
```

No additional retries.

---

## Retryable HTTP Statuses

Version 1 should retry:

```text
408 Request Timeout
429 Too Many Requests
500 Internal Server Error
502 Bad Gateway
503 Service Unavailable
504 Gateway Timeout
```

Optionally, other 5xx responses may be classified as retryable by default.

---

## Non-Retryable HTTP Statuses

Version 1 should normally treat persistent client-side failures as terminal:

```text
400 Bad Request
401 Unauthorized
403 Forbidden
404 Not Found
405 Method Not Allowed
410 Gone
422 Unprocessable Entity
```

Result:

```text
FAILED
```

without consuming all scheduled retries.

This behavior should be covered by tests and can later become configurable.

---

# 6. Network Errors

Retryable:

- connection refused
- connection reset
- DNS temporary failure
- connect timeout
- read timeout

Persist a meaningful error classification.

Example:

```text
TIMEOUT
CONNECTION_REFUSED
DNS_ERROR
NETWORK_ERROR
```

Do not store only a generic `Exception occurred` message.

---

# 7. HTTP 429

If the consumer returns:

```http
Retry-After
```

the platform may respect it when the value is valid and within a safe maximum.

Otherwise use the normal retry schedule.

This behavior is optional for first implementation but recommended.

---

# 8. State Transitions

Normal flow:

```text
PENDING
  |
  v
PROCESSING
  |
  +-- 2xx --------------------> DELIVERED
  |
  +-- retryable failure ------> RETRYING
  |
  +-- terminal failure -------> FAILED
```

Retry flow:

```text
RETRYING
   |
   | next_retry_at <= now
   v
PROCESSING
```

Max-attempt flow:

```text
PROCESSING
   |
   | retryable failure
   | attempt_count == max_attempts
   v
FAILED
```

---

# 9. Manual Retry

Version 1 recommendation:

A manual retry reuses the same `WebhookDelivery`.

Behavior:

```text
FAILED
  |
  | developer clicks Retry
  v
PENDING
```

Rules:

- previous attempts remain immutable
- attempt numbering continues
- manual retry should be auditable
- max-attempt handling for manual retry must be explicit

Recommended simple MVP behavior:

A manual retry grants one additional immediate attempt.

If it fails with a retryable error, the standard retry schedule may restart with a fresh retry budget only if explicitly implemented and documented.

For initial implementation, prefer the simpler rule:

```text
Manual retry = one additional attempt
```

This avoids ambiguous retry budget behavior.

---

# 10. Retry Storm Protection

Version 1 should:

- limit worker batch size
- use backoff
- avoid immediate tight-loop retries

Future options:

- jitter
- endpoint-level rate limits
- circuit breaker
- global concurrency controls

These are not mandatory for MVP.

---

# 11. Endpoint Health

Suggested operational behavior:

```text
successful delivery
-> reset consecutive_failure_count

failed attempt leading to terminal failure
-> increment endpoint failure indicator
```

Do not automatically disable an endpoint after a small number of failures in version 1 unless explicitly required.

---

# 12. Test Matrix

At minimum:

| Outcome | Expected |
|---|---|
| HTTP 200 | DELIVERED |
| HTTP 201 | DELIVERED |
| HTTP 400 | FAILED |
| HTTP 401 | FAILED |
| HTTP 404 | FAILED |
| HTTP 408 | RETRYING |
| HTTP 429 | RETRYING |
| HTTP 500 | RETRYING |
| HTTP 503 | RETRYING |
| timeout | RETRYING |
| max retry reached | FAILED |

Also test:

```text
500 -> 500 -> 200
```

Expected:

```text
3 attempts
final status DELIVERED
```
