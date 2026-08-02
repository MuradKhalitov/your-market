# Telegram error handling

Errors are classified for safe metrics/logs as `400`, `403`, `429`, `5xx`, `timeout`, `network`, or `other`.

- `400` and `403` are confirmed permanent failures: no endless automatic retry; fix data or bot permissions.
- `429` is transient: honour Telegram `retry_after` where available and never run a tight loop.
- Network/5xx failures can be ambiguous. Persisted claims and reconciliation are used rather than blind re-send.
- A timeout never proves that Telegram did not execute an operation.

Telegram API calls remain outside PostgreSQL transactions. Retries must use the existing persisted operation owner and must not bypass reconciliation.
