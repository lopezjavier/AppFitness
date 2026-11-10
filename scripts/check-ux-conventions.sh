#!/usr/bin/env bash
# UX conventions guard (T-118, planning/phase1/ux-audit-2026-10.md section 10, planning/05-design-system.md).
# Features must reuse the design-system templates instead of reinventing them (guards G1..G19).
#
# Usage: check-ux-conventions.sh [root-dir] [warnings-file] [allowlist-file]
#   root-dir        tree to scan (default: repository root). Tests pass a fixture tree.
#   warnings-file   per-guard, per-path warning list (default: scripts/ux-conventions-warnings.txt).
#   allowlist-file  G18 inline-text allow-list (default: scripts/ux-inline-text-allowlist.txt): one string
#                   key per line (`key  # reason`, trailing `*` wildcard allowed). Not a warn-list: a
#                   matching key not listed fails, and a listed entry matching no key fails (no rot).
#
# A hit in a path listed for that guard in the warnings file prints WARN and does not fail; any other
# hit prints and exits 1. Each listed path is migration debt with an owning task; removing a line
# turns that path into a failure. Lines are `G<n> <path-regex>`; `#` starts a comment. Exit 2 or
# more is a script/grep error and is never a pass.
set -euo pipefail

script_dir="$(cd "$(dirname "$0")" && pwd)"
root="${1:-$script_dir/..}"
warnings_file="${2:-$script_dir/ux-conventions-warnings.txt}"
allowlist_file="${3:-$script_dir/ux-inline-text-allowlist.txt}"
cd "$root"

if [ ! -f "$warnings_file" ]; then
  echo "UX conventions guard ERROR: warnings file not found: $warnings_file" >&2
  exit 2
fi
if [ ! -f "$allowlist_file" ]; then
  echo "UX conventions guard ERROR: allow-list file not found: $allowlist_file" >&2
  exit 2
fi

# --- warning list: parallel arrays of guard ids and path regexes -------------------------------
warn_guards=()
warn_paths=()
while IFS= read -r raw || [ -n "$raw" ]; do
  line="${raw%%#*}"
  line="$(printf '%s' "$line" | sed -E 's/^[[:space:]]+//; s/[[:space:]]+$//')"
  [ -z "$line" ] && continue
  if ! printf '%s' "$line" | grep -qE '^G([1-9]|1[0-8]) [^ ]+$'; then
    echo "UX conventions guard ERROR: malformed warnings line: $raw" >&2
    exit 2
  fi
  warn_guards+=("${line%% *}")
  warn_paths+=("${line#* }")
done < "$warnings_file"

is_warned() { # guard path
  local i rc
  for i in "${!warn_guards[@]}"; do
    [ "${warn_guards[$i]}" = "$1" ] || continue
    rc=0
    printf '%s\n' "$2" | grep -qE "${warn_paths[$i]}" || rc=$?
    if [ "$rc" -eq 0 ]; then return 0; fi
    if [ "$rc" -ge 2 ]; then
      echo "UX conventions guard ERROR: invalid regex in warnings file: ${warn_paths[$i]}" >&2
      exit 2
    fi
  done
  return 1
}

# --- G18 allow-list: string-key globs (trailing * wildcard) -----------------------------------
allow_keys=()
while IFS= read -r raw || [ -n "$raw" ]; do
  line="${raw%%#*}"
  line="$(printf '%s' "$line" | sed -E 's/^[[:space:]]+//; s/[[:space:]]+$//')"
  [ -z "$line" ] && continue
  if ! printf '%s' "$line" | grep -qE '^[a-z0-9_]+\*?[a-z0-9_]*$'; then
    echo "UX conventions guard ERROR: malformed allow-list line: $raw" >&2
    exit 2
  fi
  allow_keys+=("$line")
done < "$allowlist_file"

is_allowed_key() { # key
  local entry
  for entry in "${allow_keys[@]+"${allow_keys[@]}"}"; do
    # shellcheck disable=SC2254
    case "$1" in $entry) return 0 ;; esac
  done
  return 1
}

