# API Specification

## 1. Overview

The API is divided into two logical surfaces:

1. Producer API
2. Dashboard Management API

Base path:

```text
/api/v1
```

All JSON APIs use:

```http
Content-Type: application/json
```

---

# 2. Dashboard Authentication API

Dashboard developers authenticate through Google OIDC and a backend-managed session.

```http
GET /api/v1/auth/me
```

Returns the local user ID, email, display name, and nullable avatar URL. Unauthenticated requests return JSON `401 Unauthorized`. Google identity claims and tokens are never returned.

```http
GET /api/v1/auth/csrf
```

Returns a session-backed CSRF token for authenticated dashboard mutations.

```http
POST /api/v1/auth/logout
```

Requires the CSRF token, invalidates the local session, and returns `204 No Content`.

---

# 3. Producer API

## 3.1 Authentication

Producer requests use an API key.

Example:

```http
Authorization: Bearer whk_live_xxxxxxxxx
```

The API key identifies the producer application.

---

## 3.2 Publish Event

```http
POST /api/v1/events
```

### Request

```json
{
  "sourceEventId": "evt-ai-982",
  "eventType": "ai.solution.completed",
  "payload": {
    "solutionId": "sol_7392",
    "subject": "mathematics",
    "status": "COMPLETED"
  }
}
```

### Required Fields

```text
sourceEventId
eventType
payload
```

`payload` must be a JSON object; an empty object is valid, while arrays, primitives, and `null` are rejected. The raw request body is limited to 1 MiB. The request does not accept an Application ID because identity derives exclusively from the authenticated API key.

### Behavior

The platform must:

1. validate API key
2. resolve application
3. validate event input
4. check `(application_id, source_event_id)` idempotency
5. persist the immutable event if new
6. return without selecting subscriptions or creating deliveries

### Response

Recommended:

```http
201 Created
```

```json
{
  "id": "evt_01J67FC92X",
  "sourceEventId": "evt-ai-982",
  "eventType": "ai.solution.completed",
  "createdAt": "2026-08-29T05:20:14Z"
}
```

### Duplicate Source Event

If the same application republishes the same `sourceEventId` with the same event type and JSONB-equivalent payload, the platform returns the existing event instead of creating a duplicate row.

Recommended response:

```http
200 OK
```

```json
{
  "id": "evt_01J67FC92X",
  "sourceEventId": "evt-ai-982",
  "eventType": "ai.solution.completed",
  "createdAt": "2026-08-29T05:20:14Z"
}
```

Reusing a source event ID with a different event type or JSONB payload returns `409 SOURCE_EVENT_ID_CONFLICT`.

---

# 4. Application Management API

All Application APIs require an authenticated dashboard session. Mutations require the session CSRF token. Ownership always comes from the authenticated local user and is never accepted from request data. Missing and non-owned resources both return `404 APPLICATION_NOT_FOUND`.

## Create Application

```http
POST /api/v1/applications
```

Example request:

```json
{
  "name": "AI Study Assistant",
  "slug": "ai-study-assistant",
  "environment": "PRODUCTION"
}
```

`name` is trimmed, nonblank, and at most 120 characters. `slug` is immutable, at most 63 characters, and matches `^[a-z0-9]+(?:-[a-z0-9]+)*$`. It is unique per owner; a collision returns `409 APPLICATION_SLUG_CONFLICT`. `environment` is immutable and is either `DEVELOPMENT` or `PRODUCTION`.

Returns `201 Created`, a `Location` header, and Application metadata. Status starts as `ACTIVE`.

## List Applications

```http
GET /api/v1/applications
```

Returns only the current user's Applications ordered by `createdAt DESC, id DESC`.

## Get Application

```http
GET /api/v1/applications/{applicationId}
```

Returns owned Application metadata.

## Update Application

```http
PATCH /api/v1/applications/{applicationId}
```

Accepts one or both of `name` and `status`. Status is `ACTIVE` or `DISABLED`. ID, owner, slug, and environment are immutable.

---

# 5. API Key Management

API-key management uses the authenticated dashboard session and owner-scoped authorization. Mutations require CSRF. Keys do not expire in M2.

## Create API Key

```http
POST /api/v1/applications/{applicationId}/api-keys
```

Example request:

```json
{
  "name": "Production"
}
```

Response may contain the full key once:

```json
{
  "id": "key_123",
  "name": "Production",
  "keyPrefix": "whk_live_7Fx",
  "apiKey": "whk_live_7Fx_FULL_SECRET_VALUE",
  "createdAt": "2026-08-29T05:30:00Z"
}
```

The full value must not be returned again.

The response uses `Cache-Control: no-store`. Production Applications receive a `whk_live_` key and development Applications receive `whk_test_`. The secret contains 32 CSPRNG bytes encoded as unpadded Base64 URL text. If the response is lost, the raw key cannot be recovered.

## List API Keys

```http
GET /api/v1/applications/{applicationId}/api-keys
```

