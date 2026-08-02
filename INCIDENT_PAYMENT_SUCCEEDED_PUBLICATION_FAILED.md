# Incident: Stars payment succeeded, publication not completed

1. Do not create a second payment or resend an invoice.
2. Find payment and advertisement IDs in logs; confirm `payments.status=SUCCEEDED`.
3. Check the channel manually before retrying.

Status handling:

- `PAID`: scheduler retry is safe and resumes after restart.
- `PUBLICATION_FAILED`: protected retry after checking the channel.
- `PUBLICATION_IN_PROGRESS`: wait for recovery; no parallel retry.
- `PUBLICATION_RECONCILIATION_REQUIRED`: do not retry automatically.

If a post exists, use protected `MARK_PUBLISHED` with its verified positive message ID. If Telegram definitely has no post and no progress was persisted, use `RETRY_AFTER_VERIFICATION`.

Record operator, payment ID, advertisement ID, observed message IDs and decision in the incident ticket. Do not update payment rows directly in production.

See `OBSERVABILITY.md` and `TELEGRAM_ERROR_HANDLING.md` for signals and safe failure classification.
