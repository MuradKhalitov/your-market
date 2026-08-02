# Observability

Actuator exposes only `health` and `info` on localhost. `/actuator/health/liveness` checks JVM/application liveness; `/actuator/health/readiness` checks readiness state and PostgreSQL. Readiness does not claim that Telegram polling works.

Micrometer metrics use the `yourmarket.` prefix and bounded tags only: `result`, `error_class`, `scheduler`. Payment, user, advertisement IDs, payloads and charge IDs are never tags.

Important signals: Telegram 403 > 0, sustained 429, any invoice/refund reconciliation state, a paid unpublished advertisement older than 15 minutes, missing scheduler success, database unavailable, disk >80%, or an app restart loop.

```bash
df -h
docker system df
docker compose --env-file .env.prod -f deploy/docker-compose.prod.yml ps
docker inspect -f '{{.RestartCount}}' yourmarket-app
du -sh /var/lib/docker/containers/*/*-json.log
```

`yourmarket.telegram.updates.last.success.timestamp` is an operational gauge. If it stops changing while activity is expected, check long polling, webhook and bot token.
