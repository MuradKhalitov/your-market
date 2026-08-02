# Moderation reconciliation

`SEND_UNKNOWN` means Telegram moderation-message delivery cannot be proven automatically. A normal retry is blocked.

Inspect the current operation:

```bash
docker compose --env-file .env.prod -f deploy/docker-compose.prod.yml exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "select id,status,moderation_submission_status,moderation_operation_id,moderation_message_id,moderation_phase from advertisements where id = '\''<ADVERTISEMENT_ID>'\'';"'
```

After checking the moderation chat, call the protected endpoint with the current operation ID:

```bash
curl -X POST 'http://127.0.0.1:8080/api/admin/advertisements/<ADVERTISEMENT_ID>/resolve-moderation?operationId=<OPERATION_ID>&moderationMessageConfirmed=true&moderationMessageId=<POSITIVE_MESSAGE_ID>' -H 'X-Admin-Api-Key: <ADMIN_API_KEY>'
```

For a proven non-send, pass `moderationMessageConfirmed=false`. The endpoint never sends Telegram messages. A later normal retry can send only the missing action message when moderation media IDs were already saved.
