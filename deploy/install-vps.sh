#!/usr/bin/env bash
set -Eeuo pipefail

[[ "$(uname -s)" == Linux ]] || { echo "This installer supports Linux only." >&2; exit 1; }
[[ -r /etc/os-release ]] || { echo "Cannot determine Linux distribution." >&2; exit 1; }
. /etc/os-release
case "${ID:-}" in
  ubuntu|debian) ;;
  *) echo "Unsupported distribution: ${ID:-unknown}. Install Docker Engine and Compose manually." >&2; exit 1 ;;
esac

if ! command -v docker >/dev/null || ! docker compose version >/dev/null 2>&1; then
  cat <<'EOF'
Docker Engine with the Compose plugin is required.
Install it from the official instructions, then rerun this script:
  https://docs.docker.com/engine/install/ubuntu/
  https://docs.docker.com/engine/install/debian/
This script intentionally does not modify repositories, firewall rules, or start a downloaded installer automatically.
EOF
  exit 1
fi

install_dir=/opt/yourmarket
if [[ ! -d "$install_dir" ]]; then
  sudo mkdir -p "$install_dir"
  sudo chown "$(id -u):$(id -g)" "$install_dir"
fi

cat <<EOF
VPS directory is ready: $install_dir
Copy these files there:
  deploy/docker-compose.prod.yml
  deploy/deploy.sh
  deploy/.env.prod.example
Then create $install_dir/.env.prod from the example, fill secrets directly on the VPS,
and run: chmod +x $install_dir/deploy.sh
PostgreSQL is not exposed by the production Compose file. No firewall rules were changed.
EOF
