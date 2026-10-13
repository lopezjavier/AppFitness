#!/usr/bin/env bash
# Per-module JUnit test counts (T-032). Reads **/build/test-results/**/TEST-*.xml, prints a
# Markdown table to stdout and $GITHUB_STEP_SUMMARY. Exits 1 if no results exist or zero tests
# ran, so a silently skipped test task is visible.
set -uo pipefail

cd "$(dirname "$0")/.."

attr() { # attr <name> <file>: value of the attribute on the <testsuite> element
  grep -m1 -oE "<testsuite [^>]*" "$2" | grep -oE " $1=\"[0-9]+\"" | head -1 | grep -oE '[0-9]+'
}

declare -A tests failures errors skipped
modules=()
total=0
files=0

while IFS= read -r f; do
  module=${f%%/build/test-results/*}
  files=$((files + 1))
  if [ -z "${tests[$module]+x}" ]; then
    modules+=("$module")
    tests[$module]=0 failures[$module]=0 errors[$module]=0 skipped[$module]=0
  fi
  tests[$module]=$((tests[$module] + $(attr tests "$f")))
  failures[$module]=$((failures[$module] + $(attr failures "$f")))
  errors[$module]=$((errors[$module] + $(attr errors "$f")))
  skipped[$module]=$((skipped[$module] + $(attr skipped "$f")))
done < <(find . -path '*/build/test-results/*' -name 'TEST-*.xml' -not -path './node_modules/*' | sed 's|^\./||' | sort)

out=$(mktemp)
{
  echo "### Unit test summary"
  echo
  echo "| module | tests | failures | errors | skipped |"
  echo "|---|---|---|---|---|"
  t=0 fl=0 er=0 sk=0
  for m in "${modules[@]}"; do
    echo "| $m | ${tests[$m]} | ${failures[$m]} | ${errors[$m]} | ${skipped[$m]} |"
    t=$((t + tests[$m])); fl=$((fl + failures[$m])); er=$((er + errors[$m])); sk=$((sk + skipped[$m]))
  done
  echo "| **total** | **$t** | **$fl** | **$er** | **$sk** |"
  total=$t
} >"$out"

cat "$out"
[ -n "${GITHUB_STEP_SUMMARY:-}" ] && cat "$out" >>"$GITHUB_STEP_SUMMARY"
rm -f "$out"

if [ "$files" -eq 0 ]; then
  echo "ERROR: no JUnit XML files found under */build/test-results/" >&2
  exit 1
fi
if [ "$total" -eq 0 ]; then
  echo "ERROR: JUnit XML files exist but no test ran" >&2
  exit 1
fi
