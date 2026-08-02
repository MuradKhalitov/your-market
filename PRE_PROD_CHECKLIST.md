# YourMarket — pre-production checklist

## Configuration and access

- [ ] `SPRING_PROFILES_ACTIVE=production`.
- [ ] `ADMIN_API_KEY` is a high-entropy secret.
- [ ] Telegram and PostgreSQL secrets exist only in ignored `.env.prod`.
- [ ] `PUBLICATION_PRICE_STARS` is an integer greater than zero.
- [ ] With moderation enabled, moderation chat and administrator IDs are configured.
- [ ] The bot is channel administrator with post/delete permissions.
- [ ] Exactly one app instance uses the bot token; webhook is disabled for long polling.

## Deployment

- [ ] Use an immutable image tag, not `latest`.
- [ ] Validate compose config and wait for app health.
- [ ] PostgreSQL has no public port; app port is bound to localhost only.
- [ ] A recent backup and a tested restore procedure exist.

## Telegram smoke test

- [ ] Draft, validation, preview and cancellation work.
- [ ] A test invoice uses `XTR`, one price item and no provider token.
- [ ] Successful payment reaches moderation or publication.
- [ ] Admin approve/reject, media deletion and expiration work.

## Monitoring baseline

- [ ] Centralize logs; alert on `SEND_UNKNOWN`, publication/refund reconciliation and unhealthy containers.
- [ ] Monitor VPS disk space and Docker log growth.
- [ ] Docker JSON log rotation and `APP_MEM_LIMIT`/`POSTGRES_MEM_LIMIT` are configured for VPS capacity.
- [ ] Operator procedure exists for Telegram 403/429, scheduler failure and paid-but-unpublished advertisements older than 15 minutes.