# --- file sets ----------------------------------------------------------------------------------
kotlin_all=()
kotlin_feature=()
while IFS= read -r file; do
  kotlin_all+=("$file")
  case "$file" in
    */designsystem/*) ;;
    *) kotlin_feature+=("$file") ;;
  esac
done < <(
  find composeApp/src -type f -name '*.kt' 2>/dev/null \
    | grep -E '^composeApp/src/[^/]+Main/' | sort || true
)
strings_file="composeApp/src/commonMain/composeResources/values/strings.xml"

violations=0
warnings=0

# report <guard> <description> <grep-output-lines>: classify each hit by its file path.
report() {
  local guard="$1" description="$2" hits="$3" line path
  while IFS= read -r line; do
    [ -z "$line" ] && continue
    path="${line%%:*}"
    if is_warned "$guard" "$path"; then
      echo "WARN $guard $description: $line"
      warnings=$((warnings + 1))
    else
      echo "FAIL $guard $description: $line" >&2
      violations=$((violations + 1))
    fi
  done <<< "$hits"
}

# scan <guard> <description> <pattern> <allow-regex or ""> <files...>
scan() {
  local guard="$1" description="$2" pattern="$3" allow="$4" hits rc=0
  shift 4
  [ "$#" -eq 0 ] && return 0
  hits=$(grep -HnE "$pattern" "$@") || rc=$?
  if [ "$rc" -ge 2 ]; then
    echo "UX conventions guard ERROR: grep failed with exit code $rc ($guard)." >&2
    exit "$rc"
  fi
  [ "$rc" -eq 1 ] && return 0
  if [ -n "$allow" ]; then
    rc=0
    hits=$(printf '%s\n' "$hits" | grep -Ev "^($allow):") || rc=$?
    if [ "$rc" -ge 2 ]; then
      echo "UX conventions guard ERROR: grep failed with exit code $rc ($guard allow-list)." >&2
      exit "$rc"
    fi
    [ "$rc" -eq 1 ] && return 0
  fi
  report "$guard" "$description" "$hits"
}

# G1: ModalBottomSheet only inside designsystem/AppBottomSheet.kt (the only file allowed).
scan G1 "ModalBottomSheet outside AppBottomSheet.kt" '\bModalBottomSheet\(' \
  '.*/designsystem/AppBottomSheet\.kt' "${kotlin_all[@]}"
# G2: features use a sheet archetype, not AppBottomSheet directly.
scan G2 "AppBottomSheet called from feature code (use an archetype)" '\bAppBottomSheet\(' '' "${kotlin_feature[@]}"
# G3: messages go through AppMessages; SnackbarHost only in the shell.
scan G3 "SnackbarHost outside app/ (use AppMessages)" '\bSnackbarHost\(' '.*/app/[^:]*' "${kotlin_feature[@]}"
# G4: the offline banner is drawn by the shell only (the catalog may demo it).
scan G4 "OfflineBanner outside the shell (use OfflineState or OfflineHint)" '\bOfflineBanner\(' \
  '.*/app/(AppScaffold|App)\.kt|.*/feature/catalog/[^:]*' "${kotlin_feature[@]}"
# G5: confirmation dialogs only for the allow-listed destructive cases (R-08).
scan G5 "AppConfirmDialog outside the R-08 allow-list" '\bAppConfirmDialog\(' \
  '.*/feature/profile/ProfileDialogs\.kt|.*/feature/workout/LiveSessionDialogs\.kt|.*/feature/workout/WorkoutEntry\.kt|.*/feature/trainer/TrainerPlanSection\.kt|.*/feature/trainer/TrainerBlockSection\.kt|.*/designsystem/DiscardChangesDialog\.kt|.*/feature/catalog/[^:]*' \
  "${kotlin_feature[@]}"
# G6..G8: single owners of help icon, swipe-to-delete and drag reordering.
scan G6 "ic_help outside HelpButton" '\bic_help\b' '' "${kotlin_feature[@]}"
scan G7 "SwipeToDismissBox outside SwipeToDeleteRow" '\bSwipeToDismissBox\b' '' "${kotlin_feature[@]}"
scan G8 "detectDragGestures outside ReorderableColumn" '\bdetectDragGestures\b' '' "${kotlin_feature[@]}"

# G11: R-38 / ADR-80 sheets have no drag handle, outside-tap dismissal only through the picker, info and action
# archetypes (and the calendar dialog), and one state factory.
scan G11 "sheet drag handle (R-38)" 'dragHandle = \{|BottomSheetDefaults\.DragHandle|SheetHandle' '' "${kotlin_all[@]}"
scan G11 "shouldDismissOnClickOutside = true (R-38, ADR-80: use AppBottomSheet(dismissOnClickOutside))" \
  'shouldDismissOnClickOutside = true' '' "${kotlin_all[@]}"
