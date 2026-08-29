# Webhook Delivery Platform

Current milestone: **M2 — Application + API Key**

This standalone platform will receive domain events from producer applications and reliably deliver them to subscribed HTTP webhook endpoints. M2 adds owner-scoped producer Applications and reveal-once API-key lifecycle management on top of the M1 Google OIDC foundation. Event ingestion and webhook delivery are not implemented yet.

## Architecture and technology

The MVP is a modular monolith: a React dashboard calls a Spring Boot backend, which persists durable state in PostgreSQL. A PostgreSQL-backed worker will be added in a later milestone. Production uses Vercel for the frontend and Nginx in front of a Dockerized backend on AWS EC2, connected to AWS RDS for PostgreSQL. Authenticated production deployments require related custom domains such as `webhook.<domain>` and `api.webhook.<domain>`.

- Backend: Java 17, Spring Boot 3.5, Maven, Spring Web, Validation, Data JPA, Security, OAuth2 Client, Actuator, PostgreSQL, Flyway
- Frontend: React, TypeScript, Vite, React Router
- Authentication: Google OAuth 2.0 / OpenID Connect with server-managed sessions and local users
- Runtime: Docker for the backend; Docker Compose PostgreSQL is local-development only

Environment topology:

```text
Local:      React -> Spring Boot -> Docker Compose PostgreSQL
Production: Vercel -> webhook.<domain> -> HTTPS -> api.webhook.<domain> -> Nginx/EC2 -> Docker Spring Boot -> AWS RDS PostgreSQL
```

Production RDS should not be publicly accessible. Its security group should allow TCP 5432 from the EC2 backend security group, never from `0.0.0.0/0`.

Kafka, Redis, RabbitMQ, microservices, Kubernetes, ECS, and EKS are outside the MVP.

## Repository structure

```text
.
├── AGENTS.md
├── DESIGN.md
├── README.md
├── docs/
├── backend/
└── frontend/
```

The backend root package is `com.webhookplatform.webhook`. Feature packages will be introduced with their first real implementation rather than preserved with placeholder source files.

## Prerequisites

- Java 17 or newer
- Maven 3.6.3 or newer
- Node.js 20.19 or newer
- npm 10 or newer
- Docker and Docker Compose for the local PostgreSQL and image build workflows

## Environment variables

Copy `.env.example` to a local `.env` and provide values as needed. Local defaults are available for the development database, but production must set all database values explicitly.

| Variable | Purpose |
| --- | --- |
| `DATABASE_URL` | PostgreSQL JDBC URL; local example: `jdbc:postgresql://localhost:5432/webhook_platform`, production shape: `jdbc:postgresql://<rds-endpoint>:5432/webhook` |
| `DATABASE_USERNAME` | PostgreSQL username |
| `DATABASE_PASSWORD` | PostgreSQL password |
| `GOOGLE_CLIENT_ID` | Google OAuth client ID; required to start the backend |
| `GOOGLE_CLIENT_SECRET` | Google OAuth client secret; required to start the backend |
| `FRONTEND_URL` | Exact canonical dashboard origin and trusted post-login target; production uses `https://webhook.<domain>` |
| `SESSION_TIMEOUT` | Backend session idle timeout; defaults to `30m` |
| `WEBHOOK_SECRET_ENCRYPTION_KEY` | Reserved for a later signing-secret milestone; unused in M2 |
| `SPRING_PROFILES_ACTIVE` | Use `dev` locally or `prod` in production |
| `VITE_API_BASE_URL` | Frontend backend base URL; defaults to `http://localhost:8080` |

Never commit a populated `.env` file.

The Vercel-provided `*.vercel.app` hostname may be used for previews or non-authenticated verification, but it is not the production authenticated-dashboard origin when the API uses an unrelated custom domain. The production frontend and API must share the same registrable site. The session cookie remains scoped to the API host and is never exposed to frontend JavaScript.

## Database setup

Start the local-development-only PostgreSQL container:

```bash
docker compose up -d postgres
```

If port 5432 is already occupied, use `POSTGRES_PORT=5433 docker compose up -d postgres` and set `DATABASE_URL=jdbc:postgresql://localhost:5433/webhook_platform` for the backend.

Production uses AWS RDS for PostgreSQL and must not run PostgreSQL in Docker on the EC2 backend host. Flyway migrations `V1__create_users.sql` and `V2__create_applications_and_api_keys.sql` own the M1/M2 schema. Hibernate schema mode is `validate`, never `update`.

## Backend

Run tests and build:

```bash
cd backend
mvn test
mvn package
```

The repository path must not contain `:` on Unix because Java classpaths and Vite virtual-module resolution interpret it as a separator. Use a colon-free clone or working copy for backend and frontend test execution.

Set Google OAuth credentials, then run with the default development profile after PostgreSQL is available:

```bash
mvn spring-boot:run
curl -i http://localhost:8080/healthz
```

Configure `http://localhost:8080/login/oauth2/code/google` as the local Google callback. The frontend origin defaults to `http://localhost:5173`.

## Frontend

```bash
cd frontend
npm install
npm run dev
```

Create a production build with `npm run build`. Run authentication UI tests with `npm test`. `/app` verifies the backend session before rendering and redirects unauthenticated users to `/login`.

## Docker

Build the backend image:

```bash
docker build -t webhook-platform-backend:m2 backend
```

For a local image smoke test, run it against the local PostgreSQL instance:

```bash
docker run --rm -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DATABASE_URL=jdbc:postgresql://host.docker.internal:5432/webhook_platform \
  -e DATABASE_USERNAME=webhook \
  -e DATABASE_PASSWORD=webhook \
  -e GOOGLE_CLIENT_ID=local-client-id \
  -e GOOGLE_CLIENT_SECRET=local-client-secret \
  -e FRONTEND_URL=http://localhost:5173 \
  webhook-platform-backend:m2
```

Production uses the same image with its RDS URL and credentials supplied through protected EC2 runtime configuration. Secrets are never baked into the image.

## Documentation

- [Requirements](docs/requirements.md)
- [Domain model](docs/domain-model.md)
- [API specification](docs/api-spec.md)
- [Architecture](docs/architecture.md)
- [Retry policy](docs/retry-policy.md)
- [Security](docs/security.md)
- [Authentication](docs/authentication.md)
- [Deployment](docs/deployment.md)
- [Testing](docs/testing.md)
- [Visual design system](DESIGN.md)

## M2 status and MVP scope

M2 implements authenticated Application create/list/detail/update APIs, owner-scoped authorization, Application status and environment, API-key creation/list/revocation APIs, SHA-256 storage of high-entropy keys, and reveal-once raw credentials. The raw key is never persisted and cannot be recovered if its one creation response is lost. The full React Applications/API Keys dashboard remains deferred to M11.

M2 does not implement producer API-key authentication, event ingestion, endpoints, subscriptions, events, deliveries, attempts, workers, retries, HMAC signing, API-key expiration/rotation, or AI Study Assistant integration.

M1 operational limitation: changing a user from `ACTIVE` to `DISABLED` prevents new login sessions but does not immediately revoke a session that is already authenticated. That session remains usable until logout, idle expiration (30 minutes by default), backend restart, or explicit session invalidation. Immediate distributed revocation is outside M1.