Returns owned key metadata ordered by `createdAt DESC, id DESC`: ID, name, safe prefix, status, nullable last-used time, creation time, and nullable revocation time. It never returns the raw key or hash.

## Revoke API Key

```http
POST /api/v1/api-keys/{apiKeyId}/revoke
```

Revocation is irreversible and idempotent. It returns safe metadata with status `REVOKED` and preserves the original `revokedAt` on repeated calls. Missing and non-owned keys both return `404 API_KEY_NOT_FOUND`.

---

# 6. Endpoint Management

M9 endpoint creation returns endpoint metadata plus a one-time `signingSecret` and `Cache-Control: no-store`. Normal GET/list responses never include it. A pre-M9 endpoint is provisioned once by the owner through `POST /api/v1/applications/{applicationId}/endpoints/{endpointId}/signing-secret`, also CSRF-protected and `no-store`.

## Create Endpoint

```http
POST /api/v1/applications/{applicationId}/endpoints
```

Example:

```json
{
  "name": "AI Analytics",
  "url": "https://analytics.example.com/webhooks/ai"
}
```

Recommended response:

```json
{
  "id": "ep_123",
  "name": "AI Analytics",
  "url": "https://analytics.example.com/webhooks/ai",
  "status": "ACTIVE",
  "createdAt": "2026-01-01T00:00:00Z",
  "updatedAt": "2026-01-01T00:00:00Z"
}
```

Endpoint ownership derives from the authenticated dashboard user through the Application. M3 has no signing-secret response or storage.

## List Endpoints

```http
GET /api/v1/applications/{applicationId}/endpoints
```

Returns owned endpoints ordered by `createdAt DESC, id DESC`.

## Get Endpoint

```http
GET /api/v1/applications/{applicationId}/endpoints/{endpointId}
```

## Update Endpoint

```http
PATCH /api/v1/applications/{applicationId}/endpoints/{endpointId}
```

Only `name`, `url`, and `status` may be patched. Status is `ACTIVE` or `DISABLED`; disabling does not remove subscriptions. M3 has no delete endpoint API.

Hosted runtime requires HTTPS and rejects localhost and unsafe literal IP targets. The local `dev` profile alone permits HTTP localhost/127.0.0.1 URLs for controlled testing. DNS, redirect, and resolved-IP SSRF checks are deferred until outbound delivery exists.

---

# 7. Subscription Management

M3 manages one exact event type per Subscription row:

```http
POST /api/v1/applications/{applicationId}/endpoints/{endpointId}/subscriptions
```

Example:

```json
{
  "eventType": "ai.solution.completed"
}
```

```http
GET /api/v1/applications/{applicationId}/endpoints/{endpointId}/subscriptions
DELETE /api/v1/applications/{applicationId}/endpoints/{endpointId}/subscriptions/{subscriptionId}
```

`UNIQUE(endpoint_id, event_type)` is the final duplicate-prevention guarantee. Duplicate creation returns `409 SUBSCRIPTION_ALREADY_EXISTS`. Cross-user and mismatched nested resources return `404`.

---

# 8. Dashboard Read APIs

M10 provides session-authenticated, Application-scoped observability reads. The authenticated dashboard user owns the Application in every route; a non-owned or mismatched nested resource returns the established `404` response. A producer API key cannot authorize any dashboard read.

Collection routes use opaque keyset cursors. Ordering is `createdAt DESC`, then `id DESC`; the default size is `25` and the maximum is `100`. `nextCursor` is `null` on the final page. Consumers must treat a cursor as opaque.

All `createdFrom` and `createdTo` values use ISO-8601 UTC instants. A range where `createdFrom > createdTo`, malformed cursor/timestamp, invalid status, or an out-of-range size returns `400` with the normal error DTO.

## Dashboard Summary

```http
GET /api/v1/applications/{applicationId}/dashboard/summary
```

```json
{
  "events": { "total": 1284, "last24Hours": 42 },
  "deliveries": {
    "pending": 3,
    "processing": 1,
    "retryScheduled": 5,
    "delivered": 1200,
    "failed": 75
  },
  "recentFailures": 4
}
```

## Events

```http
GET /api/v1/applications/{applicationId}/events
GET /api/v1/applications/{applicationId}/events/{eventId}
```

List filters: `eventType`, `sourceEventId`, `createdFrom`, `createdTo`, `cursor`, and `size`.

```json
{
  "items": [{
    "id": "6eb5736d-4f40-45a2-8123-366e51880df7",
    "sourceEventId": "solution-93482",
    "eventType": "ai.solution.completed",
    "createdAt": "2026-08-29T05:20:14Z",
    "deliveryCount": 2,
    "deliveredCount": 1,
    "failedCount": 0,
    "retryScheduledCount": 1
  }],
  "nextCursor": "opaque-cursor"
}
```

The event detail additionally returns its immutable JSON `payload` and delivery counters:

