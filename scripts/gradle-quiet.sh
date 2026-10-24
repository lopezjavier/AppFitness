#!/usr/bin/env bash
# Run a Gradle command ONCE and keep every failure reason, so nobody re-runs it to find out why it failed.
# Usage: bash scripts/gradle-quiet.sh <gradle args...>
#   e.g. bash scripts/gradle-quiet.sh :shared:testAndroidHostTest --tests '*RestTimerTest'
# Full output goes to .workflow/last-gradle.log (local, unversioned). Stdout gets: exit code, a short
# list of failed tests (class, name, message, first stack lines) read from the JUnit XML, and compiler/
# lint errors (lines starting with "e: " or containing "error:"/"FAILED"/ktlint/detekt findings).
# Exit code is Gradle's.
set -uo pipefail

cd "$(dirname "$0")/.."
mkdir -p .workflow
log=.workflow/last-gradle.log

./gradlew --console=plain --continue "$@" >"$log" 2>&1
code=$?

echo "gradle exit code: $code (full log: $log, $(wc -l <"$log") lines)"
[ "$code" -eq 0 ] && { echo "OK"; exit 0; }

echo "--- compile / lint / task errors ---"
grep -nE '^e: |^w: .*\.kt|error:|FAILED|\.kt:[0-9]+:[0-9]+ |Execution failed|What went wrong' "$log" | head -40

echo "--- failed tests (from JUnit XML) ---"
while IFS= read -r f; do
  awk '
    /<testcase / { name=$0; sub(/.*classname="/,"",name); cls=name; sub(/".*/,"",cls)
                   sub(/.*name="/,"",name); sub(/".*/,"",name) }
    /<failure |<error / { msg=$0; sub(/.*message="/,"",msg); sub(/" type=.*/,"",msg)
                          print "FAIL " cls "." name ": " substr(msg,1,300); n=6; next }
    n>0 { print "    " substr($0,1,200); n-- }
  ' "$f"
done < <(find . -path '*/build/test-results/*' -name 'TEST-*.xml' -newer "$log" -o -path '*/build/test-results/*' -name 'TEST-*.xml' -mmin -30 2>/dev/null | sort -u | xargs -r grep -lE '<failure |<error ' 2>/dev/null) | head -80

exit "$code"
