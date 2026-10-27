#!/usr/bin/env bash
# Fixture tests for scripts/check-ux-conventions.sh (T-118). For each guard G1..G11 a small tree under
# scripts/tests/ux-conventions/g<n>/ holds one violating file. Three cases per guard:
#   violation  : no warning list              -> exit 1
#   listed     : path in the warning list     -> exit 0 and a WARN line
#   clean      : violating file emptied       -> exit 0, no WARN
# T-128: the real warning list has no G9 entry, so a generic action text in strings.xml always fails.
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

for n in 1 2 3 4 5 6 7 8 9 10 11; do
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

# The folder-delete dialog of the routines list is on the R-08 allow-list (G5).
run "$here/ux-conventions/g5-allowed" "$empty"
expect "G5 routines folder-delete dialog is allowed" 0 NOWARN

# T-124b: the exercise screens are migrated, so the real warning list no longer excuses G2/G3/G5 there.
real_list="$here/../ux-conventions-warnings.txt"
for pair in "2:ExerciseFormSheet" "3:ExerciseDetailScreen" "3:ExercisePickerScreen" "5:ExerciseDetailScreen"; do
  n="${pair%%:*}"
  file="${pair##*:}"
  tree="$work/exercises-g$n-$file"
  mkdir -p "$tree/composeApp/src/commonMain/kotlin/demo/feature/exercises"
  cp "$here/ux-conventions/g$n/composeApp/src/commonMain/kotlin/demo/feature/Bad.kt" \
    "$tree/composeApp/src/commonMain/kotlin/demo/feature/exercises/$file.kt"
  run "$tree" "$real_list"
  expect "G$n in feature/exercises/$file.kt fails with the real warning list" 1
done

# T-125: the live session and its sheets are migrated, so the real warning list no longer excuses them.
for pair in "2:feature/workout/CalculatorSheetParts" "2:feature/workout/ExerciseMenu" \
  "2:feature/workout/FinishSessionSheet" "2:feature/workout/UpdateRoutineSheet" \
  "2:feature/workout/VolumeOptionsSheets" "2:feature/routines/RoutineSheetActions" \
  "3:feature/workout/LiveSessionScreen"; do
  n="${pair%%:*}"
  path="${pair##*:}"
  tree="$work/live-g$n-$(basename "$path")"
  mkdir -p "$tree/composeApp/src/commonMain/kotlin/demo/$(dirname "$path")"
  cp "$here/ux-conventions/g$n/composeApp/src/commonMain/kotlin/demo/feature/Bad.kt" \
    "$tree/composeApp/src/commonMain/kotlin/demo/$path.kt"
  run "$tree" "$real_list"
  expect "G$n in $path.kt fails with the real warning list" 1
done

# T-125c: the muscle detail sheet is migrated, so the real warning list no longer excuses it.
tree="$work/stats-g2-MuscleDetailSheet"
mkdir -p "$tree/composeApp/src/commonMain/kotlin/demo/feature/analytics/stats"
cp "$here/ux-conventions/g2/composeApp/src/commonMain/kotlin/demo/feature/Bad.kt" \
  "$tree/composeApp/src/commonMain/kotlin/demo/feature/analytics/stats/MuscleDetailSheet.kt"
run "$tree" "$real_list"
expect "G2 in feature/analytics/stats/MuscleDetailSheet.kt fails with the real warning list" 1

# T-125b: the history and workout detail screens are migrated, so the real warning list no longer excuses them.
for pair in "2:feature/workout/PastWorkoutChooserSheet" "2:feature/workout/SaveAsRoutineSheet" \
  "3:feature/workout/EditWorkoutScreen" "3:feature/workout/PastWorkoutScreen" \
  "3:feature/workout/SessionDetailScreen"; do
  n="${pair%%:*}"
  path="${pair##*:}"
  tree="$work/history-g$n-$(basename "$path")"
  mkdir -p "$tree/composeApp/src/commonMain/kotlin/demo/$(dirname "$path")"
  cp "$here/ux-conventions/g$n/composeApp/src/commonMain/kotlin/demo/feature/Bad.kt" \
    "$tree/composeApp/src/commonMain/kotlin/demo/$path.kt"
  run "$tree" "$real_list"
  expect "G$n in $path.kt fails with the real warning list" 1
done

# T-128: the real warning list no longer excuses G9 (generic action texts in strings.xml).
run "$here/ux-conventions/g9" "$real_list"
expect "G9 in strings.xml fails with the real warning list" 1

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
