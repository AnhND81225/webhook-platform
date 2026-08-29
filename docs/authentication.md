# Authentication Decision

## Decision

Use **Google OAuth 2.0 / OpenID Connect** for Webhook Dashboard user authentication.

Producer applications continue to authenticate separately with platform API keys.

These are two distinct authentication systems:

```text
Developer -> Google OAuth -> Dashboard

Producer Application -> API Key -> Event Ingestion API
```

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

- `Application.owner_user_id`
- endpoints are also scoped to the same authenticated owner
- all dashboard queries enforce ownership server-side

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

---

## Environment Configuration

Backend environment variables:

```text
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=
```

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
