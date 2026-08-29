# AGENTS.md

## Project Overview

This repository contains a standalone Webhook Delivery Platform.

The platform receives domain events from producer applications and reliably delivers those events to subscribed HTTP webhook endpoints.

The first real producer is an existing AI Study Assistant.

Initial producer event types:

- `ai.solution.completed`
- `ai.grade.completed`
- `ai.answer.reported`

The project is intended to demonstrate production-oriented backend engineering concepts such as:

- asynchronous processing
- reliable webhook delivery
- retry with backoff
- delivery observability
- idempotency
- concurrency control
- API key authentication
- HMAC request signing

---

## Repository Structure

Preferred structure:

```text
webhook-platform/
├── AGENTS.md
├── DESIGN.md
├── docs/
│   ├── requirements.md
│   ├── domain-model.md
│   ├── api-spec.md
│   ├── architecture.md
│   ├── retry-policy.md
│   ├── security.md
│   ├── authentication.md
│   ├── deployment.md
│   └── testing.md
├── backend/
└── frontend/
```

---

## Architecture Rules

### Version 1

Use a modular monolith.

Do not introduce:

- microservices
- Kafka
- Redis
- Kubernetes
- distributed workflow engines
- multi-region infrastructure

unless explicitly requested by a later requirement.

The backend uses PostgreSQL as both:

- the persistent data store
- the backing store for pending delivery work

Webhook delivery is performed asynchronously by a background worker.

---

## Backend Technology

Preferred backend stack:

- Java 17+
- Spring Boot
- Spring Web
- Spring Data JPA
- PostgreSQL
- Flyway or Liquibase migrations
- Maven
- WebClient or another supported HTTP client

Use the repository's actual configured versions once the project is initialized.

---

## Frontend Technology

Preferred frontend stack:

- React
- TypeScript
- component-driven architecture

`DESIGN.md` is the visual source of truth.

Do not invent new:

- colors
- typography scales
- spacing systems
- border radii
- status semantics

unless explicitly required.

---

## Package Structure

Prefer package-by-feature.

Example:

```text
com.example.webhook
├── application
├── apikey
├── endpoint
├── subscription
├── event
├── delivery
├── retry
├── signature
├── security
└── common
```

Avoid a repository-wide structure containing only:

```text
controller/
service/
repository/
entity/
```

---

## Domain Rules

Keep the following concepts distinct:

- `WebhookEvent`: one immutable event received from a producer
- `WebhookDelivery`: delivery of one event to one endpoint
- `WebhookDeliveryAttempt`: one physical HTTP request for a delivery

Do not merge these concepts into a single entity.

A single event may create multiple deliveries.

A single delivery may create multiple attempts.

---

## Delivery Guarantee

Version 1 provides **at-least-once delivery**.

Duplicate webhook delivery is possible.

Do not claim exactly-once delivery.

Consumers should be able to deduplicate requests using a stable delivery identifier.

---

## Persistence Rules

Use database migrations for schema changes.

Do not rely on destructive Hibernate automatic schema updates.

Prefer PostgreSQL `JSONB` for arbitrary event payloads.

Store only the minimum data required for delivery and observability.

Avoid storing sensitive user content in webhook payloads unless explicitly required.

---

## Concurrency Rules

Multiple delivery workers must not process the same pending delivery concurrently.

For PostgreSQL-backed worker claiming, prefer a locking strategy equivalent to:

```sql
FOR UPDATE SKIP LOCKED
```

or another clearly documented atomic claim mechanism.

Do not implement naive polling that allows two workers to send the same delivery at the same time under normal operation.

At-least-once semantics still mean duplicates may occur after crash or timeout scenarios.

---

## Security Rules

Dashboard users authenticate with **Google OAuth 2.0 / OpenID Connect**.

Producer applications authenticate with API keys.

Never persist raw API keys after initial creation.

Webhook requests to consumers must support HMAC signing.

Secrets must not be committed to source control.

Do not log:

- raw API keys
- raw signing secrets
- authorization headers

Mask sensitive values in logs and dashboard responses.

---

## Coding Principles

- Inspect existing code before creating a new abstraction.
- Prefer the smallest change that satisfies the documented requirement.
- Avoid speculative abstractions.
- Do not refactor unrelated code.
- Keep controllers thin.
- Business logic belongs in services/domain components.
- External HTTP delivery logic must be isolated from controllers.
- Retry policy must be centralized rather than duplicated.
- State transitions must be explicit.
- Avoid silent exception swallowing.

---

## API Rules

Producer event ingestion and dashboard management APIs are separate concerns.

Use stable versioned routes such as:

```text
/api/v1/...
```

API responses should use consistent error formatting.

Do not expose internal database details unnecessarily.

---

## Testing Rules

Before declaring a backend task complete:

1. run unit tests
2. run integration tests relevant to the task
3. run backend build
4. inspect the final diff
5. confirm no unrelated files changed

Critical logic must have tests:

- event idempotency
- delivery creation
- retry decisions
- retry timing
- delivery state transitions
- HMAC signature generation/verification
- worker concurrency/claiming behavior

Do not claim verification that was not actually executed.

---

## Definition of Done

A task is complete only when:

- documented requirements are satisfied
- acceptance criteria are covered
- relevant tests pass
- build succeeds
- migrations are valid
- no unrelated code changes remain
- implementation does not violate the documented MVP scope

At task completion report:

1. files changed
2. behavior implemented
3. design decisions
4. tests executed
5. remaining limitations

---

## Deployment Rules

Version 1 deployment architecture:

```text
Frontend: Vercel
Backend: Docker on AWS EC2
Reverse Proxy: Nginx
TLS: HTTPS
Database: AWS RDS for PostgreSQL
CI/CD: GitHub Actions
```

Backend code must remain container-friendly and stateless.

Do not depend on EC2 local disk for durable application state.

Do not introduce ECS, EKS, or Kubernetes unless explicitly requested.

Spring Boot port 8080 must not be exposed publicly in production; public backend traffic should pass through Nginx over HTTPS.

Deployment-specific behavior must follow `docs/deployment.md`.

### RDS Rules

Production database is AWS RDS for PostgreSQL.

- Prefer `Publicly accessible = No`.
- RDS port 5432 must not be open to `0.0.0.0/0`.
- Allow PostgreSQL access from the EC2 backend Security Group.
- Database credentials must come from environment or protected secret storage.
- Flyway owns production schema migrations.
- Do not run production PostgreSQL inside Docker on EC2.
- RDS must not be publicly exposed unless there is an explicit documented reason.
