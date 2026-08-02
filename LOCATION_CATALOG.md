# Location catalog

`src/main/resources/location-catalog.yaml` is a project product catalogue, built and reviewed like `vehicle-catalog.yaml`. It contains popular regions and cities with stable codes, display names, `popular` and `sortOrder`; the first ten entries are a product ordering.

It is deliberately not a complete official directory of Russia and has no GAR/FIAS metadata, importer, external API or runtime download. Changes are made by pull request. Rare cities, villages, settlements, auls and stanitsas are entered through «Другой населённый пункт» inside the selected region.

Snapshots are copied to the draft and advertisement. `LocationFormatter` uses those snapshots; legacy `city` remains only as a fallback for rows created before the structured location rollout.