scan G11 "dismissOnClickOutside = true outside the picker/info/action archetypes and the calendar (R-38, ADR-80)" \
  '\bdismissOnClickOutside = true' \
  '.*/designsystem/(AppSheets|AppActionSheet|AppDatePickerDialog)\.kt' "${kotlin_all[@]}"
scan G11 "rememberModalBottomSheetState outside AppBottomSheet.kt (use rememberAppSheetState)" 'brememberModalBottomSheetState\(' '.*/designsystem/AppBottomSheet\.kt' "${kotlin_all[@]}"

# G12: R-50 set tables size header columns from the shared column spec (SetRowDefaults), never a literal
# 48 dp spacer, and the live SetRow draws no per-set number.
set_table_files=()
for file in "${kotlin_all[@]}"; do
  case "$file" in
    */designsystem/SetRow.kt | */feature/workout/WorkoutFormSetRow.kt | */feature/routines/RoutineBuilderSets.kt)
      set_table_files+=("$file") ;;
  esac
done
scan G12 "literal minTouchTarget spacer in a set table header (R-50: use SetRowDefaults column widths)" \
  'Spacer\(Modifier\.width\((Spacing\.)?minTouchTarget\)\)' '' "${set_table_files[@]}"
scan G12 "SetOrdinal inside the live SetRow (R-50)" '\bSetOrdinal\(' \
  '.*/feature/[^:]*' "${set_table_files[@]}"

# G13: every tab root carries the notification bell in all its states (R-04). The four roots are listed here;
# a root file without NotificationsBell( or notificationsBarAction( fails.
for file in "${kotlin_feature[@]}"; do
  case "$file" in
    */feature/trainer/TrainerScreen.kt | */feature/diary/DiaryScreen.kt | */feature/workout/WorkoutEntry.kt \
      | */feature/profile/ProfileRootScreen.kt)
      rc=0
      grep -qE '\b(NotificationsBell|notificationsBarAction)\(' "$file" || rc=$?
      if [ "$rc" -ge 2 ]; then
        echo "UX conventions guard ERROR: grep failed with exit code $rc (G13)." >&2
        exit "$rc"
      fi
      [ "$rc" -eq 1 ] && report G13 "tab root without the notification bell (R-04)" "$file:1:missing NotificationsBell("
      ;;
  esac
done

# G14: AppEditSheet( (a Save/Apply footer) only in the files below, all multi-field or stepper/wheel editors
# (T-171 sweep). A single choice from a list applies on tap in AppPickerSheet (R-12). A new file using it fails:
# use the Picker archetype, or add the file here with a one-line reason (trainer/ReplacePlanSheet: confirm footer "Crear plan", retired by T-176; trainer/ActivityUi: cardio-minutes stepper with a Save footer, T-181; trainer/IntakeFields: years stepper and maintenance kcal field with a Guardar footer, T-198; trainer/BuilderCreateExerciseSheet: name, equipment, pattern and switch form with an Añadir footer, T-222).
scan G14 "AppEditSheet outside the multi-field allow-list (R-12: a single choice uses AppPickerSheet)" \
  '\bAppEditSheet\(' \
  '.*/feature/(catalog/OverlayCatalogSections|exercises/ExerciseFormSheet|meals/defaults/MealDefaultsSheets|profile/PersonalDataCard|trainer/(ReplacePlanSheet|ActivityUi|IntakeFields|BuilderCreateExerciseSheet)|weight/(CircumferenceSheet|GoalSheets|WeightDaySheet)|workout/(DiscomfortSheet|ExerciseMenu|FinishSessionSheet|UpdateRoutineSheet|VolumeOptionsSheets|WarmUpCalculatorSheet))\.kt' \
  "${kotlin_feature[@]}"

# G15: R-51 the live exercise card shows its per-exercise information as status rows; no "?" help buttons there
# (the suggestion row opens its reason sheet, the RPE target is plain text).
live_card_files=()
for file in "${kotlin_feature[@]}"; do
  case "$file" in
    */feature/workout/LiveExerciseCard.kt) live_card_files+=("$file") ;;
  esac
done
scan G15 "HelpTriggerButton/RpeHelpButton in LiveExerciseCard (R-51: status rows, no help buttons)" \
  '\b(HelpTriggerButton|RpeHelpButton)\(' '' "${live_card_files[@]}"

