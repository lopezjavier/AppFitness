#!/usr/bin/env bash
# UX conventions guard (T-118, planning/ux-audit-2026-10.md section 10, planning/05-design-system.md).
# Features must reuse the design-system templates instead of reinventing them (guards G1..G11).
#
# Usage: check-ux-conventions.sh [root-dir] [warnings-file]
#   root-dir       tree to scan (default: repository root). Tests pass a fixture tree.
#   warnings-file  per-guard, per-path warning list (default: scripts/ux-conventions-warnings.txt).
#
# A hit in a path listed for that guard in the warnings file prints WARN and does not fail; any other
# hit prints and exits 1. Each listed path is migration debt with an owning task; removing a line
# turns that path into a failure. Lines are `G<n> <path-regex>`; `#` starts a comment. Exit 2 or
# more is a script/grep error and is never a pass.
set -euo pipefail

script_dir="$(cd "$(dirname "$0")" && pwd)"
root="${1:-$script_dir/..}"
warnings_file="${2:-$script_dir/ux-conventions-warnings.txt}"
cd "$root"

if [ ! -f "$warnings_file" ]; then
  echo "UX conventions guard ERROR: warnings file not found: $warnings_file" >&2
  exit 2
fi

# --- warning list: parallel arrays of guard ids and path regexes -------------------------------
warn_guards=()
warn_paths=()
while IFS= read -r raw || [ -n "$raw" ]; do
  line="${raw%%#*}"
  line="$(printf '%s' "$line" | sed -E 's/^[[:space:]]+//; s/[[:space:]]+$//')"
  [ -z "$line" ] && continue
  if ! printf '%s' "$line" | grep -qE '^G([1-9]|1[01]) [^ ]+$'; then
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
  '.*/feature/profile/ProfileDialogs\.kt|.*/feature/workout/LiveSessionDialogs\.kt|.*/feature/workout/WorkoutEntry\.kt|.*/feature/routines/RoutinesSheets\.kt|.*/feature/catalog/[^:]*' \
  "${kotlin_feature[@]}"
# G6..G8: single owners of help icon, swipe-to-delete and drag reordering.
scan G6 "ic_help outside HelpButton" '\bic_help\b' '' "${kotlin_feature[@]}"
scan G7 "SwipeToDismissBox outside SwipeToDeleteRow" '\bSwipeToDismissBox\b' '' "${kotlin_feature[@]}"
scan G8 "detectDragGestures outside ReorderableColumn" '\bdetectDragGestures\b' '' "${kotlin_feature[@]}"

# G11: R-38 sheets have no drag handle, no outside/swipe dismissal and one state factory.
scan G11 "sheet drag handle or outside dismissal (R-38)" 'dragHandle = \{|BottomSheetDefaults\.DragHandle|SheetHandle|shouldDismissOnClickOutside = true' '' "${kotlin_all[@]}"
scan G11 "rememberModalBottomSheetState outside AppBottomSheet.kt (use rememberAppSheetState)" 'rememberModalBottomSheetState\(' '.*/designsystem/AppBottomSheet\.kt' "${kotlin_all[@]}"

# G9 / G10: strings.xml.
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
  [ "$rc" -eq 0 ] && report G10 "three dots or 'Deshacer' in strings.xml (use the ellipsis character)" "$hits"
fi

if [ "$violations" -gt 0 ]; then
  echo "UX conventions guard FAILED: $violations violation(s), $warnings warning(s)." >&2
  echo "Use the design-system template (planning/05-design-system.md section 9); see planning/06-agent-workflow.md section 4." >&2
  exit 1
fi
echo "UX conventions guard passed (${#kotlin_all[@]} Kotlin files scanned, $warnings warning(s) on the migration list)."
