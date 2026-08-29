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

Suggested visible prefix:

```text
whk_live_7Fx
```

Suggested key metadata:

```text
id
application_id
name
key_prefix
key_hash
status
created_at
last_used_at
expires_at
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
X-Webhook-Signature: v1=<hex-or-base64-signature>
X-Webhook-Event: ai.solution.completed
```

Exact encoding must be documented and tested.

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

Do not store the raw signing secret as ordinary plaintext.

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

Version 1 should require valid HTTPS URLs for non-local environments.

Development exceptions may allow:

```text
http://localhost
http://127.0.0.1
```

for local testing.

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

At minimum, document SSRF as a known security boundary even if full protection is introduced after MVP.

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

Version 1 may use simple development-only dashboard access while core webhook behavior is built.

Before public deployment, dashboard management APIs require authentication and authorization.

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