# G16: R-54 an exercise is shown by its real name (ExerciseNameResolver), never by a generic label or a raw id.
# Fallbacks in feature code: a name that falls back to a string, ifBlank/orEmpty/elvis on an exercise name, an id as label.
scan G16 "exercise name falls back to a string (R-54: resolve the name through ExerciseNameResolver)" \
  '([eE]xercise|record|chip|row|names?)[A-Za-z]*(\.[A-Za-z]*[nN]ame\b|\[)[^?]*\?:[[:space:]]*(stringResource\(|unnamed\b|fallback\b)' '' \
  "${kotlin_feature[@]}"
# Allow-list: the exercise form and detail view model use the own exercise's name as the edit form's initial text,
# which is not a displayed fallback.
scan G16 "ifBlank/orEmpty/elvis on an exercise name (R-54)" \
  '\b[A-Za-z]*[eE]xercise[A-Za-z]*\??\.name\b[^/]*(\.ifBlank[[:space:]]*\{|\.orEmpty\(\)|\?:)' \
  '.*/feature/exercises/(ExerciseDetailViewModel|ExerciseFormSheet)\.kt' "${kotlin_feature[@]}"
scan G16 "empty/blank fallback on a looked-up exercise name (R-54)" \
  '[nN]ames?\[[^]]*\]\??(\.orEmpty\(\)|\.ifBlank|[[:space:]]*\?:[[:space:]]*"")' '' "${kotlin_feature[@]}"
# Allow-list: ExerciseDetailScreen titles itself with exercise_detail_title while no detail is loaded (R-01, not a name).
scan G16 "a name falls back to a string resource (R-54)" \
  '\.name\b[[:space:]]*\?:[[:space:]]*stringResource\(' '.*/feature/exercises/ExerciseDetailScreen\.kt' \
  "${kotlin_feature[@]}"
scan G16 "exercise id used as a label (R-54)" '\?:[[:space:]]*[A-Za-z.]*[eE]xerciseId\b' '' "${kotlin_feature[@]}"

# G9 / G10 / G16: strings.xml.
if [ -f "$strings_file" ]; then
  rc=0
  generic='>(Guardar|Cancelar|Cerrar|Eliminar|Listo|Descartar|Reintentar|Editar)</string>'
  hits=$(grep -HnE "$generic" "$strings_file") || rc=$?
  if [ "$rc" -ge 2 ]; then
    echo "UX conventions guard ERROR: grep failed with exit code $rc (G9)." >&2
    exit "$rc"
  fi
  if [ "$rc" -eq 0 ]; then
    rc=0
    hits=$(printf '%s\n' "$hits" | grep -v 'name="action_') || rc=$?
    if [ "$rc" -ge 2 ]; then
      echo "UX conventions guard ERROR: grep failed with exit code $rc (G9 filter)." >&2
      exit "$rc"
    fi
    [ "$rc" -eq 0 ] && report G9 "generic action text outside action_* keys (R-36)" "$hits"
  fi
  rc=0
  hits=$(grep -HnE '\.\.\.|Deshacer' "$strings_file") || rc=$?
  if [ "$rc" -ge 2 ]; then
    echo "UX conventions guard ERROR: grep failed with exit code $rc (G10)." >&2
    exit "$rc"
  fi
  if [ "$rc" -eq 0 ]; then
    # The only "Deshacer" is the undo of an applied coach load change (ADR-88, T-228).
    rc=0
    hits=$(printf '%s
' "$hits" | grep -vE 'name="coach_change_undo(_cd)?"') || rc=$?
    if [ "$rc" -ge 2 ]; then
      echo "UX conventions guard ERROR: grep failed with exit code $rc (G10 filter)." >&2
      exit "$rc"
    fi
  fi
  [ "$rc" -eq 0 ] && report G10 "three dots or 'Deshacer' in strings.xml (use the ellipsis character)" "$hits"
  # G17: Compose resources do not collapse %%, so a doubled sign prints twice (T-201). Use a single %.
  rc=0
  hits=$(grep -HnE '%%' "$strings_file") || rc=$?
  if [ "$rc" -ge 2 ]; then
    echo "UX conventions guard ERROR: grep failed with exit code $rc (G17)." >&2
    exit "$rc"
  fi
  [ "$rc" -eq 0 ] && report G17 "'%%' in strings.xml (Compose resources print both signs; use a single %)" "$hits"
  # G16: generic exercise labels. exercise_detail_title is the screen-kind title shown while the exercise is loading
  # (no object known yet, R-01), not an exercise name.
  rc=0
  hits=$(grep -HnE '>[[:space:]]*(Ejercicio( (sin nombre|sin título|desconocido|sin especificar))?|Sin nombre|Desconocido|Sin título)[[:space:]]*</string>' "$strings_file") || rc=$?
  if [ "$rc" -ge 2 ]; then
    echo "UX conventions guard ERROR: grep failed with exit code $rc (G16)." >&2
    exit "$rc"
  fi
  if [ "$rc" -eq 0 ]; then
    rc=0
    hits=$(printf '%s\n' "$hits" | grep -v 'name="exercise_detail_title"') || rc=$?
    if [ "$rc" -ge 2 ]; then
      echo "UX conventions guard ERROR: grep failed with exit code $rc (G16 filter)." >&2
      exit "$rc"
    fi
    [ "$rc" -eq 0 ] && report G16 "generic exercise label in strings.xml (R-54: resolve the name through ExerciseNameResolver)" "$hits"
  fi
fi

# G18: R-57 inline text under a row that explains or advises lives behind the row's "?" (HelpButton). A string
# key named *_helper/_hint/_example/_advice/_tip must be allow-listed (one short fact, disabled reason, empty
# state, option descriptor); so must the key behind a Helper(...) / helper = ... call in feature code.
# Limitation: advice under other key names (e.g. folder_review_result_*) is covered by review, not by this guard.
if [ -f "$strings_file" ]; then
  all_keys=$(grep -oE 'name="[^"]+"' "$strings_file" | sed -E 's/^name="//; s/"$//' || true)
  while IFS= read -r key; do
    [ -z "$key" ] && continue
    if printf '%s' "$key" | grep -qE '_(helper|hint|example|advice|tip)$' && ! is_allowed_key "$key"; then
      report G18 "inline helper/hint string not allow-listed (R-57: put it behind a HelpButton)" "$strings_file:1:$key"
    fi
  done <<< "$all_keys"
  for entry in "${allow_keys[@]+"${allow_keys[@]}"}"; do
    matched=0
    while IFS= read -r key; do
      [ -z "$key" ] && continue
      # shellcheck disable=SC2254
      case "$key" in $entry) matched=1; break ;; esac
    done <<< "$all_keys"
    if [ "$matched" -eq 0 ]; then
      report G18 "stale allow-list entry matches no string key" "$allowlist_file:1:$entry"
    fi
  done
