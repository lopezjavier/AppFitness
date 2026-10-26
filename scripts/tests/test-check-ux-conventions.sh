#!/usr/bin/env bash
# Fixture tests for scripts/check-ux-conventions.sh (T-118). For each guard G1..G10 a small tree under
# scripts/tests/ux-conventions/g<n>/ holds one violating file. Three cases per guard:
#   violation  : no warning list              -> exit 1
#   listed     : path in the warning list     -> exit 0 and a WARN line
#   clean      : violating file emptied       -> exit 0, no WARN
# Plus: a malformed warning line and an invalid regex in the list are errors (exit >= 2), and a
# warning for another guard does not excuse the violation.
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
guard="$here/../check-ux-conventions.sh"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
failures=0
cases=0

run() { # tree warnings-file -> sets rc and out
  rc=0
  out=$(bash "$guard" "$1" "$2" 2>&1) || rc=$?
}

expect() { # name expected-rc [WARN|NOWARN]
  cases=$((cases + 1))
  local ok=1
  [ "$rc" -eq "$2" ] || ok=0
  if [ "${3:-}" = "WARN" ]; then printf '%s' "$out" | grep -q '^WARN' || ok=0; fi
  if [ "${3:-}" = "NOWARN" ]; then ! printf '%s' "$out" | grep -q '^WARN' || ok=0; fi
  if [ "$ok" -eq 1 ]; then
    echo "ok   $1"
  else
    echo "FAIL $1 (rc=$rc, expected $2 ${3:-})"
    printf '%s\n' "$out" | sed 's/^/     /'
    failures=$((failures + 1))
  fi
}

empty="$work/empty.txt"
: > "$empty"

for n in 1 2 3 4 5 6 7 8 9 10; do
  tree="$here/ux-conventions/g$n"
  listed="$work/listed-g$n.txt"
  other="$work/other-g$n.txt"
  printf 'G%s .*\n' "$n" > "$listed"
  printf 'G%s .*\n' "$(( n % 10 + 1 ))" > "$other"

  run "$tree" "$empty"
  expect "G$n violation outside the list fails" 1
  run "$tree" "$other"
  expect "G$n warning for another guard does not excuse it" 1

  # The listed regex must match the real fixture path (G9/G10: strings.xml).
  run "$tree" "$listed"
  expect "G$n violation in a listed path warns and passes" 0 WARN

  clean="$work/clean-g$n"
  cp -r "$tree" "$clean"
  find "$clean" -type f -exec sh -c ': > "$1"' _ {} \;
  run "$clean" "$empty"
  expect "G$n clean tree passes" 0 NOWARN
done

printf 'not a valid line\n' > "$work/bad-line.txt"
run "$here/ux-conventions/g2" "$work/bad-line.txt"
expect "malformed warning line is an error" 2

printf 'G2 (unclosed\n' > "$work/bad-regex.txt"
run "$here/ux-conventions/g2" "$work/bad-regex.txt"
expect "invalid regex in the list is a grep error" 2

run "$here/ux-conventions/g2" "$work/missing.txt"
expect "missing warning list is an error" 2

echo "$cases cases, $failures failure(s)"
[ "$failures" -eq 0 ]
