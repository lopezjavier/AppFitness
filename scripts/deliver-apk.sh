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

adb_bin=""
if command -v adb >/dev/null 2>&1; then
  adb_bin=$(command -v adb)
else
  for dir in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}"; do
    [ -n "$dir" ] || continue
    for name in adb adb.exe; do
      if [ -x "$dir/platform-tools/$name" ]; then adb_bin="$dir/platform-tools/$name"; break 2; fi
    done
  done
fi
if [ -z "$adb_bin" ]; then
  echo "error: adb not found. Add it to PATH or set ANDROID_HOME / ANDROID_SDK_ROOT (platform-tools)." >&2
  exit 1
fi
adb() { "$adb_bin" "$@"; }

if [ "$build" = 1 ]; then
  log=$(mktemp)
  trap 'rm -f "$log"' EXIT
  if ! ./gradlew -q :androidApp:assembleProdDebug >"$log" 2>&1; then
    cat "$log" >&2
    echo "Build FAILED" >&2
    exit 1
  fi
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
  if ! devices_out=$(adb devices); then
    echo "error: 'adb devices' failed" >&2
    exit 1
  fi
  devices_out=$(printf '%s\n' "$devices_out" | tr -d '\r' | tail -n +2)
  while read -r serial state _; do
    [ -n "${serial:-}" ] || continue
    case "$state" in
      device) ;;
      unauthorized|offline)
        echo "Skipped $serial ($state): accept the USB debugging prompt on the device." >&2
        continue ;;
      *) continue ;;
    esac
    case "$serial" in emulator-*) [ "$emulator" = 1 ] || continue ;; esac
    serials+=("$serial")
  done <<EOF
$devices_out
EOF
fi

if [ ${#serials[@]} -eq 0 ]; then
  echo "No physical device connected."
  echo "Install later with: adb -s <serial> install -r \"$shown\""
  exit 0
fi

fail=0
for s in "${serials[@]}"; do
  model=$(adb -s "$s" shell getprop ro.product.model 2>/dev/null | tr -d '\r' || true)
  if out=$(adb -s "$s" install -r "$apk" 2>&1); then
    echo "OK     $s (${model:-unknown model})"
  else
    echo "FAILED $s (${model:-unknown model})" >&2
    printf '%s\n' "$out" >&2
    fail=1
  fi
done
exit "$fail"
