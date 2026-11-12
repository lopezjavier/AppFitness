#!/usr/bin/env bash
# Developer's definition of done: run what CI would run on the checks that most often fail, once, locally.
# Usage: bash scripts/verify-local.sh [--full]
# Default mode (stops at the first failing step and says which one):
#   1. ktlintFormat (all modules), then ktlintCheck detekt
#   2. check-design-tokens.sh, check-unused-resources.py, python script tests, tests/test-check-ux-conventions.sh, check-ux-conventions.sh (as android-ci.yml)
#   3. cross-cutting test classes: *AppModuleTest, *ShellChromeTest, *StringsTest
#   4. test classes of every test file changed vs main (committed + working tree) and the test class
#      named after each changed source file when it exists (FooViewModel.kt -> FooViewModelTest)
# --full adds the whole suite as CI runs it (testLocalDebugUnitTest, :androidApp:testProdDebugUnitTest).
# Everything goes through scripts/gradle-quiet.sh (full log in .workflow/last-gradle.log). The last line
# is read from the JUnit XML written by THIS run (tests are forced with --rerun), never from the exit code:
#   VERIFY-LOCAL OK|FAIL: <steps> steps, <N> tests run, <F> failed, <S> skipped
# FAIL also when zero tests ran in steps 3-4. Exit code 0 only on OK.
set -uo pipefail
cd "$(dirname "$0")/.."

FULL=0
[ "${1:-}" = "--full" ] && FULL=1

mkdir -p .workflow
stamp=.workflow/verify-local.stamp
: >"$stamp"
steps=0
failed_step=""

summary() {
  local status=$1 tests=0 fails=0 skips=0 t f s
  while IFS= read -r x; do
    t=$(sed -n 's/.*<testsuite [^>]*tests="\([0-9]*\)".*/\1/p' "$x" | head -1)
    f=$(sed -n 's/.*<testsuite [^>]*failures="\([0-9]*\)".*/\1/p' "$x" | head -1)
    e=$(sed -n 's/.*<testsuite [^>]*errors="\([0-9]*\)".*/\1/p' "$x" | head -1)
    s=$(sed -n 's/.*<testsuite [^>]*skipped="\([0-9]*\)".*/\1/p' "$x" | head -1)
    tests=$((tests + ${t:-0})); fails=$((fails + ${f:-0} + ${e:-0})); skips=$((skips + ${s:-0}))
  done < <(find . -path '*/build/test-results/*' -name 'TEST-*.xml' -newer "$stamp" 2>/dev/null)
  if [ "$status" = OK ] && { [ "$tests" -eq 0 ] || [ "$fails" -gt 0 ]; }; then status=FAIL; fi
  [ -n "$failed_step" ] && echo "failed step: $failed_step"
  echo "VERIFY-LOCAL $status: $steps steps, $tests tests run, $fails failed, $skips skipped"
  [ "$status" = OK ]
}

fail() { failed_step="$1"; summary FAIL; exit 1; }

run() { # run <label> <command...>
  local label=$1; shift
  steps=$((steps + 1))
  echo "== step $steps: $label"
  "$@" || fail "$label"
}

gq() { bash scripts/gradle-quiet.sh "$@"; }

# Gradle test task of the module that owns a test file.
task_for() {
  case "$1" in
    androidApp/src/testLocal/*) echo ":androidApp:testLocalDebugUnitTest" ;;
    androidApp/src/testProd/*) echo ":androidApp:testProdDebugUnitTest" ;;
    composeApp/*) echo ":composeApp:testAndroidHostTest" ;;
    shared/*) echo ":shared:testAndroidHostTest" ;;
    *) echo "" ;;
  esac
}

# run_tests <label> <test file paths...>: groups classes by module task and runs each group once.
run_tests() {
  local label=$1; shift
  local -A groups=()
  local f t c
  for f in "$@"; do
    t=$(task_for "$f"); [ -z "$t" ] && continue
    c=$(basename "$f" .kt)
    case " ${groups[$t]:-} " in *" $c "*) ;; *) groups[$t]="${groups[$t]:-} $c" ;; esac
  done
  [ "${#groups[@]}" -eq 0 ] && { echo "== $label: no test classes"; return 0; }
  local task args cls
  for task in "${!groups[@]}"; do
    args=()
    for cls in ${groups[$task]}; do args+=(--tests "*$cls"); done
    run "$label ($task)" gq "$task" --rerun "${args[@]}"
  done
}

all_tests=$(git ls-files '*.kt' | grep -E '/src/[A-Za-z]*[Tt]est[A-Za-z]*/')

run "ktlintFormat" gq ktlintFormat
run "ktlintCheck detekt" gq ktlintCheck detekt
run "check-design-tokens" bash scripts/check-design-tokens.sh
run "test-check-ux-conventions" bash scripts/tests/test-check-ux-conventions.sh
run "check-ux-conventions" bash scripts/check-ux-conventions.sh
run "check-unused-resources" python -I scripts/check-unused-resources.py
run "python script tests" bash -c 'for t in scripts/test_deliver_apk.py scripts/test_repdb_tools.py scripts/tests/test_generate_band_long.py scripts/tests/test_check_unused_resources.py scripts/tests/test_gallery_index.py; do python -I "$t" || exit 1; done'

# (c) cross-cutting classes
mapfile -t cross < <(echo "$all_tests" | grep -E '(AppModuleTest|ShellChromeTest|StringsTest)\.kt$')
run_tests "cross-cutting tests" "${cross[@]}"

# (d) classes of changed files
changed=$( { git diff --name-only main...HEAD; git diff --name-only HEAD; git ls-files --others --exclude-standard; } 2>/dev/null | sort -u | grep '\.kt$' || true)
sel=()
while IFS= read -r f; do
  [ -z "$f" ] && continue
  if echo "$f" | grep -qE '/src/[A-Za-z]*[Tt]est[A-Za-z]*/'; then
    [ -f "$f" ] && sel+=("$f")
  else
    name=$(basename "$f" .kt)
    while IFS= read -r m; do [ -n "$m" ] && sel+=("$m"); done < <(echo "$all_tests" | grep -E "/${name}Test\.kt$")
  fi
done <<<"$changed"
run_tests "changed-file tests" "${sel[@]}"

if [ "$FULL" -eq 1 ]; then
  run "full testLocalDebugUnitTest" gq testLocalDebugUnitTest
  run "full :androidApp:testProdDebugUnitTest" gq :androidApp:testProdDebugUnitTest
fi

summary OK
