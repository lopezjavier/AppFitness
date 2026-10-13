#!/usr/bin/env bash
# Build the prod debug APK and install it on connected physical Android devices.
set -euo pipefail

usage() {
  cat <<'EOF'
Usage: scripts/deliver-apk.sh [--no-build] [--serial <id>]... [--include-emulator] [-h|--help]

  --no-build           Install the existing APK without building.
  --serial <id>        Install only on this device (repeatable).
  --include-emulator   Also install on emulator-* devices.
EOF
}

build=1
emulator=0
serials=()
while [ $# -gt 0 ]; do
  case "$1" in
    --no-build) build=0 ;;
    --include-emulator) emulator=1 ;;
    --serial)
      [ $# -ge 2 ] || { echo "error: --serial needs a value" >&2; exit 2; }
      serials+=("$2"); shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "error: unknown option $1" >&2; usage >&2; exit 2 ;;
  esac
  shift
done

cd "$(dirname "$0")/.."

if [ "$build" = 1 ]; then
  log=$(mktemp)
  if ! ./gradlew -q :androidApp:assembleProdDebug >"$log" 2>&1; then
    cat "$log" >&2
    rm -f "$log"
    echo "Build FAILED" >&2
    exit 1
  fi
  rm -f "$log"
  echo "Build OK"
fi

apk=""
for f in androidApp/build/outputs/apk/prod/debug/*.apk; do
  [ -f "$f" ] && apk="$f" && break
done
if [ -z "$apk" ]; then
  echo "error: no APK found under androidApp/build/outputs/apk/prod/debug/" >&2
  exit 1
fi
apk="$(cd "$(dirname "$apk")" && pwd)/$(basename "$apk")"
shown="$apk"
if command -v cygpath >/dev/null 2>&1; then shown=$(cygpath -w "$apk"); fi
echo "APK: $shown"

if [ ${#serials[@]} -eq 0 ]; then
  while read -r serial state _; do
    [ "$state" = "device" ] || continue
    case "$serial" in emulator-*) [ "$emulator" = 1 ] || continue ;; esac
    serials+=("$serial")
  done < <(adb devices | tr -d '\r' | tail -n +2)
fi

if [ ${#serials[@]} -eq 0 ]; then
  echo "No physical device connected."
  echo "Install later with: adb -s <serial> install -r \"$shown\""
  exit 0
fi

fail=0
for s in "${serials[@]}"; do
  model=$(adb -s "$s" shell getprop ro.product.model 2>/dev/null | tr -d '\r' || true)
  if adb -s "$s" install -r "$apk" >/dev/null 2>&1; then
    echo "OK     $s (${model:-unknown model})"
  else
    echo "FAILED $s (${model:-unknown model})" >&2
    fail=1
  fi
done
exit "$fail"
