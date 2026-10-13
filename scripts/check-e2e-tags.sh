#!/usr/bin/env bash
# Fails when a tests/e2e/*.yaml flow (subflows excluded) has no non-empty `tags:` list in its
# header (the part before the first `---`). Usage: bash scripts/check-e2e-tags.sh
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
bad=0
for f in tests/e2e/*.yaml; do
  [ -e "$f" ] || continue
  ok=$(awk '
    /^---[ \t\r]*$/ { exit }
    /^tags:[ \t]*\[[ \t]*[^ \t\]]/ { found=1; next }
    /^tags:[ \t]*\r?$/ { intags=1; next }
    intags && /^[ \t]*-[ \t]*[^ \t\r]/ { found=1; next }
    intags && /^[^ \t#-]/ { intags=0 }
    END { print found ? "yes" : "no" }' "$f")
  if [ "$ok" != "yes" ]; then
    echo "$f: missing or empty 'tags:' list in the header" >&2
    bad=$((bad + 1))
  fi
done
[ "$bad" -eq 0 ] || { echo "check-e2e-tags: $bad flow(s) without tags" >&2; exit 1; }
echo "check-e2e-tags: OK"
