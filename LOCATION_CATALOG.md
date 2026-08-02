# Location catalog

`location-catalog.yaml` is the only runtime source. It is loaded locally with a safe YAML parser; the bot neither downloads nor parses GAR at startup.

The current file is explicitly a **bootstrap**, not an official GAR import: `source=BOOTSTRAP_NO_GAR_ARCHIVE`, `sourceVersion=not-generated`. It contains 10 priority regions and 37 cities. Dagestan, Chechnya and Ingushetia are first; their current bootstrap city lists are respectively 10, 6 and 5 entries. Supply an official GAR XML archive to produce the complete catalogue before production rollout.

Snapshots (`regionNameSnapshot`, `cityNameSnapshot`) protect old listings. `customLocality` is retained as raw text and escaped only for Telegram HTML. Legacy `city` is retained for older rows as a display fallback; no legacy value is guessed into a structured location.

After importing GAR, review the deterministic YAML diff, its reported region/city totals, source version and removed/renamed objects. Do not commit GAR archives. To audit legacy data before tightening policy:

```sql
SELECT count(*) FROM advertisements WHERE city IS NOT NULL AND region_code IS NULL;
SELECT count(*) FROM advertisement_drafts WHERE city IS NOT NULL AND region_code IS NULL;
```

Rollback is the Git revert of the generated YAML plus Liquibase rollback in a controlled migration window; do not delete legacy advertisements automatically.