```json
{
  "id": "6eb5736d-4f40-45a2-8123-366e51880df7",
  "sourceEventId": "solution-93482",
  "eventType": "ai.solution.completed",
  "createdAt": "2026-08-29T05:20:14Z",
  "payload": {
    "status": "completed"
  },
  "deliveryCount": 2,
  "deliveredCount": 1,
  "failedCount": 0,
  "retryScheduledCount": 1
}
```

Event list rows deliberately do not include payloads.

## Deliveries

```http
GET /api/v1/applications/{applicationId}/deliveries
GET /api/v1/applications/{applicationId}/deliveries/{deliveryId}
GET /api/v1/applications/{applicationId}/deliveries/{deliveryId}/attempts
```

List filters: `status`, `endpointId`, `eventType`, `createdFrom`, `createdTo`, `cursor`, and `size`. Supported statuses are `PENDING`, `PROCESSING`, `RETRY_SCHEDULED`, `DELIVERED`, and `FAILED`.

```json
{
  "items": [{
    "id": "0c764b64-1851-4a80-bd04-18b8ce5faea5",
    "eventId": "6eb5736d-4f40-45a2-8123-366e51880df7",
    "eventType": "ai.solution.completed",
    "endpointId": "7993122c-d8d9-4ff8-906a-2bda495d85cc",
    "endpointName": "Analytics",
    "status": "RETRY_SCHEDULED",
    "nextRetryAt": "2026-08-29T05:21:14Z",
    "attemptCount": 1,
    "lastAttempt": { "attemptNumber": 1, "status": "FAILED", "httpStatusCode": 500, "errorCode": "HTTP_ERROR" },
    "createdAt": "2026-08-29T05:20:14Z",
    "updatedAt": "2026-08-29T05:20:15Z"
  }],
  "nextCursor": null
}
```

Delivery detail includes event and endpoint summaries and the snapshotted `targetUrl`. Both endpoint and target URLs are sanitized for dashboard output: user-info, query strings, and fragments are removed. This does not alter endpoint configuration or M5 target-URL snapshot semantics.

```json
{
  "id": "0c764b64-1851-4a80-bd04-18b8ce5faea5",
  "status": "RETRY_SCHEDULED",
  "event": {
    "id": "6eb5736d-4f40-45a2-8123-366e51880df7",
    "sourceEventId": "solution-93482",
    "eventType": "ai.solution.completed",
    "createdAt": "2026-08-29T05:20:14Z"
  },
  "endpoint": {
    "id": "7993122c-d8d9-4ff8-906a-2bda495d85cc",
    "name": "Analytics",
    "url": "https://consumer.example/webhooks/analytics"
  },
  "targetUrl": "https://consumer.example/webhooks/analytics",
  "nextRetryAt": "2026-08-29T05:21:14Z",
  "createdAt": "2026-08-29T05:20:14Z",
  "updatedAt": "2026-08-29T05:20:15Z"
}
```

`nextRetryAt` is `null` for delivery statuses other than `RETRY_SCHEDULED`.

Attempt history is ascending by `attemptNumber`:

```json
[{ "id": "b5c5f56c-2ebb-498c-a6dd-c3d0a14bf8fc", "attemptNumber": 1, "status": "FAILED", "startedAt": "2026-08-29T05:20:14Z", "completedAt": "2026-08-29T05:20:15Z", "durationMs": 812, "httpStatusCode": 500, "errorCode": "HTTP_ERROR" }]
```

`IN_PROGRESS`, `SUCCEEDED`, `FAILED`, and `ABANDONED` are attempt states. `ABANDONED` means the outcome is unknown after stale/crash recovery; it does not prove the consumer did not receive the request.

Dashboard responses never expose raw API keys, API-key hashes, signing secrets/ciphertext/nonces/master keys, claim tokens, webhook signatures, response bodies, or raw exception details. M9 signing-secret provisioning remains a separate one-time endpoint workflow.

---

# 9. Error Format

Use a consistent error response.

M2 responses use the safe core shape:

```json
{
  "code": "APPLICATION_NOT_FOUND",
  "message": "Application was not found."
}
```

M4 additionally defines generic producer `INVALID_API_KEY`, `INVALID_EVENT_REQUEST`, `SOURCE_EVENT_ID_CONFLICT`, and `PAYLOAD_TOO_LARGE` errors. Database errors, hashes, and security internals are never exposed. Request IDs, timestamps, and field-level error arrays may be added consistently in a later cross-cutting API milestone.

Example:

```json
{
  "code": "ENDPOINT_NOT_FOUND",
  "message": "Webhook endpoint was not found.",
  "requestId": "req_123",
  "timestamp": "2026-08-29T05:30:00Z"
}
```

Validation example:

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed.",
  "errors": [
    {
      "field": "url",
      "message": "Must be a valid HTTPS URL."
    }
  ],
  "requestId": "req_124"
}
```

---

# 10. API Design Principles

- use stable identifiers
- use versioned routes
- use pagination for collection endpoints
- avoid exposing entity persistence internals
- never expose raw stored secrets
- producer ingestion must be idempotent
- external delivery must not happen synchronously inside `POST /events`
