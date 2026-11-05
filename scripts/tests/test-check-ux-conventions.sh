#!/usr/bin/env bash
# Fixture tests for scripts/check-ux-conventions.sh (T-118). For each guard G1..G11 (G16: own cases below; G13/G14: own cases below) a small tree under
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

run() { # tree warnings-file [allowlist-file] -> sets rc and out (the allow-list defaults to an empty one)
  rc=0
  out=$(bash "$guard" "$1" "$2" "${3:-$work/empty.txt}" 2>&1) || rc=$?
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

# G14 (allow-list) and G13 (tab-root bell) have their own fixtures: an emptied G13 root would fail by design.
run "$here/ux-conventions/g14" "$empty"
expect "G14 AppEditSheet in an unlisted feature file fails" 1
run "$here/ux-conventions/g14-allowed" "$empty"
expect "G14 AppEditSheet in an allow-listed file passes" 0 NOWARN
printf 'G14 .*\n' > "$work/listed-g14.txt"
run "$here/ux-conventions/g14" "$work/listed-g14.txt"
expect "G14 violation in a listed path warns and passes (parser accepts G14)" 0 WARN
run "$here/ux-conventions/g13" "$empty"
expect "G13 tab root without the bell fails" 1
run "$here/ux-conventions/g13-ok" "$empty"
expect "G13 tab root with the bell passes" 0 NOWARN
printf 'G13 .*\n' > "$work/listed-g13.txt"
run "$here/ux-conventions/g13" "$work/listed-g13.txt"
expect "G13 violation in a listed path warns and passes (parser accepts G13)" 0 WARN
printf 'G12 .*\n' > "$work/listed-g12.txt"
run "$here/ux-conventions/g13-ok" "$work/listed-g12.txt"
expect "the warnings parser accepts G12" 0 NOWARN

# G15 (R-51): no help buttons inside LiveExerciseCard.kt.
run "$here/ux-conventions/g15" "$empty"
expect "G15 help buttons in LiveExerciseCard fail" 1
run "$here/ux-conventions/g15-ok" "$empty"
expect "G15 LiveExerciseCard without help buttons passes" 0 NOWARN
printf 'G15 .*\n' > "$work/listed-g15.txt"
run "$here/ux-conventions/g15" "$work/listed-g15.txt"
expect "G15 violation in a listed path warns and passes (parser accepts G15)" 0 WARN

# G16 (R-54): no generic exercise labels or name fallbacks.
mk16() { # name file-path content
  local d="$work/g16-$1"
  mkdir -p "$d/$(dirname "$2")"
  printf '%s\n' "$3" > "$d/$2"
  echo "$d"
}
kt16="composeApp/src/commonMain/kotlin/demo/feature/workout/Card.kt"
xml16="composeApp/src/commonMain/composeResources/values/strings.xml"
run "$(mk16 a "$xml16" '<string name="x">Ejercicio sin nombre</string>')" "$empty"
expect "G16 generic exercise label in strings.xml fails" 1
run "$(mk16 b "$xml16" '<string name="exercise_detail_title">Ejercicio</string>')" "$empty"
expect "G16 exercise_detail_title is allow-listed" 0 NOWARN
run "$(mk16 c "$kt16" 'val n = exercise.name ?: stringResource(Res.string.x)')" "$empty"
expect "G16 name falling back to a string fails" 1
run "$(mk16 d "$kt16" 'val n = exercise.name.ifBlank { "x" }')" "$empty"
expect "G16 ifBlank on an exercise name fails" 1
run "$(mk16 e "$kt16" 'val n = names[id] ?: exerciseId')" "$empty"
expect "G16 raw exercise id as a label fails" 1
run "$(mk16 f "$kt16" 'val n = folder.name.orEmpty()')" "$empty"
expect "G16 orEmpty on a non-exercise name passes" 0 NOWARN
run "$(mk16 h "$kt16" 'val n = names[id].orEmpty()')" "$empty"
expect "G16 orEmpty on names[id] fails" 1
run "$(mk16 i "$kt16" 'val n = names[id]?.ifBlank { "x" }')" "$empty"
expect "G16 ifBlank on names[id] fails" 1
run "$(mk16 j "$kt16" 'val n = lookup.names[id] ?: ""')" "$empty"
expect "G16 empty-string elvis on lookup.names[id] fails" 1
run "$(mk16 k "$kt16" 'val n = items.map { it.name ?: stringResource(Res.string.x) }')" "$empty"
expect "G16 it.name falling back to a string fails" 1
run "$(mk16 l "$kt16" 'val n = ex?.name ?: stringResource(Res.string.x)')" "$empty"
expect "G16 ex?.name falling back to a string fails" 1
run "$(mk16 m "$kt16" 'val n = row.rpe ?: stringResource(Res.string.x)')" "$empty"
expect "G16 a non-name value with a string fallback passes" 0 NOWARN
printf 'G16 .*\n' > "$work/listed-g16.txt"
run "$(mk16 g "$kt16" 'val n = exercise.name ?: stringResource(Res.string.x)')" "$work/listed-g16.txt"
expect "G16 violation in a listed path warns and passes (parser accepts G16)" 0 WARN

# Profile dialogs are on the R-08 allow-list (G5).
run "$here/ux-conventions/g5-allowed" "$empty"
expect "G5 profile dialog is allowed" 0 NOWARN

# ADR-95 removed the routines folder-delete dialog: RoutinesSheets.kt is no longer allowed.
run "$here/ux-conventions/g5-routines" "$empty"
expect "G5 routines dialog is no longer allowed" 1 G5

# ADR-80: dismissOnClickOutside = true is allowed in the designsystem archetypes, never in feature code.
run "$here/ux-conventions/g11-allowed" "$empty"
expect "G11 dismissOnClickOutside = true in designsystem/AppSheets.kt is allowed" 0 NOWARN
run "$here/ux-conventions/g11" "$empty"
expect "G11 feature code with dismissOnClickOutside = true fails" 1
printf '%s' "$out" | grep -q 'BadOutsideTap.kt' && printf '%s' "$out" | grep -q 'BadPlatformOutsideTap.kt' \
  && echo "ok   G11 reports both outside-tap fixtures" || { echo "FAIL G11 outside-tap fixtures not both reported"; failures=$((failures + 1)); }

# T-124b: the exercise screens are migrated, so the real warning list no longer excuses G2/G3/G5 there.
real_list="$here/../ux-conventions-warnings.txt"
real_allow="$here/../ux-inline-text-allowlist.txt"
repo_root="$here/../.."
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

# G18 (R-57): inline helper/hint text must be allow-listed; the allow-list cannot rot.
xml18="composeApp/src/commonMain/composeResources/values/strings.xml"
kt18="composeApp/src/commonMain/kotlin/demo/feature/profile/Row.kt"
mk18() { # name file-path content
  local d="$work/g18-$1"
  mkdir -p "$d/$(dirname "$2")"
  printf '%s
' "$3" > "$d/$2"
  echo "$d"
}
bad18='<string name="x_helper">Añade 3 series para progresar</string>'
run "$(mk18 a "$xml18" "$bad18")" "$empty"
expect "G18 unlisted _helper key fails" 1
printf '%s' "$out" | grep -q 'G18' && echo "ok   G18 is named in the output"   || { echo "FAIL G18 not named in the output"; failures=$((failures + 1)); }
printf 'x_helper  # one short fact
' > "$work/allow-x.txt"
run "$(mk18 b "$xml18" "$bad18")" "$empty" "$work/allow-x.txt"
expect "G18 allow-listed _helper key passes" 0 NOWARN
printf 'x_*  # wildcard
' > "$work/allow-wild.txt"
run "$(mk18 c "$xml18" "$bad18")" "$empty" "$work/allow-wild.txt"
expect "G18 wildcard entry allow-lists the key" 0 NOWARN
run "$(mk18 d "$xml18" '<string name="x_title">Titulo</string>')" "$empty"
expect "G18 a key without a helper/hint suffix passes" 0 NOWARN
run "$(mk18 e "$xml18" '<string name="x_title">Titulo</string>')" "$empty" "$work/allow-x.txt"
expect "G18 stale allow-list entry fails" 1
run "$(mk18 f "$kt18" 'AppSwitchRow(helper = stringResource(Res.string.y_note))')" "$empty"
expect "G18 helper = stringResource in feature code fails" 1
run "$(mk18 g "$kt18" 'Helper(stringResource(Res.string.y_note))')" "$empty"
expect "G18 Helper(stringResource) in feature code fails" 1
printf 'y_note
' > "$work/allow-y.txt"
run "$(mk18 h "$kt18" 'AppSwitchRow(helper = stringResource(Res.string.y_note))')" "$empty" "$work/allow-y.txt"
expect "G18 allow-listed key in a helper = argument passes" 0 NOWARN
printf 'Not A Key
' > "$work/allow-bad.txt"
run "$(mk18 i "$xml18" "$bad18")" "$empty" "$work/allow-bad.txt"
expect "G18 malformed allow-list line is an error" 2
printf 'G18 .*
' > "$work/listed-g18.txt"
run "$(mk18 j "$xml18" "$bad18")" "$work/listed-g18.txt"
expect "G18 violation in a listed path warns and passes (parser accepts G18)" 0 WARN
run "$repo_root" "$real_list" "$real_allow"
expect "the real tree passes with the real allow-list" 0

echo "$cases cases, $failures failure(s)"
[ "$failures" -eq 0 ]
