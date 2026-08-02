# PostgreSQL backup and restore

## Backup

Run on the VPS and copy encrypted backups off-host:

```bash
docker compose --env-file .env.prod -f docker-compose.yml exec -T postgres \
  sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' \
  > /opt/yourmarket/backups/yourmarket-$(date +%F-%H%M%S).dump
```

Test restore to a separate database regularly. Stop only the app before a real restore, and never treat `down -v` as a backup procedure.

## Empty-database baseline reset

The Stars-only Liquibase baseline is for a new empty database. Resetting deletes all payments and advertisements. Take and verify a backup, obtain explicit approval, stop services, remove only the PostgreSQL volume, then start compose again.
