# GAR location-catalog generator

Download the current official GAR XML file export from the FNS FIAS/GAR portal (Developer → Open data / file exports). Do not add the archive to Git or the Docker image.

The generator is intentionally an explicit build-time operation: it must accept the local archive path, retain only current address objects of official type `город`, resolve their subject, preserve GAR IDs, create deterministic short callback codes and write `src/main/resources/location-catalog.yaml`. It must report counts and additions/removals/renames against the current YAML. The runtime application must never invoke it.

This repository currently has no official archive, so the checked-in YAML remains a clearly marked bootstrap. Provide the XML archive to complete and verify the production catalogue.
