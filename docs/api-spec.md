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

# 8. Event Query API

## List Events

```http
GET /api/v1/events
```

Suggested filters:

```text
applicationId
eventType
createdFrom
createdTo
search
page
size
```

## Get Event

```http
GET /api/v1/events/{eventId}
```

Recommended response includes:

- event metadata
- payload
- delivery summary

Example:

```json
{
  "id": "evt_01J67FC92X",
  "sourceEventId": "evt-ai-982",
  "application": {
    "id": "app_01",
    "name": "AI Study Assistant"
  },
  "type": "ai.solution.completed",
  "occurredAt": "2026-08-29T05:20:14Z",
  "receivedAt": "2026-08-29T05:20:14.102Z",
  "data": {
    "solutionId": "sol_7392"
  },
  "deliverySummary": {
    "total": 3,
    "delivered": 2,
    "retrying": 0,
    "failed": 1
  }
}
```

---

# 9. Delivery API

## List Deliveries

```http
GET /api/v1/deliveries
```

Suggested filters:

```text
status
endpointId
eventType
applicationId
httpStatus
createdFrom
createdTo
search
page
size
```

## Get Delivery

```http
GET /api/v1/deliveries/{deliveryId}
```

Recommended response:

```json
{
  "id": "del_01",
  "status": "DELIVERED",
  "event": {
    "id": "evt_01",
    "type": "ai.solution.completed"
  },
  "endpoint": {
    "id": "ep_01",
    "name": "AI Analytics",
    "url": "https://analytics.example.com/webhooks/ai"
  },
  "attemptCount": 3,
  "maxAttempts": 5,
  "createdAt": "2026-08-29T05:20:14Z",
  "deliveredAt": "2026-08-29T05:20:54Z",
  "attempts": [
    {
      "attemptNumber": 1,
      "responseStatus": 500,
      "latencyMs": 812
    },
    {
      "attemptNumber": 2,
      "responseStatus": 500,
      "latencyMs": 624
    },
    {
      "attemptNumber": 3,
      "responseStatus": 200,
      "latencyMs": 142
    }
  ]
}
```

## Manual Retry

```http
POST /api/v1/deliveries/{deliveryId}/retry
```

Expected behavior is defined in `retry-policy.md`.

---

# 10. Dashboard Overview API

To avoid excessive frontend aggregation, version 1 may expose a dashboard summary endpoint.

```http
GET /api/v1/dashboard/overview
```

Suggested response:

```json
{
  "events": 1284,
  "deliveries": 2517,
  "deliveryRate": 98.7,
  "averageLatencyMs": 184,
  "failedDeliveries": 24,
  "retryingDeliveries": 5
}
```

This endpoint is optional if existing query endpoints can efficiently provide the same data.

---

# 11. Error Format

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

# 12. API Design Principles

- use stable identifiers
- use versioned routes
- use pagination for collection endpoints
- avoid exposing entity persistence internals
- never expose raw stored secrets
- producer ingestion must be idempotent
- external delivery must not happen synchronously inside `POST /events`
