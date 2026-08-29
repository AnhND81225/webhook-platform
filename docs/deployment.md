# Deployment Strategy

## 1. Goal

Define a deployment approach for the Webhook Platform that is:

- simple enough for an MVP
- production-oriented
- inexpensive
- easy to operate
- compatible with Google OAuth
- safe for webhook delivery workers
- easy to demonstrate in a portfolio

Version 1 should avoid unnecessary infrastructure such as:

- Kubernetes
- multi-region deployment
- Kafka clusters
- self-managed PostgreSQL
- complex service meshes

---

# 2. Recommended Deployment Architecture

Version 1 production deployment:

```text
Vercel
   |
   | HTTPS
   v
AWS EC2
   |
   v
Nginx
   |
   v
Dockerized Spring Boot
   |
   | PostgreSQL :5432
   v
AWS RDS for PostgreSQL
```

The React frontend and backend are deployed separately. The Spring Boot API and delivery worker run in the same stateless container for Version 1.

The backend must be reachable over HTTPS because it handles:

- Google OAuth callbacks
- dashboard APIs
- producer event ingestion
- webhook management

---

# 3. Recommended Platform Choices

## Frontend

Recommended:

```text
Vercel
```

Alternative:

```text
Netlify
Cloudflare Pages
Render Static Site
```

Frontend responsibilities:

- React application
- dashboard UI
- OAuth login entry point
- authenticated API calls

The frontend must not contain:

- Google client secret
- API key hashes
- webhook signing secrets
- database credentials

---

## Backend

Version 1 target:

```text
Dockerized Spring Boot on AWS EC2
Nginx reverse proxy
HTTPS public ingress through Nginx
```

The backend runs:

```text
Spring Boot API
+
Delivery Worker
```

For Version 1, the API and worker can run in the same deployed application process.

Future versions may separate them.

---

## Database

Use AWS RDS for PostgreSQL. It is the only production database target for Version 1.

Required networking posture:

```text
EC2 Security Group
       |
       | TCP 5432
       v
RDS Security Group
```

Prefer `Publicly accessible = No`. Never allow RDS PostgreSQL access from `0.0.0.0/0`; the RDS security group should allow port 5432 from the EC2 backend security group.

Do not run production PostgreSQL in Docker on EC2. Local Docker Compose PostgreSQL remains development-only.

---

# 4. Deployment Environments

Use at least two logical environments:

```text
development
production
```

Optional later:

```text
staging
```

Suggested configuration:

```text
Development
- localhost frontend
- localhost backend
- Docker Compose PostgreSQL
- Google OAuth development callback
- localhost webhook consumers allowed

Production
- HTTPS frontend
- Nginx HTTPS ingress on AWS EC2
- Dockerized Spring Boot backend
- private AWS RDS for PostgreSQL
- production Google OAuth callback
- stricter endpoint URL validation
```

---

# 5. Environment Variables

Backend environment variables should include values similar to:

```text
SPRING_PROFILES_ACTIVE=prod

DATABASE_URL=jdbc:postgresql://<rds-endpoint>:5432/<database>
DATABASE_USERNAME=
DATABASE_PASSWORD=

GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=

FRONTEND_URL=

SESSION_COOKIE_SECURE=true

WEBHOOK_SECRET_ENCRYPTION_KEY=

DELIVERY_WORKER_ENABLED=true
DELIVERY_WORKER_BATCH_SIZE=50
DELIVERY_HTTP_CONNECT_TIMEOUT_MS=5000
DELIVERY_HTTP_READ_TIMEOUT_MS=10000
```

Exact names may differ from implementation.

Secrets must not be committed to Git. The RDS URL shown above is a placeholder shape, not a deployable endpoint.

Do not hard-code the RDS endpoint, AWS region, database credentials, or security group identifiers. M0 supplies credentials through protected EC2 runtime environment configuration. AWS Secrets Manager or Systems Manager Parameter Store may be considered in a later deployment-hardening milestone.

---

# 6. Google OAuth Production Configuration

