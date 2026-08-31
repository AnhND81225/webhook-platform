# Security Specification

## 1. Security Boundaries

There are two primary trust boundaries:

```text
Producer -> Webhook Platform
```

and

```text
Webhook Platform -> Consumer
```

Each uses a different authentication mechanism.

---

# 2. Producer Authentication

Producer applications authenticate using API keys.

Example:

```http
Authorization: Bearer whk_live_xxxxxxxxx
```

The key resolves to exactly one producer application.

---

# 3. API Key Storage

Never persist raw API keys.

Recommended creation flow:

1. generate strong random key
2. return full key once
3. store key prefix for identification
4. store secure hash for verification
5. discard raw value

Visible prefix:

```text
whk_live_7Fx
```

M2 generates 32 random bytes with `SecureRandom`, encodes them as unpadded Base64 URL text, and formats the credential as `whk_test_<secret>` for development or `whk_live_<secret>` for production. The complete raw key is hashed using SHA-256 and stored as a unique 64-character lowercase hexadecimal digest. The marker plus the first four secret characters is safe display metadata only and never authenticates a request.

Stored key metadata:

```text
id
application_id
name
key_prefix
key_hash
status
created_at
last_used_at
revoked_at
```

---

# 4. API Key Lifecycle

Support:

- create
- view metadata
- revoke

Optional later:

- expiration
- rotation workflow
- scoped permissions

Version 1 keys may have application-level event-publish permission only.

M2 statuses are `ACTIVE` and `REVOKED`. Revocation is irreversible and idempotent. Expiration, reactivation, and automated rotation are deferred. If a client does not receive the one-time creation response, the key cannot be recovered; create a replacement and revoke the unusable key.

M4 activates producer authentication exclusively for `POST /api/v1/events`. The complete Bearer key is SHA-256 hashed and looked up by unique `key_hash`; the safe prefix, Application ID, and slug never authenticate a request. A key and its owning Application must both be `ACTIVE`. All credential failures return the same `401 INVALID_API_KEY` response, and successful credential validation updates `last_used_at` even if later event validation fails.

Producer ingestion uses a dedicated stateless security chain with no session lookup, OAuth login, request cache, or CSRF requirement. Dashboard routes remain session/OIDC authenticated and CSRF-protected. A dashboard session cannot substitute for a producer key, and a producer key cannot access dashboard APIs.

---

# 5. Webhook Signing

Outbound webhook requests must use HMAC SHA-256.

Recommended signed content:

```text
timestamp + "." + raw_request_body
```

Example conceptual signature:

```text
HMAC_SHA256(signing_secret, timestamp + "." + body)
```

Recommended headers:

```http
X-Webhook-Id: evt_01J...
X-Webhook-Delivery-Id: del_01J...
X-Webhook-Timestamp: 1787980814
X-Webhook-Signature: v1=<lowercase-hex-hmac-sha256>
X-Webhook-Event: ai.solution.completed
```

M9 fixes this contract as `v1=<lowercase hexadecimal HMAC-SHA256 digest>`. The signed byte sequence is UTF-8 timestamp bytes, a single `.` byte, then the exact HTTP body bytes sent on the wire.

---

# 6. Signature Verification Contract

Consumers should verify:

1. timestamp is within allowed tolerance
2. signature is recomputed using the raw body
3. comparison uses constant-time equality
4. delivery ID may be used for deduplication

Suggested timestamp tolerance:

```text
5 minutes
```

This helps reduce replay risk.

---

# 7. Signing Secret Storage

Unlike an API key hash, webhook signing requires the original secret to generate signatures.

Therefore the platform must store the secret in recoverable protected form.

Version 1 options:

- encrypted database column using an application-managed encryption key
- secret manager when deployed to cloud

