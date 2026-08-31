#!/usr/bin/env bash
# Deploy one immutable backend image to the production EC2 host.
# This script reads secrets from SSM Parameter Store; it never prints them.

set -euo pipefail

readonly AWS_REGION="ap-southeast-1"
readonly ECR_REPOSITORY="webhook-platform-backend"
readonly CONTAINER_NAME="webhook-platform-backend"
readonly HEALTH_URL="http://127.0.0.1:8080/healthz"

usage() {
  printf 'Usage: FRONTEND_URL=https://webhook.<domain> %s <immutable-image-tag>\n' "$0" >&2
  exit 64
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    printf 'Required command is unavailable: %s\n' "$1" >&2
    exit 69
  }
}

require_nonempty() {
  local name="$1"
  local value="$2"
  if [[ -z "$value" || "$value" == "None" || "$value" == "null" ]]; then
    printf 'Required value is unavailable: %s\n' "$name" >&2
    exit 65
  fi
}

read_ssm_parameter() {
  local parameter_name="$1"
  local value
  value="$(aws ssm get-parameter \
    --region "$AWS_REGION" \
    --name "$parameter_name" \
    --with-decryption \
    --query 'Parameter.Value' \
    --output text)"
  require_nonempty "$parameter_name" "$value"
  printf '%s' "$value"
}

write_env() {
  local name="$1"
  local value="$2"
  if [[ "$value" == *$'\n'* || "$value" == *$'\r'* ]]; then
    printf 'Invalid newline in %s\n' "$name" >&2
    exit 65
  fi
  printf '%s=%s\n' "$name" "$value" >> "$ENV_FILE"
}

if [[ $# -ne 1 ]]; then
  usage
fi

readonly IMAGE_TAG="$1"
if [[ -z "$IMAGE_TAG" || "$IMAGE_TAG" == "latest" || "$IMAGE_TAG" == */* || "$IMAGE_TAG" == *:* || "$IMAGE_TAG" == *[[:space:]]* ]]; then
  printf 'Provide one immutable image tag or Git SHA; "latest" is not allowed.\n' >&2
  exit 64
fi

: "${FRONTEND_URL:?FRONTEND_URL is required}"
if [[ ! "$FRONTEND_URL" =~ ^https?://[^/?#]+$ ]]; then
  printf 'FRONTEND_URL must be an absolute origin without a path, query, or fragment.\n' >&2
  exit 64
fi

require_command aws
require_command docker
require_command curl

readonly ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"
require_nonempty "AWS account id" "$ACCOUNT_ID"
readonly ECR_REGISTRY="${ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
readonly IMAGE_URI="${ECR_REGISTRY}/${ECR_REPOSITORY}:${IMAGE_TAG}"

# Fetch every required parameter before interrupting the current healthy container.
readonly DATABASE_URL="$(read_ssm_parameter /webhook-platform/prod/db/url)"
readonly DATABASE_USERNAME="$(read_ssm_parameter /webhook-platform/prod/db/username)"
readonly DATABASE_PASSWORD="$(read_ssm_parameter /webhook-platform/prod/db/password)"
readonly GOOGLE_CLIENT_ID="$(read_ssm_parameter /webhook-platform/prod/google/client-id)"
readonly GOOGLE_CLIENT_SECRET="$(read_ssm_parameter /webhook-platform/prod/google/client-secret)"
readonly WEBHOOK_SECRET_ENCRYPTION_KEY="$(read_ssm_parameter /webhook-platform/prod/signing/master-key)"

umask 077
ENV_FILE="$(mktemp /tmp/webhook-platform-backend.XXXXXX.env)"
trap 'rm -f "$ENV_FILE"' EXIT

write_env SPRING_PROFILES_ACTIVE prod
write_env DATABASE_URL "$DATABASE_URL"
write_env DATABASE_USERNAME "$DATABASE_USERNAME"
write_env DATABASE_PASSWORD "$DATABASE_PASSWORD"
write_env GOOGLE_CLIENT_ID "$GOOGLE_CLIENT_ID"
write_env GOOGLE_CLIENT_SECRET "$GOOGLE_CLIENT_SECRET"
write_env WEBHOOK_SECRET_ENCRYPTION_KEY "$WEBHOOK_SECRET_ENCRYPTION_KEY"
write_env FRONTEND_URL "$FRONTEND_URL"

printf 'Authenticating to ECR in %s...\n' "$AWS_REGION"
aws ecr get-login-password --region "$AWS_REGION" | docker login --username AWS --password-stdin "$ECR_REGISTRY" >/dev/null

printf 'Pulling backend image tag %s...\n' "$IMAGE_TAG"
docker pull "$IMAGE_URI" >/dev/null

if docker container inspect "$CONTAINER_NAME" >/dev/null 2>&1; then
  printf 'Replacing the existing backend container...\n'
  docker rm --force "$CONTAINER_NAME" >/dev/null
fi

printf 'Starting backend container...\n'
docker run --detach \
  --name "$CONTAINER_NAME" \
  --restart unless-stopped \
  --publish 127.0.0.1:8080:8080 \
  --env-file "$ENV_FILE" \
  --env 'JAVA_TOOL_OPTIONS=-Xms256m -Xmx640m -XX:+ExitOnOutOfMemoryError' \
  "$IMAGE_URI" >/dev/null

printf 'Waiting for backend health check...\n'
for _ in $(seq 1 20); do
  if curl --fail --silent --show-error --max-time 3 "$HEALTH_URL" >/dev/null; then
    printf 'Deployment succeeded: backend is healthy on 127.0.0.1:8080.\n'
    exit 0
  fi
  sleep 3
done

printf 'Deployment failed: backend health check did not become healthy.\n' >&2
exit 1