Google OAuth requires production URLs to be explicitly configured.

Example frontend:

```text
https://webhook.example.com
```

Example backend:

```text
https://api.webhook.example.com
```

Authorized OAuth callback example:

```text
https://api.webhook.example.com/login/oauth2/code/google
```

Configure this exact callback in Google Cloud Console.

Do not use localhost callbacks for production.

---

# 7. CORS Configuration

If frontend and backend use different domains:

```text
Frontend
https://webhook.example.com

Backend
https://api.webhook.example.com
```

configure CORS explicitly.

Allow only known frontend origins.

Example conceptual configuration:

```text
Allowed Origin:
https://webhook.example.com

Allowed Methods:
GET
POST
PUT
PATCH
DELETE

Allow Credentials:
true
```

Do not use:

```text
Access-Control-Allow-Origin: *
```

with authenticated browser sessions.

---

# 8. Session Cookie Configuration

For production dashboard authentication:

```text
HttpOnly=true
Secure=true
SameSite=Lax
```

If frontend/backend deployment topology requires cross-site cookies, evaluate:

```text
SameSite=None
Secure=true
```

but only use it when necessary.

Prefer keeping frontend and backend under the same parent domain when possible.

Example:

```text
webhook.example.com
api.webhook.example.com
```

---

# 9. Database Migrations

Production schema changes must use:

```text
Flyway
or
Liquibase
```

Recommended deployment sequence:

```text
1. deploy migration-compatible backend
2. run database migration
3. start application
4. verify health endpoint
```

Do not depend on:

```text
hibernate.ddl-auto=create
hibernate.ddl-auto=update
```

for production schema management.

Recommended production setting:

```text
hibernate.ddl-auto=validate
```

---

# 10. Health Endpoints

Expose a lightweight application health endpoint.

Recommended:

```http
GET /healthz
```

Expected:

```text
200 OK
```

Optional richer readiness endpoint:

```http
GET /readyz
```

Readiness may verify:

- application initialized
- database reachable
- migrations completed

Do not make the basic liveness endpoint depend on external webhook consumers.

---

# 11. Delivery Worker Deployment

Version 1 runs the delivery worker inside the same Spring Boot deployment.

Example:

```text
Spring Boot Process

+ REST API
+ Scheduled Delivery Worker
```

The worker continuously processes:

```text
PENDING
RETRYING where next_retry_at <= now
```

Important:

If the hosting platform automatically scales the backend to multiple instances, concurrency-safe database claiming must already be implemented.

Use:

```text
FOR UPDATE SKIP LOCKED
```

or an equivalent atomic claim strategy.

---

# 12. EC2 Runtime Availability

The EC2 host must keep the Spring Boot container running for the API and delivery worker. Process supervision, instance recovery, backups, and availability architecture belong to the deployment milestone.

Do not depend on EC2 local disk for durable application state. PostgreSQL state belongs in AWS RDS.

---

# 13. Webhook Delivery Networking

Production outbound webhook delivery must use HTTPS.

Development may allow:

```text
http://localhost
```

Production should reject or protect against unsafe internal destinations.

SSRF protection should eventually block destinations such as:

```text
127.0.0.1
localhost
169.254.169.254
private network ranges
link-local addresses
```

unless explicitly allowed in trusted development environments.

---

# 14. Logging

Use structured application logs.

Include identifiers:

```text
request_id
event_id
delivery_id
endpoint_id
attempt_number
http_status
latency_ms
```

Never log:

```text
raw API keys
Google client secrets
webhook signing secrets
Authorization headers
database passwords
```

---

# 15. Production Error Handling

The deployed backend should return safe client-facing errors.

Do not expose:

- Java stack traces
- SQL errors
- secret values
- internal filesystem paths

Full exception details may remain in protected server logs.

---

# 16. Deployment Pipeline

Recommended GitHub workflow:

```text
feature branch
     |
     v
Pull Request
     |
     v
CI
- backend tests
- frontend tests
- build
     |
     v
merge main
     |
     v
production deployment
```

---