fi
if [ "${#kotlin_feature[@]}" -gt 0 ]; then
  rc=0
  hits=$(grep -HnE '(\bHelper\(|\bhelper = )[^)]*Res\.string\.[a-z0-9_]+' "${kotlin_feature[@]}") || rc=$?
  if [ "$rc" -ge 2 ]; then
    echo "UX conventions guard ERROR: grep failed with exit code $rc (G18)." >&2
    exit "$rc"
  fi
  if [ "$rc" -eq 0 ]; then
    while IFS= read -r hit; do
      [ -z "$hit" ] && continue
      key=$(printf '%s' "$hit" | grep -oE 'Res\.string\.[a-z0-9_]+' | head -n 1 | sed 's/^Res\.string\.//')
      is_allowed_key "$key" || report G18 "Helper(...)/helper = with a non-allow-listed string (R-57: put it behind a HelpButton)" "$hit"
    done <<< "$hits"
  fi
fi

# G19 (ADR-104): the energy check is a hard block; "Guardar igualmente", "Añadir igualmente" and the food audit
# ("Revisar alimentos") are gone and never come back. Scans strings.xml and every Kotlin file under the app sources.
forbidden_files=("${kotlin_all[@]+"${kotlin_all[@]}"}")
[ -f "$strings_file" ] && forbidden_files+=("$strings_file")
if [ "${#forbidden_files[@]}" -gt 0 ]; then
  rc=0
  hits=$(grep -HnE 'Guardar igualmente|Añadir igualmente|Revisar alimentos' "${forbidden_files[@]}") || rc=$?
  if [ "$rc" -ge 2 ]; then
    echo "UX conventions guard ERROR: grep failed with exit code $rc (G19)." >&2
    exit "$rc"
  fi
  [ "$rc" -eq 0 ] && report G19 "removed energy-check override or food audit text (ADR-104: a failing food cannot be saved)" "$hits"
fi

if [ "$violations" -gt 0 ]; then
  echo "UX conventions guard FAILED: $violations violation(s), $warnings warning(s)." >&2
  echo "Use the design-system template (planning/05-design-system.md section 9); see planning/06-agent-workflow.md section 4." >&2
  exit 1
fi
echo "UX conventions guard passed (${#kotlin_all[@]} Kotlin files scanned, $warnings warning(s) on the migration list)."
