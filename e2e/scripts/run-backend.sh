#!/usr/bin/env bash
# Recreate the scratch database, then run the backend with the e2e profile (Playwright's webServer).
set -euo pipefail
cd "$(dirname "$0")/../../backend"
export PGPASSWORD="${PGPASSWORD:-mmv}"
PSQL="psql -h ${PGHOST:-localhost} -p ${PGPORT:-5432} -U ${PGUSER:-mmv} -d postgres -v ON_ERROR_STOP=1 -q"
$PSQL -c "DROP DATABASE IF EXISTS mmv_e2e WITH (FORCE)" >/dev/null
$PSQL -c "CREATE DATABASE mmv_e2e" >/dev/null
echo "[e2e] fresh database mmv_e2e; starting backend"
exec ./gradlew bootRun --no-daemon -q --args='--spring.profiles.active=e2e'