M9 stores each endpoint's independent 32-byte signing key as AES-256-GCM ciphertext with a fresh 12-byte nonce and endpoint/key-version authenticated associated data. The Base64 32-byte master key is supplied only through `WEBHOOK_SECRET_ENCRYPTION_KEY`; it is never stored in PostgreSQL. A missing or undecryptable secret produces terminal `SIGNING_ERROR` and never permits an unsigned send.

Do not log it.

---

# 8. Secret Rotation

Endpoint secret rotation should:

1. generate a new secret
2. protect and persist it
3. reveal the new secret according to dashboard policy
4. invalidate the old secret

Future improvement:

Support overlapping old/new secrets during a rotation grace period.

This is not required for MVP.

---

# 9. Endpoint URL Validation

Hosted runtime requires valid HTTPS endpoint URLs. This rule is based on the server runtime profile, never on `Application.environment`.

Only the local `dev` profile may allow:

```text
http://localhost
http://127.0.0.1
```

for controlled local testing. Hosted runtime rejects localhost, loopback, and obvious unsafe private literal IP targets.

Reject obviously invalid schemes such as:

```text
file://
ftp://
```

---

# 10. SSRF Considerations

Webhook delivery can create Server-Side Request Forgery risk.

Production-oriented design should consider blocking:

- loopback addresses
- link-local addresses
- private network ranges
- cloud metadata endpoints

Development mode may need controlled exceptions for localhost mock consumers.

M3 performs configuration-time validation only. M6 adds delivery-time validation through a DNS resolver used by the outbound HTTP client itself: every resolved address is checked and any unsafe result rejects the destination. Automatic redirects are disabled, so a redirect cannot bypass validation. TLS certificate and hostname verification remain enabled. Only the local `dev` profile may use controlled localhost/127.0.0.1 targets; Application environment never weakens this boundary.

---

# 11. Sensitive Logging

Never log:

- raw API keys
- raw signing secrets
- `Authorization` headers
- full encrypted secret values

Sanitize outbound request headers before storing attempt logs.

Example:

```text
Authorization: [REDACTED]
```

---

# 12. Webhook Payload Privacy

The AI Study Assistant should publish minimal data.

Prefer:

```json
{
  "solutionId": "sol_123",
  "status": "COMPLETED"
}
```

over publishing:

- full student question
- full generated solution
- personally identifying data
- unnecessary internal model prompts

Webhook events should notify consumers, not automatically mirror all application data.

---

# 13. Dashboard Security

Dashboard developers authenticate through Google OIDC. The backend maps the validated Google `sub` to a local user and stores authentication in a server-managed session.

Production session cookies are host-only, `HttpOnly`, `Secure`, and `SameSite=Lax`. Authenticated production deployments require related custom frontend/API hosts under the same registrable site. Those hosts are still different origins, so credentialed CORS permits only the configured frontend origin. No broad cookie `Domain` is configured, and CSRF remains enabled for logout and future dashboard mutations.

Changing a local user to `DISABLED` blocks subsequent logins but does not revoke an already-authenticated M1 session. The existing session remains valid until logout, idle expiration, backend restart, or explicit invalidation. This accepted M1 limitation avoids per-request database lookups and distributed revocation infrastructure.

Google tokens are never returned to the frontend or persisted in application tables.

Full multi-user RBAC is out of MVP scope.

---

# 14. Abuse Controls

Future protections may include:

- producer request rate limiting
- endpoint-level delivery concurrency
- payload size limits
- event size limits
- API key usage quotas

For MVP, payload size limits should still be enforced.

M4 limits the raw producer request body to 1 MiB, including requests without `Content-Length`. Event payloads must be JSON objects; arrays, primitives, and null are rejected. Do not log raw credentials, key hashes, Authorization headers, or complete event payloads.

---

# 15. Security Tests

Required tests should include:

- invalid API key rejected
- revoked API key rejected
- valid API key resolves correct application
- raw API key not persisted
- HMAC signature deterministic
- modified payload fails verification
- modified timestamp fails verification
- stale timestamp rejected by consumer verification example
- sensitive headers redacted from stored attempt data
