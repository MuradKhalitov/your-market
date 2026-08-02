# YourMarket operations runbook

## Health and logs

```bash
docker compose --env-file .env.prod -f docker-compose.yml ps
docker compose --env-file .env.prod -f docker-compose.yml logs --tail=200 app
curl --fail http://127.0.0.1:8080/actuator/health
```

A healthy HTTP endpoint does not prove Telegram permissions or long-polling delivery. Confirm one app instance, a valid token, disabled webhook, and channel admin rights.

## Deploy and rollback

Use immutable image tags. `deploy/deploy.sh` pulls, waits for health and attempts rollback.

```bash
YOURMARKET_IMAGE=docker.io/owner/your-market:previous-tag \
  docker compose --env-file .env.prod -f docker-compose.yml up -d --force-recreate app
```

Never use `docker compose down -v` in production.

## Reconciliation endpoints

All require `X-Admin-Api-Key` and Telegram-side verification.

- `POST /api/admin/advertisements/payments/{paymentId}/resolve-invoice?retryAllowed=true|false`
- `POST /api/admin/advertisements/{id}/resolve-publication?action=MARK_PUBLISHED&channelMessageId=...`
- `POST /api/admin/advertisements/payments/{paymentId}/resolve-refund?refundConfirmed=true|false`
- `POST /api/admin/advertisements/{id}/resolve-moderation?operationId=...&moderationMessageConfirmed=true|false&moderationMessageId=...`

## Capacity and stuck-state checks

```bash
df -h
docker system df
docker compose --env-file .env.prod -f docker-compose.yml ps
docker inspect -f '{{.RestartCount}}' yourmarket-app
du -sh /var/lib/docker/containers/*/*-json.log
```

Alert or investigate disk usage above 80%, a restart loop, Telegram 403/429, any reconciliation state, missing scheduler success or PostgreSQL failure. See `OBSERVABILITY.md`.

An uncertain refund is never retried automatically. `refundConfirmed=false` returns it to `SUCCEEDED` without issuing another refund.

## Scheduled recovery

- `PAID` advertisements are retried after restart.
- stale invoice claims become `SEND_UNKNOWN`, never automatic resend;
- stale refund claims become `REFUND_RECONCILIATION_REQUIRED`, never automatic retry.

Configure host log rotation and monitor `df -h` / `docker system df`; default Docker JSON logs can exhaust a small VPS.
