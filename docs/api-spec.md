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
  "type": "ai.solution.completed",
  "occurredAt": "2026-08-29T05:20:14Z",
  "data": {
    "solutionId": "sol_7392",
    "subject": "mathematics",
    "status": "COMPLETED"
  }
}
```

### Required Fields

```text
sourceEventId
type
occurredAt
data
```

### Behavior

The platform must:

1. validate API key
2. resolve application
3. validate event input
4. check `(application_id, source_event_id)` idempotency
5. persist the event if new
6. create deliveries for matching active subscriptions
7. return before external HTTP delivery occurs

### Response

Recommended:

```http
202 Accepted
```

```json
{
  "id": "evt_01J67FC92X",
  "sourceEventId": "evt-ai-982",
  "type": "ai.solution.completed",
  "status": "ACCEPTED",
  "deliveryCount": 3
}
```

### Duplicate Source Event

If the same application republishes the same `sourceEventId`, the platform should return the existing accepted event instead of creating duplicate deliveries.

Recommended response:

```http
200 OK
```

```json
{
  "id": "evt_01J67FC92X",
  "sourceEventId": "evt-ai-982",
  "type": "ai.solution.completed",
  "status": "ALREADY_ACCEPTED",
  "deliveryCount": 3
}
```

---

# 4. Application Management API

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

## List Applications

```http
GET /api/v1/applications
```

## Get Application

```http
GET /api/v1/applications/{applicationId}
```

## Update Application

```http
PATCH /api/v1/applications/{applicationId}
```

---

# 5. API Key Management

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

## List API Keys

```http
GET /api/v1/applications/{applicationId}/api-keys
```

## Revoke API Key

```http
POST /api/v1/api-keys/{apiKeyId}/revoke
```

---

# 6. Endpoint Management

## Create Endpoint

```http
POST /api/v1/endpoints
```

Example:

```json
{
  "name": "AI Analytics",
  "url": "https://analytics.example.com/webhooks/ai",
  "subscriptions": [
    "ai.solution.completed",
    "ai.grade.completed"
  ]
}
```

Recommended response:

```json
{
  "id": "ep_123",
  "name": "AI Analytics",
  "url": "https://analytics.example.com/webhooks/ai",
  "status": "ACTIVE",
  "subscriptions": [
    "ai.solution.completed",
    "ai.grade.completed"
  ],
  "signingSecret": "whsec_ONE_TIME_VISIBLE_SECRET"
}
```

Full signing secret should be shown only according to the chosen secure secret-management design.

## List Endpoints

```http
GET /api/v1/endpoints
```

Suggested filters:

```text
status
search
page
size
```

## Get Endpoint

```http
GET /api/v1/endpoints/{endpointId}
```

## Update Endpoint

```http
PATCH /api/v1/endpoints/{endpointId}
```

## Disable Endpoint

```http
POST /api/v1/endpoints/{endpointId}/disable
```

## Enable Endpoint

```http
POST /api/v1/endpoints/{endpointId}/enable
```

## Rotate Signing Secret

```http
POST /api/v1/endpoints/{endpointId}/rotate-secret
```

## Send Test Webhook

```http
POST /api/v1/endpoints/{endpointId}/test
```

---

# 7. Subscription Management

A simple version may manage subscriptions as part of endpoint create/update.

If explicit subscription endpoints are preferred:

```http
PUT /api/v1/endpoints/{endpointId}/subscriptions
```

Example:

```json
{
  "eventTypes": [
    "ai.solution.completed",
    "ai.grade.completed",
    "ai.answer.reported"
  ]
}
```

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
