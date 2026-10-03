#!/usr/bin/env bash
# Runs RealPostgrestSyncTest against the local Supabase.
# Start the local stack first: supabase start (Docker must be running).
set -euo pipefail

cd "$(dirname "$0")/.."

status=$(supabase status -o env) || {
  echo "The local Supabase is not running. Run: supabase start" >&2
  exit 1
}
value() { printf '%s\n' "$status" | sed -n "s/^$1=\"\{0,1\}\([^\"]*\)\"\{0,1\}$/\1/p"; }

SUPABASE_IT_URL=$(value API_URL)
SUPABASE_IT_SERVICE_KEY=$(value SERVICE_ROLE_KEY)
export SUPABASE_IT_URL SUPABASE_IT_SERVICE_KEY

# --rerun runs the test even when Gradle has a cached result.
./gradlew :mobile:shared:jvmTest --rerun --tests '*RealPostgrestSyncTest*' "$@"
