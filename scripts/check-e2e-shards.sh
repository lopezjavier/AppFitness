#!/usr/bin/env bash
# E2E shard guard (T-032): every top-level Maestro flow in tests/e2e/*.yaml must be listed in
# exactly one shard of .github/workflows/e2e.yml, and the workflow must not reference a flow
# that does not exist. Subflows (tests/e2e/subflows/) are not top-level and are ignored.
set -euo pipefail

cd "$(dirname "$0")/.."

workflow=.github/workflows/e2e.yml
status=0

mapfile -t referenced < <(grep -oE 'tests/e2e/[A-Za-z0-9_-]+\.yaml' "$workflow" | sort)
mapfile -t existing < <(ls tests/e2e/*.yaml 2>/dev/null | sort)

for flow in "${existing[@]}"; do
  count=0
  for ref in "${referenced[@]}"; do
    [ "$ref" = "$flow" ] && count=$((count + 1))
  done
  if [ "$count" -eq 0 ]; then
    echo "ERROR: $flow is in no shard of $workflow" >&2
    status=1
  elif [ "$count" -gt 1 ]; then
    echo "ERROR: $flow is listed $count times in $workflow (must be exactly once)" >&2
    status=1
  fi
done

for ref in $(printf '%s\n' "${referenced[@]}" | sort -u); do
  [ -f "$ref" ] || { echo "ERROR: $workflow references $ref, which does not exist" >&2; status=1; }
done

[ "$status" -eq 0 ] && echo "e2e shards OK: ${#existing[@]} flows, each in exactly one shard"
exit "$status"
