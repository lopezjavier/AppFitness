#!/usr/bin/env bash
# Runs Maestro flows on the running emulator/device (T-032). Same script locally and in CI.
# Usage: bash scripts/e2e-run-shard.sh <apk> <flow.yaml>...
# Runs every flow even after a failure (no retries); exits 1 if any flow failed.
set -uo pipefail

cd "$(dirname "$0")/.."

if [ "$#" -lt 2 ]; then
  echo "Usage: $0 <apk> <flow.yaml>..." >&2
  exit 2
fi

apk=$1
shift

adb install -r "$apk" || { echo "ERROR: adb install failed" >&2; exit 1; }

# Flows require animations off (also done by emulator-runner in CI; this keeps local runs self-sufficient).
for scale in animator_duration_scale window_animation_scale transition_animation_scale; do
  adb shell settings put global "$scale" 0
done

# Wait until the package manager answers (the emulator can report boot_completed slightly early).
for _ in $(seq 1 30); do
  adb shell cmd package list packages >/dev/null 2>&1 && break
  sleep 2
done

rows=()
failed=0
for flow in "$@"; do
  start=$(date +%s)
  if maestro test --test-output-dir tests/e2e/screenshots "$flow"; then
    result=PASS
  else
    result=FAIL
    failed=1
  fi
  rows+=("$flow|$result|$(($(date +%s) - start))")
done

# CI-only diagnostics: when something failed, keep the device logs next to Maestro's output.
if [ "$failed" -eq 1 ]; then
  diag=tests/e2e/screenshots/diagnostics
  mkdir -p "$diag"
  adb logcat -d -b all >"$diag/logcat-all.txt" 2>&1
  adb shell dumpsys meminfo >"$diag/meminfo.txt" 2>&1
  adb shell df >"$diag/df.txt" 2>&1
  adb shell getprop >"$diag/getprop.txt" 2>&1
fi

echo
echo "| flow | result | seconds |"
echo "|---|---|---|"
for row in "${rows[@]}"; do
  IFS='|' read -r f r s <<<"$row"
  echo "| $f | $r | $s |"
done

if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
  {
    echo "| flow | result | seconds |"
    echo "|---|---|---|"
    for row in "${rows[@]}"; do
      IFS='|' read -r f r s <<<"$row"
      echo "| $f | $r | $s |"
    done
  } >>"$GITHUB_STEP_SUMMARY"
fi

exit "$failed"