# 17. CI Checks

Before production deployment:

Backend:

```text
mvn test
mvn package
migration validation
```

Frontend:

```text
install dependencies
lint
test
build
```

Optional:

```text
dependency/security scan
```

Deployment must stop if required checks fail.

---

# 18. Post-Deployment Verification

After backend deployment:

```text
GET /healthz
```

Then verify:

```text
Google OAuth login
GET /api/v1/auth/me
database connectivity
event ingestion
delivery worker
HMAC-signed webhook
retry behavior
dashboard data
```

A minimal production smoke test should publish a controlled test event.

---

# 19. Rollback Strategy

Every production deployment should support rollback.

Application rollback:

```text
restore previous backend deployment
```

Frontend rollback:

```text
restore previous frontend deployment
```

Database migrations require more care.

Prefer backward-compatible migrations.

Example safe pattern:

```text
1. add nullable column
2. deploy code supporting both states
3. backfill if required
4. enforce constraint later
```

Avoid destructive migrations coupled to a single deployment.

---

# 20. Recommended Domain Setup

Example:

```text
webhook.example.com
-> frontend

api.webhook.example.com
-> backend
```

Benefits:

- cleaner OAuth setup
- predictable CORS
- cleaner cookie policy
- professional portfolio presentation

A custom domain is optional for the first deployment.

---

# 21. MVP Deployment Recommendation

A practical low-cost setup:

```text
Frontend
Vercel

Backend
Docker on AWS EC2 behind Nginx

Database
AWS RDS for PostgreSQL

Authentication
Google OAuth / OIDC

Source Control + CI
GitHub + GitHub Actions
```

AWS RDS is the only production database target for Version 1.

---

# 22. Initial Deployment Order

Recommended order:

```text
1. Create private AWS RDS for PostgreSQL
2. Allow TCP 5432 from the EC2 backend security group to the RDS security group
3. Configure protected production database credentials on EC2
4. Deploy the Spring Boot container to EC2 behind Nginx
5. Verify /healthz through HTTPS
6. Configure Google OAuth production callback
7. Deploy React frontend to Vercel
8. Configure frontend/backend URLs
9. Verify Google login
10. Create first application
11. Generate producer API key
12. Configure test webhook endpoint
13. Integrate AI Study Assistant
14. Publish test event
15. Verify delivery and retry logs
```

---

# 23. AI Study Assistant Deployment Integration

The existing AI Study Assistant needs configuration such as:

```text
WEBHOOK_PLATFORM_URL=https://api.webhook.example.com
WEBHOOK_PLATFORM_API_KEY=whk_live_xxx
```

The AI Study Assistant must not hard-code these values.

Publishing flow:

```text
AI Study Assistant
       |
       | HTTPS
       | API key
       v
Webhook Platform
```

If the Webhook Platform is temporarily unavailable, the initial integration may fail to publish the event.

A future hardening phase may add a transactional outbox to the AI Study Assistant.

---

# 24. Monitoring After Deployment

Minimum operational signals:

```text
application uptime
event ingestion count
pending deliveries
retrying deliveries
failed deliveries
delivery success rate
average latency
p95 latency
worker backlog
```

The Webhook Dashboard itself should expose several of these metrics.

External infrastructure monitoring may be added later.

---

# 25. Scaling Path

Do not optimize before measurement.

Recommended progression:

```text
Stage 1
1 backend instance
PostgreSQL worker queue

Stage 2
multiple backend/worker instances
SKIP LOCKED claiming

Stage 3
separate API and worker deployment

Stage 4
Redis for justified rate limiting/cache use

Stage 5
Kafka or dedicated queue if measured throughput requires it
```

---

# 26. Deployment Definition of Done

Deployment is considered successful when:

- backend health check passes
- database migrations succeed
- frontend loads successfully
- Google OAuth works
- authenticated dashboard requests work
- producer API key authentication works
- event ingestion works
- asynchronous delivery works
- HMAC headers are present
- retry behavior works
- secrets are not exposed
- failed deployments can be rolled back
