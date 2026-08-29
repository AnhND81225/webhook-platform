# Webhook Delivery Platform

Current milestone: **M0 — Repository Bootstrap**

This standalone platform will receive domain events from producer applications and reliably deliver them to subscribed HTTP webhook endpoints. It centralizes delivery concerns such as asynchronous processing, retries, observability, idempotency, API-key authentication, and HMAC signing. M0 provides only the technical foundation; webhook business functionality is not implemented yet.

## Architecture and technology

The MVP is a modular monolith: a React dashboard calls a Spring Boot backend, which persists durable state in PostgreSQL. A PostgreSQL-backed worker will be added in a later milestone. Production uses Vercel for the frontend and Nginx in front of a Dockerized backend on AWS EC2, connected to AWS RDS for PostgreSQL.

- Backend: Java 17, Spring Boot 3.5, Maven, Spring Web, Validation, Data JPA, Security, OAuth2 Client, Actuator, PostgreSQL, Flyway
- Frontend: React, TypeScript, Vite, React Router
- Authentication foundation: Google OAuth 2.0 / OpenID Connect with server-managed sessions in a later milestone
- Runtime: Docker for the backend; Docker Compose PostgreSQL is local-development only

Environment topology:

```text
Local:      React -> Spring Boot -> Docker Compose PostgreSQL
Production: Vercel -> HTTPS -> AWS EC2 -> Nginx -> Docker Spring Boot -> AWS RDS PostgreSQL
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
| `GOOGLE_CLIENT_ID` | Google OAuth client ID; required when the `oauth` profile is active |
| `GOOGLE_CLIENT_SECRET` | Google OAuth client secret; required when the `oauth` profile is active |
| `FRONTEND_URL` | Allowed dashboard origin and post-login target foundation |
| `WEBHOOK_SECRET_ENCRYPTION_KEY` | Reserved for a later signing-secret milestone; unused in M0 |
| `SPRING_PROFILES_ACTIVE` | Use `dev`, `dev,oauth`, `prod`, or `prod,oauth` |
| `VITE_API_BASE_URL` | Frontend backend base URL; defaults to `http://localhost:8080` |

Never commit a populated `.env` file.

## Database setup

Start the local-development-only PostgreSQL container:

```bash
docker compose up -d postgres
```

If port 5432 is already occupied, use `POSTGRES_PORT=5433 docker compose up -d postgres` and set `DATABASE_URL=jdbc:postgresql://localhost:5433/webhook_platform` for the backend.

Production uses AWS RDS for PostgreSQL and must not run PostgreSQL in Docker on the EC2 backend host. The RDS endpoint and credentials are supplied through protected EC2 runtime configuration and are never committed or baked into the image. Flyway is enabled. M0 intentionally contains no SQL migrations because it defines no business tables. Hibernate schema mode is `validate`, never `update`.

## Backend

Run tests and build:

```bash
cd backend
mvn test
mvn package
```

The repository path should not contain `:` on Unix because Java uses it as the classpath separator. If working from such a path is unavoidable, run tests with `mvn test -DforkCount=0`.

Run with the default development profile after PostgreSQL is available:

```bash
mvn spring-boot:run
curl -i http://localhost:8080/healthz
```

To enable the Google OAuth/OIDC client foundation, set `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET`, then activate `dev,oauth`. User persistence and login provisioning are intentionally deferred to M1.

## Frontend

```bash
cd frontend
npm install
npm run dev
```

Create a production build with `npm run build`. The M0 UI contains only the routing shell, login placeholder, authenticated-layout placeholder, error boundary, and API configuration foundation.

## Docker

Build the backend image:

```bash
docker build -t webhook-platform-backend:m0 backend
```

For a local image smoke test, run it against the local PostgreSQL instance:

```bash
docker run --rm -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DATABASE_URL=jdbc:postgresql://host.docker.internal:5432/webhook_platform \
  -e DATABASE_USERNAME=webhook \
  -e DATABASE_PASSWORD=webhook \
  webhook-platform-backend:m0
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

## M0 status and MVP scope

M0 establishes compilable backend/frontend projects, PostgreSQL and Flyway configuration, `/healthz`, OAuth configuration foundations, and a production-oriented backend Docker image. It does not implement users, applications, API keys, endpoints, subscriptions, events, deliveries, attempts, workers, retries, HMAC signing, dashboard business APIs/pages, or AI Study Assistant integration.
