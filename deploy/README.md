# Production deployment bootstrap

This directory supports the first manual production deployment of the backend to an AWS EC2 `t3.small` instance. It does not create AWS infrastructure or publish images. The backend is intentionally bound to the EC2 loopback interface; Nginx is the only public ingress.

## Prerequisites

- An EC2 host in `ap-southeast-1` with Docker, AWS CLI v2, curl, and Nginx installed.
- The EC2 instance role can read the following encrypted SSM Parameter Store values and can authenticate to the ECR repository `webhook-platform-backend`:

  ```text
  /webhook-platform/prod/db/url
  /webhook-platform/prod/db/username
  /webhook-platform/prod/db/password
  /webhook-platform/prod/google/client-id
  /webhook-platform/prod/google/client-secret
  /webhook-platform/prod/signing/master-key
  ```

- An immutable backend image tag (normally the Git commit SHA) already present in ECR.

No secret values belong in this repository, shell history, Docker image, or command output.

## First-time RDS setup

RDS initially provides its default `postgres` database. From the EC2 host, connect to that database with an authorized administration account and create the application database:

```sql
CREATE DATABASE webhook_platform;
```

Then set SSM parameter `/webhook-platform/prod/db/url` to this value, substituting the actual private RDS endpoint:

```text
jdbc:postgresql://<RDS_ENDPOINT>:5432/webhook_platform
```

Do not place RDS passwords in scripts or documentation. The RDS security group must accept TCP 5432 only from the EC2 backend security group; RDS should not be publicly accessible.

On the first backend startup against an empty `webhook_platform` database, Flyway applies V1 through V9. Hibernate then validates the schema (`ddl-auto=validate`) before the application starts. No schema migration is run by this deployment script itself.

## Deploy the backend

Copy this `deploy/` directory to the EC2 host or run the script from a checked-out repository. Provide the exact immutable ECR image tag and the non-secret dashboard origin:

```bash
cd deploy
FRONTEND_URL=https://webhook.<domain> ./deploy-backend.sh <git-sha>
```

`FRONTEND_URL` is required and must be an origin without a path, query, or fragment. Before the final Vercel/custom-domain setup exists, a deliberately supplied temporary origin may be used for a smoke deployment. The final production value must be `https://webhook.<domain>`; do not hard-code localhost or a private domain in the script.

The script:

- gets secrets only from SSM with decryption;
- uses the fixed region `ap-southeast-1` and ECR repository `webhook-platform-backend`;
- writes a mode-600 temporary Docker env file and removes it on exit;
- pulls the exact requested image tag before replacing the current container;
- runs the existing non-root image with `--restart unless-stopped` and a conservative Java heap (`-Xms256m -Xmx640m`) for a 2 GiB host;
- binds Spring Boot only to `127.0.0.1:8080` and fails if `/healthz` does not become healthy.

It does not mount the Docker socket, enable privileged mode, use host networking, or expose port 8080 publicly.

## Nginx bootstrap

Install `deploy/nginx/webhook-platform.conf` as an Nginx site, validate it, then reload Nginx:

```bash
sudo install -m 644 nginx/webhook-platform.conf /etc/nginx/sites-available/webhook-platform.conf
sudo ln -s /etc/nginx/sites-available/webhook-platform.conf /etc/nginx/sites-enabled/webhook-platform.conf
sudo nginx -t
sudo systemctl reload nginx
```

The bootstrap server listens on port 80 and proxies only to `127.0.0.1:8080`, forwarding the host and standard client/proto headers. It deliberately contains no fake TLS certificate paths. Once Route 53/DNS points the production backend hostname to EC2, add HTTPS using Certbot and configure the real production hostname. The Spring `prod` profile already processes forwarded headers for secure OAuth callback generation.
