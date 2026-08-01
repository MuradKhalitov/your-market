#!/usr/bin/env bash
set -Eeuo pipefail

IMAGE="${1:?Usage: ./deploy.sh docker.io/user/your-market:tag}"
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

command -v docker >/dev/null || { echo "docker is required" >&2; exit 1; }
docker compose version >/dev/null || { echo "docker compose plugin is required" >&2; exit 1; }
[[ -f .env.prod ]] || { echo "Missing $SCRIPT_DIR/.env.prod; create it from .env.prod.example" >&2; exit 1; }
[[ -f docker-compose.prod.yml ]] || { echo "Missing docker-compose.prod.yml" >&2; exit 1; }
[[ "$IMAGE" =~ ^[^[:space:]]+:[^/:[:space:]]+$ ]] || { echo "Image must include an explicit tag: registry/user/name:tag" >&2; exit 1; }
if [[ "$IMAGE" == *:latest ]]; then
  echo "WARNING: deploying mutable latest tag; an immutable version tag is recommended."
fi

if [[ -n "${SERVER_DOCKERHUB_TOKEN:-}" ]]; then
  image_user="$(sed -E 's#^(docker\.io/)?([^/]+)/.*#\2#' <<<"$IMAGE")"
  printf '%s' "$SERVER_DOCKERHUB_TOKEN" | docker login --username "$image_user" --password-stdin >/dev/null
fi

previous_image="$(docker inspect --format '{{.Config.Image}}' yourmarket-app 2>/dev/null || true)"

show_failure() {
  echo "Deployment failed. Application logs:" >&2
  docker compose --env-file .env.prod -f docker-compose.prod.yml logs --tail=100 app >&2 || true
  docker compose --env-file .env.prod -f docker-compose.prod.yml ps >&2 || true
}

rollback() {
  if [[ -n "$previous_image" && "$previous_image" != "$IMAGE" ]]; then
    echo "Rolling back to $previous_image"
    YOURMARKET_IMAGE="$previous_image" docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --remove-orphans
  else
    echo "No distinct previous image is available for automatic rollback." >&2
  fi
}

docker pull "$IMAGE"
if ! YOURMARKET_IMAGE="$IMAGE" docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --remove-orphans; then
  show_failure
  rollback || true
  exit 1
fi

deadline=$((SECONDS + 120))
while (( SECONDS < deadline )); do
  running="$(docker inspect --format '{{.State.Running}}' yourmarket-app 2>/dev/null || echo false)"
  health="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' yourmarket-app 2>/dev/null || echo missing)"
  if [[ "$running" != true ]]; then break; fi
  if [[ "$health" == healthy ]]; then
    echo "Deployment successful: $IMAGE"
    docker compose --env-file .env.prod -f docker-compose.prod.yml ps
    docker compose --env-file .env.prod -f docker-compose.prod.yml logs --tail=30 app
    docker image prune --force --filter dangling=true >/dev/null
    exit 0
  fi
  [[ "$health" == unhealthy ]] && break
  sleep 5
done

show_failure
rollback || true
exit 1
