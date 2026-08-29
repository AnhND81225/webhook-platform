# Authentication Decision

## Decision

Use **Google OAuth 2.0 / OpenID Connect** for Webhook Dashboard user authentication.

Producer applications continue to authenticate separately with platform API keys.

These are two distinct authentication systems:

```text
Developer -> Google OAuth -> Dashboard

Producer Application -> API Key -> Event Ingestion API
```

M4 uses the single canonical producer format `Authorization: Bearer <full-api-key>` on `POST /api/v1/events`. Producer authentication is stateless and does not read a dashboard session. CSRF remains enabled for dashboard mutations and is disabled only for this producer route.

---

## Why Google OAuth

For the MVP it provides:

- fast onboarding
- no password storage
- lower implementation burden
- verified Google identity
- familiar login UX
- compatibility with Spring Security OAuth2 Client

---

## Recommended Backend Flow

```text
1. User clicks "Continue with Google"
2. Browser opens backend OAuth authorization route
3. Backend redirects to Google
4. Google authenticates user
5. Google redirects to backend callback
6. Backend validates OAuth/OIDC response
7. Backend reads stable Google `sub`
8. Backend finds or creates local User
9. Backend establishes secure local session
10. Browser returns to dashboard
```

---

## Local User Model

Recommended fields:

```text
id
google_subject
email
display_name
avatar_url
status
last_login_at
created_at
updated_at
```

Constraint:

```text
UNIQUE(google_subject)
```

Local IDs are UUIDs. Email is required and verified but is not unique. Different Google subjects are never merged by email. Status is `ACTIVE` or `DISABLED`; disabled users cannot establish a new session.

M1 does not immediately revoke a session that was established while the user was active. If the corresponding row is later changed to `DISABLED`, that existing session remains usable until logout, session idle expiration, backend restart, or explicit session invalidation. This limitation is accepted for M1; per-request database checks and distributed session revocation are outside this milestone.

On repeat login, email, display name, avatar URL, and `last_login_at` are synchronized. Missing `sub`, missing email, or an unverified email fails authentication. Missing name falls back to email and avatar is nullable.

---

## Resource Ownership

For MVP:

```text
User
 |
 +-- Applications
       |
       +-- API Keys
       +-- Events
```

Endpoint ownership can either:

1. belong directly to the authenticated user, or
2. belong to an application.

Recommended MVP decision:

- `Application.owner_user_id` (implemented in M2)
- endpoints are also scoped to the same authenticated owner
- all dashboard queries enforce ownership server-side

M2 Application and API-key management resolves the local user ID from the authenticated backend session. Application and API-key repository queries enforce ownership server-side, and missing or cross-user resources return the same `404` response.

If cross-application shared endpoints are required later, introduce a Workspace model.

---

## Session Recommendation

Prefer server-managed authenticated sessions for the dashboard MVP.

Reasons:

- frontend does not need Google access tokens
- simpler token lifecycle
- fewer secrets exposed to browser code
- fits Spring Security OAuth2 Login well

Production session cookie:

```text
HttpOnly
Secure
SameSite=Lax
```

The production frontend and backend must use related custom hosts such as `webhook.<domain>` and `api.webhook.<domain>`. They are different origins, so exact credentialed CORS is required, but they share one registrable site, so `SameSite=Lax` is valid. The cookie is host-only on the API host; no broad `Domain` attribute is set and frontend JavaScript does not need direct access to it.

Sessions expire after 30 minutes of inactivity by default and use session-fixation protection. This is an idle timeout, not an absolute maximum lifetime: activity can extend a session. React sends cookies with `credentials: include`. Session-backed CSRF tokens are obtained from `GET /api/v1/auth/csrf` and required for `POST /api/v1/auth/logout` and future mutations.

Successful login redirects only to configured `${FRONTEND_URL}/app`; failure redirects to `${FRONTEND_URL}/login?error=oauth`. Arbitrary redirect parameters are not accepted.

Only `openid`, `profile`, and `email` scopes are requested. Google tokens remain backend-side and are never stored in application tables or returned by `/api/v1/auth/me`.

---

## Environment Configuration

Backend environment variables:

```text
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=
FRONTEND_URL=https://webhook.<domain>
```

In production, `FRONTEND_URL` is the canonical related custom frontend origin. It must be an HTTP/HTTPS origin with no path, query, or fragment and is used as both the exact trusted CORS origin and the fixed post-login redirect base.

Never commit real values.

Google Cloud Console must configure authorized redirect URIs.

Example local redirect URI:

```text
http://localhost:8080/login/oauth2/code/google
```

Production redirect URI must use HTTPS.

---

## Out of Scope

Version 1 does not include:

- email/password login
- GitHub OAuth
- Microsoft OAuth
- organization SSO
- SAML
- advanced RBAC
- team invitations
- multi-tenant workspaces

These can be introduced after the core webhook platform is stable.
