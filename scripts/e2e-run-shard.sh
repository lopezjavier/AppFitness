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

# CI only. Android 16+ system images (API 36 and 37, any GPU mode) abort surfaceflinger in
# mapper.ranchu.so ("Assertion failed: !rcEnc->featureInfo()->hasReadColorBufferDma") on the
# RegionSamplingThread, which the gesture navigation bar triggers. Every abort restarts
# system_server mid-run ("Can't find service: package", run 38410778915). The 3-button navigation
# bar does not register that listener, so switch to it. Local AVDs are left untouched.
if [ -n "${CI:-}" ]; then
  for _ in $(seq 1 30); do
    adb shell cmd overlay enable com.android.internal.systemui.navbar.threebutton >/dev/null 2>&1 &&
      adb shell cmd overlay disable com.android.internal.systemui.navbar.gestural >/dev/null 2>&1 && break
    sleep 2
  done
  sleep 10 # let a framework restart that was already in flight settle
fi

adb install -r "$apk" || { echo "ERROR: adb install failed" >&2; exit 1; }

# Flows require animations off (also done by emulator-runner in CI; this keeps local runs self-sufficient).
for scale in animator_duration_scale window_animation_scale transition_animation_scale; do
  adb shell settings put global "$scale" 0
done

wait_for_system() {
  # Waits until boot completed and the package manager answers (up to ~2 min).
  for _ in $(seq 1 60); do
    [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] &&
      adb shell cmd package list packages >/dev/null 2>&1 && return 0
    sleep 2
  done
  return 1
}
wait_for_system

rows=()
failed=0
for flow in "$@"; do
  start=$(date +%s)
  pid_before=$(adb shell pidof system_server 2>/dev/null | tr -d '\r')
  if maestro test --test-output-dir tests/e2e/screenshots "$flow"; then
    result=PASS
  else
    result=FAIL
    # Emulator infrastructure bug (run 38411875620): on the Android 17 image system_server aborts
    # in TaskSnapshotPersister ("Assertion failed: !rcEnc->featureInfo()->hasReadColorBufferDma")
    # when the app is torn down between flows, which kills the flow under test. Re-run the flow
    # once, and only when system_server restarted, so real flow failures are never retried.
    pid_after=$(adb shell pidof system_server 2>/dev/null | tr -d '\r')
    if [ "$pid_before" != "$pid_after" ]; then
      echo "system_server restarted during $flow (pid $pid_before -> $pid_after): re-running it once"
      wait_for_system
      if maestro test --test-output-dir tests/e2e/screenshots "$flow"; then
        result="PASS (re-run after system_server restart)"
      fi
    fi
    [ "$result" = FAIL ] && failed=1
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
